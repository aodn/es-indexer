package au.org.aodn.esindexer.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class UrlUtils {

    @Autowired
    protected RestTemplate restTemplate;

    public boolean checkUrlExists(String url) {
        try {
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.HEAD, null, Void.class);
            return response.getStatusCode() == HttpStatus.OK;
        }
        catch (Exception e) {
            return false;
        }
    }
}
