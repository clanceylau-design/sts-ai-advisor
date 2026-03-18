package com.stsaiadvisor.event;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.stsaiadvisor.STSAIAdvisorMod;
import com.stsaiadvisor.agent.GameAgent;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.ShopItemsState;

import java.util.concurrent.CompletableFuture;

/**
 * ShopEventListener - 商店场景事件监听器
 *
 * <p>职责：
 * <ul>
 *   <li>检测进入商店界面</li>
 *   <li>触发购物建议分析</li>
 * </ul>
 *
 * <p>触发时机：进入商店时自动触发
 */
public class ShopEventListener implements PostUpdateSubscriber {

    private final GameContext gameContext;
    private final GameAgent gameAgent;
    private CompletableFuture<Void> pendingRequest;

    /** 上一帧是否在商店 */
    private boolean wasInShop = false;

    /** 是否已请求过当前商店 */
    private boolean hasRequestedCurrentShop = false;

    /**
     * 构造函数
     *
     * @param gameAgent 游戏代理
     */
    public ShopEventListener(GameAgent gameAgent) {
        this.gameContext = new GameContext();
        this.gameAgent = gameAgent;
        BaseMod.subscribe(this);
        System.out.println("[ShopEventListener] Registered");
    }

    /**
     * 每帧更新回调
     */
    @Override
    public void receivePostUpdate() {
        try {
            // 检测是否在商店界面
            boolean inShop = gameContext.getShopCapture().isInShop();

            // 检测进入商店
            if (inShop && !wasInShop) {
                System.out.println("[ShopEventListener] Entered shop screen");
                hasRequestedCurrentShop = false;
            }

            // 检测离开商店
            if (!inShop && wasInShop) {
                System.out.println("[ShopEventListener] Left shop screen");
                hasRequestedCurrentShop = false;
                pendingRequest = null;
            }

            // 在商店且未请求过，自动请求分析
            if (inShop && !hasRequestedCurrentShop) {
                System.out.println("[ShopEventListener] Auto-triggering shop analysis");
                requestShopAdvice();
                hasRequestedCurrentShop = true;
            }

            wasInShop = inShop;
        } catch (Exception e) {
            System.err.println("[ShopEventListener] receivePostUpdate error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 手动请求商店建议
     */
    public void requestManualAdvice() {
        if (!gameContext.getShopCapture().isInShop()) {
            System.out.println("[ShopEventListener] Not in shop screen");
            return;
        }
        requestShopAdvice();
    }

    /**
     * 请求商店分析
     */
    private void requestShopAdvice() {
        // 检查是否有待处理的请求
        if (pendingRequest != null && !pendingRequest.isDone()) {
            System.out.println("[ShopEventListener] Previous request pending");
            return;
        }

        // 刷新游戏上下文
        if (!gameContext.refreshContext()) {
            System.out.println("[ShopEventListener] Failed to capture context");
            return;
        }

        if (!gameContext.isInShop()) {
            System.out.println("[ShopEventListener] Not in shop after refresh");
            return;
        }

        // 检查商品
        ShopItemsState shopItems = gameContext.getShopItems();
        if (!shopItems.hasAnyItems()) {
            System.out.println("[ShopEventListener] No shop items available");
            return;
        }

        System.out.println("[ShopEventListener] Requesting advice for shop: "
            + shopItems.getCardItems().size() + " cards, "
            + shopItems.getRelicItems().size() + " relics, "
            + shopItems.getPotionItems().size() + " potions, "
            + "gold=" + shopItems.getPlayerGold());

        // 获取自定义提示词
        String userPrompt = null;
        if (STSAIAdvisorMod.isOverlayMode()) {
            try {
                userPrompt = STSAIAdvisorMod.getOverlayClient().getCustomPrompt();
            } catch (Exception e) {
                System.err.println("[ShopEventListener] 获取自定义提示词失败: " + e.getMessage());
            }
        }

        // 使用默认提示词
        if (userPrompt == null || userPrompt.isEmpty()) {
            userPrompt = GameAgent.getDefaultUserPrompt("shop");
            System.out.println("[ShopEventListener] 使用默认提示词: " + userPrompt);
        } else {
            System.out.println("[ShopEventListener] 使用自定义提示词: " + userPrompt);
        }

        // 发起异步请求
        pendingRequest = gameAgent.process(userPrompt, gameContext);
        pendingRequest.whenComplete((v, e) -> {
            if (e != null) {
                System.err.println("[ShopEventListener] Error: " + e.getMessage());
            } else {
                System.out.println("[ShopEventListener] Analysis completed");
            }
        });
    }

    /**
     * 判断是否在商店界面
     */
    public boolean isInShop() {
        return gameContext.getShopCapture().isInShop();
    }
}