package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Diff;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.DiffBlock;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.DiffLine;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class PatchFileConverter {
    public static void main(String[] args) throws IOException {
        if (args.length != 8) {
            System.err.println("Usage is <inputpath> <inputmappings> <inputnamespace> <outputpath> <outputmappings> <outputnamespace> <inputrepo> <outputrepo>");
            System.exit(-1);
        }

        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[3]);
        Files.createDirectories(outputPath);

        MappingSet inputToOutput = Util.createInputToOutputMappings(args[1], args[2], args[4], args[5]);

        Path inputRepo = Path.of(args[6]);
        Path outputRepo = Path.of(args[7]);

        // Check for uncommitted changes in the input repo
        String uncommittedInputRepoChanges = Util.getUncommittedChanges(inputRepo);
        if (!uncommittedInputRepoChanges.isEmpty()) {
            System.err.println("You have uncommitted changes in the input repository. Please commit or stash them before continuing\n"
                    + uncommittedInputRepoChanges);
            System.exit(-1);
        }

        // Get what HEAD is pointing to
        String inputRepoHead = Util.getRepoHead(inputRepo);

        Files.walkFileTree(inputPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                try {
                    Path relative = inputPath.relativize(path);
                    convertPatchFile(path, outputPath.resolve(relative), inputToOutput, inputRepo, outputRepo);
                } catch (Exception e) {
                    System.err.println("Failed to convert " + path);
                    e.printStackTrace();
                }
                return super.visitFile(path, attrs);
            }
        });

        // Reset the input repo to how it was before
        Util.runGitCommand(inputRepo, "checkout", inputRepoHead);
    }

    public static void convertPatchFile(Path path, Path outputPath, MappingSet inputToOutput, Path inputRepo, Path outputRepo) throws IOException {
        // Steps
        // 1. Read patch file
        // 2. Read yarn `from` file, store class intermediary to hashed mappings
        // 2.a. Before, we have to restore yarn to how it was before the commit in the patch
        // 3. Remap individually changes
        // 3.1. Iterate each changed diff line
        // 3.2. Remap line
        // 3.3. Store remapped line by source/dest line number
        // 4. Reorder changes with EnigmaFile, replace "context"
        // 4.1. Read qm file and export it to a List of lines
        // 4.2. For each removed remapped line, store the line number
        // 4.3. Read what would be the qm file with the patch applied export it to a List of lines
        // TODO: This needs some processing to put the added remapped lines in the right places
        // 4.4. For each added remapped line, store the line number
        // 4.5. Go over the lines to line number, add context and separate by blocks
        // 4.5.1. Order the lines to line number map by line number
        // 4.5.2. Add context before
        // 4.5.3. Add context after
        // 4.5.4. Separate by blocks
        // 4.a. Remove redundant changes
        // 5. Create new patch
        // 6. Save

        // 1
        Patch patch = Patch.read(path);

        List<Diff> diffs = new ArrayList<>();

        for (Diff diff : patch.getDiffs()) {
            if (!diff.getFrom().endsWith(".mapping")) {
                diffs.add(diff);
                continue;
            }
            Path yarnFilePath = inputRepo.resolve(diff.getFrom());
            List<ClassMapping<?, ?>> classesIntermediaryToHashed = new ArrayList<>();

            // 2
            String fromLine = patch.getHeader().get(0);
            String commitFrom = fromLine.substring(fromLine.lastIndexOf("From ") + 5, fromLine.lastIndexOf(" Mon Sep 17 00:00:00 2001"));
            String output = Util.runGitCommand(inputRepo, "checkout", commitFrom + "^");
            if (output.contains("error:")) {
                throw new RuntimeException("There was an error checking out the commit previous to the patch " + path + "\n" + output);
            }

            EnigmaReader.readFile(yarnFilePath, (type, original, signature, isMethod) -> {
                if (type == EnigmaMapping.Type.CLASS) {
                    Optional<? extends ClassMapping<?, ?>> optionalMapping = inputToOutput.getClassMapping(original);
                    ClassMapping<?, ?> mapping = null;
                    if (optionalMapping.isPresent()) {
                        mapping = optionalMapping.get();
                    } else {
                        for (int i = classesIntermediaryToHashed.size() - 1; i >= 0; --i) {
                            ClassMapping<?, ?> classMapping = classesIntermediaryToHashed.get(i);
                            if (classMapping.hasInnerClassMapping(original)) {
                                mapping = classMapping.getInnerClassMapping(original).get();
                            }
                        }
                        if (mapping == null) {
                            throw new RuntimeException("Failed to find mappings for class " + original);
                        }
                    }
                    classesIntermediaryToHashed.add(mapping);
                }
                return original;
            });

            // 3
            List<DiffLine> remappedRemovedLines = new ArrayList<>();
            List<DiffLine> remappedAddedLines = new ArrayList<>();
            // 3.1
            for (DiffBlock block : diff.getBlocks()) {
                for (DiffLine diffLine : block.getDiffLines()) {
                    DiffLine.LineType type = diffLine.getType();
                    if (type != DiffLine.LineType.UNCHANGED) {
                        // 3.3
                        String remappedLine = remapLine(diffLine.getLine(), inputToOutput, classesIntermediaryToHashed);
                        DiffLine remappedDiffLine = new DiffLine(remappedLine, type);
                        switch (type) {
                            case REMOVED -> remappedRemovedLines.add(remappedDiffLine);
                            case ADDED -> remappedAddedLines.add(remappedDiffLine);
                        }
                    }
                }
            }

            // 4
            Path qmFile = outputRepo.resolve(diff.getFrom());
            // 4.1
            EnigmaFile enigmaQmFile = EnigmaReader.readFile(qmFile);
            List<String> enigmaQmFileLines = List.of(enigmaQmFile.toString().split("\n"));

            // 4.2
            Map<DiffLine, Integer> diffLinesToLineNumber = new HashMap<>();
            for (DiffLine remappedRemovedLine : remappedRemovedLines) {
                int index = enigmaQmFileLines.indexOf(remappedRemovedLine.getLine());
                if (index != -1) {
                    diffLinesToLineNumber.put(remappedRemovedLine, index + 1);
                }
            }

            // 4.3
            List<String> qmFileWithDifferences = Files.readAllLines(qmFile);
            for (DiffLine remappedAddedLine : remappedAddedLines) {
                String line = remappedAddedLine.getLine();
                String[] tokens = line.trim().split("\\s+");
                EnigmaMapping.Type type = EnigmaMapping.Type.valueOf(tokens[0]);
                int indent = line.lastIndexOf("\t");
                if (indent == -1) {
                    qmFileWithDifferences.add(0, line);
                } else if (indent == 0 && type != EnigmaMapping.Type.COMMENT) {
                    // Find where to put the line
                    // TODO: Fix for comments
                    int i = 1;
                    for (; i < qmFileWithDifferences.size(); ++i) {
                        String line2 = qmFileWithDifferences.get(i);
                        String[] tokens2 = line2.trim().split("\\s+");
                        EnigmaMapping.Type type2 = EnigmaMapping.Type.valueOf(tokens2[0]);
                        if (type == type2) {
                            break;
                        }
                    }
                    qmFileWithDifferences.add(i, line);
                } else {
                    // TODO
                    System.out.println("WARN: Added line '" + line + "' will not be in the final result");
                }
            }
            for (DiffLine remappedRemovedLine : remappedRemovedLines) {
                qmFileWithDifferences.remove(remappedRemovedLine.getLine());
            }
            EnigmaFile enigmaQmFileWithDifferences = EnigmaReader.readLines(qmFileWithDifferences);
            List<String> enigmaQmFileWithDifferencesLines = List.of(enigmaQmFileWithDifferences.toString().split("\n"));

            // 4.4
            for (DiffLine remappedAddedLine : remappedAddedLines) {
                String line = remappedAddedLine.getLine();
                String[] tokens = line.trim().split("\\s+");
                EnigmaMapping.Type type = EnigmaMapping.Type.valueOf(tokens[0]);

                int indent = line.lastIndexOf("\t");
                if (indent >= 1 || type == EnigmaMapping.Type.COMMENT) {
                    continue;
                }

                int index = enigmaQmFileWithDifferencesLines.indexOf(line);
                if (index == -1) {
                    throw new RuntimeException("Failed to find line '" + remappedAddedLine.getLine().trim() + "' in ordered enigma file");
                }
                diffLinesToLineNumber.put(remappedAddedLine, index + 1);
            }

            // 4.5
            // 4.5.1
            List<Map.Entry<DiffLine, Integer>> diffLinesToLineNumberOrdered = new LinkedList<>(diffLinesToLineNumber.entrySet());
            diffLinesToLineNumberOrdered.sort(Comparator.comparingInt((ToIntFunction<Map.Entry<DiffLine, Integer>>) Map.Entry::getValue).thenComparing(o -> o.getKey().getType()));

            List<DiffBlock> blocks = new ArrayList<>();
            Deque<Map.Entry<DiffLine, Integer>> blockEntriesStack = new ArrayDeque<>();
            int contextLines = 3;
            for (int i = 0; i < diffLinesToLineNumberOrdered.size(); ++i) {
                Map.Entry<DiffLine, Integer> entry = diffLinesToLineNumberOrdered.get(i);
                DiffLine diffLine = entry.getKey();
                DiffLine.LineType lineType = diffLine.getType();
                int lineNumber = entry.getValue();

                List<String> allLines = lineType == DiffLine.LineType.REMOVED ? enigmaQmFileLines : enigmaQmFileWithDifferencesLines;
                boolean hasPrev = i > 0;
                boolean hasNext = i < diffLinesToLineNumberOrdered.size() - 1;
                Map.Entry<DiffLine, Integer> prev = hasPrev ? diffLinesToLineNumberOrdered.get(i - 1) : null;
                Map.Entry<DiffLine, Integer> next = hasNext ? diffLinesToLineNumberOrdered.get(i + 1) : null;

                // 4.5.2
                boolean hasContextBefore = lineNumber > 1 && (!hasPrev || lineNumber > prev.getValue() + 1);
                if (hasContextBefore) {
                    int contextStartLine = Math.max(lineNumber - contextLines, hasPrev ? prev.getValue() + 1 : 1);
                    for (int j = contextStartLine; j < lineNumber; ++j) {
                        DiffLine contextLine = new DiffLine(allLines.get(j - 1), DiffLine.LineType.UNCHANGED);
                        blockEntriesStack.push(new AbstractMap.SimpleEntry<>(contextLine, j));
                    }
                }

                blockEntriesStack.push(entry);

                // 4.5.3
                boolean hasContextAfter = lineNumber < allLines.size() && (!hasNext || lineNumber < next.getValue() - 1);
                if (hasContextAfter) {
                    int contextEndLine = Math.min(lineNumber + contextLines, hasNext ? next.getValue() - 1 : allLines.size());
                    if (hasNext && next.getValue() - lineNumber < contextLines * 2) {
                        contextEndLine = Math.max(next.getValue() - contextLines, lineNumber + 1) - 1;
                    }
                    for (int j = lineNumber + 1; j <= contextEndLine; ++j) {
                        DiffLine contextLine = new DiffLine(allLines.get(j - 1), DiffLine.LineType.UNCHANGED);
                        blockEntriesStack.push(new AbstractMap.SimpleEntry<>(contextLine, j));
                    }
                }

                // 4.5.4
                int distanceToNext = hasNext ? next.getValue() - 1 - lineNumber : contextLines * 2 + 1;
                if (distanceToNext > contextLines * 2) {
                    List<Map.Entry<DiffLine, Integer>> sourceLines = blockEntriesStack.stream().filter(entry1 -> entry1.getKey().getType().increasesSourceLineNumber()).collect(Collectors.toList());
                    List<Map.Entry<DiffLine, Integer>> destLines = blockEntriesStack.stream().filter(entry1 -> entry1.getKey().getType().increasesDestLineNumber()).collect(Collectors.toList());
                    int sourceLine = sourceLines.get(sourceLines.size() - 1).getValue();
                    int sourceSize = sourceLines.size();
                    int destLine = destLines.get(destLines.size() - 1).getValue();
                    int destSize = destLines.size();

                    List<DiffLine> diffLines = blockEntriesStack.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                    Collections.reverse(diffLines);
                    DiffBlock block = new DiffBlock(sourceLine, sourceSize, destLine, destSize, diffLines);
                    blockEntriesStack.clear();
                    blocks.add(block);
                }
            }

            diffs.add(new Diff(diff.getFrom(), diff.getTo(), blocks, diff.getInfo()));
        }

        // 5
        Patch newPatch = new Patch(patch.getHeader(), diffs, patch.getFooter());
        String rawNewPatch = newPatch.export();

        // 6
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(rawNewPatch);
        }
    }

    private static String remapLine(String line, MappingSet intermediaryToHashed, List<ClassMapping<?, ?>> classesIntermediaryToHashed) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length <= 1) {
            return line;
        }

        switch (tokens[0]) {
            case "CLASS" -> {
                Optional<? extends ClassMapping<?, ?>> optionalMapping = intermediaryToHashed.getClassMapping(tokens[1]);
                ClassMapping<?, ?> mapping = null;
                if (optionalMapping.isEmpty()) {
                    for (ClassMapping<?, ?> classMapping1 : classesIntermediaryToHashed) {
                        if (classMapping1.getInnerClassMapping(tokens[1]).isPresent()) {
                            mapping = classMapping1.getInnerClassMapping(tokens[1]).get();
                            break;
                        }
                    }
                } else {
                    mapping = optionalMapping.get();
                }
                if (mapping == null) {
                    break;
                }

                return line.replace(tokens[1], mapping.getDeobfuscatedName());
            }
            case "METHOD" -> {
                String descriptor = tokens.length == 4 ? tokens[3] : tokens[2];
                MethodSignature signature = MethodSignature.of(tokens[1], descriptor);
                // Find a class with the method
                ClassMapping<?, ?> classMapping = null;
                for (ClassMapping<?, ?> classMapping1 : classesIntermediaryToHashed) {
                    if (classMapping1.hasMethodMapping(signature)) {
                        classMapping = classMapping1;
                        break;
                    }
                }

                if (classMapping == null) {
                    break;
                }

                MethodMapping mapping = classMapping.getMethodMapping(signature).get();
                String newLine = line;
                if (!"<init>".equals(tokens[1])) {
                    newLine = newLine.replace(tokens[1], mapping.getDeobfuscatedName());
                    if (tokens.length == 3 && !tokens[1].startsWith("method_")) {
                        newLine = newLine.substring(0, newLine.lastIndexOf(" ") + 1) + tokens[1] + newLine.substring(newLine.lastIndexOf(" "));
                    }
                }
                return newLine.replace(descriptor, mapping.getDeobfuscatedDescriptor());
            }
            case "FIELD" -> {
                // Find a class with the field
                ClassMapping<?, ?> classMapping = null;
                for (ClassMapping<?, ?> classMapping1 : classesIntermediaryToHashed) {
                    if (classMapping1.hasFieldMapping(tokens[1])) {
                        classMapping = classMapping1;
                        break;
                    }
                }

                if (classMapping == null) {
                    break;
                }

                FieldMapping mapping = classMapping.getFieldMapping(tokens[1]).get();
                String newLine = line.replace(tokens[1], mapping.getDeobfuscatedName());
                return newLine.substring(0, newLine.lastIndexOf(" ") + 1) + intermediaryToHashed.deobfuscate(mapping.getType().get()).toString();
            }
        }

        return line;
    }

    // Searches for the right position to put the new lines so EnigmaReader doesn't break
    private static void addLinesToMappingFile(List<String> lines, List<String> newLines) {
        // TODO: Proper support for inner classes, parameters and comments
        int fieldDefinitionIndex = lines.size() - 1;
        int methodDefinitionIndex = lines.size() - 1;
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            String[] tokens = line.trim().split("\\s+");
            EnigmaMapping.Type type = EnigmaMapping.Type.valueOf(tokens[0]);
            int currentIndent = line.lastIndexOf("\t");
            if (type == EnigmaMapping.Type.FIELD) {
                if (i < lines.size() - 1) {
                    String nextLine = lines.get(i + 1);
                    int nextIndent = nextLine.lastIndexOf("\t");
                    // Find the next line with the same indent
                    if (nextIndent > currentIndent) {
                        ++i;
                        while (nextIndent > currentIndent && i < lines.size()) {
                            ++i;
                            nextLine = lines.get(i);
                            nextIndent = nextLine.lastIndexOf("\t");
                        }
                        fieldDefinitionIndex = i;
                    } else {
                        fieldDefinitionIndex = i + 1;
                    }
                }
            } else if (type == EnigmaMapping.Type.METHOD) {
                if (i < lines.size() - 1) {
                    String nextLine = lines.get(i + 1);
                    int nextIndent = nextLine.lastIndexOf("\t");
                    // Find the next line with the same indent
                    if (nextIndent > currentIndent) {
                        ++i;
                        while (nextIndent > currentIndent && i < lines.size() - 1) {
                            ++i;
                            nextLine = lines.get(i);
                            nextIndent = nextLine.lastIndexOf("\t");
                        }
                        methodDefinitionIndex = i;
                    } else {
                        methodDefinitionIndex = i + 1;
                    }
                }
            }
        }

        for (String line : newLines) {
            String[] tokens = line.trim().split("\\s+");
            EnigmaMapping.Type type = EnigmaMapping.Type.valueOf(tokens[0]);
            if (type == EnigmaMapping.Type.FIELD) {
                lines.add(fieldDefinitionIndex++, line);
            } else if (type == EnigmaMapping.Type.METHOD) {
                lines.add(methodDefinitionIndex++, line);
            }
        }
    }
}
