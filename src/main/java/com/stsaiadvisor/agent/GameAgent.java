package com.stsaiadvisor.agent;

import com.google.gson.*;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.overlay.OverlayClient;
import com.stsaiadvisor.tool.GameTool;
import com.stsaiadvisor.tool.ToolRegistry;
import com.stsaiadvisor.tool.ToolResult;
import com.stsaiadvisor.util.AsyncExecutor;
import com.stsaiadvisor.util.LLMLogger;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * GameAgent - 通用游戏AI代理
 *
 * <p>核心设计理念：
 * <ul>
 *   <li>单Agent架构：一个Agent处理所有场景</li>
 *   <li>Tool驱动：LLM自主决定调用哪些工具获取信息</li>
 *   <li>User Prompt区分场景：不同场景追加不同的用户请求</li>
 *   <li>对话记忆：保持上下文连续性，减少重复Tool调用</li>
 * </ul>
 *
 * <p>工作流程（Agentic Loop）：
 * <ol>
 *   <li>构建System Prompt + Tool定义</li>
 *   <li>发送初始消息到LLM</li>
 *   <li>循环处理：LLM返回文本 → 输出；LLM请求Tool Call → 执行Tool，返回结果</li>
 *   <li>LLM结束 → 完成</li>
 * </ol>
 *
 * <p>记忆机制：
 * <ul>
 *   <li>对话历史：保存最近的消息（包含tool call和result）</li>
 *   <li>工具缓存：STABLE类型的工具结果会被缓存</li>
 *   <li>战斗重置：每场新战斗开始时清空记忆</li>
 * </ul>
 *
 * @see ToolRegistry
 * @see GameContext
 */
public class GameAgent {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    /** 最大Tool Call轮次 */
    private static final int MAX_TOOL_ROUNDS = 5;

    /** 最大Token数量（约4KB上下文） */
    private static final int MAX_CONTEXT_TOKENS = 4000;

    /** 最少保留的消息轮数 */
    private static final int MIN_KEEP_ROUNDS = 3;

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final ToolRegistry toolRegistry;
    private final OverlayClient overlayClient;

    /** 对话历史（用于保持上下文连续性） */
    private List<JsonObject> conversationHistory = new ArrayList<>();

    /** 当前战斗ID（用于检测战斗切换） */
    private String currentBattleId = null;

    /**
     * 构造函数
     *
     * @param config Mod配置
     * @param toolRegistry 工具注册中心
     * @param overlayClient Overlay客户端
     */
    public GameAgent(ModConfig config, ToolRegistry toolRegistry, OverlayClient overlayClient) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.overlayClient = overlayClient;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 处理用户请求
     *
     * @param userPrompt 用户提示
     * @param context 游戏上下文
     * @return CompletableFuture<Void>
     */
    public CompletableFuture<Void> process(String userPrompt, GameContext context) {
        return AsyncExecutor.submit(() -> {
            processSync(userPrompt, context);
            return null;
        });
    }

    /**
     * 重置对话记忆
     *
     * <p>在战斗开始时调用，清空历史消息和工具缓存
     */
    public void resetMemory() {
        conversationHistory.clear();
        currentBattleId = null;
        if (toolRegistry != null) {
            toolRegistry.invalidateCache();
        }
        System.out.println("[GameAgent] Memory reset");
    }

    /**
     * 检查并更新战斗状态
     *
     * @param context 游戏上下文
     * @return true 如果是新战斗
     */
    private boolean checkAndUpdateBattleId(GameContext context) {
        String battleId = generateBattleId(context);
        if (battleId == null) {
            // 不在战斗中，清空记忆
            if (currentBattleId != null) {
                resetMemory();
            }
            return false;
        }

        if (!battleId.equals(currentBattleId)) {
            // 新战斗，清空记忆
            System.out.println("[GameAgent] New battle detected: " + battleId);
            resetMemory();
            currentBattleId = battleId;
            return true;
        }

        return false;
    }

