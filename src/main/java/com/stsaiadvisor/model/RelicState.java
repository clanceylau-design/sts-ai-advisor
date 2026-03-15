package com.stsaiadvisor.model;

/**
 * RelicState - 遗物状态模型
 *
 * <p>存储遗物的名称和效果描述
 */
public class RelicState {

    /** 遗物ID */
    private String id;

    /** 遗物名称 */
    private String name;

    /** 效果描述 */
    private String description;

    /** 计数器值（如：针线堆叠数） */
    private int counter;

    // ========== Getters and Setters ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    /**
     * 获取完整的遗物信息（名称 + 描述）
     */
    public String getFullInfo() {
        if (description != null && !description.isEmpty()) {
            return name + "：" + description;
        }
        return name;
    }
}