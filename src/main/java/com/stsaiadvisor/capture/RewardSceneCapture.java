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
     * 转换游戏卡牌到模型
     */
    private CardState convertCard(AbstractCard card, int index) {
        CardState state = new CardState();
        state.setId(card.cardID);
        state.setName(card.name);
        state.setCost(card.cost);
        state.setType(card.type != null ? card.type.name() : "UNKNOWN");
        state.setDamage(card.baseDamage);
        state.setBlock(card.baseBlock);
        state.setUpgraded(card.upgraded);
        state.setEthereal(card.isEthereal);
        state.setExhausts(card.exhaust);
        state.setDescription(card.rawDescription);
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
        System.out.println("Reward cards: " + rewardCards.size());
        for (CardState card : rewardCards) {
            System.out.println("  [" + card.getCardIndex() + "] " + card.getName() + " (" + card.getCost() + "费)");
        }
        System.out.println("=========================================");
    }
}