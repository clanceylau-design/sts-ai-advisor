package com.stsaiadvisor.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.stsaiadvisor.STSAIAdvisorMod;
import com.stsaiadvisor.event.BattleEventListener;
import com.stsaiadvisor.event.RewardEventListener;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * ModHttpServer - Mod端HTTP服务器
 *
 * <p>监听来自Overlay的触发请求，实现Overlay -> Mod通信
 */
public class ModHttpServer {

    private static final int PORT = 17533;

    private HttpServer server;
    private static ModHttpServer instance;

    private ModHttpServer() {}

    public static synchronized ModHttpServer getInstance() {
        if (instance == null) {
            instance = new ModHttpServer();
        }
        return instance;
    }

    /**
     * 启动HTTP服务器
     */
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // 触发分析端点
            server.createContext("/trigger-analysis", createTriggerHandler());

            // 状态检查端点（供 Overlay watchdog 使用）
            server.createContext("/status", exchange -> {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                sendResponse(exchange, 200, "{\"status\":\"running\"}");
            });

            server.setExecutor(null);
            server.start();

            System.out.println("[ModHttpServer] HTTP服务器启动，端口: " + PORT);
        } catch (IOException e) {
            System.err.println("[ModHttpServer] 启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止HTTP服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[ModHttpServer] HTTP服务器已停止");
        }
    }

    /**
     * 创建触发分析处理器
     *
     * <p>根据当前场景自动选择对应的监听器：
     * <ul>
     *   <li>战斗场景 -> BattleEventListener</li>
     *   <li>奖励场景 -> RewardEventListener</li>
     *   <li>其他场景 -> 直接调用 GameAgent</li>
     * </ul>
     *
     * <p>现在支持在任何场景下对话，工具会根据场景自动过滤可用性
     */
    private HttpHandler createTriggerHandler() {
        return exchange -> {
            // 允许跨域
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                // 检查战斗场景
                BattleEventListener battleListener = STSAIAdvisorMod.getEventManager().getBattleEventListener();
                if (battleListener != null && battleListener.isInBattle()) {
                    // 在新线程中触发分析，避免阻塞HTTP响应
                    new Thread(() -> {
                        battleListener.requestManualAdvice();
                    }).start();

                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Battle analysis triggered\"}");
                    return;
                }

                // 检查奖励场景
                RewardEventListener rewardListener = STSAIAdvisorMod.getEventManager().getRewardEventListener();
                if (rewardListener != null && rewardListener.isInCardReward()) {
                    // 在新线程中触发分析，避免阻塞HTTP响应
                    new Thread(() -> {
                        rewardListener.requestManualAdvice();
                    }).start();

                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Reward analysis triggered\"}");
                    return;
                }

                // 其他场景：通用对话模式
                new Thread(() -> {
                    STSAIAdvisorMod.getEventManager().triggerGeneralAnalysis();
                }).start();

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"General analysis triggered\"}");
            } else {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
            }
        };
    }

    /**
     * 发送JSON响应
     */
    private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}