package com.stsaiadvisor.knowledge;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill管理器
 *
 * <p>职责：
 * <ul>
 *   <li>加载skills目录下的所有.md文件</li>
 *   <li>解析元数据用于筛选</li>
 *   <li>按需读取完整内容</li>
 * </ul>
 *
 * <p>Skill文件格式：
 * <pre>
 * # 技能名称
 *
 * ## 元数据
 * - 角色: XXX
 * - 核心卡牌: XXX
 * - 适用敌人: XXX
 * - 类型: XXX
 *
 * ## 核心机制
 * ...
 *
 * ## 关键战术
 * ...
 * </pre>
 */
public class SkillManager {

    /** Skill元数据 */
    public static class SkillMeta {
        String id;           // 文件名(不含扩展名)
        String name;         // 技能名称
        String character;    // 适用角色
        String type;         // 类型: archetype/enemy/synergy
        String scenario;     // 场景: battle/reward/shop
        List<String> coreCards;   // 核心卡牌
        List<String> enemies;     // 适用敌人
        String filePath;     // 文件路径

        public boolean matchesCharacter(String charClass) {
            if (character == null || charClass == null) return true;
            String cnName = SkillManager.toCNCharacter(charClass);
            return character.contains(cnName) || character.contains("通用");
        }

        public boolean matchesEnemy(String enemyName) {
            if (enemies == null || enemies.isEmpty()) return false;
            for (String e : enemies) {
                if (enemyName != null && enemyName.contains(e)) return true;
            }
            return false;
        }

        public boolean matchesCard(String cardName) {
            if (coreCards == null || cardName == null) return false;
            for (String c : coreCards) {
                if (cardName.contains(c) || c.contains(cardName)) return true;
            }
            return false;
        }

        public boolean matchesScenario(String scenarioType) {
            if (scenario == null) return true;
            return scenario.contains(scenarioType);
        }
    }

    private final String skillsPath;
    private final Map<String, SkillMeta> skillsMeta = new HashMap<>();
    private boolean loaded = false;

    public SkillManager(String skillsPath) {
        this.skillsPath = skillsPath;
    }

    /**
     * 加载所有skill的元数据（支持子目录）
     */
    public void loadMetadata() {
        if (loaded) return;

        File skillsDir = new File(skillsPath);
        if (!skillsDir.exists() || !skillsDir.isDirectory()) {
            System.err.println("[SkillManager] Skills directory not found: " + skillsPath);
            return;
        }

        // 递归扫描子目录
        loadSkillsFromDirectory(skillsDir);

        loaded = true;
        System.out.println("[SkillManager] Loaded " + skillsMeta.size() + " skills");
    }

