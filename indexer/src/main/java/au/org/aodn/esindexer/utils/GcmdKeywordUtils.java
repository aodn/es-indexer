package au.org.aodn.esindexer.utils;

import au.org.aodn.stac.model.ConceptModel;
import au.org.aodn.stac.model.StacCollectionModel;
import au.org.aodn.stac.model.ThemesModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GcmdKeywordUtils {

    private String getLastWord(String keyword) {
        if (keyword.contains("|")) {
            return keyword.substring(keyword.lastIndexOf("|") + 1).strip();
        } else if (keyword.contains(">")) {
            return keyword.substring(keyword.lastIndexOf(">") + 1).strip();
        } else {
            return keyword.strip();
        }
    }

    public List<String> extractGcmdKeywordLastWords(StacCollectionModel stacCollectionModel) {
        Set<String> keywords = new HashSet<>();
        List<ThemesModel> themes = stacCollectionModel.getThemes();
        for (ThemesModel themesModel : themes) {
            if ((themesModel.getTitle().toLowerCase().contains("gcmd") || themesModel.getTitle().toLowerCase().contains("global change master directory")) && !themesModel.getTitle().toLowerCase().contains("palaeo temporal coverage")) {
                for (ConceptModel conceptModel : themesModel.getConcepts()) {
                    keywords.add(getLastWord(conceptModel.getId().replace("\"", "")));
                }
            }
        }
        return new ArrayList<>(keywords);
    }
}
