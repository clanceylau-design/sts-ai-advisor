package com.stsaiadvisor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.stsaiadvisor.model.CardPlaySuggestion;
import com.stsaiadvisor.model.Recommendation;

import java.util.ArrayList;
import java.util.List;

/**
 * 浮动建议面板
 *
 * <p>支持多场景显示：战斗出牌建议、奖励选牌建议
 */
public class RecommendationPanel {

    private boolean visible = false;
    private float width;
    private float height;

    // 位置
    private float screenX;
    private float screenY;

    // 拖拽
    private boolean isDragging = false;
    private float dragOffsetX = 0;
    private float dragOffsetY = 0;
    private boolean wasButtonPressed = false;

    // 滚动
    private float scrollOffset = 0;
    private float contentHeight = 0;

    private Recommendation currentRecommendation;
    private String statusMessage = null;
    private boolean isLoading = false;
    private long requestStartTime = 0;

    private ShapeRenderer shapeRenderer;
    private Matrix4 projectionMatrix;

    /** 行高：基础18 + 额外20 = 38 */
    private static final float LINE_HEIGHT = 38f;
    private static final float LINE_HEIGHT_LARGE = 28f;
    private static final float PADDING = 10f;
    /** 换行宽度：25个字符 */
    private static final int CHARS_PER_LINE = 25;

    /** 当前场景类型 */
    private String currentScenario = "battle";

    public RecommendationPanel() {
        this.width = 320f;
        this.height = 350f;
        this.shapeRenderer = new ShapeRenderer();
        this.projectionMatrix = new Matrix4();

        this.screenX = Gdx.graphics.getWidth() - width - 20f;
        this.screenY = Gdx.graphics.getHeight() - height - 20f;
    }

    public void render(SpriteBatch sb) {
        if (!visible) return;

        handleDrag();
        handleScroll();

        float renderY = Gdx.graphics.getHeight() - screenY - height;
        projectionMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        sb.end();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 背景
        shapeRenderer.setProjectionMatrix(projectionMatrix);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 0.95f);
        shapeRenderer.rect(screenX, renderY, width, height);
        shapeRenderer.end();

