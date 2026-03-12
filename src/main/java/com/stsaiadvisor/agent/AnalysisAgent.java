package com.stsaiadvisor.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.llm.AnalysisPromptBuilder;
import com.stsaiadvisor.model.*;
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
 * AnalysisAgent - 通用分析Agent
 *
 * <p>职责：
 * <ul>
 *   <li>根据场景类型进行不同的分析</li>
 *   <li>battle场景：局势分析、威胁评估</li>
 *   <li>reward场景：牌组分析、流派识别</li>
 * </ul>
 *
 * <p>输出格式（battle场景）：
 * <pre>
 * 【局势】LOW | 一句话总结
 * 【威胁】预计伤害X，风险Y%，主要威胁
 * 【机会】致死伤害X，可击杀：是/否，主要机会
 * </pre>
 *
 * <p>输出格式（reward场景）：
 * <pre>
 * 【流派】主流派名称，成型度X%
 * 【统计】总X张，攻击X，技能X，能力X，均费X.X
 * 【短板】缺防御/缺过牌/牌组过厚
 * </pre>
 *
 * @see AnalysisResult
 * @see SceneContext
 */
public class AnalysisAgent implements Agent<SceneContext, AnalysisResult> {

    /** HTTP请求的JSON媒体类型 */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Gson实例 */
    private static final Gson GSON = new Gson();

    // ========== Battle场景正则 ==========

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

    // ========== Reward场景正则 ==========

    /** 解析流派的正则 */
    private static final Pattern ARCHETYPE_PATTERN =
        Pattern.compile("【流派】\\s*(.+?)[，,]\\s*成型度\\s*(\\d+)%");

    /** 解析统计的正则 */
    private static final Pattern STATS_PATTERN =
        Pattern.compile("【统计】\\s*总(\\d+)张.*攻击(\\d+).*技能(\\d+).*能力(\\d+).*均费([\\d.]+)");

    /** 解析短板的正则 */
    private static final Pattern WEAKNESS_PATTERN =
        Pattern.compile("【短板】\\s*(.+)");

    /** Mod配置 */
    private final ModConfig config;

    /** OkHttp客户端 */
    private final OkHttpClient httpClient;

    /** 提示词构建器 */
    private final AnalysisPromptBuilder promptBuilder;

    /**
     * 构造函数
     *
     * @param config Mod配置对象
     */
    public AnalysisAgent(ModConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.promptBuilder = new AnalysisPromptBuilder();
    }

    /**
     * 异步处理场景上下文
     *
     * @param context 场景上下文
     * @return CompletableFuture<AnalysisResult> 异步结果
     */
    @Override
    public CompletableFuture<AnalysisResult> process(SceneContext context) {
        return AsyncExecutor.submit(() -> processSync(context));
    }

    /**
     * 同步处理场景上下文
     *
     * @param context 场景上下文
     * @return AnalysisResult 分析结果
     * @throws IOException 网络请求失败
     */
    private AnalysisResult processSync(SceneContext context) throws IOException {
        String scenario = context.getScenario();
        String systemPrompt = promptBuilder.getSystemPrompt(scenario);
        String userPrompt = promptBuilder.buildPrompt(context);
        String apiUrl = config.getEffectiveBaseUrl("https://api.openai.com/v1/chat/completions");

        // 构建请求体
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
                "AnalysisAgent", apiUrl, config.getModel(),
                systemPrompt, userPrompt, requestBodyStr, responseBody,
                elapsed, response.isSuccessful()
            );

            if (!response.isSuccessful()) {
                System.err.println("[AnalysisAgent] API error: " + response.code());
                return createDefaultResult(scenario, "API error");
            }

