package au.org.aodn.stac.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil {

    public static <T> String toJsonString(T instance) {
        ObjectMapper mapper = new ObjectMapper();
        try{
            return mapper.writeValueAsString(instance);
        }catch (JsonProcessingException ignored){}
        return null;
    }
}
