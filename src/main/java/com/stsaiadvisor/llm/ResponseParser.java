package com.stsaiadvisor.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stsaiadvisor.model.Recommendation;

/**
 * Parses LLM responses into Recommendation objects.
 */
public class ResponseParser {

    private static final Gson GSON = new Gson();

    /**
     * Parse a response string into a Recommendation.
     */
    public Recommendation parse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return createEmptyRecommendation("Empty response from API");
        }

        try {
            // Try to find JSON in the response
            String jsonContent = extractJson(responseBody);

            if (jsonContent == null) {
                return createEmptyRecommendation("No valid JSON found in response: " + truncate(responseBody, 100));
            }

            return GSON.fromJson(jsonContent, Recommendation.class);
        } catch (Exception e) {
            System.err.println("[AI Advisor] Failed to parse response: " + e.getMessage());
            return createEmptyRecommendation("Failed to parse response: " + e.getMessage());
        }
    }

    /**
     * Extract JSON from a response that might contain other text.
     */
    private String extractJson(String response) {
        // If it's already valid JSON
        if (isValidJson(response)) {
            return response;
        }

        // Try to find JSON block in the response
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            String potentialJson = response.substring(start, end + 1);
            if (isValidJson(potentialJson)) {
                return potentialJson;
            }
        }

        // Try to extract from code block
        if (response.contains("```json")) {
            int jsonStart = response.indexOf("```json") + 7;
            int jsonEnd = response.indexOf("```", jsonStart);
            if (jsonEnd > jsonStart) {
                return response.substring(jsonStart, jsonEnd).trim();
            }
        }

        // Try to extract from plain code block
        if (response.contains("```")) {
            int blockStart = response.indexOf("```") + 3;
            // Skip language identifier if present
            int newlineIdx = response.indexOf('\n', blockStart);
            if (newlineIdx > blockStart) {
                blockStart = newlineIdx + 1;
            }
            int blockEnd = response.indexOf("```", blockStart);
            if (blockEnd > blockStart) {
                String content = response.substring(blockStart, blockEnd).trim();
                if (isValidJson(content)) {
                    return content;
                }
            }
        }

        return null;
    }

    /**
     * Check if a string is valid JSON.
     */
    private boolean isValidJson(String str) {
        try {
            JsonElement element = new JsonParser().parse(str);
            return element.isJsonObject();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create an empty recommendation with an error message.
     */
    private Recommendation createEmptyRecommendation(String error) {
        Recommendation rec = new Recommendation();
        rec.setReasoning(error);
        rec.setCompanionMessage("Sorry, I couldn't process the response. Please try again.");
        return rec;
    }

    /**
     * Truncate a string for logging.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}