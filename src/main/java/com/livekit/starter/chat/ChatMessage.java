package com.livekit.starter.chat;

import lombok.Data;

/**
 * 聊天消息模型
 */
@Data
public class ChatMessage {
    private String type;        // "chat" | "join" | "leave" | "system"
    private String userId;
    private String userName;
    private String content;
    private int onlineCount;    // 当前在线人数
    private long timestamp;     // epoch millis

    public static ChatMessage join(String userId, String userName, int onlineCount) {
        ChatMessage msg = new ChatMessage();
        msg.setType("join");
        msg.setUserId(userId);
        msg.setUserName(userName);
        msg.setContent(userName + " 进入了直播间");
        msg.setOnlineCount(onlineCount);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static ChatMessage leave(String userId, String userName, int onlineCount) {
        ChatMessage msg = new ChatMessage();
        msg.setType("leave");
        msg.setUserId(userId);
        msg.setUserName(userName);
        msg.setContent(userName + " 离开了直播间");
        msg.setOnlineCount(onlineCount);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static ChatMessage chat(String userId, String userName, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setType("chat");
        msg.setUserId(userId);
        msg.setUserName(userName);
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    public static ChatMessage system(String content) {
        ChatMessage msg = new ChatMessage();
        msg.setType("system");
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }
}
