package com.livekit.starter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livekit.starter.autoconfigure.LiveKitProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * LiveKit 房间管理器。
 * 通过 LiveKit Server REST API 管理房间生命周期。
 * LiveKit 使用自定义 Bearer token 认证（基于 apiKey + apiSecret 签名的 JWT）。
 */
public class RoomManager {

    private final LiveKitProperties properties;
    private final HttpClient httpClient;
    private final TokenGenerator tokenGenerator;
    private final ObjectMapper objectMapper;
    private String serverTokenCache;
    private long serverTokenExpiry;

    public RoomManager(LiveKitProperties properties, TokenGenerator tokenGenerator) {
        this.properties = properties;
        this.tokenGenerator = tokenGenerator;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 创建房间
     */
    public boolean createRoom(String roomName, String metadata) {
        try {
            String url = properties.getHost() + "/twirp/livekit.RoomService/CreateRoom";
            String body;
            if (metadata != null) {
                body = String.format("{\"name\":\"%s\",\"empty_timeout\":300,\"metadata\":\"%s\"}",
                        roomName, metadata);
            } else {
                body = String.format("{\"name\":\"%s\",\"empty_timeout\":300}", roomName);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getServerToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            throw new RuntimeException("创建 LiveKit 房间失败: " + roomName, e);
        }
    }

    /**
     * 删除房间
     */
    public boolean deleteRoom(String roomName) {
        try {
            String url = properties.getHost() + "/twirp/livekit.RoomService/DeleteRoom";
            String body = String.format("{\"room\":\"%s\"}", roomName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getServerToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            throw new RuntimeException("删除 LiveKit 房间失败: " + roomName, e);
        }
    }

    /**
     * 列出所有房间
     */
    public String listRooms() {
        try {
            String url = properties.getHost() + "/twirp/livekit.RoomService/ListRooms";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getServerToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("查询房间列表失败", e);
        }
    }

    /**
     * 查询房间参与者
     */
    public String listParticipants(String roomName) {
        try {
            String url = properties.getHost() + "/twirp/livekit.RoomService/ListParticipants";
            String body = String.format("{\"room\":\"%s\"}", roomName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getServerToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("查询房间参与者失败: " + roomName, e);
        }
    }

    /**
     * 移除参与者
     */
    public boolean removeParticipant(String roomName, String identity) {
        try {
            String url = properties.getHost() + "/twirp/livekit.RoomService/RemoveParticipant";
            String body = String.format("{\"room\":\"%s\",\"identity\":\"%s\"}", roomName, identity);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getServerToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            throw new RuntimeException("移除参与者失败: " + roomName + "/" + identity, e);
        }
    }

    /**
     * 房间是否存在
     */
    public boolean roomExists(String roomName) {
        try {
            String rooms = listRooms();
            return rooms != null && rooms.contains("\"" + roomName + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取服务器管理 token（缓存 23 小时）
     */
    private String getServerToken() {
        long now = System.currentTimeMillis();
        if (serverTokenCache != null && now < serverTokenExpiry) {
            return serverTokenCache;
        }
        serverTokenCache = tokenGenerator.generateServerToken();
        serverTokenExpiry = now + 23 * 60 * 60 * 1000;
        return serverTokenCache;
    }
}
