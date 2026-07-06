package com.livekit.starter.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 聊天室管理器 — 管理 WebSocket 连接和消息广播
 */
public class ChatRoomManager {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** roomId -> 该房间的所有 WebSocket 连接 */
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    /** session -> 用户信息 */
    private final Map<WebSocketSession, Map<String, String>> userInfo = new ConcurrentHashMap<>();

    /**
     * 用户加入聊天室
     */
    public void joinRoom(String roomId, WebSocketSession session, String userId, String userName) {
        rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);

        Map<String, String> info = new ConcurrentHashMap<>();
        info.put("userId", userId);
        info.put("userName", userName);
        userInfo.put(session, info);

        // 广播加入消息
        broadcast(roomId, ChatMessage.join(userId, userName, getOnlineCount(roomId)));
        log.info("用户 {} ({}) 加入聊天室 {}", userId, userName, roomId);
    }

    /**
     * 用户离开聊天室
     */
    public void leaveRoom(String roomId, WebSocketSession session) {
        Map<String, String> info = userInfo.remove(session);
        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                rooms.remove(roomId);
            }
        }

        if (info != null) {
            broadcast(roomId, ChatMessage.leave(info.get("userId"), info.get("userName"), getOnlineCount(roomId)));
            log.info("用户 {} ({}) 离开聊天室 {}", info.get("userId"), info.get("userName"), roomId);
        }
    }

    /**
     * 广播消息到房间所有人
     */
    public void broadcast(String roomId, ChatMessage message) {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        if (sessions == null) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化聊天消息失败", e);
            return;
        }

        log.info("广播消息到房间 {}: type={}, content={}", roomId, message.getType(), message.getContent());
        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (Exception e) {
                    log.error("发送消息失败到 session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 获取用户的 WebSocket 元信息
     */
    public Map<String, String> getUserInfo(WebSocketSession session) {
        return userInfo.get(session);
    }

    /**
     * 获取房间在线人数
     */
    public int getOnlineCount(String roomId) {
        Set<WebSocketSession> sessions = rooms.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }
}
