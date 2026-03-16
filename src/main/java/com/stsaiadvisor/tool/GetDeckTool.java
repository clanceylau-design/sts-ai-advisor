package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.CardState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * GetDeckTool - 获取完整牌组工具
 *
 * <p>返回玩家的完整牌组信息，用于分析流派和策略
 */
public class GetDeckTool implements GameTool {

    @Override
    public String getId() {
        return "get_deck";
    }

    @Override
    public String getDescription() {
        return "获取玩家完整牌组信息，用于分析流派构成和牌组策略";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.STABLE;
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
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                List<CardState> deck = context.getDeck();
                if (deck.isEmpty()) {
                    return ToolResult.failure(getId(), "牌组为空", System.currentTimeMillis() - start);
                }

                JsonArray cardsArray = new JsonArray();

                // 统计信息
                int attackCount = 0;
                int skillCount = 0;
                int powerCount = 0;
                int totalCost = 0;
                Map<String, Integer> cardCounts = new HashMap<>();

                for (CardState card : deck) {
                    JsonObject cardObj = new JsonObject();
                    cardObj.addProperty("name", card.getName());
                    cardObj.addProperty("cost", card.getCost());
                    cardObj.addProperty("type", card.getType());
                    if (card.isUpgraded()) {
                        cardObj.addProperty("upgraded", true);
                    }

                    cardsArray.add(cardObj);

                    // 统计
                    String type = card.getType();
                    if ("ATTACK".equals(type)) attackCount++;
                    else if ("SKILL".equals(type)) skillCount++;
                    else if ("POWER".equals(type)) powerCount++;
                    totalCost += Math.max(0, card.getCost());

                    cardCounts.merge(card.getName(), 1, Integer::sum);
                }

                JsonObject data = new JsonObject();
                data.add("cards", cardsArray);
                data.addProperty("total_count", deck.size());
                data.addProperty("attack_count", attackCount);
                data.addProperty("skill_count", skillCount);
                data.addProperty("power_count", powerCount);
                data.addProperty("average_cost", deck.isEmpty() ? 0 : Math.round(totalCost * 10.0 / deck.size()) / 10.0);

                // 添加卡牌数量统计（找出数量大于1的卡）
                JsonArray duplicatesArray = new JsonArray();
                for (Map.Entry<String, Integer> entry : cardCounts.entrySet()) {
                    if (entry.getValue() > 1) {
                        JsonObject dup = new JsonObject();
                        dup.addProperty("name", entry.getKey());
                        dup.addProperty("count", entry.getValue());
                        duplicatesArray.add(dup);
                    }
                }
                if (duplicatesArray.size() > 0) {
                    data.add("duplicates", duplicatesArray);
                }

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取牌组失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}