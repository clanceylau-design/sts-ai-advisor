package com.stsaiadvisor.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logger for LLM API requests and responses.
 */
public class LLMLogger {
    private static final String LOG_DIR = "mods/sts-ai-advisor/logs/";
    private static final String LOG_FILE = LOG_DIR + "llm_requests.log";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Log an LLM request and response.
     */
    public static void logRequest(String provider, String url, String model,
                                   String systemPrompt, String userPrompt,
                                   String requestBody, String responseBody,
                                   long durationMs, boolean success) {
        try {
            // Ensure log directory exists
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Append to log file
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8)) {

                writer.write("\n");
                writer.write("================================================================================\n");
                writer.write("[" + DATE_FORMAT.format(new Date()) + "] LLM Request\n");
                writer.write("================================================================================\n");
                writer.write("Provider: " + provider + "\n");
                writer.write("URL: " + url + "\n");
                writer.write("Model: " + model + "\n");
                writer.write("Duration: " + durationMs + "ms\n");
                writer.write("Success: " + success + "\n");
                writer.write("\n");

                writer.write("--- System Prompt ---\n");
                writer.write(systemPrompt + "\n");
                writer.write("\n");

                writer.write("--- User Prompt ---\n");
                writer.write(userPrompt + "\n");
                writer.write("\n");

                writer.write("--- Request Body ---\n");
                try {
                    JsonObject parsed = GSON.fromJson(requestBody, JsonObject.class);
                    writer.write(GSON.toJson(parsed) + "\n");
                } catch (Exception e) {
                    writer.write(requestBody + "\n");
                }
                writer.write("\n");

                writer.write("--- Response Body ---\n");
                try {
                    JsonObject parsed = GSON.fromJson(responseBody, JsonObject.class);
                    writer.write(GSON.toJson(parsed) + "\n");
                } catch (Exception e) {
                    writer.write(responseBody + "\n");
                }
                writer.write("\n");

                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("[AI Advisor] Failed to write LLM log: " + e.getMessage());
        }
    }

    /**
     * Log a simple message.
     */
    public static void log(String message) {
        System.out.println("[AI Advisor] " + message);
    }
}