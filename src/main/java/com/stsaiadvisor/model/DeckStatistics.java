package com.stsaiadvisor.model;

/**
 * DeckStatistics - 牌组统计信息
 *
 * <p>用于奖励场景分析牌组结构
 */
public class DeckStatistics {

    /** 总卡牌数 */
    private int totalCards;

    /** 攻击牌数量 */
    private int attackCards;

    /** 技能牌数量 */
    private int skillCards;

    /** 能力牌数量 */
    private int powerCards;

    /** 诅咒牌数量 */
    private int curseCards;

    /** 状态牌数量 */
    private int statusCards;

    /** 平均费用 */
    private double averageCost;

    /** 防御牌占比 */
    private double blockRatio;

    // ========== Getters and Setters ==========

    public int getTotalCards() { return totalCards; }
    public void setTotalCards(int totalCards) { this.totalCards = totalCards; }

    public int getAttackCards() { return attackCards; }
    public void setAttackCards(int attackCards) { this.attackCards = attackCards; }

    public int getSkillCards() { return skillCards; }
    public void setSkillCards(int skillCards) { this.skillCards = skillCards; }

    public int getPowerCards() { return powerCards; }
    public void setPowerCards(int powerCards) { this.powerCards = powerCards; }

    public int getCurseCards() { return curseCards; }
    public void setCurseCards(int curseCards) { this.curseCards = curseCards; }

    public int getStatusCards() { return statusCards; }
    public void setStatusCards(int statusCards) { this.statusCards = statusCards; }

    public double getAverageCost() { return averageCost; }
    public void setAverageCost(double averageCost) { this.averageCost = averageCost; }

    public double getBlockRatio() { return blockRatio; }
    public void setBlockRatio(double blockRatio) { this.blockRatio = blockRatio; }

    @Override
    public String toString() {
        return String.format("DeckStats[total=%d, atk=%d, skl=%d, pwr=%d, avgCost=%.1f]",
            totalCards, attackCards, skillCards, powerCards, averageCost);
    }
}