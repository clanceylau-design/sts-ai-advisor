package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents a card's state.
 */
public class CardState {
    private String id;
    private String name;
    private int cost;
    private String type;
    private int damage;
    private int block;
    private String description;
    private boolean upgraded;
    private boolean ethereal;
    private boolean exhausts;
    private List<String> keywords;
    private int cardIndex; // Position in hand

    public CardState() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCost() { return cost; }
    public void setCost(int cost) { this.cost = cost; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }

    public int getBlock() { return block; }
    public void setBlock(int block) { this.block = block; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isUpgraded() { return upgraded; }
    public void setUpgraded(boolean upgraded) { this.upgraded = upgraded; }

    public boolean isEthereal() { return ethereal; }
    public void setEthereal(boolean ethereal) { this.ethereal = ethereal; }

    public boolean isExhausts() { return exhausts; }
    public void setExhausts(boolean exhausts) { this.exhausts = exhausts; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public int getCardIndex() { return cardIndex; }
    public void setCardIndex(int cardIndex) { this.cardIndex = cardIndex; }

    @Override
    public String toString() {
        String suffix = upgraded ? "+" : "";
        return String.format("[%d] %s%s (Cost: %d, Type: %s)", cardIndex, name, suffix, cost, type);
    }
}