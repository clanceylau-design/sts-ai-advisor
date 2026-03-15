package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.CardState;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetCardRewardsTool - 获取卡牌奖励选项工具
 *
 * <p>返回可选的卡牌奖励列表
 */
public class GetCardRewardsTool implements GameTool {

    @Override
    public String getId() {
        return "get_card_rewards";
    }

    @Override
    public String getDescription() {
        return "获取当前可选的卡牌奖励列表，仅在卡牌奖励场景可用";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonObject());
        return schema;
    }

    @Override
    public boolean isAvailableForScenario(String scenario) {
        return "reward".equals(scenario);
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                if (!context.isInCardReward()) {
                    return ToolResult.failure(getId(), "不在卡牌奖励场景", System.currentTimeMillis() - start);
                }

                List<CardState> rewardCards = context.getCardRewards();
                if (rewardCards.isEmpty()) {
                    return ToolResult.failure(getId(), "没有可选的奖励卡牌", System.currentTimeMillis() - start);
                }

                JsonArray cardsArray = new JsonArray();
                for (CardState card : rewardCards) {
                    JsonObject cardObj = new JsonObject();
                    cardObj.addProperty("index", card.getCardIndex());
                    cardObj.addProperty("name", card.getName());
                    cardObj.addProperty("cost", card.getCost());
                    cardObj.addProperty("type", card.getType());

                    if (card.getDamage() > 0) {
                        cardObj.addProperty("damage", card.getDamage());
                    }
                    if (card.getBlock() > 0) {
                        cardObj.addProperty("block", card.getBlock());
                    }
                    if (card.getDescription() != null) {
                        cardObj.addProperty("description", card.getDescription());
                    }
                    if (card.isUpgraded()) {
                        cardObj.addProperty("upgraded", true);
                    }
                    if (card.isEthereal()) {
                        cardObj.addProperty("ethereal", true);
                    }
                    if (card.isExhausts()) {
                        cardObj.addProperty("exhausts", true);
                    }

                    cardsArray.add(cardObj);
                }

                JsonObject data = new JsonObject();
                data.add("cards", cardsArray);
                data.addProperty("count", rewardCards.size());

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取卡牌奖励失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}