package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.EnemyState;
import com.stsaiadvisor.model.EnemyIntent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetEnemiesTool - 获取敌人信息工具
 *
 * <p>返回当前战斗中所有敌人的详细信息，包括HP、格挡、意图等
 */
public class GetEnemiesTool implements GameTool {

    @Override
    public String getId() {
        return "get_enemies";
    }

    @Override
    public String getDescription() {
        return "获取当前战斗中所有敌人的信息，包括HP、格挡、意图类型、预计伤害等";
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
                List<EnemyState> enemies = context.getEnemies();
                if (enemies.isEmpty()) {
                    return ToolResult.failure(getId(), "没有敌人或不在战斗中", System.currentTimeMillis() - start);
                }

                JsonArray enemiesArray = new JsonArray();
                for (EnemyState enemy : enemies) {
                    JsonObject enemyObj = new JsonObject();
                    enemyObj.addProperty("index", enemy.getEnemyIndex());
                    enemyObj.addProperty("name", enemy.getName());
                    enemyObj.addProperty("current_health", enemy.getCurrentHealth());
                    enemyObj.addProperty("max_health", enemy.getMaxHealth());
                    enemyObj.addProperty("block", enemy.getBlock());

                    // 意图
                    if (enemy.getIntents() != null && !enemy.getIntents().isEmpty()) {
                        EnemyIntent intent = enemy.getIntents().get(0);
                        enemyObj.addProperty("intent_type", intent.getType());
                        if (intent.getDamage() > 0) {
                            enemyObj.addProperty("intent_damage", intent.getDamage());
                            if (intent.getMultiplier() > 1) {
                                enemyObj.addProperty("intent_multiplier", intent.getMultiplier());
                                enemyObj.addProperty("intent_total_damage", intent.getDamage() * intent.getMultiplier());
                            }
                        }
                    }

                    // 能力/buff/debuff
                    if (enemy.getPowers() != null && !enemy.getPowers().isEmpty()) {
                        StringBuilder powers = new StringBuilder();
                        for (String power : enemy.getPowers()) {
                            if (powers.length() > 0) powers.append(", ");
                            powers.append(power);
                        }
                        enemyObj.addProperty("powers", powers.toString());
                    }

                    enemiesArray.add(enemyObj);
                }

                JsonObject data = new JsonObject();
                data.add("enemies", enemiesArray);
                data.addProperty("count", enemies.size());

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取敌人信息失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}