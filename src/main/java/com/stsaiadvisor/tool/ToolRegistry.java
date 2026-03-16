package com.stsaiadvisor.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolRegistry - 工具注册中心
 *
 * <p>职责：
 * <ul>
 *   <li>管理工具的注册和查找</li>
 *   <li>生成LLM可用的工具定义列表</li>
 *   <li>根据场景过滤可用工具</li>
 *   <li>缓存稳定信息类型工具的结果</li>
 * </ul>
 *
 * @see GameTool
 */
public class ToolRegistry {

    private static final Gson GSON = new Gson();

    /** 工具注册表 */
    private final Map<String, GameTool> tools = new ConcurrentHashMap<>();

    /** 工具结果缓存（用于 STABLE 类型的工具） */
    private final Map<String, JsonObject> toolCache = new ConcurrentHashMap<>();

    /** 缓存是否有效 */
    private boolean cacheValid = false;

    /**
     * 注册工具
     *
     * @param tool 工具实例
     * @return this（链式调用）
     */
    public ToolRegistry register(GameTool tool) {
        tools.put(tool.getId(), tool);
        System.out.println("[ToolRegistry] Registered tool: " + tool.getId() + " (" + tool.getInfoType() + ")");
        return this;
    }

    /**
     * 批量注册工具
     *
     * @param tools 工具列表
     * @return this（链式调用）
     */
    public ToolRegistry registerAll(List<GameTool> tools) {
        for (GameTool tool : tools) {
            register(tool);
        }
        return this;
    }

    /**
     * 获取工具
     *
     * @param toolId 工具ID
     * @return 工具实例，不存在返回null
     */
    public GameTool getTool(String toolId) {
        return tools.get(toolId);
    }

    /**
     * 检查工具是否存在
     *
     * @param toolId 工具ID
     * @return true如果存在
     */
    public boolean hasTool(String toolId) {
        return tools.containsKey(toolId);
    }

    /**
     * 获取所有已注册的工具
     *
     * @return 工具列表
     */
    public Collection<GameTool> getAllTools() {
        return tools.values();
    }

    /**
     * 获取指定场景可用的工具
     *
     * @param scenario 场景类型
     * @return 可用工具列表
     */
    public List<GameTool> getAvailableTools(String scenario) {
        List<GameTool> available = new ArrayList<>();
        for (GameTool tool : tools.values()) {
            if (tool.isAvailableForScenario(scenario)) {
                available.add(tool);
            }
        }
        return available;
    }

    // ============================================================
    // 缓存管理
    // ============================================================

    /**
     * 获取缓存的工具结果
     *
     * @param toolId 工具ID
     * @return 缓存的结果，不存在返回null
     */
    public JsonObject getCachedResult(String toolId) {
        if (!cacheValid) {
            return null;
        }
        return toolCache.get(toolId);
    }

    /**
     * 缓存工具结果
     *
     * @param toolId 工具ID
     * @param result 工具结果
     */
    public void cacheResult(String toolId, JsonObject result) {
        toolCache.put(toolId, result);
        cacheValid = true;
        System.out.println("[ToolRegistry] Cached result for: " + toolId);
    }

    /**
     * 检查工具是否有缓存结果
     *
     * @param toolId 工具ID
     * @return true如果有缓存
     */
    public boolean hasCachedResult(String toolId) {
        return cacheValid && toolCache.containsKey(toolId);
    }

    /**
     * 使缓存失效（战斗结束时调用）
     */
    public void invalidateCache() {
        toolCache.clear();
        cacheValid = false;
        System.out.println("[ToolRegistry] Cache invalidated");
    }

    /**
     * 缓存是否有效
     *
     * @return true如果缓存有效
     */
    public boolean isCacheValid() {
        return cacheValid;
    }

    // ============================================================
    // 工具定义生成
    // ============================================================

    /**
     * 生成LLM可用的工具定义列表（OpenAI格式）
     *
     * @return 工具定义JSON数组
     */
    public JsonArray generateToolDefinitions() {
        return generateToolDefinitions(null);
    }

    /**
     * 生成指定场景可用的工具定义列表
     *
     * <p>在 description 中附加信息类型说明，帮助 LLM 决定何时调用：
     * <ul>
     *   <li>[实时]: 每回合都可能变化，建议每次调用</li>
     *   <li>[稳定]: 战斗内很少变化，已自动缓存</li>
     *   <li>[按需]: 根据需要决定是否调用</li>
     * </ul>
     *
     * @param scenario 场景类型，null表示所有工具
     * @return 工具定义JSON数组
     */
    public JsonArray generateToolDefinitions(String scenario) {
        JsonArray definitions = new JsonArray();

        Collection<GameTool> toolsToUse = scenario != null
            ? getAvailableTools(scenario)
            : tools.values();

        for (GameTool tool : toolsToUse) {
            JsonObject toolDef = new JsonObject();
            toolDef.addProperty("type", "function");

            JsonObject functionDef = new JsonObject();
            functionDef.addProperty("name", tool.getId());

            // 在 description 中附加信息类型说明
            String enhancedDescription = enhanceDescription(tool);
            functionDef.addProperty("description", enhancedDescription);
            functionDef.add("parameters", tool.getParametersSchema());

            toolDef.add("function", functionDef);
            definitions.add(toolDef);
        }

        return definitions;
    }

    /**
     * 增强工具描述，附加信息类型说明
     */
    private String enhanceDescription(GameTool tool) {
        String baseDescription = tool.getDescription();
        GameTool.InfoType infoType = tool.getInfoType();

        String typeNote;
        switch (infoType) {
            case REALTIME:
                typeNote = "【实时信息】每回合都可能变化，需要每次调用获取最新数据。";
                break;
            case STABLE:
                typeNote = "【稳定信息】战斗内很少变化。";
                // 如果已有缓存，提示 LLM
                if (hasCachedResult(tool.getId())) {
                    typeNote += "（已缓存，可直接使用之前的结果）";
                }
                break;
            case ON_DEMAND:
            default:
                typeNote = "【按需信息】根据场景需要决定是否调用。";
                break;
        }

        return baseDescription + "\n\n" + typeNote;
    }

    /**
     * 获取工具数量
     *
     * @return 已注册的工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 清空所有注册的工具
     */
    public void clear() {
        tools.clear();
        toolCache.clear();
        cacheValid = false;
    }
}