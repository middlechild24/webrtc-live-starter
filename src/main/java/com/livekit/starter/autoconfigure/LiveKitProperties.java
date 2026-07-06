package com.livekit.starter.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "livekit")
public class LiveKitProperties {
    /** LiveKit Server 地址 */
    private String host = "http://localhost:7880";

    /** LiveKit API Key */
    private String apiKey = "devkey";

    /** LiveKit API Secret */
    private String apiSecret = "secret";

    /** 是否启用 WebSocket 聊天室 */
    private boolean chatEnabled = true;

    /** 是否启用监考/监控模块 */
    private boolean monitoringEnabled = false;
}
