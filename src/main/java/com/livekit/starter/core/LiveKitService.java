package com.livekit.starter.core;

import java.util.Map;

/**
 * LiveKit 核心服务 — 对外暴露的统一入口。
 * 任何 Spring Boot 项目注入此 Bean 即可使用全部 WebRTC 能力。
 */
public class LiveKitService {

    private final RoomManager roomManager;
    private final TokenGenerator tokenGenerator;

    public LiveKitService(RoomManager roomManager, TokenGenerator tokenGenerator) {
        this.roomManager = roomManager;
        this.tokenGenerator = tokenGenerator;
    }

    // ===== 房间管理 =====

    /** 创建直播间 */
    public boolean createRoom(String roomName, String metadata) {
        return roomManager.createRoom(roomName, metadata);
    }

    /** 创建直播间（简化版） */
    public boolean createRoom(String roomName) {
        return roomManager.createRoom(roomName, null);
    }

    /** 删除直播间 */
    public boolean deleteRoom(String roomName) {
        return roomManager.deleteRoom(roomName);
    }

    /** 房间是否存在 */
    public boolean roomExists(String roomName) {
        return roomManager.roomExists(roomName);
    }

    // ===== Token / 鉴权 =====

    /** 生成讲师 token（可推流 + 订阅） */
    public String generateTeacherToken(String roomName, String identity) {
        return tokenGenerator.generateTeacherToken(roomName, identity);
    }

    /** 生成学员 token（仅订阅，不可推流） */
    public String generateStudentToken(String roomName, String identity) {
        return tokenGenerator.generateStudentToken(roomName, identity);
    }

    /** 生成 token（可推流 + 自定义元数据） */
    public String generateToken(String roomName, String identity, boolean canPublish, Map<String, String> metadata) {
        return tokenGenerator.generateToken(roomName, identity, canPublish, metadata);
    }

    /** 生成 token（可推流，无元数据） */
    public String generateToken(String roomName, String identity, boolean canPublish) {
        return tokenGenerator.generateToken(roomName, identity, canPublish, null);
    }

    /** 踢出用户 */
    public boolean kickParticipant(String roomName, String identity) {
        return roomManager.removeParticipant(roomName, identity);
    }

    // ===== 查询 =====

    /** 获取房间参与者列表 */
    public String listParticipants(String roomName) {
        return roomManager.listParticipants(roomName);
    }

    /** 列出所有房间 */
    public String listRooms() {
        return roomManager.listRooms();
    }
}
