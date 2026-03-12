package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the input request for SkillAgent.
 */
public class SkillRequest {
    private String characterClass;
    private List<CardState> fullDeck;
    private DeckArchetype archetype;
    private List<String> relics;
    private List<EnemyState> enemies;
    private int act;
    private String scenario;  // 场景类型: battle / reward

    public SkillRequest() {}

    // Getters and Setters
    public String getCharacterClass() { return characterClass; }
    public void setCharacterClass(String characterClass) { this.characterClass = characterClass; }

    public List<CardState> getFullDeck() { return fullDeck; }
    public void setFullDeck(List<CardState> fullDeck) { this.fullDeck = fullDeck; }

    public DeckArchetype getArchetype() { return archetype; }
    public void setArchetype(DeckArchetype archetype) { this.archetype = archetype; }

    public List<String> getRelics() { return relics; }
    public void setRelics(List<String> relics) { this.relics = relics; }

    public List<EnemyState> getEnemies() { return enemies; }
    public void setEnemies(List<EnemyState> enemies) { this.enemies = enemies; }

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
}