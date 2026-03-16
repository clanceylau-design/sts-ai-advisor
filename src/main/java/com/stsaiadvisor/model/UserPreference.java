package com.stsaiadvisor.model;

/**
 * UserPreference - 用户偏好
 *
 * <p>存储用户的策略偏好，以语义化方式记录
 *
 * <p>特点：
 * <ul>
 *   <li>content：语义化的偏好描述（自由文本）</li>
 *   <li>context：产生该偏好的上下文</li>
 *   <li>无固定schema，完全可扩展</li>
 * </ul>
 */
public class UserPreference {

    /** 偏好ID（时间戳） */
    private String id;

    /** 偏好内容（语义化描述） */
    private String content;

    /** 产生该偏好的上下文 */
    private String context;

    /** 创建时间（ISO格式） */
    private String createdAt;

    public UserPreference() {
        // 默认构造函数
    }

    public UserPreference(String content, String context) {
        this.id = "pref_" + System.currentTimeMillis();
        this.content = content;
        this.context = context;
        this.createdAt = java.time.Instant.now().toString();
    }

    // ========== Getters & Setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "UserPreference{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", context='" + context + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}