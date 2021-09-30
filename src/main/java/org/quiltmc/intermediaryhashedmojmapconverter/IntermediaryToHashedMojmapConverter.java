package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
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
import org.jetbrains.annotations.Nullable;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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

    public static MappingSet getIntermediaryToHashed(String minecraftVersion) throws IOException {
        checkAndCreateMappingsCache("net.fabricmc", "intermediary", minecraftVersion, "-v2", "https://maven.fabricmc.net", false);
        checkAndCreateMappingsCache("org.quiltmc", "hashed-mojmap", minecraftVersion, "", "https://maven.quiltmc.org/repository/snapshot", true);

        MappingSet officialToIntermediary = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), CACHE_DIR, "intermediary", minecraftVersion + ".tiny").toFile()))), "official", "intermediary").read();
        MappingSet officialToHashed = new TinyMappingsReader(TinyMappingFactory.load(new BufferedReader(new FileReader(Path.of(System.getProperty("user.home"), CACHE_DIR, "hashed-mojmap", minecraftVersion + ".tiny").toFile()))), "official", "hashed").read();

        return MappingSet.create().merge(officialToIntermediary.reverse()).merge(officialToHashed);
    }

    public static void checkAndCreateMappingsCache(String group, String name, String minecraftVersion, String classifier, String mavenUrl, boolean isSnapshotArtifact) throws IOException {
        Path cachedFilePath = Path.of(System.getProperty("user.home"), CACHE_DIR, name, minecraftVersion + ".tiny");

        if (!cachedFilePath.toFile().exists()) {
            Path cachedJarPath = Path.of(System.getProperty("user.home"), CACHE_DIR, name, minecraftVersion + ".jar");
            File cachedJar = downloadArtifact(group, name, minecraftVersion + (isSnapshotArtifact ? "-SNAPSHOT" : ""), classifier, mavenUrl, cachedJarPath);
            ZipFile jarFile = new ZipFile(cachedJar);
            ZipEntry tinyFileEntry = jarFile.getEntry("hashed/mappings.tiny");
            if (tinyFileEntry == null) {
                tinyFileEntry = jarFile.getEntry("mappings/mappings.tiny");
            }
            Files.write(cachedFilePath, jarFile.getInputStream(tinyFileEntry).readAllBytes());
        }
    }

    public static File downloadArtifact(String group, String name, String version, String classifier, String mavenUrl, @Nullable Path output) throws IOException {
        if (output == null) {
            output = Path.of(System.getProperty("user.home"), CACHE_DIR, group + "-" + name + "-" + version + ".jar");
        }
        File outputFile = output.toFile();
        boolean needsDownloading = !outputFile.exists();

        String urlGroup = group.replace(".", "/");
        boolean isSnapshot = version.endsWith("-SNAPSHOT");
        String snapshotVersion = "";

        // Read maven-metadata.xml to get the latest version
        if (isSnapshot) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
                Document document = documentBuilder.parse(mavenUrl + "/" + urlGroup + "/" + name + "/" + version + "/maven-metadata.xml");
                document.getDocumentElement().normalize();

                Element versioningElement = (Element) document.getElementsByTagName("versioning").item(0);
                Element snapshotElement = (Element) versioningElement.getElementsByTagName("snapshot").item(0);
                String timestamp = snapshotElement.getElementsByTagName("timestamp").item(0).getTextContent();
                String buildNumber = snapshotElement.getElementsByTagName("buildNumber").item(0).getTextContent();

                snapshotVersion = version.replace("-SNAPSHOT", "") + "-" + timestamp + "-" + buildNumber;
                Date snapshotDate = new SimpleDateFormat("yyyyMMdd.HHmmss").parse(timestamp);
                needsDownloading = !outputFile.exists() || new Date(outputFile.lastModified()).before(snapshotDate);
            } catch (ParserConfigurationException | ParseException | SAXException e) {
                throw new RuntimeException("Failed to find and verify the latest snapshot for " + group + ":" + name + ":" + version);
            }
        }

        if (needsDownloading) {
            String url = mavenUrl + "/" + urlGroup + "/" + name + "/" + version + "/";
            if (isSnapshot) {
                url += name + "-" + snapshotVersion + classifier + ".jar";
            } else {
                url += name + "-" + version + classifier + ".jar";
            }
            URL downloadableUrl = new URL(url);
            System.out.println("Downloading artifact from " + url);
            Files.createDirectories(output.getParent());
            Files.createFile(output);
            Files.write(output, downloadableUrl.openStream().readAllBytes());
        }

        return outputFile;
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
