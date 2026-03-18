package com.stsaiadvisor.capture;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.stsaiadvisor.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * RewardSceneCapture - 卡牌奖励场景状态捕获器
 *
 * <p>职责：
 * <ul>
 *   <li>检测是否处于卡牌奖励界面</li>
 *   <li>捕获可选卡牌列表</li>
 *   <li>捕获完整牌组信息</li>
 * </ul>
 *
 * @see SceneContext
 */
public class RewardSceneCapture {

    /** 上一次检测结果，用于避免日志刷屏 */
    private boolean lastInCardReward = false;

    /**
     * 检测是否处于卡牌奖励选择界面
     *
     * <p>检测两种情况：
     * <ul>
     *   <li>CARD_REWARD screen - 特定卡牌奖励界面</li>
     *   <li>combatRewardScreen - 战斗胜利后的奖励界面</li>
     * </ul>
     *
     * @return true如果正在选择卡牌奖励
     */
    public boolean isInCardReward() {
        try {
            boolean inCardReward = false;
            String detectedSource = null;

            // 情况1：标准的 CARD_REWARD screen
            if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD) {
                inCardReward = true;
                detectedSource = "CARD_REWARD screen";
            }

            // 情况2：战斗胜利后的 combatRewardScreen
            if (!inCardReward && AbstractDungeon.combatRewardScreen != null) {
                // 检查奖励界面是否打开且包含卡牌奖励
                boolean hasCardReward = false;

                if (AbstractDungeon.combatRewardScreen.rewards != null) {
                    for (RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
                        if (reward != null && reward.type == RewardItem.RewardType.CARD) {
                            hasCardReward = true;
                            break;
                        }
                    }
                }

                // 如果有卡牌奖励，检查界面是否正在显示
                // 通常在战斗胜利后，房间phase为COMPLETE，且screen不为NONE时说明在奖励界面
                if (hasCardReward) {
                    boolean isShowing = false;

                    // 通过反射检查 rewardPanel 是否可见
                    if (AbstractDungeon.overlayMenu != null) {
                        try {
                            // 获取 rewardPanel 字段（需要反射，因为是 protected）
                            java.lang.reflect.Field rewardPanelField =
                                AbstractDungeon.overlayMenu.getClass().getDeclaredField("rewardPanel");
                            rewardPanelField.setAccessible(true);
                            Object rewardPanel = rewardPanelField.get(AbstractDungeon.overlayMenu);

                            if (rewardPanel != null) {
                                // 获取 show 字段
                                java.lang.reflect.Field showField =
                                    rewardPanel.getClass().getDeclaredField("show");
                                showField.setAccessible(true);
                                isShowing = showField.getBoolean(rewardPanel);
                            }
                        } catch (Exception e) {
                            // 反射失败时，使用备选判断：房间COMPLETE且有卡牌奖励
                            isShowing = AbstractDungeon.getCurrRoom() != null
                                && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMPLETE;
                        }
                    }

                    if (isShowing) {
                        inCardReward = true;
                        detectedSource = "combatRewardScreen";
                    }
                }
            }

            // 只在状态变化时打印日志
            if (inCardReward != lastInCardReward) {
                if (inCardReward) {
                    System.out.println("[RewardCapture] Entered card reward scene (" + detectedSource + ")");
                } else {
                    System.out.println("[RewardCapture] Left card reward scene");
                }
                lastInCardReward = inCardReward;
            }

            return inCardReward;
        } catch (Exception e) {
            System.err.println("[RewardCapture] isInCardReward error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 捕获卡牌奖励场景上下文
     *
     * @return SceneContext 场景上下文，如果不是卡牌奖励场景返回null
     */
    public SceneContext capture() {
        if (!isInCardReward()) {
            return null;
        }

        try {
            SceneContext context = new SceneContext();
            context.setScenario("reward");

            // 玩家状态
            context.setPlayer(capturePlayer());

            // 完整牌组
            context.setDeck(captureDeck());

            // 遗物
            context.setRelics(captureRelics());

            // 药水
            context.setPotions(capturePotions());

            // 层数
            context.setAct(AbstractDungeon.actNum);

            // 可选卡牌（场景特定数据）
            List<CardState> rewardCards = captureRewardCards();
            context.addSceneData("rewardCards", rewardCards);

            // 调试输出
            debugPrintContext(context, rewardCards);

            return context;
        } catch (Exception e) {
            System.err.println("[RewardCapture] Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 捕获玩家状态
     */
    private PlayerState capturePlayer() {
        if (AbstractDungeon.player == null) {
            return null;
        }

        PlayerState state = new PlayerState();
        state.setCurrentHealth(AbstractDungeon.player.currentHealth);
        state.setMaxHealth(AbstractDungeon.player.maxHealth);
        state.setGold(AbstractDungeon.player.gold);
        state.setCharacterClass(AbstractDungeon.player.chosenClass != null
            ? AbstractDungeon.player.chosenClass.name() : "UNKNOWN");
        return state;
    }

    /**
     * 捕获完整牌组（主牌组，不包含手牌和弃牌堆）
     */
    private List<CardState> captureDeck() {
        List<CardState> cards = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.masterDeck == null) {
            return cards;
        }

        CardGroup masterDeck = AbstractDungeon.player.masterDeck;
        int index = 0;
        for (AbstractCard card : masterDeck.group) {
            if (card != null) {
                cards.add(convertCard(card, index++));
            }
        }
        return cards;
    }

    /**
     * 捕获可选奖励卡牌
     */
    private List<CardState> captureRewardCards() {
        List<CardState> cards = new ArrayList<>();

        try {
            // 从奖励列表中获取卡牌
            if (AbstractDungeon.combatRewardScreen != null
                && AbstractDungeon.combatRewardScreen.rewards != null) {

                int index = 0;
                for (RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
                    if (reward.type == RewardItem.RewardType.CARD && reward.cards != null) {
                        for (AbstractCard card : reward.cards) {
                            if (card != null) {
                                cards.add(convertCard(card, index++));
                            }
                        }
                    }
                }
            }

            // 备选：从cardRewardScreen获取
            if (cards.isEmpty() && AbstractDungeon.cardRewardScreen != null) {
                // 尝试通过反射获取rewardCards字段
                try {
                    java.lang.reflect.Field field = AbstractDungeon.cardRewardScreen.getClass()
                        .getDeclaredField("rewardCards");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<AbstractCard> rewardCards = (List<AbstractCard>) field.get(AbstractDungeon.cardRewardScreen);
                    if (rewardCards != null) {
                        int index = 0;
                        for (AbstractCard card : rewardCards) {
                            if (card != null) {
                                cards.add(convertCard(card, index++));
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[RewardCapture] Could not get reward cards: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[RewardCapture] Error capturing reward cards: " + e.getMessage());
        }

        return cards;
    }

    /**
     * 捕获遗物列表
     */
    private List<RelicState> captureRelics() {
        List<RelicState> relics = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.relics == null) {
            return relics;
        }

        for (com.megacrit.cardcrawl.relics.AbstractRelic relic : AbstractDungeon.player.relics) {
            if (relic != null && relic.name != null) {
                RelicState state = new RelicState();
                state.setId(relic.relicId);
                state.setName(relic.name);
                state.setDescription(relic.description);
                state.setCounter(relic.counter);
                relics.add(state);
            }
        }
        return relics;
    }

    /**
     * 捕获药水列表
     */
    private List<PotionState> capturePotions() {
        List<PotionState> potions = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.potions == null) {
            return potions;
        }

        int slot = 0;
        for (com.megacrit.cardcrawl.potions.AbstractPotion potion : AbstractDungeon.player.potions) {
            if (potion != null && potion.name != null && !potion.name.isEmpty()) {
                PotionState state = new PotionState();
                state.setId(potion.ID);
                state.setName(potion.name);
                state.setDescription(potion.description);
                state.setSlot(slot);
                potions.add(state);
            }
            slot++;
        }
        return potions;
    }

    /**
     * 转换游戏卡牌到模型
     */
    private CardState convertCard(AbstractCard card, int index) {
        CardState state = new CardState();
        state.setId(card.cardID);
        state.setName(card.name);
        state.setCost(card.cost);
        state.setType(card.type != null ? card.type.name() : "UNKNOWN");

        // 使用实际值，如果为-1则使用基础值（奖励场景下 damage/block 可能未初始化）
        int damageValue = card.damage >= 0 ? card.damage : card.baseDamage;
        int blockValue = card.block >= 0 ? card.block : card.baseBlock;
        int magicValue = card.magicNumber;

        state.setDamage(damageValue);
        state.setBlock(blockValue);
        state.setUpgraded(card.upgraded);
        state.setEthereal(card.isEthereal);
        state.setExhausts(card.exhaust);

        // 替换描述中的动态数值占位符
        String description = card.rawDescription;
        if (description != null) {
            // !D! = 伤害值
            description = description.replace("!D!", String.valueOf(damageValue));
            // !B! = 格挡值
            description = description.replace("!B!", String.valueOf(blockValue));
            // !M! = 魔法数值
            description = description.replace("!M!", String.valueOf(magicValue));
        }
        state.setDescription(description);
        state.setCardIndex(index);
        return state;
    }

    /**
     * 调试输出
     */
    private void debugPrintContext(SceneContext context, List<CardState> rewardCards) {
        System.out.println("========== RewardContext Debug ==========");
        System.out.println("Scenario: " + context.getScenario());
        System.out.println("Act: " + context.getAct());

        if (context.getPlayer() != null) {
            PlayerState p = context.getPlayer();
            System.out.println("Player: HP=" + p.getCurrentHealth() + "/" + p.getMaxHealth() + ", Gold=" + p.getGold());
        }

        System.out.println("Deck size: " + (context.getDeck() != null ? context.getDeck().size() : 0));
        System.out.println("Relics: " + (context.getRelics() != null ? context.getRelics().size() : 0));
        System.out.println("Potions: " + (context.getPotions() != null ? context.getPotions().size() : 0));
        System.out.println("Reward cards: " + rewardCards.size());
        for (CardState card : rewardCards) {
            System.out.println("  [" + card.getCardIndex() + "] " + card.getName() + " (" + card.getCost() + "费)");
        }
        System.out.println("=========================================");
    }

    // ========== 新增：奖励物品捕获方法 ==========

    /**
     * 捕获奖励界面中的遗物和药水奖励
     *
     * @return RewardItemsState 包含遗物奖励、药水奖励和金币奖励
     */
    public RewardItemsState captureRewardItems() {
        RewardItemsState state = new RewardItemsState();

        if (AbstractDungeon.combatRewardScreen == null
            || AbstractDungeon.combatRewardScreen.rewards == null) {
            System.out.println("[RewardCapture] captureRewardItems: No reward screen available");
            return state;
        }

        int index = 0;
        for (RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            if (reward == null) continue;

            // 使用字符串比较，避免枚举兼容性问题
            String typeName = reward.type != null ? reward.type.name() : "";

            if ("RELIC".equals(typeName)) {
                RewardItemsState.RewardItem relicItem = new RewardItemsState.RewardItem();
                relicItem.setIndex(index);
                if (reward.relic != null) {
                    relicItem.setId(reward.relic.relicId);
                    relicItem.setName(reward.relic.name);
                    relicItem.setDescription(reward.relic.description);
                } else {
                    relicItem.setName("未知遗物");
                }
                state.addRelicReward(relicItem);
            } else if ("POTION".equals(typeName)) {
                RewardItemsState.RewardItem potionItem = new RewardItemsState.RewardItem();
                potionItem.setIndex(index);
                if (reward.potion != null) {
                    potionItem.setId(reward.potion.ID);
                    potionItem.setName(reward.potion.name);
                    potionItem.setDescription(reward.potion.description);
                } else {
                    potionItem.setName("未知药水");
                }
                state.addPotionReward(potionItem);
            } else if ("GOLD".equals(typeName)) {
                RewardItemsState.GoldReward goldReward = new RewardItemsState.GoldReward();
                goldReward.setIndex(index);
                goldReward.setAmount(reward.goldAmt);
                state.addGoldReward(goldReward);
            } else if ("EMERALD_KEY".equals(typeName)) {
                state.setHasEmeraldKey(true);
            } else if ("SAPPHIRE_KEY".equals(typeName)) {
                state.setHasSapphireKey(true);
            } else if ("RUBY_KEY".equals(typeName)) {
                state.setHasRubyKey(true);
            }
            // CARD 类型已在 captureRewardCards() 中处理

            index++;
        }

        // 调试日志
        debugPrintRewardItems(state);

        return state;
    }

    /**
     * 调试输出奖励物品信息
     */
    private void debugPrintRewardItems(RewardItemsState state) {
        System.out.println("========== RewardItems Debug ==========");

        if (!state.getRelicRewards().isEmpty()) {
            System.out.println("Relic Rewards:");
            for (RewardItemsState.RewardItem relic : state.getRelicRewards()) {
                System.out.println("  [" + relic.getIndex() + "] " + relic.getName());
            }
        }

        if (!state.getPotionRewards().isEmpty()) {
            System.out.println("Potion Rewards:");
            for (RewardItemsState.RewardItem potion : state.getPotionRewards()) {
                System.out.println("  [" + potion.getIndex() + "] " + potion.getName());
            }
        }

        if (!state.getGoldRewards().isEmpty()) {
            System.out.println("Gold Rewards:");
            for (RewardItemsState.GoldReward gold : state.getGoldRewards()) {
                System.out.println("  [" + gold.getIndex() + "] " + gold.getAmount() + " gold");
            }
        }

        if (state.isHasEmeraldKey() || state.isHasSapphireKey() || state.isHasRubyKey()) {
            System.out.println("Keys: Emerald=" + state.isHasEmeraldKey()
                + " Sapphire=" + state.isHasSapphireKey()
                + " Ruby=" + state.isHasRubyKey());
        }

        System.out.println("========================================");
    }
}