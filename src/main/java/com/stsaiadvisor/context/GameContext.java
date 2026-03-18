package com.stsaiadvisor.context;

import com.stsaiadvisor.capture.BattleStateCapture;
import com.stsaiadvisor.capture.MapStateCapture;
import com.stsaiadvisor.capture.RewardSceneCapture;
import com.stsaiadvisor.capture.ShopSceneCapture;
import com.stsaiadvisor.model.*;

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

    /** 地图状态捕获器 */
    private final MapStateCapture mapCapture;

    /** 商店场景捕获器 */
    private final ShopSceneCapture shopCapture;

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
        this.mapCapture = new MapStateCapture();
        this.shopCapture = new ShopSceneCapture();
    }

    /**
     * 刷新场景上下文
     *
     * <p>捕获当前游戏状态并缓存
     *
     * <p>检测顺序：战斗场景 > 奖励场景 > 商店场景
     * <ul>
     *   <li>战斗场景检测最可靠（检查玩家、房间状态、活着的怪物），优先检测</li>
     *   <li>奖励场景在战斗结束后显示，需检查 combatRewardScreen</li>
     *   <li>商店场景检查 CurrentScreen.SHOP</li>
     * </ul>
     *
     * <p>如果不在任何特定场景，设置为 general 通用场景，允许用户随时对话
     *
     * @return 是否成功捕获
     */
    public boolean refreshContext() {
        cachedContext = null;
        currentScenario = null;

        // 优先检查战斗场景（检测最可靠、最常见）
        if (battleCapture.isInBattle()) {
            System.out.println("[GameContext] Detected battle scene");
            cachedContext = battleCapture.captureSceneContext();
            currentScenario = "battle";
            if (cachedContext != null) {
                return true;
            }
        }

        // 检查奖励场景（战斗胜利后显示奖励界面）
        if (rewardCapture.isInCardReward()) {
            System.out.println("[GameContext] Detected reward scene");
            cachedContext = rewardCapture.capture();
            currentScenario = "reward";
            if (cachedContext != null) {
                return true;
            }
        }

        // 检查商店场景
        if (shopCapture.isInShop()) {
            System.out.println("[GameContext] Detected shop scene");
            currentScenario = "shop";
            return true;
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
     * 是否在商店界面
     */
    public boolean isInShop() {
        return "shop".equals(currentScenario);
    }

    /**
     * 是否在通用场景（非战斗/奖励/商店）
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

    /**
     * 获取地图状态捕获器
     */
    public MapStateCapture getMapCapture() {
        return mapCapture;
    }

    // ========== 奖励物品（遗物/药水） ==========

    /**
     * 获取奖励界面中的遗物和药水奖励
     *
     * @return 奖励物品状态
     */
    public RewardItemsState getRewardItems() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (!"reward".equals(currentScenario)) {
            return new RewardItemsState();
        }
        return rewardCapture.captureRewardItems();
    }

    // ========== 地图信息 ==========

    /**
     * 获取地图信息
     *
     * @return 地图状态
     */
    public MapInfoState getMapInfo() {
        return mapCapture.captureMapInfo();
    }

    // ========== Boss信息 ==========

    /**
     * 获取Boss信息
     *
     * @return Boss状态
     */
    public BossInfoState getBossInfo() {
        return mapCapture.captureBossInfo();
    }

    // ========== 牌堆信息 ==========

    /**
     * 获取牌堆信息（抽牌堆、弃牌堆、消耗牌堆）
     *
     * @return 牌堆状态
     */
    public PileState getPiles() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (!"battle".equals(currentScenario)) {
            return new PileState();
        }
        return battleCapture.capturePiles();
    }

    // ========== 商店商品 ==========

    /**
     * 获取商店商品信息
     *
     * @return 商店商品状态
     */
    public ShopItemsState getShopItems() {
        if (cachedContext == null) {
            refreshContext();
        }
        if (!"shop".equals(currentScenario)) {
            return new ShopItemsState();
        }
        return shopCapture.captureShopItems();
    }

    /**
     * 获取商店场景捕获器
     */
    public ShopSceneCapture getShopCapture() {
        return shopCapture;
    }
}