package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * PileState - 牌堆状态模型
 *
 * <p>包含抽牌堆、弃牌堆、消耗牌堆的统计信息
 */
public class PileState {

    /** 抽牌堆信息 */
    private PileInfo drawPile;

    /** 弃牌堆信息 */
    private PileInfo discardPile;

    /** 消耗牌堆信息 */
    private PileInfo exhaustPile;

    // ========== 内部类：牌堆详情 ==========

    public static class PileInfo {
        /** 卡牌数量 */
        private int count;

        /** 预览卡牌（前5张） */
        private List<CardState> preview;

        /** 按类型统计 */
        private TypeStats typeStats;

        public PileInfo() {
            this.preview = new ArrayList<>();
            this.typeStats = new TypeStats();
        }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public List<CardState> getPreview() {
            if (preview == null) preview = new ArrayList<>();
            return preview;
        }
        public void setPreview(List<CardState> preview) { this.preview = preview; }

        public TypeStats getTypeStats() {
            if (typeStats == null) typeStats = new TypeStats();
            return typeStats;
        }
        public void setTypeStats(TypeStats typeStats) { this.typeStats = typeStats; }
    }

    // ========== 内部类：类型统计 ==========

    public static class TypeStats {
        private int attack;
        private int skill;
        private int power;
        private int curse;
        private int status;

        public int getAttack() { return attack; }
        public void setAttack(int attack) { this.attack = attack; }

        public int getSkill() { return skill; }
        public void setSkill(int skill) { this.skill = skill; }

        public int getPower() { return power; }
        public void setPower(int power) { this.power = power; }

        public int getCurse() { return curse; }
        public void setCurse(int curse) { this.curse = curse; }

        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }

        /**
         * 根据卡牌类型增加计数
         */
        public void incrementByType(String type) {
            if (type == null) return;
            switch (type.toUpperCase()) {
                case "ATTACK": attack++; break;
                case "SKILL": skill++; break;
                case "POWER": power++; break;
                case "CURSE": curse++; break;
                case "STATUS": status++; break;
            }
        }
    }

    // ========== Getters and Setters ==========

    public PileInfo getDrawPile() {
        if (drawPile == null) drawPile = new PileInfo();
        return drawPile;
    }
    public void setDrawPile(PileInfo drawPile) { this.drawPile = drawPile; }

    public PileInfo getDiscardPile() {
        if (discardPile == null) discardPile = new PileInfo();
        return discardPile;
    }
    public void setDiscardPile(PileInfo discardPile) { this.discardPile = discardPile; }

    public PileInfo getExhaustPile() {
        if (exhaustPile == null) exhaustPile = new PileInfo();
        return exhaustPile;
    }
    public void setExhaustPile(PileInfo exhaustPile) { this.exhaustPile = exhaustPile; }
}