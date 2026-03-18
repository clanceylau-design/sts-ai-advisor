package com.stsaiadvisor.capture;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.stsaiadvisor.model.ShopItemsState;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * ShopSceneCapture - 商店场景捕获器
 *
 * <p>职责：捕获商店中的商品信息
 */
public class ShopSceneCapture {

    /** 上一次检测结果，用于避免日志刷屏 */
    private boolean lastInShop = false;

    /**
     * 检测是否在商店界面
     */
    public boolean isInShop() {
        try {
            // 首先检查当前屏幕类型
            boolean isShopScreen = AbstractDungeon.screen == AbstractDungeon.CurrentScreen.SHOP;

            // 然后检查 shopScreen 状态
            boolean shopScreenActive = AbstractDungeon.shopScreen != null
                && AbstractDungeon.shopScreen.isActive;

            // 两个条件都要满足
            boolean inShop = isShopScreen && shopScreenActive;

            // 只在状态变化时打印日志
            if (inShop != lastInShop) {
                if (inShop) {
                    System.out.println("[ShopCapture] Entered shop scene");
                } else {
                    System.out.println("[ShopCapture] Left shop scene");
                }
                lastInShop = inShop;
            }

            return inShop;
        } catch (Exception e) {
            System.err.println("[ShopCapture] isInShop error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 捕获商店商品信息
     *
     * @return ShopItemsState 商店商品状态
     */
    public ShopItemsState captureShopItems() {
        ShopItemsState state = new ShopItemsState();

        if (!isInShop()) {
            System.out.println("[ShopCapture] Not in shop, returning empty state");
            return state;
        }

        try {
            ShopScreen shop = AbstractDungeon.shopScreen;
            if (shop == null) {
                return state;
            }

            // 玩家金币
            if (AbstractDungeon.player != null) {
                state.setPlayerGold(AbstractDungeon.player.gold);
            }

            // 捕获卡牌商品
            captureCardItems(shop, state);

            // 捕获遗物商品
            captureRelicItems(shop, state);

            // 捕获药水商品
            capturePotionItems(shop, state);

            // 捕获卡牌移除服务
            captureCardRemoval(shop, state);

            // 调试输出
            debugPrintShopItems(state);

            return state;
        } catch (Exception e) {
            System.err.println("[ShopCapture] Error capturing shop items: " + e.getMessage());
            e.printStackTrace();
            return state;
        }
    }

    /**
     * 捕获卡牌商品
     */
    private void captureCardItems(ShopScreen shop, ShopItemsState state) {
        try {
            // 使用反射获取卡牌列表
            Field col1Field = ShopScreen.class.getDeclaredField("col1");
            col1Field.setAccessible(true);
            Object col1 = col1Field.get(shop);

            Field col2Field = ShopScreen.class.getDeclaredField("col2");
            col2Field.setAccessible(true);
            Object col2 = col2Field.get(shop);

            int index = 0;

            // 第一列卡牌
            if (col1 instanceof ArrayList) {
                ArrayList<?> col1List = (ArrayList<?>) col1;
                for (Object item : col1List) {
                    if (item == null) continue;
                    AbstractCard card = extractCardFromMerchantItem(item);
                    if (card != null) {
                        ShopItemsState.CardItem cardItem = convertToCardItem(card, index);
                        int price = extractPriceFromMerchantItem(item);
                        cardItem.setPrice(price);
                        state.addCardItem(cardItem);
                        index++;
                    }
                }
            }

            // 第二列卡牌
            if (col2 instanceof ArrayList) {
                ArrayList<?> col2List = (ArrayList<?>) col2;
                for (Object item : col2List) {
                    if (item == null) continue;
                    AbstractCard card = extractCardFromMerchantItem(item);
                    if (card != null) {
                        ShopItemsState.CardItem cardItem = convertToCardItem(card, index);
                        int price = extractPriceFromMerchantItem(item);
                        cardItem.setPrice(price);
                        state.addCardItem(cardItem);
                        index++;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ShopCapture] Error capturing card items: " + e.getMessage());
        }
    }

    /**
     * 捕获遗物商品
     */
    private void captureRelicItems(ShopScreen shop, ShopItemsState state) {
        try {
            Field relicsField = ShopScreen.class.getDeclaredField("relics");
            relicsField.setAccessible(true);
            Object relics = relicsField.get(shop);

            if (relics instanceof ArrayList) {
                ArrayList<?> relicsList = (ArrayList<?>) relics;
                int index = 0;
                for (Object item : relicsList) {
                    if (item == null) continue;
                    com.megacrit.cardcrawl.relics.AbstractRelic relic = extractRelicFromMerchantItem(item);
                    if (relic != null) {
                        ShopItemsState.RelicItem relicItem = new ShopItemsState.RelicItem();
                        relicItem.setIndex(index);
                        relicItem.setId(relic.relicId);
                        relicItem.setName(relic.name);
                        relicItem.setDescription(relic.description);
                        relicItem.setPrice(extractPriceFromMerchantItem(item));
                        state.addRelicItem(relicItem);
                        index++;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ShopCapture] Error capturing relic items: " + e.getMessage());
        }
    }

    /**
     * 捕获药水商品
     */
    private void capturePotionItems(ShopScreen shop, ShopItemsState state) {
        try {
            Field potionsField = ShopScreen.class.getDeclaredField("potions");
            potionsField.setAccessible(true);
            Object potions = potionsField.get(shop);

            if (potions instanceof ArrayList) {
                ArrayList<?> potionsList = (ArrayList<?>) potions;
                int index = 0;
                for (Object item : potionsList) {
                    if (item == null) continue;
                    com.megacrit.cardcrawl.potions.AbstractPotion potion = extractPotionFromMerchantItem(item);
                    if (potion != null) {
                        ShopItemsState.PotionItem potionItem = new ShopItemsState.PotionItem();
                        potionItem.setIndex(index);
                        potionItem.setId(potion.ID);
                        potionItem.setName(potion.name);
                        potionItem.setDescription(potion.description);
                        potionItem.setPrice(extractPriceFromMerchantItem(item));
                        state.addPotionItem(potionItem);
                        index++;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ShopCapture] Error capturing potion items: " + e.getMessage());
        }
    }

    /**
     * 捕获卡牌移除服务
     */
    private void captureCardRemoval(ShopScreen shop, ShopItemsState state) {
        try {
            ShopItemsState.CardRemovalService service = state.getCardRemoval();
            service.setAvailable(true);

            // 尝试获取移除价格
            Field removeField = ShopScreen.class.getDeclaredField("removeCardCost");
            removeField.setAccessible(true);
            service.setPrice(removeField.getInt(shop));
        } catch (Exception e) {
            // 默认价格
            state.getCardRemoval().setPrice(75);
            state.getCardRemoval().setAvailable(true);
        }
    }

    /**
     * 从商品对象中提取卡牌
     */
    private AbstractCard extractCardFromMerchantItem(Object item) {
        try {
            Field cardField = item.getClass().getDeclaredField("card");
            cardField.setAccessible(true);
            return (AbstractCard) cardField.get(item);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从商品对象中提取遗物
     */
    private com.megacrit.cardcrawl.relics.AbstractRelic extractRelicFromMerchantItem(Object item) {
        try {
            Field relicField = item.getClass().getDeclaredField("relic");
            relicField.setAccessible(true);
            return (com.megacrit.cardcrawl.relics.AbstractRelic) relicField.get(item);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从商品对象中提取药水
     */
    private com.megacrit.cardcrawl.potions.AbstractPotion extractPotionFromMerchantItem(Object item) {
        try {
            Field potionField = item.getClass().getDeclaredField("potion");
            potionField.setAccessible(true);
            return (com.megacrit.cardcrawl.potions.AbstractPotion) potionField.get(item);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从商品对象中提取价格
     */
    private int extractPriceFromMerchantItem(Object item) {
        try {
            Field priceField = item.getClass().getDeclaredField("price");
            priceField.setAccessible(true);
            return priceField.getInt(item);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 转换卡牌到商品项
     */
    private ShopItemsState.CardItem convertToCardItem(AbstractCard card, int index) {
        ShopItemsState.CardItem item = new ShopItemsState.CardItem();
        item.setIndex(index);
        item.setId(card.cardID);
        item.setName(card.name);
        item.setType(card.type != null ? card.type.name() : "UNKNOWN");
        item.setCost(card.cost);
        item.setPrice(card.price);

        // 使用实际值，如果为-1则使用基础值（商店场景下可能未初始化）
        int damageValue = card.damage >= 0 ? card.damage : card.baseDamage;
        int blockValue = card.block >= 0 ? card.block : card.baseBlock;
        int magicValue = card.magicNumber;

        // 描述
        String description = card.rawDescription;
        if (description != null) {
            description = description.replace("!D!", String.valueOf(damageValue));
            description = description.replace("!B!", String.valueOf(blockValue));
            description = description.replace("!M!", String.valueOf(magicValue));
        }
        item.setDescription(description);

        return item;
    }

    /**
     * 调试输出商店商品
     */
    private void debugPrintShopItems(ShopItemsState state) {
        System.out.println("========== ShopItems Debug ==========");
        System.out.println("Player Gold: " + state.getPlayerGold());

        if (!state.getCardItems().isEmpty()) {
            System.out.println("Cards for sale:");
            for (ShopItemsState.CardItem card : state.getCardItems()) {
                System.out.println("  [" + card.getIndex() + "] " + card.getName()
                    + " (" + card.getType() + ", " + card.getCost() + "能量) - " + card.getPrice() + "金币");
            }
        }

        if (!state.getRelicItems().isEmpty()) {
            System.out.println("Relics for sale:");
            for (ShopItemsState.RelicItem relic : state.getRelicItems()) {
                System.out.println("  [" + relic.getIndex() + "] " + relic.getName() + " - " + relic.getPrice() + "金币");
            }
        }

        if (!state.getPotionItems().isEmpty()) {
            System.out.println("Potions for sale:");
            for (ShopItemsState.PotionItem potion : state.getPotionItems()) {
                System.out.println("  [" + potion.getIndex() + "] " + potion.getName() + " - " + potion.getPrice() + "金币");
            }
        }

        System.out.println("Card Removal: " + state.getCardRemoval().getPrice() + "金币");
        System.out.println("======================================");
    }
}