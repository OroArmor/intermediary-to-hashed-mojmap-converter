package org.quiltmc.intermediaryhashedmojmapconverter.enigma;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.quiltmc.intermediaryhashedmojmapconverter.TestUtil;
import org.quiltmc.intermediaryhashedmojmapconverter.Util;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;

import static org.junit.jupiter.api.Assertions.*;

public class EnigmaReaderTest {
    @Test
    public void testReader() throws IOException {
        List<Path> files = Util.walkDirectoryAndCollectFiles(TestUtil.getResource("org/quiltmc/test_mappings"));
        for (Path path : files) {
            EnigmaFile file = EnigmaReader.readFile(path);
            String expected = Files.readString(path).replace("\r\n", "\n").trim();
            String actual = file.toString().trim();
            assertEquals(expected, actual);
        }
    }
}
