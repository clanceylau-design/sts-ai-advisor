package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.stsaiadvisor.context.GameContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GuaranteedDamageCalculator - 保底伤害计算器
 *
 * <p>职责：
 * <ul>
 *   <li>计算当前能量和手牌下可打出的最大保底伤害</li>
 *   <li>仅计算攻击牌的直接伤害值（不含力量加成、遗物效果等变数）</li>
 *   <li>协助 Agent 进行基本伤害判断</li>
 * </ul>
 *
 * <p>算法：
 * <ul>
 *   <li>使用贪心算法：优先选择伤害/能量比最高的牌</li>
 *   <li>考虑多目标攻击牌的总伤害（对所有敌人伤害总和）</li>
 *   <li>不考虑抽牌、能量回复等变数</li>
 * </ul>
 *
 * <p>场景限制：仅 battle 场景可用
 */
public class GuaranteedDamageCalculator implements GameTool {

    @Override
    public String getId() {
        return "calculate_guaranteed_damage";
    }

    @Override
    public String getDescription() {
        return "Calculate the maximum guaranteed damage you can deal this turn with current energy and hand cards. "
            + "Only considers direct damage values on attack cards (no buffs, draw, or energy gain effects). "
            + "Returns optimal card sequence and total damage.";
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
    public InfoType getInfoType() {
        return InfoType.ON_DEMAND;
    }

    @Override
    public boolean isAvailableForScenario(String scenario) {
        return "battle".equals(scenario);
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<ToolResult>() {
            @Override
            public ToolResult get() {
                long start = System.currentTimeMillis();

                try {
                    // 检查是否在战斗中
                    if (AbstractDungeon.player == null || AbstractDungeon.getCurrRoom() == null) {
                        return ToolResult.failure(getId(), "Not in battle", System.currentTimeMillis() - start);
                    }

                    // 获取当前能量
                    int currentEnergy = getCurrentEnergy();

                    // 获取手牌中的攻击牌
                    List<CardDamageInfo> attackCards = getAttackCardsInHand();

                    // 计算最优组合
                    DamageCalculationResult calcResult = calculateMaxDamage(attackCards, currentEnergy);

                    // 构建结果
                    JsonObject data = buildResultJson(calcResult, currentEnergy, attackCards.size());

                    // 调试输出
                    System.out.println("[GuaranteedDamage] Energy: " + currentEnergy
                        + ", Attack cards: " + attackCards.size()
                        + ", Max damage: " + calcResult.totalDamage
                        + ", Cards used: " + calcResult.cardsUsed.size());

                    return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
                } catch (Exception e) {
                    System.err.println("[GuaranteedDamage] Error: " + e.getMessage());
                    e.printStackTrace();
                    return ToolResult.failure(getId(), "Calculation error: " + e.getMessage(), System.currentTimeMillis() - start);
                }
            }
        });
    }

    /**
     * 获取当前可用能量
     */
    private int getCurrentEnergy() {
        try {
            return EnergyPanel.totalCount;
        } catch (Exception e) {
            // 备选方案
            if (AbstractDungeon.player != null && AbstractDungeon.player.energy != null) {
                return AbstractDungeon.player.energy.energy;
            }
            return 0;
        }
    }

    /**
     * 获取手牌中的攻击牌
     */
    private List<CardDamageInfo> getAttackCardsInHand() {
        List<CardDamageInfo> cards = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.hand == null) {
            return cards;
        }

