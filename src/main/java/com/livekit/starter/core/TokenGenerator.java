package com.livekit.starter.core;

import com.livekit.starter.autoconfigure.LiveKitProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * LiveKit Token 生成器。
 * 使用 HMAC-SHA256 签名 JWT token，控制用户身份和房间权限。
 */
public class TokenGenerator {

    private final LiveKitProperties properties;
    private final SecretKey signingKey;

    public TokenGenerator(LiveKitProperties properties) {
        this.properties = properties;
        // LiveKit 用 API Secret 作为 HMAC 签名密钥
        // jjwt 0.12.x 要求 HMAC-SHA256 密钥至少 256 bits (32 bytes)
        // 如果 secret 太短，用 SHA-256 哈希扩展到 32 字节
        byte[] secretBytes = properties.getApiSecret().getBytes();
        if (secretBytes.length < 32) {
            try {
                java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
                secretBytes = sha256.digest(secretBytes);
            } catch (Exception e) {
                throw new RuntimeException("无法扩展 API Secret", e);
            }
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 生成加入 LiveKit 房间的 JWT token
     *
     * @param roomName   房间名称
     * @param identity   用户唯一标识（如 "teacher-1", "student-42"）
     * @param canPublish 是否有推流权限
     * @param canSubscribe 是否有订阅权限
     * @param metadata   额外元数据（JSON 字符串）
     * @return JWT token 字符串
     */
    public String generateToken(String roomName, String identity,
                                 boolean canPublish, boolean canSubscribe,
                                 Map<String, String> metadata) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 24 * 60 * 60 * 1000); // 24小时

        Map<String, Object> videoGrants = new HashMap<>();
        videoGrants.put("roomJoin", true);
        videoGrants.put("room", roomName);
        videoGrants.put("canPublish", canPublish);
        videoGrants.put("canSubscribe", canSubscribe);

        var builder = Jwts.builder()
                .issuer(properties.getApiKey())
                .subject(identity)
                .issuedAt(now)
                .expiration(expiry)
                .claim("video", videoGrants)
                .claim("name", identity); // 默认显示名 = identity

        if (metadata != null && !metadata.isEmpty()) {
            builder.claim("metadata", metadata);
        }

        return builder.signWith(signingKey).compact();
    }

    /**
     * 生成讲师 token（可推流 + 订阅）
     */
    public String generateTeacherToken(String roomName, String identity) {
        return generateToken(roomName, identity, true, true, null);
    }

    /**
     * 生成学员 token（仅订阅，不可推流）
     */
    public String generateStudentToken(String roomName, String identity) {
        return generateToken(roomName, identity, false, true, null);
    }

    /**
     * 生成带元数据的 token
     */
    public String generateToken(String roomName, String identity, boolean canPublish, Map<String, String> metadata) {
        return generateToken(roomName, identity, canPublish, true, metadata);
    }

    /**
     * 生成服务器管理 token（创建/删除房间、管理参与者）
     */
    public String generateServerToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 24 * 60 * 60 * 1000);

        Map<String, Object> videoGrants = new HashMap<>();
        videoGrants.put("roomCreate", true);
        videoGrants.put("roomList", true);
        videoGrants.put("roomAdmin", true);
        videoGrants.put("canListParticipants", true);

        return Jwts.builder()
                .issuer(properties.getApiKey())
                .subject("server-admin")
                .issuedAt(now)
                .expiration(expiry)
                .claim("video", videoGrants)
                .signWith(signingKey)
                .compact();
    }
}
