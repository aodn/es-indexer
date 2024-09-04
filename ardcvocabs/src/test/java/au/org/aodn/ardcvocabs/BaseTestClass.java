package au.org.aodn.ardcvocabs;

import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class BaseTestClass {
    public static String readResourceFile(String path) throws IOException {
        File f = ResourceUtils.getFile(path);
        return new String(Files.readAllBytes(f.toPath()));
    }
}
