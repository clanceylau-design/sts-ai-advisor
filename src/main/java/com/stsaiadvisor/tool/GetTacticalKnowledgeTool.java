package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.knowledge.SkillManager;
import com.stsaiadvisor.model.CardState;
import com.stsaiadvisor.model.EnemyState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetTacticalKnowledgeTool - 获取战术知识工具
 *
 * <p>根据当前牌组和敌人筛选相关的战术知识
 */
public class GetTacticalKnowledgeTool implements GameTool {

    private final SkillManager skillManager;

    public GetTacticalKnowledgeTool() {
        this.skillManager = new SkillManager("mods/sts-ai-advisor/skills");
        this.skillManager.loadMetadata();
    }

    public GetTacticalKnowledgeTool(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @Override
    public String getId() {
        return "get_tactical_knowledge";
    }

    @Override
    public String getDescription() {
        return "获取与当前牌组和敌人相关的战术知识，帮助做出更好的决策";
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
                if (!skillManager.isLoaded()) {
                    return ToolResult.failure(getId(), "战术知识库未加载", System.currentTimeMillis() - start);
                }

                // 获取当前牌组和敌人信息
                List<CardState> deck = context.getDeck();
                List<CardState> handCards = context.getHandCards();
                List<EnemyState> enemies = context.getEnemies();

                // 提取卡牌名称
                List<String> cardNames = new ArrayList<>();
                for (CardState card : deck) {
                    if (card.getName() != null) cardNames.add(card.getName());
                }
                for (CardState card : handCards) {
                    if (card.getName() != null && !cardNames.contains(card.getName())) {
                        cardNames.add(card.getName());
                    }
                }

                // 提取敌人名称
                List<String> enemyNames = new ArrayList<>();
                for (EnemyState enemy : enemies) {
                    if (enemy.getName() != null) enemyNames.add(enemy.getName());
                }

                // 获取角色类型
                String characterClass = null;
                if (context.getPlayerState() != null) {
                    characterClass = context.getPlayerState().getCharacterClass();
                }

                // 获取当前场景
                String scenario = context.getCurrentScenario();

                // 筛选相关技能
                List<String> relevantSkills = skillManager.selectRelevantSkills(
                    characterClass, cardNames, enemyNames, scenario
                );

                if (relevantSkills.isEmpty()) {
                    JsonObject data = new JsonObject();
                    data.addProperty("message", "没有找到相关的战术知识");
                    return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
                }

                // 读取技能内容（限制数量）
                JsonArray skillsArray = new JsonArray();
                int maxSkills = 3;
                int count = 0;
                for (String skillId : relevantSkills) {
                    if (count >= maxSkills) break;

                    String content = skillManager.getSkillContent(skillId);
                    if (content != null && !content.isEmpty()) {
                        JsonObject skillObj = new JsonObject();
                        skillObj.addProperty("id", skillId);
                        skillObj.addProperty("content", content);
                        skillsArray.add(skillObj);
                        count++;
                    }
                }

                JsonObject data = new JsonObject();
                data.add("skills", skillsArray);
                data.addProperty("total_found", relevantSkills.size());
                data.addProperty("returned", count);

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return ToolResult.failure(getId(), "获取战术知识失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}