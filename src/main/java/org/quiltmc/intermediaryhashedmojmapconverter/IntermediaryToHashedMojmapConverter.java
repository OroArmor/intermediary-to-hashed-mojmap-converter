package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;

public class IntermediaryToHashedMojmapConverter {

    public static void main(String[] args) throws IOException {
        if (args.length != 6) {
            System.err.println("Usage is <inputpath> <inputmappings> <inputnamespace> <outputpath> <outputmappings> <outputnamespace>");
        }

        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[3]);
        Files.createDirectories(outputPath);

        Path inputTinyFile = checkAndCreateTinyCache(args[1]);
        Path outputTinyFile = checkAndCreateTinyCache(args[4]);

        MappingSet officialToInput = new TinyMappingsReader(TinyMappingFactory.load(Files.newBufferedReader(inputTinyFile)), "official", args[2]).read();
        MappingSet officialToOutput = new TinyMappingsReader(TinyMappingFactory.load(Files.newBufferedReader(outputTinyFile)), "official", args[5]).read();

        MappingSet inputToOutput = MappingSet.create().merge(officialToInput.reverse()).merge(officialToOutput);

        Files.walkFileTree(inputPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                remapAndOutputFile(file, outputPath, inputToOutput);
                return super.visitFile(file, attrs);
            }
        });
    }

    private static void remapAndOutputFile(Path inputPath, Path outputPath, MappingSet inputToOutput) throws IOException {
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();

        EnigmaFile transformed = EnigmaReader.readFile(inputPath, (type, original, signature, isMethod) -> {
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
                while (!mappings.peek().hasInnerClassMapping(original) && !mappings.isEmpty()) {
                    mappings.pop();
                }

                mappings.push(mappings.peek().getOrCreateInnerClassMapping(original));
            }

            return mappings.peek().getDeobfuscatedName();
        });

        String name = transformed.getEnigmaClass().getMappedName();
        transformed.export(outputPath.resolve((name.isEmpty() ? transformed.getEnigmaClass().getObfuscatedName() : name) + ".mapping"));
    }

    private static Path checkAndCreateTinyCache(String artifact) throws IOException {
        MavenFileDownloader.MavenArtifact mavenArtifact = MavenFileDownloader.MavenArtifact.from(artifact);
        Path cachedFilePath = Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", mavenArtifact.artifactId(), mavenArtifact.version() + ".jar");
        if (!Files.exists(cachedFilePath)) {
            MavenFileDownloader.downloadFile(mavenArtifact, cachedFilePath);
            ZipFile jarFile = new ZipFile(cachedFilePath.toFile());
            ZipEntry tinyFileEntry = jarFile.stream().filter(zipEntry -> zipEntry.getName().endsWith(".tiny")).findFirst().get();
            Files.write(cachedFilePath.getParent().resolve(mavenArtifact.version() + ".tiny"), jarFile.getInputStream(tinyFileEntry).readAllBytes());
        }
        return cachedFilePath.getParent().resolve(mavenArtifact.version() + ".tiny");
    }
}