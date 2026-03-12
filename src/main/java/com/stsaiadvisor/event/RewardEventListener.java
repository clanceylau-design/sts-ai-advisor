package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.stsaiadvisor.STSAIAdvisorMod;
import com.stsaiadvisor.agent.SceneOrchestrator;
import com.stsaiadvisor.capture.RewardSceneCapture;
import com.stsaiadvisor.model.SceneContext;
import com.stsaiadvisor.model.SceneRecommendation;

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
 * @see RewardSceneCapture
 * @see SceneOrchestrator
 */
public class RewardEventListener implements PostUpdateSubscriber {

    /** 奖励场景状态捕获器 */
    private final RewardSceneCapture rewardCapture;

    /** 场景编排器 */
    private final SceneOrchestrator orchestrator;

    /** 待处理的请求 */
    private CompletableFuture<SceneRecommendation> pendingRequest;

    /** 上一帧是否在奖励界面 */
    private boolean wasInReward = false;

    /** 是否已请求过当前奖励 */
    private boolean hasRequestedCurrentReward = false;

    /**
     * 构造函数
     *
     * @param orchestrator 场景编排器
     */
    public RewardEventListener(SceneOrchestrator orchestrator) {
        this.rewardCapture = new RewardSceneCapture();
        this.orchestrator = orchestrator;
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
        boolean inReward = rewardCapture.isInCardReward();

        // 检测进入奖励界面
        if (inReward && !wasInReward) {
            System.out.println("[RewardEventListener] Entered card reward screen");
            hasRequestedCurrentReward = false;
            STSAIAdvisorMod.getPanel().setStatusMessage("检测到卡牌奖励...");
            STSAIAdvisorMod.getPanel().setVisible(true);
        }

        // 检测离开奖励界面
        if (!inReward && wasInReward) {
            System.out.println("[RewardEventListener] Left card reward screen");
            hasRequestedCurrentReward = false;
            // 清理待处理请求
            pendingRequest = null;
        }

        // 在奖励界面且未请求过，自动请求分析
        if (inReward && !hasRequestedCurrentReward) {
            // 稍作延迟，等待UI完全加载
            requestRewardAdvice();
            hasRequestedCurrentReward = true;
        }

        wasInReward = inReward;
    }

    /**
     * 手动请求奖励建议
     */
    public void requestManualAdvice() {
        if (!rewardCapture.isInCardReward()) {
            STSAIAdvisorMod.getPanel().setStatusMessage("不在卡牌奖励界面");
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

        // 捕获场景上下文
        SceneContext context = rewardCapture.capture();
        if (context == null) {
            STSAIAdvisorMod.getPanel().setStatusMessage("无法获取奖励信息");
            return;
        }

        // 检查可选卡牌
        @SuppressWarnings("unchecked")
        java.util.List<com.stsaiadvisor.model.CardState> rewardCards =
            context.getSceneData("rewardCards");
        if (rewardCards == null || rewardCards.isEmpty()) {
            System.out.println("[RewardEventListener] No reward cards available");
            STSAIAdvisorMod.getPanel().setStatusMessage("无可选卡牌");
            return;
        }

        System.out.println("[RewardEventListener] Requesting advice for " + rewardCards.size() + " cards");
        STSAIAdvisorMod.getPanel().setLoading(true);

        // 发起异步请求
        pendingRequest = orchestrator.processAsync(context);
        pendingRequest.thenAccept(rec -> {
            if (rec != null) {
                System.out.println("[RewardEventListener] Got recommendation");
                // 设置场景类型并更新面板
                STSAIAdvisorMod.getPanel().setScenario("reward");
                STSAIAdvisorMod.getPanel().updateRecommendation(rec.toRecommendation());
            } else {
                STSAIAdvisorMod.getPanel().setStatusMessage("无建议返回");
            }
        }).exceptionally(e -> {
            System.err.println("[RewardEventListener] Error: " + e.getMessage());
            STSAIAdvisorMod.getPanel().setStatusMessage("分析出错: " + e.getMessage());
            return null;
        });
    }

    /**
     * 判断是否在卡牌奖励界面
     */
    public boolean isInCardReward() {
        return rewardCapture.isInCardReward();
    }
}