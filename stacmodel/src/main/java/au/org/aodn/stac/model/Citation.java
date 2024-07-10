package au.org.aodn.stac.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Data
@Builder
public class Citation {

    private String suggestedCitation;
    private List<String> useLimitations;
    private List<String> otherConstraints ;

    public String toJsonString() {
        ObjectMapper mapper = new ObjectMapper();
        try{
            return mapper.writeValueAsString(this);
        }catch (JsonProcessingException ignored){
            return null;
        }
    }
}
