import au.org.aodn.esindexer.utils.StringUtil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilTest {
    @Test
    public void testToUTF8String_withAsciiString() {
        String asciiString = "Hello World";
        String result = StringUtil.toUTF8String(asciiString);
        assertEquals(asciiString, result, "The UTF-8 conversion of an ASCII string should not change the string");
    }

    @Test
    public void testToUTF8String_withFrenchCharacters() {
        String frenchString = "Bonjour le monde! Ça va?";
        String result = StringUtil.toUTF8String(frenchString);
        assertEquals(frenchString, result, "The UTF-8 conversion of a string with French characters should not change the string");
    }

    @Test
    public void testToUTF8String_withDegreeSign() {
        String stringWithDegreeSign = "Temperature: 25°C";
        String result = StringUtil.toUTF8String(stringWithDegreeSign);
        assertEquals(stringWithDegreeSign, result, "The UTF-8 conversion of a string with a degree sign should not change the string");
    }
}
