package com.stsaiadvisor.knowledge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.stsaiadvisor.model.EnemyState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge base loader and manager.
 * Loads tactics knowledge from JSON files.
 */
public class KnowledgeBase {

    private static KnowledgeBase instance;

    private final Map<String, JsonObject> characterKnowledge = new HashMap<>();
    private final Map<String, JsonObject> enemyKnowledge = new HashMap<>();
    private final Gson gson = new Gson();
    private boolean loaded = false;

    private KnowledgeBase() {}

    public static synchronized KnowledgeBase getInstance() {
        if (instance == null) {
            instance = new KnowledgeBase();
        }
        return instance;
    }

    /**
     * Load knowledge base from files.
     * @param knowledgePath Path to knowledge directory
     */
    public void load(String knowledgePath) {
        if (loaded) return;

        try {
            // Load character knowledge
            loadCharacterKnowledge(knowledgePath + "/characters");

            // Load enemy knowledge
            loadEnemyKnowledge(knowledgePath + "/enemies");

            loaded = true;
            System.out.println("[KnowledgeBase] Loaded knowledge base from: " + knowledgePath);
        } catch (Exception e) {
            System.err.println("[KnowledgeBase] Failed to load knowledge base: " + e.getMessage());
            // Use default knowledge
            loadDefaultKnowledge();
        }
    }

    private void loadCharacterKnowledge(String path) {
        String[] characters = {"ironclad.json", "silent.json", "defect.json", "watcher.json"};
        for (String filename : characters) {
            try {
                File file = new File(path, filename);
                if (file.exists()) {
                    String content = readFile(file);
                    JsonObject json = gson.fromJson(content, JsonObject.class);
                    String charKey = filename.replace(".json", "");
                    characterKnowledge.put(charKey, json);
                    System.out.println("[KnowledgeBase] Loaded character knowledge: " + charKey);
                }
            } catch (Exception e) {
                System.err.println("[KnowledgeBase] Failed to load " + filename + ": " + e.getMessage());
            }
        }
    }

    private void loadEnemyKnowledge(String path) {
        String[] files = {"common.json", "elites.json", "bosses.json"};
        for (String filename : files) {
            try {
                File file = new File(path, filename);
                if (file.exists()) {
                    String content = readFile(file);
                    JsonObject json = gson.fromJson(content, JsonObject.class);
                    String enemyType = filename.replace(".json", "");
                    enemyKnowledge.put(enemyType, json);
                    System.out.println("[KnowledgeBase] Loaded enemy knowledge: " + enemyType);
                }
            } catch (Exception e) {
                System.err.println("[KnowledgeBase] Failed to load " + filename + ": " + e.getMessage());
            }
        }
    }

    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private void loadDefaultKnowledge() {
        // Ironclad default knowledge
        JsonObject ironclad = new JsonObject();
        ironclad.add("archetypes", createDefaultIroncladArchetypes());
        characterKnowledge.put("ironclad", ironclad);

        // Silent default knowledge
        JsonObject silent = new JsonObject();
        silent.add("archetypes", createDefaultSilentArchetypes());
        characterKnowledge.put("silent", silent);

        // Common enemy default knowledge
        JsonObject commonEnemies = createDefaultEnemyKnowledge();
        enemyKnowledge.put("common", commonEnemies);

        loaded = true;
        System.out.println("[KnowledgeBase] Loaded default knowledge");
    }

    private JsonObject createDefaultIroncladArchetypes() {
        JsonObject archetypes = new JsonObject();

        JsonObject strength = new JsonObject();
        strength.addProperty("name", "力量流");
        strength.addProperty("win_condition", "通过力量叠加使每张攻击牌伤害爆炸");
        JsonArray strengthCards = new JsonArray();
        strengthCards.add("Flex"); strengthCards.add("Inflame"); strengthCards.add("Limit Break");
        strength.add("core_cards", strengthCards);
        archetypes.add("strength", strength);

        JsonObject exhaust = new JsonObject();
        exhaust.addProperty("name", "消耗流");
        exhaust.addProperty("win_condition", "通过消耗牌触发遗物和增益效果");
        JsonArray exhaustCards = new JsonArray();
        exhaustCards.add("Feel No Pain"); exhaustCards.add("Corruption"); exhaustCards.add("Fiend Fire");
        exhaust.add("core_cards", exhaustCards);
        archetypes.add("exhaust", exhaust);

        return archetypes;
    }

    private JsonObject createDefaultSilentArchetypes() {
        JsonObject archetypes = new JsonObject();

        JsonObject poison = new JsonObject();
        poison.addProperty("name", "毒流");
        poison.addProperty("win_condition", "通过毒叠加持续伤害");
        JsonArray poisonCards = new JsonArray();
        poisonCards.add("Poison Stab"); poisonCards.add("Catalyst"); poisonCards.add("Noxious Fumes");
        poison.add("core_cards", poisonCards);
        archetypes.add("poison", poison);

        JsonObject shiv = new JsonObject();
        shiv.addProperty("name", "刀刃流");
        shiv.addProperty("win_condition", "生成大量刀刃进行攻击");
        JsonArray shivCards = new JsonArray();
        shivCards.add("Blade Dance"); shivCards.add("Accuracy"); shivCards.add("Storm of Steel");
        shiv.add("core_cards", shivCards);
        archetypes.add("shiv", shiv);

        return archetypes;
    }

    private JsonObject createDefaultEnemyKnowledge() {
        JsonObject enemies = new JsonObject();

        JsonObject slime = new JsonObject();
        slime.addProperty("name", "史莱姆");
        slime.addProperty("strategy", "优先击杀小史莱姆，避免分裂后数量过多");
        enemies.add("Slime", slime);

        JsonObject cultist = new JsonObject();
        cultist.addProperty("name", "邪教徒");
        cultist.addProperty("strategy", "第一回合会叠加仪式力量，尽快击杀");
        enemies.add("Cultist", cultist);

        return enemies;
    }

    /**
     * Get relevant tactics based on character, archetype, and enemies.
     */
    public String getRelevantTactics(String characterClass, String archetype, List<EnemyState> enemies) {
        StringBuilder tactics = new StringBuilder();

        // Get character tactics
        String charKey = characterClass != null ? characterClass.toLowerCase() : "ironclad";
        JsonObject charKnowledge = characterKnowledge.get(charKey);
        if (charKnowledge != null && charKnowledge.has("archetypes")) {
            JsonObject archetypes = charKnowledge.getAsJsonObject("archetypes");
            for (Map.Entry<String, JsonElement> entry : archetypes.entrySet()) {
                JsonObject arch = entry.getValue().getAsJsonObject();
                tactics.append("- ").append(arch.get("name").getAsString());
                tactics.append(": ").append(arch.get("win_condition").getAsString()).append("\n");
            }
        }

        // Get enemy-specific tactics
        if (enemies != null) {
            for (EnemyState enemy : enemies) {
                String enemyName = enemy.getName();
                for (JsonObject enemyKnowledge : this.enemyKnowledge.values()) {
                    if (enemyKnowledge.has(enemyName)) {
                        JsonObject enemyInfo = enemyKnowledge.getAsJsonObject(enemyName);
                        tactics.append("- 对").append(enemyName).append(": ");
                        tactics.append(enemyInfo.get("strategy").getAsString()).append("\n");
                        break;
                    }
                }
            }
        }

        return tactics.toString();
    }

    public boolean isLoaded() {
        return loaded;
    }
}