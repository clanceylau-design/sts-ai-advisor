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
import com.stsaiadvisor.agent.SceneOrchestrator;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.event.EventManager;
import com.stsaiadvisor.llm.LLMClient;
import com.stsaiadvisor.llm.LLMClientFactory;
import com.stsaiadvisor.overlay.OverlayClient;
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
 */
@SpireInitializer
public class STSAIAdvisorMod implements PostInitializeSubscriber, PostRenderSubscriber, PostUpdateSubscriber {

    private static ModConfig config;
    private static LLMClient llmClient;
    private static SceneOrchestrator orchestrator;
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

        // Initialize LLM client (kept for backward compatibility)
        llmClient = LLMClientFactory.createClient(config);
        System.out.println("[AI Advisor] LLM client created: " + llmClient.getClientName());

        // Initialize Scene Orchestrator
        orchestrator = new SceneOrchestrator(config);
        System.out.println("[AI Advisor] Scene Orchestrator initialized");

        // Initialize UI
        panel = new RecommendationPanel();

        // Initialize Overlay client and auto-start
        overlayClient = new OverlayClient();

        // 尝试启动 Overlay
        if (!overlayClient.isAvailable()) {
            startOverlayProcess();
        }

        boolean overlayAvailable = overlayClient.isAvailable();
        System.out.println("[AI Advisor] Overlay available: " + overlayAvailable);

        if (overlayAvailable) {
            // 让 Overlay 显示并显示初始状态
            overlayClient.show();
            System.out.println("[AI Advisor] Overlay shown on startup");
        } else {
            System.err.println("[AI Advisor] WARNING: Overlay not running and failed to start!");
            System.err.println("[AI Advisor] Please run: cd overlay && npm start");
        }

        // Register event listeners (now uses SceneOrchestrator)
        eventManager = new EventManager(orchestrator);

        // Register mod badge
        registerModBadge();

        System.out.println("[AI Advisor] Mod loaded successfully!");
        System.out.println("[AI Advisor] Press F4 to toggle the panel");
        System.out.println("[AI Advisor] Press F3 to request advice manually");
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

    public static LLMClient getLLMClient() {
        return llmClient;
    }

    public static SceneOrchestrator getOrchestrator() {
        return orchestrator;
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
     * <p>尝试启动 Electron Overlay 应用。
     * 查找顺序：
     * <ol>
     *   <li>项目目录下的 overlay/</li>
     *   <li>mod 目录下的 overlay/</li>
     * </ol>
     */
    private static void startOverlayProcess() {
        System.out.println("[AI Advisor] Attempting to start Overlay...");

        // 可能的 Overlay 路径
        String[] possiblePaths = {
            // 项目开发目录
            "C:\\Users\\grenty\\sts-ai-advisor\\overlay",
            // Mod 目录
            "mods\\sts-ai-advisor\\overlay"
        };

        for (String overlayPath : possiblePaths) {
            java.io.File dir = new java.io.File(overlayPath);
            if (dir.exists() && new java.io.File(dir, "main.js").exists()) {
                try {
                    // 使用 npm start 启动
                    ProcessBuilder pb = new ProcessBuilder("npm", "start");
                    pb.directory(dir);
                    pb.redirectErrorStream(true);
                    pb.start();

                    System.out.println("[AI Advisor] Overlay started from: " + overlayPath);

                    // 等待一下让 Overlay 启动
                    Thread.sleep(2000);
                    return;
                } catch (Exception e) {
                    System.err.println("[AI Advisor] Failed to start Overlay from " + overlayPath + ": " + e.getMessage());
                }
            }
        }

        System.err.println("[AI Advisor] Overlay not found in any known location");
    }

    /**
     * Reinitialize the LLM client and orchestrator (call after config changes).
     */
    public static void reinitializeClient() {
        if (config != null) {
            llmClient = LLMClientFactory.createClient(config);
            orchestrator = new SceneOrchestrator(config);
            System.out.println("[AI Advisor] LLM client and orchestrator reinitialized: " + llmClient.getClientName());
        }
    }
}