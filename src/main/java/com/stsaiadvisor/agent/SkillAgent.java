package com.stsaiadvisor.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.knowledge.SkillManager;
import com.stsaiadvisor.model.*;
import com.stsaiadvisor.util.AsyncExecutor;
import com.stsaiadvisor.util.LLMLogger;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * SkillAgent - 战术技能Agent
 *
 * <p>职责：
 * <ol>
 *   <li>根据角色&牌组&敌人筛选相关skill</li>
 *   <li>读取skill详细内容</li>
 *   <li>用LLM提炼关键信息</li>
 * </ol>
 *
 * <p>输出：提炼后的战术要点，供AdvisorAgent使用
 */
public class SkillAgent implements Agent<SkillRequest, TacticalSkills> {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final SkillManager skillManager;

    public SkillAgent(ModConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.skillManager = new SkillManager("mods/sts-ai-advisor/skills");
        this.skillManager.loadMetadata();
    }

    @Override
    public CompletableFuture<TacticalSkills> process(SkillRequest request) {
        return AsyncExecutor.submit(() -> processSync(request));
    }

    private TacticalSkills processSync(SkillRequest request) throws IOException {
        // Step 1: 筛选相关skill
        List<String> cardNames = extractCardNames(request.getFullDeck());
        List<String> enemyNames = extractEnemyNames(request.getEnemies());

        // 使用场景参数筛选skill
        List<String> relevantSkills = skillManager.selectRelevantSkills(
            request.getCharacterClass(),
            cardNames,
            enemyNames,
            request.getScenario()  // 场景过滤
        );

        System.out.println("[SkillAgent] Selected " + relevantSkills.size() + " relevant skills for scenario: " + request.getScenario());

        // Step 2: 读取skill内容（限制数量）
        StringBuilder skillsContent = new StringBuilder();
        int maxSkills = 3;
        int count = 0;
        for (String skillId : relevantSkills) {
            if (count >= maxSkills) break;
            String content = skillManager.getSkillContent(skillId);
            if (content != null && !content.isEmpty()) {
                skillsContent.append("---\n").append(content).append("\n");
                count++;
            }
        }

        if (skillsContent.length() == 0) {
            return createDefaultTacticalSkills("无相关战术知识");
        }

        // Step 3: 用LLM提炼关键信息
        return extractKeyInsights(request, skillsContent.toString());
    }

    /**
     * 用LLM提炼关键信息
     */
    private TacticalSkills extractKeyInsights(SkillRequest request, String skillsContent) throws IOException {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request, skillsContent);

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
        requestBody.addProperty("max_tokens", 256);
        requestBody.addProperty("temperature", 0.3);
        requestBody.addProperty("enable_thinking", false);

        String requestBodyStr = GSON.toJson(requestBody);

        Request httpRequest = new Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBodyStr, JSON))
            .build();

        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            String responseBody = response.body() != null ? response.body().string() : "";

            LLMLogger.logRequest(
                "SkillAgent", apiUrl, config.getModel(),
                systemPrompt, userPrompt, requestBodyStr, responseBody,
                elapsed, response.isSuccessful()
            );

            if (!response.isSuccessful()) {
                return createDefaultTacticalSkills("API错误");
            }

            return parseResponse(responseBody);
        }
    }

    private String buildSystemPrompt() {
        return "你是杀戮尖塔的战术专家。根据提供的战术知识，提炼出当前局势最关键的战术要点。\n\n" +
               "## 输出要求\n" +
               "直接输出关键信息，格式：\n\n" +
               "【流派】主流派，成型判断\n" +
               "【策略】一句话核心策略\n" +
               "【要点】最关键的2-3个战术要点\n\n" +
               "限制：输出不超过100字";
    }

    private String buildUserPrompt(SkillRequest request, String skillsContent) {
        StringBuilder prompt = new StringBuilder();

        // 当前状态
        prompt.append("【角色】").append(getCharacterCN(request.getCharacterClass())).append("\n");

        if (request.getFullDeck() != null && !request.getFullDeck().isEmpty()) {
            prompt.append("【牌组】");
            int count = 0;
            for (CardState card : request.getFullDeck()) {
                if (count++ >= 10) break;
                prompt.append(card.getName()).append(" ");
            }
            prompt.append("\n");
        }

        if (request.getEnemies() != null && !request.getEnemies().isEmpty()) {
            prompt.append("【敌人】");
            for (EnemyState enemy : request.getEnemies()) {
                prompt.append(enemy.getName()).append("(").append(enemy.getCurrentHealth()).append("HP) ");
            }
            prompt.append("\n");
        }

        // 战术知识
        prompt.append("\n【相关战术知识】\n").append(skillsContent);

        prompt.append("\n请提炼关键战术要点。");

        return prompt.toString();
    }

    private TacticalSkills parseResponse(String responseBody) {
        try {
            JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray choices = json.getAsJsonArray("choices");

            if (choices == null || choices.size() == 0) {
                return createDefaultTacticalSkills("无响应");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            // 直接保存原文，不再解析格式
            TacticalSkills skills = new TacticalSkills();
            skills.setRawOutput(content.trim());
            skills.setSkills(new ArrayList<>());
            skills.setPriorityTargets(new ArrayList<>());

            return skills;
        } catch (Exception e) {
            return createDefaultTacticalSkills("解析错误");
        }
    }

    /**
     * 提取卡牌名称列表
     */
    private List<String> extractCardNames(List<CardState> cards) {
        List<String> names = new ArrayList<>();
        if (cards == null) return names;
        for (CardState card : cards) {
            if (card.getName() != null) {
                names.add(card.getName());
            }
        }
        return names;
    }

    /**
     * 提取敌人名称列表
     */
    private List<String> extractEnemyNames(List<EnemyState> enemies) {
        List<String> names = new ArrayList<>();
        if (enemies == null) return names;
        for (EnemyState enemy : enemies) {
            if (enemy.getName() != null) {
                names.add(enemy.getName());
            }
        }
        return names;
    }

    private String getCharacterCN(String characterClass) {
        if (characterClass == null) return "未知";
        switch (characterClass) {
            case "IRONCLAD": return "铁甲战士";
            case "THE_SILENT": return "静默猎人";
            case "DEFECT": return "故障机器人";
            case "WATCHER": return "观者";
            default: return characterClass;
        }
    }

    private TacticalSkills createDefaultTacticalSkills(String error) {
        TacticalSkills skills = new TacticalSkills();
        skills.setRawOutput(error);
        skills.setSkills(new ArrayList<>());
        skills.setPriorityTargets(new ArrayList<>());
        return skills;
    }

    @Override
    public String getAgentName() {
        return "SkillAgent";
    }
}