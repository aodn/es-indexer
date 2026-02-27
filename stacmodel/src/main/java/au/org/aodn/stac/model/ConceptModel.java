package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;


@Data
@Builder
public class ConceptModel {
    private String id;
    private String url;
    private String title;
    private  String description;

    // field for distinguishing AI guessed keywords
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("ai:description")
    private String aiDescription;

    public ConceptModel(String id, String url, String title, String description) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.description = description;
        // for original keywords, not show this field
        this.aiDescription = null;
    }

    // need this full constructor required by builder
    public ConceptModel(String id, String url, String title, String description, String aiDescription) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.description = description;
        this.aiDescription = aiDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConceptModel that = (ConceptModel) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(url, that.url)
                && Objects.equals(title, that.title)
                && Objects.equals(description, that.description);
    }
    @Override
    public int hashCode() {
        return Objects.hash(id, url);
    }
}
