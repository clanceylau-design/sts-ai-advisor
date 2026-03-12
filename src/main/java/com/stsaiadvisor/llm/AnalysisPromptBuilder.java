package com.stsaiadvisor.llm;

import com.stsaiadvisor.model.*;

import java.util.List;

/**
 * AnalysisPromptBuilder - 分析Agent提示词构建器
 *
 * <p>职责：
 * <ul>
 *   <li>根据场景类型构建不同的提示词</li>
 *   <li>battle场景：战斗局势分析提示词</li>
 *   <li>reward场景：牌组分析提示词</li>
 * </ul>
 *
 * @see AnalysisAgent
 */
public class AnalysisPromptBuilder {

    /**
     * 获取场景对应的系统提示词
     *
     * @param scenario 场景类型
     * @return 系统提示词
     */
    public String getSystemPrompt(String scenario) {
        switch (scenario) {
            case "battle":
                return getBattleSystemPrompt();
            case "reward":
                return getRewardSystemPrompt();
            default:
                return getBattleSystemPrompt();
        }
    }

    /**
     * 构建场景对应的用户提示词
     *
     * @param context 场景上下文
     * @return 用户提示词
     */
    public String buildPrompt(SceneContext context) {
        switch (context.getScenario()) {
            case "battle":
                return buildBattlePrompt(context);
            case "reward":
                return buildRewardPrompt(context);
            default:
                return buildBattlePrompt(context);
        }
    }

    // ========== Battle场景提示词 ==========

    /**
     * 战斗场景系统提示词
     */
    private String getBattleSystemPrompt() {
        return "你是杀戮尖塔的战局分析专家。你的任务是理解当前战斗状态并判断局势。\n\n" +
               "## 输出要求\n" +
               "直接输出分析结果，格式：\n\n" +
               "【局势】LOW|MEDIUM|HIGH|CRITICAL | 一句话总结\n" +
               "【威胁】预计伤害X，风险Y%，主要威胁描述\n" +
               "【机会】致死伤害X，可击杀：是|否，主要机会描述\n\n" +
               "局势等级说明：\n" +
               "- LOW: 玩家安全，可以规划长期策略\n" +
               "- MEDIUM: 正常战斗节奏\n" +
               "- HIGH: 玩家危险（HP<30%或即将大量伤害）\n" +
               "- CRITICAL: 生存危机（HP<15%或下回合可能死亡）\n\n" +
               "限制：输出不超过100字";
    }

    /**
     * 构建战斗场景用户提示词
     */
    private String buildBattlePrompt(SceneContext context) {
        StringBuilder prompt = new StringBuilder();

        // 玩家状态
        if (context.getPlayer() != null) {
            PlayerState p = context.getPlayer();
            prompt.append("【玩家】HP: ").append(p.getCurrentHealth()).append("/").append(p.getMaxHealth());
            prompt.append(", 能量: ").append(p.getEnergy()).append("/").append(p.getMaxEnergy());
            prompt.append(", 格挡: ").append(p.getBlock());
            if (p.getStrength() != 0) prompt.append(", 力量: ").append(p.getStrength());
            prompt.append("\n");
        }

        // 手牌
        List<CardState> hand = context.getHand();
        if (hand != null && !hand.isEmpty()) {
            prompt.append("【手牌】");
            for (CardState card : hand) {
                prompt.append("[").append(card.getCardIndex()).append("]")
                      .append(card.getName()).append("(").append(card.getCost()).append(") ");
            }
            prompt.append("\n");
        }

        // 敌人
        List<EnemyState> enemies = context.getEnemies();
        if (enemies != null && !enemies.isEmpty()) {
            prompt.append("【敌人】");
            for (EnemyState e : enemies) {
                prompt.append(e.getName()).append("(").append(e.getCurrentHealth()).append("/").append(e.getMaxHealth()).append("HP) ");
                if (e.getIntents() != null && !e.getIntents().isEmpty()) {
                    EnemyIntent intent = e.getIntents().get(0);
                    prompt.append("[").append(intent.getType());
                    if (intent.getDamage() > 0) {
                        prompt.append(intent.getMultiplier() > 1 ? "x" + intent.getMultiplier() : "");
                        prompt.append(intent.getDamage());
                    }
                    prompt.append("] ");
                }
            }
            prompt.append("\n");
        }

        prompt.append("\n请分析当前局势。");
        return prompt.toString();
    }

    // ========== Reward场景提示词 ==========

    /**
     * 奖励场景系统提示词
     */
    private String getRewardSystemPrompt() {
        return "你是杀戮尖塔的牌组分析专家。根据牌组构成分析流派和短板。\n\n" +
               "## 输出要求\n" +
               "直接输出分析结果，格式：\n\n" +
               "【流派】主流派名称，成型度X%\n" +
               "【统计】总X张，攻击X，技能X，能力X，均费X.X\n" +
               "【短板】缺防御/缺过牌/牌组过厚/无明显短板\n\n" +
               "常见流派（铁甲战士）：力量流、消耗流、格挡流、狂暴流\n" +
               "常见流派（静默猎人）：毒流、刀刃流、弃牌流\n" +
               "常见流派（故障机器人）：球流、能力流、集中流\n" +
               "常见流派（观者）：姿态流、真言流、压力流\n\n" +
               "限制：输出不超过100字";
    }

    /**
     * 构建奖励场景用户提示词
     */
    private String buildRewardPrompt(SceneContext context) {
        StringBuilder prompt = new StringBuilder();

        // 角色信息
        if (context.getPlayer() != null) {
            prompt.append("【角色】").append(getCharacterCN(context.getPlayer().getCharacterClass())).append("\n");
        }

        // 层数
        prompt.append("【层数】第").append(context.getAct()).append("层\n");

        // 完整牌组
        List<CardState> deck = context.getDeck();
        if (deck != null && !deck.isEmpty()) {
            prompt.append("【牌组】共").append(deck.size()).append("张：\n");
            // 按类型分组统计
            int attacks = 0, skills = 0, powers = 0;
            for (CardState card : deck) {
                String type = card.getType();
                if ("ATTACK".equals(type)) attacks++;
                else if ("SKILL".equals(type)) skills++;
                else if ("POWER".equals(type)) powers++;
            }
            prompt.append("攻击").append(attacks).append("张，技能").append(skills).append("张，能力").append(powers).append("张\n");
        }

        // 遗物
        List<String> relics = context.getRelics();
        if (relics != null && !relics.isEmpty()) {
            prompt.append("【遗物】").append(String.join("、", relics.subList(0, Math.min(5, relics.size()))));
            if (relics.size() > 5) prompt.append("等").append(relics.size()).append("个");
            prompt.append("\n");
        }

        // 可选卡牌（奖励场景特有）
        @SuppressWarnings("unchecked")
        List<CardState> rewardCards = context.getSceneData("rewardCards");
        if (rewardCards != null && !rewardCards.isEmpty()) {
            prompt.append("\n【可选卡牌】\n");
            for (int i = 0; i < rewardCards.size(); i++) {
                CardState card = rewardCards.get(i);
                prompt.append("[").append(i).append("] ").append(card.getName());
                prompt.append("(").append(card.getCost()).append("费)");
                if (card.getDescription() != null) {
                    prompt.append("：").append(card.getDescription());
                }
                prompt.append("\n");
            }
        }

        prompt.append("\n请分析牌组流派和短板。");
        return prompt.toString();
    }

    /**
     * 角色ID转中文名
     */
    private String getCharacterCN(String characterClass) {
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