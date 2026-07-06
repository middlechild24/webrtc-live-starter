package com.livekit.example.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livekit.starter.core.LiveKitService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/exam")
public class ExamApiController {

    private final LiveKitService liveKitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存中记录已开始的考试房间（考试开始→记录，考试结束→移除）
    private final Map<String, Map<String, Object>> activeRooms = new LinkedHashMap<>();

    public ExamApiController(LiveKitService liveKitService) {
        this.liveKitService = liveKitService;
    }

    /** 考试开始 — 为考生创建监控房间 */
    @PostMapping("/start")
    public Map<String, Object> startExam(@RequestBody Map<String, Object> body) {
        Long examId = ((Number) body.get("examId")).longValue();
        Long userId = ((Number) body.get("userId")).longValue();
        String userName = (String) body.getOrDefault("userName", "User-" + userId);

        String roomName = "exam-" + examId + "-" + userId;
        liveKitService.createRoom(roomName);

        String token = liveKitService.generateToken(roomName, "examinee-" + userId, true);

        // 记录活跃房间
        Map<String, Object> roomInfo = new HashMap<>();
        roomInfo.put("roomName", roomName);
        roomInfo.put("examId", examId);
        roomInfo.put("userId", userId);
        roomInfo.put("userName", userName);
        activeRooms.put(roomName, roomInfo);

        Map<String, Object> result = new HashMap<>();
        result.put("roomName", roomName);
        result.put("token", token);
        result.put("wsUrl", "ws://localhost:7880");
        result.put("examId", examId);
        result.put("userId", userId);
        return result;
    }

    /** 获取活跃的监控房间列表 — 监考端使用 */
    @GetMapping("/rooms")
    public Map<String, Object> listActiveRooms() {
        // 从 LiveKit Server 查询实际活跃的房间
        List<Map<String, Object>> rooms = new ArrayList<>();
        try {
            String roomsJson = liveKitService.listRooms();
            JsonNode root = objectMapper.readTree(roomsJson);
            JsonNode roomsNode = root.path("rooms");
            if (roomsNode.isArray()) {
                for (JsonNode room : roomsNode) {
                    String name = room.path("name").asText();
                    int numParticipants = room.path("num_participants").asInt(0);
                    // 只显示 exam- 前缀的房间且有人在里面
                    if (name.startsWith("exam-") && numParticipants > 0) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("roomName", name);
                        info.put("participants", numParticipants);

                        // 解析 examId 和 userId
                        String[] parts = name.replace("exam-", "").split("-");
                        if (parts.length >= 2) {
                            info.put("examId", parts[0]);
                            info.put("userId", parts[1]);
                        }

                        // 从内存中取 userName
                        Map<String, Object> cached = activeRooms.get(name);
                        info.put("userName", cached != null ? cached.get("userName") : "考生-" + parts[parts.length - 1]);

                        rooms.add(info);
                    }
                }
            }
        } catch (Exception e) {
            // 降级：使用内存缓存
            for (Map.Entry<String, Map<String, Object>> entry : activeRooms.entrySet()) {
                rooms.add(new HashMap<>(entry.getValue()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("rooms", rooms);
        result.put("total", rooms.size());
        return result;
    }

    /** 监考端 — 获取拉流 token */
    @PostMapping("/monitor")
    public Map<String, Object> monitorStudent(@RequestBody Map<String, Object> body) {
        String roomName = (String) body.get("roomName");
        Long proctorId = ((Number) body.get("proctorId")).longValue();

        String token = liveKitService.generateToken(roomName, "proctor-" + proctorId, false);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("wsUrl", "ws://localhost:7880");
        return result;
    }

    /** 调试：查看房间参与者详情（含 track 信息） */
    @GetMapping("/debug/participants")
    public Map<String, Object> debugParticipants(@RequestParam String roomName) {
        try {
            String json = liveKitService.listParticipants(roomName);
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> result = new HashMap<>();
            result.put("roomName", roomName);
            result.put("raw", root.toPrettyString());
            return result;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** 考试结束 — 销毁监控房间 */
    @PostMapping("/end")
    public Map<String, Object> endExam(@RequestBody Map<String, Object> body) {
        String roomName = (String) body.get("roomName");
        boolean deleted = liveKitService.deleteRoom(roomName);
        activeRooms.remove(roomName);
        return Map.of("success", deleted);
    }
}
