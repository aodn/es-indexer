package au.org.aodn.stac.model;

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

    public ConceptModel(String id, String url, String title, String description) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.description = description;
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
