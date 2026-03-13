package com.stsaiadvisor.capture;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.stsaiadvisor.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BattleStateCapture - 战斗场景状态捕获器
 *
 * <p>职责：捕获战斗场景的完整状态，输出SceneContext
 *
 * @see SceneContext
 */
public class BattleStateCapture {

    /**
     * 捕获完整战斗上下文（返回SceneContext）
     */
    public SceneContext captureSceneContext() {
        if (!isInBattle()) {
            return null;
        }

        try {
            SceneContext context = new SceneContext();
            context.setScenario("battle");

            // 玩家状态
            context.setPlayer(capturePlayer());

            // 完整牌组（主牌组）
            context.setDeck(captureMasterDeck());

            // 手牌
            context.setHand(captureHand());

            // 抽牌堆
            context.setDrawPile(captureDrawPile());

            // 弃牌堆
            context.setDiscardPile(captureDiscardPile());

            // 敌人
            context.setEnemies(captureEnemies());

            // 遗物
            context.setRelics(captureRelics());

            // 回合和层数
            if (AbstractDungeon.actionManager != null) {
                context.setTurn(AbstractDungeon.actionManager.turn);
            }
            context.setAct(AbstractDungeon.actNum);

            // 调试输出
            debugPrintContext(context);

            return context;
        } catch (Exception e) {
            System.err.println("[AI Advisor] Error capturing battle state: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 兼容旧接口：返回BattleContext
     */
    public BattleContext capture() {
        SceneContext sceneContext = captureSceneContext();
        if (sceneContext == null) {
            return null;
        }
        return toBattleContext(sceneContext);
    }

    /**
     * SceneContext转BattleContext（兼容旧代码）
     */
    private BattleContext toBattleContext(SceneContext sc) {
        BattleContext bc = new BattleContext();
        bc.setScenario(sc.getScenario());
        bc.setPlayer(sc.getPlayer());
        bc.setHand(sc.getHand());
        bc.setDrawPile(sc.getDrawPile());
        bc.setDiscardPile(sc.getDiscardPile());
        bc.setEnemies(sc.getEnemies());
        bc.setRelics(sc.getRelics());
        bc.setTurn(sc.getTurn());
        bc.setAct(sc.getAct());
        return bc;
    }

    /**
     * 调试输出
     */
    private void debugPrintContext(SceneContext context) {
        System.out.println("========== BattleContext Debug ==========");
        System.out.println("Turn: " + context.getTurn() + ", Act: " + context.getAct());

        // Player
        if (context.getPlayer() != null) {
            PlayerState p = context.getPlayer();
            System.out.println("Player: HP=" + p.getCurrentHealth() + "/" + p.getMaxHealth() +
                ", Energy=" + p.getEnergy() + ", Block=" + p.getBlock());
        }

        // Hand cards - CRITICAL
        List<CardState> hand = context.getHand();
        System.out.println("Hand size: " + (hand != null ? hand.size() : "null"));
        if (hand != null && !hand.isEmpty()) {
            for (CardState card : hand) {
                System.out.println("  Card[" + card.getCardIndex() + "]: " + card.getName() +
                    " (Cost: " + card.getCost() + ", Type: " + card.getType() + ")");
            }
        } else {
            System.out.println("  WARNING: No hand cards captured!");
        }

        // Enemies
        List<EnemyState> enemies = context.getEnemies();
        System.out.println("Enemies: " + (enemies != null ? enemies.size() : "null"));
        if (enemies != null) {
            for (EnemyState e : enemies) {
                System.out.println("  Enemy: " + e.getName() + " HP=" + e.getCurrentHealth() + "/" + e.getMaxHealth());
                if (e.getIntents() != null && !e.getIntents().isEmpty()) {
                    System.out.println("    Intent: " + e.getIntents().get(0).getType() +
                        " Dmg=" + e.getIntents().get(0).getDamage());
                }
            }
        }

        System.out.println("=========================================");
    }

    /**
     * 捕获主牌组（完整牌组）
     */
    private List<CardState> captureMasterDeck() {
        List<CardState> cards = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.masterDeck == null) {
            return cards;
        }

        int index = 0;
        for (AbstractCard card : AbstractDungeon.player.masterDeck.group) {
            if (card != null) {
                cards.add(convertCard(card, index++));
            }
        }
        return cards;
    }

    /**
     * Check if we're currently in a battle.
     */
    public boolean isInBattle() {
        try {
            return AbstractDungeon.player != null
                && AbstractDungeon.getCurrRoom() != null
                && AbstractDungeon.getCurrRoom().monsters != null
                && !AbstractDungeon.getCurrRoom().monsters.monsters.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Capture player state.
     */
    private PlayerState capturePlayer() {
        AbstractPlayer player = AbstractDungeon.player;
        PlayerState state = new PlayerState();

        state.setCurrentHealth(player.currentHealth);
        state.setMaxHealth(player.maxHealth);
        state.setEnergy(player.energy != null ? player.energy.energy : 0);
        state.setMaxEnergy(player.energy != null ? player.energy.energyMaster : 3);
        state.setBlock(player.currentBlock);
        state.setStrength(getPowerAmount(player, "Strength"));
        state.setDexterity(getPowerAmount(player, "Dexterity"));
        state.setFocus(getPowerAmount(player, "Focus"));
        state.setGold(player.gold);
        state.setCharacterClass(player.chosenClass != null ? player.chosenClass.name() : "UNKNOWN");

        // 捕获玩家能力/buff/debuff
        List<String> powers = new ArrayList<>();
        if (player.powers != null) {
            for (AbstractPower power : player.powers) {
                if (power != null && power.name != null) {
                    String powerDesc = power.name;
                    if (power.amount != 0) {
                        powerDesc += " (" + power.amount + ")";
                    }
                    powers.add(powerDesc);
                }
            }
        }
        state.setPowers(powers);

        return state;
    }

    /**
     * Get the amount of a specific power on a creature.
     */
    private int getPowerAmount(AbstractCreature creature, String powerId) {
        if (creature == null || creature.powers == null) {
            return 0;
        }
        for (AbstractPower power : creature.powers) {
            if (power.ID != null && power.ID.equalsIgnoreCase(powerId)) {
                return power.amount;
            }
        }
        return 0;
    }

    /**
     * Capture hand cards.
     */
    private List<CardState> captureHand() {
        List<CardState> cards = new ArrayList<>();

        // Debug: check player state
        if (AbstractDungeon.player == null) {
            System.out.println("[AI Advisor] DEBUG: AbstractDungeon.player is NULL");
            return cards;
        }

        System.out.println("[AI Advisor] DEBUG: Player exists, checking hand...");

        if (AbstractDungeon.player.hand == null) {
            System.out.println("[AI Advisor] DEBUG: player.hand is NULL");
            return cards;
        }

        if (AbstractDungeon.player.hand.group == null) {
            System.out.println("[AI Advisor] DEBUG: player.hand.group is NULL");
            return cards;
        }

        System.out.println("[AI Advisor] DEBUG: hand.group size = " + AbstractDungeon.player.hand.group.size());

        int index = 0;
        for (AbstractCard card : AbstractDungeon.player.hand.group) {
            if (card != null) {
                System.out.println("[AI Advisor] DEBUG: Card[" + index + "] = " + card.name + " (ID: " + card.cardID + ")");
                cards.add(convertCard(card, index++));
            }
        }
        System.out.println("[AI Advisor] Captured " + cards.size() + " hand cards");
        return cards;
    }

    /**
     * Capture draw pile (simplified - just count and maybe a few cards).
     */
    private List<CardState> captureDrawPile() {
        List<CardState> cards = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.drawPile == null) {
            return cards;
        }
        // Just provide basic info, not full card details
        for (int i = 0; i < Math.min(3, AbstractDungeon.player.drawPile.size()); i++) {
            AbstractCard card = AbstractDungeon.player.drawPile.group.get(i);
            CardState state = new CardState();
            state.setName(card.name);
            state.setType(card.type.name());
            cards.add(state);
        }
        return cards;
    }

    /**
     * Capture discard pile (simplified).
     */
    private List<CardState> captureDiscardPile() {
        List<CardState> cards = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.discardPile == null) {
            return cards;
        }
        // Just provide basic info
        for (int i = 0; i < Math.min(3, AbstractDungeon.player.discardPile.size()); i++) {
            AbstractCard card = AbstractDungeon.player.discardPile.group.get(i);
            CardState state = new CardState();
            state.setName(card.name);
            state.setType(card.type.name());
            cards.add(state);
        }
        return cards;
    }

    /**
     * Convert a game card to our model.
     */
    private CardState convertCard(AbstractCard card, int index) {
        CardState state = new CardState();

        state.setId(card.cardID);
        state.setName(card.name);
        state.setCost(card.costForTurn);
        state.setType(card.type != null ? card.type.name() : "UNKNOWN");
        state.setDamage(card.baseDamage);
        state.setBlock(card.baseBlock);
        state.setUpgraded(card.upgraded);
        state.setEthereal(card.isEthereal);
        state.setExhausts(card.exhaust);
        state.setDescription(card.rawDescription);
        state.setCardIndex(index);

        // Keywords
        List<String> keywords = new ArrayList<>();
        if (card.tags != null) {
            for (AbstractCard.CardTags tag : card.tags) {
                keywords.add(tag.name());
            }
        }
        state.setKeywords(keywords);

        return state;
    }

    /**
     * Capture all living enemies.
     */
    private List<EnemyState> captureEnemies() {
        List<EnemyState> enemies = new ArrayList<>();
        if (AbstractDungeon.getCurrRoom() == null
            || AbstractDungeon.getCurrRoom().monsters == null) {
            return enemies;
        }

        int index = 0;
        for (AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (!monster.isDeadOrEscaped()) {
                enemies.add(convertEnemy(monster, index++));
            }
        }
        return enemies;
    }

    /**
     * Convert a game monster to our model.
     */
    private EnemyState convertEnemy(AbstractMonster monster, int index) {
        EnemyState state = new EnemyState();

        state.setId(monster.id);
        state.setName(monster.name);
        state.setCurrentHealth(monster.currentHealth);
        state.setMaxHealth(monster.maxHealth);
        state.setBlock(monster.currentBlock);
        state.setEnemyIndex(index);

        // Capture intent
        List<EnemyIntent> intents = new ArrayList<>();
        EnemyIntent intent = new EnemyIntent();

        // Determine intent type from monster.intent enum
        String intentType = "UNKNOWN";
        if (monster.intent != null) {
            switch (monster.intent) {
                case ATTACK:
                case ATTACK_BUFF:
                case ATTACK_DEBUFF:
                case ATTACK_DEFEND:
                    intentType = "ATTACK";
                    break;
                case DEFEND:
                case DEFEND_BUFF:
                case DEFEND_DEBUFF:
                    intentType = "DEFEND";
                    break;
                case BUFF:
                    intentType = "BUFF";
                    break;
                case DEBUFF:
                    intentType = "DEBUFF";
                    break;
                case SLEEP:
                    intentType = "SLEEP";
                    break;
                case STUN:
                    intentType = "STUN";
                    break;
                case ESCAPE:
                    intentType = "ESCAPE";
                    break;
                case UNKNOWN:
                case DEBUG:
                    intentType = "UNKNOWN";
                    break;
                default:
                    // Log unknown intent for debugging
                    System.out.println("[AI Advisor] Unhandled intent type: " + monster.intent);
                    intentType = "UNKNOWN";
            }
        } else {
            System.out.println("[AI Advisor] Monster intent is null for: " + monster.name);
        }

        intent.setType(intentType);

        // Try to get damage info using reflection with better error handling
        int dmg = 0;
        int multi = 1;
        try {
            // Try multiple possible field names for damage
            java.lang.reflect.Field dmgField = null;
            try {
                dmgField = AbstractMonster.class.getDeclaredField("intentDmg");
            } catch (NoSuchFieldException e1) {
                try {
                    dmgField = AbstractMonster.class.getDeclaredField("intentDamage");
                } catch (NoSuchFieldException e2) {
                    // Try to use the getter method if available
                    try {
                        java.lang.reflect.Method getDmg = AbstractMonster.class.getDeclaredMethod("getIntentDmg");
                        getDmg.setAccessible(true);
                        dmg = (Integer) getDmg.invoke(monster);
                    } catch (Exception e3) {
                        // No damage field found, use 0
                    }
                }
            }

            if (dmgField != null) {
                dmgField.setAccessible(true);
                dmg = dmgField.getInt(monster);
            }

            // Try to get multiplier
            java.lang.reflect.Field multiField = null;
            try {
                multiField = AbstractMonster.class.getDeclaredField("intentMultiAmt");
            } catch (NoSuchFieldException e1) {
                try {
                    multiField = AbstractMonster.class.getDeclaredField("intentMultiAmount");
                } catch (NoSuchFieldException e2) {
                    // Use default
                }
            }

            if (multiField != null) {
                multiField.setAccessible(true);
                multi = multiField.getInt(monster);
                if (multi <= 0) multi = 1;
            }
        } catch (Exception e) {
            // Fall back to defaults if reflection fails
            System.out.println("[AI Advisor] Could not get damage info for " + monster.name + ": " + e.getMessage());
        }

        intent.setDamage(dmg);
        intent.setMultiplier(multi);
        intents.add(intent);
        state.setIntents(intents);

        // Capture powers/debuffs
        List<String> powers = new ArrayList<>();
        if (monster.powers != null) {
            for (AbstractPower power : monster.powers) {
                if (power != null && power.name != null) {
                    powers.add(power.name + (power.amount != 0 ? " (" + power.amount + ")" : ""));
                }
            }
        }
        state.setPowers(powers);

        return state;
    }

    /**
     * Capture relic names.
     */
    private List<String> captureRelics() {
        List<String> relics = new ArrayList<>();
        if (AbstractDungeon.player == null || AbstractDungeon.player.relics == null) {
            return relics;
        }

        for (com.megacrit.cardcrawl.relics.AbstractRelic relic : AbstractDungeon.player.relics) {
            if (relic != null && relic.name != null) {
                relics.add(relic.name);
            }
        }
        return relics;
    }

    /**
     * Get a summary string for logging.
     */
    public String getSummary() {
        BattleContext ctx = capture();
        if (ctx == null) {
            return "Not in battle";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Turn ").append(ctx.getTurn());
        sb.append(" | Player: ").append(ctx.getPlayer());
        sb.append(" | Hand: ").append(ctx.getHand().size()).append(" cards");
        sb.append(" | Enemies: ").append(ctx.getEnemies().size());
        return sb.toString();
    }
}