package com.stsaiadvisor.overlay;

import com.google.gson.Gson;
import com.stsaiadvisor.model.Recommendation;
import com.stsaiadvisor.model.CardPlaySuggestion;

import java.io.IOException;
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
 *   <li>POST /update - 更新推荐内容</li>
 *   <li>POST /loading - 显示加载状态</li>
 *   <li>POST /hide - 隐藏面板</li>
 *   <li>POST /show - 显示面板</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * OverlayClient client = new OverlayClient();
 * client.update(recommendation, "battle");
 * client.loading();
 * </pre>
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
     *
     * <p>如果当前可见则隐藏，如果隐藏则显示
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
     * 更新推荐内容
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
    public void loading(String text) {
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

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            // 读取响应（忽略内容）
            conn.getResponseCode();
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
}