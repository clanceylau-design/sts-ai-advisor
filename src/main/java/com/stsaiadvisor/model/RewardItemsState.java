package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * RewardItemsState - 奖励物品状态模型
 *
 * <p>包含奖励界面中的遗物奖励和药水奖励
 */
public class RewardItemsState {

    /** 遗物奖励列表 */
    private List<RewardItem> relicRewards;

    /** 药水奖励列表 */
    private List<RewardItem> potionRewards;

    /** 金币奖励列表 */
    private List<GoldReward> goldRewards;

    /** 是否有绿钥匙 */
    private boolean hasEmeraldKey;

    /** 是否有蓝钥匙 */
    private boolean hasSapphireKey;

    /** 是否有红钥匙 */
    private boolean hasRubyKey;

    // ========== 内部类：奖励物品 ==========

    public static class RewardItem {
        private int index;
        private String id;
        private String name;
        private String description;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // ========== 内部类：金币奖励 ==========

    public static class GoldReward {
        private int index;
        private int amount;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
    }

    // ========== Getters and Setters ==========

    public List<RewardItem> getRelicRewards() {
        if (relicRewards == null) relicRewards = new ArrayList<>();
        return relicRewards;
    }
    public void setRelicRewards(List<RewardItem> relicRewards) { this.relicRewards = relicRewards; }

    public List<RewardItem> getPotionRewards() {
        if (potionRewards == null) potionRewards = new ArrayList<>();
        return potionRewards;
    }
    public void setPotionRewards(List<RewardItem> potionRewards) { this.potionRewards = potionRewards; }

    public List<GoldReward> getGoldRewards() {
        if (goldRewards == null) goldRewards = new ArrayList<>();
        return goldRewards;
    }
    public void setGoldRewards(List<GoldReward> goldRewards) { this.goldRewards = goldRewards; }

    public boolean isHasEmeraldKey() { return hasEmeraldKey; }
    public void setHasEmeraldKey(boolean hasEmeraldKey) { this.hasEmeraldKey = hasEmeraldKey; }

    public boolean isHasSapphireKey() { return hasSapphireKey; }
    public void setHasSapphireKey(boolean hasSapphireKey) { this.hasSapphireKey = hasSapphireKey; }

    public boolean isHasRubyKey() { return hasRubyKey; }
    public void setHasRubyKey(boolean hasRubyKey) { this.hasRubyKey = hasRubyKey; }

    // ========== 便捷方法 ==========

    public RewardItemsState addRelicReward(RewardItem item) {
        getRelicRewards().add(item);
        return this;
    }

    public RewardItemsState addPotionReward(RewardItem item) {
        getPotionRewards().add(item);
        return this;
    }

    public RewardItemsState addGoldReward(GoldReward gold) {
        getGoldRewards().add(gold);
        return this;
    }

    /**
     * 检查是否有任何奖励
     */
    public boolean hasAnyRewards() {
        return !getRelicRewards().isEmpty()
            || !getPotionRewards().isEmpty()
            || !getGoldRewards().isEmpty();
    }
}