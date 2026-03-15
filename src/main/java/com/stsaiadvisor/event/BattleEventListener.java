package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.OnStartBattleSubscriber;
import basemod.interfaces.PostBattleSubscriber;
import basemod.interfaces.PostDrawSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.stsaiadvisor.agent.GameAgent;
import com.stsaiadvisor.context.GameContext;
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
 * @see GameAgent
 * @see GameContext
 */
public class BattleEventListener implements
        OnStartBattleSubscriber,
        PostBattleSubscriber,
        PostDrawSubscriber,
        PostUpdateSubscriber {

    private final GameContext gameContext;
    private final GameAgent gameAgent;
    private CompletableFuture<Void> pendingRequest;

    // Track last draw time to detect when drawing is complete
    private long lastDrawTime = 0;
    private boolean waitingForDrawComplete = false;
    private static final long DRAW_COMPLETE_DELAY_MS = 1000; // 1 second

    // Track if we've requested analysis this turn
    private int lastAnalysisTurn = -1;

    /**
     * 构造函数
     *
     * @param gameAgent 游戏代理
     */
    public BattleEventListener(GameAgent gameAgent) {
        this.gameContext = new GameContext();
        this.gameAgent = gameAgent;
        BaseMod.subscribe(this);
        System.out.println("[BattleEventListener] Registered with GameAgent");
    }

    @Override
    public void receiveOnBattleStart(AbstractRoom room) {
        System.out.println("[BattleEventListener] Battle started");
        lastAnalysisTurn = -1;
        lastDrawTime = 0;
        waitingForDrawComplete = false;
    }

    @Override
    public void receivePostBattle(AbstractRoom room) {
        System.out.println("[BattleEventListener] Battle ended");
        lastAnalysisTurn = -1;
        pendingRequest = null;
        waitingForDrawComplete = false;

        // 清空 Overlay 内容
        if (STSAIAdvisorMod.isOverlayMode()) {
            STSAIAdvisorMod.getOverlayClient().clear();
        }
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
        if (!gameContext.getBattleCapture().isInBattle()) {
            System.out.println("[BattleEventListener] Not in battle");
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

        // 清空 Overlay 内容（重新分析时清除上次的结果）
        if (STSAIAdvisorMod.isOverlayMode()) {
            STSAIAdvisorMod.getOverlayClient().clear();
        }

        // 刷新游戏上下文
        if (!gameContext.refreshContext()) {
            System.out.println("[BattleEventListener] Failed to capture context");
            return;
        }

        if (!gameContext.isInBattle()) {
            System.out.println("[BattleEventListener] Not in battle after refresh");
            return;
        }

        if (gameContext.getHandCards().isEmpty()) {
            System.out.println("[BattleEventListener] No cards in hand");
            return;
        }

        System.out.println("[BattleEventListener] Requesting analysis with " + gameContext.getHandCards().size() + " cards");

        // 发起异步请求
        String userPrompt = GameAgent.getDefaultUserPrompt("battle");
        pendingRequest = gameAgent.process(userPrompt, gameContext);
        pendingRequest.whenComplete((v, e) -> {
            if (e != null) {
                System.err.println("[BattleEventListener] Error: " + e.getMessage());
            } else {
                System.out.println("[BattleEventListener] Analysis completed");
            }
        });
    }

    /**
     * 判断是否在战斗中
     */
    public boolean isInBattle() {
        return gameContext.getBattleCapture().isInBattle();
    }
}