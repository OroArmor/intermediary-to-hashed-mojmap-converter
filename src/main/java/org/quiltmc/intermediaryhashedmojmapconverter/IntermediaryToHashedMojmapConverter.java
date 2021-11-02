package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        ExecutorService executor = Executors.newFixedThreadPool(32);

        Set<Path> inProgress = new HashSet<>();

        Files.walkFileTree(inputPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                inProgress.add(file);
                executor.execute(() -> {
                    try {
                        remapAndOutputFile(file, outputPath, inputToOutput);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    inProgress.remove(file);
                });
                return super.visitFile(file, attrs);
            }
        });


        try {
            boolean successful = executor.awaitTermination(100, TimeUnit.SECONDS);
            if (!successful) {
                System.err.println("Executor failed to stop.");
                inProgress.forEach(System.out::println);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void remapAndOutputFile(Path inputPath, Path outputPath, MappingSet inputToOutput) throws IOException {
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();

        EnigmaFile transformed = EnigmaReader.readFile(inputPath, (type, original, signature, isMethod) -> {
            try {
                return Util.remapObfuscated(type, original, signature, isMethod, inputToOutput, mappings);
            } catch (Exception e) {
                System.err.println("Error finding mapping for " + original + " with type " + type + " in file " + inputPath);
                return original;
            }
        });

        String name = transformed.getEnigmaClass().getMappedName();
        transformed.export(outputPath.resolve((name.isEmpty() ? transformed.getEnigmaClass().getObfuscatedName() : name) + ".mapping"));
    }
}
