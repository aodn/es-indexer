package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.ThemesModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GcmdKeywordUtils {

    private static String getLastWord(String keyword) {
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

    protected static List<String> extractGcmdKeywordLastWords(List<ThemesModel> themes) {
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

    protected static String getParameterVocabByGcmdKeywordLastWord(String gcmdKeywordLastWord) {
        String result;

        // TODO: implement the mapping schema here
        result = gcmdKeywordLastWord;

        return gcmdKeywordLastWord;
    }

    public static List<String> getMappedParameterVocabsFromGcmdKeywords(List<ThemesModel> themes) {
        Set<String> results = new HashSet<>();

        List<String> gcmdKeywordLastWords = extractGcmdKeywordLastWords(themes);

        for (String gcmdKeywordLastWord : gcmdKeywordLastWords) {
            results.add(getParameterVocabByGcmdKeywordLastWord(gcmdKeywordLastWord));
        }

        return new ArrayList<>(results);
    }
}
