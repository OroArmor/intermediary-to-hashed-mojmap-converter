package org.quiltmc.intermediaryhashedmojmapconverter.patch;

import org.junit.jupiter.api.Test;
import org.quiltmc.intermediaryhashedmojmapconverter.TestUtil;
import org.quiltmc.intermediaryhashedmojmapconverter.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PatchTest {
    @Test
    public void testApply() throws IOException {
        Path resourcesDir = TestUtil.getResource("org/quiltmc/patch");
        Path patchesDir = resourcesDir.resolve("patches");
        Path filesDir = resourcesDir.resolve("files");
        Path expectedFilesDir = resourcesDir.resolve("expected_files");

        List<Path> patches = Util.walkDirectoryAndCollectFiles(patchesDir);
        for (Path path : patches) {
            Patch patch = Patch.read(path);
            for (Diff diff : patch.getDiffs()) {
                Path inputFile = filesDir.resolve(diff.getFrom());
                assertTrue(Files.exists(inputFile) && !Files.isDirectory(inputFile), "Input file " + diff.getFrom() + " does not exist");
                Path expectedFile = expectedFilesDir.resolve(diff.getTo());
                assertTrue(Files.exists(expectedFile) && !Files.isDirectory(expectedFile), "Expected file " + diff.getTo() + " does not exist");
                List<String> fileLines = Files.readAllLines(inputFile);
                List<String> appliedFileLines = Patch.applyDiff(fileLines, diff);
                String actualContent = String.join("\n", appliedFileLines);
                String expectedContent = Files.readString(expectedFile).trim().replace("\r\n", "\n");
                assertEquals(expectedContent, actualContent);
            }
        }
    }
}
