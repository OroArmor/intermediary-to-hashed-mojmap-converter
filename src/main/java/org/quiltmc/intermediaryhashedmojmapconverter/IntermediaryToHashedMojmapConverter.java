package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringFormatterMessageFactory;
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
    public static Logger LOGGER = LogManager.getLogger("Intermediary To Hashed Mojmap Converter", new StringFormatterMessageFactory());

    public static void main(String[] args) throws IOException {
        List<String> argList = List.of(args);

        Path inputPath = getArgProperty("-DquiltInputFiles", argList, Path.of(System.getProperty("user.dir")), Path::of);
        Path outputPath = getArgProperty("-DquiltOutputDirectory", argList, Path.of(System.getProperty("user.dir"), "remapped"), Path::of);
        String minecraftVersion = getArgProperty("-DquiltMinecraft", argList, "1.17", Function.identity());
        Files.createDirectories(outputPath);

        checkAndCreateTinyCache("net.fabricmc:intermediary:" + minecraftVersion + ":v2");
        checkAndCreateTinyCache("org.quiltmc:hashed-mojmap:" + minecraftVersion + "-SNAPSHOT");

        MappingSet officialToIntermediary = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", "intermediary", minecraftVersion + ".tiny").toFile()))), "official", "intermediary").read();
        MappingSet officialToHashed = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", "hashed-mojmap", minecraftVersion + "-SNAPSHOT.tiny").toFile()))), "official", "hashed").read();

        MappingSet intermediaryToHashed = MappingSet.create().merge(officialToIntermediary.reverse()).merge(officialToHashed);

        if (inputPath.toFile().isDirectory()) {
            iterateOverDirectory(inputPath, Path.of("."), outputPath, intermediaryToHashed);
        } else {
            remapAndOutputFile(inputPath, outputPath, intermediaryToHashed);
        }
    }

    private static void iterateOverDirectory(Path inputPath, Path initialPath, Path outputPath, MappingSet intermediaryToHashed) {
        File inputFilePath = new File(inputPath.toUri());
        if (inputFilePath.isDirectory()) {
            for (File file : inputFilePath.listFiles()) {
                iterateOverDirectory(Path.of(file.toURI()), initialPath.resolve(inputPath.getFileName()), outputPath, intermediaryToHashed);
            }
        } else {
            try {
                remapAndOutputFile(inputPath, outputPath.resolve(initialPath), intermediaryToHashed);
            } catch (IOException e) {
                System.err.println("Unable to remap " + inputPath.getFileName());
            }
        }
    }

    private static void remapAndOutputFile(Path inputPath, Path outputPath, MappingSet intermediaryToHashed) throws IOException {
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();

        EnigmaFile transformed = EnigmaReader.readFile(inputPath, (type, original, signature, isMethod) -> {
            if (signature) {
                String obfuscatedName = original.substring(0, original.indexOf(";"));
                String oldSignature = original.substring(original.indexOf(";") + 1);

                if (isMethod) {
                    MethodMapping methodMapping = mappings.peek().getOrCreateMethodMapping(MethodSignature.of(obfuscatedName, oldSignature));
                    return methodMapping.getDeobfuscatedName() + ";" + methodMapping.getDeobfuscatedDescriptor();
                } else {
                    FieldMapping fieldMapping = mappings.peek().getFieldMapping(obfuscatedName).orElseThrow(() -> new RuntimeException("Unable to find mapping for " + mappings.peek().getObfuscatedName() + "." + obfuscatedName));
                    return fieldMapping.getDeobfuscatedName() + ";" + fieldMapping.getDeobfuscatedSignature().getType().get();
                }
            } else {
                if (mappings.isEmpty()) {
                    mappings.push(intermediaryToHashed.getOrCreateClassMapping(original));
                } else {
                    while (!mappings.peek().hasInnerClassMapping(original) && !mappings.isEmpty()) {
                        mappings.pop();
                    }
                    mappings.push(mappings.peek().getOrCreateInnerClassMapping(original));
                }
                return mappings.peek().getDeobfuscatedName();
            }
        });

        transformed.export(outputPath.resolve(inputPath.getFileName()));
    }

    private static void checkAndCreateTinyCache(String artifact) throws IOException {
        MavenFileDownloader.MavenArtifact mavenArtifact = MavenFileDownloader.MavenArtifact.from(artifact);
        Path cachedFilePath = Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", mavenArtifact.artifactId(), mavenArtifact.version() + ".jar");
        if (!Files.exists(cachedFilePath)) {
            MavenFileDownloader.downloadFile(mavenArtifact, cachedFilePath);
            ZipFile jarFile = new ZipFile(cachedFilePath.toFile());
            ZipEntry tinyFileEntry = jarFile.stream().filter(zipEntry -> zipEntry.getName().endsWith(".tiny")).findFirst().get();
            Files.write(cachedFilePath.getParent().resolve(mavenArtifact.version() + ".tiny"), jarFile.getInputStream(tinyFileEntry).readAllBytes());
        }
    }

    private static <T> T getArgProperty(String name, List<String> args, T defaultValue, Function<String, T> converter) {
        int propertyIndex = args.indexOf(name);
        if (propertyIndex == -1) {
            System.out.println("Property " + name + " was not set. Defaulting to " + defaultValue);
            return defaultValue;
        }
        return converter.apply(args.get(propertyIndex + 1));
    }
}