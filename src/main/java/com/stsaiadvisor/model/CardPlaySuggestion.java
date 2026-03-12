package com.stsaiadvisor.model;

/**
 * Represents a single card play suggestion from the AI.
 */
public class CardPlaySuggestion {
    private int cardIndex; // Hand index
    private int targetIndex; // Target enemy index (-1 for no target/self)
    private String targetName; // 目标名称（敌人名称或"自身"）
    private String cardName;
    private int priority; // 1-5, 1 being highest priority
    private String reason;

    public CardPlaySuggestion() {}

    // Getters and Setters
    public int getCardIndex() { return cardIndex; }
    public void setCardIndex(int cardIndex) { this.cardIndex = cardIndex; }

    public int getTargetIndex() { return targetIndex; }
    public void setTargetIndex(int targetIndex) { this.targetIndex = targetIndex; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return String.format("[%d] %s -> %s (Priority: %d)", cardIndex, cardName, targetName, priority);
    }
}