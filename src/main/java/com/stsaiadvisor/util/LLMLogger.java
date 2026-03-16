package com.stsaiadvisor.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * LLM API 请求日志记录器
 *
 * <p>日志格式说明：
 * <ul>
 *   <li>请求摘要：快速了解请求内容</li>
 *   <li>消息流：以可读格式显示对话历史</li>
 *   <li>原始数据：完整JSON（折叠显示）</li>
 * </ul>
 */
public class LLMLogger {
    private static final String LOG_DIR = "mods/sts-ai-advisor/logs/";
    private static final String LOG_FILE = LOG_DIR + "llm_requests.log";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 记录 LLM 请求和响应
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

                // 请求摘要
                writer.write("--- 请求摘要 ---\n");
                writer.write("Provider: " + provider + "\n");
                writer.write("Model: " + model + "\n");
                writer.write("Duration: " + durationMs + "ms\n");
                writer.write("Success: " + success + "\n");
                writer.write("\n");

                // 解析请求体
                JsonObject requestJson = null;
                JsonArray messagesArray = null;
                try {
                    requestJson = GSON.fromJson(requestBody, JsonObject.class);
                    if (requestJson.has("messages")) {
                        messagesArray = requestJson.getAsJsonArray("messages");
                    }
                } catch (Exception e) {
                    // ignore
                }

                // 消息流（可读格式）
                writer.write("--- 消息流 ---\n");
                if (messagesArray != null) {
                    formatMessagesFlow(writer, messagesArray);
                } else {
                    writer.write("(无法解析消息)\n");
                }
                writer.write("\n");

                // 响应摘要
                writer.write("--- 响应摘要 ---\n");
                try {
                    JsonObject responseJson = GSON.fromJson(responseBody, JsonObject.class);
                    formatResponseSummary(writer, responseJson);
                } catch (Exception e) {
                    writer.write("(无法解析响应)\n");
                }
                writer.write("\n");

                // 原始数据（折叠格式，用于调试）
                writer.write("--- 原始请求 (JSON) ---\n");
                if (requestJson != null) {
                    // 折叠 messages 数组，只显示结构
                    JsonObject compactRequest = compactRequestForDisplay(requestJson);
                    writer.write(GSON.toJson(compactRequest) + "\n");
                } else {
                    writer.write(requestBody + "\n");
                }
                writer.write("\n");

                writer.write("--- 原始响应 (JSON) ---\n");
                try {
                    JsonObject responseJson = GSON.fromJson(responseBody, JsonObject.class);
                    writer.write(GSON.toJson(responseJson) + "\n");
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
     * 格式化消息流（可读格式）
     */
    private static void formatMessagesFlow(Writer writer, JsonArray messages) throws IOException {
        int msgIndex = 0;
        for (JsonElement elem : messages) {
            JsonObject msg = elem.getAsJsonObject();
            String role = msg.has("role") ? msg.get("role").getAsString() : "unknown";

            msgIndex++;
            writer.write("[" + msgIndex + "] " + role.toUpperCase() + "\n");

            switch (role) {
                case "system":
                    // System 消息显示前100字符
                    String sysContent = msg.has("content") ? msg.get("content").getAsString() : "";
                    String sysPreview = sysContent.length() > 100 ? sysContent.substring(0, 100) + "..." : sysContent;
                    writer.write("    " + sysPreview.replace("\n", " ") + "\n");
                    break;

                case "user":
                    // User 消息显示完整内容
                    String userContent = msg.has("content") ? msg.get("content").getAsString() : "";
                    writer.write("    " + userContent + "\n");
                    break;

                case "assistant":
                    // Assistant 消息：显示内容和 tool_calls
                    if (msg.has("content") && !msg.get("content").isJsonNull()) {
                        String asstContent = msg.get("content").getAsString();
                        writer.write("    内容: " + truncate(asstContent, 200) + "\n");
                    }
                    if (msg.has("tool_calls") && !msg.get("tool_calls").isJsonNull()) {
                        JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
                        writer.write("    工具调用:\n");
                        for (JsonElement tc : toolCalls) {
                            JsonObject tcObj = tc.getAsJsonObject();
                            if (tcObj.has("function")) {
                                JsonObject func = tcObj.getAsJsonObject("function");
                                String toolName = func.has("name") ? func.get("name").getAsString() : "unknown";
                                writer.write("      → " + toolName + "\n");
                            }
                        }
                    }
                    break;

                case "tool":
                    // Tool 消息：显示工具名和结果摘要
                    String toolCallId = msg.has("tool_call_id") ? msg.get("tool_call_id").getAsString() : "?";
                    String toolContent = msg.has("content") ? msg.get("content").getAsString() : "";
                    writer.write("    tool_call_id: " + toolCallId + "\n");
                    writer.write("    结果: " + truncate(toolContent, 100) + "\n");
                    break;
            }
            writer.write("\n");
        }
    }

    /**
     * 格式化响应摘要
     */
    private static void formatResponseSummary(Writer writer, JsonObject response) throws IOException {
        if (response.has("choices")) {
            JsonArray choices = response.getAsJsonArray("choices");
            if (choices.size() > 0) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject message = choice.getAsJsonObject("message");

                // 显示内容
                if (message.has("content") && !message.get("content").isJsonNull()) {
                    String content = message.get("content").getAsString();
                    writer.write("回复内容: " + truncate(content, 300) + "\n");
                }

                // 显示 tool_calls
                if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                    JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                    writer.write("工具调用:\n");
                    for (JsonElement tc : toolCalls) {
                        JsonObject tcObj = tc.getAsJsonObject();
                        if (tcObj.has("function")) {
                            JsonObject func = tcObj.getAsJsonObject("function");
                            String toolName = func.has("name") ? func.get("name").getAsString() : "unknown";
                            String args = func.has("arguments") ? func.get("arguments").getAsString() : "{}";
                            writer.write("  → " + toolName + "(" + truncate(args, 50) + ")\n");
                        }
                    }
                }
            }
        }

