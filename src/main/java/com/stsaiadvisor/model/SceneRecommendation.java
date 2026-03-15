package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * SceneRecommendation - 场景推荐输出模型
 *
 * <p>职责：
 * <ul>
 *   <li>作为GameAgent对所有场景的统一输出</li>
 *   <li>通过scenario字段区分不同场景的建议</li>
 * </ul>
 *
 * <p>场景输出：
 * <ul>
 *   <li>battle: 出牌顺序建议</li>
 *   <li>reward: 选牌建议</li>
 * </ul>
 *
 * @see FinalRecommendation
 */
public class SceneRecommendation {

    /** 场景类型 */
    private String scenario;

    // ========== 战斗场景输出（兼容FinalRecommendation） ==========

    /** 出牌建议列表 */
    private List<CardPlaySuggestion> cardPlays;

    /** 策略说明 */
    private String reasoning;

    /** 鼓励消息 */
    private String companionMessage;

    /** 风险提示 */
    private RiskWarning risks;

    // ========== 奖励场景输出 ==========

    /** 卡牌奖励建议列表 */
    private List<CardRewardSuggestion> cardRewards;

    /** 是否建议跳过 */
    private boolean recommendSkip;

    /** 跳过理由 */
    private String skipReason;

    // ========== Getters and Setters ==========

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public List<CardPlaySuggestion> getCardPlays() {
        if (cardPlays == null) cardPlays = new ArrayList<>();
        return cardPlays;
    }
    public void setCardPlays(List<CardPlaySuggestion> cardPlays) { this.cardPlays = cardPlays; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getCompanionMessage() { return companionMessage; }
    public void setCompanionMessage(String companionMessage) { this.companionMessage = companionMessage; }

    public RiskWarning getRisks() { return risks; }
    public void setRisks(RiskWarning risks) { this.risks = risks; }

    public List<CardRewardSuggestion> getCardRewards() {
        if (cardRewards == null) cardRewards = new ArrayList<>();
        return cardRewards;
    }
    public void setCardRewards(List<CardRewardSuggestion> cardRewards) { this.cardRewards = cardRewards; }

    public boolean isRecommendSkip() { return recommendSkip; }
    public void setRecommendSkip(boolean recommendSkip) { this.recommendSkip = recommendSkip; }

    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }

    // ========== 便捷方法 ==========

    /**
     * 判断是否有战斗建议
     */
    public boolean hasCardPlays() {
        return cardPlays != null && !cardPlays.isEmpty();
    }

    /**
     * 判断是否有奖励建议
     */
    public boolean hasCardRewards() {
        return cardRewards != null && !cardRewards.isEmpty();
    }

    /**
     * 转换为FinalRecommendation（兼容旧代码）
     */
    public FinalRecommendation toFinalRecommendation() {
        FinalRecommendation fr = new FinalRecommendation();
        fr.setSuggestions(cardPlays);
        fr.setReasoning(reasoning);
        fr.setCompanionMessage(companionMessage);
        fr.setRisks(risks);
        return fr;
    }

    /**
     * 转换为Recommendation（兼容旧UI）
     */
    public Recommendation toRecommendation() {
        Recommendation rec = new Recommendation();
        rec.setSuggestions(cardPlays);
        rec.setReasoning(reasoning);
        rec.setCompanionMessage(companionMessage);
        return rec;
    }

    /**
     * 从FinalRecommendation创建（兼容旧代码）
     */
    public static SceneRecommendation fromFinalRecommendation(FinalRecommendation fr) {
        SceneRecommendation sr = new SceneRecommendation();
        sr.setScenario("battle");
        sr.setCardPlays(fr.getSuggestions());
        sr.setReasoning(fr.getReasoning());
        sr.setCompanionMessage(fr.getCompanionMessage());
        sr.setRisks(fr.getRisks());
        return sr;
    }
}