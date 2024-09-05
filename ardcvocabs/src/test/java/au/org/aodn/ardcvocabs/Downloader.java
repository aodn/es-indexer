package au.org.aodn.ardcvocabs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a utility to download the missing json file from ardc for testing, it is super hard to do it manually, it read
 * a log file and download the file
 */
public class Downloader {
    private static final String LOG_FILE = "/tmp/logfile.txt";  // Change this to your log file path
    private static final String URL_REGEX = "https://[^ ]+";
    private static final String CATEGORY_REGEX = "category/(\\d+)";
    private static final String ENTITY_REGEX = "entity/(\\d+)";
    private static final String NERC_URL_REGEX = "http://vocab\\.nerc\\.ac\\.uk/collection/.*/current/([A-Z0-9]+)";

    public static void main(String[] args) {
        try {
            // Read the log file line by line
            Files.lines(Paths.get(LOG_FILE)).forEach(Downloader::processLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processLine(String line) {
        try {
            // Try to extract both formats of URLs
            String ardcUrl = extractUrl(line);
            String entityNumber = extractEntityNumber(line);
            String nercUrl = extractUrl(line);
            String nercCode = extractNercCode(line);

            if (ardcUrl != null && entityNumber != null) {
                System.out.println("Downloading ARDC entity " + entityNumber + " from URL: " + ardcUrl);
                downloadAndSaveResource(ardcUrl, "/tmp/entity" + entityNumber + ".json");
            } else if (nercUrl != null && nercCode != null) {
                // Save as nerc[code].json (e.g., nercCHLTMASS.json)
                String fileName = "/tmp/nerc" + nercCode + ".json";
                System.out.println("Downloading NERC entity " + nercCode + " from URL: " + nercUrl);
                downloadAndSaveResource(nercUrl, fileName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractUrl(String line) {
        // Use a regex to find the ARDC URL in the line
        Pattern pattern = Pattern.compile(URL_REGEX);
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group() : null;
    }

    private static String extractEntityNumber(String line) {
        // Use a regex to find the ARDC entity number in the line
        Pattern pattern = Pattern.compile(ENTITY_REGEX);
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractCategoryNumber(String line) {
        // Use a regex to find the ARDC entity number in the line
        Pattern pattern = Pattern.compile(CATEGORY_REGEX);
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractNercUrl(String line) {
        // Use a regex to find the NERC URL in the line
        Pattern pattern = Pattern.compile(NERC_URL_REGEX);
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group() : null;
    }

    private static String extractNercCode(String line) {
        // Use a regex to find the NERC entity code in the line
        Pattern pattern = Pattern.compile(NERC_URL_REGEX);
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void downloadAndSaveResource(String urlString, String fileName) {
        try {
            // Create URL and open connection
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Check if the response code is 200 (OK)
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Input stream to read the file content
                InputStream inputStream = connection.getInputStream();

                // Define output file path as fileName (entity[number].json or nerc[code].json)
                File outputFile = new File(fileName);

                // Write the response to the output file
                try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                System.out.println("Downloaded and saved: " + fileName);
            } else {
                System.out.println("Failed to download resource: " + urlString + " (Response code: " + responseCode + ")");
            }

            connection.disconnect();
        } catch (Exception e) {
            System.out.println("Error downloading resource: " + urlString);
            e.printStackTrace();
        }
    }
}