        for (AbstractCard card : AbstractDungeon.player.hand.group) {
            if (card != null && card.type == AbstractCard.CardType.ATTACK) {
                cards.add(new CardDamageInfo(card));
            }
        }
        return cards;
    }

    /**
     * 获取存活敌人数量
     */
    private int getEnemyCount() {
        if (AbstractDungeon.getCurrRoom() == null
            || AbstractDungeon.getCurrRoom().monsters == null) {
            return 1;
        }
        int count = 0;
        for (AbstractMonster m : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (m != null && !m.isDeadOrEscaped()) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * 计算最大保底伤害
     *
     * <p>使用贪心算法：优先选择伤害/能量比最高的牌
     */
    private DamageCalculationResult calculateMaxDamage(List<CardDamageInfo> cards, int energy) {
        DamageCalculationResult result = new DamageCalculationResult();

        if (cards.isEmpty() || energy <= 0) {
            return result;
        }

        // 按伤害/能量比排序（降序）
        List<CardDamageInfo> sortedCards = new ArrayList<CardDamageInfo>(cards);
        Collections.sort(sortedCards, new Comparator<CardDamageInfo>() {
            @Override
            public int compare(CardDamageInfo a, CardDamageInfo b) {
                // 优先比较伤害/能量比
                double ratioA = a.damagePerEnergy;
                double ratioB = b.damagePerEnergy;
                if (ratioA != ratioB) {
                    return Double.compare(ratioB, ratioA); // 降序
                }
                // 相同比例时，优先低费用
                return Integer.compare(a.cost, b.cost);
            }
        });

        int remainingEnergy = energy;

        // 贪心选择
        for (CardDamageInfo card : sortedCards) {
            if (remainingEnergy >= card.cost && card.damage > 0) {
                result.totalDamage += card.damage;
                result.cardsUsed.add(card);
                remainingEnergy -= card.cost;
            }
        }

        result.remainingEnergy = remainingEnergy;

        return result;
    }

    /**
     * 构建结果 JSON
     */
    private JsonObject buildResultJson(DamageCalculationResult calcResult, int totalEnergy, int attackCardCount) {
        JsonObject data = new JsonObject();

        data.addProperty("total_energy", totalEnergy);
        data.addProperty("attack_cards_in_hand", attackCardCount);
        data.addProperty("max_guaranteed_damage", calcResult.totalDamage);
        data.addProperty("remaining_energy", calcResult.remainingEnergy);

        // 最优序列
        JsonArray sequenceArray = new JsonArray();
        for (CardDamageInfo card : calcResult.cardsUsed) {
            JsonObject cardObj = new JsonObject();
            cardObj.addProperty("name", card.name);
            cardObj.addProperty("cost", card.cost);
            cardObj.addProperty("damage", card.damage);
            cardObj.addProperty("damage_per_energy", Math.round(card.damagePerEnergy * 100) / 100.0);
            sequenceArray.add(cardObj);
        }
        data.add("optimal_sequence", sequenceArray);

        // 未使用的攻击牌
        JsonArray unplayedArray = new JsonArray();
        for (CardDamageInfo card : getAttackCardsInHand()) {
            boolean used = false;
            for (CardDamageInfo usedCard : calcResult.cardsUsed) {
                if (card.name.equals(usedCard.name) && card.cost == usedCard.cost) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("name", card.name);
                cardObj.addProperty("cost", card.cost);
                cardObj.addProperty("damage", card.damage);
                unplayedArray.add(cardObj);
            }
        }
        data.add("unplayed_attacks", unplayedArray);

        return data;
    }

    /**
     * 卡牌伤害信息
     */
    private class CardDamageInfo {
        final String name;
        final int cost;
        final int damage;
        final double damagePerEnergy;

        CardDamageInfo(AbstractCard card) {
            this.name = card.name;
            this.cost = card.costForTurn;

            // 使用实际伤害值（已包含力量加成等）
            int dmg = card.damage;

            // 如果是多目标攻击，计算总伤害
            if (card.target == AbstractCard.CardTarget.ALL_ENEMY) {
                int enemyCount = getEnemyCount();
                dmg = dmg * Math.max(1, enemyCount);
            }

            this.damage = Math.max(0, dmg);

            // 计算伤害/能量比
            if (this.cost > 0) {
                this.damagePerEnergy = (double) this.damage / this.cost;
            } else {
                // 0费牌视为高优先级
                this.damagePerEnergy = this.damage > 0 ? 999.0 : 0.0;
            }
        }
    }

    /**
     * 伤害计算结果
     */
    private static class DamageCalculationResult {
        int totalDamage = 0;
        int remainingEnergy = 0;
        List<CardDamageInfo> cardsUsed = new ArrayList<CardDamageInfo>();
    }
}