package com.livekit.starter.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 聊天处理器
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ChatRoomManager chatRoomManager;

    public ChatWebSocketHandler(ChatRoomManager chatRoomManager) {
        this.chatRoomManager = chatRoomManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = extractRoomId(session);
        String userId = extractParam(session, "userId");
        String userName = extractParam(session, "userName");

        if (roomId == null || userId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        chatRoomManager.joinRoom(roomId, session, userId, userName != null ? userName : userId);
        log.info("WebSocket 连接建立: room={}, user={}", roomId, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = extractRoomId(session);
        if (roomId == null) return;

        Map<String, String> info = chatRoomManager.getUserInfo(session);
        String userId = info != null ? info.get("userId") : "unknown";
        String userName = info != null ? info.get("userName") : "unknown";

        String content = message.getPayload();
        chatRoomManager.broadcast(roomId, ChatMessage.chat(userId, userName, content));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = extractRoomId(session);
        if (roomId != null) {
            chatRoomManager.leaveRoom(roomId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误", exception);
        String roomId = extractRoomId(session);
        if (roomId != null) {
            chatRoomManager.leaveRoom(roomId, session);
        }
        session.close();
    }

    private String extractRoomId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        // /ws/chat/{roomId}
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    private String extractParam(WebSocketSession session, String param) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                return kv[1];
            }
        }
        return null;
    }

    // 暴露用户信息查询给 ChatRoomManager
    public Map<String, String> getUserInfo(WebSocketSession session) {
        return chatRoomManager.getUserInfo(session);
    }
}
