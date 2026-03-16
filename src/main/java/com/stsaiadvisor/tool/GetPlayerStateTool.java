package com.stsaiadvisor.tool;

import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.PlayerState;

import java.util.concurrent.CompletableFuture;

/**
 * GetPlayerStateTool - 获取玩家状态工具
 *
 * <p>返回玩家的HP、能量、格挡、金币等基础信息
 */
public class GetPlayerStateTool implements GameTool {

    @Override
    public String getId() {
        return "get_player_state";
    }

    @Override
    public String getDescription() {
        return "获取玩家当前状态，包括HP、能量、格挡、金币等基础信息";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.REALTIME;
    }

    @Override
    public JsonObject getParametersSchema() {
        // 无参数
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
                PlayerState player = context.getPlayerState();
                if (player == null) {
                    return ToolResult.failure(getId(), "无法获取玩家状态");
                }

                JsonObject data = new JsonObject();
                data.addProperty("current_health", player.getCurrentHealth());
                data.addProperty("max_health", player.getMaxHealth());
                data.addProperty("energy", player.getEnergy());
                data.addProperty("max_energy", player.getMaxEnergy());
                data.addProperty("block", player.getBlock());
                data.addProperty("gold", player.getGold());
                data.addProperty("character_class", player.getCharacterClass());

                // 力量/敏捷/集中
                data.addProperty("strength", player.getStrength());
                data.addProperty("dexterity", player.getDexterity());
                data.addProperty("focus", player.getFocus());

                // 能力/buff/debuff
                if (player.getPowers() != null && !player.getPowers().isEmpty()) {
                    StringBuilder powers = new StringBuilder();
                    for (String power : player.getPowers()) {
                        if (powers.length() > 0) powers.append(", ");
                        powers.append(power);
                    }
                    data.addProperty("powers", powers.toString());
                }

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取玩家状态失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}