package au.org.aodn.stac.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@SuppressWarnings("unused")
public class Citation {

    private String suggestedCitation;
    private List<String> useLimitations;
    private List<String> otherConstraints ;

    public void addUseLimitation(String useLimitation){
        if (this.useLimitations == null) {
            this.useLimitations = new ArrayList<>();
        }
        this.useLimitations.add(useLimitation);
    }

    public void addOtherConstraint(String otherConstraint){
        if (this.otherConstraints == null) {
            this.otherConstraints = new ArrayList<>();
        }
        this.otherConstraints.add(otherConstraint);
    }

    public String toJsonString() {
        ObjectMapper mapper = new ObjectMapper();
        try{
            return mapper.writeValueAsString(this);
        }catch (JsonProcessingException ignored){
            return null;
        }
    }
}
