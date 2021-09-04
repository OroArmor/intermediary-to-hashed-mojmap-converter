package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Diff;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.DiffBlock;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.DiffLine;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.quiltmc.intermediaryhashedmojmapconverter.IntermediaryToHashedMojmapConverter.*;

public class PatchFileConverter {
    public static void main(String[] a) throws IOException {
        List<String> args = List.of(a);
        Path inputPath = getArgProperty("quilt.inputFiles", "-DquiltInputFiles", args, Path.of(System.getProperty("user.dir")), Path::of);
        Path outputPath = getArgProperty("quilt.outputDirectory", "-DquiltOutputDirectory", args, Path.of(System.getProperty("user.dir"), "remapped"), Path::of);
        String minecraftVersion = getArgProperty("quilt.minecraft", "-DquiltMinecraft", args, "1.17", Function.identity());
        Files.createDirectories(outputPath);

        Path yarnDir = getArgProperty("quilt.yarnDir", "-DyarnDir", args, Path.of(System.getProperty("user.dir"), "yarn"), Path::of);
        Path mappingsDir = getArgProperty("quilt.mappingsDir", "-DquiltMappingsDir", args, Path.of(System.getProperty("user.dir"), "quilt-mappings"), Path::of);

        MappingSet intermediaryToHashed = getIntermediaryToHashed(minecraftVersion);

        iterateOverFiles(inputPath, path -> {
            try {
                Path relative = inputPath.relativize(path);
                convertPatchFile(path, outputPath.resolve(relative), intermediaryToHashed, yarnDir, mappingsDir);
            } catch (Exception e) {
                System.err.println("Failed to convert " + path);
                e.printStackTrace();
            }
        });
    }