            return parseResponse(responseBody, scenario);
        }
    }

    /**
     * 解析API响应
     *
     * @param responseBody API响应体
     * @param scenario 场景类型
     * @return AnalysisResult 分析结果
     */
    private AnalysisResult parseResponse(String responseBody, String scenario) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray choices = json.getAsJsonArray("choices");

            if (choices == null || choices.size() == 0) {
                return createDefaultResult(scenario, "No response");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            // 根据场景分支解析
            switch (scenario) {
                case "battle":
                    return parseBattleResult(content);
                case "reward":
                    return parseRewardResult(content);
                default:
                    return createDefaultResult(scenario, content);
            }
        } catch (Exception e) {
            System.err.println("[AnalysisAgent] Parse error: " + e.getMessage());
            return createDefaultResult(scenario, "Parse error");
        }
    }

    /**
     * 解析战斗场景结果
     */
    private AnalysisResult parseBattleResult(String content) {
        AnalysisResult result = new AnalysisResult();
        result.setScenario("battle");

        // 清理格式字符
        content = cleanText(content);

        // 按行解析
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            // 解析局势
            Matcher situationMatcher = SITUATION_PATTERN.matcher(line);
            if (situationMatcher.find()) {
                result.setUrgencyLevel(situationMatcher.group(1).toUpperCase());
                result.setSituationSummary(situationMatcher.group(2).trim());
                continue;
            }

            // 解析威胁
            Matcher threatMatcher = THREAT_PATTERN.matcher(line);
            if (threatMatcher.find()) {
                ThreatAssessment threats = new ThreatAssessment();
                threats.setIncomingDamage(Integer.parseInt(threatMatcher.group(1)));
                threats.setSurvivalRisk(Integer.parseInt(threatMatcher.group(2)));
                threats.setPrimaryThreat(threatMatcher.group(3).trim());
                result.setThreats(threats);
                continue;
            }

            // 解析机会
            Matcher oppMatcher = OPPORTUNITY_PATTERN.matcher(line);
            if (oppMatcher.find()) {
                OpportunityAssessment opp = new OpportunityAssessment();
                opp.setLethalDamage(Integer.parseInt(oppMatcher.group(1)));
                opp.setCanKillThisTurn("是".equals(oppMatcher.group(2)));
                opp.setPrimaryOpportunity(oppMatcher.group(3).trim());
                result.setOpportunities(opp);
            }
        }

        // 默认值
        if (result.getUrgencyLevel() == null) {
            result.setUrgencyLevel("MEDIUM");
        }
        if (result.getSituationSummary() == null) {
            result.setSituationSummary(content);
        }

        return result;
    }

    /**
     * 解析奖励场景结果
     */
    private AnalysisResult parseRewardResult(String content) {
        AnalysisResult result = new AnalysisResult();
        result.setScenario("reward");

        // 清理格式字符
        content = cleanText(content);

        // 按行解析
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            // 解析流派
            Matcher archetypeMatcher = ARCHETYPE_PATTERN.matcher(line);
            if (archetypeMatcher.find()) {
                result.setDeckArchetype(archetypeMatcher.group(1).trim());
                result.setArchetypeStrength(Integer.parseInt(archetypeMatcher.group(2)));
                continue;
            }

            // 解析统计
            Matcher statsMatcher = STATS_PATTERN.matcher(line);
            if (statsMatcher.find()) {
                DeckStatistics stats = new DeckStatistics();
                stats.setTotalCards(Integer.parseInt(statsMatcher.group(1)));
                stats.setAttackCards(Integer.parseInt(statsMatcher.group(2)));
                stats.setSkillCards(Integer.parseInt(statsMatcher.group(3)));
                stats.setPowerCards(Integer.parseInt(statsMatcher.group(4)));
                stats.setAverageCost(Double.parseDouble(statsMatcher.group(5)));
                result.setDeckStatistics(stats);
                continue;
            }

            // 解析短板
            Matcher weaknessMatcher = WEAKNESS_PATTERN.matcher(line);
            if (weaknessMatcher.find()) {
                String[] weaknesses = weaknessMatcher.group(1).split("[/／]");
                List<String> weaknessList = new ArrayList<>();
                for (String w : weaknesses) {
                    String trimmed = w.trim();
                    if (!trimmed.isEmpty()) {
                        weaknessList.add(trimmed);
                    }
                }
                result.setDeckWeaknesses(weaknessList);
            }
        }

        // 默认值
        if (result.getDeckArchetype() == null) {
            result.setDeckArchetype("未识别");
        }

        return result;
    }

    /**
     * 清理文本中的格式字符
     */
    private String cleanText(String text) {
        if (text == null) return "";
        return text
            .replaceAll("[\r\n]+", "\n")
            .replaceAll("[\\s　]+", " ")
            .trim();
    }

    /**
     * 创建默认结果
     */
    private AnalysisResult createDefaultResult(String scenario, String error) {
        AnalysisResult result = new AnalysisResult();
        result.setScenario(scenario);

        if ("battle".equals(scenario)) {
            result.setSituationSummary(error);
            result.setUrgencyLevel("MEDIUM");
            result.setKeyFocus(new ArrayList<>());
        } else if ("reward".equals(scenario)) {
            result.setDeckArchetype(error);
            result.setDeckWeaknesses(new ArrayList<>());
        }

        return result;
    }

    @Override
    public String getAgentName() {
        return "AnalysisAgent";
    }
}