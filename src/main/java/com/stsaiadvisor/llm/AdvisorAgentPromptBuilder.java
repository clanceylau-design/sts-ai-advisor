package com.stsaiadvisor.llm;

import com.google.gson.Gson;
import com.stsaiadvisor.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdvisorAgentPromptBuilder - 顾问Agent提示词构建器
 *
 * <p>职责：
 * <ul>
 *   <li>根据场景类型构建不同的System Prompt和User Prompt</li>
 *   <li>battle场景：出牌建议</li>
 *   <li>reward场景：选牌建议</li>
 * </ul>
 *
 * @see AdvisorAgent
 */
public class AdvisorAgentPromptBuilder {

    private static final Gson GSON = new Gson();

    // ========== Battle场景提示词 ==========

    /**
     * Battle场景System Prompt
     */
    private static final String BATTLE_SYSTEM_PROMPT =
        "你是杀戮尖塔的决策顾问。根据局势和战术建议，给出本回合最优出牌顺序。\n\n" +
        "## 决策原则\n" +
        "- CRITICAL: 生存第一，优先防御\n" +
        "- HIGH: 注意防御和资源管理\n" +
        "- LOW/MEDIUM: 可考虑连招和长期策略\n\n" +
        "## 输出要求\n" +
        "直接输出建议，格式如下：\n\n" +
        "【出牌顺序】\n" +
        "[手牌索引] 卡牌名 -> 目标敌人名：理由（10字内）\n" +
        "无目标卡牌写：[手牌索引] 卡牌名 -> 自身\n\n" +
        "【策略】一句话说明\n\n" +
        "【提示】简短鼓励（可选）\n\n" +
        "示例：\n" +
        "【出牌顺序】\n" +
        "[4] 剑柄打击 -> 酸液史莱姆：9伤击杀\n" +
        "[2] 打击 -> 酸液史莱姆：补刀\n\n" +
        "【策略】先清小怪，再处理大怪\n\n" +
        "【提示】这回合稳了！";

    // ========== Reward场景提示词 ==========

    /**
     * Reward场景System Prompt
     */
    private static final String REWARD_SYSTEM_PROMPT =
        "你是杀戮尖塔的选牌顾问。根据牌组分析和战术知识，给出卡牌奖励选择建议。\n\n" +
        "## 决策原则\n" +
        "- 流派优先：优先选择符合当前流派的卡牌\n" +
        "- 短板补充：优先解决牌组明显短板\n" +
        "- 精简原则：牌组过厚(>30张)时考虑跳过\n\n" +
        "## 输出要求\n" +
        "直接输出建议，格式如下：\n\n" +
        "【推荐】[索引] 卡牌名：理由（15字内）\n" +
        "【备选】[索引] 卡牌名：理由（可选）\n\n" +
        "【策略】选牌/跳过的整体策略\n\n" +
        "【跳过】是/否：理由（可选）\n\n" +
        "示例：\n" +
        "【推荐】[0] 灵体：消耗流核心，配合遗物\n" +
        "【备选】[1] 重刃：力量流补充输出\n\n" +
        "【策略】优先补充消耗流核心卡\n\n" +
        "【跳过】否：有核心卡可选";

    /**
     * 获取场景对应的System Prompt
     *
     * @param scenario 场景类型
     * @return System Prompt字符串
     */
    public String getSystemPrompt(String scenario) {
        if ("reward".equals(scenario)) {
            return REWARD_SYSTEM_PROMPT;
        }
        return BATTLE_SYSTEM_PROMPT;
    }

    /**
     * 获取System Prompt（兼容旧接口，默认battle场景）
     */
    public String getSystemPrompt() {
        return BATTLE_SYSTEM_PROMPT;
    }

    /**
     * 构建User Prompt
     *
     * @param request 顾问请求
     * @return User Prompt字符串
     */
    public String buildPrompt(AdvisorRequest request) {
        // 判断场景类型
        String scenario = determineScenario(request);

        if ("reward".equals(scenario)) {
            return buildRewardPrompt(request);
        }
        return buildBattlePrompt(request);
    }

    /**
     * 判断场景类型
     */
    private String determineScenario(AdvisorRequest request) {
        if (request.getSceneContext() != null) {
            return request.getSceneContext().getScenario();
        }
        if (request.getQuestion() != null && "CARD_REWARD".equals(request.getQuestion().getType())) {
            return "reward";
        }
        return "battle";
    }

    // ========== Battle场景User Prompt ==========

