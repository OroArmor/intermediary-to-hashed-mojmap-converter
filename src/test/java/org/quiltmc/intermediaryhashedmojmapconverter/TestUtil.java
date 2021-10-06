package org.quiltmc.intermediaryhashedmojmapconverter;

import java.io.File;
import java.nio.file.Path;

public class TestUtil {
    public static Path getResource(String name) {
        return new File(TestUtil.class.getClassLoader().getResource(name).getPath()).toPath();
    }
}
