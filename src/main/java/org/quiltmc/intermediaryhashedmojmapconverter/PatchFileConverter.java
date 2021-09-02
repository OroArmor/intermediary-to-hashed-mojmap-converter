package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.lorenz.MappingSet;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.quiltmc.intermediaryhashedmojmapconverter.IntermediaryToHashedMojmapConverter.*;

public class PatchFileConverter {
    public static void main(String[] a) throws IOException {
        List<String> args = List.of(a);
        Path inputPath = getArgProperty("quilt.inputFiles", "-DquiltInputFiles", args, Path.of(System.getProperty("user.dir")), Path::of);
        Path outputPath = getArgProperty("quilt.outputDirectory", "-DquiltOutputDirectory", args, Path.of(System.getProperty("user.dir"), "remapped"), Path::of);
        String minecraftVersion = getArgProperty("quilt.minecraft", "-DquiltMinecraft", args, "1.17", Function.identity());
        Files.createDirectories(outputPath);

        Path yarnDir = getArgProperty("quilt.yarnDir", "-DyarnDir", args, Path.of(System.getProperty("user.dir"), "yarn"), Path::of);
        Path mappingsDir = getArgProperty("quilt.mappingsDir", "-DquiltMappingsDir", args, Path.of(System.getProperty("user.dir"), "quilt-mappings"), Path::of);

        MappingSet intermediaryToHashed = getIntermediaryToHashed(minecraftVersion);

        iterateOverFiles(inputPath, path -> {
            try {
                Path relative = inputPath.relativize(path);
                convertPatchFile(path, outputPath.resolve(relative), intermediaryToHashed, yarnDir, mappingsDir);
            } catch (Exception e) {
                System.err.println("Failed to convert " + path);
                e.printStackTrace();
            }
        });
    }

    private static void convertPatchFile(Path path, Path outputPath, MappingSet intermediaryToHashed, Path yarnDir, Path mappingsDir) throws IOException {
        Patch patch = Patch.read(path);

        // TODO

        Patch newPatch = new Patch(patch.getHeader(), /*diffs*/patch.getDiffs(), patch.getFooter());
        String rawNewPatch = newPatch.export();

        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(rawNewPatch);
        }
    }

    private static void iterateOverFiles(Path inputPath, Consumer<Path> processor) {
        File inputFilePath = inputPath.toFile();
        if (inputFilePath.isDirectory()) {
            for (File file : inputFilePath.listFiles()) {
                iterateOverFiles(file.toPath(), processor);
            }
        } else {
            processor.accept(inputPath);
        }
    }
}
