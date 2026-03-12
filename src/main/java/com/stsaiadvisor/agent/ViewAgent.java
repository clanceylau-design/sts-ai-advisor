package com.stsaiadvisor.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.llm.ViewAgentPromptBuilder;
import com.stsaiadvisor.model.BattleContext;
import com.stsaiadvisor.model.OpportunityAssessment;
import com.stsaiadvisor.model.ThreatAssessment;
import com.stsaiadvisor.model.ViewState;
import com.stsaiadvisor.util.AsyncExecutor;
import com.stsaiadvisor.util.LLMLogger;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ViewAgent - 状态理解Agent
 *
 * <p>职责：分析战斗状态，判断局势紧急程度
 *
 * <p>输出格式：简洁文本
 * <pre>
 * 【局势】LOW | 一句话总结
 * 【威胁】预计伤害X，风险Y%，主要威胁
 * 【机会】致死伤害X，可击杀：是/否，主要机会
 * </pre>
 */
public class ViewAgent implements Agent<BattleContext, ViewState> {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    /** 解析局势的正则 */
    private static final Pattern SITUATION_PATTERN =
        Pattern.compile("【局势】\\s*(\\w+)\\s*[|｜]\\s*(.+)");

    /** 解析威胁的正则 */
    private static final Pattern THREAT_PATTERN =
        Pattern.compile("【威胁】\\s*预计伤害\\s*(\\d+).*风险\\s*(\\d+)%[，,]?\\s*(.+)");

    /** 解析机会的正则 */
    private static final Pattern OPPORTUNITY_PATTERN =
        Pattern.compile("【机会】\\s*致死伤害\\s*(\\d+).*可击杀[：:]\\s*(是|否)[，,]?\\s*(.+)",
            Pattern.DOTALL);

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final ViewAgentPromptBuilder promptBuilder;

    public ViewAgent(ModConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.promptBuilder = new ViewAgentPromptBuilder();
    }

    @Override
    public CompletableFuture<ViewState> process(BattleContext context) {
        return AsyncExecutor.submit(() -> processSync(context));
    }

    private ViewState processSync(BattleContext context) throws IOException {
        String systemPrompt = promptBuilder.getSystemPrompt();
        String userPrompt = promptBuilder.buildPrompt(context);
        String apiUrl = config.getEffectiveBaseUrl("https://api.openai.com/v1/chat/completions");

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());

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
        requestBody.addProperty("max_tokens", 256);  // 简短输出
        requestBody.addProperty("temperature", 0.3);
        requestBody.addProperty("enable_thinking", false);

        String requestBodyStr = GSON.toJson(requestBody);

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

            LLMLogger.logRequest(
                "ViewAgent", apiUrl, config.getModel(),
                systemPrompt, userPrompt, requestBodyStr, responseBody,
                elapsed, response.isSuccessful()
            );

            if (!response.isSuccessful()) {
                System.err.println("[ViewAgent] API error: " + response.code());
                return createDefaultViewState("API error");
            }

            return parseResponse(responseBody);
        }
    }

    private ViewState parseResponse(String responseBody) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray choices = json.getAsJsonArray("choices");

            if (choices == null || choices.size() == 0) {
                return createDefaultViewState("No response");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            return parseSimpleTextFormat(content);
        } catch (Exception e) {
            System.err.println("[ViewAgent] Parse error: " + e.getMessage());
            return createDefaultViewState("Parse error");
        }
    }

    /**
     * 解析简洁文本格式
     */
    private ViewState parseSimpleTextFormat(String content) {
        ViewState viewState = new ViewState();

        // 清理格式字符
        content = cleanText(content);

        // 按行解析
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            // 解析局势
            Matcher situationMatcher = SITUATION_PATTERN.matcher(line);
            if (situationMatcher.find()) {
                try {
                    viewState.setUrgencyLevel(ViewState.UrgencyLevel.valueOf(situationMatcher.group(1).toUpperCase()));
                } catch (IllegalArgumentException e) {
                    viewState.setUrgencyLevel(ViewState.UrgencyLevel.MEDIUM);
                }
                viewState.setSituationSummary(situationMatcher.group(2).trim());
                continue;
            }

            // 解析威胁
            Matcher threatMatcher = THREAT_PATTERN.matcher(line);
            if (threatMatcher.find()) {
                ThreatAssessment threats = new ThreatAssessment();
                threats.setIncomingDamage(Integer.parseInt(threatMatcher.group(1)));
                threats.setSurvivalRisk(Integer.parseInt(threatMatcher.group(2)));
                threats.setPrimaryThreat(threatMatcher.group(3).trim());
                viewState.setThreats(threats);
                continue;
            }

            // 解析机会
            Matcher oppMatcher = OPPORTUNITY_PATTERN.matcher(line);
            if (oppMatcher.find()) {
                OpportunityAssessment opp = new OpportunityAssessment();
                opp.setLethalDamage(Integer.parseInt(oppMatcher.group(1)));
                opp.setCanKillThisTurn("是".equals(oppMatcher.group(2)));
                opp.setPrimaryOpportunity(oppMatcher.group(3).trim());
                viewState.setOpportunities(opp);
            }
        }

        // 默认值
        if (viewState.getUrgencyLevel() == null) {
            viewState.setUrgencyLevel(ViewState.UrgencyLevel.MEDIUM);
        }
        if (viewState.getSituationSummary() == null) {
            viewState.setSituationSummary(content);
        }

        return viewState;
    }

    /**
     * 清理文本中的格式字符
     */
    private String cleanText(String text) {
        if (text == null) return "";
        // 移除多余空白和特殊字符
        return text
            .replaceAll("[\r\n]+", "\n")
            .replaceAll("[\\s　]+", " ")
            .trim();
    }

    private ViewState createDefaultViewState(String error) {
        ViewState viewState = new ViewState();
        viewState.setSituationSummary(error);
        viewState.setUrgencyLevel(ViewState.UrgencyLevel.MEDIUM);
        viewState.setKeyFocus(new ArrayList<>());
        return viewState;
    }

    @Override
    public String getAgentName() {
        return "ViewAgent";
    }
}