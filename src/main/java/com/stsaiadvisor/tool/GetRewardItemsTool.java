package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.RewardItemsState;

import java.util.concurrent.CompletableFuture;

/**
 * GetRewardItemsTool - 获取奖励物品工具
 *
 * <p>获取奖励界面中的遗物奖励和药水奖励
 */
public class GetRewardItemsTool implements GameTool {

    @Override
    public String getId() {
        return "get_reward_items";
    }

    @Override
    public String getDescription() {
        return "获取奖励界面中的遗物奖励和药水奖励，包括金币奖励和钥匙奖励";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.ON_DEMAND;
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
        return "reward".equals(scenario);
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            System.out.println("[GetRewardItemsTool] Executing...");

            try {
                RewardItemsState rewardItems = context.getRewardItems();

                JsonObject data = new JsonObject();

                // 遗物奖励
                JsonArray relicArray = new JsonArray();
                for (RewardItemsState.RewardItem relic : rewardItems.getRelicRewards()) {
                    JsonObject relicObj = new JsonObject();
                    relicObj.addProperty("index", relic.getIndex());
                    relicObj.addProperty("name", relic.getName());
                    if (relic.getId() != null) {
                        relicObj.addProperty("id", relic.getId());
                    }
                    if (relic.getDescription() != null) {
                        relicObj.addProperty("description", relic.getDescription());
                    }
                    relicArray.add(relicObj);
                }
                data.add("relic_rewards", relicArray);

                // 药水奖励
                JsonArray potionArray = new JsonArray();
                for (RewardItemsState.RewardItem potion : rewardItems.getPotionRewards()) {
                    JsonObject potionObj = new JsonObject();
                    potionObj.addProperty("index", potion.getIndex());
                    potionObj.addProperty("name", potion.getName());
                    if (potion.getId() != null) {
                        potionObj.addProperty("id", potion.getId());
                    }
                    if (potion.getDescription() != null) {
                        potionObj.addProperty("description", potion.getDescription());
                    }
                    potionArray.add(potionObj);
                }
                data.add("potion_rewards", potionArray);

                // 金币奖励
                JsonArray goldArray = new JsonArray();
                for (RewardItemsState.GoldReward gold : rewardItems.getGoldRewards()) {
                    JsonObject goldObj = new JsonObject();
                    goldObj.addProperty("index", gold.getIndex());
                    goldObj.addProperty("amount", gold.getAmount());
                    goldArray.add(goldObj);
                }
                data.add("gold_rewards", goldArray);

                // 钥匙
                JsonObject keys = new JsonObject();
                keys.addProperty("emerald_key", rewardItems.isHasEmeraldKey());
                keys.addProperty("sapphire_key", rewardItems.isHasSapphireKey());
                keys.addProperty("ruby_key", rewardItems.isHasRubyKey());
                data.add("keys", keys);

                System.out.println("[GetRewardItemsTool] Success: Relics=" + rewardItems.getRelicRewards().size()
                    + " Potions=" + rewardItems.getPotionRewards().size()
                    + " Gold=" + rewardItems.getGoldRewards().size()
                    + " (" + (System.currentTimeMillis() - start) + "ms)");

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                System.err.println("[GetRewardItemsTool] Error: " + e.getMessage());
                return ToolResult.failure(getId(), "获取奖励物品失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}