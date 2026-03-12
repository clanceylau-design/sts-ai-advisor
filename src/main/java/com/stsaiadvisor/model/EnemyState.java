package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents an enemy's current state.
 */
public class EnemyState {
    private String id;
    private String name;
    private int currentHealth;
    private int maxHealth;
    private int block;
    private List<EnemyIntent> intents;
    private List<String> powers;
    private int enemyIndex; // Position in enemy array

    public EnemyState() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(int currentHealth) { this.currentHealth = currentHealth; }

    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }

    public int getBlock() { return block; }
    public void setBlock(int block) { this.block = block; }

    public List<EnemyIntent> getIntents() { return intents; }
    public void setIntents(List<EnemyIntent> intents) { this.intents = intents; }

    public List<String> getPowers() { return powers; }
    public void setPowers(List<String> powers) { this.powers = powers; }

    public int getEnemyIndex() { return enemyIndex; }
    public void setEnemyIndex(int enemyIndex) { this.enemyIndex = enemyIndex; }

    @Override
    public String toString() {
        return String.format("%s (HP: %d/%d, Block: %d)", name, currentHealth, maxHealth, block);
    }
}