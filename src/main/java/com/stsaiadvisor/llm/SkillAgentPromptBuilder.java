package com.stsaiadvisor.llm;

import com.stsaiadvisor.knowledge.KnowledgeBase;
import com.stsaiadvisor.model.*;

import java.util.List;

/**
 * SkillAgent提示词构建器
 *
 * <p>职责：构建SkillAgent所需的System Prompt和User Prompt
 *
 * <p>设计原则：简洁输出，只输出核心战术策略
 */
public class SkillAgentPromptBuilder {

    /**
     * System Prompt - 简洁版
     */
    private static final String SYSTEM_PROMPT =
        "你是杀戮尖塔的战术专家。根据牌组给出战术建议。\n\n" +
        "## 输出要求\n" +
        "直接输出结果，格式如下：\n\n" +
        "【流派】主流派名称，成型度X%\n\n" +
        "【策略】一句话整体策略\n\n" +
        "【优先目标】优先攻击的敌人\n\n" +
        "示例：\n" +
        "【流派】力量流，成型度65%\n" +
        "【策略】通过力量叠加提高伤害，优先击杀高威胁敌人\n" +
        "【优先目标】酸液史莱姆";

    /**
     * 获取System Prompt
     */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 构建User Prompt
     */
    public String buildPrompt(SkillRequest request, KnowledgeBase knowledgeBase) {
        StringBuilder prompt = new StringBuilder();

        // 角色信息
        prompt.append(String.format("【角色】%s 第%d层\n",
            getCharacterNameCN(request.getCharacterClass()), request.getAct()));

        // 流派分析
        if (request.getArchetype() != null) {
            DeckArchetype archetype = request.getArchetype();
            String primary = archetype.getPrimaryArchetype() != null ? archetype.getPrimaryArchetype() : "混合";
            prompt.append(String.format("【流派分析】%s，成型度%d%%\n",
                primary, archetype.getArchetypeStrength()));
        }

        // 牌组概要
        if (request.getFullDeck() != null && !request.getFullDeck().isEmpty()) {
            int attacks = 0, skills = 0;
            for (CardState card : request.getFullDeck()) {
                if ("ATTACK".equals(card.getType())) attacks++;
                else if ("SKILL".equals(card.getType())) skills++;
            }
            prompt.append(String.format("【牌组】%d张(%d攻%d技)\n",
                request.getFullDeck().size(), attacks, skills));
        }

        // 遗物
        if (request.getRelics() != null && !request.getRelics().isEmpty()) {
            prompt.append("【遗物】").append(String.join("、", request.getRelics())).append("\n");
        }

        // 敌人
        if (request.getEnemies() != null && !request.getEnemies().isEmpty()) {
            prompt.append("【敌人】");
            for (EnemyState enemy : request.getEnemies()) {
                prompt.append(String.format("%s(%dHP) ", enemy.getName(), enemy.getCurrentHealth()));
            }
            prompt.append("\n");
        }

        prompt.append("\n请给出战术建议。");

        return prompt.toString();
    }

    /**
     * 角色名称转中文
     */
    private String getCharacterNameCN(String characterClass) {
        if (characterClass == null) return "未知";
        switch (characterClass) {
            case "IRONCLAD": return "铁甲战士";
            case "THE_SILENT": return "静默猎人";
            case "DEFECT": return "故障机器人";
            case "WATCHER": return "观者";
            default: return characterClass;
        }
    }
}