    /**
     * 递归加载目录下的skill文件
     */
    private void loadSkillsFromDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归处理子目录
                loadSkillsFromDirectory(file);
            } else if (file.getName().endsWith(".md")) {
                try {
                    SkillMeta meta = parseMetadata(file);
                    if (meta != null) {
                        // 从父目录名推断场景类型
                        String parentDir = file.getParentFile().getName();
                        if ("battle".equals(parentDir) || "reward".equals(parentDir) || "shop".equals(parentDir)) {
                            meta.scenario = parentDir;
                        }

                        skillsMeta.put(meta.id, meta);
                        System.out.println("[SkillManager] Loaded skill: " + meta.id + " (scenario: " + meta.scenario + ")");
                    }
                } catch (Exception e) {
                    System.err.println("[SkillManager] Failed to parse " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * 解析skill文件的元数据
     */
    private SkillMeta parseMetadata(File file) throws IOException {
        SkillMeta meta = new SkillMeta();
        meta.id = file.getName().replace(".md", "");
        meta.filePath = file.getAbsolutePath();
        meta.coreCards = new ArrayList<>();
        meta.enemies = new ArrayList<>();

        // 只读取前50行用于解析元数据
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            int lineCount = 0;
            boolean inMetadata = false;

            while ((line = reader.readLine()) != null && lineCount < 50) {
                lineCount++;
                line = line.trim();

                // 提取标题作为技能名称
                if (line.startsWith("# ") && meta.name == null) {
                    meta.name = line.substring(2).trim();
                    continue;
                }

                // 进入元数据区域
                if (line.equals("## 元数据")) {
                    inMetadata = true;
                    continue;
                }

                // 离开元数据区域
                if (line.startsWith("## ") && !line.equals("## 元数据")) {
                    inMetadata = false;
                    continue;
                }

                // 解析元数据行
                if (inMetadata && line.startsWith("- ")) {
                    parseMetaLine(line, meta);
                }
            }
        }

        return meta.name != null ? meta : null;
    }

    /**
     * 解析单行元数据
     */
    private void parseMetaLine(String line, SkillMeta meta) {
        line = line.substring(2).trim(); // 移除 "- "

        if (line.startsWith("角色:")) {
            meta.character = line.substring("角色:".length()).trim();
        } else if (line.startsWith("类型:")) {
            meta.type = line.substring("类型:".length()).trim();
        } else if (line.startsWith("核心卡牌:")) {
            String cards = line.substring("核心卡牌:".length()).trim();
            meta.coreCards = parseList(cards);
        } else if (line.startsWith("适用敌人:")) {
            String enemies = line.substring("适用敌人:".length()).trim();
            meta.enemies = parseList(enemies);
        }
    }

    /**
     * 解析逗号分隔的列表
     */
    private List<String> parseList(String str) {
        List<String> list = new ArrayList<>();
        if (str == null || str.isEmpty()) return list;

        String[] parts = str.split("[,，]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * 读取skill的完整内容
     */
    public String getSkillContent(String skillId) {
        SkillMeta meta = skillsMeta.get(skillId);
        if (meta == null) return null;

        try {
            return readFile(meta.filePath);
        } catch (IOException e) {
            System.err.println("[SkillManager] Failed to read " + skillId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 读取文件内容
     */
    private String readFile(String path) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * 筛选相关的skill
     *
     * @param characterClass 角色类型
     * @param cardNames 手牌/牌组中的卡牌名称
     * @param enemyNames 敌人名称
     * @return 相关skill的ID列表
     */
    public List<String> selectRelevantSkills(String characterClass,
                                               List<String> cardNames,
                                               List<String> enemyNames) {
        return selectRelevantSkills(characterClass, cardNames, enemyNames, null);
    }

    /**
     * 筛选相关的skill（支持场景过滤）
     *
     * @param characterClass 角色类型
     * @param cardNames 手牌/牌组中的卡牌名称
     * @param enemyNames 敌人名称
     * @param scenario 场景类型（battle/reward/shop），为null时不过滤
     * @return 相关skill的ID列表
     */
    public List<String> selectRelevantSkills(String characterClass,
                                               List<String> cardNames,
                                               List<String> enemyNames,
                                               String scenario) {
        List<String> relevant = new ArrayList<>();

        for (Map.Entry<String, SkillMeta> entry : skillsMeta.entrySet()) {
            SkillMeta meta = entry.getValue();

            // 场景过滤
            if (scenario != null && !meta.matchesScenario(scenario)) {
                continue;
            }

            boolean match = false;

            // 检查角色匹配
            if (meta.matchesCharacter(characterClass)) {
                match = true;
            }

            // 检查卡牌匹配
            if (cardNames != null) {
                for (String card : cardNames) {
                    if (meta.matchesCard(card)) {
                        match = true;
                        break;
                    }
                }
            }

            // 检查敌人匹配
            if (enemyNames != null) {
                for (String enemy : enemyNames) {
                    if (meta.matchesEnemy(enemy)) {
                        match = true;
                        break;
                    }
                }
            }

            if (match) {
                relevant.add(meta.id);
            }
        }

        return relevant;
    }

    /**
     * 获取所有skill元数据
     */
    public Collection<SkillMeta> getAllMetas() {
        return skillsMeta.values();
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