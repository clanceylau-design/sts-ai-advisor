package com.stsaiadvisor.llm;

import com.stsaiadvisor.model.*;

import java.util.List;

/**
 * Builds prompts for the LLM based on game state.
 */
public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
        "你是杀戮尖塔（Slay the Spire）的专家级AI助手。你的任务是分析当前游戏状态，为玩家提供最优的决策建议。\n\n" +
        "## 你的能力\n" +
        "- 深度理解游戏机制：卡牌效果、遗物协同、敌人行为模式\n" +
        "- 平衡短期战术与长期战略\n" +
        "- 提供清晰的推理过程\n\n" +
        "## 输出格式\n" +
        "请以JSON格式输出建议，包含以下字段：\n" +
        "- \"suggestions\": 出牌建议数组，每项包含：\n" +
        "  - \"cardIndex\": 手牌索引（从0开始）\n" +
        "  - \"targetIndex\": 目标敌人索引（-1表示无目标或自身）\n" +
        "  - \"cardName\": 卡牌名称\n" +
        "  - \"priority\": 优先级（1=最高，5=最低）\n" +
        "  - \"reason\": 简要说明\n" +
        "- \"reasoning\": 整体策略说明（1-2句话）\n" +
        "- \"companionMessage\": 友好的鼓励评论（可选但推荐）\n\n" +
        "## 分析要点\n" +
        "1. 当前回合的最优解\n" +
        "2. 能量效率\n" +
        "3. 敌人意图应对\n" +
        "4. 牌组和遗物的协同效应\n" +
        "5. 长期资源管理";

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * Build a battle prompt from context (Chinese version).
     */
    public String buildBattlePrompt(BattleContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 当前战斗状态\n\n");

        // 玩家信息
        if (context.getPlayer() != null) {
            PlayerState p = context.getPlayer();
            prompt.append("**玩家**\n");
            prompt.append(String.format("- 生命值: %d/%d | 能量: %d/%d | 格挡: %d\n",
                p.getCurrentHealth(), p.getMaxHealth(), p.getEnergy(), p.getMaxEnergy(), p.getBlock()));
            prompt.append(String.format("- 力量: %d | 敏捷: %d | 金币: %d\n",
                p.getStrength(), p.getDexterity(), p.getGold()));
            prompt.append(String.format("- 角色: %s\n\n", getCharacterNameCN(p.getCharacterClass())));
        }

        // 手牌
        if (context.getHand() != null && !context.getHand().isEmpty()) {
            prompt.append("**手牌**（").append(context.getHand().size()).append("张）\n");
            for (CardState card : context.getHand()) {
                prompt.append(String.format("- [%d] %s%s | 费用: %d | 类型: %s",
                    card.getCardIndex(),
                    card.getName(),
                    card.isUpgraded() ? "+" : "",
                    card.getCost(),
                    getCardTypeCN(card.getType())));
                if (card.getDamage() > 0) {
                    prompt.append(" | 伤害: ").append(card.getDamage());
                }
                if (card.getBlock() > 0) {
                    prompt.append(" | 格挡: ").append(card.getBlock());
                }
                // 特殊效果标签
                if (card.isEthereal()) {
                    prompt.append(" | 虚无");
                }
                if (card.isExhausts()) {
                    prompt.append(" | 消耗");
                }
                prompt.append("\n");

                // 卡牌效果描述
                if (card.getDescription() != null && !card.getDescription().isEmpty()) {
                    prompt.append("  效果: ").append(cleanDescription(card.getDescription())).append("\n");
                }
            }
            prompt.append("\n");
        }

        // 敌人
        if (context.getEnemies() != null && !context.getEnemies().isEmpty()) {
            prompt.append("**敌人**\n");
            for (EnemyState enemy : context.getEnemies()) {
                prompt.append(String.format("- [%d] %s | 生命值: %d/%d | 格挡: %d\n",
                    enemy.getEnemyIndex(),
                    enemy.getName(),
                    enemy.getCurrentHealth(),
                    enemy.getMaxHealth(),
                    enemy.getBlock()));

                if (enemy.getIntents() != null && !enemy.getIntents().isEmpty()) {
                    prompt.append("  意图: ").append(enemy.getIntents().get(0).getDescription()).append("\n");
                }

                if (enemy.getPowers() != null && !enemy.getPowers().isEmpty()) {
                    prompt.append("  状态: ").append(String.join(", ", enemy.getPowers())).append("\n");
                }
            }
            prompt.append("\n");
        }

        // 遗物
        if (context.getRelics() != null && !context.getRelics().isEmpty()) {
            prompt.append("**遗物**\n");
            prompt.append(String.join(", ", context.getRelics())).append("\n\n");
        }

        // 回合/层数
        prompt.append(String.format("**第%d回合 | 第%d层**\n\n", context.getTurn(), context.getAct()));

        // 请求
        prompt.append("根据当前状态，请提供出牌建议（JSON格式）。\n");
        prompt.append("考虑：防御敌人攻击、最大化伤害、设置连招、能量效率。");

        return prompt.toString();
    }

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

    private String getCardTypeCN(String type) {
        if (type == null) return "未知";
        switch (type) {
            case "ATTACK": return "攻击";
            case "SKILL": return "技能";
            case "POWER": return "能力";
            case "CURSE": return "诅咒";
            case "STATUS": return "状态";
            default: return type;
        }
    }

    /**
     * Clean card description by removing special formatting characters.
     */
    private String cleanDescription(String description) {
        if (description == null) return "";
        // Remove dynamic value placeholders like !D! !B! !M!
        String cleaned = description
            .replace("!D!", "X")
            .replace("!B!", "X")
            .replace("!M!", "X")
            .replace("*", "")
            .replace("  ", " ");
        return cleaned.trim();
    }
}