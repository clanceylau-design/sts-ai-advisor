package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SceneContext - 统一场景上下文模型
 *
 * <p>职责：
 * <ul>
 *   <li>作为所有Agent的统一输入模型</li>
 *   <li>通过scenario字段区分不同场景</li>
 *   <li>sceneData承载场景特定的数据</li>
 * </ul>
 *
 * <p>支持场景：
 * <ul>
 *   <li>battle - 战斗场景</li>
 *   <li>reward - 卡牌奖励场景</li>
 *   <li>shop - 商店场景（预留）</li>
 * </ul>
 *
 * @see AnalysisAgent
 * @see SkillAgent
 * @see AdvisorAgent
 */
public class SceneContext {

    /** 场景类型 */
    private String scenario;

    /** 玩家状态 */
    private PlayerState player;

    /** 完整牌组 */
    private List<CardState> deck;

    /** 遗物列表 */
    private List<String> relics;

    /** 当前层数 */
    private int act;

    /** 场景特定数据 */
    private Map<String, Object> sceneData;

    // ========== 战斗场景专用字段（兼容旧代码） ==========

    /** 手牌 */
    private List<CardState> hand;

    /** 抽牌堆 */
    private List<CardState> drawPile;

    /** 弃牌堆 */
    private List<CardState> discardPile;

    /** 敌人列表 */
    private List<EnemyState> enemies;

    /** 当前回合 */
    private int turn;

    public SceneContext() {
        this.sceneData = new HashMap<>();
    }

    // ========== Getters and Setters ==========

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public PlayerState getPlayer() { return player; }
    public void setPlayer(PlayerState player) { this.player = player; }

    public List<CardState> getDeck() {
        if (deck == null) deck = new ArrayList<>();
        return deck;
    }
    public void setDeck(List<CardState> deck) { this.deck = deck; }

    public List<String> getRelics() {
        if (relics == null) relics = new ArrayList<>();
        return relics;
    }
    public void setRelics(List<String> relics) { this.relics = relics; }

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }

    public Map<String, Object> getSceneData() {
        if (sceneData == null) sceneData = new HashMap<>();
        return sceneData;
    }
    public void setSceneData(Map<String, Object> sceneData) { this.sceneData = sceneData; }

    // ========== 场景数据便捷方法 ==========

    /**
     * 添加场景特定数据
     */
    public SceneContext addSceneData(String key, Object value) {
        getSceneData().put(key, value);
        return this;
    }

    /**
     * 获取场景特定数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getSceneData(String key) {
        return (T) getSceneData().get(key);
    }

    // ========== 战斗场景兼容方法 ==========

    public List<CardState> getHand() { return hand; }
    public void setHand(List<CardState> hand) { this.hand = hand; }

    public List<CardState> getDrawPile() { return drawPile; }
    public void setDrawPile(List<CardState> drawPile) { this.drawPile = drawPile; }

    public List<CardState> getDiscardPile() { return discardPile; }
    public void setDiscardPile(List<CardState> discardPile) { this.discardPile = discardPile; }

    public List<EnemyState> getEnemies() { return enemies; }
    public void setEnemies(List<EnemyState> enemies) { this.enemies = enemies; }

    public int getTurn() { return turn; }
    public void setTurn(int turn) { this.turn = turn; }

    /**
     * 判断是否为战斗场景
     */
    public boolean isBattle() {
        return "battle".equals(scenario);
    }

    /**
     * 判断是否为奖励场景
     */
    public boolean isReward() {
        return "reward".equals(scenario);
    }
}