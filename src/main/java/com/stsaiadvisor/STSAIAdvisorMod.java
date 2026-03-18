package com.stsaiadvisor;

import basemod.BaseMod;
import basemod.ModPanel;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.stsaiadvisor.agent.GameAgent;
import com.stsaiadvisor.config.LocalConfig;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.event.EventManager;
import com.stsaiadvisor.tool.*;
import com.stsaiadvisor.overlay.OverlayClient;
import com.stsaiadvisor.server.ModHttpServer;
import com.stsaiadvisor.ui.RecommendationPanel;
import com.stsaiadvisor.util.Constants;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * STSAIAdvisorMod - Mod主入口
 *
 * <p>职责：
 * <ul>
 *   <li>初始化Mod配置和组件</li>
 *   <li>管理生命周期</li>
 *   <li>提供全局访问点</li>
 * </ul>
 *
 * <p>UI 模式：
 * <ul>
 *   <li>Overlay 模式（默认）：独立的 Electron 悬浮窗口，通过 HTTP 通信</li>
 *   <li>Panel 模式（备用）：游戏内嵌面板</li>
 * </ul>
 *
 * <p>架构：
 * <ul>
 *   <li>GameAgent：单Agent处理所有场景</li>
 *   <li>ToolRegistry：工具注册中心</li>
 *   <li>GameContext：游戏状态访问上下文</li>
 * </ul>
 */
@SpireInitializer
public class STSAIAdvisorMod implements PostInitializeSubscriber, PostRenderSubscriber, PostUpdateSubscriber {

    private static ModConfig config;
    private static GameAgent gameAgent;
    private static ToolRegistry toolRegistry;
    private static RecommendationPanel panel;
    private static EventManager eventManager;
    private static OverlayClient overlayClient;

    /**
     * Required by @SpireInitializer - this is the entry point called by ModTheSpire.
     */
    public static void initialize() {
        // Fix console encoding for Chinese characters
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            // Ignore if UTF-8 is not supported
        }

