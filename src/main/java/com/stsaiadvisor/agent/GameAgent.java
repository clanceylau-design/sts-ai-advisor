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
import com.stsaiadvisor.knowledge.ArchetypeLoader;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *   <li>流式输出：所有轮次使用流式调用，实时输出到Overlay</li>
 * </ul>
 *
 * <p>工作流程（Agentic Loop）：
 * <ol>
 *   <li>构建System Prompt + Tool定义</li>
 *   <li>发送初始消息到LLM（流式）</li>
 *   <li>循环处理：LLM返回文本 → 流式输出；LLM请求Tool Call → 执行Tool，返回结果</li>
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
    private static final int MAX_TOOL_ROUNDS = 10;

    /** 最大Token数量（约4KB上下文） */
    private static final int MAX_CONTEXT_TOKENS = 4000;

    /** 最少保留的消息轮数 */
    private static final int MIN_KEEP_ROUNDS = 3;

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final ToolRegistry toolRegistry;
    private final OverlayClient overlayClient;

    /** Archetype加载器（用于注入archetype cards） */
    private final ArchetypeLoader archetypeLoader;

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

        // 初始化Archetype加载器
        this.archetypeLoader = new ArchetypeLoader("mods/sts-ai-advisor/skills-data/archetypes.json");
        this.archetypeLoader.load();
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

        // 4. Agentic Loop
        int round = 0;

        while (round < MAX_TOOL_ROUNDS) {
            round++;

            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", config.getModel());
            requestBody.add("messages", GSON.toJsonTree(messages).getAsJsonArray());
            requestBody.add("tools", tools);
            requestBody.addProperty("max_tokens", 2048);
            requestBody.addProperty("temperature", 0.7);

            // 统一使用流式调用
            LLMResult result = callLLMStreaming(requestBody, messages);

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
                    System.out.println("[GameAgent] Processing tool_call: " + toolCall.toString());
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

            // 没有tool_calls，流式已完成输出
            if (result.content != null && !result.content.isEmpty()) {
                System.out.println("[GameAgent] Streaming completed, content length: " + result.content.length());
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
     * <p>确保消息结构完整性：
     * <ul>
     *   <li>每个 tool 消息前面都有带对应 tool_call_id 的 assistant 消息</li>
     *   <li>assistant 消息中的 tool_calls 都有对应的 tool 消息响应</li>
     * </ul>
     *
     * @param messages 原始消息列表
     * @return 修复后的消息列表
     */
    private List<JsonObject> validateAndFixMessages(List<JsonObject> messages) {
        // 第一遍：处理 assistant 消息，收集有效的 tool_call_ids
        java.util.Set<String> validToolCallIds = new java.util.HashSet<>();
        List<JsonObject> tempMessages = new ArrayList<>();

        for (JsonObject msg : messages) {
            String role = msg.has("role") ? msg.get("role").getAsString() : "";

            if ("assistant".equals(role)) {
                if (msg.has("tool_calls") && !msg.get("tool_calls").isJsonNull()) {
                    JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                    JsonArray validToolCalls = new JsonArray();

                    for (JsonElement tc : toolCalls) {
                        JsonObject tcObj = tc.getAsJsonObject();
                        String tcId = tcObj.has("id") ? tcObj.get("id").getAsString() : "";
                        // 只保留有效的 tool_call（id 非空且以 call_ 或 tc_ 开头）
                        if (tcId.startsWith("call_") || tcId.startsWith("tc_")) {
                            validToolCalls.add(tcObj);
                            validToolCallIds.add(tcId);
                        } else {
                            System.out.println("[GameAgent] 移除无效的 tool_call: " + tcId);
                        }
                    }

                    if (validToolCalls.size() > 0) {
                        JsonObject fixedMsg = copyJsonObject(msg);
                        fixedMsg.add("tool_calls", validToolCalls);
                        tempMessages.add(fixedMsg);
                    } else {
                        // 没有 tool_calls 或都被移除了，检查是否有 content
                        if (msg.has("content") && !msg.get("content").isJsonNull() && !msg.get("content").getAsString().isEmpty()) {
                            JsonObject fixedMsg = copyJsonObject(msg);
                            fixedMsg.remove("tool_calls");
                            tempMessages.add(fixedMsg);
                        } else {
                            System.out.println("[GameAgent] 跳过无内容的 assistant 消息");
                        }
                    }
                } else {
                    tempMessages.add(msg);
                }
            } else {
                tempMessages.add(msg);
            }
        }

        // 第二遍：过滤 tool 消息，只保留有对应 validToolCallIds 的
        List<JsonObject> fixedMessages = new ArrayList<>();
        for (JsonObject msg : tempMessages) {
            String role = msg.has("role") ? msg.get("role").getAsString() : "";

            if ("tool".equals(role)) {
                if (msg.has("tool_call_id")) {
                    String tcId = msg.get("tool_call_id").getAsString();
                    if (validToolCallIds.contains(tcId)) {
                        fixedMessages.add(msg);
                    } else {
                        System.out.println("[GameAgent] 跳过孤立的 tool 消息: " + tcId);
                    }
                } else {
                    System.out.println("[GameAgent] 跳过无 tool_call_id 的 tool 消息");
                }
            } else {
                fixedMessages.add(msg);
            }
        }

        return fixedMessages;
    }

    /**
     * 复制 JsonObject
     */
    private JsonObject copyJsonObject(JsonObject source) {
        return GSON.fromJson(source, JsonObject.class);
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
     * 流式调用LLM API
     *
     * <p>统一使用流式调用，实现实时输出效果：
     * <ul>
     *   <li>流式输出 content delta 到 Overlay</li>
     *   <li>累积 tool_calls delta，返回结果继续 Agentic Loop</li>
     * </ul>
     *
     * @param requestBody 请求体
     * @param messages 消息历史（会被更新）
     * @return LLM结果（包含 tool_calls 信息）
     */
    private LLMResult callLLMStreaming(JsonObject requestBody, List<JsonObject> messages) throws IOException {
        String apiUrl = config.getEffectiveBaseUrl("https://api.openai.com/v1/chat/completions");

        // 设置 stream=true
        requestBody.addProperty("stream", true);
        String requestBodyStr = GSON.toJson(requestBody);

        Request request = new Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBodyStr, JSON))
            .build();

        long startTime = System.currentTimeMillis();
        LLMResult result = new LLMResult();
        StringBuilder contentBuilder = new StringBuilder();
        List<JsonObject> toolCallsList = new ArrayList<>();
        Map<Integer, JsonObject> toolCallsMap = new java.util.HashMap<>(); // 按索引累积 tool_call

        // 开始流式输出
        if (overlayClient != null) {
            overlayClient.sendStreamStart();
        }

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                System.err.println("[GameAgent] Streaming API error: " + response.code() + " - " + errorBody);
                // 结束流式
                if (overlayClient != null) {
                    overlayClient.sendStreamEnd();
                }
                return null;
            }

            // 读取 SSE 流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();

                        if ("[DONE]".equals(data)) {
                            System.out.println("[GameAgent] SSE: [DONE]");
                            break;
                        }

                        // 调试：打印原始 SSE 数据
                        System.out.println("[GameAgent] SSE data: " + data);

                        try {
                            JsonObject chunk = GSON.fromJson(data, JsonObject.class);
                            JsonObject delta = extractDelta(chunk);

                            if (delta != null) {
                                // 处理 content delta
                                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                    String contentDelta = delta.get("content").getAsString();
                                    contentBuilder.append(contentDelta);
                                    System.out.println("[GameAgent] Content delta: " + contentDelta.replace("\n", "\\n"));

                                    // 流式输出到 overlay
                                    if (overlayClient != null) {
                                        overlayClient.sendStreamChunk(contentDelta);
                                    }
                                }

                                // 处理 tool_calls delta
                                if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull()) {
                                    JsonArray toolCallsDelta = delta.getAsJsonArray("tool_calls");
                                    System.out.println("[GameAgent] Tool calls delta: " + toolCallsDelta.toString());

                                    for (JsonElement tcElement : toolCallsDelta) {
                                        JsonObject tcDelta = tcElement.getAsJsonObject();
                                        int index = tcDelta.has("index") ? tcDelta.get("index").getAsInt() : 0;
                                        System.out.println("[GameAgent] tcDelta[" + index + "]: " + tcDelta.toString());

                                        // 获取或创建对应索引的 tool_call 对象
                                        JsonObject tc = toolCallsMap.computeIfAbsent(index, k -> {
                                            JsonObject newTc = new JsonObject();
                                            newTc.addProperty("id", "");
                                            newTc.addProperty("type", "function");
                                            JsonObject func = new JsonObject();
                                            func.addProperty("name", "");
                                            func.addProperty("arguments", "");
                                            newTc.add("function", func);
                                            return newTc;
                                        });

                                        // 累积字段（只有非空时才覆盖）
                                        if (tcDelta.has("id") && !tcDelta.get("id").isJsonNull()) {
                                            String newId = tcDelta.get("id").getAsString();
                                            if (!newId.isEmpty()) {
                                                tc.addProperty("id", newId);
                                                System.out.println("[GameAgent] Accumulated id: " + newId);
                                            }
                                        }
                                        if (tcDelta.has("function")) {
                                            JsonObject funcDelta = tcDelta.getAsJsonObject("function");
                                            JsonObject func = tc.getAsJsonObject("function");

                                            if (funcDelta.has("name") && !funcDelta.get("name").isJsonNull()) {
                                                String newName = funcDelta.get("name").getAsString();
                                                if (!newName.isEmpty()) {
                                                    func.addProperty("name", newName);
                                                    System.out.println("[GameAgent] Accumulated name: " + newName);
                                                }
                                            }
                                            if (funcDelta.has("arguments") && !funcDelta.get("arguments").isJsonNull()) {
                                                String args = func.has("arguments") ? func.get("arguments").getAsString() : "";
                                                func.addProperty("arguments", args + funcDelta.get("arguments").getAsString());
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (JsonSyntaxException e) {
                            // 忽略解析错误，继续处理
                        }
                    }
                }
            }
        }

        // 打印累积结果
        System.out.println("[GameAgent] Accumulated toolCallsMap: " + toolCallsMap.toString());
        System.out.println("[GameAgent] Accumulated content length: " + contentBuilder.length());

        // 检查是否有有效的 tool_calls
        List<JsonObject> validToolCalls = new ArrayList<>();
        for (JsonObject tc : toolCallsMap.values()) {
            String tcId = tc.has("id") ? tc.get("id").getAsString() : "";
            JsonObject func = tc.has("function") ? tc.getAsJsonObject("function") : null;
            String funcName = (func != null && func.has("name") && !func.get("name").isJsonNull())
                ? func.get("name").getAsString() : "";

            // 只保留有效的 tool_call（id 和 name 都非空）
            if (!tcId.isEmpty() && !funcName.isEmpty()) {
                validToolCalls.add(tc);
            } else {
                System.out.println("[GameAgent] 过滤无效的 tool_call: id=" + tcId + ", name=" + funcName);
            }
        }

        if (!validToolCalls.isEmpty()) {
            result.hasToolCalls = true;
            result.toolCalls = validToolCalls;

            // 有 tool_calls 时，必须添加 assistant 消息（包含 tool_calls）
            JsonObject assistantMessage = new JsonObject();
            assistantMessage.addProperty("role", "assistant");

            // 构建 tool_calls 数组
            JsonArray toolCallsArray = new JsonArray();
            for (JsonObject tc : result.toolCalls) {
                toolCallsArray.add(tc);
            }
            assistantMessage.add("tool_calls", toolCallsArray);

            // 如果有 content 也添加
            if (contentBuilder.length() > 0) {
                assistantMessage.addProperty("content", contentBuilder.toString());
            }

            messages.add(assistantMessage);
            conversationHistory.add(assistantMessage);
            System.out.println("[GameAgent] Added assistant message with tool_calls to messages");
        } else {
            result.content = contentBuilder.toString();
        }

        // 结束流式输出
        if (overlayClient != null) {
            overlayClient.sendStreamEnd();
        }

        // 如果没有 tool_calls，添加 assistant 消息
        if (!result.hasToolCalls && contentBuilder.length() > 0) {
            JsonObject assistantMessage = new JsonObject();
            assistantMessage.addProperty("role", "assistant");
            assistantMessage.addProperty("content", result.content);
            messages.add(assistantMessage);
            conversationHistory.add(assistantMessage);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[GameAgent] Streaming completed in " + elapsed + "ms, hasToolCalls=" + result.hasToolCalls + ", contentLength=" + contentBuilder.length());
        if (contentBuilder.length() > 0) {
            System.out.println("[GameAgent] Content preview: " + contentBuilder.substring(0, Math.min(100, contentBuilder.length())));
        }

        // 记录 LLM 日志
        String responseSummary = result.hasToolCalls
            ? "Tool calls: " + result.toolCalls.size()
            : "Content: " + (result.content != null ? result.content.substring(0, Math.min(200, result.content.length())) : "null");
        LLMLogger.logRequest(
            "GameAgent", apiUrl, config.getModel(),
            "System prompt", "User prompt",
            requestBodyStr, responseSummary,
            elapsed, true
        );

        return result;
    }

    /**
     * 从 SSE chunk 中提取 delta
     */
    private JsonObject extractDelta(JsonObject chunk) {
        if (!chunk.has("choices")) return null;

        JsonArray choices = chunk.getAsJsonArray("choices");
        if (choices.size() == 0) return null;

        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!choice.has("delta")) return null;

        return choice.getAsJsonObject("delta");
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

        prompt.append("## 游戏基本规则\n");
        prompt.append("杀戮尖塔是Roguelike卡牌构筑游戏，目标是爬塔通关。\n\n");
        prompt.append("【回合流程】每回合：抽5张牌（遗物或药水可以影响） → 消耗能量出牌 → 回合结束弃牌 → 牌库空时洗牌。\n");
        prompt.append("【能量】每回合3点能量（遗物或药水可以影响），需合理分配避免浪费。\n");
        prompt.append("【卡牌类型】攻击牌（造成伤害）、技能牌（各种效果）、能力牌（持续生效）。\n");
        prompt.append("【关键状态】格挡（抵消伤害/下回合清空）、力量（攻击伤害+N）、中毒（每回合扣血）。\n");
        prompt.append("【构筑原则】提前规划构筑流派,优先抓取高质量单卡或流派核心组件,避免盲目拿牌导致卡组臃肿卡手。\n\n");


        prompt.append("## 记忆机制\n");
        prompt.append("你有对话记忆能力，可以记住之前的工具调用和结果。\n");
        prompt.append("- 如果已经获取过某些信息，不需要重复调用工具\n");
        prompt.append("- 【实时信息】工具每回合都需要调用获取最新数据\n");
        prompt.append("- 【稳定信息】工具结果会被缓存，可直接使用之前的结果\n\n");

        prompt.append("## 用户偏好学习\n");
        prompt.append("你可以学习和记住用户的游戏偏好：\n");
        prompt.append("- 使用 get_user_preferences 获取用户的历史偏好\n");
        prompt.append("- 当用户明确表达对你的建议有不同看法时，使用 save_user_preference 保存偏好\n");
        prompt.append("- 提供建议时应参考用户的偏好，但也要考虑当前局势\n\n");

        prompt.append("## 可用工具\n");
        prompt.append("你可以使用以下工具获取游戏状态信息：\n");

        for (GameTool tool : toolRegistry.getAvailableTools(scenario)) {
            prompt.append("- ").append(tool.getId()).append(": ").append(tool.getDescription()).append("\n");
        }
        //skills效果太差，先不用
//        // 注入Archetype Cards
//        prompt.append("## 可参考的出牌/卡组构筑攻略技巧:\n");
//        prompt.append("以下是当前角色卡组构筑可用的流派技巧，当玩家手牌/牌组包含核心卡牌时，应参考对应流派策略：\n\n");
//        prompt.append(getArchetypeCardsForPrompt());
//        prompt.append("\n提示：使用 get_tactical_knowledge 工具可获取详细的流派策略文档。\n\n");


        if ("battle".equals(scenario)) {
            prompt.append("## 工作流程\n" +
                    "1. 调用 get_user_preferences、get_player_state、get_hand_cards、get_enemies\n" +
                    "2. 如尚未获取遗物/牌组信息，调用 get_relics、get_deck（稳定信息，同一战斗内无需重复调用）\n" +
                    "3. 调用 get_tactical_knowledge，获取流派知识\n" +
                    "4. 进行出牌前分析（必须在输出建议前完成，且必须写出以下内容）：\n" +
                    "   - 敌人意图：[攻击/强化/睡眠/…] → 本回合是否需要格挡？\n" +
                    "   - 遗物约束检查：当前遗物是否影响格挡/伤害策略？（例：奥利哈钢→无格挡时自动补6，避免手动打低效防御）\n" +
                    "   - 手牌约束检查：逐张列出每张牌的类型，标注有条件限制的牌是否满足打出前提\n" +
                    "   - 能量分配：列出候选出牌组合及对应总伤害/格挡值\n" +
                    "   - 用户偏好对照：当前建议是否与已记录偏好冲突？\n" +
                    "5. 给出最终建议\n" +
                    "6. 若用户纠正或表达偏好，调用 save_user_preference\n");

            prompt.append("## 输出格式\n");
            prompt.append("直接给出你的分析和建议，使用友好的语气。\n");
            prompt.append("【出牌】[索引]卡牌名→目标：理由\n");
            prompt.append("【策略】一句话（≤20字）\n");
        } else if ("reward".equals(scenario)) {
            prompt.append("## 奖励场景分析模板（必须在输出推荐前完整填写）\n" +
                    "【卡组诊断】\n" +
                    "- 当前流派方向：___（根据核心牌判断，如\"交锋攻击流\"、\"撕裂力量流\"）\n" +
                    "- 卡组短板：___（如\"缺AOE\"、\"缺稳定格挡\"、\"缺过牌\"）\n" +
                    "- 关键遗物约束：___（影响选牌方向的遗物，如\"有奥利哈钢→防御牌价值下降\"）\n" +
                    "【候选牌逐张评估】\n" +
                    "对每张候选牌，必须回答：\n" +
                    "- 这张牌在当前卡组里的边际价值是什么？\n" +
                    "- 是一次性收益还是跨回合/持续收益？\n" +
                    "- 若是条件牌或buff牌，量化其在当前卡组中的实际收益\n" +
                    "  （例：3点力量 × 11张攻击牌 = 每循环额外约33伤害）\n" +
                    "- 是否与已记录的用户偏好冲突或契合？\n" +
                    "【跳过判断】\n" +
                    "- 三张牌是否都弱于当前卡组平均水平？是→跳过，否→选最优\n" +
                    "【推荐】[索引]卡牌名：理由（一句话，包含核心量化依据）\n" +
                    "【跳过奖励】是/否：理由");
            prompt.append("## 输出格式\n");
            prompt.append("【推荐】[索引]卡牌名：理由\n");
            prompt.append("【跳过奖励】是/否：理由\n");
        } else if ("shop".equals(scenario)) {
            prompt.append("## 商店场景分析模板（必须在输出推荐前完整填写）\n" +
                    "【当前状态】\n" +
                    "- 当前金币：___\n" +
                    "- 当前卡组特点：___（流派方向、卡组大小）\n" +
                    "- 关键遗物：___\n" +
                    "【商品评估】\n" +
                    "对每个商品，评估：\n" +
                    "- 性价比：价格是否合理？（参考：普通卡约50-100金，遗物约150金）\n" +
                    "- 卡组契合度：是否增强当前流派或补足短板？\n" +
                    "- 边际收益：购买后对通关的帮助程度\n" +
                    "【卡牌移除服务】\n" +
                    "- 卡组是否有需要移除的牌？（诅咒牌、低效打击/防御）\n" +
                    "- 当前金币是否足够？移除后卡组精简程度如何？\n" +
                    "【购买建议】\n" +
                    "- 必买：性价比高且契合卡组\n" +
                    "- 可选：有一定价值但非必需\n" +
                    "- 不推荐：性价比低或与卡组不契合\n");
            prompt.append("## 输出格式\n");
            prompt.append("【必买】商品名：理由\n");
            prompt.append("【可选】商品名：理由\n");
            prompt.append("【不推荐】商品名：理由\n");
            prompt.append("【移除建议】是/否：理由\n");
        } else if ("map".equals(scenario)) {
            prompt.append("## 地图场景分析模板\n" +
                    "【当前位置】\n" +
                    "- 当前层数：___\n" +
                    "- 当前幕数：___\n" +
                    "【路线规划考虑因素】\n" +
                    "- 血量状态：血量健康可选精英/事件，血量低优先休息/商店\n" +
                    "- 卡组强度：卡组强可选精英，卡组弱优先问号事件避免战斗\n" +
                    "- 金币情况：金币充足可跳过商店，金币少可考虑商店\n" +
                    "- Boss信息：根据可能Boss准备对策\n" +
                    "【房间优先级】\n" +
                    "- 精英：高风险高回报（遗物）\n" +
                    "- 休息：恢复血量或强化卡牌\n" +
                    "- 商店：移除卡牌、购买遗物/药水\n" +
                    "- 问号：随机事件，可能获得遗物/卡牌/金币\n" +
                    "- 普通：低风险战斗\n");
            prompt.append("## 输出格式\n");
            prompt.append("【建议路线】下一步选择：理由\n");
            prompt.append("【后续规划】优先选择的房间类型\n");
        }

        return prompt.toString();
    }

    /**
     * 获取archetype cards用于System Prompt
     */
    private String getArchetypeCardsForPrompt() {
        // 获取当前角色（从context中获取，这里暂时返回所有角色的cards）
        // TODO: 根据实际游戏角色动态筛选
        if (!archetypeLoader.isLoaded()) {
            return "（流派信息加载失败）";
        }

        StringBuilder sb = new StringBuilder();

        // 按角色分组
        sb.append("### 铁甲战士\n");
        sb.append(archetypeLoader.getArchetypeCards("IRONCLAD"));

        sb.append("\n### 静默猎人\n");
        sb.append(archetypeLoader.getArchetypeCards("THE_SILENT"));

        sb.append("\n### 故障机器人\n");
        sb.append(archetypeLoader.getArchetypeCards("DEFECT"));

        return sb.toString();
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
            case "shop":
                return "帮我分析一下商店里值得买的东西";
            case "map":
                return "帮我分析一下接下来的路线选择";
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
            case "get_piles":
                return "牌堆信息";
            case "get_reward_items":
                return "奖励物品";
            case "get_map_info":
                return "地图信息";
            case "get_boss_info":
                return "Boss信息";
            case "get_shop_items":
                return "商店商品";
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

                case "get_piles":
                    JsonObject drawPile = data.has("draw_pile") ? data.getAsJsonObject("draw_pile") : null;
                    JsonObject discardPile = data.has("discard_pile") ? data.getAsJsonObject("discard_pile") : null;
                    JsonObject exhaustPile = data.has("exhaust_pile") ? data.getAsJsonObject("exhaust_pile") : null;
                    int draw = drawPile != null && drawPile.has("count") ? drawPile.get("count").getAsInt() : 0;
                    int discard = discardPile != null && discardPile.has("count") ? discardPile.get("count").getAsInt() : 0;
                    int exhaust = exhaustPile != null && exhaustPile.has("count") ? exhaustPile.get("count").getAsInt() : 0;
                    return "抽" + draw + " 弃" + discard + " 消耗" + exhaust;

                case "get_shop_items":
                    JsonArray cards = data.has("cards") ? data.getAsJsonArray("cards") : null;
                    JsonArray relics = data.has("relics") ? data.getAsJsonArray("relics") : null;
                    JsonArray potions = data.has("potions") ? data.getAsJsonArray("potions") : null;
                    int cardCount = cards != null ? cards.size() : 0;
                    int relicCount2 = relics != null ? relics.size() : 0;
                    int potionCount = potions != null ? potions.size() : 0;
                    int gold = data.has("player_gold") ? data.get("player_gold").getAsInt() : 0;
                    return cardCount + "卡 " + relicCount2 + "遗物 " + potionCount + "药水 金" + gold;

                case "get_map_info":
                    int act = data.has("act") ? data.get("act").getAsInt() : 0;
                    int floor = data.has("current_floor") ? data.get("current_floor").getAsInt() : 0;
                    return "Act" + act + " 层数" + floor;

                case "get_boss_info":
                    String bossName = data.has("boss_name") && !data.get("boss_name").isJsonNull()
                        ? data.get("boss_name").getAsString() : "未知";
                    return "Boss: " + bossName;

                case "get_reward_items":
                    JsonArray relicRewards = data.has("relic_rewards") ? data.getAsJsonArray("relic_rewards") : null;
                    JsonArray potionRewards = data.has("potion_rewards") ? data.getAsJsonArray("potion_rewards") : null;
                    int relicR = relicRewards != null ? relicRewards.size() : 0;
                    int potionR = potionRewards != null ? potionRewards.size() : 0;
                    return relicR + "遗物 " + potionR + "药水";

                default:
                    return "已获取";
            }
        } catch (Exception e) {
            return "已获取";
        }
    }
}