    /**
     * 生成战斗ID
     *
     * <p>用于识别是否是新战斗
     */
    private String generateBattleId(GameContext context) {
        if (context == null || !context.isInBattle()) {
            return null;
        }
        // 使用敌人名称组合作为战斗ID
        StringBuilder sb = new StringBuilder();
        if (context.getEnemies() != null) {
            for (com.stsaiadvisor.model.EnemyState enemy : context.getEnemies()) {
                if (enemy.getName() != null) {
                    sb.append(enemy.getName()).append("_");
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : "battle_" + System.currentTimeMillis();
    }

    /**
     * 同步处理请求
     */
    private void processSync(String userPrompt, GameContext context) throws IOException {
        // 记录开始时间
        long startTime = System.currentTimeMillis();

        System.out.println("[GameAgent] processSync 收到 userPrompt: " + userPrompt);

        String scenario = context.getCurrentScenario();

        // 检查是否是新战斗，如果是则重置记忆
        checkAndUpdateBattleId(context);

        // 1. 构建System Prompt
        String systemPrompt = buildSystemPrompt(scenario);

        // 2. 构建消息列表
        List<JsonObject> messages = new ArrayList<>();

        // 添加System消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        // 添加历史消息（排除旧的system消息，并确保消息结构完整）
        for (JsonObject historyMsg : conversationHistory) {
            if (historyMsg.has("role") && !"system".equals(historyMsg.get("role").getAsString())) {
                messages.add(historyMsg);
            }
        }

        // 验证消息结构完整性：确保每个 tool 消息前面都有带 tool_calls 的 assistant 消息
        messages = validateAndFixMessages(messages);

        // 添加新的User消息
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        // 保存到历史
        conversationHistory.add(userMessage);

        // 3. 获取工具定义
        JsonArray tools = toolRegistry.generateToolDefinitions(scenario);

        // 4. 发送加载状态
        if (overlayClient != null) {
            overlayClient.sendLoading("分析中...");
        }

        // 5. Agentic Loop
        int round = 0;

        while (round < MAX_TOOL_ROUNDS) {
            round++;

            // 构建请求体（非流式）
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", config.getModel());
            requestBody.add("messages", GSON.toJsonTree(messages).getAsJsonArray());
            requestBody.add("tools", tools);
            requestBody.addProperty("max_tokens", 1024);
            requestBody.addProperty("temperature", 0.7);

            // 调用LLM
            LLMResult result = callLLM(requestBody, messages);
            if (result == null) {
                System.err.println("[GameAgent] LLM call failed");
                break;
            }

            // 检查是否有tool_calls
            if (result.hasToolCalls) {
                // 构建进度显示
                StringBuilder progressBuilder = new StringBuilder();
                progressBuilder.append("🔍 **获取信息中...**\n\n");

                // 处理每个tool call
                for (JsonObject toolCall : result.toolCalls) {
                    // 获取tool call ID
                    String toolCallId;
                    if (toolCall.has("id") && !toolCall.get("id").isJsonNull() && !toolCall.get("id").getAsString().isEmpty()) {
                        toolCallId = toolCall.get("id").getAsString();
                    } else {
                        toolCallId = "tc_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
                    }

                    // 检查function是否存在
                    if (!toolCall.has("function") || toolCall.get("function").isJsonNull()) {
                        System.err.println("[GameAgent] Tool call missing function: " + toolCall);
                        continue;
                    }

                    JsonObject function = toolCall.getAsJsonObject("function");

                    // 检查name是否存在
                    if (!function.has("name") || function.get("name").isJsonNull()) {
                        System.err.println("[GameAgent] Tool call missing function name: " + function);
                        continue;
                    }

                    String toolName = function.get("name").getAsString();

                    // 检查arguments是否存在
                    String argumentsStr = "{}";
                    if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
                        argumentsStr = function.get("arguments").getAsString();
                    }

                    System.out.println("[GameAgent] Tool call: " + toolName + ", id: " + toolCallId);

                    // 显示正在调用的工具
                    String toolDisplayName = getToolDisplayName(toolName);
                    progressBuilder.append("→ ").append(toolDisplayName).append("\n");
                    if (overlayClient != null) {
                        overlayClient.sendLoading(progressBuilder.toString());
                    }

                    // 执行工具
                    JsonObject args = GSON.fromJson(argumentsStr, JsonObject.class);
                    ToolResult toolResult = executeTool(toolName, args, context);

                    // 显示工具结果摘要
                    if (toolResult.isSuccess()) {
                        progressBuilder.append("  ✓ ").append(getToolResultSummary(toolName, toolResult)).append("\n");
                    } else {
                        progressBuilder.append("  ✗ 获取失败\n");
                    }

                    // 添加tool result消息
                    JsonObject toolResultMsg = new JsonObject();
                    toolResultMsg.addProperty("role", "tool");
                    toolResultMsg.addProperty("tool_call_id", toolCallId);

                    if (toolResult.isSuccess()) {
                        // 发送给LLM的完整结果
                        String fullContent = GSON.toJson(toolResult.getData());
                        toolResultMsg.addProperty("content", fullContent);
                        messages.add(toolResultMsg);

                        // 保存到历史时：REALTIME类型用简短占位符，节省token
                        JsonObject historyMsg = new JsonObject();
                        historyMsg.addProperty("role", "tool");
                        historyMsg.addProperty("tool_call_id", toolCallId);
                        GameTool tool = toolRegistry.getTool(toolName);
                        if (tool != null && tool.getInfoType() == GameTool.InfoType.REALTIME) {
                            // 实时数据已过期，用占位符代替详细结果
                            historyMsg.addProperty("content", "[实时数据，已过期]");
                        } else {
                            // STABLE类型保留完整结果（可复用）
                            historyMsg.addProperty("content", fullContent);
                        }
                        conversationHistory.add(historyMsg);
                    } else {
                        toolResultMsg.addProperty("content", "Error: " + toolResult.getError());
                        messages.add(toolResultMsg);
                        conversationHistory.add(toolResultMsg);
                    }
                }

                // 继续循环
                continue;
            }

            // 没有tool_calls，输出最终内容
            if (result.content != null && !result.content.isEmpty()) {
                // 计算总耗时
                long elapsedMs = System.currentTimeMillis() - startTime;

                System.out.println("[GameAgent] Final content length: " + result.content.length());
                System.out.println("[GameAgent] Final content preview: " + result.content.substring(0, Math.min(100, result.content.length())));

                // 在内容顶部添加耗时信息
                String contentWithTiming = "⏱ " + elapsedMs + "ms\n\n" + result.content;

                if (overlayClient != null) {
                    overlayClient.sendUpdate(contentWithTiming);
                } else {
                    System.err.println("[GameAgent] overlayClient is null!");
                }
            } else {
                System.out.println("[GameAgent] No final content, result.content=" + (result.content == null ? "null" : "empty"));
            }
            break;
        }

        // 裁剪历史，防止过长
        trimHistory();

        System.out.println("[GameAgent] Completed in " + round + " rounds, history size: " + conversationHistory.size());
    }

    /**
     * 智能裁剪历史消息
     *
     * <p>策略：
     * <ul>
     *   <li>基于Token计数，而非消息计数</li>
     *   <li>优先保留最近的消息</li>
     *   <li>tool消息必须和对应的assistant(tool_calls)消息成对保留</li>
     *   <li>保证消息结构完整性</li>
     * </ul>
     */
    private void trimHistory() {
        // 计算当前总Token数
        int totalTokens = estimateTokens(conversationHistory);
        System.out.println("[GameAgent] 当前上下文约 " + totalTokens + " tokens, " + conversationHistory.size() + " 条消息");

        if (totalTokens <= MAX_CONTEXT_TOKENS) {
            return;  // 不需要裁剪
        }

        // 按轮次分组（user + assistant + tool结果 = 一轮）
        List<List<JsonObject>> rounds = groupByRounds();

        // 从最早的轮次开始删除，直到满足Token限制
        int tokensToRemove = totalTokens - MAX_CONTEXT_TOKENS;
        int removedRounds = 0;

        while (tokensToRemove > 0 && rounds.size() > MIN_KEEP_ROUNDS) {
            List<JsonObject> oldestRound = rounds.get(0);
            int roundTokens = estimateTokens(oldestRound);

            // 从历史中移除这轮消息
            for (JsonObject msg : oldestRound) {
                conversationHistory.remove(msg);
            }

            tokensToRemove -= roundTokens;
            removedRounds++;
            rounds.remove(0);
        }

        System.out.println("[GameAgent] 裁剪了 " + removedRounds + " 轮对话，剩余 " + conversationHistory.size() + " 条消息");
    }

    /**
     * 按轮次分组消息
     *
     * <p>一轮 = user消息 + assistant(tool_calls) + 对应的tool结果
     */
    private List<List<JsonObject>> groupByRounds() {
        List<List<JsonObject>> rounds = new ArrayList<>();
        List<JsonObject> currentRound = new ArrayList<>();
        java.util.Set<String> pendingToolCallIds = new java.util.HashSet<>();

        for (JsonObject msg : conversationHistory) {
            String role = msg.has("role") ? msg.get("role").getAsString() : "";

            if ("user".equals(role)) {
                // 新的一轮开始
                if (!currentRound.isEmpty()) {
                    rounds.add(currentRound);
                }
                currentRound = new ArrayList<>();
                currentRound.add(msg);
            } else if ("assistant".equals(role)) {
                currentRound.add(msg);
                // 记录这轮的tool_call_ids
                if (msg.has("tool_calls") && !msg.get("tool_calls").isJsonNull()) {
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    for (JsonElement tc : toolCalls) {
                        JsonObject tcObj = tc.getAsJsonObject();
                        if (tcObj.has("id")) {
                            pendingToolCallIds.add(tcObj.get("id").getAsString());
                        }
                    }
                }
            } else if ("tool".equals(role)) {
                // tool消息归属于当前轮
                currentRound.add(msg);
                if (msg.has("tool_call_id")) {
                    pendingToolCallIds.remove(msg.get("tool_call_id").getAsString());
                }
            }
        }

        // 添加最后一轮
        if (!currentRound.isEmpty()) {
            rounds.add(currentRound);
        }

        return rounds;
    }

    /**
     * 估算消息列表的Token数
     *
     * <p>简单估算：中文约1.5字符/token，英文约4字符/token
     */
    private int estimateTokens(List<JsonObject> messages) {
        int totalChars = 0;
        for (JsonObject msg : messages) {
            totalChars += msg.toString().length();
        }
        // 保守估算：平均3字符/token
        return totalChars / 3;
    }

    /**
     * 验证并修复消息结构
     *
     * <p>确保每个 tool 消息前面都有带 tool_calls 的 assistant 消息
     * 如果发现孤立的 tool 消息，删除它们
     *
     * @param messages 原始消息列表
     * @return 修复后的消息列表
     */
    private List<JsonObject> validateAndFixMessages(List<JsonObject> messages) {
        List<JsonObject> fixedMessages = new ArrayList<>();
        java.util.Set<String> validToolCallIds = new java.util.HashSet<>();

        for (int i = 0; i < messages.size(); i++) {
            JsonObject msg = messages.get(i);
            String role = msg.has("role") ? msg.get("role").getAsString() : "";

            if ("system".equals(role) || "user".equals(role)) {
                // system 和 user 消息总是有效
                fixedMessages.add(msg);
                validToolCallIds.clear();  // 新的 user 消息会重置 tool call 上下文
            } else if ("assistant".equals(role)) {
                // assistant 消息：检查是否有 tool_calls
                if (msg.has("tool_calls") && !msg.get("tool_calls").isJsonNull()) {
                    // 记录所有 tool_call_id
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    for (JsonElement tc : toolCalls) {
                        JsonObject tcObj = tc.getAsJsonObject();
                        if (tcObj.has("id")) {
                            validToolCallIds.add(tcObj.get("id").getAsString());
                        }
                    }
                }
                fixedMessages.add(msg);
            } else if ("tool".equals(role)) {
                // tool 消息：检查是否有对应的 tool_call_id
                if (msg.has("tool_call_id")) {
                    String toolCallId = msg.get("tool_call_id").getAsString();
                    if (validToolCallIds.contains(toolCallId)) {
                        fixedMessages.add(msg);
                        // 移除已匹配的 tool_call_id，防止重复
                        validToolCallIds.remove(toolCallId);
                    } else {
                        System.out.println("[GameAgent] 跳过孤立的 tool 消息: " + toolCallId);
                    }
                } else {
                    System.out.println("[GameAgent] 跳过无 tool_call_id 的 tool 消息");
                }
            } else {
                // 未知角色，直接添加
                fixedMessages.add(msg);
            }
        }

        return fixedMessages;
    }

    /**
     * 调用LLM API（非流式）
     *
     * @param requestBody 请求体
     * @param messages 消息历史（会被更新）
     * @return LLM结果
     */
    private LLMResult callLLM(JsonObject requestBody, List<JsonObject> messages) throws IOException {
        String apiUrl = config.getEffectiveBaseUrl("https://api.openai.com/v1/chat/completions");
        String requestBodyStr = GSON.toJson(requestBody);

        Request request = new Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBodyStr, JSON))
            .build();

        long startTime = System.currentTimeMillis();
        LLMResult result = new LLMResult();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                System.err.println("[GameAgent] API error: " + response.code() + " - " + responseBody);
                return null;
            }

