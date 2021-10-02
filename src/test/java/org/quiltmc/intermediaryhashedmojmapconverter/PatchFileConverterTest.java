package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.lorenz.MappingSet;
import org.junit.jupiter.api.Test;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.PatchTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class PatchFileConverterTest {
    private static final Path YARN_PATH = Path.of("C:\\Users\\Martin\\repo\\FabricMC\\yarn");
    private static final Path QM_PATH = Path.of("C:\\Users\\Martin\\repo\\QuiltMC\\quilt-mappings");

    @Test
    public void testConvertPatchFile() throws IOException {
        Path resourcesDir = new File(PatchTest.class.getClassLoader().getResource("org/quiltmc/patchconverter").getPath()).toPath();
        Path patchesDir = resourcesDir.resolve("patches");
        Path expectedPatchesDir = resourcesDir.resolve("expected");
        Path outputsDir = Files.createTempDirectory("patchfileconvertertest");

        MappingSet intermediaryToHashed = IntermediaryToHashedMojmapConverter.getIntermediaryToHashed("1.17.1");

        List<Path> patches = Files.walk(patchesDir).filter(path -> !Files.isDirectory(path)).collect(Collectors.toList());
        for (Path path : patches) {
            Path expectedPatchPath = expectedPatchesDir.resolve(patchesDir.relativize(path));
            assertTrue(Files.exists(expectedPatchPath) && !Files.isDirectory(expectedPatchPath), "Expected file " + patchesDir.relativize(path) + " does not exist");

            Path outputPath = outputsDir.resolve(patchesDir.relativize(path));
            PatchFileConverter.convertPatchFile(path, outputPath, intermediaryToHashed, YARN_PATH, QM_PATH);

            List<String> expectedLines = Files.readAllLines(expectedPatchPath);
            String expectedContent = String.join("\n", expectedLines);
            List<String> actualLines = Files.readAllLines(outputPath);
            String actualContent = String.join("\n", actualLines);
            assertEquals(expectedContent, actualContent);
        }
    }
}
