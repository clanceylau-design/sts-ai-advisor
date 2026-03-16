package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.stsaiadvisor.STSAIAdvisorMod;
import com.stsaiadvisor.agent.GameAgent;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.overlay.OverlayClient;
import com.stsaiadvisor.ui.KeyInputListener;

/**
 * EventManager - 中央事件管理器
 *
 * <p>职责：
 * <ul>
 *   <li>管理所有场景的事件监听器</li>
 *   <li>协调战斗场景和奖励场景的事件处理</li>
 *   <li>支持通用场景对话</li>
 * </ul>
 *
 * @see BattleEventListener
 * @see RewardEventListener
 */
public class EventManager implements PostUpdateSubscriber {

    /** 游戏代理 */
    private GameAgent gameAgent;

    /** 游戏上下文 */
    private GameContext gameContext;

    /** 战斗场景事件监听器 */
    private BattleEventListener battleEventListener;

    /** 奖励场景事件监听器 */
    private RewardEventListener rewardEventListener;

    /** 热键监听器 */
    private KeyInputListener keyInputListener;

    /**
     * 构造函数
     *
     * @param gameAgent 游戏代理
     */
    public EventManager(GameAgent gameAgent) {
        BaseMod.subscribe(this);

        this.gameAgent = gameAgent;
        this.gameContext = new GameContext();

        // 战斗场景监听器
        battleEventListener = new BattleEventListener(gameAgent);

        // 奖励场景监听器
        rewardEventListener = new RewardEventListener(gameAgent);

        // 热键监听器
        keyInputListener = new KeyInputListener();

        // 设置热键回调
        keyInputListener.setCallbacks(
            () -> {
                // F4: 切换 Overlay 显示/隐藏
                OverlayClient overlayClient = STSAIAdvisorMod.getOverlayClient();
                if (overlayClient == null) {
                    System.out.println("[EventManager] OverlayClient not initialized");
                    return;
                }

                // 检查 Overlay 是否可用
                if (overlayClient.isAvailable()) {
                    // 可用：切换显示/隐藏
                    overlayClient.toggle();
                    System.out.println("[EventManager] Overlay toggled, visible: " + overlayClient.isVisible());
                } else {
                    // 不可用：尝试重新启动
                    System.out.println("[EventManager] Overlay not available, attempting to restart...");
                    if (STSAIAdvisorMod.startOverlayProcess()) {
                        overlayClient.show();
                        System.out.println("[EventManager] Overlay restarted and shown");
                    } else {
                        System.err.println("[EventManager] Failed to restart Overlay");
                    }
                }
            },
            () -> {
                // F3: 根据当前场景请求建议
                if (battleEventListener.isInBattle()) {
                    battleEventListener.requestManualAdvice();
                } else if (rewardEventListener.isInCardReward()) {
                    rewardEventListener.requestManualAdvice();
                } else {
                    // 通用场景
                    triggerGeneralAnalysis();
                }
            }
        );

        System.out.println("[EventManager] Initialized with Battle + Reward + General support");
    }

    @Override
    public void receivePostUpdate() {
        // 热键轮询
        if (keyInputListener != null) {
            keyInputListener.pollInput();
        }
        // Note: BattleEventListener和RewardEventListener各自接收PostUpdate事件
    }

    public BattleEventListener getBattleEventListener() {
        return battleEventListener;
    }

    public RewardEventListener getRewardEventListener() {
        return rewardEventListener;
    }

    /**
     * 触发通用场景分析
     *
     * <p>在非战斗/奖励场景下，允许用户进行对话
     * 工具会根据场景自动过滤可用性
     */
    public void triggerGeneralAnalysis() {
        System.out.println("[EventManager] Triggering general analysis");

        // 获取自定义提示词
        String userPrompt = null;
        if (STSAIAdvisorMod.isOverlayMode()) {
            try {
                userPrompt = STSAIAdvisorMod.getOverlayClient().getCustomPrompt();
            } catch (Exception e) {
                System.err.println("[EventManager] 获取自定义提示词失败: " + e.getMessage());
            }
        }

        // 使用默认提示词
        if (userPrompt == null || userPrompt.isEmpty()) {
            userPrompt = GameAgent.getDefaultUserPrompt("general");
            System.out.println("[EventManager] 使用默认提示词: " + userPrompt);
        } else {
            System.out.println("[EventManager] 使用自定义提示词: " + userPrompt);
        }

        // 刷新游戏上下文
        gameContext.refreshContext();

        // 发起异步请求
        gameAgent.process(userPrompt, gameContext);
    }
}