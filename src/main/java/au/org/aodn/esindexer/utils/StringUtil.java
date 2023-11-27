package au.org.aodn.esindexer.utils;

import java.nio.charset.StandardCharsets;

public class StringUtil {
    // Static method to convert to UTF-8 String
    public static String encodeUTF8(String input) {
        return new String(input.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
}
