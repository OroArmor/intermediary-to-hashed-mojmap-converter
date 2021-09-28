package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
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
        List<String> argList = List.of(args);
        System.out.println(argList);

        Path inputPath = getArgProperty("-DquiltInputFiles", argList, Path.of(System.getProperty("user.dir")), Path::of);
        Path outputPath = getArgProperty("-DquiltOutputDirectory", argList, Path.of(System.getProperty("user.dir"), "remapped"), Path::of);
        String minecraftVersion = getArgProperty("-DquiltMinecraft", argList, "1.17", Function.identity());
        Files.createDirectories(outputPath);

        checkAndCreateTinyCache(minecraftVersion, "intermediary", "-v2");
        checkAndCreateTinyCache(minecraftVersion, "hashed-mojmap", "");

        MappingSet officialToIntermediary = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", "intermediary", minecraftVersion + ".tiny").toFile()))), "official", "intermediary").read();
        MappingSet officialToHashed = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", "hashed-mojmap", minecraftVersion + ".tiny").toFile()))), "official", "hashed").read();

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

    private static void checkAndCreateTinyCache(String minecraftVersion, String name, String classifier) throws IOException {
        Path cachedFilePath = Path.of(System.getProperty("user.home"), ".intermediaryhashedmojmapconverter", name, minecraftVersion + ".jar");
        if (!cachedFilePath.toFile().exists()) {
            URL downloadableMavenURL = new URL("https://maven.quiltmc.org/repository/release/org/quiltmc/" + name + "/" + minecraftVersion + "/" + name + "-" + minecraftVersion + classifier + ".jar");
            Files.createDirectories(cachedFilePath.getParent());
            Files.createFile(cachedFilePath);
            Files.write(cachedFilePath, downloadableMavenURL.openStream().readAllBytes());
            ZipFile jarFile = new ZipFile(cachedFilePath.toFile());
            ZipEntry tinyFileEntry = jarFile.getEntry((name.equals("hashed-mojmap") ? "hashed" : "mappings") + "/mappings.tiny");
            Files.write(cachedFilePath.getParent().resolve(minecraftVersion + ".tiny"), jarFile.getInputStream(tinyFileEntry).readAllBytes());
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