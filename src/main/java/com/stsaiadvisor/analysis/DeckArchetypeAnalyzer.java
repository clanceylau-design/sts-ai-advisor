package com.stsaiadvisor.analysis;

import com.stsaiadvisor.model.CardState;
import com.stsaiadvisor.model.DeckArchetype;

import java.util.*;

/**
 * Analyzes deck archetype based on card composition.
 */
public class DeckArchetypeAnalyzer {

    // Ironclad archetype definitions
    private static final Map<String, ArchetypeDefinition> IRONCLAD_ARCHETYPES = new HashMap<>();

    // Silent archetype definitions
    private static final Map<String, ArchetypeDefinition> SILENT_ARCHETYPES = new HashMap<>();

    // Defect archetype definitions
    private static final Map<String, ArchetypeDefinition> DEFECT_ARCHETYPES = new HashMap<>();

    // Watcher archetype definitions
    private static final Map<String, ArchetypeDefinition> WATCHER_ARCHETYPES = new HashMap<>();

    static {
        // Ironclad archetypes
        IRONCLAD_ARCHETYPES.put("STRENGTH", new ArchetypeDefinition(
            "力量流",
            Arrays.asList("Flex", "Inflame", "Limit Break", "Spotter", "Heavy Blade", "Sword Boomerang", "Whirlwind"),
            "通过力量叠加使攻击牌伤害爆炸"
        ));
        IRONCLAD_ARCHETYPES.put("EXHAUST", new ArchetypeDefinition(
            "消耗流",
            Arrays.asList("Feel No Pain", "Sentinel", "Fiend Fire", "Corruption", "Dead Branch", "Power Through"),
            "通过消耗牌触发遗物和增益效果"
        ));
        IRONCLAD_ARCHETYPES.put("BLOCK", new ArchetypeDefinition(
            "格挡流",
            Arrays.asList("Body Slam", "Entrench", "Barricade", "Juggernaut", "Impervious"),
            "利用高格挡进行防御反击"
        ));
        IRONCLAD_ARCHETYPES.put("RAMPAGE", new ArchetypeDefinition(
            "狂暴流",
            Arrays.asList("Rampage", "Pommel Strike", "Double Tap", "Unceasing Top"),
            "快速循环牌组，多次使用核心牌"
        ));

        // Silent archetypes
        SILENT_ARCHETYPES.put("POISON", new ArchetypeDefinition(
            "毒流",
            Arrays.asList("Poison Stab", "Bane", "Catalyst", "Corpse Explosion", "Envenom", "Noxious Fumes"),
            "通过毒叠加持续伤害"
        ));
        SILENT_ARCHETYPES.put("SHIV", new ArchetypeDefinition(
            "刀刃流",
            Arrays.asList("Blade Dance", "Cloak and Dagger", "Storm of Steel", "Accuracy", "Infinite Blades"),
            "生成大量刀刃进行攻击"
        ));
        SILENT_ARCHETYPES.put("DISCARD", new ArchetypeDefinition(
            "弃牌流",
            Arrays.asList("Survivor", "Acrobatics", "Prepared", "Tactician", "Reflex", "Eternity"),
            "利用弃牌机制获得额外效果"
        ));

        // Defect archetypes
        DEFECT_ARCHETYPES.put("ORBS", new ArchetypeDefinition(
            "球流",
            Arrays.asList("Zap", "Ball Lightning", "Glacier", "Capacitor", "Loop", "Storm"),
            "利用球循环产生效果"
        ));
        DEFECT_ARCHETYPES.put("POWER", new ArchetypeDefinition(
            "能力流",
            Arrays.asList("Defragment", "Capacitor", "Biased Cog", "Amplify", "Heatsinks"),
            "大量能力牌叠加效果"
        ));
        DEFECT_ARCHETYPES.put("EXHAUST_DEFECT", new ArchetypeDefinition(
            "消耗流",
            Arrays.asList("Fusion", "Reprogram", "Storm", "Steam", "Hyperbeam"),
            "消耗牌库获得强力效果"
        ));

        // Watcher archetypes
        WATCHER_ARCHETYPES.put("STANCE", new ArchetypeDefinition(
            "姿态流",
            Arrays.asList("Vigilance", "Rushdown", "Mental Fortress", "Flurry of Blows", "Empty Fist"),
            "频繁切换姿态获得效果"
        ));
        WATCHER_ARCHETYPES.put("MANTRA", new ArchetypeDefinition(
            "真言流",
            Arrays.asList("Prostrate", "Prayer", "Deus Ex Machina", "Devotion", "Nirvana"),
            "快速进入神格姿态"
        ));
        WATCHER_ARCHETYPES.put("PRESSURE", new ArchetypeDefinition(
            "压力流",
            Arrays.asList("Pressure Points", "Meditate", "Evaluate", "Battle Hymn"),
            "利用压力点击杀敌人"
        ));
    }

