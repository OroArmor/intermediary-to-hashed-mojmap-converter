package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.Test;

public class IntermediaryToHashedMojmapConverterTest {
    @Test
    public void testRemapFile() throws IOException, URISyntaxException {
        ((Logger) IntermediaryToHashedMojmapConverter.LOGGER).setLevel(Level.ALL);

        Path inputFile = Path.of("C:\\Users\\elior\\git-projects\\yarn\\mappings");
//        		IntermediaryToHashedMojmapConverterTest.class.getClassLoader()
//						.getResource("org/quiltmc/test_mappings/").toURI());

        if (Files.exists(inputFile)) {
			Path outputDirectory = Path.of("test_output");

			IntermediaryToHashedMojmapConverter.main(new String[]{
					inputFile.toAbsolutePath().toString(),
					"net.fabricmc:intermediary:21w39a:v2",
					"intermediary",
					outputDirectory.toAbsolutePath().toString(),
					"org.quiltmc:hashed-mojmap:21w39a-SNAPSHOT",
					"hashed"
			});
		}
    }
}
