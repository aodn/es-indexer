package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static au.org.aodn.esindexer.utils.CommonUtils.safeGet;


@Slf4j
@Component
public class GcmdKeywordUtils {

    protected Map<String, String> gcmdMapping = new HashMap<>();

    @PostConstruct
    public void init() {
        loadCsvToMap("config_files/gcmd-mapping.csv");
    }

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
        Resource resource = new ClassPathResource(path);
        InputStream fStream = resource.getInputStream();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fStream))) {
            return reader.lines()
                    .collect(Collectors.joining("\n"));
        }
    }

    // Load the CSV file into a HashMap
    private void loadCsvToMap(String path) {
        try {
            log.info("Loading GCMD mapping contents from CSV resource: {}", path);

            // Read the file content using Apache Commons CSV
            Resource resource = new ClassPathResource(path);
            try (InputStream inputStream = resource.getInputStream();
                 Reader reader = new InputStreamReader(inputStream);
                 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT)) {

                for (CSVRecord record : csvParser) {
                    if (record.size() >= 2) { // Ensure at least key-value pairs exist
                        String key = record.get(0).trim();
                        String value = record.get(1).trim();
                        gcmdMapping.put(key, value);
                    }
                }
            }

            log.info("Successfully loaded GCMD mapping contents from CSV resource: {}", path);
        } catch (IOException e) {
            log.error("Error while loading GCMD mapping contents from CSV resource: {}", path, e);
        }
    }

    protected List<String> extractGcmdKeywordLastWords(List<ThemesModel> themes) {
        log.info("Extracting GCMD keywords from record's themes");
        Set<String> keywords = new HashSet<>();
//        // Filter out null themes and empty concepts
//        themes = themes.stream()
//                .filter(Objects::nonNull)
//                .filter(theme -> theme.getConcepts() != null)
//                .filter(theme -> !theme.getConcepts().isEmpty())
//                .collect(Collectors.toList());
//
//        for (var theme : themes) {
//            for (var concept : theme.getConcepts()) {
//                if (concept.getId() == null || concept.getId().isEmpty()) {
//                    continue;
//                }
//                if (concept.getTitle() == null || concept.getTitle().isEmpty()) {
//                    continue;
//                }
//               var lowerCaseTitle = concept.getTitle().toLowerCase();
//                if (lowerCaseTitle.contains("palaeo temporal coverage")) {
//                    continue;
//                }
//                if (lowerCaseTitle.contains("gcmd") || lowerCaseTitle.contains("global change master directory")) {
//                    keywords.add(getLastWord(concept.getId().replace("\"", "")).toUpperCase());
//                }
//            }
//        }

        for (ThemesModel themesModel : themes) {
            for (var concept : themesModel.getConcepts()) {

                // TODO: refactor the too deep nested ifs
                if (concept.getId() == null || concept.getId().isEmpty()) {
                    continue;
                }
                if (concept.getTitle() == null || concept.getTitle().isEmpty()) {
                    continue;
                }

                if ((concept.getTitle().toLowerCase().contains("gcmd") || concept.getTitle().toLowerCase().contains("global change master directory")) && !concept.getTitle().toLowerCase().contains("palaeo temporal coverage")) {
                    for (ConceptModel conceptModel : themesModel.getConcepts()) {
                        if (conceptModel.getId() != null && !conceptModel.getId().isEmpty()) {
                            keywords.add(getLastWord(conceptModel.getId().replace("\"", "")).toUpperCase());
                        }
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

        log.info("Get parameter vocabs from record's GCMD keywords");

        List<String> gcmdKeywordLastWords = extractGcmdKeywordLastWords(themes);

        if (!gcmdKeywordLastWords.isEmpty()) {
            for (String gcmdKeywordLastWord : gcmdKeywordLastWords) {
                String mappedParameterVocab = getParameterVocabByGcmdKeywordLastWord(gcmdKeywordLastWord);
                if (!mappedParameterVocab.isEmpty() && !mappedParameterVocab.equalsIgnoreCase("uncategorised")) {
                    results.add(mappedParameterVocab.toLowerCase());
                }
            }
        }

        return new ArrayList<>(results);
    }
}
