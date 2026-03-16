package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.memory.PreferenceManager;

import java.util.concurrent.CompletableFuture;

/**
 * SaveUserPreferenceTool - 保存用户偏好工具
 *
 * <p>职责：
 * <ul>
 *   <li>将识别到的用户偏好持久化存储</li>
 *   <li>支持语义化的偏好描述</li>
 *   <li>自动管理偏好数量上限</li>
 * </ul>
 *
 * <p>调用时机：
 * <ul>
 *   <li>用户明确表达对某策略的喜好或厌恶</li>
 *   <li>用户纠正AI的建议并给出原因</li>
 *   <li>用户主动告知自己的游戏风格偏好</li>
 * </ul>
 *
 * <p>信息类型：ON_DEMAND（仅在识别到偏好时调用）
 */
public class SaveUserPreferenceTool implements GameTool {

    @Override
    public String getId() {
        return "save_user_preference";
    }

    @Override
    public String getDescription() {
        return "保存用户的策略偏好。当用户明确表达对某策略的喜好/厌恶，" +
               "或纠正你的建议并给出原因时，调用此工具记录偏好。" +
               "偏好应该简洁明确，例如：'偏好高伤害输出卡牌'、'不喜欢依赖运气的策略'。";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.ON_DEMAND;
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // preference 参数：偏好内容
        JsonObject preferenceParam = new JsonObject();
        preferenceParam.addProperty("type", "string");
        preferenceParam.addProperty("description",
            "语义化的偏好描述，应简洁明确。例如：'偏好攻击型卡牌'、'不喜欢消耗型卡牌'、'喜欢叠甲策略'");
        properties.add("preference", preferenceParam);

        // context 参数：产生偏好的上下文（可选）
        JsonObject contextParam = new JsonObject();
        contextParam.addProperty("type", "string");
        contextParam.addProperty("description", "产生该偏好的上下文，例如用户原话或具体场景");
        properties.add("context", contextParam);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("preference");
        schema.add("required", required);

        return schema;
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                // 解析参数
                if (!args.has("preference") || args.get("preference").isJsonNull()) {
                    System.err.println("[SaveUserPreferenceTool] Missing required parameter: preference");
                    return ToolResult.failure(getId(), "缺少必要参数: preference", System.currentTimeMillis() - start);
                }

                String preference = args.get("preference").getAsString();
                if (preference == null || preference.trim().isEmpty()) {
                    System.err.println("[SaveUserPreferenceTool] Empty preference content");
                    return ToolResult.failure(getId(), "偏好内容不能为空", System.currentTimeMillis() - start);
                }

                // 获取上下文（可选）
                String preferenceContext = "";
                if (args.has("context") && !args.get("context").isJsonNull()) {
                    preferenceContext = args.get("context").getAsString();
                }

                // 保存偏好
                PreferenceManager manager = PreferenceManager.getInstance();
                boolean success = manager.savePreference(preference.trim(), preferenceContext);

                if (success) {
                    JsonObject data = new JsonObject();
                    data.addProperty("saved", true);
                    data.addProperty("preference", preference);
                    data.addProperty("total_count", manager.size());
                    data.addProperty("message", "偏好已保存，将在后续建议中参考此偏好");

                    System.out.println("[SaveUserPreferenceTool] Saved preference: " + preference);
                    return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
                } else {
                    return ToolResult.failure(getId(), "保存偏好失败", System.currentTimeMillis() - start);
                }

            } catch (Exception e) {
                System.err.println("[SaveUserPreferenceTool] Error: " + e.getMessage());
                e.printStackTrace();
                return ToolResult.failure(getId(), "保存偏好失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}