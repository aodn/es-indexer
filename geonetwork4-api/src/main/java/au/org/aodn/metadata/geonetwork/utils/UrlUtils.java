package au.org.aodn.metadata.geonetwork.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class UrlUtils {

    @Autowired
    protected RestTemplate restTemplate;

    public boolean checkUrlExists(String url) {
        try {
            if(url.startsWith("http")) {
                // For using https to avoid redirect 301 status
                UriComponents components = UriComponentsBuilder
                        .fromUriString(url)
                        .scheme("https")    // Always force https
                        .port(null)
                        .build();
                url = components.toUriString();
            }

            ResponseEntity<Void> response = restTemplate.getForEntity(url, Void.class);
            return response.getStatusCode() == HttpStatus.OK;
        }
        catch (Exception e) {
            return false;
        }
    }
}
