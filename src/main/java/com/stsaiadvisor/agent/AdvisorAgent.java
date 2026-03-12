package com.stsaiadvisor.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.llm.AdvisorAgentPromptBuilder;
import com.stsaiadvisor.model.*;
import com.stsaiadvisor.util.AsyncExecutor;
import com.stsaiadvisor.util.LLMLogger;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AdvisorAgent - 决策顾问Agent
 *
 * <p>职责：
 * <ul>
 *   <li>整合AnalysisAgent的分析结果和SkillAgent的战术建议</li>
 *   <li>根据场景类型给出不同建议</li>
 * </ul>
 *
 * <p>支持场景：
 * <ul>
 *   <li>battle：出牌顺序建议</li>
 *   <li>reward：选牌建议</li>
 * </ul>
 *
 * <p>Battle输出格式：
 * <pre>
 * 【出牌顺序】
 * [4] 剑柄打击 -> 酸液史莱姆：9伤害击杀
 * [2] 打击 -> 酸液史莱姆：补刀
 *
 * 【策略】先清小怪
 *
 * 【提示】这回合稳了！
 * </pre>
 *
 * <p>Reward输出格式：
 * <pre>
 * 【推荐】[0] 灵体：消耗流核心
 * 【备选】[1] 重刃：力量流补充
 *
 * 【策略】优先补充消耗流核心卡
 *
 * 【跳过】否：有核心卡可选
 * </pre>
 *
 * @see FinalRecommendation
 * @see SceneRecommendation
 */
public class AdvisorAgent implements Agent<AdvisorRequest, FinalRecommendation> {

    /** HTTP请求的JSON媒体类型 */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Gson实例 */
    private static final Gson GSON = new Gson();

    // ========== Battle场景正则 ==========

    /** 解析出牌建议：[0] 卡牌名 -> 目标：理由 */
    private static final Pattern SUGGESTION_PATTERN =
        Pattern.compile("\\[(\\d+)\\]\\s*(.+?)\\s*->\\s*(.+?)\\s*[：:]\\s*(.+)");

    // ========== Reward场景正则 ==========

    /** 解析推荐卡牌：【推荐】[0] 卡牌名：理由 */
    private static final Pattern RECOMMEND_PATTERN =
        Pattern.compile("【推荐】\\s*\\[(\\d+)\\]\\s*(.+?)\\s*[：:]\\s*(.+)");

    /** 解析备选卡牌：【备选】[0] 卡牌名：理由 */
    private static final Pattern ALTERNATE_PATTERN =
        Pattern.compile("【备选】\\s*\\[(\\d+)\\]\\s*(.+?)\\s*[：:]\\s*(.+)");

    /** 解析跳过建议：【跳过】是/否：理由 */
    private static final Pattern SKIP_PATTERN =
        Pattern.compile("【跳过】\\s*(是|否)\\s*[：:]?\\s*(.*)");

    /** Mod配置 */
    private final ModConfig config;

    /** OkHttp客户端 */
    private final OkHttpClient httpClient;

    /** 提示词构建器 */
    private final AdvisorAgentPromptBuilder promptBuilder;

    /** 当前请求的敌人列表（battle场景） */
    private List<EnemyState> currentEnemies;

    /** 当前场景类型 */
    private String currentScenario;

    /**
     * 构造函数
     *
     * @param config Mod配置对象
     */
    public AdvisorAgent(ModConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.promptBuilder = new AdvisorAgentPromptBuilder();
    }

    /**
     * 异步处理顾问请求
     *
     * @param request 顾问请求
     * @return CompletableFuture<FinalRecommendation> 异步结果
     */
    @Override
    public CompletableFuture<FinalRecommendation> process(AdvisorRequest request) {
        // 判断场景类型
        this.currentScenario = determineScenario(request);

        // 保存敌人列表（battle场景）
        if ("battle".equals(currentScenario) && request.getContext() != null) {
            this.currentEnemies = request.getContext().getEnemies();
        } else {
            this.currentEnemies = null;
        }

        return AsyncExecutor.submit(() -> processSync(request));
    }