    private static void convertPatchFile(Path path, Path outputPath, MappingSet intermediaryToHashed, Path yarnDir, Path mappingsDir) throws IOException {
        // Steps
        // 1. Read patch file
        // 2. Analyze context, add class definitions
        // 3. Remap individually changes
        // 3.1. Iterate over each diff, then each block, then each changed line
        // 3.2. Read change
        // 3.3. Find mapping for line
        // 3.3.a. If the diff context has a class, use it
        // 3.3.b. If the diff context does not have a class, read the `from` yarn file
        // 3.4. Remap line
        // 3.4.1. Replace intermediary "obfuscated" name
        // 3.4.2. Replace descriptor
        // 4. Reorder changes, replace "context"
        // 4.a. Remove redundant changes
        // 5. Create new patch
        // 6. Save

        // 1
        Patch patch = Patch.read(path);

        List<Diff> diffs = new ArrayList<>();

        for (Diff diff : patch.getDiffs()) {
            // 2
            Deque<ClassMapping<?, ?>> classMappingsStack = new ArrayDeque<>();
            for (DiffBlock block : diff.getBlocks()) {
                for (DiffLine diffLine : block.getDiffLines()) {
                    String line = diffLine.getLine();
                    DiffLine.LineType lineType = diffLine.getType();
                    if (lineType == DiffLine.LineType.UNCHANGED) {
                        updateClassMappingsStack(line, intermediaryToHashed, classMappingsStack);
                    }
                }
            }

            // 3
            List<DiffBlock> blocks = new ArrayList<>();
            for (DiffBlock block : diff.getBlocks()) {
                List<DiffLine> diffLines = new ArrayList<>();
                for (DiffLine diffLine : block.getDiffLines()) {
                    String line = diffLine.getLine();
                    DiffLine.LineType lineType = diffLine.getType();
                    String newLine;

                    if (lineType == DiffLine.LineType.UNCHANGED) {
                        newLine = line;
                    } else {
                        newLine = remapLine(line, intermediaryToHashed, classMappingsStack);
                    }

                    diffLines.add(new DiffLine(newLine, lineType));
                }

                // TODO: Step 4 using the qm files
                int sourceLine = block.getSourceLine();
                int sourceSize = block.getSourceSize();
                int destLine = block.getDestLine();
                int destSize = block.getDestSize();
                blocks.add(new DiffBlock(sourceLine, sourceSize, destLine, destSize, diffLines));
            }

            diffs.add(new Diff(diff.getFrom(), diff.getTo(), blocks, diff.getInfo()));
        }

        Patch newPatch = new Patch(patch.getHeader(), diffs, patch.getFooter());
        String rawNewPatch = newPatch.export();

        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(rawNewPatch);
        }
    }

    private static void iterateOverFiles(Path inputPath, Consumer<Path> processor) {
        File inputFilePath = inputPath.toFile();
        if (inputFilePath.isDirectory()) {
            for (File file : inputFilePath.listFiles()) {
                iterateOverFiles(file.toPath(), processor);
            }
        } else {
            processor.accept(inputPath);
        }
    }

    private static void updateClassMappingsStack(String line, MappingSet intermediaryToHashed, Deque<ClassMapping<?, ?>> classMappingsStack) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length <= 1) {
            return;
        }

        if ("CLASS".equals(tokens[0])) {
            System.out.println("Putting '" + line + "' on classMappingsStack");
            Optional<? extends ClassMapping<?, ?>> optionalMapping = intermediaryToHashed.getClassMapping(tokens[1]);
            ClassMapping<?, ?> mapping = null;

            if (optionalMapping.isPresent()) {
                mapping = optionalMapping.get();
            } else if (!classMappingsStack.isEmpty()) {
                // Find the upper class for our inner class
                for (ClassMapping<?, ?> classMapping : classMappingsStack) {
                    if (classMapping.hasInnerClassMapping(tokens[1])) {
                        mapping = classMapping.getOrCreateInnerClassMapping(tokens[1]);
                        break;
                    }
                }
            }
            if (mapping == null) {
                throw new RuntimeException("Failed to find mappings for class " + tokens[1]);
            }

            classMappingsStack.push(mapping);
        }
    }

    private static String remapLine(String line, MappingSet intermediaryToHashed, Deque<ClassMapping<?, ?>> classMappingsStack) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length <= 1) {
            return line;
        }

        switch (tokens[0]) {
            case "CLASS" -> {
                Optional<? extends ClassMapping<?, ?>> optionalMapping = intermediaryToHashed.getClassMapping(tokens[1]);
                ClassMapping<?, ?> mapping = null;
                if (optionalMapping.isEmpty()) {
                    for (ClassMapping<?, ?> classMapping1 : classMappingsStack) {
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
                if (!classMappingsStack.isEmpty()) {
                    ClassMapping<?, ?> classMapping = classMappingsStack.peek();
                    if (classMapping.getMethodMapping(signature).isEmpty()) {
                        // Find another class with the method
                        for (ClassMapping<?, ?> classMapping1 : classMappingsStack) {
                            if (classMapping1.hasMethodMapping(signature)) {
                                classMapping = classMapping1;
                                break;
                            }
                        }
                        if (!classMapping.hasMethodMapping(signature)) {
                            break;
                        }
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
                } else {
                    // TODO: Find a class mapping for the method's class using the yarn files
                }
            }
            case "FIELD" -> {
                if (!classMappingsStack.isEmpty()) {
                    ClassMapping<?, ?> classMapping = classMappingsStack.peek();
                    if (classMapping.getFieldMapping(tokens[1]).isEmpty()) {
                        // Find another class with the field
                        for (ClassMapping<?, ?> classMapping1 : classMappingsStack) {
                            if (classMapping1.hasFieldMapping(tokens[1])) {
                                classMapping = classMapping1;
                                break;
                            }
                        }

                        if (!classMapping.hasFieldMapping(tokens[1])) {
                            break;
                        }
                    }

                    FieldMapping mapping = classMapping.getFieldMapping(tokens[1]).get();
                    String newLine = line.replace(tokens[1], mapping.getDeobfuscatedName());
                    return newLine.substring(0, newLine.lastIndexOf(" ") + 1) + intermediaryToHashed.deobfuscate(mapping.getType().get()).toString();
                } else {
                    // TODO: Find a class mapping for the field's class using the yarn files
                }
            }
        }

        return line;
    }
}
