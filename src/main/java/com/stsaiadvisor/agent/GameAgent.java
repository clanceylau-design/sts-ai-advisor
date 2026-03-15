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
 * @see ToolRegistry
 * @see GameContext
 */
public class GameAgent {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    /** 最大Tool Call轮次 */
    private static final int MAX_TOOL_ROUNDS = 5;

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final ToolRegistry toolRegistry;
    private final OverlayClient overlayClient;

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
     * 同步处理请求
     */
    private void processSync(String userPrompt, GameContext context) throws IOException {
        String scenario = context.getCurrentScenario();

        // 1. 构建System Prompt
        String systemPrompt = buildSystemPrompt(scenario);

        // 2. 构建消息列表
        List<JsonObject> messages = new ArrayList<>();

        // 添加System消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        // 添加User消息
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

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

                    // 执行工具
                    JsonObject args = GSON.fromJson(argumentsStr, JsonObject.class);
                    ToolResult toolResult = executeTool(toolName, args, context);

                    // 添加tool result消息
                    JsonObject toolResultMsg = new JsonObject();
                    toolResultMsg.addProperty("role", "tool");
                    toolResultMsg.addProperty("tool_call_id", toolCallId);

                    if (toolResult.isSuccess()) {
                        toolResultMsg.addProperty("content", GSON.toJson(toolResult.getData()));
                    } else {
                        toolResultMsg.addProperty("content", "Error: " + toolResult.getError());
                    }

                    messages.add(toolResultMsg);
                }

                // 继续循环
                continue;
            }

            // 没有tool_calls，输出最终内容
            if (result.content != null && !result.content.isEmpty()) {
                System.out.println("[GameAgent] Final content length: " + result.content.length());
                System.out.println("[GameAgent] Final content preview: " + result.content.substring(0, Math.min(100, result.content.length())));
                if (overlayClient != null) {
                    overlayClient.sendUpdate(result.content);
                } else {
                    System.err.println("[GameAgent] overlayClient is null!");
                }
            } else {
                System.out.println("[GameAgent] No final content, result.content=" + (result.content == null ? "null" : "empty"));
            }
            break;
        }

        System.out.println("[GameAgent] Completed in " + round + " rounds");
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
     */
    private ToolResult executeTool(String toolName, JsonObject args, GameContext context) {
        GameTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return ToolResult.failure(toolName, "Tool not found: " + toolName);
        }

        try {
            return tool.execute(args, context).get();
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

        prompt.append("## 可用工具\n");
        prompt.append("你可以使用以下工具获取游戏状态信息：\n");

        for (GameTool tool : toolRegistry.getAvailableTools(scenario)) {
            prompt.append("- ").append(tool.getId()).append(": ").append(tool.getDescription()).append("\n");
        }

        prompt.append("\n## 工作流程\n");
        prompt.append("1. 根据用户请求，调用必要的工具获取信息\n");
        prompt.append("2. 分析当前局势\n");
        prompt.append("3. 给出建议\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("直接给出你的分析和建议，使用友好的语气。\n");

        if ("battle".equals(scenario)) {
            prompt.append("\n## 战斗场景输出格式\n");
            prompt.append("【出牌顺序】\n");
            prompt.append("[卡牌索引] 卡牌名 -> 目标：理由\n");
            prompt.append("\n【策略】核心策略说明\n");
            prompt.append("\n【提示】简短的鼓励语\n");
        } else if ("reward".equals(scenario)) {
            prompt.append("\n## 奖励场景输出格式\n");
            prompt.append("【推荐】[卡牌索引] 卡牌名：理由\n");
            prompt.append("【备选】[卡牌索引] 卡牌名：理由（可选）\n");
            prompt.append("\n【策略】选牌策略说明\n");
            prompt.append("\n【跳过】是/否：理由\n");
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
}