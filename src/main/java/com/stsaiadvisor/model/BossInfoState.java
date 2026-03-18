package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * BossInfoState - Boss信息状态模型
 *
 * <p>包含当前幕的最终Boss信息
 */
public class BossInfoState {

    /** 当前幕数 (1-4) */
    private int act;

    /** 幕类型 */
    private String actType;

    /** Boss名称（如果已确定） */
    private String bossName;

    /** 可能的Boss列表 */
    private List<String> bossList;

    /** 最终Boss提示 */
    private String finalBossHint;

    /** 是否是心脏战（Act 4） */
    private boolean isHeartFight;

    public BossInfoState() {
        this.bossList = new ArrayList<>();
    }

    // ========== Getters and Setters ==========

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }

    public String getActType() { return actType; }
    public void setActType(String actType) { this.actType = actType; }

    public String getBossName() { return bossName; }
    public void setBossName(String bossName) { this.bossName = bossName; }

    public List<String> getBossList() {
        if (bossList == null) bossList = new ArrayList<>();
        return bossList;
    }
    public void setBossList(List<String> bossList) { this.bossList = bossList; }

    public String getFinalBossHint() { return finalBossHint; }
    public void setFinalBossHint(String finalBossHint) { this.finalBossHint = finalBossHint; }

    public boolean isHeartFight() { return isHeartFight; }
    public void setHeartFight(boolean heartFight) { isHeartFight = heartFight; }

    // ========== 便捷方法 ==========

    public BossInfoState addBoss(String boss) {
        getBossList().add(boss);
        return this;
    }

    /**
     * 获取Act 1可能的Boss列表
     */
    public static List<String> getAct1Bosses() {
        List<String> bosses = new ArrayList<>();
        bosses.add("六火亡魂");
        bosses.add("史莱姆老大");
        bosses.add("守护者");
        return bosses;
    }

    /**
     * 获取Act 2可能的Boss列表
     */
    public static List<String> getAct2Bosses() {
        List<String> bosses = new ArrayList<>();
        bosses.add("青铜机械兽");
        bosses.add("收藏家");
        bosses.add("自动机");
        return bosses;
    }

    /**
     * 获取Act 3可能的Boss列表
     */
    public static List<String> getAct3Bosses() {
        List<String> bosses = new ArrayList<>();
        bosses.add("觉醒者");
        bosses.add("时间吞噬者");
        bosses.add("多态老鼠");
        return bosses;
    }
}