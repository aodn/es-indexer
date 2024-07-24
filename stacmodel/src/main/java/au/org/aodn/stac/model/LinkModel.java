package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Objects;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkModel {
    protected String href;
    protected String rel;
    protected String type;
    protected String title;

    @Override
    public int hashCode() {
        return Objects.hash(href, rel, type, title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinkModel that = (LinkModel) o;
        return Objects.equals(href, that.href)
                && Objects.equals(rel, that.rel)
                && Objects.equals(type, that.type)
                && Objects.equals(title, that.title);
    }
}
