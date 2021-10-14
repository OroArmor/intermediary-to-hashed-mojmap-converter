package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.lorenz.MappingSet;
import org.junit.jupiter.api.AfterAll;
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
    private static final Path INPUT_REPO_PATH = Path.of("C:\\Users\\Martin\\repo\\FabricMC\\yarn");
    private static final Path TEST_MAPPINGS_PATH = TestUtil.getResource("org/quiltmc/test_mappings");
    private static final Path RESOURCES_DIR = TestUtil.getResource("org/quiltmc/patchconverter");
    private static final Path PATCHES_DIR = RESOURCES_DIR.resolve("patches");
    private static final Path EXPECTED_PATCHES_DIR = RESOURCES_DIR.resolve("expected");
    private static final Path OUTPUTS_DIR;
    private final MappingSet inputToOutput = Util.createInputToOutputMappings("net.fabricmc:intermediary:1.17.1", "intermediary", "org.quiltmc:hashed-mojmap:1.17.1-20210916.004720-4", "hashed");

    private static String inputRepoHead;

    static {
        Path outputsDir;
        try {
            outputsDir = Files.createTempDirectory("patchfileconvertertest");
        } catch (IOException e) {
            e.printStackTrace();
            outputsDir = null;
            System.exit(-1);
        }
        OUTPUTS_DIR = outputsDir;
    }

    public PatchFileConverterTest() throws IOException {
    }

    public static List<Arguments> provideConvertPatchFileArguments() throws IOException {
        return Files.walk(PATCHES_DIR).filter(path -> !Files.isDirectory(path)).map(path -> Arguments.of(path, PATCHES_DIR.relativize(path))).collect(Collectors.toList());
    }

    @BeforeAll
    public static void prepare() throws IOException {
        assertTrue(Files.exists(INPUT_REPO_PATH), "The input repository " + INPUT_REPO_PATH + " does not exist");
        inputRepoHead = Util.getRepoHead(INPUT_REPO_PATH);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideConvertPatchFileArguments")
    public void testConvertPatchFile(Path path, Path relative) throws IOException {
        Path expectedPatchPath = EXPECTED_PATCHES_DIR.resolve(relative);
        assertTrue(Files.exists(expectedPatchPath) && !Files.isDirectory(expectedPatchPath), "Expected file " + relative + " does not exist");

        Path outputPath = OUTPUTS_DIR.resolve(relative);
        PatchFileConverter.convertPatchFile(path, outputPath, inputToOutput, INPUT_REPO_PATH, TEST_MAPPINGS_PATH);
        assertTrue(Files.exists(outputPath) && !Files.isDirectory(outputPath), "Output file " + outputPath + " does not exist!");

        List<String> expectedLines = Files.readAllLines(expectedPatchPath);
        String expectedContent = String.join("\n", expectedLines).trim();
        List<String> actualLines = Files.readAllLines(outputPath);
        String actualContent = String.join("\n", actualLines).trim();
        assertEquals(expectedContent, actualContent);
    }

    @AfterAll
    public static void end() throws IOException {
        Util.runGitCommand(INPUT_REPO_PATH, "checkout", inputRepoHead);
    }
}