        // Usage 信息
        if (response.has("usage")) {
            JsonObject usage = response.getAsJsonObject("usage");
            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
            int totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").getAsInt() : 0;
            writer.write("Token 使用: prompt=" + promptTokens + ", completion=" + completionTokens + ", total=" + totalTokens + "\n");
        }
    }

    /**
     * 压缩请求体用于显示（折叠长内容）
     */
    private static JsonObject compactRequestForDisplay(JsonObject request) {
        JsonObject compact = new JsonObject();

        // 复制基本字段
        if (request.has("model")) {
            compact.addProperty("model", request.get("model").getAsString());
        }
        if (request.has("max_tokens")) {
            compact.addProperty("max_tokens", request.get("max_tokens").getAsInt());
        }
        if (request.has("temperature")) {
            compact.addProperty("temperature", request.get("temperature").getAsDouble());
        }

        // 折叠 messages 数组
        if (request.has("messages")) {
            JsonArray messages = request.getAsJsonArray("messages");
            JsonArray compactMessages = new JsonArray();

            for (JsonElement elem : messages) {
                JsonObject msg = elem.getAsJsonObject();
                JsonObject compactMsg = new JsonObject();

                String role = msg.has("role") ? msg.get("role").getAsString() : "unknown";
                compactMsg.addProperty("role", role);

                if ("system".equals(role)) {
                    compactMsg.addProperty("content", "[系统提示词，长度: " +
                        (msg.has("content") ? msg.get("content").getAsString().length() : 0) + " 字符]");
                } else if ("user".equals(role)) {
                    String content = msg.has("content") ? msg.get("content").getAsString() : "";
                    compactMsg.addProperty("content", truncate(content, 100));
                } else if ("assistant".equals(role)) {
                    if (msg.has("content") && !msg.get("content").isJsonNull()) {
                        compactMsg.addProperty("content", truncate(msg.get("content").getAsString(), 100));
                    }
                    if (msg.has("tool_calls")) {
                        compactMsg.add("tool_calls", msg.get("tool_calls"));
                    }
                } else if ("tool".equals(role)) {
                    compactMsg.addProperty("tool_call_id", msg.has("tool_call_id") ? msg.get("tool_call_id").getAsString() : "?");
                    String content = msg.has("content") ? msg.get("content").getAsString() : "";
                    compactMsg.addProperty("content", truncate(content, 100));
                }

                compactMessages.add(compactMsg);
            }

            compact.add("messages", compactMessages);
        }

        // 工具定义（显示工具名称列表）
        if (request.has("tools")) {
            JsonArray tools = request.getAsJsonArray("tools");
            JsonArray toolNames = new JsonArray();
            for (JsonElement tool : tools) {
                JsonObject toolObj = tool.getAsJsonObject();
                if (toolObj.has("function")) {
                    JsonObject func = toolObj.getAsJsonObject("function");
                    if (func.has("name")) {
                        toolNames.add(func.get("name").getAsString());
                    }
                }
            }
            compact.add("available_tools", toolNames);
        }

        return compact;
    }

    /**
     * 截断字符串
     */
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Log a simple message.
     */
    public static void log(String message) {
        System.out.println("[AI Advisor] " + message);
    }
}