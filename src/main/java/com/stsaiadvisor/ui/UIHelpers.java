package com.stsaiadvisor.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;

/**
 * Utility class for UI rendering operations.
 */
public class UIHelpers {

    private static final ShapeRenderer shapeRenderer = new ShapeRenderer();

    /**
     * Draw a simple rounded rectangle (using regular rect as fallback).
     */
    public static void drawRoundedRect(SpriteBatch sb, float x, float y, float width, float height,
                                       Color bgColor, Color borderColor, float alpha) {
        sb.end();

        // Background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(bgColor.r, bgColor.g, bgColor.b, alpha);
        shapeRenderer.rect(x, y, width, height);
        shapeRenderer.end();

        // Border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(borderColor.r, borderColor.g, borderColor.b, borderColor.a);
        shapeRenderer.rect(x, y, width, height);
        shapeRenderer.end();

        sb.begin();
    }

    /**
     * Draw a simple tooltip-style box.
     */
    public static void drawTooltipBox(SpriteBatch sb, float x, float y, float width, float height,
                                      String title, String[] lines) {
        // Background
        sb.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 0.95f);
        shapeRenderer.rect(x, y, width, height);
        shapeRenderer.end();
        sb.begin();

        // Title
        float textY = y + height - 20 * Settings.scale;
        if (title != null) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                title, x + 10 * Settings.scale, textY, Settings.GOLD_COLOR);
            textY -= 25 * Settings.scale;
        }

        // Lines
        for (String line : lines) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                line, x + 10 * Settings.scale, textY, Color.WHITE);
            textY -= 20 * Settings.scale;
        }
    }

    /**
     * Get a color based on percentage (health, etc.).
     */
    public static Color getPercentageColor(float percent) {
        if (percent > 0.6f) {
            return Color.GREEN;
        } else if (percent > 0.3f) {
            return Color.YELLOW;
        } else {
            return Color.RED;
        }
    }

    /**
     * Get a color for enemy intent.
     */
    public static Color getIntentColor(String intentType) {
        if (intentType == null) {
            return Color.GRAY;
        }

        switch (intentType) {
            case "ATTACK":
                return Color.RED;
            case "DEFEND":
                return Color.BLUE;
            case "BUFF":
                return Color.GREEN;
            case "DEBUFF":
                return Color.PURPLE;
            case "SLEEP":
            case "STUN":
                return Color.GRAY;
            default:
                return Color.WHITE;
        }
    }

    /**
     * Wrap text to fit within a given width (simplified implementation).
     */
    public static String wrapText(String text, float maxWidth, BitmapFont font) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Simple implementation - split by spaces and estimate width
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder();
        float spaceWidth = 20 * Settings.scale; // estimate

        for (String word : text.split(" ")) {
            float wordWidth = word.length() * 10 * Settings.scale; // rough estimate
            float lineWidth = line.length() * 10 * Settings.scale;

            if (lineWidth + wordWidth + spaceWidth > maxWidth && line.length() > 0) {
                result.append(line).append("\n");
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) {
                    line.append(" ");
                }
                line.append(word);
            }
        }

        if (line.length() > 0) {
            result.append(line);
        }

        return result.toString();
    }
}