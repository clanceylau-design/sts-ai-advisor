package com.stsaiadvisor.model;

/**
 * CardRewardSuggestion - 卡牌奖励建议
 *
 * <p>用于奖励场景，表示对单张可选卡牌的建议
 */
public class CardRewardSuggestion {

    /** 卡牌索引（在可选列表中） */
    private int cardIndex;

    /** 卡牌名称 */
    private String cardName;

    /** 推荐优先级 (1-3, 1最高) */
    private int priority;

    /** 推荐理由 */
    private String reason;

    /** 与流派契合度 (0-100) */
    private int synergyScore;

    /** 是否推荐跳过 */
    private boolean skip;

    public CardRewardSuggestion() {}

    // ========== Getters and Setters ==========

    public int getCardIndex() { return cardIndex; }
    public void setCardIndex(int cardIndex) { this.cardIndex = cardIndex; }

    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getSynergyScore() { return synergyScore; }
    public void setSynergyScore(int synergyScore) { this.synergyScore = synergyScore; }

    public boolean isSkip() { return skip; }
    public void setSkip(boolean skip) { this.skip = skip; }

    @Override
    public String toString() {
        return String.format("CardReward[%d] %s: priority=%d, synergy=%d, skip=%s",
            cardIndex, cardName, priority, synergyScore, skip);
    }
}