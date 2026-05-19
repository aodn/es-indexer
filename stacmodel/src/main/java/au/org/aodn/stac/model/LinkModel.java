package au.org.aodn.stac.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkModel {
    protected String href;
    protected String rel;
    protected String type;
    protected String title;
    protected String description;
    @JsonProperty("ai:group")
    protected String aiGroup;
    @JsonProperty("ai:role")
    protected List<String> aiRole;

    @Override
    public int hashCode() {
        return Objects.hash(href, rel, type, title, description, aiGroup, aiRole);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinkModel that = (LinkModel) o;
        return Objects.equals(href, that.href)
                && Objects.equals(rel, that.rel)
                && Objects.equals(type, that.type)
                && Objects.equals(title, that.title)
                && Objects.equals(description, that.description)
                && Objects.equals(aiGroup, that.aiGroup);
    }
}
