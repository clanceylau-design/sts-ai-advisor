package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.CardState;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetHandCardsTool - 获取手牌信息工具
 *
 * <p>返回当前手牌的详细信息，包括卡牌名称、费用、类型、效果等
 */
public class GetHandCardsTool implements GameTool {

    @Override
    public String getId() {
        return "get_hand_cards";
    }

    @Override
    public String getDescription() {
        return "获取当前手牌信息，包括每张卡的名称、费用、类型、效果描述等详细信息";
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
        return "battle".equals(scenario);
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                List<CardState> handCards = context.getHandCards();
                if (handCards.isEmpty()) {
                    return ToolResult.failure(getId(), "手牌为空或不在战斗中", System.currentTimeMillis() - start);
                }

                JsonArray cardsArray = new JsonArray();
                for (CardState card : handCards) {
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
                data.addProperty("count", handCards.size());

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取手牌失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}