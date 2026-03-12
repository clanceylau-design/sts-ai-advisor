package com.stsaiadvisor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Mod configuration management.
 */
public class ModConfig {
    private static final String CONFIG_DIR = "mods/sts-ai-advisor/";
    private static final String CONFIG_FILE = CONFIG_DIR + "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String apiKey = "";
    private String baseUrl = ""; // Custom API base URL (empty = use default)
    private String model = "claude-3-5-sonnet-20241022";
    private String apiProvider = "anthropic"; // anthropic or openai
    private boolean enableAutoAdvice = true;
    private boolean showReasoning = true;
    private int requestTimeout = 30; // seconds
    private boolean showCompanionMessage = true;

    public ModConfig() {}

    // Getters and Setters
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getApiProvider() { return apiProvider; }
    public void setApiProvider(String apiProvider) { this.apiProvider = apiProvider; }

    public boolean isEnableAutoAdvice() { return enableAutoAdvice; }
    public void setEnableAutoAdvice(boolean enableAutoAdvice) { this.enableAutoAdvice = enableAutoAdvice; }

    public boolean isShowReasoning() { return showReasoning; }
    public void setShowReasoning(boolean showReasoning) { this.showReasoning = showReasoning; }

    public int getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(int requestTimeout) { this.requestTimeout = requestTimeout; }

    public boolean isShowCompanionMessage() { return showCompanionMessage; }
    public void setShowCompanionMessage(boolean showCompanionMessage) { this.showCompanionMessage = showCompanionMessage; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Get the effective base URL (custom or default).
     */
    public String getEffectiveBaseUrl(String defaultUrl) {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            return baseUrl.trim();
        }
        return defaultUrl;
    }

    /**
     * Load configuration from file.
     */
    public static ModConfig load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                System.err.println("[AI Advisor] Failed to load config: " + e.getMessage());
            }
        }
        return new ModConfig();
    }

    /**
     * Save configuration to file.
     */
    public void save() {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(CONFIG_FILE);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            System.out.println("[AI Advisor] Config saved successfully");
        } catch (Exception e) {
            System.err.println("[AI Advisor] Failed to save config: " + e.getMessage());
        }
    }
}