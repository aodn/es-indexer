package au.org.aodn.metadata.geonetwork.utils;

import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

public class CommonUtils {
    public static MediaType MEDIA_UTF8_XML = new MediaType("application", "xml", StandardCharsets.UTF_8);
}
