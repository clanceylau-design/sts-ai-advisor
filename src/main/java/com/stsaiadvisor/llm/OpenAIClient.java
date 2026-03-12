package com.stsaiadvisor.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.model.BattleContext;
import com.stsaiadvisor.model.Recommendation;
import com.stsaiadvisor.util.AsyncExecutor;
import com.stsaiadvisor.util.Constants;
import com.stsaiadvisor.util.LLMLogger;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API client implementation.
 */
public class OpenAIClient implements LLMClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public OpenAIClient(ModConfig config) {
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
            rec.setReasoning("未配置API密钥，请在mod设置中设置您的API密钥。");
            rec.setCompanionMessage("我需要API密钥才能帮助您！请检查mod设置。");
            return rec;
        }

        String systemPrompt = promptBuilder.getSystemPrompt();
        String userPrompt = promptBuilder.buildBattlePrompt(context);

        // Use custom base URL if configured
        String apiUrl = config.getEffectiveBaseUrl(Constants.OPENAI_API_URL);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());

        // Messages array
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("max_tokens", 2048);
        requestBody.addProperty("temperature", 0.7);

        // Disable deep thinking to reduce response time
        requestBody.addProperty("enable_thinking", false);

        String requestBodyStr = gson.toJson(requestBody);

        Request request = new Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBodyStr, JSON))
            .build();

        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            String responseBody = response.body() != null ? response.body().string() : "";

            boolean success = response.isSuccessful();

            // Log request and response
            LLMLogger.logRequest(
                "OpenAI",
                apiUrl,
                config.getModel(),
                systemPrompt,
                userPrompt,
                requestBodyStr,
                responseBody,
                elapsed,
                success
            );

            System.out.println("[AI Advisor] API响应时间: " + elapsed + "ms, 成功: " + success);

            if (!success) {
                System.err.println("[AI Advisor] API错误: " + response.code() + " - " + responseBody);

                Recommendation rec = new Recommendation();
                rec.setReasoning("API请求失败: " + response.code());
                rec.setCompanionMessage("哎呀，API出了点问题。错误码: " + response.code());
                return rec;
            }

            return parseResponse(responseBody);
        }
    }

    private Recommendation parseResponse(String responseBody) {
        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Extract content from OpenAI's response format
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return responseParser.parse(responseBody);
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String textContent = message.get("content").getAsString();

            return responseParser.parse(textContent);
        } catch (Exception e) {
            System.err.println("[AI Advisor] 解析响应失败: " + e.getMessage());
            return responseParser.parse(responseBody);
        }
    }

    @Override
    public boolean testConnection() {
        if (!config.isConfigured()) {
            return false;
        }

        // Use custom base URL if configured
        String apiUrl = config.getEffectiveBaseUrl(Constants.OPENAI_API_URL);

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", config.getModel());

            JsonArray messages = new JsonArray();
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", "Say 'ok'");
            messages.add(userMessage);
            requestBody.add("messages", messages);
            requestBody.addProperty("max_tokens", 10);

            // Disable deep thinking
            requestBody.addProperty("enable_thinking", false);

            Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            System.err.println("[AI Advisor] 连接测试失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getClientName() {
        return "OpenAI (" + config.getModel() + ")";
    }
}