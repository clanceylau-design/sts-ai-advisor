package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * AnalysisResult - AnalysisAgent统一输出模型
 *
 * <p>职责：
 * <ul>
 *   <li>作为AnalysisAgent对所有场景的统一输出</li>
 *   <li>通过scenario字段区分不同场景的分析结果</li>
 * </ul>
 *
 * <p>场景输出：
 * <ul>
 *   <li>battle: 局势分析、关键信息列表</li>
 *   <li>reward: 牌组流派分析、短板识别</li>
 * </ul>
 *
 * @see AnalysisAgent
 * @see ViewState
 */
public class AnalysisResult {

    /** 场景类型 */
    private String scenario;

    // ========== 战斗场景输出 ==========

    /** 局势总结 */
    private String situationSummary;

    /** 局势紧急程度 */
    private String urgencyLevel;

    /** 关键信息列表（分点列出） */
    private List<String> keyInfo;

    /** 关键焦点 */
    private List<String> keyFocus;

    // ========== 奖励场景输出 ==========

    /** 牌组流派 */
    private String deckArchetype;

    /** 流派成型度 (0-100) */
    private int archetypeStrength;

    /** 牌组统计 */
    private DeckStatistics deckStatistics;

    /** 牌组短板 */
    private List<String> deckWeaknesses;

    // ========== Getters and Setters ==========

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getSituationSummary() { return situationSummary; }
    public void setSituationSummary(String situationSummary) { this.situationSummary = situationSummary; }

    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public List<String> getKeyInfo() {
        if (keyInfo == null) keyInfo = new ArrayList<>();
        return keyInfo;
    }
    public void setKeyInfo(List<String> keyInfo) { this.keyInfo = keyInfo; }

    public List<String> getKeyFocus() {
        if (keyFocus == null) keyFocus = new ArrayList<>();
        return keyFocus;
    }
    public void setKeyFocus(List<String> keyFocus) { this.keyFocus = keyFocus; }

    public String getDeckArchetype() { return deckArchetype; }
    public void setDeckArchetype(String deckArchetype) { this.deckArchetype = deckArchetype; }

    public int getArchetypeStrength() { return archetypeStrength; }
    public void setArchetypeStrength(int archetypeStrength) { this.archetypeStrength = archetypeStrength; }

    public DeckStatistics getDeckStatistics() { return deckStatistics; }
    public void setDeckStatistics(DeckStatistics deckStatistics) { this.deckStatistics = deckStatistics; }

    public List<String> getDeckWeaknesses() {
        if (deckWeaknesses == null) deckWeaknesses = new ArrayList<>();
        return deckWeaknesses;
    }
    public void setDeckWeaknesses(List<String> deckWeaknesses) { this.deckWeaknesses = deckWeaknesses; }

    // ========== 便捷方法 ==========

    /**
     * 转换为ViewState（兼容旧代码）
     */
    public ViewState toViewState() {
        ViewState vs = new ViewState();
        vs.setSituationSummary(situationSummary);
        // 转换urgencyLevel字符串到枚举
        if (urgencyLevel != null) {
            try {
                vs.setUrgencyLevel(ViewState.UrgencyLevel.valueOf(urgencyLevel.toUpperCase()));
            } catch (IllegalArgumentException e) {
                vs.setUrgencyLevel(ViewState.UrgencyLevel.MEDIUM);
            }
        }
        vs.setKeyFocus(keyInfo != null && !keyInfo.isEmpty() ? keyInfo : keyFocus);
        return vs;
    }

    /**
     * 从ViewState创建（兼容旧代码）
     */
    public static AnalysisResult fromViewState(ViewState vs) {
        AnalysisResult result = new AnalysisResult();
        result.setScenario("battle");
        result.setSituationSummary(vs.getSituationSummary());
        // 转换枚举到字符串
        if (vs.getUrgencyLevel() != null) {
            result.setUrgencyLevel(vs.getUrgencyLevel().name());
        }
        result.setKeyFocus(vs.getKeyFocus());
        return result;
    }
}