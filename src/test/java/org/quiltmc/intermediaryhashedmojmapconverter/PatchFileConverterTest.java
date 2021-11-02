package org.quiltmc.intermediaryhashedmojmapconverter;

import org.cadixdev.lorenz.MappingSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.quiltmc.intermediaryhashedmojmapconverter.patch.Patch;

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
    private static final Path EXPECTED_MAPPINGS_DIR = RESOURCES_DIR.resolve("expected");
    private static final Path OUTPUTS_DIR;
    private static final String INPUT_ARTIFACT = "net.fabricmc:intermediary:1.17.1";
    private static final String INPUT_NAMESPACE = "intermediary";
    private static final String OUTPUT_ARTIFACT = "org.quiltmc:hashed-mojmap:1.17.1-20210916.004720-4";
    private static final String OUTPUT_NAMESPACE = "hashed";

    private static String inputRepoHead;
    private static MappingSet inputToOutput;

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

    public static List<Arguments> provideConvertPatchFileArguments() throws IOException {
        return Util.walkDirectoryAndCollectFiles(PATCHES_DIR).stream().map(path -> Arguments.of(path, PATCHES_DIR.relativize(path))).collect(Collectors.toList());
    }

    @BeforeAll
    public static void prepare() throws IOException {
        assertTrue(Files.exists(INPUT_REPO_PATH), "The input repository " + INPUT_REPO_PATH + " does not exist");
        inputRepoHead = Util.getRepoHead(INPUT_REPO_PATH);

        // Copy the test mappings to the output directory
        for (Path path : Util.walkDirectoryAndCollectFiles(TEST_MAPPINGS_PATH)) {
            Path outputPath = OUTPUTS_DIR.resolve(TEST_MAPPINGS_PATH.relativize(path));
            if (!Files.exists(outputPath.getParent())) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.copy(path, outputPath);
        }

        inputToOutput = Util.createInputToOutputMappings(INPUT_ARTIFACT, INPUT_NAMESPACE, OUTPUT_ARTIFACT, OUTPUT_NAMESPACE);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("provideConvertPatchFileArguments")
    public void testConvertPatchFile(Path path, Path relative) throws IOException {
        Patch patch = Patch.read(path);
        PatchFileConverter.convertFile(path, inputToOutput, INPUT_REPO_PATH, OUTPUTS_DIR);

        for (String removedFile : patch.getRemovedFiles()) {
            assertFalse(Files.exists(OUTPUTS_DIR.resolve(removedFile)), "The file " + removedFile + " was not removed");
        }
        for (String addedFile : patch.getAddedFiles()) {
            assertTrue(Files.exists(OUTPUTS_DIR.resolve(addedFile)), "The file " + addedFile + " was not added");
        }
        for (String modifiedFile : patch.getModifiedFiles()) {
            Path file = OUTPUTS_DIR.resolve(modifiedFile);
            assertTrue(Files.exists(file), "The file " + modifiedFile + " does not exist");
            Path expectedFile = EXPECTED_MAPPINGS_DIR.resolve(modifiedFile);

            // TODO: Remove
            if (!Files.exists(expectedFile)) {
                System.out.println("Missing expected file: " + expectedFile);
                if (!Files.exists(expectedFile.getParent())) {
                    Files.createDirectories(expectedFile.getParent());
                }
                Files.copy(file, expectedFile);
                continue;
            }

            assertTrue(Files.exists(expectedFile), "The expected file " + modifiedFile + " does not exist");
            List<String> expectedLines = Files.readAllLines(expectedFile);
            String expectedContent = String.join("\n", expectedLines).trim();
            List<String> actualLines = Files.readAllLines(file);
            String actualContent = String.join("\n", actualLines).trim();
            assertEquals(expectedContent, actualContent, "The file " + modifiedFile + " does not match the expected result");
        }
    }

    @AfterAll
    public static void end() throws IOException {
        Util.runGitCommand(INPUT_REPO_PATH, "checkout", inputRepoHead);
    }
}
