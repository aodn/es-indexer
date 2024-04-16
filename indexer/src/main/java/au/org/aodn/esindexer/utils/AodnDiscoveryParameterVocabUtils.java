package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.abstracts.OgcApiRequestEntityCreator;
import au.org.aodn.stac.model.ThemesModel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class AodnDiscoveryParameterVocabUtils {

    @Value("${ogc-api.host}")
    private String ogcApiHost;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    OgcApiRequestEntityCreator ogcApiRequestEntityCreator;

    @PostConstruct
    protected JsonNode fetchAodnDiscoveryParameterVocabs() {
        HttpEntity<String> requestEntity = ogcApiRequestEntityCreator.getRequestEntity(MediaType.APPLICATION_JSON, null);
        ResponseEntity<JsonNode> responseEntity = restTemplate.exchange(
                ogcApiHost + "/api/v1/ogc/ext/parameter/categories",
                HttpMethod.GET,
                requestEntity,
                JsonNode.class);
        return responseEntity.getBody();
    }

    public List<String> getAodnDiscoveryCategories(List<ThemesModel> themes) {
        List<String> result = new ArrayList<>();
        JsonNode aodnDiscoveryParameterVocabs = this.fetchAodnDiscoveryParameterVocabs();
        themes.forEach(theme -> {
            if (theme.getTitle().equals("AODN Discovery Parameter Vocabulary")) {
                theme.getConcepts().forEach(concept -> {
                    String id = concept.get("id");
                    String url = concept.get("url");
                    // TODO: do something with id and url
                    System.out.println("id: " + id + " | url: " + url);
                });
            }
        });
        return result;
    }
}
