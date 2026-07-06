package com.livekit.starter.api;

import com.livekit.starter.core.LiveKitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 直播管理 REST API — 供业务系统调用
 */
@RestController
@RequestMapping("/api/livekit")
public class LiveController {

    private final LiveKitService liveKitService;

    public LiveController(LiveKitService liveKitService) {
        this.liveKitService = liveKitService;
    }

    /**
     * 创建直播间
     * POST /api/livekit/rooms
     * Body: { "roomName": "course-1-live", "metadata": "..." }
     */
    @PostMapping("/rooms")
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody Map<String, String> body) {
        String roomName = body.get("roomName");
        String metadata = body.get("metadata");

        if (roomName == null || roomName.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "roomName is required");
            return ResponseEntity.badRequest().body(error);
        }

        boolean success = liveKitService.createRoom(roomName, metadata);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("roomName", roomName);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除直播间
     * DELETE /api/livekit/rooms/{roomName}
     */
    @DeleteMapping("/rooms/{roomName}")
    public ResponseEntity<Map<String, Object>> deleteRoom(@PathVariable String roomName) {
        boolean success = liveKitService.deleteRoom(roomName);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取讲师推流 token
     * POST /api/livekit/token/teacher
     * Body: { "roomName": "...", "identity": "..." }
     */
    @PostMapping("/token/teacher")
    public ResponseEntity<Map<String, Object>> getTeacherToken(@RequestBody Map<String, String> body) {
        String roomName = body.get("roomName");
        String identity = body.get("identity");

        if (roomName == null || identity == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "roomName and identity are required"));
        }

        String token = liveKitService.generateTeacherToken(roomName, identity);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("roomName", roomName);
        result.put("canPublish", true);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取学员观看 token
     * POST /api/livekit/token/student
     */
    @PostMapping("/token/student")
    public ResponseEntity<Map<String, Object>> getStudentToken(@RequestBody Map<String, String> body) {
        String roomName = body.get("roomName");
        String identity = body.get("identity");

        if (roomName == null || identity == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "roomName and identity are required"));
        }

        String token = liveKitService.generateStudentToken(roomName, identity);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("roomName", roomName);
        result.put("canPublish", false);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取房间参与者列表
     * GET /api/livekit/rooms/{roomName}/participants
     */
    @GetMapping("/rooms/{roomName}/participants")
    public ResponseEntity<String> listParticipants(@PathVariable String roomName) {
        return ResponseEntity.ok(liveKitService.listParticipants(roomName));
    }

    /**
     * 踢出参与者
     * POST /api/livekit/rooms/{roomName}/kick/{identity}
     */
    @PostMapping("/rooms/{roomName}/kick/{identity}")
    public ResponseEntity<Map<String, Object>> kickParticipant(
            @PathVariable String roomName, @PathVariable String identity) {
        boolean success = liveKitService.kickParticipant(roomName, identity);
        return ResponseEntity.ok(Map.of("success", success));
    }
}
