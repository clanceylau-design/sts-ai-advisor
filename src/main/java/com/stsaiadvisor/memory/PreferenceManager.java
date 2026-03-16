package com.stsaiadvisor.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.stsaiadvisor.model.UserPreference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PreferenceManager - 用户偏好管理器
 *
 * <p>职责：
 * <ul>
 *   <li>管理用户偏好的内存存储</li>
 *   <li>异步持久化到文件</li>
 *   <li>启动时加载历史偏好</li>
 * </ul>
 *
 * <p>性能保障：
 * <ul>
 *   <li>内存优先：所有读取操作零延迟</li>
 *   <li>异步写入：不阻塞主流程</li>
 *   <li>定时flush：避免频繁I/O</li>
 * </ul>
 */
public class PreferenceManager {

    private static final String DATA_DIR = "mods/sts-ai-advisor/data/";
    private static final String PREFERENCES_FILE = DATA_DIR + "user_preferences.json";

    /** 最大偏好数量 */
    private static final int MAX_PREFERENCES = 30;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 内存中的偏好列表 */
    private final List<UserPreference> preferences;

    /** 脏标记：是否有未保存的变更 */
    private volatile boolean dirty = false;

    /** 后台调度器 */
    private ScheduledExecutorService scheduler;

    /** 单例实例 */
    private static PreferenceManager instance;

    /**
     * 获取单例实例
     */
    public static synchronized PreferenceManager getInstance() {
        if (instance == null) {
            instance = new PreferenceManager();
        }
        return instance;
    }

    private PreferenceManager() {
        this.preferences = new ArrayList<>();
        loadFromFile();
        startFlushTask();
        System.out.println("[PreferenceManager] Initialized with " + preferences.size() + " preferences");
    }

    /**
     * 保存用户偏好
     *
     * <p>立即写入内存，后台持久化
     *
     * @param content 偏好内容
     * @param context 产生该偏好的上下文
     * @return 是否成功
     */
    public boolean savePreference(String content, String context) {
        if (content == null || content.trim().isEmpty()) {
            System.err.println("[PreferenceManager] Cannot save empty preference");
            return false;
        }

        try {
            UserPreference preference = new UserPreference(content.trim(), context);
            preferences.add(preference);

            // 超过限制时移除最早的
            while (preferences.size() > MAX_PREFERENCES) {
                preferences.remove(0);
                System.out.println("[PreferenceManager] Removed oldest preference (max limit reached)");
            }

            dirty = true;
            System.out.println("[PreferenceManager] Saved preference: " + content);
            return true;
        } catch (Exception e) {
            System.err.println("[PreferenceManager] Failed to save preference: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取所有偏好
     *
     * @return 偏好列表（只读）
     */
    public List<UserPreference> getPreferences() {
        return new ArrayList<>(preferences);
    }

    /**
     * 获取偏好内容列表（用于注入LLM）
     *
     * @return 偏好内容列表
     */
    public List<String> getPreferenceContents() {
        List<String> contents = new ArrayList<>();
        for (UserPreference pref : preferences) {
            if (pref.getContent() != null && !pref.getContent().isEmpty()) {
                contents.add(pref.getContent());
            }
        }
        return contents;
    }

    /**
     * 清空所有偏好
     */
    public void clearAll() {
        preferences.clear();
        dirty = true;
        System.out.println("[PreferenceManager] All preferences cleared");
    }

    /**
     * 偏好数量
     */
    public int size() {
        return preferences.size();
    }

    // ============================================================
    // 持久化
    // ============================================================

    /**
     * 从文件加载偏好
     */
    private void loadFromFile() {
        try {
            Path path = Paths.get(PREFERENCES_FILE);
            if (!Files.exists(path)) {
                System.out.println("[PreferenceManager] No existing preferences file, starting fresh");
                return;
            }

            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            if (root != null && root.has("preferences")) {
                JsonArray prefsArray = root.getAsJsonArray("preferences");
                List<UserPreference> loaded = GSON.fromJson(prefsArray,
                    new TypeToken<List<UserPreference>>(){}.getType());

                if (loaded != null) {
                    preferences.clear();
                    preferences.addAll(loaded);
                    System.out.println("[PreferenceManager] Loaded " + preferences.size() + " preferences from file");
                }
            }
        } catch (Exception e) {
            System.err.println("[PreferenceManager] Failed to load preferences: " + e.getMessage());
            // 加载失败不影响启动，使用空列表
        }
    }

    /**
     * 保存到文件
     */
    private synchronized void flushToDisk() {
        if (!dirty) {
            return;
        }

        try {
            // 确保目录存在
            Path dirPath = Paths.get(DATA_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 构建JSON
            JsonObject root = new JsonObject();
            root.addProperty("lastUpdated", Instant.now().toString());
            root.add("preferences", GSON.toJsonTree(preferences));

            // 写入文件
            String json = GSON.toJson(root);
            Files.write(Paths.get(PREFERENCES_FILE), json.getBytes(StandardCharsets.UTF_8));

            dirty = false;
            System.out.println("[PreferenceManager] Flushed " + preferences.size() + " preferences to disk");
        } catch (Exception e) {
            System.err.println("[PreferenceManager] Failed to flush preferences: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动后台flush任务
     */
    private void startFlushTask() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PreferenceManager-Flush");
            t.setDaemon(true);
            return t;
        });

        // 每30秒检查一次，有变更才写入
        scheduler.scheduleAtFixedRate(() -> {
            try {
                flushToDisk();
            } catch (Exception e) {
                System.err.println("[PreferenceManager] Flush task error: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);

        // 注册退出钩子，确保程序退出时保存
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (dirty) {
                    flushToDisk();
                    System.out.println("[PreferenceManager] Saved preferences on shutdown");
                }
            } catch (Exception e) {
                System.err.println("[PreferenceManager] Shutdown hook error: " + e.getMessage());
            }
        }));
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        try {
            flushToDisk();
            if (scheduler != null) {
                scheduler.shutdown();
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            }
            System.out.println("[PreferenceManager] Shutdown complete");
        } catch (Exception e) {
            System.err.println("[PreferenceManager] Shutdown error: " + e.getMessage());
        }
    }
}