package au.org.aodn.stac.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JsonUtil {

    protected final static ObjectMapper mapper = new ObjectMapper();

    public static <T> String toJsonString(T instance) {
        try{
            return mapper.writeValueAsString(instance);
        }
        catch (JsonProcessingException ignored){}
        return null;
    }

    public static Reader createJsonStream(String indexMappingFile, Map<String, String> param) throws IOException {
        try (InputStream inputStream = JsonUtil.class.getResourceAsStream("/schema/" + indexMappingFile)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                if(param != null) {
                    for (Map.Entry<String, String> entry : param.entrySet()) {
                        json = json.replace("${" + entry.getKey() + "}", entry.getValue());
                    }
                }
                return new StringReader(json);
            }
        }
        return null;
    }
}
