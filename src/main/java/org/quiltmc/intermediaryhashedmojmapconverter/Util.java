package org.quiltmc.intermediaryhashedmojmapconverter;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Util {
    public static final String CACHE_DIR = ".intermediaryhashedmojmapconverter";

    public static Path checkAndCreateTinyCache(String artifact) throws IOException {
        MavenFileDownloader.MavenArtifact mavenArtifact = MavenFileDownloader.MavenArtifact.from(artifact);
        Path cachedJarFilePath = Path.of(System.getProperty("user.home"), CACHE_DIR, mavenArtifact.artifactId(), mavenArtifact.version() + ".jar");
        Path cachedFilePath = cachedJarFilePath.getParent().resolve(mavenArtifact.version() + ".tiny");
        if (!Files.exists(cachedJarFilePath)) {
            MavenFileDownloader.downloadFile(mavenArtifact, cachedJarFilePath);
            ZipFile jarFile = new ZipFile(cachedJarFilePath.toFile());
            ZipEntry tinyFileEntry = jarFile.stream().filter(zipEntry -> zipEntry.getName().endsWith(".tiny")).findFirst().get();
            Files.write(cachedFilePath, jarFile.getInputStream(tinyFileEntry).readAllBytes());
        }
        return cachedFilePath;
    }

    public static MappingSet createInputToOutputMappings(String inputArtifact, String inputNamespace, String outputArtifact, String outputNamespace) throws IOException {
        Path inputTinyFile = Util.checkAndCreateTinyCache(inputArtifact);
        Path outputTinyFile = Util.checkAndCreateTinyCache(outputArtifact);

        MappingSet officialToInput = new TinyMappingsReader(TinyMappingFactory.load(Files.newBufferedReader(inputTinyFile)), "official", inputNamespace).read();
        MappingSet officialToOutput = new TinyMappingsReader(TinyMappingFactory.load(Files.newBufferedReader(outputTinyFile)), "official", outputNamespace).read();

        return MappingSet.create().merge(officialToInput.reverse()).merge(officialToOutput);
    }

    public static String remapObfuscated(EnigmaMapping.Type type, String original, boolean signature, boolean isMethod, MappingSet inputToOutput, Deque<ClassMapping<?, ?>> mappings) {
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
    }

    public static List<Path> walkDirectoryAndCollectFiles(Path directory) throws IOException {
        return Files.walk(directory).filter(path -> !Files.isDirectory(path)).collect(Collectors.toList());
    }

    public static String getUncommittedChanges(Path repository) throws IOException {
        return runGitCommand(repository, "diff-index", "HEAD", "--");
    }

    public static String getRepoHead(Path repository) throws IOException {
        String headPointer = "HEAD -> ";
        String[] headInfo = runGitCommand(repository, "log", "HEAD", "--format=\"%H;%D\"", "-n", "1").split(";");
        return headInfo[1].contains(headPointer)
                // HEAD is pointing to a branch
                ? headInfo[1].substring(headInfo[1].indexOf(headPointer) + headPointer.length(), headInfo[1].indexOf(","))
                // HEAD is detached
                : headInfo[0];
    }

    public static String runGitCommand(Path directory, String... args) throws IOException {
        String[] command = new String[args.length + 1];
        System.arraycopy(args, 0, command, 1, args.length);
        command[0] = "git";
        ProcessBuilder processBuilder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        Process process = processBuilder.start();

        String out;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            process.waitFor();
            out = reader.lines().collect(Collectors.joining("\n"));
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to wait for git command '" + Arrays.stream(command).skip(1).collect(Collectors.joining(" ")) + "'", e);
        }

        return out;
    }
}
