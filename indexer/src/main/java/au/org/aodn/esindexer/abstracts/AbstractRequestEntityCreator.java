package au.org.aodn.esindexer.abstracts;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public abstract class AbstractRequestEntityCreator {
    abstract HttpHeaders createHeaders(MediaType accept);

    public HttpEntity<String> getRequestEntity(String body) {
        return getRequestEntity(null, body);
    }

    public HttpEntity<String> getRequestEntity(MediaType accept, String body) {
        HttpHeaders headers = createHeaders(accept);
        return body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
    }
}
