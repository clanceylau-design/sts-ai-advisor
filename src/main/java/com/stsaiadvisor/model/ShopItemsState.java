package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * ShopItemsState - 商店商品状态模型
 *
 * <p>包含商店中所有商品信息
 */
public class ShopItemsState {

    /** 卡牌商品 */
    private List<CardItem> cardItems;

    /** 遗物商品 */
    private List<RelicItem> relicItems;

    /** 药水商品 */
    private List<PotionItem> potionItems;

    /** 卡牌移除服务 */
    private CardRemovalService cardRemoval;

    /** 玩家当前金币 */
    private int playerGold;

    // ========== 内部类：卡牌商品 ==========

    public static class CardItem {
        private int index;
        private String id;
        private String name;
        private String type;
        private int cost;
        private int price;
        private String description;
        private boolean onSale;
        private int originalPrice;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public int getCost() { return cost; }
        public void setCost(int cost) { this.cost = cost; }

        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isOnSale() { return onSale; }
        public void setOnSale(boolean onSale) { this.onSale = onSale; }

        public int getOriginalPrice() { return originalPrice; }
        public void setOriginalPrice(int originalPrice) { this.originalPrice = originalPrice; }
    }

    // ========== 内部类：遗物商品 ==========

    public static class RelicItem {
        private int index;
        private String id;
        private String name;
        private int price;
        private String description;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // ========== 内部类：药水商品 ==========

    public static class PotionItem {
        private int index;
        private String id;
        private String name;
        private int price;
        private String description;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // ========== 内部类：卡牌移除服务 ==========

    public static class CardRemovalService {
        private int price;
        private boolean available;

        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
    }

    // ========== Getters and Setters ==========

    public List<CardItem> getCardItems() {
        if (cardItems == null) cardItems = new ArrayList<>();
        return cardItems;
    }
    public void setCardItems(List<CardItem> cardItems) { this.cardItems = cardItems; }

    public List<RelicItem> getRelicItems() {
        if (relicItems == null) relicItems = new ArrayList<>();
        return relicItems;
    }
    public void setRelicItems(List<RelicItem> relicItems) { this.relicItems = relicItems; }

    public List<PotionItem> getPotionItems() {
        if (potionItems == null) potionItems = new ArrayList<>();
        return potionItems;
    }
    public void setPotionItems(List<PotionItem> potionItems) { this.potionItems = potionItems; }

    public CardRemovalService getCardRemoval() {
        if (cardRemoval == null) cardRemoval = new CardRemovalService();
        return cardRemoval;
    }
    public void setCardRemoval(CardRemovalService cardRemoval) { this.cardRemoval = cardRemoval; }

    public int getPlayerGold() { return playerGold; }
    public void setPlayerGold(int playerGold) { this.playerGold = playerGold; }

    // ========== 便捷方法 ==========

    public ShopItemsState addCardItem(CardItem item) {
        getCardItems().add(item);
        return this;
    }

    public ShopItemsState addRelicItem(RelicItem item) {
        getRelicItems().add(item);
        return this;
    }

    public ShopItemsState addPotionItem(PotionItem item) {
        getPotionItems().add(item);
        return this;
    }

    public boolean hasAnyItems() {
        return !getCardItems().isEmpty() || !getRelicItems().isEmpty() || !getPotionItems().isEmpty();
    }
}