package au.org.aodn.esindexer.abstracts;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class OgcApiRequestEntityCreator extends AbstractRequestEntityCreator {
    @Override
    HttpHeaders createHeaders(MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(accept == null ? MediaType.APPLICATION_JSON : accept));
        return headers;
    }
}
