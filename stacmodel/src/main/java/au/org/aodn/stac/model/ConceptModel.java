package au.org.aodn.stac.model;

import lombok.Builder;
import lombok.Data;

import java.util.Objects;


@Data
@Builder
public class ConceptModel {
    private String id;
    private String url;
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConceptModel that = (ConceptModel) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(url, that.url);
    }
    @Override
    public int hashCode() {
        return Objects.hash(id, url);
    }
}