    /**
     * 判断场景类型
     */
    private String determineScenario(AdvisorRequest request) {
        if (request.getSceneContext() != null) {
            return request.getSceneContext().getScenario();
        }
        if (request.getQuestion() != null && "CARD_REWARD".equals(request.getQuestion().getType())) {
            return "reward";
        }
        return "battle";
    }

    /**
     * 同步处理顾问请求
     */
    private FinalRecommendation processSync(AdvisorRequest request) throws IOException {
        String systemPrompt = promptBuilder.getSystemPrompt(currentScenario);
        String userPrompt = promptBuilder.buildPrompt(request);
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
        requestBody.addProperty("max_tokens", 512);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("enable_thinking", false);

        String requestBodyStr = GSON.toJson(requestBody);

        Request request2 = new Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBodyStr, JSON))
            .build();

        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request2).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            String responseBody = response.body() != null ? response.body().string() : "";

            LLMLogger.logRequest(
                "AdvisorAgent",
                apiUrl,
                config.getModel(),
                systemPrompt,
                userPrompt,
                requestBodyStr,
                responseBody,
                elapsed,
                response.isSuccessful()
            );

            if (!response.isSuccessful()) {
                System.err.println("[AdvisorAgent] API error: " + response.code());
                return createDefaultRecommendation(currentScenario, "API error: " + response.code());
            }

            return parseResponse(responseBody);
        }
    }

    /**
     * 解析API响应
     */
    private FinalRecommendation parseResponse(String responseBody) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray choices = json.getAsJsonArray("choices");

            if (choices == null || choices.size() == 0) {
                return createDefaultRecommendation(currentScenario, "No response");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            // 根据场景分支解析
            if ("reward".equals(currentScenario)) {
                return parseRewardResult(content);
            } else {
                return parseBattleResult(content);
            }
        } catch (Exception e) {
            System.err.println("[AdvisorAgent] Parse error: " + e.getMessage());
            return createDefaultRecommendation(currentScenario, "Parse error: " + e.getMessage());
        }
    }

    // ========== Battle场景解析 ==========

    /**
     * 解析Battle场景结果
     */
    private FinalRecommendation parseBattleResult(String content) {
        FinalRecommendation recommendation = new FinalRecommendation();
        List<CardPlaySuggestion> suggestions = new ArrayList<>();

        // 构建敌人名称到索引的映射
        Map<String, Integer> enemyNameToIndex = buildEnemyNameMap();

        // 按行解析
        String[] lines = content.split("\n");
        int priority = 1;

        for (String line : lines) {
            line = line.trim();

            // 匹配出牌建议行
            Matcher matcher = SUGGESTION_PATTERN.matcher(line);
            if (matcher.find()) {
                CardPlaySuggestion suggestion = new CardPlaySuggestion();
                suggestion.setCardIndex(Integer.parseInt(matcher.group(1)));
                suggestion.setCardName(matcher.group(2).trim());

                String targetName = matcher.group(3).trim();
                suggestion.setTargetName(targetName);
                suggestion.setTargetIndex(resolveTargetIndex(targetName, enemyNameToIndex));

                suggestion.setReason(matcher.group(4).trim());
                suggestion.setPriority(priority++);
                suggestions.add(suggestion);
                continue;
            }

            // 匹配策略行
            if (line.startsWith("【策略】") || line.contains("策略")) {
                String strategy = line.replace("【策略】", "").replace("策略：", "").trim();
                recommendation.setReasoning(strategy);
                continue;
            }

            // 匹配提示行
            if (line.startsWith("【提示】") || line.contains("提示")) {
                String tip = line.replace("【提示】", "").replace("提示：", "").trim();
                recommendation.setCompanionMessage(tip);
            }
        }

        recommendation.setSuggestions(suggestions);

        if (suggestions.isEmpty() && recommendation.getReasoning() == null) {
            recommendation.setReasoning(content);
        }

        if (recommendation.getCompanionMessage() == null) {
            recommendation.setCompanionMessage("加油！");
        }

        return recommendation;
    }

    /**
     * 构建敌人名称到索引的映射
     */
    private Map<String, Integer> buildEnemyNameMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("自身", -1);

        if (currentEnemies == null || currentEnemies.isEmpty()) {
            return map;
        }

        Map<String, Integer> nameCount = new HashMap<>();

        for (int i = 0; i < currentEnemies.size(); i++) {
            EnemyState enemy = currentEnemies.get(i);
            String baseName = enemy.getName();

            int sameNameCount = countEnemiesByName(baseName);
            if (sameNameCount == 1) {
                map.put(baseName, i);
            }

            int count = nameCount.getOrDefault(baseName, 0) + 1;
            nameCount.put(baseName, count);

            String indexedName = baseName + "(" + count + ")";
            map.put(indexedName, i);
            map.put(baseName + "（" + count + "）", i);
        }

        return map;
    }

    private int countEnemiesByName(String name) {
        if (currentEnemies == null) return 0;
        int count = 0;
        for (EnemyState enemy : currentEnemies) {
            if (enemy.getName().equals(name)) {
                count++;
            }
        }
        return count;
    }

    private int resolveTargetIndex(String targetName, Map<String, Integer> nameMap) {
        if (nameMap.containsKey(targetName)) {
            return nameMap.get(targetName);
        }

        String cleanName = targetName.trim();

        if (cleanName.contains("自身") || cleanName.contains("自己")) {
            return -1;
        }

        for (Map.Entry<String, Integer> entry : nameMap.entrySet()) {
            if (cleanName.contains(entry.getKey()) || entry.getKey().contains(cleanName)) {
                return entry.getValue();
            }
        }

        return currentEnemies != null && !currentEnemies.isEmpty() ? 0 : -1;
    }

    // ========== Reward场景解析 ==========

    /**
     * 解析Reward场景结果
     */
    private FinalRecommendation parseRewardResult(String content) {
        FinalRecommendation recommendation = new FinalRecommendation();
        List<CardPlaySuggestion> suggestions = new ArrayList<>();

        // 按行解析
        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim();

            // 匹配推荐卡牌
            Matcher recommendMatcher = RECOMMEND_PATTERN.matcher(line);
            if (recommendMatcher.find()) {
                CardPlaySuggestion suggestion = new CardPlaySuggestion();
                suggestion.setCardIndex(Integer.parseInt(recommendMatcher.group(1)));
                suggestion.setCardName(recommendMatcher.group(2).trim());
                suggestion.setReason(recommendMatcher.group(3).trim());
                suggestion.setPriority(1);  // 推荐卡牌优先级1
                suggestions.add(suggestion);
                continue;
            }

            // 匹配备选卡牌
            Matcher alternateMatcher = ALTERNATE_PATTERN.matcher(line);
            if (alternateMatcher.find()) {
                CardPlaySuggestion suggestion = new CardPlaySuggestion();
                suggestion.setCardIndex(Integer.parseInt(alternateMatcher.group(1)));
                suggestion.setCardName(alternateMatcher.group(2).trim());
                suggestion.setReason(alternateMatcher.group(3).trim());
                suggestion.setPriority(2);  // 备选卡牌优先级2
                suggestions.add(suggestion);
                continue;
            }

            // 匹配跳过建议
            Matcher skipMatcher = SKIP_PATTERN.matcher(line);
            if (skipMatcher.find()) {
                recommendation.setReasoning("跳过建议: " + skipMatcher.group(2).trim());
                // 注意：FinalRecommendation没有skip字段，这里用reasoning存储
                continue;
            }

            // 匹配策略行
            if (line.startsWith("【策略】") || line.contains("策略")) {
                String strategy = line.replace("【策略】", "").replace("策略：", "").trim();
                if (recommendation.getReasoning() == null) {
                    recommendation.setReasoning(strategy);
                }
                continue;
            }
        }

        recommendation.setSuggestions(suggestions);

        if (suggestions.isEmpty() && recommendation.getReasoning() == null) {
            recommendation.setReasoning(content);
        }

        recommendation.setCompanionMessage("选牌愉快！");

        return recommendation;
    }

    // ========== 工具方法 ==========

    /**
     * 创建默认建议
     */
    private FinalRecommendation createDefaultRecommendation(String scenario, String error) {
        FinalRecommendation rec = new FinalRecommendation();
        rec.setReasoning(error);
        rec.setCompanionMessage("抱歉，分析出现问题。");
        rec.setSuggestions(new ArrayList<>());
        return rec;
    }

    @Override
    public String getAgentName() {
        return "AdvisorAgent";
    }
}