package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.bombe.type.signature.MethodSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Diff;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PatchFileConverter {
    public static void main(String[] args) throws IOException {
        if (args.length != 8) {
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

        List<Path> patchFiles = Util.walkDirectoryAndCollectFiles(patchesPath);

        Set<Path> inProgress = new HashSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(8);

        for (Path patchFile : patchFiles) {
            inProgress.add(patchFile);
            executor.execute(() -> {
                try {
                    PatchFileConverter.convertFile(patchFile, inputToOutput, inputRepo, outputPath);
                } catch (Throwable t) {
                    System.err.println("Failed to convert " + patchFile);
                    t.printStackTrace();
                }
                inProgress.remove(patchFile);
            });
        }

        try {
            boolean successful = executor.awaitTermination(100, TimeUnit.SECONDS);
            if (!successful) {
                System.err.println("Executor failed to stop.");
                inProgress.forEach(System.out::println);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Reset the input repo to how it was before
        Util.runGitCommand(inputRepo, "checkout", inputRepoHead);
    }

    public static void convertFile(Path patchFile, MappingSet inputToOutput, Path inputRepo, Path outputPath) throws IOException {
        Patch patch = Patch.read(patchFile);

        for (Diff diff : patch.getDiffs()) {
            boolean renamedFile = !diff.getSrc().equals(diff.getDst());
            boolean newFile = diff.getSrc().equals("/dev/null");
            boolean deletedFile = diff.getDst().equals("/dev/null");

            if (newFile && deletedFile) {
                throw new IllegalStateException("Patch file " + patchFile + " contains a diff pointing to a null file");
            } else if (deletedFile) {
                // Delete the file
                Files.deleteIfExists(outputPath.resolve(diff.getSrc()));
            } else if (newFile) {
                Path inputFile = inputRepo.resolve(diff.getDst());

                // Checkout patch commit in the input repo
                String fromLine = patch.getHeader().get(0);
                String commitFrom = fromLine.substring(fromLine.lastIndexOf("From ") + 5, fromLine.lastIndexOf(" Mon Sep 17 00:00:00 2001"));
                String checkoutOutput = Util.runGitCommand(inputRepo, "checkout", commitFrom);
                if (checkoutOutput.contains("error:")) {
                    throw new RuntimeException("There was an error checking out the patch commit for " + patchFile + "\n" + checkoutOutput);
                }

                EnigmaFile remappedFile = readAndRemapFile(inputFile, inputToOutput);
                remappedFile.export(outputPath.resolve(diff.getDst()));
            } else {
                Path inputSrcFile = inputRepo.resolve(diff.getSrc());
                Path outputSrcFile = outputPath.resolve(diff.getSrc());

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
                    System.out.println("WARNING: The output repository file " + diff.getSrc() + " does not have the same content as the input repository file. The conversion will add/remove some mappings");
                }

                List<String> inputSrcFileLines = Files.readAllLines(inputSrcFile);
                List<String> inputDstFileLines = Patch.applyDiff(inputSrcFileLines, diff);
                Path outputDstFile = outputPath.resolve(diff.getDst());

                EnigmaFile remappedInputDstEnigmaFile = readAndRemapFileLines(inputDstFileLines, inputToOutput);
                if (renamedFile) {
                    Files.deleteIfExists(outputSrcFile);
                }
                remappedInputDstEnigmaFile.export(outputDstFile);
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
}
