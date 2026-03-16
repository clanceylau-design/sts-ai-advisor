package com.stsaiadvisor.overlay;

import com.google.gson.Gson;
import com.stsaiadvisor.model.Recommendation;
import com.stsaiadvisor.model.CardPlaySuggestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Overlay 客户端
 *
 * <p>职责：通过 HTTP 将推荐数据推送到独立的 Overlay 窗口
 *
 * <p>通信协议：
 * <ul>
 *   <li>POST /update - 更新推荐内容（结构化数据或纯文本）</li>
 *   <li>POST /loading - 显示加载状态</li>
 *   <li>POST /hide - 隐藏面板</li>
 *   <li>POST /show - 显示面板</li>
 *   <li>POST /clear - 清空面板内容</li>
 * </ul>
 */
public class OverlayClient {

    /** Overlay HTTP 服务端口 */
    private static final int PORT = 17532;

    /** 本地地址 */
    private static final String HOST = "http://localhost:" + PORT;

    private final Gson gson;
    private boolean enabled = true;

    /** 面板是否可见 */
    private boolean visible = true;

    public OverlayClient() {
        this.gson = new Gson();
    }

    /**
     * 检查 Overlay 是否可用
     *
     * @return true 如果 Overlay 正在运行
     */
    public boolean isAvailable() {
        try {
            URL url = new URL(HOST + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);

            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 切换 Overlay 显示/隐藏
     */
    public void toggle() {
        if (!enabled) return;

        if (visible) {
            hide();
        } else {
            show();
        }
    }

    /**
     * 面板是否可见
     *
     * @return 可见状态
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 更新推荐内容（结构化数据）
     *
     * @param recommendation 推荐数据
     * @param scenario 场景类型：battle / reward
     */
    public void update(Recommendation recommendation, String scenario) {
        if (!enabled || recommendation == null) return;

        OverlayUpdate data = new OverlayUpdate();
        data.scenario = scenario;
        data.companionMessage = recommendation.getCompanionMessage();
        data.reasoning = recommendation.getReasoning();

        if (recommendation.hasSuggestions()) {
            data.suggestions = new OverlaySuggestion[recommendation.getSuggestions().size()];
            for (int i = 0; i < recommendation.getSuggestions().size(); i++) {
                CardPlaySuggestion s = recommendation.getSuggestions().get(i);
                OverlaySuggestion os = new OverlaySuggestion();
                os.cardIndex = s.getCardIndex();
                os.cardName = s.getCardName();
                os.targetName = s.getTargetName();
                os.priority = s.getPriority();
                os.reason = s.getReason();
                data.suggestions[i] = os;
            }
        }

        post("/update", data);
    }

    /**
     * 更新推荐内容（纯文本）
     *
     * @param text 文本内容（Markdown格式）
     */
    public void sendUpdate(String text) {
        if (!enabled || text == null) {
            System.out.println("[OverlayClient] sendUpdate skipped: enabled=" + enabled + ", text=" + (text == null ? "null" : "not null"));
            return;
        }

        TextUpdate data = new TextUpdate();
        data.text = text;
        System.out.println("[OverlayClient] Sending update, text length: " + text.length());
        post("/update", data);
    }

    /**
     * 显示加载状态
     */
    public void loading() {
        if (!enabled) return;
        post("/loading", new Object());
    }

    /**
     * 显示加载状态（带文字）
     *
     * @param text 加载文字
     */
    public void sendLoading(String text) {
        if (!enabled) return;
        LoadingData data = new LoadingData();
        data.loadingText = text;
        post("/loading", data);
    }

    /**
     * 隐藏面板
     */
    public void hide() {
        if (!enabled) return;
        post("/hide", new Object());
        visible = false;
    }

    /**
     * 显示面板
     */
    public void show() {
        if (!enabled) return;
        post("/show", new Object());
        visible = true;
    }

    /**
     * 清空面板内容（不隐藏窗口）
     */
    public void clear() {
        if (!enabled) return;
        post("/clear", new Object());
    }

    /**
     * 获取自定义提示词
     *
     * <p>从Overlay获取用户输入的自定义提示词，获取后会自动清空
     *
     * @return 自定义提示词，如果没有则返回null
     */
    public String getCustomPrompt() {
        if (!enabled) return null;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(HOST + "/custom-prompt");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int responseCode = conn.getResponseCode();
            System.out.println("[OverlayClient] GET /custom-prompt 响应码: " + responseCode);

            if (responseCode == 200) {
                try (InputStream is = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    String response = baos.toString("UTF-8");
                    System.out.println("[OverlayClient] 响应内容: " + response);
                    com.google.gson.JsonObject json = gson.fromJson(response, com.google.gson.JsonObject.class);
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        if (json.has("prompt") && !json.get("prompt").isJsonNull()) {
                            String prompt = json.get("prompt").getAsString();
                            System.out.println("[OverlayClient] 获取到自定义提示词: " + prompt);
                            return prompt;
                        }
                    } else {
                        System.out.println("[OverlayClient] Overlay返回: success=false (无自定义提示词)");
                    }
                }
            } else {
                System.out.println("[OverlayClient] 响应码非200: " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("[OverlayClient] 获取自定义提示词失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    /**
     * 设置是否启用
     *
     * @param enabled true 启用，false 禁用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否启用
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 发送 POST 请求
     *
     * @param path 请求路径
     * @param data 请求数据
     */
    private void post(String path, Object data) {
        try {
            URL url = new URL(HOST + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(200);
            conn.setReadTimeout(200);

            String json = gson.toJson(data);
            System.out.println("[OverlayClient] POST " + path + " JSON: " + json.substring(0, Math.min(200, json.length())));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            // 读取响应（忽略内容）
            int responseCode = conn.getResponseCode();
            System.out.println("[OverlayClient] Response code: " + responseCode);
            conn.disconnect();

        } catch (IOException e) {
            // Overlay 未运行时静默失败
            System.err.println("[OverlayClient] 连接失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 数据传输对象
    // ============================================================

    /** 更新请求数据 */
    private static class OverlayUpdate {
        String scenario;
        String companionMessage;
        String reasoning;
        OverlaySuggestion[] suggestions;
    }

    /** 建议数据 */
    private static class OverlaySuggestion {
        int cardIndex;
        String cardName;
        String targetName;
        int priority;
        String reason;
    }

    /** 加载数据 */
    private static class LoadingData {
        String loadingText;
    }

    /** 纯文本更新 */
    private static class TextUpdate {
        String text;
    }
}