package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.OnStartBattleSubscriber;
import basemod.interfaces.PostBattleSubscriber;
import basemod.interfaces.PostDrawSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.stsaiadvisor.agent.SceneOrchestrator;
import com.stsaiadvisor.capture.BattleStateCapture;
import com.stsaiadvisor.model.SceneContext;
import com.stsaiadvisor.model.SceneRecommendation;
import com.stsaiadvisor.STSAIAdvisorMod;

import java.util.concurrent.CompletableFuture;

/**
 * BattleEventListener - 战斗场景事件监听器
 *
 * <p>职责：
 * <ul>
 *   <li>监听战斗开始/结束事件</li>
 *   <li>检测抽牌完成触发分析</li>
 *   <li>处理手动请求</li>
 * </ul>
 *
 * @see SceneOrchestrator
 * @see SceneContext
 */
public class BattleEventListener implements
        OnStartBattleSubscriber,
        PostBattleSubscriber,
        PostDrawSubscriber,
        PostUpdateSubscriber {

    private final BattleStateCapture stateCapture;
    private final SceneOrchestrator orchestrator;
    private CompletableFuture<SceneRecommendation> pendingRequest;

    // Track last draw time to detect when drawing is complete
    private long lastDrawTime = 0;
    private boolean waitingForDrawComplete = false;
    private static final long DRAW_COMPLETE_DELAY_MS = 1000; // 1 second

    // Track if we've requested analysis this turn
    private int lastAnalysisTurn = -1;

    /**
     * 构造函数
     *
     * @param orchestrator 场景编排器
     */
    public BattleEventListener(SceneOrchestrator orchestrator) {
        this.stateCapture = new BattleStateCapture();
        this.orchestrator = orchestrator;
        BaseMod.subscribe(this);
        System.out.println("[BattleEventListener] Registered with Scene Orchestrator");
    }

    @Override
    public void receiveOnBattleStart(AbstractRoom room) {
        System.out.println("[BattleEventListener] Battle started");
        lastAnalysisTurn = -1;
        lastDrawTime = 0;
        waitingForDrawComplete = false;
        STSAIAdvisorMod.getPanel().setStatusMessage("战斗开始！");
        STSAIAdvisorMod.getPanel().setVisible(true);
    }

    @Override
    public void receivePostBattle(AbstractRoom room) {
        System.out.println("[BattleEventListener] Battle ended");
        lastAnalysisTurn = -1;
        pendingRequest = null;
        waitingForDrawComplete = false;
        STSAIAdvisorMod.getPanel().clear();
    }

    @Override
    public void receivePostDraw(AbstractCard card) {
        // 每次抽牌重置计时器，等待1秒无新抽牌后触发分析
        lastDrawTime = System.currentTimeMillis();
        waitingForDrawComplete = true;
        System.out.println("[BattleEventListener] Card drawn: " + card.name);
    }

    @Override
    public void receivePostUpdate() {
        // 检测抽牌完成（1秒无新抽牌）
        if (waitingForDrawComplete) {
            long elapsed = System.currentTimeMillis() - lastDrawTime;

            if (elapsed >= DRAW_COMPLETE_DELAY_MS) {
                waitingForDrawComplete = false;

                int currentTurn = getCurrentTurn();
                if (currentTurn > 0 && currentTurn != lastAnalysisTurn) {
                    if (STSAIAdvisorMod.getConfig().isEnableAutoAdvice()) {
                        System.out.println("[BattleEventListener] Draw complete, turn " + currentTurn);
                        requestAnalysis();
                        lastAnalysisTurn = currentTurn;
                    }
                }
            }
        }
    }

    private int getCurrentTurn() {
        try {
            return AbstractDungeon.actionManager != null ? AbstractDungeon.actionManager.turn : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 手动请求建议
     */
    public void requestManualAdvice() {
        if (!stateCapture.isInBattle()) {
            STSAIAdvisorMod.getPanel().setStatusMessage("不在战斗中！");
            return;
        }
        requestAnalysis();
    }

    /**
     * 请求战斗分析
     */
    private void requestAnalysis() {
        if (pendingRequest != null && !pendingRequest.isDone()) {
            System.out.println("[BattleEventListener] Previous request pending");
            return;
        }

        // 使用新的SceneContext
        SceneContext context = stateCapture.captureSceneContext();
        if (context == null) {
            STSAIAdvisorMod.getPanel().setStatusMessage("无法获取战斗状态");
            return;
        }

        if (context.getHand() == null || context.getHand().isEmpty()) {
            System.out.println("[BattleEventListener] No cards in hand");
            STSAIAdvisorMod.getPanel().setStatusMessage("等待抽牌...");
            return;
        }

        System.out.println("[BattleEventListener] Requesting analysis with " + context.getHand().size() + " cards");
        STSAIAdvisorMod.getPanel().setLoading(true);

        pendingRequest = orchestrator.processAsync(context);
        pendingRequest.thenAccept(rec -> {
            if (rec != null) {
                System.out.println("[BattleEventListener] Got recommendation");
                // 设置场景类型并更新面板
                STSAIAdvisorMod.getPanel().setScenario("battle");
                STSAIAdvisorMod.getPanel().updateRecommendation(rec.toRecommendation());
            } else {
                STSAIAdvisorMod.getPanel().setStatusMessage("无响应");
            }
        }).exceptionally(e -> {
            System.err.println("[BattleEventListener] Error: " + e.getMessage());
            STSAIAdvisorMod.getPanel().setStatusMessage("分析出错: " + e.getMessage());
            return null;
        });
    }

    /**
     * 判断是否在战斗中
     */
    public boolean isInBattle() {
        return stateCapture.isInBattle();
    }
}