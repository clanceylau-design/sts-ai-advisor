package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.CardState;
import com.stsaiadvisor.model.PileState;

import java.util.concurrent.CompletableFuture;

/**
 * GetPilesTool - 获取牌堆信息工具
 *
 * <p>战斗中实时获取抽牌堆/弃牌堆/消耗牌堆的卡牌信息
 */
public class GetPilesTool implements GameTool {

    @Override
    public String getId() {
        return "get_piles";
    }

    @Override
    public String getDescription() {
        return "获取牌堆信息，包括抽牌堆、弃牌堆和消耗牌堆的数量统计、类型分布和卡牌预览";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.REALTIME; // 每回合都可能变化
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonArray());
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
            System.out.println("[GetPilesTool] Executing...");

            try {
                PileState piles = context.getPiles();

                JsonObject data = new JsonObject();

                // 抽牌堆
                data.add("draw_pile", convertPileInfo(piles.getDrawPile()));

                // 弃牌堆
                data.add("discard_pile", convertPileInfo(piles.getDiscardPile()));

                // 消耗牌堆
                data.add("exhaust_pile", convertPileInfo(piles.getExhaustPile()));

                System.out.println("[GetPilesTool] Success: Draw=" + piles.getDrawPile().getCount()
                    + " Discard=" + piles.getDiscardPile().getCount()
                    + " Exhaust=" + piles.getExhaustPile().getCount()
                    + " (" + (System.currentTimeMillis() - start) + "ms)");

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                System.err.println("[GetPilesTool] Error: " + e.getMessage());
                return ToolResult.failure(getId(), "获取牌堆信息失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }

    /**
     * 转换牌堆信息为JSON
     */
    private JsonObject convertPileInfo(PileState.PileInfo info) {
        JsonObject obj = new JsonObject();
        obj.addProperty("count", info.getCount());

        // 类型统计
        PileState.TypeStats stats = info.getTypeStats();
        JsonObject statsObj = new JsonObject();
        statsObj.addProperty("ATTACK", stats.getAttack());
        statsObj.addProperty("SKILL", stats.getSkill());
        statsObj.addProperty("POWER", stats.getPower());
        statsObj.addProperty("CURSE", stats.getCurse());
        statsObj.addProperty("STATUS", stats.getStatus());
        obj.add("type_stats", statsObj);

        // 预览卡牌
        JsonArray previewArray = new JsonArray();
        for (CardState card : info.getPreview()) {
            JsonObject cardObj = new JsonObject();
            cardObj.addProperty("name", card.getName());
            cardObj.addProperty("type", card.getType());
            if (card.getCost() >= 0) {
                cardObj.addProperty("cost", card.getCost());
            }
            if (card.isUpgraded()) {
                cardObj.addProperty("upgraded", true);
            }
            previewArray.add(cardObj);
        }
        obj.add("preview", previewArray);

        return obj;
    }
}