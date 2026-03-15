package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.stsaiadvisor.agent.GameAgent;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.STSAIAdvisorMod;

import java.util.concurrent.CompletableFuture;

/**
 * RewardEventListener - 卡牌奖励场景事件监听器
 *
 * <p>职责：
 * <ul>
 *   <li>检测进入卡牌奖励界面</li>
 *   <li>触发选牌建议分析</li>
 *   <li>更新UI显示</li>
 * </ul>
 *
 * <p>触发时机：战斗胜利后进入卡牌奖励选择界面
 *
 * @see GameAgent
 * @see GameContext
 */
public class RewardEventListener implements PostUpdateSubscriber {

    private final GameContext gameContext;
    private final GameAgent gameAgent;
    private CompletableFuture<Void> pendingRequest;

    /** 上一帧是否在奖励界面 */
    private boolean wasInReward = false;

    /** 是否已请求过当前奖励 */
    private boolean hasRequestedCurrentReward = false;

    /**
     * 构造函数
     *
     * @param gameAgent 游戏代理
     */
    public RewardEventListener(GameAgent gameAgent) {
        this.gameContext = new GameContext();
        this.gameAgent = gameAgent;
        BaseMod.subscribe(this);
        System.out.println("[RewardEventListener] Registered");
    }

    /**
     * 每帧更新回调
     *
     * <p>检测奖励界面状态变化，触发分析
     */
    @Override
    public void receivePostUpdate() {
        // 检测是否在卡牌奖励界面
        boolean inReward = gameContext.getRewardCapture().isInCardReward();

        // 检测进入奖励界面
        if (inReward && !wasInReward) {
            System.out.println("[RewardEventListener] Entered card reward screen");
            hasRequestedCurrentReward = false;
        }

        // 检测离开奖励界面
        if (!inReward && wasInReward) {
            System.out.println("[RewardEventListener] Left card reward screen");
            hasRequestedCurrentReward = false;
            // 清理待处理请求
            pendingRequest = null;

            // 清空 Overlay 内容
            if (STSAIAdvisorMod.isOverlayMode()) {
                STSAIAdvisorMod.getOverlayClient().clear();
            }
        }

        // 在奖励界面且未请求过，自动请求分析
        if (inReward && !hasRequestedCurrentReward) {
            requestRewardAdvice();
            hasRequestedCurrentReward = true;
        }

        wasInReward = inReward;
    }

    /**
     * 手动请求奖励建议
     */
    public void requestManualAdvice() {
        if (!gameContext.getRewardCapture().isInCardReward()) {
            System.out.println("[RewardEventListener] Not in card reward screen");
            return;
        }
        requestRewardAdvice();
    }

    /**
     * 请求奖励分析
     */
    private void requestRewardAdvice() {
        // 检查是否有待处理的请求
        if (pendingRequest != null && !pendingRequest.isDone()) {
            System.out.println("[RewardEventListener] Previous request pending");
            return;
        }

        // 刷新游戏上下文
        if (!gameContext.refreshContext()) {
            System.out.println("[RewardEventListener] Failed to capture context");
            return;
        }

        if (!gameContext.isInCardReward()) {
            System.out.println("[RewardEventListener] Not in reward after refresh");
            return;
        }

        // 检查可选卡牌
        if (gameContext.getCardRewards().isEmpty()) {
            System.out.println("[RewardEventListener] No reward cards available");
            return;
        }

        System.out.println("[RewardEventListener] Requesting advice for " + gameContext.getCardRewards().size() + " cards");

        // 发起异步请求
        String userPrompt = GameAgent.getDefaultUserPrompt("reward");
        pendingRequest = gameAgent.process(userPrompt, gameContext);
        pendingRequest.whenComplete((v, e) -> {
            if (e != null) {
                System.err.println("[RewardEventListener] Error: " + e.getMessage());
            } else {
                System.out.println("[RewardEventListener] Analysis completed");
            }
        });
    }

    /**
     * 判断是否在卡牌奖励界面
     */
    public boolean isInCardReward() {
        return gameContext.getRewardCapture().isInCardReward();
    }
}