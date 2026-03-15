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
 * </ul>
 *
 * @see GameTool
 */
public class ToolRegistry {

    private static final Gson GSON = new Gson();

    /** 工具注册表 */
    private final Map<String, GameTool> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具
     *
     * @param tool 工具实例
     * @return this（链式调用）
     */
    public ToolRegistry register(GameTool tool) {
        tools.put(tool.getId(), tool);
        System.out.println("[ToolRegistry] Registered tool: " + tool.getId());
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

    /**
     * 生成LLM可用的工具定义列表（OpenAI格式）
     *
     * <p>返回格式：
     * <pre>
     * [
     *   {
     *     "type": "function",
     *     "function": {
     *       "name": "get_player_state",
     *       "description": "获取玩家当前状态",
     *       "parameters": { ... }
     *     }
     *   },
     *   ...
     * ]
     * </pre>
     *
     * @return 工具定义JSON数组
     */
    public JsonArray generateToolDefinitions() {
        return generateToolDefinitions(null);
    }

    /**
     * 生成指定场景可用的工具定义列表
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
            functionDef.addProperty("description", tool.getDescription());
            functionDef.add("parameters", tool.getParametersSchema());

            toolDef.add("function", functionDef);
            definitions.add(toolDef);
        }

        return definitions;
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
    }
}