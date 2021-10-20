package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;

public class IntermediaryToHashedMojmapConverter {
    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            System.err.println("Usage is <inputpath> <inputmappings> <inputnamespace> <outputpath> <outputmappings> <outputnamespace>");
            System.exit(-1);
        }

        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[3]);
        Files.createDirectories(outputPath);

        MappingSet inputToOutput = Util.createInputToOutputMappings(args[1], args[2], args[4], args[5]);

        ExecutorService executor = Executors.newFixedThreadPool(8);

        Files.walkFileTree(inputPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                executor.submit(() -> {
                    try {
                        remapAndOutputFile(file, outputPath, inputToOutput);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                return super.visitFile(file, attrs);
            }
        });
    }

    private static void remapAndOutputFile(Path inputPath, Path outputPath, MappingSet inputToOutput) throws IOException {
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();

        EnigmaFile transformed = EnigmaReader.readFile(inputPath, (type, original, signature, isMethod) -> {
            try {
                if (signature) {
                    String obfuscatedName = original.substring(0, original.indexOf(";"));
                    String oldSignature = original.substring(original.indexOf(";") + 1);

                    if (isMethod) {
                        MethodMapping methodMapping = mappings.peek().getOrCreateMethodMapping(MethodSignature.of(obfuscatedName, oldSignature));
                        return methodMapping.getDeobfuscatedName() + ";" + methodMapping.getDeobfuscatedDescriptor();
                    }

                    FieldMapping fieldMapping = mappings.peek().getFieldMapping(obfuscatedName).orElseThrow(() -> new RuntimeException("Unable to find mapping for " + mappings.peek().getObfuscatedName() + "." + obfuscatedName));
                    return fieldMapping.getDeobfuscatedName() + ";" + fieldMapping.getDeobfuscatedSignature().getType().get();
                }

                if (mappings.isEmpty()) {
                    mappings.push(inputToOutput.getOrCreateClassMapping(original));
                } else {
                    while (!mappings.isEmpty() && !mappings.peek().hasInnerClassMapping(original)) {
                        mappings.pop();
                    }

                    mappings.push(mappings.peek().getOrCreateInnerClassMapping(original));
                }

                return mappings.peek().getDeobfuscatedName();
            } catch (Exception e) {
                System.err.println("Error finding mapping for " + original + " with type " + type);
                return original;
            }
        });

        String name = transformed.getEnigmaClass().getMappedName();
        transformed.export(outputPath.resolve((name.isEmpty() ? transformed.getEnigmaClass().getObfuscatedName() : name) + ".mapping"));
    }
}
