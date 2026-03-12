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
 */
@SpireInitializer
public class STSAIAdvisorMod implements PostInitializeSubscriber, PostRenderSubscriber, PostUpdateSubscriber {

    private static ModConfig config;
    private static LLMClient llmClient;
    private static SceneOrchestrator orchestrator;
    private static RecommendationPanel panel;
    private static EventManager eventManager;

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
     */
    @Override
    public void receivePostRender(SpriteBatch sb) {
        if (panel != null) {
            if (panel.isVisible()) {
                panel.render(sb);
            }
        }
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