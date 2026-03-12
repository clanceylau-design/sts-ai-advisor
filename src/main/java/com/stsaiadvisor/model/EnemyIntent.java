package com.stsaiadvisor.model;

/**
 * Represents an enemy's intent for the next action.
 */
public class EnemyIntent {
    private String type; // ATTACK, DEFEND, BUFF, DEBUFF, SLEEP, UNKNOWN
    private int damage;
    private int multiplier;

    public EnemyIntent() {}

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }

    public int getMultiplier() { return multiplier; }
    public void setMultiplier(int multiplier) { this.multiplier = multiplier; }

    public String getDescription() {
        if (type == null) {
            return "未知";
        }
        switch (type) {
            case "ATTACK":
                if (multiplier > 1) {
                    return String.format("攻击 %d x%d", damage, multiplier);
                }
                return String.format("攻击 %d", damage);
            case "DEFEND":
                return "防御";
            case "BUFF":
                return "增益";
            case "DEBUFF":
                return "减益";
            case "SLEEP":
                return "睡眠";
            case "STUN":
                return "眩晕";
            case "ESCAPE":
                return "逃跑";
            default:
                return "未知";
        }
    }

    @Override
    public String toString() {
        return getDescription();
    }
}