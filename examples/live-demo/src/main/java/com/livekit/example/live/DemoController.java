package com.livekit.example.live;

import com.livekit.starter.chat.ChatRoomManager;
import com.livekit.starter.core.LiveKitService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 直播演示 REST API
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final LiveKitService liveKitService;
    private final ChatRoomManager chatRoomManager;

    public DemoController(LiveKitService liveKitService, ChatRoomManager chatRoomManager) {
        this.liveKitService = liveKitService;
        this.chatRoomManager = chatRoomManager;
    }

    /**
     * 创建直播间 + 获取讲师 token
     */
    @PostMapping("/create-room")
    public Map<String, Object> createRoom(@RequestBody Map<String, String> body) {
        String roomName = body.getOrDefault("roomName", "demo-room");
        String teacherName = body.getOrDefault("teacherName", "Teacher");

        boolean created = liveKitService.createRoom(roomName);
        String token = liveKitService.generateTeacherToken(roomName, "teacher-" + teacherName);

        Map<String, Object> result = new HashMap<>();
        result.put("roomName", roomName);
        result.put("token", token);
        result.put("wsUrl", "ws://localhost:7880");
        result.put("created", created);
        return result;
    }

    /**
     * 获取学员 token
     */
    @PostMapping("/join-room")
    public Map<String, Object> joinRoom(@RequestBody Map<String, String> body) {
        String roomName = body.getOrDefault("roomName", "demo-room");
        String studentName = body.getOrDefault("studentName", "Student");

        String token = liveKitService.generateStudentToken(roomName, "student-" + studentName);

        Map<String, Object> result = new HashMap<>();
        result.put("roomName", roomName);
        result.put("token", token);
        result.put("wsUrl", "ws://localhost:7880");
        return result;
    }

    /**
     * 获取聊天室在线人数
     */
    @GetMapping("/online-count")
    public Map<String, Object> onlineCount(@RequestParam String roomName) {
        int count = chatRoomManager.getOnlineCount(roomName);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return result;
    }
}
