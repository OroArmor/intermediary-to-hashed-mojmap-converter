package org.quiltmc.intermediaryhashedmojmapconverter;

import net.fabricmc.lorenztiny.TinyMappingsReader;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import org.cadixdev.lorenz.MappingSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Util {
    public static final String CACHE_DIR = ".intermediaryhashedmojmapconverter";
    private static final String GIT_PATH;

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

    static String runGitCommand(Path directory, String... args) throws IOException {
        String[] command = new String[args.length + 1];
        System.arraycopy(args, 0, command, 1, args.length);
        command[0] = GIT_PATH;
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

    static {
        String gitPath = null;
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, "git.exe");
            if (file.isFile() && file.canExecute()) {
                gitPath = file.getAbsolutePath();
            } else {
                file = new File(dirname, "git");
                if (file.isFile() && file.canExecute()) {
                    gitPath = file.getAbsolutePath();
                }
            }
        }

        if (gitPath == null) {
            throw new RuntimeException("No git executable found in PATH");
        }
        GIT_PATH = gitPath;
    }
}
