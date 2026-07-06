package com.livekit.starter.autoconfigure;

import com.livekit.starter.core.LiveKitService;
import com.livekit.starter.core.RoomManager;
import com.livekit.starter.core.TokenGenerator;
import com.livekit.starter.chat.ChatRoomManager;
import com.livekit.starter.chat.ChatWebSocketHandler;
import com.livekit.starter.monitoring.MonitoringService;
import com.livekit.starter.webhook.LiveKitWebhookController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitAutoConfiguration {

    // ===== 核心服务（总是注册）=====

    @Bean
    public TokenGenerator tokenGenerator(LiveKitProperties properties) {
        return new TokenGenerator(properties);
    }

    @Bean
    public RoomManager roomManager(LiveKitProperties properties, TokenGenerator tokenGenerator) {
        return new RoomManager(properties, tokenGenerator);
    }

    @Bean
    public LiveKitService liveKitService(RoomManager roomManager, TokenGenerator tokenGenerator) {
        return new LiveKitService(roomManager, tokenGenerator);
    }

    // ===== WebHook 回调（Web 环境下注册）=====

    @Bean
    @ConditionalOnWebApplication
    public LiveKitWebhookController liveKitWebhookController() {
        return new LiveKitWebhookController();
    }

    // ===== 聊天室（需 WebSocket 依赖 + chatEnabled=true）=====

    @Configuration
    @EnableWebSocket
    @ConditionalOnWebApplication
    @ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketConfigurer")
    @ConditionalOnProperty(prefix = "livekit", name = "chat-enabled", havingValue = "true", matchIfMissing = true)
    public static class ChatConfiguration implements WebSocketConfigurer {

        private final ObjectProvider<ChatWebSocketHandler> handlerProvider;

        public ChatConfiguration(ObjectProvider<ChatWebSocketHandler> handlerProvider) {
            this.handlerProvider = handlerProvider;
        }

        @Bean
        public ChatRoomManager chatRoomManager() {
            return new ChatRoomManager();
        }

        @Bean
        public ChatWebSocketHandler chatWebSocketHandler(ChatRoomManager chatRoomManager) {
            return new ChatWebSocketHandler(chatRoomManager);
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            ChatWebSocketHandler handler = handlerProvider.getIfAvailable();
            if (handler != null) {
                registry.addHandler(handler, "/ws/chat/{roomId}")
                        .setAllowedOrigins("*");
            }
        }
    }

    // ===== 监控模块（monitoringEnabled=true 时注册）=====

    @Bean
    @ConditionalOnProperty(prefix = "livekit", name = "monitoring-enabled", havingValue = "true")
    public MonitoringService monitoringService(LiveKitService liveKitService) {
        return new MonitoringService(liveKitService);
    }
}
