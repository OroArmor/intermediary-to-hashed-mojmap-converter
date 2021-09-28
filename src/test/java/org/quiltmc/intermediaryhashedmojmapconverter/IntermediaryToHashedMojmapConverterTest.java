package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;


public class IntermediaryToHashedMojmapConverterTest {
    @Test
    public void testRemapFile() throws IOException, URISyntaxException {
        Path inputFile = Path.of("C:\\Users\\elior\\git-projects\\yarn\\mappings");
//        		IntermediaryToHashedMojmapConverterTest.class.getClassLoader()
//						.getResource("org/quiltmc/test_mappings/").toURI());

        if (Files.exists(inputFile)) {
			Path outputDirectory = Path.of("test_output");

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