    /**
     * 构建Battle场景User Prompt
     */
    private String buildBattlePrompt(AdvisorRequest request) {
        StringBuilder prompt = new StringBuilder();

        // 局势摘要
        if (request.getViewState() != null) {
            ViewState viewState = request.getViewState();
            prompt.append("【局势】");
            prompt.append(viewState.getUrgencyLevel()).append(" | ");
            prompt.append(viewState.getSituationSummary());
            prompt.append("\n");
        }

        // 战术提示
        if (request.getSkills() != null && request.getSkills().getDeckStrategy() != null) {
            prompt.append("【战术】").append(request.getSkills().getDeckStrategy()).append("\n");
        }

        // 当前状态
        if (request.getContext() != null) {
            BattleContext context = request.getContext();

            // 玩家状态
            if (context.getPlayer() != null) {
                PlayerState p = context.getPlayer();
                prompt.append(String.format("【玩家】HP %d/%d 能量 %d 格挡 %d\n",
                    p.getCurrentHealth(), p.getMaxHealth(), p.getEnergy(), p.getBlock()));
            }

            // 手牌
            if (context.getHand() != null && !context.getHand().isEmpty()) {
                prompt.append("【手牌】");
                for (CardState card : context.getHand()) {
                    prompt.append(String.format("[%d]%s(%d费",
                        card.getCardIndex(),
                        card.getName(),
                        card.getCost()));
                    if (card.getDamage() > 0) {
                        prompt.append(",").append(card.getDamage()).append("伤");
                    }
                    if (card.getBlock() > 0) {
                        prompt.append(",").append(card.getBlock()).append("挡");
                    }
                    prompt.append(") ");
                }
                prompt.append("\n");
            }

            // 敌人
            if (context.getEnemies() != null && !context.getEnemies().isEmpty()) {
                prompt.append("【敌人】");
                Map<String, Integer> nameCount = new HashMap<>();
                for (int i = 0; i < context.getEnemies().size(); i++) {
                    EnemyState enemy = context.getEnemies().get(i);
                    String baseName = enemy.getName();
                    String displayName;

                    int count = nameCount.getOrDefault(baseName, 0) + 1;
                    nameCount.put(baseName, count);

                    if (countEnemiesByName(context.getEnemies(), baseName) > 1) {
                        displayName = String.format("%s(%d)", baseName, count);
                    } else {
                        displayName = baseName;
                    }

                    prompt.append(String.format("%s(%d/%dHP) ",
                        displayName,
                        enemy.getCurrentHealth(),
                        enemy.getMaxHealth()));
                }
                prompt.append("\n");
            }
        }

        prompt.append("\n请给出本回合出牌建议。");
        return prompt.toString();
    }

    // ========== Reward场景User Prompt ==========

    /**
     * 构建Reward场景User Prompt
     */
    private String buildRewardPrompt(AdvisorRequest request) {
        StringBuilder prompt = new StringBuilder();

        SceneContext context = request.getSceneContext();
        if (context == null) {
            return "请给出选牌建议。";
        }

        // 牌组分析结果
        AnalysisResult analysis = request.getSceneData("analysisResult");
        if (analysis != null) {
            if (analysis.getDeckArchetype() != null) {
                prompt.append("【流派】").append(analysis.getDeckArchetype());
                if (analysis.getArchetypeStrength() > 0) {
                    prompt.append("，成型度").append(analysis.getArchetypeStrength()).append("%");
                }
                prompt.append("\n");
            }
            if (analysis.getDeckWeaknesses() != null && !analysis.getDeckWeaknesses().isEmpty()) {
                prompt.append("【短板】").append(String.join("、", analysis.getDeckWeaknesses())).append("\n");
            }
        }

        // 牌组统计
        if (context.getDeck() != null && !context.getDeck().isEmpty()) {
            List<CardState> deck = context.getDeck();
            int attacks = 0, skills = 0, powers = 0;
            for (CardState card : deck) {
                String type = card.getType();
                if ("ATTACK".equals(type)) attacks++;
                else if ("SKILL".equals(type)) skills++;
                else if ("POWER".equals(type)) powers++;
            }
            prompt.append(String.format("【牌组】共%d张：攻击%d、技能%d、能力%d\n",
                deck.size(), attacks, skills, powers));
        }

        // 战术知识
        if (request.getSkills() != null && request.getSkills().getDeckStrategy() != null) {
            prompt.append("【战术】").append(request.getSkills().getDeckStrategy()).append("\n");
        }

        // 可选卡牌
        @SuppressWarnings("unchecked")
        List<CardState> rewardCards = context.getSceneData("rewardCards");
        if (rewardCards != null && !rewardCards.isEmpty()) {
            prompt.append("\n【可选卡牌】\n");
            for (CardState card : rewardCards) {
                prompt.append(String.format("[%d] %s（%d费）",
                    card.getCardIndex(),
                    card.getName(),
                    card.getCost()));

                // 显示实际数值属性（不依赖描述文本）
                List<String> stats = new ArrayList<>();
                if (card.getDamage() > 0) {
                    stats.add(card.getDamage() + "伤");
                }
                if (card.getBlock() > 0) {
                    stats.add(card.getBlock() + "挡");
                }
                // 检查是否有魔法数值（从description中提取数值）
                String desc = card.getDescription();
                if (desc != null) {
                    // 提取 !M! 对应的数值（通常是格挡回复等）
                    if (desc.contains("格挡") && card.getBlock() == 0) {
                        // 格挡类卡牌但block字段为0，可能需要特殊处理
                    }
                    // 简化描述，移除占位符和冗余信息
                    desc = simplifyDescription(desc);
                }

                if (!stats.isEmpty()) {
                    prompt.append(" - ").append(String.join("、", stats));
                }

                if (desc != null && !desc.isEmpty() && desc.length() > 2) {
                    prompt.append("：").append(desc);
                }
                prompt.append("\n");
            }
        }

        prompt.append("\n请给出选牌建议。");
        return prompt.toString();
    }

    /**
     * 简化卡牌描述，移除占位符和冗余信息
     */
    private String simplifyDescription(String desc) {
        if (desc == null) return "";

        // 移除动态数值占位符 !B! !D! !M! 等
        desc = desc.replaceAll("![A-Z]!", "");

        // 移除 NL（换行标记）
        desc = desc.replaceAll(" NL ", "。");
        desc = desc.replaceAll(" NL", "");

        // 移除多余空格
        desc = desc.replaceAll("\\s+", " ").trim();

        // 截断过长描述
        if (desc.length() > 60) {
            desc = desc.substring(0, 60) + "...";
        }

        return desc;
    }

    /**
     * 统计同名的敌人数量
     */
    private int countEnemiesByName(List<EnemyState> enemies, String name) {
        int count = 0;
        for (EnemyState enemy : enemies) {
            if (enemy.getName().equals(name)) {
                count++;
            }
        }
        return count;
    }
}