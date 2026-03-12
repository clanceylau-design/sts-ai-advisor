package com.stsaiadvisor.util;

/**
 * Constants used throughout the mod.
 */
public class Constants {
    public static final String MOD_ID = "sts-ai-advisor";
    public static final String MOD_NAME = "AI Advisor";
    public static final String VERSION = "0.1.0";

    // UI - Panel will be positioned in top-right corner
    public static final float PANEL_WIDTH = 280f;
    public static final float PANEL_HEIGHT = 300f;
    public static final float PANEL_MARGIN_RIGHT = 20f;
    public static final float PANEL_MARGIN_TOP = 20f;

    // Colors (RGBA)
    public static final float[] COLOR_BG = {0.1f, 0.1f, 0.15f, 0.85f};
    public static final float[] COLOR_BORDER = {0.4f, 0.4f, 0.5f, 1.0f};
    public static final float[] COLOR_TEXT = {1.0f, 1.0f, 1.0f, 1.0f};
    public static final float[] COLOR_HIGHLIGHT = {0.3f, 0.7f, 1.0f, 1.0f};
    public static final float[] COLOR_PRIORITY_HIGH = {1.0f, 0.3f, 0.3f, 1.0f};
    public static final float[] COLOR_PRIORITY_MEDIUM = {1.0f, 0.8f, 0.3f, 1.0f};
    public static final float[] COLOR_PRIORITY_LOW = {0.5f, 0.8f, 0.5f, 1.0f};

    // Hotkeys - using actual keycodes from game (not LibGDX standard)
    // F3 = 246, F4 = 247 based on game's key mapping
    public static final int HOTKEY_TOGGLE_PANEL = 247; // F4
    public static final int HOTKEY_REQUEST_ADVICE = 246; // F3

    // API
    public static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    public static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
}