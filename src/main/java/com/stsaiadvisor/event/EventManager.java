package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.stsaiadvisor.STSAIAdvisorMod;
import com.stsaiadvisor.agent.SceneOrchestrator;
import com.stsaiadvisor.overlay.OverlayClient;
import com.stsaiadvisor.ui.KeyInputListener;

/**
 * EventManager - 中央事件管理器
 *
 * <p>职责：
 * <ul>
 *   <li>管理所有场景的事件监听器</li>
 *   <li>协调战斗场景和奖励场景的事件处理</li>
 * </ul>
 *
 * @see BattleEventListener
 * @see RewardEventListener
 */
public class EventManager implements PostUpdateSubscriber {

    /** 战斗场景事件监听器 */
    private BattleEventListener battleEventListener;

    /** 奖励场景事件监听器 */
    private RewardEventListener rewardEventListener;

    /** 热键监听器 */
    private KeyInputListener keyInputListener;

    /**
     * 构造函数
     *
     * @param orchestrator 场景编排器
     */
    public EventManager(SceneOrchestrator orchestrator) {
        BaseMod.subscribe(this);

        // 战斗场景监听器
        battleEventListener = new BattleEventListener(orchestrator);

        // 奖励场景监听器
        rewardEventListener = new RewardEventListener(orchestrator);

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
                }
            }
        );

        System.out.println("[EventManager] Initialized with Battle + Reward listeners");
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
}