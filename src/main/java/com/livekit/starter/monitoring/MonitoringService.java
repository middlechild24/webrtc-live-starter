package com.livekit.starter.monitoring;

import com.livekit.starter.core.LiveKitService;

/**
 * 考试监控服务 — 摄像头 + 屏幕共享监控
 *
 * 使用场景：
 * 1. 考试开始时为每个考生创建独立监控房间
 * 2. 考生端推两路流（摄像头 + 屏幕）
 * 3. 监考端拉流查看
 */
public class MonitoringService {

    private final LiveKitService liveKitService;

    public MonitoringService(LiveKitService liveKitService) {
        this.liveKitService = liveKitService;
    }

    /**
     * 为考生创建监控房间
     *
     * @param examId  考试 ID
     * @param userId  考生 ID
     * @return 房间名称
     */
    public String createMonitoringRoom(Long examId, Long userId) {
        String roomName = "monitor-" + examId + "-" + userId;
        liveKitService.createRoom(roomName, "{\"examId\":" + examId + ",\"userId\":" + userId + "}");
        return roomName;
    }

    /**
     * 获取考生推流 token（考生端使用）
     */
    public String getExamineeToken(String roomName, Long userId) {
        return liveKitService.generateToken(roomName, "examinee-" + userId, true);
    }

    /**
     * 获取监考老师拉流 token（监考端使用）
     */
    public String getProctorToken(String roomName, Long proctorId) {
        return liveKitService.generateToken(roomName, "proctor-" + proctorId, false);
    }

    /**
     * 考试结束时销毁监控房间
     */
    public boolean destroyMonitoringRoom(String roomName) {
        return liveKitService.deleteRoom(roomName);
    }
}
