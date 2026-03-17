package com.stsaiadvisor.knowledge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Archetype加载器
 *
 * <p>职责：
 * <ul>
 *   <li>加载archetypes.json文件</li>
 *   <li>生成简洁的archetype cards用于System Prompt注入</li>
 *   <li>根据角色筛选相关archetype</li>
 * </ul>
 */
public class ArchetypeLoader {

    /** Archetype定义 */
    public static class Archetype {
        public String id;
        public String name;
        public String character;
        public String core_mechanism;
        public List<String> essential_cards;
        public List<String> synergy_cards;
        public List<String> formation_signals;
        public String risk_level;
        public String skill_path;
    }

    private final String archetypesPath;
    private List<Archetype> archetypes;
    private boolean loaded = false;

    public ArchetypeLoader(String archetypesPath) {
        this.archetypesPath = archetypesPath;
    }

    /**
     * 加载archetypes.json
     */
    public void load() {
        if (loaded) return;

        try {
            File file = new File(archetypesPath);
            if (!file.exists()) {
                System.err.println("[ArchetypeLoader] File not found: " + archetypesPath);
                return;
            }

            String content = readFile(file);
            Gson gson = new Gson();
            archetypes = gson.fromJson(content, new TypeToken<List<Archetype>>(){}.getType());

            loaded = true;
            System.out.println("[ArchetypeLoader] Loaded " + archetypes.size() + " archetypes");
        } catch (Exception e) {
            System.err.println("[ArchetypeLoader] Failed to load: " + e.getMessage());
        }
    }

    /**
     * 读取文件内容
     */
    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * 获取指定角色的archetype cards
     *
     * @param characterClass 角色类型（IRONCLAD/THE_SILENT/DEFECT/WATCHER）
     * @return archetype cards的文本描述
     */
    public String getArchetypeCards(String characterClass) {
        if (!loaded || archetypes == null) return "";

        String cnCharacter = toCNCharacter(characterClass);
        StringBuilder sb = new StringBuilder();

        for (Archetype arch : archetypes) {
            if (cnCharacter.equals(arch.character) || arch.character == null) {
                sb.append(formatArchetypeCard(arch)).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 格式化单个archetype card
     */
    private String formatArchetypeCard(Archetype arch) {
        StringBuilder sb = new StringBuilder();

        sb.append("### ").append(arch.name).append("\n");
        sb.append("- 核心机制：").append(arch.core_mechanism).append("\n");

        if (arch.essential_cards != null && !arch.essential_cards.isEmpty()) {
            sb.append("- 核心卡牌：").append(String.join("、", arch.essential_cards)).append("\n");
        }

        if (arch.formation_signals != null && !arch.formation_signals.isEmpty()) {
            sb.append("- 成型信号：").append(String.join("；", arch.formation_signals)).append("\n");
        }

        sb.append("- 风险等级：").append(arch.risk_level != null ? arch.risk_level : "中").append("\n");

        return sb.toString();
    }

    /**
     * 获取所有archetype（用于调试）
     */
    public List<Archetype> getAllArchetypes() {
        return archetypes;
    }

    /**
     * 根据卡牌名称匹配archetype
     *
     * @param cardNames 卡牌名称列表
     * @return 匹配的archetype ID列表
     */
    public List<String> matchArchetypes(List<String> cardNames) {
        if (!loaded || archetypes == null || cardNames == null) return Collections.emptyList();

        List<String> matched = new ArrayList<>();

        for (Archetype arch : archetypes) {
            if (arch.essential_cards == null) continue;

            for (String card : cardNames) {
                for (String essential : arch.essential_cards) {
                    if (card != null && card.contains(essential)) {
                        if (!matched.contains(arch.id)) {
                            matched.add(arch.id);
                        }
                        break;
                    }
                }
            }
        }

        return matched;
    }

    /**
     * 角色ID转中文名
     */
    private static String toCNCharacter(String characterClass) {
        if (characterClass == null) return "";
        switch (characterClass) {
            case "IRONCLAD": return "铁甲战士";
            case "THE_SILENT": return "静默猎人";
            case "DEFECT": return "故障机器人";
            case "WATCHER": return "观者";
            default: return characterClass;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }
}