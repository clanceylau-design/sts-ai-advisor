package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the player's current state.
 */
public class PlayerState {
    private int currentHealth;
    private int maxHealth;
    private int energy;
    private int maxEnergy;
    private int block;
    private int strength;
    private int dexterity;
    private int focus;
    private int gold;
    private String characterClass;

    /** 玩家身上的能力/buff/debuff列表 */
    private List<String> powers;

    public PlayerState() {}

    // Getters and Setters
    public int getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(int currentHealth) { this.currentHealth = currentHealth; }

    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }

    public int getEnergy() { return energy; }
    public void setEnergy(int energy) { this.energy = energy; }

    public int getMaxEnergy() { return maxEnergy; }
    public void setMaxEnergy(int maxEnergy) { this.maxEnergy = maxEnergy; }

    public int getBlock() { return block; }
    public void setBlock(int block) { this.block = block; }

    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = strength; }

    public int getDexterity() { return dexterity; }
    public void setDexterity(int dexterity) { this.dexterity = dexterity; }

    public int getFocus() { return focus; }
    public void setFocus(int focus) { this.focus = focus; }

    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }

    public String getCharacterClass() { return characterClass; }
    public void setCharacterClass(String characterClass) { this.characterClass = characterClass; }

    public List<String> getPowers() { return powers; }
    public void setPowers(List<String> powers) { this.powers = powers; }

    @Override
    public String toString() {
        return String.format("HP: %d/%d, Energy: %d/%d, Block: %d, STR: %d, DEX: %d",
            currentHealth, maxHealth, energy, maxEnergy, block, strength, dexterity);
    }
}