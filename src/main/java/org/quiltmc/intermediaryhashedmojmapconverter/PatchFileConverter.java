package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Diff;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.DiffBlock;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.DiffLine;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;
import org.quiltmc.intermediaryhashedmojmapconverter.util.Pair;
import org.quiltmc.intermediaryhashedmojmapconverter.util.Tree;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class PatchFileConverter {
    private static MappingSet inputToOutput;
    private static Path inputRepo;
    private static Path outputRepo;
    private final Path patchFile;
    private final Path convertedFile;

    public PatchFileConverter(Path patchFile, Path outputFile) {
        this.patchFile = patchFile;
        this.convertedFile = outputFile;
    }

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

        convertDirectory(inputPath, outputPath, inputToOutput, inputRepo, outputRepo);

        // Reset the input repo to how it was before
        Util.runGitCommand(inputRepo, "checkout", inputRepoHead);
    }

    public static void convertDirectory(Path inputPath, Path outputPath, MappingSet inputToOutput, Path inputRepo, Path outputRepo) throws IOException {
        setup(inputToOutput, inputRepo, outputRepo);
        List<Path> patchFiles = Util.walkDirectoryAndCollectFiles(inputPath);

        ExecutorService executor = Executors.newFixedThreadPool(8);

        for (Path patchFile : patchFiles) {
            executor.submit(() -> {
                try {
                    PatchFileConverter converter = new PatchFileConverter(patchFile, outputPath.resolve(inputPath.relativize(patchFile)));
                    converter.convert();
                } catch (Throwable t) {
                    System.err.println("Failed to convert " + patchFile);
                    t.printStackTrace();
                }
            });
        }
    }

    public static void setup(MappingSet inputToOutput, Path inputRepo, Path outputRepo) {
        PatchFileConverter.inputToOutput = inputToOutput;
        PatchFileConverter.inputRepo = inputRepo;
        PatchFileConverter.outputRepo = outputRepo;
    }

    private static void checkSetup() {
        if (inputToOutput == null || inputRepo == null || outputRepo == null) {
            throw new IllegalStateException("PatchFileConverter is not set up");
        }
    }

    public void convert() throws IOException {
        checkSetup();

        Patch patch = Patch.read(patchFile);

        List<Diff> convertedDiffs = new ArrayList<>();
        for (Diff diff : patch.getDiffs()) {
            if (!diff.getFrom().endsWith(".mapping")) {
                convertedDiffs.add(diff);
                continue;
            }

            Path inputFile = inputRepo.resolve(diff.getFrom());
            Path outputFile = outputRepo.resolve(diff.getFrom());

            // Checkout commit before the patch in the input repo
            String fromLine = patch.getHeader().get(0);
            String commitFrom = fromLine.substring(fromLine.lastIndexOf("From ") + 5, fromLine.lastIndexOf(" Mon Sep 17 00:00:00 2001"));
            String checkoutOutput = Util.runGitCommand(inputRepo, "checkout", commitFrom + "^");
            if (checkoutOutput.contains("error:")) {
                throw new RuntimeException("There was an error checking out the commit previous to the patch " + patchFile + "\n" + checkoutOutput);
            }

            // (original, parent) -> (type, remapped)
            Map<Pair<String, String>, Pair<EnigmaMapping.Type, String>> inputFileMappingsRemapInfo = new HashMap<>();
            List<String> inputFileLines = Files.readAllLines(inputFile);
            ArrayDeque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();
            EnigmaFile remappedInputFileEnigma = EnigmaReader.readLines(inputFileLines, (type, original, signature, isMethod) -> remapObfuscatedStoringRemapInfo(type, original, signature, isMethod, mappings, inputFileMappingsRemapInfo));

            // Check the input and output files have the same content
            if (!Files.readString(outputFile).replace("\r\n", "\n").equals(remappedInputFileEnigma.toString())) {
                System.out.println("WARNING: The output file " + diff.getFrom() + " does not have the same content as the input file. The conversion may fail");
            }

            Map<Pair<String, String>, Pair<EnigmaMapping.Type, String>> patchedInputFileMappingsRemapInfo = new HashMap<>();
            List<String> patchedInputFileLines = Patch.applyDiff(inputFileLines, diff);
            mappings.clear();
            EnigmaFile patchedRemappedInputFileEnigma = EnigmaReader.readLines(patchedInputFileLines, (type, original, signature, isMethod) -> remapObfuscatedStoringRemapInfo(type, original, signature, isMethod, mappings, patchedInputFileMappingsRemapInfo));
            List<String> patchedRemappedInputFileLines = List.of(patchedRemappedInputFileEnigma.toString().split("\n"));

            // Remap changes
            Map<Pair<DiffLine, Integer>, DiffLine> remappedDiffLinesByOriginalWithLineNumber = new LinkedHashMap<>();
            for (DiffBlock diffBlock : diff.getBlocks()) {
                for (DiffLine diffLine : diffBlock.getDiffLines()) {
                    if (diffLine.getType() == DiffLine.LineType.UNCHANGED) {
                        continue;
                    }

                    boolean removed = diffLine.getType() == DiffLine.LineType.REMOVED;
                    int lineNumber = removed ? diffBlock.getSourceLineNumber(diffLine) : diffBlock.getDestLineNumber(diffLine);
                    DiffLine converted;
                    if (diffLineNeedsRemapping(diffLine)) {
                        if (removed) {
                            converted = convertDiffLine(diffLine, lineNumber - 1, inputFileLines, inputFileMappingsRemapInfo);
                        } else {
                            converted = convertDiffLine(diffLine, lineNumber - 1, patchedInputFileLines, patchedInputFileMappingsRemapInfo);
                        }
                    } else {
                        converted = diffLine;
                    }

                    remappedDiffLinesByOriginalWithLineNumber.put(Pair.of(diffLine, lineNumber), converted);
                }
            }

            Function<String, Pair<String, EnigmaMapping.Type>> lineToCondensedMapping = line -> {
                EnigmaMapping.Type type = getMappingType(line);
                return Pair.of(mappingTypeNeedsRemapping(type) ? lineToCondensedMapping(line) : line.trim(), type);
            };

            List<String> outputFileLines = Files.readAllLines(outputFile);
            Tree<String> outputFileLineTree = Tree.createTreeFromLineList(outputFileLines);
            Map<Pair<String, EnigmaMapping.Type>, String> outputFileLinesByCondensedMappings = new HashMap<>();
            Map<Pair<String, EnigmaMapping.Type>, Integer> condensedMappingToLineNumber = new HashMap<>();
            Tree<Pair<String, EnigmaMapping.Type>> outputFileMappingTree = outputFileLineTree.map(line -> {
                Pair<String, EnigmaMapping.Type> condensed = lineToCondensedMapping.apply(line);
                outputFileLinesByCondensedMappings.put(condensed, line);
                return condensed;
            });

            // Get a line number for each converted diff line
            List<Pair<DiffLine, Integer>> convertedDiffLinesToLineNumber = new ArrayList<>();
            for (Pair<DiffLine, Integer> diffLineWithLineNumber : remappedDiffLinesByOriginalWithLineNumber.keySet()) {
                DiffLine diffLine = diffLineWithLineNumber.left();
                int originalLineNumber = diffLineWithLineNumber.right();

                List<String> lines;
                Map<Pair<String, String>, Pair<EnigmaMapping.Type, String>> remapInfo;
                if (diffLine.getType() == DiffLine.LineType.REMOVED) {
                    lines = inputFileLines;
                    remapInfo = inputFileMappingsRemapInfo;
                } else {
                    lines = patchedInputFileLines;
                    remapInfo = patchedInputFileMappingsRemapInfo;
                }

                Tree<String> lineParentsTree = Tree.createParentLineTreeForLine(originalLineNumber - 1, lines);

                Tree<Pair<String, EnigmaMapping.Type>> parentMappingsTree = lineParentsTree.map(lineToCondensedMapping);

                AtomicReference<String> parentClass = new AtomicReference<>("");
                Tree<Pair<String, EnigmaMapping.Type>> remappedParentMappingsTree = parentMappingsTree.map(mappingAndType -> {
                    String mapping = mappingAndType.left();
                    EnigmaMapping.Type type = mappingAndType.right();
                    String parent = parentClass.get();
                    if (type == EnigmaMapping.Type.CLASS) {
                        parentClass.set(mapping);
                    }

                    if (mappingTypeNeedsRemapping(type)) {
                        Pair<EnigmaMapping.Type, String> remapped = remapInfo.get(Pair.of(mapping, parent));
                        if (remapped == null) {
                            throw new RuntimeException("Failed to find mappings for " + mapping);
                        } else if (remapped.left() != type) {
                            throw new RuntimeException("Found identical mappings " + mapping + " of a different type (" + type + " and " + remapped.left() + ")");
                        }
                        return Pair.of(remapped.right(), type);
                    } else {
                        return mappingAndType;
                    }
                });

                // TODO: Find parent mapping entry in output file, which could be a previous added line
                // TODO: Get line number

                // convertedDiffLinesToLineNumber.add(new Pair<>(remappedDiffLine, lineNumber));
            }

            // Order by line number
            convertedDiffLinesToLineNumber.sort(Comparator.comparingInt((ToIntFunction<Pair<DiffLine, Integer>>) Pair::right).thenComparing(p -> p.left().getType()));

            // Separate by blocks and add context
            int contextLines = 3; // TODO: Detect from patch file
            List<DiffBlock> blocks = separateDiffLinesByBlocks(convertedDiffLinesToLineNumber, contextLines, lineType -> lineType == DiffLine.LineType.REMOVED ? outputFileLines : patchedRemappedInputFileLines);

            convertedDiffs.add(new Diff(diff.getFrom(), diff.getTo(), blocks, diff.getInfo()));
        }

        Patch convertedPatch = new Patch(patch.getHeader(), convertedDiffs, patch.getFooter());
        String convertedPatchExported = convertedPatch.export();

        Files.createDirectories(convertedFile.getParent());
        if (!Files.exists(convertedFile)) {
            Files.createFile(convertedFile);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(convertedFile)) {
            writer.write(convertedPatchExported);
        }
    }

    private static String remapObfuscatedStoringRemapInfo(EnigmaMapping.Type type, String original, boolean signature, boolean isMethod, Deque<ClassMapping<?,?>> mappings, Map<Pair<String, String>, Pair<EnigmaMapping.Type, String>> remapInfo) {
        try {
            String parent;
            String remapped;
            if (signature) {
                String obfuscatedName = original.substring(0, original.indexOf(";"));
                String oldSignature = original.substring(original.indexOf(";") + 1);

                parent = mappings.peek().getObfuscatedName();

                if (isMethod) {
                    MethodMapping methodMapping = mappings.peek().getOrCreateMethodMapping(MethodSignature.of(obfuscatedName, oldSignature));
                    remapped = methodMapping.getDeobfuscatedName() + ";" + methodMapping.getDeobfuscatedDescriptor();
                } else {
                    FieldMapping fieldMapping = mappings.peek().getFieldMapping(obfuscatedName).orElseThrow(() -> new RuntimeException("Unable to find mapping for " + mappings.peek().getObfuscatedName() + "." + obfuscatedName));
                    remapped = fieldMapping.getDeobfuscatedName() + ";" + fieldMapping.getDeobfuscatedSignature().getType().get();
                }
            } else {
                if (mappings.isEmpty()) {
                    parent = "";
                    mappings.push(inputToOutput.getOrCreateClassMapping(original));
                } else {
                    while (!mappings.isEmpty() && !mappings.peek().hasInnerClassMapping(original)) {
                        mappings.pop();
                    }

                    parent = mappings.peek().getObfuscatedName();
                    mappings.push(mappings.peek().getOrCreateInnerClassMapping(original));
                }

                remapped = mappings.peek().getDeobfuscatedName();
            }

            remapInfo.put(Pair.of(original, parent), Pair.of(type, remapped));
            return remapped;
        } catch (Exception e) {
            System.err.println("Error finding mapping for " + original + " with type " + type);
            return original;
        }
    }

    private static DiffLine convertDiffLine(DiffLine diffLine, int lineIndex, List<String> lines, Map<Pair<String, String>, Pair<EnigmaMapping.Type, String>> remapInfoByMapping) {
        String line = diffLine.getLine();
        String[] tokens = line.trim().split("\\s+");
        EnigmaMapping.Type type = EnigmaMapping.Type.valueOf(tokens[0]);

        // Get the parent class of this mapping
        int parentLineIndex = Util.getParentLineIndex(lineIndex, lines);
        String parentClassName;
        if (parentLineIndex == -1) {
            if (line.lastIndexOf("\\t") != -1) {
                throw new RuntimeException("Failed to find parent mapping for " + diffLine);
            }
            parentClassName = "";
        } else {
            String parentLine = lines.get(parentLineIndex);
            parentClassName = parentLine.trim().split("\\s+")[1];
        }

        // Get the remapped mapping
        String mapping = lineToCondensedMapping(line);
        String remappedMapping = null;
        for (Pair<String, String> originalMappingWithParent : remapInfoByMapping.keySet()) {
            Pair<EnigmaMapping.Type, String> info = remapInfoByMapping.get(originalMappingWithParent);
            if (mapping.equals(originalMappingWithParent.left()) && parentClassName.equals(originalMappingWithParent.right()) && info.left() == type) {
                remappedMapping = info.right();
            }
        }
        if (remappedMapping == null) {
            throw new RuntimeException("Failed to find remapped mapping for " + diffLine);
        }

        // Create the converted line
        String indentation = "\t".repeat(line.lastIndexOf("\t") + 1);
        String deobfName = type == EnigmaMapping.Type.CLASS ? (tokens.length == 3 ? tokens[2] : null) : (tokens.length == (type == EnigmaMapping.Type.METHOD ? 4 : 3) ? tokens[2] : null);
        String convertedLine = condensedMappingToLine(remappedMapping, type, indentation, deobfName);
        return new DiffLine(convertedLine, diffLine.getType());
    }

    private static List<DiffBlock> separateDiffLinesByBlocks(List<Pair<DiffLine, Integer>> diffLinesWithLineNumber, int contextLines, Function<DiffLine.LineType, List<String>> allLinesProvider) {
        List<DiffBlock> blocks = new ArrayList<>();
        Deque<Pair<DiffLine, Integer>> blockEntriesStack = new ArrayDeque<>();
        int destLineOffset = 0;
        for (int i = 0; i < diffLinesWithLineNumber.size(); ++i) {
            Pair<DiffLine, Integer> pair = diffLinesWithLineNumber.get(i);
            DiffLine diffLine = pair.left();
            DiffLine.LineType lineType = diffLine.getType();
            Integer lineNumber = pair.right();

            List<String> allLines = allLinesProvider.apply(lineType);
            boolean hasPrev = i > 0;
            Pair<DiffLine, Integer> prev = hasPrev ? diffLinesWithLineNumber.get(i - 1) : null;

            boolean hasContextBefore = lineNumber > 1 && (!hasPrev || lineNumber > prev.right() + 1);
            if (hasContextBefore) {
                int contextStartLine = Math.max(lineNumber - contextLines, hasPrev ? prev.right() + 1 : 1);
                for (int j = contextStartLine; j < lineNumber; ++j) {
                    DiffLine contextLine = new DiffLine(allLines.get(j - 1), DiffLine.LineType.UNCHANGED);
                    blockEntriesStack.push(Pair.of(contextLine, j));
                }
            }

            blockEntriesStack.push(pair);

            boolean hasNext = i < diffLinesWithLineNumber.size() - 1;
            Pair<DiffLine, Integer> next = hasNext ? diffLinesWithLineNumber.get(i + 1) : null;

            boolean hasContextAfter = lineNumber < allLines.size() && (!hasNext || lineNumber < next.right() - 1);
            if (hasContextAfter) {
                int contextEndLine = Math.min(lineNumber + contextLines, hasNext ? next.right() - 1 : allLines.size());
                if (hasNext && next.right() - lineNumber < contextLines * 2) {
                    contextEndLine = Math.max(next.right() - contextLines, lineNumber + 1) - 1;
                }

                for (int j = lineNumber + 1; j <= contextEndLine; ++j) {
                    DiffLine contextLine = new DiffLine(allLines.get(j - 1), DiffLine.LineType.UNCHANGED);
                    blockEntriesStack.push(Pair.of(contextLine, j));
                }
            }

            int distanceToNext = hasNext ? next.right() - 1 - lineNumber : contextLines * 2 + 1;
            if (distanceToNext > contextLines * 2) {
                List<Pair<DiffLine, Integer>> sourceLines = blockEntriesStack.stream().filter(p -> p.left().getType().increasesSourceLineNumber()).collect(Collectors.toList());
                List<Pair<DiffLine, Integer>> destLines = blockEntriesStack.stream().filter(p -> p.left().getType().increasesDestLineNumber()).collect(Collectors.toList());
                int sourceLine = sourceLines.get(sourceLines.size() - 1).right();
                int sourceSize = sourceLines.size();
                int destLine = destLines.get(destLines.size() - 1).right() + destLineOffset;
                int destSize = destLines.size();

                destLineOffset += destSize - sourceSize;

                List<DiffLine> diffLines = blockEntriesStack.stream().map(Pair::left).collect(Collectors.toList());
                Collections.reverse(diffLines);
                DiffBlock block = new DiffBlock(sourceLine, sourceSize, destLine, destSize, diffLines);
                blockEntriesStack.clear();
                blocks.add(block);
            }
        }

        return blocks;
    }

    private static String lineToCondensedMapping(String line) {
        String[] tokens = line.trim().split("\\s+");
        EnigmaMapping.Type type = EnigmaMapping.Type.valueOf(tokens[0]);
        return switch (type) {
            case CLASS -> tokens[1];
            case FIELD, METHOD -> tokens[1] + ";" + (tokens.length == 4 ? tokens[3] : tokens[2]);
            default -> line;
        };
    }

    private static String condensedMappingToLine(String mapping, EnigmaMapping.Type type, String indentation, @Nullable String deobfName) {
        return switch (type) {
            case CLASS -> indentation + "CLASS " + mapping + (deobfName != null ? deobfName : "");
            case METHOD -> {
                String name = mapping.substring(0, mapping.indexOf(";"));
                String descriptor = mapping.substring(mapping.indexOf(";") + 1);
                yield indentation + "METHOD " + name + " " + (deobfName != null ? deobfName + " " : "") + descriptor;
            }
            case FIELD -> {
                String name = mapping.substring(0, mapping.indexOf(";"));
                String descriptor = mapping.substring(mapping.indexOf(";") + 1);
                yield indentation + "FIELD " + name + " " + (deobfName != null ? deobfName + " " : "") + descriptor;
            }
            default -> mapping;
        };
    }

    private static boolean diffLineNeedsRemapping(DiffLine diffLine) {
        if (diffLine.getType() == DiffLine.LineType.UNCHANGED) {
            return false;
        }

        EnigmaMapping.Type type = getMappingType(diffLine.getLine());
        return mappingTypeNeedsRemapping(type);
    }

    private static boolean mappingTypeNeedsRemapping(EnigmaMapping.Type type) {
        return type != EnigmaMapping.Type.COMMENT && type != EnigmaMapping.Type.ARG;
    }

    private static EnigmaMapping.Type getMappingType(String line) {
        return EnigmaMapping.Type.valueOf(line.trim().split("\\s+")[0]);
    }
}
