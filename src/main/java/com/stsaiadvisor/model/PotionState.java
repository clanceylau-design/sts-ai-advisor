package com.stsaiadvisor.model;

/**
 * PotionState - 药水状态模型
 *
 * <p>存储药水的名称和效果描述
 */
public class PotionState {

    /** 药水ID */
    private String id;

    /** 药水名称 */
    private String name;

    /** 效果描述 */
    private String description;

    /** 药水槽位索引（0-2，对应三个药水槽） */
    private int slot;

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

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    /**
     * 获取完整的药水信息（名称 + 描述）
     */
    public String getFullInfo() {
        if (description != null && !description.isEmpty()) {
            return name + "：" + description;
        }
        return name;
    }
}