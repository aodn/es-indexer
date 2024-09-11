package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.util.*;


@Slf4j
@Component
public class GcmdKeywordUtils {

    protected Map<String, String> gcmdMapping;

    private String getLastWord(String keyword) {
        String result;
        if (keyword.contains("|")) {
            result = keyword.substring(keyword.lastIndexOf("|") + 1).strip();
        } else if (keyword.contains(">")) {
            result = keyword.substring(keyword.lastIndexOf(">") + 1).strip();
        } else {
            result = keyword.strip();
        }
        return result;
    }


    private static String readResourceFile(String path) throws IOException {
        File f = ResourceUtils.getFile(path);
        return new String(Files.readAllBytes(f.toPath()));
    }

    // Load the CSV file into a HashMap
    @PostConstruct
    protected void loadCsvToMap(String path) {
        try {
            // Read the file as a single String
            String fileContent = readResourceFile(path);

            // Split the content into lines
            String[] lines = fileContent.split("\\r?\\n");

            // Process each line
            for (String line : lines) {
                // Split the line into key and value based on comma
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    gcmdMapping.put(key, value);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    protected List<String> extractGcmdKeywordLastWords(List<ThemesModel> themes) {
        Set<String> keywords = new HashSet<>();
        for (ThemesModel themesModel : themes) {
            if ((themesModel.getTitle().toLowerCase().contains("gcmd") || themesModel.getTitle().toLowerCase().contains("global change master directory")) && !themesModel.getTitle().toLowerCase().contains("palaeo temporal coverage")) {
                for (ConceptModel conceptModel : themesModel.getConcepts()) {
                    if (conceptModel.getId() != null && !conceptModel.getId().isEmpty()) {
                        keywords.add(getLastWord(conceptModel.getId().replace("\"", "")).toUpperCase());
                    }
                }
            }
        }
        return new ArrayList<>(keywords);
    }

    protected String getParameterVocabByGcmdKeywordLastWord(String gcmdKeywordLastWord) {
        return gcmdMapping.getOrDefault(gcmdKeywordLastWord, "");
    }

    public List<String> getMappedParameterVocabsFromGcmdKeywords(List<ThemesModel> themes) {
        Set<String> results = new HashSet<>();

        List<String> gcmdKeywordLastWords = extractGcmdKeywordLastWords(themes);

        if (!gcmdKeywordLastWords.isEmpty()) {
            for (String gcmdKeywordLastWord : gcmdKeywordLastWords) {
                results.add(getParameterVocabByGcmdKeywordLastWord(gcmdKeywordLastWord));
            }
        }

        return new ArrayList<>(results);
    }
}