            long elapsed = System.currentTimeMillis() - startTime;

            // 记录日志
            LLMLogger.logRequest(
                "GameAgent", apiUrl, config.getModel(),
                "System prompt", "User prompt",
                requestBodyStr, responseBody,
                elapsed, true
            );

            // 解析响应
            JsonObject jsonResponse = GSON.fromJson(responseBody, JsonObject.class);

            if (jsonResponse.has("choices")) {
                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    JsonObject message = choice.getAsJsonObject("message");

                    // 构建assistant消息并添加到历史
                    JsonObject assistantMessage = new JsonObject();
                    assistantMessage.addProperty("role", "assistant");

                    // 处理content
                    if (message.has("content") && !message.get("content").isJsonNull()) {
                        result.content = message.get("content").getAsString();
                        assistantMessage.addProperty("content", result.content);
                    }

                    // 处理tool_calls
                    if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                        result.hasToolCalls = true;
                        JsonArray toolCallsArray = message.getAsJsonArray("tool_calls");
                        assistantMessage.add("tool_calls", toolCallsArray);

                        result.toolCalls = new ArrayList<>();
                        for (JsonElement tc : toolCallsArray) {
                            result.toolCalls.add(tc.getAsJsonObject());
                        }
                    }

                    messages.add(assistantMessage);

