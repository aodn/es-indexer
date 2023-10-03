package au.org.aodn.esindexer.dto;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MetadataRecordsCountRequestBodyDTO {
    private final Map<String, Object> query;

    public MetadataRecordsCountRequestBodyDTO() {
        this.query = new HashMap<>();
        query.put("match_all", new HashMap<>());
    }

    public String toString() {
        return "MetadataRecordsCountRequestBodyDTO(query=" + this.getQuery() + ")";
    }
}
