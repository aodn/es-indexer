package au.org.aodn.esindexer.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;

public class StringUtilTest {
    @Test
    public void testToUTF8String_withAsciiString() {
        String asciiString = "Hello World";
        String result = StringUtil.encodeUTF8(asciiString);
        assertEquals(asciiString, result);
    }

    @Test
    public void testToUTF8String_withFrenchCharacters() {
        String frenchString = "Bonjour le monde! Ça va?";
        // Manually encoding the string to ISO-8859-1
        String manuallyEncoded = new String(frenchString.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        // convert it back to UTF-8
        String result = StringUtil.encodeUTF8(manuallyEncoded);
        assertEquals(frenchString, result);
    }

    @Test
    public void testToUTF8String_withDegreeSign() {
        // Example string containing the degree symbol
        String original = "Temperature: 25° Celsius";
        // Manually encoding the string to ISO-8859-1
        String manuallyEncoded = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        // convert it back to UTF-8
        String result = StringUtil.encodeUTF8(manuallyEncoded);
        assertEquals(original, result);
    }
}
