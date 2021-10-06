package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.lorenz.MappingSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class PatchFileConverterTest {
    private static final Path YARN_PATH = Path.of("C:\\Users\\Martin\\repo\\FabricMC\\yarn");
    private static final Path TEST_MAPPINGS_PATH = TestUtil.getResource("org/quiltmc/test_mappings");
    private static final Path RESOURCES_DIR = TestUtil.getResource("org/quiltmc/patchconverter");
    private static final Path PATCHES_DIR = RESOURCES_DIR.resolve("patches");
    private static final Path EXPECTED_PATCHES_DIR = RESOURCES_DIR.resolve("expected");
    private final Path outputsDir = Files.createTempDirectory("patchfileconvertertest");
    private final MappingSet intermediaryToHashed = IntermediaryToHashedMojmapConverter.getIntermediaryToHashed("1.17.1");

    public PatchFileConverterTest() throws IOException {
    }

    public static List<Arguments> provideConvertPatchFileArguments() throws IOException {
        return Files.walk(PATCHES_DIR).filter(path -> !Files.isDirectory(path)).map(path -> Arguments.of(path, PATCHES_DIR.relativize(path))).collect(Collectors.toList());
    }
    
    @BeforeAll
    public static void checkDirectories() {
        assertTrue(Files.exists(YARN_PATH), "The yarn repository " + YARN_PATH + " does not exist");
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideConvertPatchFileArguments")
    public void testConvertPatchFile(Path path, Path relative) throws IOException {
        Path expectedPatchPath = EXPECTED_PATCHES_DIR.resolve(relative);
        assertTrue(Files.exists(expectedPatchPath) && !Files.isDirectory(expectedPatchPath), "Expected file " + relative + " does not exist");

        Path outputPath = outputsDir.resolve(relative);
        PatchFileConverter.convertPatchFile(path, outputPath, intermediaryToHashed, YARN_PATH, TEST_MAPPINGS_PATH);

        List<String> expectedLines = Files.readAllLines(expectedPatchPath);
        String expectedContent = String.join("\n", expectedLines).trim();
        List<String> actualLines = Files.readAllLines(outputPath);
        String actualContent = String.join("\n", actualLines).trim();
        assertEquals(expectedContent, actualContent);
    }
}
