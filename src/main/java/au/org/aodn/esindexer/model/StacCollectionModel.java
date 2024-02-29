package au.org.aodn.esindexer.model;

import au.org.aodn.esindexer.configuration.AppConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StacCollectionModel {

    @JsonProperty("id")
    protected String uuid;
    protected String title;
    @JsonProperty("title_suggest")
    protected List<String> titleSuggest;
    protected String description;
    protected ExtentModel extent;
    protected SummariesModel summaries;
    protected List<ContactsModel> contacts;
    protected List<LanguageModel> languages;
    protected List<LinkModel> links;
    protected String license;
    protected List<ProviderModel> providers;
    /**
     * Do not use keywords and use theme instead, read here
     * protected List<String> keywords
     */
    protected List<ThemesModel> themes;

    @JsonProperty("stac_version")
    public String getStacVersion() {
        return AppConstants.STAC_VERSION;
    }

    @JsonProperty("type")
    public String getType() {
        return AppConstants.STAC_TYPE;
    }

    @JsonProperty("stac_extensions")
    public String[] getStacExtension() {
        return new String[] {
            "https://stac-extensions.github.io/scientific/v1.0.0/schema.json",
            "https://stac-extensions.github.io/contacts/v0.1.1/schema.json",
            "https://stac-extensions.github.io/projection/v1.1.0/schema.json",
            "https://stac-extensions.github.io/language/v1.0.0/schema.json",
            "https://stac-extensions.github.io/themes/v1.0.0/schema.json"
        };
    }
}
