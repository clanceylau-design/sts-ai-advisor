package com.stsaiadvisor.context;

import com.stsaiadvisor.capture.BattleStateCapture;
import com.stsaiadvisor.capture.RewardSceneCapture;
import com.stsaiadvisor.model.CardState;
import com.stsaiadvisor.model.EnemyState;
import com.stsaiadvisor.model.PlayerState;
import com.stsaiadvisor.model.PotionState;
import com.stsaiadvisor.model.RelicState;
import com.stsaiadvisor.model.SceneContext;

import java.util.ArrayList;
import java.util.List;

/**
 * GameContext - 游戏状态访问上下文
 *
 * <p>职责：
 * <ul>
 *   <li>为工具提供统一的游戏状态访问接口</li>
 *   <li>封装BattleStateCapture和RewardSceneCapture</li>
 *   <li>缓存场景上下文避免重复捕获</li>
 * </ul>
 *
 * @see GameTool
 */
public class GameContext {

    /** 战斗状态捕获器 */
    private final BattleStateCapture battleCapture;

    /** 奖励场景捕获器 */
    private final RewardSceneCapture rewardCapture;

    /** 缓存的场景上下文 */
    private SceneContext cachedContext;

    /** 当前场景类型 */
    private String currentScenario;

    /**
     * 构造函数
     */
    public GameContext() {
        this.battleCapture = new BattleStateCapture();
        this.rewardCapture = new RewardSceneCapture();
    }

    /**
     * 刷新场景上下文
     *
     * <p>捕获当前游戏状态并缓存
     *
     * <p>检测顺序：奖励场景优先于战斗场景检测
     * <ul>
     *   <li>原因：战斗胜利后，房间phase变为COMPLETE，但怪物列表可能仍存在</li>
     *   <li>如果奖励界面正在显示，应该优先识别为奖励场景</li>
     * </ul>
     *
     * <p>如果不在任何特定场景，设置为 general 通用场景，允许用户随时对话
     *
     * @return 是否成功捕获
     */
    public boolean refreshContext() {
        cachedContext = null;
        currentScenario = null;

        // 优先检查奖励场景（战斗胜利后显示奖励界面）
        if (rewardCapture.isInCardReward()) {
            System.out.println("[GameContext] Detected reward scene");
            cachedContext = rewardCapture.capture();
            currentScenario = "reward";
            if (cachedContext != null) {
                return true;
            }
        }

        // 再检查战斗场景
        if (battleCapture.isInBattle()) {
            System.out.println("[GameContext] Detected battle scene");
            cachedContext = battleCapture.captureSceneContext();
            currentScenario = "battle";
            return cachedContext != null;
        }

        // 不在任何特定场景，设置为 general 通用场景
        System.out.println("[GameContext] No specific scene detected, using general mode");
        currentScenario = "general";
        return true;
    }

    /**
     * 获取缓存的场景上下文
     *
     * @return 场景上下文，可能为null
     */
    public SceneContext getCachedContext() {
        return cachedContext;
    }

    /**
     * 获取当前场景类型
     *
     * @return 场景类型：battle, reward, 或 null
     */
    public String getCurrentScenario() {
        return currentScenario;
    }

    // ========== 玩家状态 ==========

    /**
     * 获取玩家当前状态
     *
     * @return 玩家状态，可能为null
     */
    public PlayerState getPlayerState() {
        if (cachedContext == null) {
            refreshContext();
        }
        return cachedContext != null ? cachedContext.getPlayer() : null;
    }

    // ========== 手牌 ==========

    /**
     * 获取手牌信息
     *
     * @return 手牌列表
     */
    public List<CardState> getHandCards() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (cachedContext == null || !"battle".equals(currentScenario)) {
            return new ArrayList<>();
        }
        List<CardState> hand = cachedContext.getHand();
        return hand != null ? hand : new ArrayList<>();
    }

    // ========== 敌人 ==========

    /**
     * 获取敌人信息
     *
     * @return 敌人列表
     */
    public List<EnemyState> getEnemies() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (cachedContext == null || !"battle".equals(currentScenario)) {
            return new ArrayList<>();
        }
        List<EnemyState> enemies = cachedContext.getEnemies();
        return enemies != null ? enemies : new ArrayList<>();
    }

    // ========== 牌组 ==========

    /**
     * 获取完整牌组
     *
     * @return 牌组列表
     */
    public List<CardState> getDeck() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (cachedContext == null) {
            return new ArrayList<>();
        }
        List<CardState> deck = cachedContext.getDeck();
        return deck != null ? deck : new ArrayList<>();
    }

    // ========== 遗物 ==========

    /**
     * 获取遗物列表
     *
     * @return 遗物状态列表
     */
    public List<RelicState> getRelics() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (cachedContext == null) {
            return new ArrayList<>();
        }
        List<RelicState> relics = cachedContext.getRelics();
        return relics != null ? relics : new ArrayList<>();
    }

    // ========== 药水 ==========

    /**
     * 获取药水信息
     *
     * @return 药水列表
     */
    public List<String> getPotions() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (cachedContext == null) {
            return new ArrayList<>();
        }
        List<PotionState> potions = cachedContext.getPotions();
        if (potions == null || potions.isEmpty()) {
            return new ArrayList<>();
        }
        // 转换为字符串列表格式
        List<String> result = new ArrayList<>();
        for (PotionState potion : potions) {
            result.add(potion.getFullInfo());
        }
        return result;
    }

    // ========== 卡牌奖励 ==========

    /**
     * 获取卡牌奖励选项
     *
     * @return 奖励卡牌列表
     */
    @SuppressWarnings("unchecked")
    public List<CardState> getCardRewards() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (cachedContext == null || !"reward".equals(currentScenario)) {
            return new ArrayList<>();
        }
        List<CardState> rewardCards = cachedContext.getSceneData("rewardCards");
        return rewardCards != null ? rewardCards : new ArrayList<>();
    }

    // ========== 事件选项 ==========

    /**
     * 获取事件选项
     *
     * <p>TODO: 需要实现事件场景捕获
     *
     * @return 事件选项列表（暂返回空列表）
     */
    public List<String> getEventOptions() {
        // TODO: 实现事件捕获
        return new ArrayList<>();
    }

    // ========== 层数信息 ==========

    /**
     * 获取当前层数
     *
     * @return 层数（1-4），失败返回0
     */
    public int getAct() {
        if (cachedContext == null) {
            refreshContext();
        }
        return cachedContext != null ? cachedContext.getAct() : 0;
    }

    /**
     * 获取当前回合数
     *
     * @return 回合数，失败返回0
     */
    public int getTurn() {
        if (cachedContext == null) {
            refreshContext();
        }
        return cachedContext != null ? cachedContext.getTurn() : 0;
    }

    // ========== 场景判断 ==========

    /**
     * 是否在战斗中
     */
    public boolean isInBattle() {
        return "battle".equals(currentScenario);
    }

    /**
     * 是否在卡牌奖励界面
     */
    public boolean isInCardReward() {
        return "reward".equals(currentScenario);
    }

    /**
     * 是否在通用场景（非战斗/奖励）
     */
    public boolean isGeneral() {
        return "general".equals(currentScenario);
    }

    // ========== 原始捕获器访问 ==========

    /**
     * 获取战斗状态捕获器
     */
    public BattleStateCapture getBattleCapture() {
        return battleCapture;
    }

    /**
     * 获取奖励场景捕获器
     */
    public RewardSceneCapture getRewardCapture() {
        return rewardCapture;
    }
}