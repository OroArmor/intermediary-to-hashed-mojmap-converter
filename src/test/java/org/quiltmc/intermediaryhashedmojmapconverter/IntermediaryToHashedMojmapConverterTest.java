package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class IntermediaryToHashedMojmapConverterTest {
    @Test
    public void testRemapFile() throws IOException {
        Path inputFile = Path.of("test_output");

        if (Files.exists(inputFile)) {
			Path outputDirectory = Path.of("test_output2");

			IntermediaryToHashedMojmapConverter.main(new String[]{
					inputFile.toAbsolutePath().toString(),
					"org.quiltmc:hashed:21w41a",
					"hashed",
					outputDirectory.toAbsolutePath().toString(),
					"org.quiltmc:hashed:21w41a-SNAPSHOT",
					"hashed"
			});
		}
    }
}
