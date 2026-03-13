package com.stsaiadvisor.config;

import java.io.*;
import java.util.Properties;

/**
 * LocalConfig - 本地开发环境配置
 *
 * <p>职责：
 * <ul>
 *   <li>加载 local.properties 配置文件</li>
 *   <li>提供游戏目录、项目目录等路径配置</li>
 * </ul>
 *
 * <p>配置文件位置：项目根目录的 local.properties
 */
public class LocalConfig {

    private static final String CONFIG_FILE = "local.properties";

    private static LocalConfig instance;

    private String gameDir;
    private String projectDir;
    private boolean loaded = false;

    private LocalConfig() {
        load();
    }

    /**
     * 获取单例实例
     */
    public static synchronized LocalConfig getInstance() {
        if (instance == null) {
            instance = new LocalConfig();
        }
        return instance;
    }

    /**
     * 加载配置文件
     */
    private void load() {
        Properties props = new Properties();

        // 尝试从项目根目录加载
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                props.load(is);
                loaded = true;
                System.out.println("[LocalConfig] Loaded from: " + file.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[LocalConfig] Failed to load: " + e.getMessage());
            }
        }

        // 读取配置
        gameDir = props.getProperty("game.dir", "D:\\SteamLibrary\\steamapps\\common\\SlayTheSpire");
        projectDir = props.getProperty("project.dir", "");
    }

    /**
     * 获取游戏安装目录
     *
     * @return 游戏目录路径
     */
    public String getGameDir() {
        return gameDir;
    }

    /**
     * 获取项目开发目录
     *
     * @return 项目目录路径
     */
    public String getProjectDir() {
        return projectDir;
    }

    /**
     * 获取 Overlay 目录路径
     *
     * @return Overlay 目录路径
     */
    public String getOverlayDir() {
        if (projectDir != null && !projectDir.isEmpty()) {
            return projectDir + "\\overlay";
        }
        return null;
    }

    /**
     * 是否已加载配置文件
     */
    public boolean isLoaded() {
        return loaded;
    }
}