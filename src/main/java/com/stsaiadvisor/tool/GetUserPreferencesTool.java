package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.memory.PreferenceManager;
import com.stsaiadvisor.model.UserPreference;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GetUserPreferencesTool - 获取用户偏好工具
 *
 * <p>职责：
 * <ul>
 *   <li>获取历史积累的用户策略偏好</li>
 *   <li>返回语义化的偏好描述列表</li>
 *   <li>为LLM提供个性化建议依据</li>
 * </ul>
 *
 * <p>信息类型：STABLE（偏好跨会话持久化，但每次请求时可能不同）
 */
public class GetUserPreferencesTool implements GameTool {

    @Override
    public String getId() {
        return "get_user_preferences";
    }

    @Override
    public String getDescription() {
        return "获取用户的历史策略偏好。这些偏好来自用户在过往游戏中的反馈，" +
               "可以帮助你提供更符合玩家风格的建议。偏好是语义化的自由文本描述。";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.STABLE;
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonArray());
        return schema;
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();

            try {
                PreferenceManager manager = PreferenceManager.getInstance();
                List<UserPreference> preferences = manager.getPreferences();

                JsonObject data = new JsonObject();

                if (preferences.isEmpty()) {
                    data.addProperty("count", 0);
                    data.addProperty("message", "暂无用户偏好记录");
                    System.out.println("[GetUserPreferencesTool] No preferences found");
                    return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
                }

                // 构建偏好列表
                JsonArray prefsArray = new JsonArray();
                for (UserPreference pref : preferences) {
                    JsonObject prefObj = new JsonObject();
                    prefObj.addProperty("content", pref.getContent());
                    if (pref.getContext() != null && !pref.getContext().isEmpty()) {
                        prefObj.addProperty("context", pref.getContext());
                    }
                    prefsArray.add(prefObj);
                }

                data.addProperty("count", preferences.size());
                data.add("preferences", prefsArray);
                data.addProperty("message", "以下是用户积累的策略偏好，请在提供建议时参考这些偏好");

                System.out.println("[GetUserPreferencesTool] Retrieved " + preferences.size() + " preferences");
                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);

            } catch (Exception e) {
                System.err.println("[GetUserPreferencesTool] Error: " + e.getMessage());
                e.printStackTrace();
                return ToolResult.failure(getId(), "获取用户偏好失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}