        new STSAIAdvisorMod();
    }

    /**
     * Constructor - registers all subscribers with BaseMod.
     */
    public STSAIAdvisorMod() {
        BaseMod.subscribe(this);
        System.out.println("[AI Advisor] Mod constructor called");
    }

    /**
     * Called after the game initializes. This is the main entry point.
     */
    @Override
    public void receivePostInitialize() {
        System.out.println("[AI Advisor] Initializing mod...");

        // Load configuration
        config = ModConfig.load();
        System.out.println("[AI Advisor] Config loaded. API configured: " + config.isConfigured());

        // Initialize Overlay client
        overlayClient = new OverlayClient();

        // Initialize Tool Registry and register all tools
        toolRegistry = new ToolRegistry();
        registerTools();
        System.out.println("[AI Advisor] Tool Registry initialized with " + toolRegistry.size() + " tools");

        // Initialize GameAgent
        gameAgent = new GameAgent(config, toolRegistry, overlayClient);
        System.out.println("[AI Advisor] GameAgent initialized");

        // Initialize UI
        panel = new RecommendationPanel();

        // Register event listeners
        eventManager = new EventManager(gameAgent);

        // 启动Mod端HTTP服务器，监听来自Overlay的请求
        ModHttpServer.getInstance().start();

        // 在后台线程启动 Overlay，避免阻塞游戏主线程导致"未响应"
        new Thread(() -> {
            try {
                if (!overlayClient.isAvailable()) {
                    startOverlayProcess();
                }

                if (overlayClient.isAvailable()) {
                    overlayClient.clear();
                    overlayClient.show();
                    System.out.println("[AI Advisor] Overlay cleared and shown on startup");
                } else {
                    System.err.println("[AI Advisor] WARNING: Overlay not running and failed to start!");
                    System.err.println("[AI Advisor] Please run: cd overlay && npm start");
                }
            } catch (Exception e) {
                System.err.println("[AI Advisor] Overlay startup error: " + e.getMessage());
            }
        }, "AI-Advisor-Overlay-Init").start();

        // Register mod badge
        registerModBadge();

        System.out.println("[AI Advisor] Mod loaded successfully!");
        System.out.println("[AI Advisor] Press F4 to toggle the panel");
        System.out.println("[AI Advisor] Press F3 to request advice manually");
    }

    /**
     * 注册所有工具
     */
    private void registerTools() {
        toolRegistry.register(new GetPlayerStateTool());
        toolRegistry.register(new GetHandCardsTool());
        toolRegistry.register(new GetEnemiesTool());
        toolRegistry.register(new GetDeckTool());
        toolRegistry.register(new GetRelicsTool());
        toolRegistry.register(new GetPotionsTool());
        toolRegistry.register(new GetCardRewardsTool());
        toolRegistry.register(new GetEventOptionsTool());
//        toolRegistry.register(new GetTacticalKnowledgeTool());

        // 用户偏好工具
        toolRegistry.register(new GetUserPreferencesTool());
        toolRegistry.register(new SaveUserPreferenceTool());

        // 新增：游戏状态检测工具
        toolRegistry.register(new GetRewardItemsTool());
        toolRegistry.register(new GetMapInfoTool());
        toolRegistry.register(new GetBossInfoTool());
        toolRegistry.register(new GetPilesTool());
        toolRegistry.register(new GetShopItemsTool());

        // 战斗计算工具
        toolRegistry.register(new GuaranteedDamageCalculator());
    }

    /**
     * Register the mod badge in the main menu.
     */
    private void registerModBadge() {
        ModPanel settingsPanel = new ModPanel();

        Texture badgeTexture;
        try {
            // Try to load a custom badge texture
            badgeTexture = new Texture(Gdx.files.internal("img/mod_badge.png"));
        } catch (Exception e) {
            // Create a 1x1 texture as fallback
            badgeTexture = createFallbackTexture();
        }

        BaseMod.registerModBadge(
            badgeTexture,
            Constants.MOD_NAME,
            "AI Advisor Team",
            "An AI-powered advisor that provides real-time battle suggestions.\n\n" +
            "Hotkeys:\n" +
            "- F4: Toggle panel\n" +
            "- F3: Request advice\n\n" +
            "Configure your API key in: mods/sts-ai-advisor/config.json",
            settingsPanel
        );
    }

    /**
     * Create a simple fallback texture.
     */
    private Texture createFallbackTexture() {
        // Create a simple colored texture programmatically
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(64, 64, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pixmap.setColor(0.2f, 0.6f, 0.8f, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /**
     * Called each frame after the game renders.
     *
     * Note: Overlay mode does not need in-game rendering.
     */
    @Override
    public void receivePostRender(SpriteBatch sb) {
        // Overlay handles rendering independently
    }

    /**
     * Called each frame after the game updates.
     */
    @Override
    public void receivePostUpdate() {
        // Event manager handles updates
    }

    // Static getters

    public static ModConfig getConfig() {
        return config;
    }

    public static GameAgent getGameAgent() {
        return gameAgent;
    }

    public static ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public static RecommendationPanel getPanel() {
        return panel;
    }

    public static EventManager getEventManager() {
        return eventManager;
    }

    public static OverlayClient getOverlayClient() {
        return overlayClient;
    }

    /**
     * 检查是否使用 Overlay 模式
     *
     * @return true 如果 Overlay 可用
     */
    public static boolean isOverlayMode() {
        return overlayClient != null && overlayClient.isAvailable();
    }

    /**
     * 启动 Overlay 进程
     *
     * <p>查找顺序：
     * <ol>
     *   <li>Mod 目录下的 overlay.exe（打包后的可执行文件）</li>
     *   <li>项目目录下的 overlay/dist/overlay.exe（开发构建）</li>
     *   <li>项目目录下的 overlay/（开发模式，npm start）</li>
     * </ol>
     *
     * @return true 如果启动成功或已运行
     */
    public static synchronized boolean startOverlayProcess() {
        System.out.println("[AI Advisor] Attempting to start Overlay...");

        // 如果已经在运行，直接返回成功
        if (overlayClient != null && overlayClient.isAvailable()) {
            System.out.println("[AI Advisor] Overlay already running");
            return true;
        }

        // 从配置获取路径
        LocalConfig localConfig = LocalConfig.getInstance();
        String gameDir = localConfig.getGameDir();
        String projectDir = localConfig.getProjectDir();

        // 可能的 overlay.exe 路径（按优先级）
        java.util.List<String> exePaths = new java.util.ArrayList<>();

        // 1. Mod 目录下的 overlay.exe（生产环境）
        exePaths.add(gameDir + "\\mods\\sts-ai-advisor\\overlay.exe");

        // 2. 项目目录下的 dist/overlay.exe（开发构建）
        if (projectDir != null && !projectDir.isEmpty()) {
            exePaths.add(projectDir + "\\overlay\\dist\\overlay.exe");
        }

        // 尝试启动 exe
        boolean started = false;
        for (String exePath : exePaths) {
            java.io.File exeFile = new java.io.File(exePath);
            if (exeFile.exists()) {
                try {
                    System.out.println("[AI Advisor] Found overlay.exe at: " + exePath);
                    ProcessBuilder pb = new ProcessBuilder(exePath);
                    pb.redirectErrorStream(true);
                    pb.start();
                    System.out.println("[AI Advisor] Overlay.exe started");
                    started = true;
                    break;
                } catch (Exception e) {
                    System.err.println("[AI Advisor] Failed to start overlay.exe: " + e.getMessage());
                }
            }
        }

        // 未找到 exe，尝试开发模式（npm start）
        if (!started && projectDir != null && !projectDir.isEmpty()) {
            String overlayDir = projectDir + "\\overlay";
            java.io.File dir = new java.io.File(overlayDir);
            if (dir.exists() && new java.io.File(dir, "main.js").exists()) {
                try {
                    System.out.println("[AI Advisor] Trying dev mode: npm start in " + overlayDir);
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npm", "start");
                    pb.directory(dir);
                    pb.redirectErrorStream(true);
                    pb.start();
                    System.out.println("[AI Advisor] Overlay started (dev mode)");
                    started = true;
                } catch (Exception e) {
                    System.err.println("[AI Advisor] Failed to start Overlay (dev mode): " + e.getMessage());
                }
            }
        }

        // 轮询等待 Overlay 就绪（最多 15 秒）
        if (started && overlayClient != null) {
            for (int i = 0; i < 15; i++) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                if (overlayClient.isAvailable()) {
                    System.out.println("[AI Advisor] Overlay is running (waited " + (i + 1) + "s)");
                    return true;
                }
            }
            System.err.println("[AI Advisor] Overlay process started but not responding after 15s");
        }

        System.err.println("[AI Advisor] Overlay not found. Please run: ./gradlew deployAll");
        return false;
    }

    /**
     * Reinitialize the GameAgent (call after config changes).
     */
    public static void reinitializeClient() {
        if (config != null) {
            gameAgent = new GameAgent(config, toolRegistry, overlayClient);
            System.out.println("[AI Advisor] GameAgent reinitialized");
        }
    }
}