package au.org.aodn.esindexer.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StringUtils {
    // Static method to convert to UTF-8 String
    public static String encodeUTF8(String input) {
        return new String(input.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    public static List<String> filterStrings(List<String> input) {
        List<String> filteredList = new ArrayList<>();

        // Compile the regex patterns for detecting digits and UUID patterns.
        Pattern uuidPattern = Pattern.compile("[a-fA-F0-9]{8}\\-[a-fA-F0-9]{4}\\-[a-fA-F0-9]{4}\\-[a-fA-F0-9]{4}\\-[a-fA-F0-9]{12}");
        Pattern nonStandardPattern = Pattern.compile(".*[^a-zA-Z- ].*");

        for (String str : input) {
            if (!uuidPattern.matcher(str).matches() && !nonStandardPattern.matcher(str).matches()) {
                filteredList.add(str);
            }
        }

        return filteredList;
    }
}
