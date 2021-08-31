package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;


public class IntermediaryToHashedMojmapConverterTest {
    @Test
    public void testRemapFile() throws IOException, URISyntaxException {
        Path inputFile = Path.of("D:\\UserData\\Eli Orona\\GitHub\\yarn\\mappings");

        if (Files.exists(inputFile)) {
			Path outputDirectory = Path.of(".", "test_output");

			IntermediaryToHashedMojmapConverter.main(new String[]{
					"-DquiltInputFiles",
					inputFile.toAbsolutePath().toString(),
					"-DquiltOutputDirectory",
					outputDirectory.toAbsolutePath().toString(),
					"-DquiltMinecraft",
					"1.17.1"
			});
		}
    }
}
