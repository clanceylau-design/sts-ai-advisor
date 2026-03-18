package com.stsaiadvisor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * MapNodeState - 地图节点状态模型
 *
 * <p>表示地图上的一个节点，包含坐标、房间类型和连接关系
 */
public class MapNodeState {

    /** X坐标 (0-14，从左到右) */
    private int x;

    /** Y坐标 (0-14，从下到上，0=底层，14=Boss层) */
    private int y;

    /** 房间类型 */
    private String roomType;

    /** 连接的下一层节点坐标列表 */
    private List<int[]> connectedTo;

    /** 是否已被访问 */
    private boolean visited;

    public MapNodeState() {
        this.connectedTo = new ArrayList<>();
    }

    // ========== Getters and Setters ==========

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public List<int[]> getConnectedTo() {
        if (connectedTo == null) connectedTo = new ArrayList<>();
        return connectedTo;
    }
    public void setConnectedTo(List<int[]> connectedTo) { this.connectedTo = connectedTo; }

    public boolean isVisited() { return visited; }
    public void setVisited(boolean visited) { this.visited = visited; }

    /**
     * 添加连接节点
     */
    public MapNodeState addConnection(int x, int y) {
        getConnectedTo().add(new int[]{x, y});
        return this;
    }
}