package com.livekit.starter.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LiveKit WebHook 回调接收器。
 * 接收 LiveKit Server 推送的房间事件（参与者加入/离开、房间关闭等）。
 */
@RestController
@RequestMapping("/livekit/webhook")
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    /**
     * 接收 LiveKit 事件回调
     * 事件类型：room_started, room_finished, participant_joined, participant_left,
     *         track_published, track_unpublished
     */
    @PostMapping
    public String handleWebhook(@RequestBody Map<String, Object> event) {
        String eventType = (String) event.get("event");
        Map<String, Object> room = (Map<String, Object>) event.get("room");
        Map<String, Object> participant = (Map<String, Object>) event.get("participant");

        String roomName = room != null ? (String) room.get("name") : "unknown";
        String participantId = participant != null ? (String) participant.get("identity") : "unknown";

        log.info("LiveKit WebHook: event={}, room={}, participant={}", eventType, roomName, participantId);

        switch (eventType) {
            case "room_started":
                log.info("房间 {} 已创建", roomName);
                break;
            case "room_finished":
                log.info("房间 {} 已关闭", roomName);
                break;
            case "participant_joined":
                log.info("用户 {} 加入房间 {}", participantId, roomName);
                break;
            case "participant_left":
                log.info("用户 {} 离开房间 {}", participantId, roomName);
                break;
            case "track_published":
                String trackType = event.get("track") != null ?
                        (String) ((Map<String, Object>) event.get("track")).get("type") : "unknown";
                log.info("用户 {} 推送了 {} 轨道", participantId, trackType);
                break;
            default:
                log.debug("未处理的事件类型: {}", eventType);
        }

        return "ok";
    }
}
