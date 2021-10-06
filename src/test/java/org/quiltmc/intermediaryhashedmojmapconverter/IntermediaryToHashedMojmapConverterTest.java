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
					"-DquiltInputFiles",
					inputFile.toAbsolutePath().toString(),
					"-DquiltOutputDirectory",
					outputDirectory.toAbsolutePath().toString(),
					"-DquiltMinecraft",
					"1.17.1"
			});
		}

//		MavenFileDownloader.downloadFile(MavenFileDownloader.MavenArtifact.from("net.fabricmc:intermediary:21w39a:v2"), Path.of(".", "output.jar"));
//        MavenFileDownloader.downloadFile(MavenFileDownloader.MavenArtifact.from("org.quiltmc:hashed-mojmap:21w39a-SNAPSHOT"), Path.of(".", "output2.jar"));
    }
}
