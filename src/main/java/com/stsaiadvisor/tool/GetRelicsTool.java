package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.RelicState;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetRelicsTool - 获取遗物列表工具
 *
 * <p>返回玩家拥有的所有遗物及其效果描述
 */
public class GetRelicsTool implements GameTool {

    @Override
    public String getId() {
        return "get_relics";
    }

    @Override
    public String getDescription() {
        return "获取玩家拥有的所有遗物及其效果描述";
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
                List<RelicState> relics = context.getRelics();
                if (relics.isEmpty()) {
                    JsonObject data = new JsonObject();
                    data.add("relics", new JsonArray());
                    data.addProperty("count", 0);
                    return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
                }

                JsonArray relicsArray = new JsonArray();
                for (RelicState relic : relics) {
                    JsonObject relicObj = new JsonObject();
                    relicObj.addProperty("name", relic.getName());

                    // 添加效果描述
                    if (relic.getDescription() != null && !relic.getDescription().isEmpty()) {
                        relicObj.addProperty("description", relic.getDescription());
                    }

                    // 添加计数器（如果有）
                    if (relic.getCounter() > 0) {
                        relicObj.addProperty("counter", relic.getCounter());
                    }

                    relicsArray.add(relicObj);
                }

                JsonObject data = new JsonObject();
                data.add("relics", relicsArray);
                data.addProperty("count", relics.size());

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取遗物失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}