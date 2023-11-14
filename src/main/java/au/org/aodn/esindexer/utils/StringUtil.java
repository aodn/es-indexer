package au.org.aodn.esindexer.utils;

import java.nio.charset.StandardCharsets;

public class StringUtil {
    // Static method to convert to UTF-8 String
    public static String toUTF8String(String input) {
        return new String(input.getBytes(StandardCharsets.UTF_8));
    }
}
