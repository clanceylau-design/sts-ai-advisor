package com.stsaiadvisor.llm;

import com.stsaiadvisor.model.Recommendation;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the response parser.
 */
public class ResponseParserTest {

    private final ResponseParser parser = new ResponseParser();

    @Test
    public void testParseValidJson() {
        String json = "{" +
            "\"suggestions\": [{" +
            "\"cardIndex\": 0," +
            "\"cardName\": \"Bash\"," +
            "\"priority\": 1," +
            "\"reason\": \"Apply vulnerable\"" +
            "}]," +
            "\"reasoning\": \"Good starting move\"," +
            "\"companionMessage\": \"Nice!\"" +
            "}";

        Recommendation rec = parser.parse(json);
        assertNotNull(rec);
        assertTrue(rec.hasSuggestions());
        assertEquals("Good starting move", rec.getReasoning());
    }

    @Test
    public void testParseJsonInCodeBlock() {
        String response = "Here's my analysis:\n" +
            "```json\n" +
            "{\n" +
            "  \"suggestions\": [],\n" +
            "  \"reasoning\": \"No good moves\"\n" +
            "}\n" +
            "```\n";

        Recommendation rec = parser.parse(response);
        assertNotNull(rec);
        assertEquals("No good moves", rec.getReasoning());
    }

    @Test
    public void testParseEmptyResponse() {
        Recommendation rec = parser.parse("");
        assertNotNull(rec);
        assertNull(rec.getSuggestions());
    }

    @Test
    public void testParseInvalidJson() {
        Recommendation rec = parser.parse("This is not JSON at all");
        assertNotNull(rec);
        assertTrue(rec.getReasoning().contains("No valid JSON"));
    }
}