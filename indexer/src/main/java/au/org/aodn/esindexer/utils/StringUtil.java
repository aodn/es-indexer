package au.org.aodn.esindexer.utils;

import au.org.aodn.esindexer.configuration.AppConstants;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StringUtil {
    // Static method to convert to UTF-8 String
    public static String encodeUTF8(String input) {
        return new String(input.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
}
