package au.org.aodn.ardcvocabs;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A utility tools to download json from ARDC, it is not mean to be generic and likely one off so please
 * make changes if you need to reuse it
 */
public class PageDownloader {

    // private static final String BASE_URL = "https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-platform-vocabulary/version-6-1/concept.json?_page=";
    //private static final String BASE_URL = "https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-platform-category-vocabulary/version-1-2/concept.json?_page=";
    // private static final String BASE_URL = "https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-organisation-category-vocabulary/version-2-5/concept.json?_page=";
    private static final String BASE_URL = "https://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-organisation-vocabulary/version-2-5/concept.json?_page=";
    private static final int MAX_PAGES = 100;  // Set the maximum number of pages you expect (adjust as needed)
    private static final String OUTPUT_DIR = "/tmp";  // Change to the directory where you want to save files

    public static void main(String[] args) {

        // Start downloading each page
        for (int i = 51; i <= MAX_PAGES; i++) {
            String pageUrl = BASE_URL + i;
            String fileName = "vocab" + i + ".json";
            try {
                System.out.println("Downloading page " + i + " from " + pageUrl);
                boolean success = downloadAndSaveResource(pageUrl, OUTPUT_DIR + File.separator + fileName);

                if (!success) {
                    // Break the loop if the page doesn't exist or returns an error
                    System.out.println("No more pages or an error occurred. Stopping.");
                    break;
                }
            } catch (Exception e) {
                System.out.println("Failed to download page " + i);
                e.printStackTrace();
                break;
            }
        }
    }

    private static boolean downloadAndSaveResource(String urlString, String filePath) {
        HttpURLConnection connection = null;
        try {
            // Create URL and open connection
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Check if the response code is 200 (OK)
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Input stream to read the file content
                InputStream inputStream = connection.getInputStream();

                // Write the response to the output file
                try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                System.out.println("Downloaded and saved: " + filePath);
                return true;  // Successful download
            } else {
                System.out.println("Failed to download resource: " + urlString + " (Response code: " + responseCode + ")");
                return false;  // Download failed or no more pages
            }
        } catch (Exception e) {
            System.out.println("Error downloading resource: " + urlString);
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
