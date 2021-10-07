package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class IntermediaryToHashedMojmapConverterTest {
    @Test
    public void testRemapFile() throws IOException {
        Path inputFile = Path.of("C:\\Users\\elior\\git-projects\\yarn\\mappings");

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
