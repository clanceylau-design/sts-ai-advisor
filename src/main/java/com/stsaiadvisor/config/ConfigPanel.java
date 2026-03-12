package com.stsaiadvisor.config;

import basemod.ModPanel;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;

/**
 * Configuration panel for the mod settings.
 */
public class ConfigPanel extends ModPanel {

    private ModConfig config;
    private int selectedProvider = 0;
    private String[] providers = {"anthropic", "openai"};
    private String[] claudeModels = {
        "claude-3-5-sonnet-20241022",
        "claude-3-opus-20240229",
        "claude-3-sonnet-20240229"
    };
    private String[] openaiModels = {
        "gpt-4o",
        "gpt-4-turbo",
        "gpt-4"
    };
    private int selectedModel = 0;

    public ConfigPanel(ModConfig config) {
        this.config = config;

        // Initialize selection based on current config
        if ("openai".equals(config.getApiProvider())) {
            selectedProvider = 1;
        }

        updateModelSelection();
    }

    private void updateModelSelection() {
        String[] models = getCurrentModels();
        for (int i = 0; i < models.length; i++) {
            if (models[i].equals(config.getModel())) {
                selectedModel = i;
                break;
            }
        }
    }

    private String[] getCurrentModels() {
        return selectedProvider == 0 ? claudeModels : openaiModels;
    }

    @Override
    public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch sb) {
        super.render(sb);

        float centerX = Settings.WIDTH / 2f;
        float startY = Settings.HEIGHT * 0.75f;
        float lineHeight = 50f * Settings.scale;

        // Title
        FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont,
            "AI Advisor Settings", centerX, startY + lineHeight, Settings.GOLD_COLOR);

        // Provider selection
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            "API Provider: " + providers[selectedProvider].toUpperCase(),
            centerX, startY - lineHeight,
            com.badlogic.gdx.graphics.Color.WHITE);

        // Model selection
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            "Model: " + getCurrentModels()[selectedModel],
            centerX, startY - lineHeight * 2,
            com.badlogic.gdx.graphics.Color.WHITE);

        // Instructions
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            "Edit mods/sts-ai-advisor/config.json to set API key",
            centerX, startY - lineHeight * 4,
            com.badlogic.gdx.graphics.Color.LIGHT_GRAY);

        // Current status
        String status = config.isConfigured() ? "API Key: Configured" : "API Key: NOT SET";
        com.badlogic.gdx.graphics.Color statusColor = config.isConfigured() ?
            com.badlogic.gdx.graphics.Color.GREEN : com.badlogic.gdx.graphics.Color.RED;
        FontHelper.renderFontCentered(sb, FontHelper.tipBodyFont,
            status, centerX, startY - lineHeight * 5, statusColor);
    }
}