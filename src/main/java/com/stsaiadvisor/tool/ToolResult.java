package com.stsaiadvisor.tool;

import com.google.gson.JsonObject;

/**
 * ToolResult - Tool执行结果
 *
 * <p>封装工具执行的返回结果，支持成功和失败状态。
 *
 * @see GameTool
 */
public class ToolResult {

    /** 是否成功 */
    private final boolean success;

    /** 结果数据（JSON格式） */
    private final JsonObject data;

    /** 错误信息（失败时） */
    private final String error;

    /** 工具ID */
    private final String toolId;

    /** 执行耗时（毫秒） */
    private final long durationMs;

    private ToolResult(Builder builder) {
        this.success = builder.success;
        this.data = builder.data;
        this.error = builder.error;
        this.toolId = builder.toolId;
        this.durationMs = builder.durationMs;
    }

    // ========== Getters ==========

    public boolean isSuccess() { return success; }
    public JsonObject getData() { return data; }
    public String getError() { return error; }
    public String getToolId() { return toolId; }
    public long getDurationMs() { return durationMs; }

    // ========== Factory Methods ==========

    /**
     * 创建成功结果
     */
    public static ToolResult success(String toolId, JsonObject data) {
        return new Builder()
            .toolId(toolId)
            .success(true)
            .data(data)
            .build();
    }

    /**
     * 创建成功结果（带耗时）
     */
    public static ToolResult success(String toolId, JsonObject data, long durationMs) {
        return new Builder()
            .toolId(toolId)
            .success(true)
            .data(data)
            .durationMs(durationMs)
            .build();
    }

    /**
     * 创建失败结果
     */
    public static ToolResult failure(String toolId, String error) {
        return new Builder()
            .toolId(toolId)
            .success(false)
            .error(error)
            .build();
    }

    /**
     * 创建失败结果（带耗时）
     */
    public static ToolResult failure(String toolId, String error, long durationMs) {
        return new Builder()
            .toolId(toolId)
            .success(false)
            .error(error)
            .durationMs(durationMs)
            .build();
    }

    // ========== Builder ==========

    public static class Builder {
        private boolean success = true;
        private JsonObject data;
        private String error;
        private String toolId;
        private long durationMs;

        public Builder toolId(String toolId) {
            this.toolId = toolId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder data(JsonObject data) {
            this.data = data;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public ToolResult build() {
            return new ToolResult(this);
        }
    }
}