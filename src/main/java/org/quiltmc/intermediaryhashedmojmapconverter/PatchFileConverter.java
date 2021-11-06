package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Diff;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PatchFileConverter {
    public static void main(String[] args) throws IOException {
        if (args.length != 7) {
            System.err.println("Usage is <patchespath> <inputmappings> <inputnamespace> <outputpath> <outputmappings> <outputnamespace> <inputrepo>");
            System.exit(-1);
        }

        Path patchesPath = Path.of(args[0]);
        Path outputPath = Path.of(args[3]);

        MappingSet inputToOutput = Util.createInputToOutputMappings(args[1], args[2], args[4], args[5]);

        Path inputRepo = Path.of(args[6]);

        // Check for uncommitted changes in the input repo
        String uncommittedInputRepoChanges = Util.getUncommittedChanges(inputRepo);
        if (!uncommittedInputRepoChanges.isEmpty()) {
            System.err.println("You have uncommitted changes in the input repository. Please commit or stash them before continuing\n"
                    + uncommittedInputRepoChanges);
            System.exit(-1);
        }

        // Get what HEAD is pointing to
        String inputRepoHead = Util.getRepoHead(inputRepo);

        Map<Path, String> modifiedFiles = new HashMap<>();
        List<Path> modifiedPaths = new ArrayList<>();
        List<Path> patchFiles = Util.walkDirectoryAndCollectFiles(patchesPath);

        for (Path patchFile : patchFiles) {
            try {
                System.out.println("Converting " + patchFile);
                PatchFileConverter.convertFile(patchFile, inputToOutput, inputRepo, outputPath, modifiedPaths, modifiedFiles);
            } catch (Throwable t) {
                System.err.println("Failed to convert " + patchFile);
                t.printStackTrace();
            }
        }

        Set<Path> uniqueModifiedPaths = new HashSet<>();
        Set<Path> duplicatedModifiedPaths = new HashSet<>();
        for (Path modifiedPath : modifiedPaths) {
            if (!uniqueModifiedPaths.contains(modifiedPath)) {
                uniqueModifiedPaths.add(modifiedPath);
            } else {
                duplicatedModifiedPaths.add(modifiedPath);
            }
        }

        if (!duplicatedModifiedPaths.isEmpty()) {
            System.err.println("The following files were modified more than once. Only the last version will be kept");
            for (Path modifiedPath : duplicatedModifiedPaths) {
                System.err.println("\t" + modifiedPath);
            }
        }

        System.out.println("Applying modified files");
        ExecutorService executor = Executors.newFixedThreadPool(32);
        Set<Path> remaining = new HashSet<>(uniqueModifiedPaths);

        for (Path modifiedPath : uniqueModifiedPaths) {
            executor.execute(() -> {
                try {
                    applyModifiedFile(outputPath, modifiedPath, modifiedFiles.get(modifiedPath));
                    remaining.remove(modifiedPath);
                } catch (Exception e) {
                    System.err.println("Failed to apply modified file " + modifiedPath);
                    e.printStackTrace();
                }
            });
        }

        try {
            boolean successful = executor.awaitTermination(100, TimeUnit.SECONDS);
            if (!successful) {
                System.err.println("Executor failed to stop. " + remaining.size() + " file(s) remaining");
                remaining.forEach(System.out::println);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Reset the input repo to how it was before
        Util.runGitCommand(inputRepo, "checkout", inputRepoHead);
    }

    public static void convertFile(Path patchFile, MappingSet inputToOutput, Path inputRepo, Path outputPath, List<Path> modifiedPaths, Map<Path, String> modifiedFiles) throws IOException {
        Patch patch = Patch.read(patchFile);

        for (Diff diff : patch.getDiffs()) {
            boolean renamedFile = !diff.getSrc().equals(diff.getDst());
            boolean newFile = diff.getSrc().equals("/dev/null");
            boolean deletedFile = diff.getDst().equals("/dev/null");

            Path srcPath = Path.of(diff.getSrc());
            Path dstPath = Path.of(diff.getDst());

            if (newFile && deletedFile) {
                throw new IllegalStateException("Patch file " + patchFile + " contains a diff pointing to a null file");
            } else if (deletedFile) {
                if (!diff.getSrc().endsWith(".mapping")) {
                    continue;
                }
                modifiedPaths.add(srcPath);
                modifiedFiles.put(srcPath, null);
            } else if (newFile) {
                if (!diff.getDst().endsWith(".mapping")) {
                    continue;
                }
                Path inputFile = inputRepo.resolve(dstPath);

                // Checkout patch commit in the input repo
                String fromLine = patch.getHeader().get(0);
                String commitFrom = fromLine.substring(fromLine.lastIndexOf("From ") + 5, fromLine.lastIndexOf(" Mon Sep 17 00:00:00 2001"));
                String checkoutOutput = Util.runGitCommand(inputRepo, "checkout", commitFrom);
                if (checkoutOutput.contains("error:")) {
                    throw new RuntimeException("There was an error checking out the patch commit for " + patchFile + "\n" + checkoutOutput);
                }

                EnigmaFile remappedFile = readAndRemapFile(inputFile, inputToOutput);
                modifiedPaths.add(dstPath);
                modifiedFiles.put(dstPath, remappedFile.toString());
            } else {
                if (!diff.getSrc().endsWith(".mapping")) {
                    continue;
                }
                Path inputSrcFile = inputRepo.resolve(srcPath);
                Path outputSrcFile = outputPath.resolve(srcPath);
                if (!Files.exists(outputSrcFile)) {
                    System.err.println("File " + srcPath + " does not exist in the output repository");
                    continue;
                }

                // Checkout commit before the patch in the input repo
                String fromLine = patch.getHeader().get(0);
                String commitFrom = fromLine.substring(fromLine.lastIndexOf("From ") + 5, fromLine.lastIndexOf(" Mon Sep 17 00:00:00 2001"));
                String checkoutOutput = Util.runGitCommand(inputRepo, "checkout", commitFrom + "^");
                if (checkoutOutput.contains("error:")) {
                    throw new RuntimeException("There was an error checking out the commit previous to the patch " + patchFile + "\n" + checkoutOutput);
                }

                // Check the input and output files have the same content
                EnigmaFile remappedInputSrcEnigmaFile = readAndRemapFile(inputSrcFile, inputToOutput);
                if (!Files.readString(outputSrcFile).replace("\r\n", "\n").equals(remappedInputSrcEnigmaFile.toString())) {
                    System.out.println("WARNING: The output repository file " + srcPath + " does not have the same content as the input repository file. The conversion will add/remove some mappings");
                }

                List<String> inputSrcFileLines = Files.readAllLines(inputSrcFile);
                List<String> inputDstFileLines = Patch.applyDiff(inputSrcFileLines, diff);

                EnigmaFile remappedInputDstEnigmaFile = readAndRemapFileLines(inputDstFileLines, inputToOutput);
                if (renamedFile) {
                    modifiedPaths.add(srcPath);
                    modifiedFiles.put(srcPath, null);
                }
                modifiedPaths.add(dstPath);
                modifiedFiles.put(dstPath, remappedInputDstEnigmaFile.toString());
            }
        }
    }

    private static EnigmaFile readAndRemapFile(Path file, MappingSet inputToOutput) throws IOException {
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();
        return EnigmaReader.readFile(file, (type, original, signature, isMethod) -> {
            try {
                return Util.remapObfuscated(type, original, signature, isMethod, inputToOutput, mappings);
            } catch (Exception e) {
                System.err.println("Error finding mapping for " + original + " with type " + type + " in file " + file);
                return original;
            }
        });
    }

    private static EnigmaFile readAndRemapFileLines(List<String> lines, MappingSet inputToOutput) {
        Deque<ClassMapping<?, ?>> mappings = new ArrayDeque<>();
        return EnigmaReader.readLines(lines, (type, original, signature, isMethod) -> {
            try {
                return Util.remapObfuscated(type, original, signature, isMethod, inputToOutput, mappings);
            } catch (Exception e) {
                System.err.println("Error finding mapping for " + original + " with type " + type);
                return original;
            }
        });
    }

    protected static void applyModifiedFiles(Path outputPath, Map<Path, String> modifiedFiles) throws IOException {
        for (Map.Entry<Path, String> entry : modifiedFiles.entrySet()) {
            applyModifiedFile(outputPath, entry.getKey(), entry.getValue());
        }
    }

    private static void applyModifiedFile(Path outputPath, Path path, String content) throws IOException {
        if (content == null) {
            Files.deleteIfExists(outputPath.resolve(path));
        } else {
            if (!Files.exists(outputPath.resolve(path.getParent()))) {
                Files.createDirectories(outputPath.resolve(path.getParent()));
            }
            Files.writeString(outputPath.resolve(path), content);
        }
    }
}
