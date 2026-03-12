package com.stsaiadvisor.model;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for data model serialization/deserialization.
 */
public class ModelTest {

    private static final Gson GSON = new Gson();

    @Test
    public void testPlayerStateSerialization() {
        PlayerState state = new PlayerState();
        state.setCurrentHealth(50);
        state.setMaxHealth(80);
        state.setEnergy(3);
        state.setStrength(2);
        state.setCharacterClass("IRONCLAD");

        String json = GSON.toJson(state);
        assertNotNull(json);
        assertTrue(json.contains("currentHealth"));
        assertTrue(json.contains("50"));

        PlayerState deserialized = GSON.fromJson(json, PlayerState.class);
        assertEquals(50, deserialized.getCurrentHealth());
        assertEquals(80, deserialized.getMaxHealth());
        assertEquals(3, deserialized.getEnergy());
        assertEquals("IRONCLAD", deserialized.getCharacterClass());
    }

    @Test
    public void testCardStateSerialization() {
        CardState state = new CardState();
        state.setId("Strike_R");
        state.setName("Strike");
        state.setCost(1);
        state.setType("ATTACK");
        state.setDamage(6);
        state.setCardIndex(0);

        String json = GSON.toJson(state);
        assertNotNull(json);

        CardState deserialized = GSON.fromJson(json, CardState.class);
        assertEquals("Strike_R", deserialized.getId());
        assertEquals(1, deserialized.getCost());
    }

    @Test
    public void testBattleContextSerialization() {
        BattleContext context = new BattleContext();
        context.setTurn(1);
        context.setAct(2);

        PlayerState player = new PlayerState();
        player.setCurrentHealth(45);
        player.setMaxHealth(80);
        context.setPlayer(player);

        String json = GSON.toJson(context);
        assertNotNull(json);
        assertTrue(json.contains("turn"));

        BattleContext deserialized = GSON.fromJson(json, BattleContext.class);
        assertEquals(1, deserialized.getTurn());
        assertEquals(2, deserialized.getAct());
        assertNotNull(deserialized.getPlayer());
    }

    @Test
    public void testRecommendationParsing() {
        String json = """
            {
              "suggestions": [
                {
                  "cardIndex": 0,
                  "targetIndex": 0,
                  "cardName": "Strike",
                  "priority": 1,
                  "reason": "Basic attack"
                }
              ],
              "reasoning": "Start with a basic attack",
              "companionMessage": "Let's do this!"
            }
            """;

        Recommendation rec = GSON.fromJson(json, Recommendation.class);
        assertNotNull(rec);
        assertEquals(1, rec.getSuggestions().size());
        assertEquals("Start with a basic attack", rec.getReasoning());
        assertEquals("Let's do this!", rec.getCompanionMessage());

        CardPlaySuggestion suggestion = rec.getSuggestions().get(0);
        assertEquals(0, suggestion.getCardIndex());
        assertEquals("Strike", suggestion.getCardName());
        assertEquals(1, suggestion.getPriority());
    }

    @Test
    public void testEnemyIntent() {
        EnemyIntent intent = new EnemyIntent();
        intent.setType("ATTACK");
        intent.setDamage(12);
        intent.setMultiplier(2);

        assertEquals("Attack 12 x2", intent.getDescription());
    }
}