package au.org.aodn.esindexer.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringUtilsTest {
    @Test
    public void testToUTF8String_withAsciiString() {
        String asciiString = "Hello World";
        String result = StringUtils.encodeUTF8(asciiString);
        assertEquals(asciiString, result);
    }

    @Test
    public void testToUTF8String_withFrenchCharacters() {
        String frenchString = "Bonjour le monde! Ça va?";
        // Manually encoding the string to ISO-8859-1
        String manuallyEncoded = new String(frenchString.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        // convert it back to UTF-8
        String result = StringUtils.encodeUTF8(manuallyEncoded);
        assertEquals(frenchString, result);
    }

    @Test
    public void testToUTF8String_withDegreeSign() {
        // Example string containing the degree symbol
        String original = "Temperature: 25° Celsius";
        // Manually encoding the string to ISO-8859-1
        String manuallyEncoded = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        // convert it back to UTF-8
        String result = StringUtils.encodeUTF8(manuallyEncoded);
        assertEquals(original, result);
    }

    @Test
    public void testFilterStrings() {
        List<String> input = Arrays.asList(
                "hello",
                "hello 123",
                "usdsoio-3434903",
                "1234",
                "ThisIsAValidInput",
                "ANFOG",
                "IMOS",
                "this is a valid string",
                "abcd123efg",
                "1234-5678-9abc-def0-ghij-klmn-opqr-stuv",
                "records d7a14921 8f3f 4522",
                "aBcD",
                "UUID:550e8400-e29b-41d4-a716-446655440000"
        );

        List<String> expectedOutput = Arrays.asList(
                "hello",
                "ThisIsAValidInput",
                "ANFOG",
                "IMOS",
                "this is a valid string",
                "aBcD"
        );

        List<String> result = StringUtils.filterStrings(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testEmptyInput() {
        List<String> input = new ArrayList<>();
        List<String> expectedOutput = new ArrayList<>();

        List<String> result = StringUtils.filterStrings(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testAllInvalidStrings() {
        List<String> input = Arrays.asList(
                "123",
                "abc123",
                "uuid-550e8400-e29b-41d4-a716-446655440000",
                "abcd123efg",
                "hello123world",
                "records d7a14921 8f3f 4522"
        );

        List<String> expectedOutput = new ArrayList<>();

        List<String> result = StringUtils.filterStrings(input);

        assertEquals(expectedOutput, result);
    }

    @Test
    public void testAllValidStrings() {
        List<String> input = Arrays.asList(
                "example",
                "test",
                "filter",
                "sample",
                "data",
                "ANFOG",
                "IMOS",
                "this is a valid string",
                "word-with-hyphen"
        );

        List<String> expectedOutput = Arrays.asList(
                "example",
                "test",
                "filter",
                "sample",
                "data",
                "ANFOG",
                "IMOS",
                "this is a valid string",
                "word-with-hyphen"
        );

        List<String> result = StringUtils.filterStrings(input);

        assertEquals(expectedOutput, result);
    }
}
