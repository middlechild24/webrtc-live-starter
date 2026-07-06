package com.livekit.starter.api;

import com.livekit.starter.core.LiveKitService;
import com.livekit.starter.monitoring.MonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 考试监控 REST API
 */
@RestController
@RequestMapping("/api/livekit/monitoring")
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final LiveKitService liveKitService;

    public MonitoringController(MonitoringService monitoringService, LiveKitService liveKitService) {
        this.monitoringService = monitoringService;
        this.liveKitService = liveKitService;
    }

    /**
     * 为考生创建监控房间
     * POST /api/livekit/monitoring/rooms
     * Body: { "examId": 123, "userId": 456 }
     */
    @PostMapping("/rooms")
    public ResponseEntity<Map<String, Object>> createMonitoringRoom(@RequestBody Map<String, Long> body) {
        Long examId = body.get("examId");
        Long userId = body.get("userId");

        if (examId == null || userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "examId and userId are required"));
        }

        String roomName = monitoringService.createMonitoringRoom(examId, userId);
        String token = monitoringService.getExamineeToken(roomName, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("roomName", roomName);
        result.put("token", token);
        result.put("wsUrl", "wss://localhost:7880"); // 考生端 WebSocket 信令地址
        return ResponseEntity.ok(result);
    }

    /**
     * 监考端获取拉流 token
     * POST /api/livekit/monitoring/token/proctor
     * Body: { "roomName": "monitor-123-456", "proctorId": 789 }
     */
    @PostMapping("/token/proctor")
    public ResponseEntity<Map<String, Object>> getProctorToken(@RequestBody Map<String, Object> body) {
        String roomName = (String) body.get("roomName");
        Long proctorId = body.get("proctorId") != null ?
                ((Number) body.get("proctorId")).longValue() : null;

        if (roomName == null || proctorId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "roomName and proctorId are required"));
        }

        String token = monitoringService.getProctorToken(roomName, proctorId);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("roomName", roomName);
        result.put("wsUrl", "wss://localhost:7880");
        return ResponseEntity.ok(result);
    }

    /**
     * 销毁监控房间
     * DELETE /api/livekit/monitoring/rooms/{roomName}
     */
    @DeleteMapping("/rooms/{roomName}")
    public ResponseEntity<Map<String, Object>> destroyMonitoringRoom(@PathVariable String roomName) {
        boolean success = monitoringService.destroyMonitoringRoom(roomName);
        return ResponseEntity.ok(Map.of("success", success));
    }
}
