package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetEventOptionsTool - 获取事件选项工具
 *
 * <p>返回当前事件的选项
 */
public class GetEventOptionsTool implements GameTool {

    @Override
    public String getId() {
        return "get_event_options";
    }

    @Override
    public String getDescription() {
        return "获取当前事件的选项列表，仅在事件场景可用";
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
    public boolean isAvailableForScenario(String scenario) {
        return "event".equals(scenario);
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                List<String> options = context.getEventOptions();

                JsonArray optionsArray = new JsonArray();
                for (String option : options) {
                    optionsArray.add(option);
                }

                JsonObject data = new JsonObject();
                data.add("options", optionsArray);
                data.addProperty("count", options.size());

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取事件选项失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}