package au.org.aodn.esindexer.dto;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class GeoNetworkSearchRequestBodyDTO {
    private final int from;
    private final int size;
    private final Map<String, Object> query;

    public GeoNetworkSearchRequestBodyDTO(String uuid) {
        this.from = 0;
        this.size = 1;
        this.query = new HashMap<>();
        Map<String, Object> match = new HashMap<>();
        match.put("metadataIdentifier", uuid);
        query.put("match", match);
    }

    public String toString() {
        return "GeoNetworkSearchRequestBodyDTO(from=" + this.getFrom() + ", size=" + this.getSize() + ", query=" + this.getQuery() + ")";
    }
}
