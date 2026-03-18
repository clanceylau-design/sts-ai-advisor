package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * MapInfoState - 地图信息状态模型
 *
 * <p>包含完整的地图结构和玩家位置信息
 */
public class MapInfoState {

    /** 当前幕数 (1-4) */
    private int act;

    /** 当前层数 */
    private int currentFloor;

    /** 最大层数 */
    private int maxFloors;

    /** 当前节点 */
    private MapNodeState currentNode;

    /** 地图行列表（按Y坐标从低到高） */
    private List<MapRow> rows;

    /** Boss所在层数 */
    private int bossFloor;

    public MapInfoState() {
        this.rows = new ArrayList<>();
    }

    // ========== 内部类：地图行 ==========

    public static class MapRow {
        private int y;
        private List<MapNodeState> nodes;

        public MapRow() {
            this.nodes = new ArrayList<>();
        }

        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public List<MapNodeState> getNodes() {
            if (nodes == null) nodes = new ArrayList<>();
            return nodes;
        }
        public void setNodes(List<MapNodeState> nodes) { this.nodes = nodes; }

        public MapRow addNode(MapNodeState node) {
            getNodes().add(node);
            return this;
        }
    }

    // ========== Getters and Setters ==========

    public int getAct() { return act; }
    public void setAct(int act) { this.act = act; }

    public int getCurrentFloor() { return currentFloor; }
    public void setCurrentFloor(int currentFloor) { this.currentFloor = currentFloor; }

    public int getMaxFloors() { return maxFloors; }
    public void setMaxFloors(int maxFloors) { this.maxFloors = maxFloors; }

    public MapNodeState getCurrentNode() { return currentNode; }
    public void setCurrentNode(MapNodeState currentNode) { this.currentNode = currentNode; }

    public List<MapRow> getRows() {
        if (rows == null) rows = new ArrayList<>();
        return rows;
    }
    public void setRows(List<MapRow> rows) { this.rows = rows; }

    public int getBossFloor() { return bossFloor; }
    public void setBossFloor(int bossFloor) { this.bossFloor = bossFloor; }

    public MapInfoState addRow(MapRow row) {
        getRows().add(row);
        return this;
    }
}