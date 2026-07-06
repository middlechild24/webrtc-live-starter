# WebRTC Live Starter — 实现文档

> 实现日期：2026-07-05  
> 项目路径：`/Users/binliang/workdir/code/webrtc-live-starter/`  
> 集成目标：`/Users/binliang/workdir/code/video-training-system/`

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术选型：为什么选 LiveKit](#2-技术选型为什么选-livekit)
3. [架构设计](#3-架构设计)
4. [实现步骤](#4-实现步骤)
5. [遇到的问题及解决方法](#5-遇到的问题及解决方法)
6. [最终交付物](#6-最终交付物)
7. [集成指南](#7-集成指南)
8. [性能分析：500 人考试监控](#8-性能分析500-人考试监控)

---

## 1. 项目概述

**目标**：构建一个可复用的 Spring Boot Starter（Jar 包），任何 Spring Boot 3.x 项目只需添加一行 Maven 依赖 + 3 行配置，即可获得 WebRTC 直播、视频会议、考试监控能力。

**交付物**：

| 组件 | 说明 | 端口 |
|------|------|------|
| `webrtc-live-starter` | Spring Boot Starter Jar | — |
| LiveKit Server | WebRTC SFU 媒体服务器（Go 二进制） | 7880 |
| `examples/live-demo` | 直播演示应用（讲师推流 + 学员观看 + 聊天室） | 8089 |
| `examples/exam-monitoring` | 考试监控演示（摄像头 + 屏幕双路推流） | 8090 |
| `video-training-system` 集成 | 视频培训系统集成 Starter | 8088 |

**方案选择**：方案 B（纯自建 WebRTC），不依赖第三方云服务/CDN。

---

## 2. 技术选型：为什么选 LiveKit

### 候选方案对比

| 维度 | LiveKit | mediasoup | Janus |
|------|---------|-----------|-------|
| 语言 | Go（单二进制） | Node.js + C++ addon | C |
| 部署 | 一个二进制，零依赖 | Node.js + npm + 编译 C++ | 手动 Makefile |
| M2 Mac 兼容 | 原生 ARM64，brew 安装 | C++ addon 编译脆弱 | 需要定制编译 |
| 客户端 SDK | 官方 JS/iOS/Android/Flutter/React | 仅 JS（mediasoup-client） | JS 插件 |
| API 风格 | REST + WebHook 回调 | 低级协议，自行封装 | 插件架构 |
| 录制 | 内置 Egress 服务 | 自行实现 | 录制插件 |
| 社区 | 最活跃，文档最好 | 文档不错 | 文档较旧 |

**选择 LiveKit 的原因**：

1. **单二进制部署**：Go 编译为单一可执行文件，不依赖 Node.js 运行时、C++ 编译链
2. **M2 Mac 原生支持**：ARM64 架构直接编译，无需 Rosetta 转译
3. **JS SDK 封装完整**：`livekit-client` 封装了所有 WebRTC 复杂度（SDP 协商、ICE candidates、Simulcast），前端只需几行代码
4. **内置 STUN**：局域网开发零配置，不需要单独部署 TURN 服务器

---

## 3. 架构设计

### 整体架构

```
浏览器 (Vue 3)
├── livekit-client JS SDK
│   ├── WebSocket (信令) → LiveKit Server (:7880)
│   └── WebRTC UDP (媒体流) → LiveKit Server (:7880)
│
LiveKit Server (:7880, Go 二进制)
├── 房间管理
├── SFU 转发（纯包转发，不做转码）
├── 内置 STUN（局域网无需 TURN）
└── WebHook 回调 → Spring Boot

Spring Boot (webrtc-live-starter jar)
├── LiveKitService — 创建房间、生成 Token
├── ChatWebSocketHandler — 文字聊天室
├── MonitoringService — 考试监控（摄像头 + 屏幕）
└── LiveKitWebhookController — 接收房间事件
```

### 关键设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 项目形态 | Spring Boot Starter | 一行依赖即可集成，比微服务更轻量 |
| Starter 父 POM | 无 Spring Boot Parent | 避免与消费项目的父 POM 冲突 |
| 依赖标记 | optional=true | Web/WebSocket 可选，非 Web 场景也能用 |
| Bean 条件化 | @ConditionalOn* | 按需加载聊天/监控模块 |
| 自动配置注册 | 双文件 | SB 3.x 需要 `spring.factories` + `AutoConfiguration.imports` |
| 考试监控房间 | 每个考生独立房间 | 避免共享房间的复杂度，监考按需切换 |
| 媒体流隔离 | UDP 走 LiveKit，HTTP 走 Spring Boot | 媒体流不影响答题接口性能 |

### 项目目录结构

```
webrtc-live-starter/
├── pom.xml                           # Spring Boot Starter POM
├── livekit.yaml                      # LiveKit 服务器配置
├── src/main/java/com/livekit/starter/
│   ├── autoconfigure/                # 自动配置
│   │   ├── LiveKitAutoConfiguration.java
│   │   └── LiveKitProperties.java
│   ├── core/                         # 核心服务
│   │   ├── LiveKitService.java       # 公共 API（用户唯一需要接触的类）
│   │   ├── RoomManager.java          # 调用 LiveKit REST API
│   │   └── TokenGenerator.java       # JWT Token 签名
│   ├── chat/                         # 聊天室模块
│   │   ├── ChatWebSocketHandler.java # Spring WebSocket 处理器
│   │   ├── ChatRoomManager.java      # 房间 → 会话映射、广播
│   │   └── ChatMessage.java          # 消息 POJO
│   ├── monitoring/                   # 考试监控模块
│   │   ├── MonitoringService.java    # 每个考生独立房间
│   │   └── MonitoringController.java # REST API
│   ├── webhook/
│   │   └── LiveKitWebhookController.java
│   └── api/
│       └── LiveController.java       # 房间管理 REST API
├── examples/
│   ├── live-demo/                    # 直播演示
│   │   ├── pom.xml
│   │   ├── src/main/java/.../LiveDemoApplication.java
│   │   └── src/main/resources/static/  # 前端 HTML
│   └── exam-monitoring/              # 考试监控演示
│       ├── pom.xml
│       ├── src/main/java/.../ExamMonitoringApplication.java
│       └── src/main/resources/static/  # 前端 HTML
└── frontend-sdk/
    └── livekit-starter.js            # 前端 JS SDK 封装
```

---

## 4. 实现步骤

### Step 1：搭建项目骨架

- 创建 Maven 项目，无 Spring Boot Parent POM
- 使用 `dependencyManagement` 导入 Spring Boot BOM
- 标记 `spring-boot-starter-web` 和 `spring-boot-starter-websocket` 为 optional
- 创建包结构：autoconfigure, core, chat, monitoring, webhook, api

### Step 2：LiveKit Server 安装

```bash
# 升级 Go（LiveKit 最新版需要 go >= 1.26）
brew upgrade go

# 从源码编译 LiveKit Server（~70MB ARM64 二进制）
git clone --depth 1 https://github.com/livekit/livekit.git
cd livekit && go build -o livekit-server ./cmd/server
cp livekit-server /opt/homebrew/bin/

# 创建配置文件 livekit.yaml
# 启动
livekit-server --config livekit.yaml &
```

### Step 3：核心服务实现

**TokenGenerator**：使用 jjwt 0.12.x 生成 LiveKit 兼容的 JWT Token

```java
// 关键：jjwt 0.12.x 要求 HMAC key ≥ 256 bits (32 bytes)
// 短 secret 需要 SHA-256 哈希补齐
byte[] secretBytes = apiSecret.getBytes();
if (secretBytes.length < 32) {
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    secretBytes = sha256.digest(secretBytes);
}
this.signingKey = Keys.hmacShaKeyFor(secretBytes);
```

**RoomManager**：通过 REST API 调用 LiveKit Server 管理房间

**LiveKitService**：对外暴露的统一 API

### Step 4：WebSocket 聊天室

- `ChatWebSocketHandler`：处理 WebSocket 连接、消息收发
- `ChatRoomManager`：管理房间内的 WebSocket 会话映射
- 支持 `/ws/chat/{roomId}` 路径，自动按房间隔离

### Step 5：考试监控模块

- 每个考生创建独立的 LiveKit 房间
- 房间名格式：`exam-{examId}-{userId}`
- 考生推流：摄像头（getUserMedia）+ 屏幕（getDisplayMedia）
- 监考端：按考生 ID 切换房间，拉取对应流

### Step 6：示例应用

- **直播演示**（8089）：讲师端（推流）+ 学员端（拉流）+ 聊天室
- **考试监控演示**（8090）：考生端（推摄像头+屏幕）+ 监考端（多考生切换）

### Step 7：集成到视频培训系统

- 添加 Maven 依赖 `com.livekit:webrtc-live-starter:1.0.0-SNAPSHOT`
- 配置 `application.yml`：`livekit.host`, `livekit.api-key`, `livekit.api-secret`
- 放行 SecurityConfig 中的 WebSocket 路径 `/ws/**`

---

## 5. 遇到的问题及解决方法

### 问题 1：Go 版本不兼容

**现象**：LiveKit 源码 `go build` 时报 `go.mod requires go >= 1.26`，但 brew 安装的 Go 是 1.24。

**原因**：LiveKit 最新版的 go.mod 声明了最小 Go 版本 1.26，brew 默认仓库滞后。

**解决**：
```bash
brew upgrade go  # 升级到 Go 1.26.x
```

**教训**：构建 LiveKit 前先检查 `go.mod` 中的 `go` 指令版本，确保本地 Go 版本 >= 要求。

---

### 问题 2：jjwt HMAC Key 长度不足

**现象**：`io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 48 bits (6 bytes) which is not secure enough for any JWT HMAC-SHA algorithm.`

**原因**：jjwt 0.12.x 要求 HMAC 密钥 ≥ 256 bits (32 bytes)。配置中的 `api-secret: secret` 只有 6 bytes。

**解决**：
```java
byte[] secretBytes = properties.getApiSecret().getBytes(StandardCharsets.UTF_8);
if (secretBytes.length < 32) {
    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    secretBytes = sha256.digest(secretBytes);  // 哈希到 32 bytes
}
this.signingKey = Keys.hmacShaKeyFor(secretBytes);
```

**教训**：不同版本的 jjwt 对密钥长度要求不同（0.11.x 宽松，0.12.x 严格）。短 secret 用 SHA-256 哈希补齐是通用做法。

---

### 问题 3：WebSocketHandler must not be null

**现象**：Spring Boot 启动时报错：
```
Caused by: java.lang.IllegalArgumentException: WebSocketHandler must not be null
    at com.livekit.starter.autoconfigure.LiveKitAutoConfiguration$ChatConfiguration
    .registerWebSocketHandlers(LiveKitAutoConfiguration.java:74)
```

**原因**：Spring 的 `WebSocketConfigurationSupport` 会在 `@Bean` 方法执行完之前就调用 `registerWebSocketHandlers()`，此时内部类中 `@Autowired` 的 `chatHandler` 还是 null。

**解决**：用 `ObjectProvider<T>` 延迟获取 Bean：

```java
@Configuration
@EnableWebSocket
public static class ChatConfiguration implements WebSocketConfigurer {

    private final ObjectProvider<ChatWebSocketHandler> handlerProvider;

    public ChatConfiguration(ObjectProvider<ChatWebSocketHandler> handlerProvider) {
        this.handlerProvider = handlerProvider;
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
```

**教训**：Spring WebSocket 的 `registerWebSocketHandlers` 执行时机早于常规 Bean 初始化。用 `ObjectProvider` 延迟获取，不要直接 `@Autowired`。

---

### 问题 4：Spring Boot 3.x 自动配置双文件注册

**现象**：只在 `spring.factories` 注册了自动配置，但 Spring Boot 3.x 不生效。

**原因**：Spring Boot 3.x 推荐使用新的注册方式 `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，但 `spring.factories` 仍然需要作为向后兼容。

**解决**：同时创建两个文件：

```
src/main/resources/META-INF/
├── spring.factories
│   org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
│   com.livekit.starter.autoconfigure.LiveKitAutoConfiguration
└── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    com.livekit.starter.autoconfigure.LiveKitAutoConfiguration
```

**教训**：Spring Boot 3.x Starter 需要双文件注册，缺一不可。

---

### 问题 5：SecurityConfig 阻止 WebSocket 连接

**现象**：集成到 video-training-system 后，WebSocket 连接返回 401。

**原因**：Spring Security 的 `anyRequest().authenticated()` 拦截了 `/ws/**` 路径。

**解决**：在 SecurityConfig 中放行 WebSocket 路径：
```java
.requestMatchers("/ws/**").permitAll()
```

---

### 问题 6：Content Security Policy 阻止 WebSocket

**现象**：前端 WebSocket 连接被 CSP 策略阻止。

**原因**：CSP 的 `connect-src` 只包含了 `https: wss:`，没有 `ws:`。

**解决**：在 SecurityConfig 中修改 CSP：
```java
.contentSecurityPolicy(csp -> csp
    .policyDirectives("... connect-src 'self' https: wss: ws:; ..."))
```

**教训**：使用 WebSocket 时需要检查两个地方：Security 路径过滤 + CSP 连接策略。

---

### 问题 7：内嵌类 @Bean 方法 static 修饰符问题

**现象**：在内嵌 `@Configuration` 类中将 `@Bean` 方法声明为 `static`，编译通过但 Spring 注入失败。

**原因**：Spring 的 `@Configuration` 内嵌类不支持 `static` Bean 方法，需要用实例方法。

**解决**：去掉 `static` 修饰符，改为普通实例方法，通过构造器注入依赖。

---

## 6. 最终交付物

### 运行状态验证（所有服务 200 OK）

```
LiveKit Server    (7880): ✅ OK
Video Training    (8088): ✅ 200
Live Demo         (8089): ✅ 200
Exam Monitoring   (8090): ✅ 200
```

### 文件清单

| 文件 | 说明 |
|------|------|
| `webrtc-live-starter/pom.xml` | Starter Maven POM |
| `webrtc-live-starter/livekit.yaml` | LiveKit 配置 |
| `webrtc-live-starter/src/.../autoconfigure/` | 自动配置（核心） |
| `webrtc-live-starter/src/.../core/LiveKitService.java` | 公共 API |
| `webrtc-live-starter/src/.../core/TokenGenerator.java` | JWT Token 生成 |
| `webrtc-live-starter/src/.../core/RoomManager.java` | 房间管理 |
| `webrtc-live-starter/src/.../chat/` | WebSocket 聊天室 |
| `webrtc-live-starter/src/.../monitoring/` | 考试监控 |
| `webrtc-live-starter/src/.../webhook/` | LiveKit 事件回调 |
| `webrtc-live-starter/src/.../api/` | REST 控制器 |
| `webrtc-live-starter/examples/live-demo/` | 直播演示 |
| `webrtc-live-starter/examples/exam-monitoring/` | 考试监控演示 |
| `webrtc-live-starter/frontend-sdk/livekit-starter.js` | 前端 SDK |
| `webrtc-live-starter/src/main/resources/META-INF/` | 自动配置注册 |
| `video-training-system/backend/pom.xml` | 集成后的 POM |
| `video-training-system/backend/src/.../SecurityConfig.java` | 放行 WebSocket |
| `video-training-system/backend/src/.../application.yml` | LiveKit 配置 |

### API 一览

```
POST   /api/livekit/rooms                    # 创建房间
DELETE /api/livekit/rooms/{name}             # 删除房间
POST   /api/livekit/token/teacher            # 讲师 Token
POST   /api/livekit/token/student             # 学员 Token
POST   /api/livekit/monitoring/start          # 开始监控
POST   /api/livekit/monitoring/stop           # 停止监控
GET    /api/livekit/monitoring/students       # 考生列表
WS     /ws/chat/{roomId}                     # 聊天室 WebSocket
POST   /api/livekit/webhook                  # LiveKit 回调
```

---

## 7. 集成指南

任何 Spring Boot 3.x 项目集成只需三步：

### Step 1：添加 Maven 依赖

```xml
<dependency>
    <groupId>com.livekit</groupId>
    <artifactId>webrtc-live-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 2：配置 application.yml

```yaml
livekit:
  host: http://localhost:7880
  api-key: devkey
  api-secret: secret
  chat-enabled: true          # 启用聊天室
  monitoring-enabled: false   # 启用考试监控
```

### Step 3：注入使用

```java
@Autowired
private LiveKitService liveKitService;

// 创建直播间
liveKitService.createRoom("course-1-live");

// 生成讲师 Token（可推流）
String teacherToken = liveKitService.generateTeacherToken("course-1-live", "teacher-1");

// 生成学员 Token（仅拉流）
String studentToken = liveKitService.generateStudentToken("course-1-live", "student-42");
```

---

## 8. 性能分析：500 人考试监控

### 核心洞察：媒体流不经过 Java 进程

```
考生浏览器 → WebRTC UDP → LiveKit Server (Go) → WebRTC UDP → 监考浏览器
                                ↑
                        完全不经过 Java 进程
```

考试答题（HTTP → Spring Boot → MySQL）和媒体流（UDP → LiveKit → UDP）是两条完全独立的通道。

### 资源估算

| 资源 | 估算值 | 说明 |
|------|--------|------|
| LiveKit CPU | 1-2 核 | SFU 是包转发器，不做转码 |
| LiveKit 内存 | 200-500 MB | 连接状态 + 缓冲区 |
| 考生上传带宽（每人） | ~500 kbps | 摄像头 200kbps (320×240, 10fps) + 屏幕 300kbps (1280×720, 2fps) |
| 服务器带宽 | 取决于监考拉流数 | 仅转发给正在观看的监考员 |

**关键优化**：监考员只拉取正在查看的考生流。500 人推流，1-3 个监考员每人看 2-4 路 = ~5-10 Mbps 服务器出口。不是 500 × 500kbps = 250 Mbps。

### 推流质量建议

| 流类型 | 分辨率 | 帧率 | 码率 |
|--------|--------|------|------|
| 摄像头（人脸检测） | 320×240 | 10 fps | ~200 kbps |
| 屏幕（文字可读） | 1280×720 | 2 fps | ~300 kbps |

### 对答题性能的影响：零影响

- 媒体编码使用硬件加速（M2 有专用编码器，x86 用 QuickSync/NVENC）
- 考生端 CPU 开销：~5-10% 用于编码
- 内存：~50-100 MB 用于 WebRTC 编码器
- HTTP 答题和 UDP 媒体流不竞争：不同协议、不同端口

---

## 附录：跨平台兼容性

| 组件 | macOS ARM64 | Windows x64 | Linux x64 |
|------|:-----------:|:-----------:|:---------:|
| LiveKit Server | ✅ Go build | ✅ `GOOS=windows go build` | ✅ Go build / Docker |
| livekit-client (JS) | ✅ Chrome/Safari/FF | ✅ Chrome/Edge/FF | ✅ Chrome/FF |
| Spring Boot Starter | ✅ Java 21 | ✅ Java 21 | ✅ Java 21 |

Go 交叉编译一行命令：`GOOS=windows GOARCH=amd64 go build -o livekit-server.exe`

---

## 相关文档

- [直播系统设计](docs/live-streaming-design.md)
- [Starter 设计](docs/webrtc-live-starter-design.md)
- [考试监控性能分析](docs/exam-monitoring-performance.md)
