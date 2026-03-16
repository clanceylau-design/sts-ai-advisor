package com.stsaiadvisor.tool;

import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;

import java.util.concurrent.CompletableFuture;

/**
 * GameTool - 游戏工具接口
 *
 * <p>定义统一的工具接口，LLM可以通过调用这些工具获取游戏状态信息。
 *
 * <p>设计参考OpenAI Function Calling规范：
 * <ul>
 *   <li>getId(): 工具唯一标识</li>
 *   <li>getDescription(): 工具描述（LLM理解用）</li>
 *   <li>getParametersSchema(): 参数Schema（JSON Schema格式）</li>
 *   <li>execute(): 执行工具</li>
 * </ul>
 *
 * @see ToolResult
 * @see GameContext
 */
public interface GameTool {

    /**
     * 工具信息类型
     */
    enum InfoType {
        /** 实时信息：每回合都可能变化，需要每次调用（手牌、血量、敌人） */
        REALTIME,
        /** 稳定信息：战斗内很少变化，可缓存（牌组、遗物） */
        STABLE,
        /** 按需信息：根据场景决定是否需要（药水、事件选项） */
        ON_DEMAND
    }

    /**
     * 获取工具ID
     *
     * <p>工具ID用于LLM调用时指定要使用的工具。
     * 建议使用snake_case命名，如：get_player_state
     *
     * @return 工具ID
     */
    String getId();

    /**
     * 获取工具描述
     *
     * <p>描述应该清晰说明工具的作用，LLM根据此描述决定是否调用。
     *
     * @return 工具描述
     */
    String getDescription();

    /**
     * 获取参数Schema
     *
     * <p>返回JSON Schema格式的参数定义。
     * 如果工具不需要参数，返回空JsonObject。
     *
     * <p>示例：
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "target_index": {
     *       "type": "integer",
     *       "description": "目标敌人索引（0开始）"
     *     }
     *   },
     *   "required": []
     * }
     * </pre>
     *
     * @return 参数Schema
     */
    JsonObject getParametersSchema();

    /**
     * 获取信息类型
     *
     * <p>用于确定工具结果是否需要缓存：
     * <ul>
     *   <li>REALTIME: 每次都执行，不缓存</li>
     *   <li>STABLE: 首次执行后缓存，战斗结束时失效</li>
     *   <li>ON_DEMAND: 不缓存，LLM自主决定</li>
     * </ul>
     *
     * @return 信息类型
     */
    default InfoType getInfoType() {
        return InfoType.ON_DEMAND;
    }

    /**
     * 执行工具
     *
     * <p>工具执行是异步的，支持耗时操作。
     *
     * @param args 工具参数（JSON对象）
     * @param context 游戏上下文，提供访问游戏状态的能力
     * @return CompletableFuture包含执行结果
     */
    CompletableFuture<ToolResult> execute(JsonObject args, GameContext context);

    /**
     * 判断工具在当前场景是否可用
     *
     * <p>某些工具只在特定场景有效（如get_card_rewards只在奖励场景）
     *
     * @param scenario 场景类型：battle, reward, shop, event
     * @return true如果工具可用
     */
    default boolean isAvailableForScenario(String scenario) {
        return true; // 默认所有场景可用
    }
}