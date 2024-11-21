package au.org.aodn.esindexer.utils;

import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {
    public static File saveResourceToTemp(String resourceName, String filename) {
        String tempDir = System.getProperty("java.io.tmpdir");
        ClassPathResource resource = new ClassPathResource(resourceName);

        File tempFile = new File(tempDir, filename);
        try(InputStream input = resource.getInputStream()) {
            tempFile.deleteOnExit();  // Ensure the file is deleted when the JVM exits

            // Write the InputStream to the temporary file
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tempFile;
    }
}
