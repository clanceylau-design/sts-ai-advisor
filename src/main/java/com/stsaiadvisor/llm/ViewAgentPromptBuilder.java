package com.stsaiadvisor.llm;

import com.stsaiadvisor.model.*;

import java.util.List;

/**
 * ViewAgent提示词构建器
 *
 * <p>职责：构建ViewAgent所需的System Prompt和User Prompt
 *
 * <p>设计原则：简洁输出，减少token消耗
 */
public class ViewAgentPromptBuilder {

    /**
     * System Prompt - 简洁版
     */
    private static final String SYSTEM_PROMPT =
        "你是杀戮尖塔的战局分析专家。分析当前战斗状态，判断局势紧急程度。\n\n" +
        "## 紧急程度定义\n" +
        "- CRITICAL: 生存危机，HP<15%或下回合可能死亡\n" +
        "- HIGH: 危险，HP<30%或即将受大量伤害\n" +
        "- MEDIUM: 正常战斗\n" +
        "- LOW: 安全，可规划长期策略\n\n" +
        "## 输出要求\n" +
        "直接输出分析结果，格式如下：\n\n" +
        "【局势】等级 | 一句话总结\n\n" +
        "【威胁】预计伤害X，风险Y%，主要威胁\n\n" +
        "【机会】致死伤害X，可击杀：是/否，主要机会\n\n" +
        "示例：\n" +
        "【局势】LOW | 玩家安全，敌人意图为减益\n" +
        "【威胁】预计伤害0，风险5%，无直接威胁\n" +
        "【机会】致死伤害18，可击杀：是，可轻松击杀小怪";

    /**
     * 获取System Prompt
     */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 构建User Prompt
     */
    public String buildPrompt(BattleContext context) {
        StringBuilder prompt = new StringBuilder();

        // 玩家状态（精简）
        if (context.getPlayer() != null) {
            PlayerState p = context.getPlayer();
            int hpPercent = p.getMaxHealth() > 0 ? (p.getCurrentHealth() * 100 / p.getMaxHealth()) : 0;
            prompt.append(String.format("【玩家】HP %d/%d(%d%%) 能量 %d 格挡 %d\n",
                p.getCurrentHealth(), p.getMaxHealth(), hpPercent, p.getEnergy(), p.getBlock()));
        }

        // 敌人（精简）
        if (context.getEnemies() != null && !context.getEnemies().isEmpty()) {
            prompt.append("【敌人】");
            for (EnemyState enemy : context.getEnemies()) {
                String intent = enemy.getIntents() != null && !enemy.getIntents().isEmpty()
                    ? enemy.getIntents().get(0).getDescription() : "未知";
                prompt.append(String.format("%s(%d/%dHP,%s) ",
                    enemy.getName(), enemy.getCurrentHealth(), enemy.getMaxHealth(), intent));
            }
            prompt.append("\n");
        }

        // 手牌概要
        if (context.getHand() != null && !context.getHand().isEmpty()) {
            int totalDamage = 0, totalBlock = 0;
            for (CardState card : context.getHand()) {
                totalDamage += card.getDamage();
                totalBlock += card.getBlock();
            }
            prompt.append(String.format("【手牌】%d张，总伤害%d，总格挡%d\n",
                context.getHand().size(), totalDamage, totalBlock));
        }

        prompt.append(String.format("【回合】第%d回合 第%d层\n", context.getTurn(), context.getAct()));
        prompt.append("\n请分析局势。");

        return prompt.toString();
    }
}