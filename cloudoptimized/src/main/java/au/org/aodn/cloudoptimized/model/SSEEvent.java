package au.org.aodn.cloudoptimized.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SSEEvent<T> {
    @JsonProperty("status")
    protected String status;

    @JsonProperty("message")
    protected String message;

    @JsonProperty("data")
    protected T data; // JSON string for result events

}
