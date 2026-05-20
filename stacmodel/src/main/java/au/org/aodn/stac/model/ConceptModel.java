package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptModel {
    private String id;
    private String url;
    private String title;
    private String description;

    // field for distinguishing AI guessed keywords
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("ai:description")
    private String aiDescription;

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
