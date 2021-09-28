package org.quiltmc.intermediaryhashedmojmapconverter.enigma;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaFile;
import org.quiltmc.intermediaryhashedmojmapconverter.engima.EnigmaReader;

public class EnigmaReaderTest {
    @Test
    public void testReader() throws URISyntaxException, IOException {
        EnigmaFile file = EnigmaReader.readFile(Path.of(EnigmaReaderTest.class.getClassLoader().getResource("org/quiltmc/test_mappings/ArmorItem.mapping").toURI()));
        System.out.println(file);
        file = EnigmaReader.readFile(Path.of(EnigmaReaderTest.class.getClassLoader().getResource("org/quiltmc/test_mappings/MinecraftClient.mapping").toURI()));
        System.out.println(file);
    }
}
