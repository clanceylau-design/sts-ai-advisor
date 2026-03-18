package com.stsaiadvisor.capture;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.stsaiadvisor.model.BossInfoState;
import com.stsaiadvisor.model.MapInfoState;
import com.stsaiadvisor.model.MapNodeState;

import java.util.ArrayList;
import java.util.List;

/**
 * MapStateCapture - 地图状态捕获器
 *
 * <p>职责：
 * <ul>
 *   <li>捕获当前地图结构</li>
 *   <li>捕获玩家当前位置</li>
 *   <li>捕获Boss信息</li>
 * </ul>
 */
public class MapStateCapture {

    /**
     * 捕获地图信息
     *
     * @return MapInfoState 地图状态，如果不在游戏中返回null
     */
    public MapInfoState captureMapInfo() {
        try {
            if (AbstractDungeon.player == null) {
                System.out.println("[MapCapture] captureMapInfo: Player is null, not in game");
                return null;
            }

            MapInfoState state = new MapInfoState();

            // 基础信息
            state.setAct(AbstractDungeon.actNum);
            state.setCurrentFloor(AbstractDungeon.floorNum);
            state.setMaxFloors(15); // 每幕最多15层
            state.setBossFloor(15);

            // 当前节点
            MapRoomNode currentNode = AbstractDungeon.getCurrMapNode();
            if (currentNode != null) {
                state.setCurrentNode(convertNode(currentNode));
            }

            // 地图结构
            if (AbstractDungeon.map != null) {
                // map 是 List<List<MapRoomNode>>，按Y坐标从低到高排列
                List<MapRoomNode> lastRow = null;
                for (int y = 0; y < AbstractDungeon.map.size(); y++) {
                    List<MapRoomNode> row = AbstractDungeon.map.get(y);
                    if (row == null) continue;

                    MapInfoState.MapRow mapRow = new MapInfoState.MapRow();
                    mapRow.setY(y);

                    for (int x = 0; x < row.size(); x++) {
                        MapRoomNode node = row.get(x);
                        if (node == null) continue;

                        MapNodeState nodeState = convertNode(node);
                        nodeState.setX(x);
                        nodeState.setY(y);

                        // 添加连接关系
                        if (node.getEdges() != null) {
                            for (MapEdge edge : node.getEdges()) {
                                if (edge != null) {
                                    nodeState.addConnection(edge.dstX, edge.dstY);
                                }
                            }
                        }

                        mapRow.addNode(nodeState);
                    }

                    state.addRow(mapRow);
                    lastRow = row;
                }
            }

            // 调试日志
            debugPrintMapInfo(state);

            return state;
        } catch (Exception e) {
            System.err.println("[MapCapture] Error capturing map: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 调试输出地图信息
     */
    private void debugPrintMapInfo(MapInfoState state) {
        System.out.println("========== MapInfo Debug ==========");
        System.out.println("Act: " + state.getAct() + ", Floor: " + state.getCurrentFloor() + "/" + state.getMaxFloors());

        if (state.getCurrentNode() != null) {
            MapNodeState node = state.getCurrentNode();
            System.out.println("Current Node: (" + node.getX() + "," + node.getY() + ") " + node.getRoomType());
        }

        System.out.println("Map Rows: " + state.getRows().size());

        // 输出每层的房间类型统计
        int[] roomCounts = new int[8]; // Monster, Elite, Shop, Treasure, Rest, Event, Boss, Other
        for (MapInfoState.MapRow row : state.getRows()) {
            for (MapNodeState node : row.getNodes()) {
                String type = node.getRoomType();
                if (type == null) continue;
                switch (type) {
                    case "Monster": roomCounts[0]++; break;
                    case "Elite": roomCounts[1]++; break;
                    case "Shop": roomCounts[2]++; break;
                    case "Treasure": roomCounts[3]++; break;
                    case "Rest": roomCounts[4]++; break;
                    case "Event": roomCounts[5]++; break;
                    case "Boss": roomCounts[6]++; break;
                    default: roomCounts[7]++; break;
                }
            }
        }
        System.out.println("Room counts: Monster=" + roomCounts[0] + " Elite=" + roomCounts[1]
            + " Shop=" + roomCounts[2] + " Treasure=" + roomCounts[3]
            + " Rest=" + roomCounts[4] + " Event=" + roomCounts[5]
            + " Boss=" + roomCounts[6] + " Other=" + roomCounts[7]);
        System.out.println("====================================");
    }

    /**
     * 捕获Boss信息
     *
     * @return BossInfoState Boss状态
     */
    public BossInfoState captureBossInfo() {
        try {
            if (AbstractDungeon.player == null) {
                System.out.println("[MapCapture] captureBossInfo: Player is null, not in game");
                return null;
            }

            BossInfoState state = new BossInfoState();
            int act = AbstractDungeon.actNum;

            state.setAct(act);
            state.setActType("act_" + act);

            // 根据 Act 设置可能的 Boss 列表
            switch (act) {
                case 1:
                    state.setBossList(BossInfoState.getAct1Bosses());
                    state.setFinalBossHint("第1幕Boss将从列表中随机选择");
                    break;
                case 2:
                    state.setBossList(BossInfoState.getAct2Bosses());
                    state.setFinalBossHint("第2幕Boss将从列表中随机选择");
                    break;
                case 3:
                    state.setBossList(BossInfoState.getAct3Bosses());
                    // 尝试获取已确定的Boss
                    if (AbstractDungeon.bossKey != null && !AbstractDungeon.bossKey.isEmpty()) {
                        String bossName = getBossNameByKey(AbstractDungeon.bossKey);
                        state.setBossName(bossName);
                        state.setFinalBossHint("第3幕Boss已确定: " + bossName);
                    } else {
                        state.setFinalBossHint("第3幕Boss将从列表中随机选择");
                    }
                    break;
                case 4:
                    state.setBossName("腐化之心");
                    state.setHeartFight(true);
                    state.setFinalBossHint("第4幕将面对腐化之心");
                    break;
                default:
                    state.setFinalBossHint("未知幕数");
            }

            // 调试日志
            debugPrintBossInfo(state);

            return state;
        } catch (Exception e) {
            System.err.println("[MapCapture] Error capturing boss info: " + e.getMessage());
            return null;
        }
    }

    /**
     * 调试输出Boss信息
     */
    private void debugPrintBossInfo(BossInfoState state) {
        System.out.println("========== BossInfo Debug ==========");
        System.out.println("Act: " + state.getAct() + " (" + state.getActType() + ")");
        System.out.println("Boss Name: " + (state.getBossName() != null ? state.getBossName() : "未确定"));
        System.out.println("Boss List: " + state.getBossList());
        System.out.println("Is Heart Fight: " + state.isHeartFight());
        System.out.println("Hint: " + state.getFinalBossHint());
        System.out.println("=====================================");
    }

    /**
     * 转换地图节点
     */
    private MapNodeState convertNode(MapRoomNode node) {
        MapNodeState state = new MapNodeState();

        if (node == null) {
            return state;
        }

        state.setX(node.x);
        state.setY(node.y);
        state.setRoomType(getRoomType(node));
        state.setVisited(node.taken);

        return state;
    }

    /**
     * 获取房间类型名称
     */
    private String getRoomType(MapRoomNode node) {
        if (node == null || node.room == null) {
            return null;
        }

        String roomClassName = node.room.getClass().getSimpleName();

        // 简化房间类型名称
        if (roomClassName.contains("MonsterRoomBoss")) {
            return "Boss";
        } else if (roomClassName.contains("MonsterRoomElite")) {
            return "Elite";
        } else if (roomClassName.contains("MonsterRoom")) {
            return "Monster";
        } else if (roomClassName.contains("TreasureRoom")) {
            return "Treasure";
        } else if (roomClassName.contains("ShopRoom")) {
            return "Shop";
        } else if (roomClassName.contains("RestRoom")) {
            return "Rest";
        } else if (roomClassName.contains("EventRoom")) {
            return "Event";
        } else if (roomClassName.contains("Exordium")) {
            return "Monster"; // 第一幕普通怪物
        } else if (roomClassName.contains("TheCity")) {
            return "Monster"; // 第二幕普通怪物
        } else if (roomClassName.contains("TheBeyond")) {
            return "Monster"; // 第三幕普通怪物
        } else if (roomClassName.contains("TheEnding")) {
            return "Boss"; // 第四幕
        }

        return roomClassName;
    }

    /**
     * 根据Boss Key获取Boss名称
     */
    private String getBossNameByKey(String bossKey) {
        if (bossKey == null) return null;

        // 常见Boss Key映射
        switch (bossKey) {
            case "HEXAGHOST": return "六火亡魂";
            case "SLIME": return "史莱姆老大";
            case "GUARDIAN": return "守护者";
            case "BRONZE_AUTOMATON": return "青铜机械兽";
            case "COLLECTOR": return "收藏家";
            case "AUTOMATON": return "自动机";
            case "AWAKENED_ONE": return "觉醒者";
            case "TIME_EATER": return "时间吞噬者";
            case "DONU_AND_DECA": return "多态老鼠";
            case "THE_HEART": return "腐化之心";
            default: return bossKey;
        }
    }
}