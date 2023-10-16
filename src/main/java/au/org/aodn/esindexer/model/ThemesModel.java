package au.org.aodn.esindexer.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.List;

@Data
@Builder
public class ThemesModel {
    protected List<Map<String, String>> concepts;
    protected String scheme;
    protected String description;
    protected String title;
}
