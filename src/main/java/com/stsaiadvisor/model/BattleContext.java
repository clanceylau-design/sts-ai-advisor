package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the current battle context sent to the LLM.
 */
public class BattleContext {
    private String scenario = "battle";
    private PlayerState player;
    private List<CardState> hand;
    private List<CardState> drawPile;
    private List<CardState> discardPile;
    private List<EnemyState> enemies;
    private List<String> relics;
    private int turn;
    private int act;

    public BattleContext() {}

    // Getters and Setters
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public PlayerState getPlayer() { return player; }
    public void setPlayer(PlayerState player) { this.player = player; }

    public List<CardState> getHand() { return hand; }
    public void setHand(List<CardState> hand) { this.hand = hand; }

    public List<CardState> getDrawPile() { return drawPile; }
    public void setDrawPile(List<CardState> drawPile) { this.drawPile = drawPile; }

    public List<CardState> getDiscardPile() { return discardPile; }
    public void setDiscardPile(List<CardState> discardPile) { this.discardPile = discardPile; }

    public List<EnemyState> getEnemies() { return enemies; }
    public void setEnemies(List<EnemyState> enemies) { this.enemies = enemies; }

    public List<String> getRelics() { return relics; }
    public void setRelics(List<String> relics) { this.relics = relics; }

    public int getTurn() { return turn; }
    public void setTurn(int turn) { this.turn = turn; }

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }
}