package com.stsaiadvisor.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stsaiadvisor.context.GameContext;
import com.stsaiadvisor.model.MapInfoState;
import com.stsaiadvisor.model.MapNodeState;

import java.util.concurrent.CompletableFuture;

/**
 * GetMapInfoTool - 获取地图信息工具
 *
 * <p>获取当前地图结构和玩家位置信息
 */
public class GetMapInfoTool implements GameTool {

    @Override
    public String getId() {
        return "get_map_info";
    }

    @Override
    public String getDescription() {
        return "获取当前地图结构、玩家位置和房间连接关系，用于规划路线";
    }

    @Override
    public InfoType getInfoType() {
        return InfoType.STABLE; // 地图在每幕内不变
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
    public boolean isAvailableForScenario(String scenario) {
        // 所有场景可用
        return true;
    }

    @Override
    public CompletableFuture<ToolResult> execute(JsonObject args, GameContext context) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            System.out.println("[GetMapInfoTool] Executing...");

            try {
                MapInfoState mapInfo = context.getMapInfo();

                if (mapInfo == null) {
                    System.err.println("[GetMapInfoTool] Error: Cannot get map info");
                    return ToolResult.failure(getId(), "无法获取地图信息，可能不在游戏中", System.currentTimeMillis() - start);
                }

                JsonObject data = new JsonObject();

                // 基础信息
                data.addProperty("act", mapInfo.getAct());
                data.addProperty("current_floor", mapInfo.getCurrentFloor());
                data.addProperty("max_floors", mapInfo.getMaxFloors());
                data.addProperty("boss_floor", mapInfo.getBossFloor());

                // 当前节点
                MapNodeState currentNode = mapInfo.getCurrentNode();
                if (currentNode != null) {
                    JsonObject currentObj = new JsonObject();
                    currentObj.addProperty("x", currentNode.getX());
                    currentObj.addProperty("y", currentNode.getY());
                    currentObj.addProperty("room_type", currentNode.getRoomType() != null ? currentNode.getRoomType() : "Unknown");
                    currentObj.addProperty("visited", currentNode.isVisited());
                    data.add("current_node", currentObj);
                }

                // 地图结构
                JsonArray rowsArray = new JsonArray();
                for (MapInfoState.MapRow row : mapInfo.getRows()) {
                    JsonObject rowObj = new JsonObject();
                    rowObj.addProperty("y", row.getY());

                    JsonArray nodesArray = new JsonArray();
                    for (MapNodeState node : row.getNodes()) {
                        JsonObject nodeObj = new JsonObject();
                        nodeObj.addProperty("x", node.getX());
                        nodeObj.addProperty("y", node.getY());
                        nodeObj.addProperty("room_type", node.getRoomType() != null ? node.getRoomType() : "Empty");
                        nodeObj.addProperty("visited", node.isVisited());

                        // 连接关系
                        JsonArray connections = new JsonArray();
                        for (int[] conn : node.getConnectedTo()) {
                            JsonObject connObj = new JsonObject();
                            connObj.addProperty("x", conn[0]);
                            connObj.addProperty("y", conn[1]);
                            connections.add(connObj);
                        }
                        nodeObj.add("connected_to", connections);

                        nodesArray.add(nodeObj);
                    }
                    rowObj.add("nodes", nodesArray);
                    rowsArray.add(rowObj);
                }

                JsonObject mapObj = new JsonObject();
                mapObj.add("rows", rowsArray);
                data.add("map", mapObj);

                System.out.println("[GetMapInfoTool] Success: Act=" + mapInfo.getAct()
                    + " Floor=" + mapInfo.getCurrentFloor()
                    + " Rows=" + mapInfo.getRows().size()
                    + " (" + (System.currentTimeMillis() - start) + "ms)");

                return ToolResult.success(getId(), data, System.currentTimeMillis() - start);
            } catch (Exception e) {
                System.err.println("[GetMapInfoTool] Error: " + e.getMessage());
                return ToolResult.failure(getId(), "获取地图信息失败: " + e.getMessage(), System.currentTimeMillis() - start);
            }
        });
    }
}