                    // 保存assistant消息到历史（重要：tool result需要配对的assistant消息）
                    conversationHistory.add(assistantMessage);
                }
            }
        } catch (JsonSyntaxException e) {
            System.err.println("[GameAgent] JSON parse error: " + e.getMessage());
            return null;
        }

        return result;
    }

    /**
     * LLM结果
     */
    private static class LLMResult {
        boolean hasToolCalls = false;
        List<JsonObject> toolCalls = null;
        String content = null;
    }

    /**
     * 执行工具
     *
     * <p>对于STABLE类型的工具，会使用缓存机制：
     * <ul>
     *   <li>如果缓存中有结果，直接返回</li>
     *   <li>如果没有缓存，执行后缓存结果</li>
     * </ul>
     *
     * <p>对于REALTIME类型的工具，每次执行前都会刷新GameContext，确保获取最新数据
     */
    private ToolResult executeTool(String toolName, JsonObject args, GameContext context) {
        GameTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return ToolResult.failure(toolName, "Tool not found: " + toolName);
        }

        // REALTIME类型工具：强制刷新GameContext获取最新数据
        if (tool.getInfoType() == GameTool.InfoType.REALTIME) {
            context.refreshContext();
            System.out.println("[GameAgent] Refreshed context for REALTIME tool: " + toolName);
        }

        // 检查是否为STABLE类型且有缓存
        if (tool.getInfoType() == GameTool.InfoType.STABLE && toolRegistry.hasCachedResult(toolName)) {
            JsonObject cachedResult = toolRegistry.getCachedResult(toolName);
            System.out.println("[GameAgent] Using cached result for: " + toolName);
            return ToolResult.success(toolName, cachedResult, 0);
        }

        try {
            ToolResult result = tool.execute(args, context).get();

            // 如果是STABLE类型且执行成功，缓存结果
            if (result.isSuccess() && tool.getInfoType() == GameTool.InfoType.STABLE) {
                toolRegistry.cacheResult(toolName, result.getData());
                System.out.println("[GameAgent] Cached result for: " + toolName);
            }

            return result;
        } catch (Exception e) {
            return ToolResult.failure(toolName, "Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * 构建System Prompt
     */
    private String buildSystemPrompt(String scenario) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是杀戮尖塔的AI助手，帮助玩家做出更好的决策。\n\n");

        prompt.append("## 记忆机制\n");
        prompt.append("你有对话记忆能力，可以记住之前的工具调用和结果。\n");
        prompt.append("- 如果已经获取过某些信息，不需要重复调用工具\n");
        prompt.append("- 【实时信息】工具每回合都需要调用获取最新数据\n");
        prompt.append("- 【稳定信息】工具结果会被缓存，可直接使用之前的结果\n\n");

        prompt.append("## 用户偏好学习\n");
        prompt.append("你可以学习和记住用户的游戏偏好：\n");
        prompt.append("- 使用 get_user_preferences 获取用户的历史偏好\n");
        prompt.append("- 当用户明确表达对某策略的喜好/厌恶，或纠正你的建议时，使用 save_user_preference 保存偏好\n");
        prompt.append("- 偏好应该是简洁明确的语义描述，例如：'偏好高伤害卡牌'、'不喜欢消耗型策略'\n");
        prompt.append("- 提供建议时应参考用户的偏好，但也要考虑当前局势\n\n");

        prompt.append("## 可用工具\n");
        prompt.append("你可以使用以下工具获取游戏状态信息：\n");

        for (GameTool tool : toolRegistry.getAvailableTools(scenario)) {
            prompt.append("- ").append(tool.getId()).append(": ").append(tool.getDescription()).append("\n");
        }

        prompt.append("\n## 工作流程\n");
        prompt.append("1. 获取用户偏好，了解玩家的游戏风格\n");
        prompt.append("2. 检查是否有已缓存的信息可以复用\n");
        prompt.append("3. 调用必要的实时信息工具获取最新数据\n");
        prompt.append("4. 结合用户偏好和当前局势给出建议\n");
        prompt.append("5. 如果用户表达了新的偏好，保存它\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("直接给出你的分析和建议，使用友好的语气。\n");

        if ("battle".equals(scenario)) {
            prompt.append("\n## 战斗场景输出格式\n");
            prompt.append("【出牌顺序】\n");
            prompt.append("[卡牌索引] 卡牌名 -> 目标：简短理由\n");
            prompt.append("\n【策略】一句话核心策略（不超过20字）\n");
        } else if ("reward".equals(scenario)) {
            prompt.append("\n## 奖励场景输出格式\n");
            prompt.append("【推荐】[卡牌索引] 卡牌名：简短理由\n");
            prompt.append("【备选】[卡牌索引] 卡牌名：简短理由（可选）\n");
            prompt.append("\n【跳过】是/否：简短理由\n");
        }

        return prompt.toString();
    }

    /**
     * 获取场景对应的默认User Prompt
     */
    public static String getDefaultUserPrompt(String scenario) {
        switch (scenario) {
            case "battle":
                return "帮我想一下这回合应该怎么出牌";
            case "reward":
                return "帮我从这些卡牌中选一张";
            case "event":
                return "帮我分析一下这个事件应该怎么选择";
            default:
                return "请帮我分析当前情况";
        }
    }

    /**
     * 获取工具显示名称
     */
    private String getToolDisplayName(String toolId) {
        switch (toolId) {
            case "get_player_state":
                return "玩家状态";
            case "get_hand_cards":
                return "手牌信息";
            case "get_enemies":
                return "敌人信息";
            case "get_deck":
                return "牌组信息";
            case "get_relics":
                return "遗物信息";
            case "get_potions":
                return "药水信息";
            case "get_tactical_knowledge":
                return "战术知识";
            case "get_card_rewards":
                return "奖励卡牌";
            case "get_event_options":
                return "事件选项";
            case "get_user_preferences":
                return "用户偏好";
            case "save_user_preference":
                return "保存偏好";
            default:
                return toolId;
        }
    }

    /**
     * 获取工具结果摘要
     */
    private String getToolResultSummary(String toolId, ToolResult result) {
        if (result == null || result.getData() == null) {
            return "无数据";
        }

        try {
            JsonObject data = result.getData();

            switch (toolId) {
                case "get_player_state":
                    int hp = data.has("current_health") ? data.get("current_health").getAsInt() : 0;
                    int maxHp = data.has("max_health") ? data.get("max_health").getAsInt() : 0;
                    int energy = data.has("energy") ? data.get("energy").getAsInt() : 0;
                    int block = data.has("block") ? data.get("block").getAsInt() : 0;
                    return "HP " + hp + "/" + maxHp + ", 能量 " + energy + ", 格挡 " + block;

                case "get_hand_cards":
                    int handCount = data.has("count") ? data.get("count").getAsInt() : 0;
                    return handCount + " 张手牌";

                case "get_enemies":
                    int enemyCount = data.has("count") ? data.get("count").getAsInt() : 0;
                    return enemyCount + " 个敌人";

                case "get_deck":
                    int deckCount = data.has("count") ? data.get("count").getAsInt() : 0;
                    return deckCount + " 张牌";

                case "get_relics":
                    int relicCount = data.has("count") ? data.get("count").getAsInt() : 0;
                    return relicCount + " 个遗物";

                case "get_card_rewards":
                    int rewardCount = data.has("count") ? data.get("count").getAsInt() : 0;
                    return rewardCount + " 张可选";

                case "get_user_preferences":
                    int prefCount = data.has("count") ? data.get("count").getAsInt() : 0;
                    return prefCount + " 条偏好";

                case "save_user_preference":
                    return "已保存";

                default:
                    return "已获取";
            }
        } catch (Exception e) {
            return "已获取";
        }
    }
}