    /**
     * Analyze deck to determine archetype.
     */
    public DeckArchetype analyze(List<CardState> deck, String characterClass) {
        if (deck == null || deck.isEmpty() || characterClass == null) {
            return createDefaultArchetype();
        }

        Map<String, ArchetypeDefinition> archetypes = getArchetypesForCharacter(characterClass);
        if (archetypes == null || archetypes.isEmpty()) {
            return createDefaultArchetype();
        }

        // Count matches for each archetype
        Map<String, Integer> matchCounts = new HashMap<>();
        Map<String, Integer> totalCards = new HashMap<>();

        for (Map.Entry<String, ArchetypeDefinition> entry : archetypes.entrySet()) {
            String archetypeKey = entry.getKey();
            ArchetypeDefinition def = entry.getValue();
            int matches = countMatches(deck, def.coreCards);
            matchCounts.put(archetypeKey, matches);
            totalCards.put(archetypeKey, def.coreCards.size());
        }

        // Find primary archetype
        String primaryArchetype = null;
        int maxMatches = 0;
        for (Map.Entry<String, Integer> entry : matchCounts.entrySet()) {
            if (entry.getValue() > maxMatches) {
                maxMatches = entry.getValue();
                primaryArchetype = entry.getKey();
            }
        }

        // Calculate strength
        int strength = 0;
        if (primaryArchetype != null && totalCards.get(primaryArchetype) > 0) {
            strength = (maxMatches * 100) / totalCards.get(primaryArchetype);
            // Cap at 100
            strength = Math.min(strength, 100);
        }

        // Build result
        DeckArchetype result = new DeckArchetype();
        if (primaryArchetype != null && maxMatches > 0) {
            ArchetypeDefinition primaryDef = archetypes.get(primaryArchetype);
            result.setPrimaryArchetype(primaryDef.name);
            result.setArchetypeStrength(strength);

            List<String> tags = new ArrayList<>();
            tags.add(primaryDef.name);

            // Add secondary archetypes
            List<String> secondaryArchetypes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : matchCounts.entrySet()) {
                if (!entry.getKey().equals(primaryArchetype) && entry.getValue() > 0) {
                    ArchetypeDefinition secondaryDef = archetypes.get(entry.getKey());
                    secondaryArchetypes.add(secondaryDef.name);
                    tags.add(secondaryDef.name);
                }
            }
            result.setSecondaryArchetypes(secondaryArchetypes);

            // Add playstyle tags
            if (strength > 60) {
                tags.add("成型");
            } else if (strength > 30) {
                tags.add("发展中");
            } else {
                tags.add("未成型");
            }
            result.setArchetypeTags(tags);
        } else {
            result.setPrimaryArchetype("混合");
            result.setArchetypeStrength(0);
            result.setArchetypeTags(Arrays.asList("混合", "未成型"));
        }

        return result;
    }

    private Map<String, ArchetypeDefinition> getArchetypesForCharacter(String characterClass) {
        if (characterClass == null) return null;
        switch (characterClass) {
            case "IRONCLAD": return IRONCLAD_ARCHETYPES;
            case "THE_SILENT": return SILENT_ARCHETYPES;
            case "DEFECT": return DEFECT_ARCHETYPES;
            case "WATCHER": return WATCHER_ARCHETYPES;
            default: return null;
        }
    }

    private int countMatches(List<CardState> deck, List<String> coreCards) {
        int matches = 0;
        for (CardState card : deck) {
            if (coreCards.contains(card.getName())) {
                matches++;
            }
        }
        return matches;
    }

    private DeckArchetype createDefaultArchetype() {
        DeckArchetype archetype = new DeckArchetype();
        archetype.setPrimaryArchetype("未确定");
        archetype.setArchetypeStrength(0);
        archetype.setArchetypeTags(Arrays.asList("未确定"));
        return archetype;
    }

    /**
     * Internal class for archetype definition.
     */
    private static class ArchetypeDefinition {
        String name;
        List<String> coreCards;
        String winCondition;

        ArchetypeDefinition(String name, List<String> coreCards, String winCondition) {
            this.name = name;
            this.coreCards = coreCards;
            this.winCondition = winCondition;
        }
    }
}