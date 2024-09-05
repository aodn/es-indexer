package au.org.aodn.ardcvocabs;

import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

public class BaseTestClass {
    public static String readResourceFile(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes());
    }
}
