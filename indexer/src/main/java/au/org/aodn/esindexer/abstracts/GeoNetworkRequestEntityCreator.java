package au.org.aodn.esindexer.abstracts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GeoNetworkRequestEntityCreator extends AbstractRequestEntityCreator {
    // used by GeoNetwork related requests
    @Override
    HttpHeaders createHeaders(MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(accept == null ? MediaType.APPLICATION_XML : accept));
        return headers;
    }
}