        // 边框
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.4f, 0.5f, 0.6f, 1.0f);
        shapeRenderer.rect(screenX, renderY, width, height);
        shapeRenderer.end();

        // 拖拽区域
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.3f, 0.4f, 0.5f, 1.0f);
        shapeRenderer.rect(screenX + 5, renderY + height - 25, width - 10, 20);
        shapeRenderer.end();

        sb.begin();

        float textY = renderY + height - 40;

        // 标题
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
            "AI Advisor", screenX + PADDING, textY, new Color(0.5f, 0.8f, 1.0f, 1.0f));
        textY -= LINE_HEIGHT_LARGE;

        // 加载状态
        if (isLoading) {
            long elapsed = System.currentTimeMillis() - requestStartTime;
            String dots = "";
            for (int i = 0; i < (int)(elapsed / 500) % 4; i++) dots += ".";
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                "分析中" + dots, screenX + PADDING, textY, Color.YELLOW);
            textY -= LINE_HEIGHT_LARGE;
        } else if (statusMessage != null) {
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                statusMessage, screenX + PADDING, textY, Color.LIGHT_GRAY);
            textY -= LINE_HEIGHT;
        }

        // 内容区域
        if (currentRecommendation != null) {
            renderContent(sb, textY, renderY + PADDING);
        }
    }

    /**
     * 渲染内容区域
     */
    private void renderContent(SpriteBatch sb, float topY, float bottomY) {
        float contentX = screenX + PADDING;
        float textY = topY + scrollOffset;

        // 计算内容高度
        List<String> allLines = new ArrayList<>();

        // 鼓励消息
        if (currentRecommendation.getCompanionMessage() != null
            && !currentRecommendation.getCompanionMessage().isEmpty()) {
            allLines.add("【" + currentRecommendation.getCompanionMessage() + "】");
            allLines.add("");
        }

        // 策略说明
        if (currentRecommendation.getReasoning() != null
            && !currentRecommendation.getReasoning().isEmpty()) {
            // 自动换行处理
            List<String> wrappedLines = wrapText(currentRecommendation.getReasoning(), CHARS_PER_LINE);
            allLines.addAll(wrappedLines);
            allLines.add("");
        }

        // 建议列表（根据场景显示不同标题）
        if (currentRecommendation.hasSuggestions()) {
            // 判断场景类型
            String sectionTitle = determineSectionTitle();
            allLines.add("【" + sectionTitle + "】");

            for (CardPlaySuggestion s : currentRecommendation.getSuggestions()) {
                String cardName = s.getCardName() != null ? s.getCardName() : "卡牌" + s.getCardIndex();

                // 根据场景格式化显示
                if ("reward".equals(currentScenario)) {
                    // Reward场景：推荐/备选格式
                    String prefix = s.getPriority() == 1 ? "★ " : "☆ ";
                    String line = prefix + cardName;
                    if (s.getReason() != null && !s.getReason().isEmpty()) {
                        line += "：" + s.getReason();
                    }
                    // 自动换行
                    allLines.addAll(wrapText(line, CHARS_PER_LINE));
                } else {
                    // Battle场景：出牌顺序格式
                    String targetName = s.getTargetName() != null ? s.getTargetName() : "目标";
                    String line = s.getPriority() + ". " + cardName + " → " + targetName;
                    if (s.getReason() != null && !s.getReason().isEmpty()) {
                        line += "：" + s.getReason();
                    }
                    // 自动换行
                    allLines.addAll(wrapText(line, CHARS_PER_LINE));
                }
            }
        }

        contentHeight = allLines.size() * LINE_HEIGHT;

        // 滚动条
        float visibleHeight = topY - bottomY;
        if (contentHeight > visibleHeight) {
            float scrollbarHeight = Math.min(visibleHeight, visibleHeight * visibleHeight / contentHeight);
            float scrollbarY = bottomY + (scrollOffset / (contentHeight - visibleHeight)) * (visibleHeight - scrollbarHeight);

            sb.end();
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0.4f, 0.5f, 0.6f, 0.8f);
            shapeRenderer.rect(screenX + width - 12, scrollbarY, 8, scrollbarHeight);
            shapeRenderer.end();
            sb.begin();
        }

        // 渲染文本
        float visibleTop = topY;
        float visibleBottom = bottomY;

        for (String line : allLines) {
            if (textY < visibleTop && textY > visibleBottom) {
                Color color = Color.LIGHT_GRAY;

                // 标题行颜色（【xxx】格式）
                if (line.startsWith("【") && line.endsWith("】")) {
                    color = new Color(1.0f, 0.85f, 0.0f, 1.0f); // 金色
                }
                // 推荐卡牌行（★开头）
                else if (line.startsWith("★")) {
                    color = new Color(0.3f, 1.0f, 0.3f, 1.0f); // 绿色
                }
                // 备选卡牌行（☆开头）
                else if (line.startsWith("☆")) {
                    color = new Color(0.7f, 0.9f, 1.0f, 1.0f); // 浅蓝色
                }
                // 出牌建议行（数字开头）
                else if (line.matches("^\\d+\\.\\s+.+")) {
                    color = Color.WHITE;
                }

                FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipBodyFont,
                    line, contentX, textY, color);
            }
            textY -= LINE_HEIGHT;
        }
    }

    /**
     * 判断当前场景的标题
     */
    private String determineSectionTitle() {
        if ("reward".equals(currentScenario)) {
            return "选牌建议";
        }
        return "出牌顺序";
    }

    /**
     * 自动换行处理
     */
    private List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        // 按换行符分割
        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            if (para.isEmpty()) {
                lines.add("");
                continue;
            }

            // 每段按字符数分割
            StringBuilder currentLine = new StringBuilder();
            int charCount = 0;

            for (char c : para.toCharArray()) {
                currentLine.append(c);
                charCount++;

                // 中文字符算2个宽度
                if (c > 127) {
                    charCount++;
                }

                if (charCount >= maxChars) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                    charCount = 0;
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    private void handleDrag() {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        float mouseScreenY = Gdx.graphics.getHeight() - mouseY;

        boolean inDragHandle = mouseX >= screenX && mouseX <= screenX + width &&
                               mouseScreenY >= screenY + height - 30 && mouseScreenY <= screenY + height;

        boolean isButtonPressed = Gdx.input.isButtonPressed(0);
        boolean justPressed = isButtonPressed && !wasButtonPressed;

        if (justPressed && inDragHandle) {
            isDragging = true;
            dragOffsetX = mouseX - screenX;
            dragOffsetY = mouseScreenY - screenY;
        }

        if (isDragging && isButtonPressed) {
            screenX = mouseX - dragOffsetX;
            screenY = mouseScreenY - dragOffsetY;
            screenX = Math.max(0, Math.min(screenX, Gdx.graphics.getWidth() - width));
            screenY = Math.max(0, Math.min(screenY, Gdx.graphics.getHeight() - height));
        } else {
            isDragging = false;
        }

        wasButtonPressed = isButtonPressed;
    }

    private void handleScroll() {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        float mouseScreenY = Gdx.graphics.getHeight() - mouseY;

        boolean inPanel = mouseX >= screenX && mouseX <= screenX + width &&
                          mouseScreenY >= screenY && mouseScreenY <= screenY + height;

        if (inPanel && InputHelper.scrollY != 0) {
            scrollOffset -= InputHelper.scrollY * 20;
            float visibleHeight = screenY + height - 60 - PADDING;
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, contentHeight - visibleHeight)));
        }
    }

    public void updateRecommendation(Recommendation recommendation) {
        this.currentRecommendation = recommendation;
        this.isLoading = false;
        this.scrollOffset = 0;
        if (recommendation != null && recommendation.hasSuggestions()) {
            this.statusMessage = null;
        }
    }

    /**
     * 更新建议并设置场景类型
     */
    public void updateRecommendation(Recommendation recommendation, String scenario) {
        this.currentScenario = scenario != null ? scenario : "battle";
        updateRecommendation(recommendation);
    }

    /**
     * 设置当前场景类型
     */
    public void setScenario(String scenario) {
        this.currentScenario = scenario != null ? scenario : "battle";
    }

    public void setLoading(boolean loading) {
        this.isLoading = loading;
        if (loading) {
            this.requestStartTime = System.currentTimeMillis();
            this.statusMessage = null;
        }
    }

    public void setStatusMessage(String message) {
        this.statusMessage = message;
        this.isLoading = false;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void clear() {
        this.currentRecommendation = null;
        this.statusMessage = "等待中...";
        this.isLoading = false;
        this.scrollOffset = 0;
        this.currentScenario = "battle";
    }
}