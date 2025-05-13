package au.org.aodn.cloudoptimized.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SSEEvent {
    @JsonProperty("status")
    protected String status;

    @JsonProperty("message")
    protected String message;

    @JsonProperty("data")
    protected String data; // JSON string for result events

}