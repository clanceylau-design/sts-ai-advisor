package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.BossInfoState;

import java.util.concurrent.CompletableFuture;

/**
 * GetBossInfoTool - 获取Boss信息工具
 *
 * <p>获取当前幕的最终Boss信息
 */
public class GetBossInfoTool implements GameTool {

    @Override
    public String getId() {
        return "get_boss_info";
    }

    @Override
    public String getDescription() {
        return "获取当前幕的最终Boss信息，包括可能的Boss列表和已确定的Boss名称";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.STABLE; // Boss信息在每幕内不变
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
        // 所有场景可用
        return true;
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            System.out.println("[GetBossInfoTool] Executing...");

            try {
                BossInfoState bossInfo = context.getBossInfo();

                if (bossInfo == null) {
                    System.err.println("[GetBossInfoTool] Error: Cannot get boss info");
                    return ToolResult.failure(getId(), "无法获取Boss信息，可能不在游戏中", System.currentTimeMillis() - start);
                }

                JsonObject data = new JsonObject();

                // 基础信息
                data.addProperty("act", bossInfo.getAct());
                data.addProperty("act_type", bossInfo.getActType());
                data.addProperty("is_heart_fight", bossInfo.isHeartFight());

                // Boss名称（如果已确定）
                if (bossInfo.getBossName() != null) {
                    data.addProperty("boss_name", bossInfo.getBossName());
                } else {
                    data.addProperty("boss_name", (String) null);
                }

                // 可能的Boss列表
                JsonArray bossArray = new JsonArray();
                for (String boss : bossInfo.getBossList()) {
                    bossArray.add(boss);
                }
                data.add("boss_list", bossArray);

                // 提示
                if (bossInfo.getFinalBossHint() != null) {
                    data.addProperty("final_boss_hint", bossInfo.getFinalBossHint());
                }

                System.out.println("[GetBossInfoTool] Success: Act=" + bossInfo.getAct()
                    + " Boss=" + (bossInfo.getBossName() != null ? bossInfo.getBossName() : "Unknown")
                    + " (" + (System.currentTimeMillis() - start) + "ms)");

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                System.err.println("[GetBossInfoTool] Error: " + e.getMessage());
                return ToolResult.failure(getId(), "获取Boss信息失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}