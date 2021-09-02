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

import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.bombe.type.Type;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;

public class IntermediaryToHashedMojmapConverter {
    public static final String CACHE_DIR = ".intermediaryhashedmojmapconverter";

    public static void main(String[] args) throws IOException {
        List<String> argList = List.of(args);
        System.out.println(argList);

        Path inputPath = getArgProperty("quilt.inputFiles", "-DquiltInputFiles", argList, Path.of(System.getProperty("user.dir")), Path::of);
        Path outputPath = getArgProperty("quilt.outputDirectory", "-DquiltOutputDirectory", argList, Path.of(System.getProperty("user.dir"), "remapped"), Path::of);
        String minecraftVersion = getArgProperty("quilt.minecraft", "-DquiltMinecraft", argList, "1.17", Function.identity());
        Files.createDirectories(outputPath);

        MappingSet intermediaryToHashed = getIntermediaryToHashed(minecraftVersion);

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
        List<String> lines = Files.readAllLines(inputPath);
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            String[] tokens = line.trim().split("\\s+");
            switch (tokens[0]) {
                case "CLASS":
                    if (mappings.isEmpty()) {
                        mappings.push(intermediaryToHashed.getOrCreateClassMapping(tokens[1]));
                    } else {
                        while (!mappings.peek().hasInnerClassMapping(tokens[1]) && !mappings.isEmpty()) {
                            mappings.pop();
                        }
                        mappings.push(mappings.peek().getOrCreateInnerClassMapping(tokens[1]));
                    }

                    line = line.replace(tokens[1], mappings.peek().getDeobfuscatedName());
                    break;
                case "METHOD":
                    String methodDescriptor = tokens.length == 4 ? tokens[3] : tokens[2];
                    MethodMapping methodMapping = mappings.peek().getOrCreateMethodMapping(MethodSignature.of(tokens[1], methodDescriptor));
                    if (!tokens[1].equals("<init>")) {
                        line = line.replace(tokens[1], methodMapping.getDeobfuscatedName());
                        if (tokens.length == 3 && !tokens[1].startsWith("method_")) {
                            line = line.substring(0, line.lastIndexOf(" ") + 1) + tokens[1] + line.substring(line.lastIndexOf(" "));
                        }
                    }
                    line = line.replace(methodDescriptor, methodMapping.getDeobfuscatedDescriptor());
                    break;
                case "FIELD":
                    FieldMapping fieldMapping = mappings.peek().getFieldMapping(tokens[1]).orElseThrow(() -> new RuntimeException("Unable to find mapping for " + mappings.peek().getObfuscatedName() + "." + tokens[1]));
                    line = line.replace(tokens[1], fieldMapping.getDeobfuscatedName());
                    String fieldDescriptor = tokens.length == 4 ? tokens[3] : tokens[2];
                    line = line.substring(0, line.lastIndexOf(" ") + 1) + intermediaryToHashed.deobfuscate(fieldMapping.getType().get()).toString();
                    break;
            }
            lines.set(i, line);
        }
        Files.createDirectories(outputPath);
        Files.write(outputPath.resolve(inputPath.getFileName()), lines);
    }

    public static MappingSet getIntermediaryToHashed(String minecraftVersion) throws IOException {
        checkAndCreateTinyCache(minecraftVersion, "intermediary", "-v2");
        checkAndCreateTinyCache(minecraftVersion, "hashed-mojmap", "");

        MappingSet officialToIntermediary = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), CACHE_DIR, "intermediary", minecraftVersion + ".tiny").toFile()))), "official", "intermediary").read();
        MappingSet officialToHashed = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), CACHE_DIR, "hashed-mojmap", minecraftVersion + ".tiny").toFile()))), "official", "hashed").read();

        return MappingSet.create().merge(officialToIntermediary.reverse()).merge(officialToHashed);
    }

    public static void checkAndCreateTinyCache(String minecraftVersion, String name, String classifier) throws IOException {
        Path cachedFilePath = Path.of(System.getProperty("user.home"), CACHE_DIR, name, minecraftVersion + ".jar");
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

    // We have `name` for backwards compatibility
    public static <T> T getArgProperty(String key, String name, List<String> args, T defaultValue, Function<String, T> converter) {
        String value;
        if (System.getProperties().contains(key)) {
            value = System.getProperty(key);
        } else {
            int propertyIndex = args.indexOf(name);
            if (propertyIndex == -1) {
                System.out.println("Property " + name + " was not set. Defaulting to " + defaultValue);
                return defaultValue;
            }
            value = args.get(propertyIndex + 1);
        }
        return converter.apply(value);
    }
}
