package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class IntermediaryToHashedMojmapConverterTest {
    @Test
    public void testRemapFile() throws IOException {
        Path inputFile = Path.of("C:\\Users\\elior\\git-projects\\quilt-mappings\\mappings");

        if (Files.exists(inputFile)) {
			Path outputDirectory = Path.of("C:\\Users\\elior\\git-projects\\quilt-mappings\\mappings");

			IntermediaryToHashedMojmapConverter.main(new String[]{
					inputFile.toAbsolutePath().toString(),
					"org.quiltmc:hashed-mojmap:1.17.1-SNAPSHOT",
					"hashed",
					outputDirectory.toAbsolutePath().toString(),
					"org.quiltmc:hashed:1.17.1-SNAPSHOT",
					"hashed"
			});
		}
    }
}
