package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetPotionsTool - 获取药水信息工具
 *
 * <p>返回玩家拥有的所有药水
 */
public class GetPotionsTool implements GameTool {

    @Override
    public String getId() {
        return "get_potions";
    }

    @Override
    public String getDescription() {
        return "获取玩家拥有的所有药水信息";
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
        schema.add("required", new JsonObject());
        return schema;
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                List<String> potions = context.getPotions();

                JsonArray potionsArray = new JsonArray();
                for (String potion : potions) {
                    potionsArray.add(potion);
                }

                JsonObject data = new JsonObject();
                data.add("potions", potionsArray);
                data.addProperty("count", potions.size());

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取药水失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}