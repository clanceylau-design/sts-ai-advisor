package com.stsaiadvisor.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.model.BattleContext;
import com.stsaiadvisor.model.Recommendation;
import com.stsaiadvisor.util.AsyncExecutor;
import com.stsaiadvisor.util.Constants;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Claude API client implementation.
 */
public class ClaudeClient implements LLMClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public ClaudeClient(ModConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
        this.promptBuilder = new PromptBuilder();
        this.responseParser = new ResponseParser();
    }

    @Override
    public CompletableFuture<Recommendation> requestAsync(BattleContext context) {
        return AsyncExecutor.submit(() -> request(context));
    }

    @Override
    public Recommendation request(BattleContext context) throws IOException {
        if (!config.isConfigured()) {
            Recommendation rec = new Recommendation();
            rec.setReasoning("API key not configured. Please set your API key in the mod settings.");
            rec.setCompanionMessage("I need an API key to help you! Check the mod settings.");
            return rec;
        }

        String prompt = promptBuilder.buildBattlePrompt(context);
        String systemPrompt = promptBuilder.getSystemPrompt();

        // Use custom base URL if configured
        String apiUrl = config.getEffectiveBaseUrl(Constants.ANTHROPIC_API_URL);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", 2048);

        // System message as top-level field
        requestBody.addProperty("system", systemPrompt);

        // User message
        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);
        requestBody.add("messages", messages);

        Request request = new Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", config.getApiKey())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[AI Advisor] Claude API response time: " + elapsed + "ms");

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                System.err.println("[AI Advisor] API error: " + response.code() + " - " + errorBody);

                Recommendation rec = new Recommendation();
                rec.setReasoning("API request failed: " + response.code());
                rec.setCompanionMessage("Oops, something went wrong with the API. Error: " + response.code());
                return rec;
            }

            String responseBody = response.body().string();
            return parseResponse(responseBody);
        }
    }

    private Recommendation parseResponse(String responseBody) {
        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Extract content from Claude's response format
            JsonArray contentArray = json.getAsJsonArray("content");
            if (contentArray == null || contentArray.size() == 0) {
                return responseParser.parse(responseBody);
            }

            JsonObject firstContent = contentArray.get(0).getAsJsonObject();
            String textContent = firstContent.get("text").getAsString();

            return responseParser.parse(textContent);
        } catch (Exception e) {
            System.err.println("[AI Advisor] Failed to parse Claude response: " + e.getMessage());
            return responseParser.parse(responseBody);
        }
    }

    @Override
    public boolean testConnection() {
        if (!config.isConfigured()) {
            return false;
        }

        try {
            // Use custom base URL if configured
        String apiUrl = config.getEffectiveBaseUrl(Constants.ANTHROPIC_API_URL);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", 10);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", "Say 'ok'");
        messages.add(userMessage);
        requestBody.add("messages", messages);

        Request request = new Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", config.getApiKey())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            System.err.println("[AI Advisor] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getClientName() {
        return "Claude (" + config.getModel() + ")";
    }
}