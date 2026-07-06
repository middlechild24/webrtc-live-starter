/**
 * LiveKitChat — WebRTC 直播房间聊天 WebSocket 封装
 * ==================================================
 * 
 * 依赖：后端提供 Spring WebSocket 端点 /ws/chat/{roomName}
 * 
 * 用法示例：
 *   import { LiveKitChat } from './js/livekit-chat.js';
 *   const chat = new LiveKitChat({
 *       roomName: 'demo-room',
 *       userName: 'Teacher',
 *       serverUrl: window.location.origin
 *   });
 *   chat.on('message', (msg) => console.log(msg));
 *   chat.on('onlineCount', (count) => console.log(count));
 *   chat.connect();
 *   chat.send('Hello!');
 */

export class LiveKitChat {
    /**
     * @param {Object} options
     * @param {string} options.roomName  - 房间名
     * @param {string} options.userName  - 用户名
     * @param {string} [options.userId]  - 用户ID（默认同 userName）
     * @param {string} [options.serverUrl] - 后端地址，默认 window.location.origin
     */
    constructor({ roomName, userName, userId, serverUrl } = {}) {
        this.roomName = roomName;
        this.userName = userName;
        this.userId = userId || userName;
        this.serverUrl = serverUrl || window.location.origin;
        this.ws = null;
        this._listeners = {};
    }

    /**
     * 注册事件回调
     * 支持的事件：
     *   'chat'       - { type: 'chat', userName, content }
     *   'join'       - { type: 'join', content, onlineCount }
     *   'leave'      - { type: 'leave', content, onlineCount }
     *   'onlineCount' - number (在线人数)
     *   'open'       - WebSocket 已连接
     *   'close'      - WebSocket 已关闭
     *   'error'      - Error
     */
    on(event, callback) {
        if (!this._listeners[event]) this._listeners[event] = [];
        this._listeners[event].push(callback);
    }

    _emit(event, ...args) {
        (this._listeners[event] || []).forEach(fn => fn(...args));
    }

    /**
     * 连接聊天
     */
    connect() {
        this.disconnect();

        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.ws = new WebSocket(
            protocol + '//' + window.location.host +
            '/ws/chat/' + encodeURIComponent(this.roomName) +
            '?userId=' + encodeURIComponent(this.userId) +
            '&userName=' + encodeURIComponent(this.userName)
        );

        this.ws.onopen = () => {
            this._emit('open');
            // 获取在线人数
            this._fetchOnlineCount();
        };

        this.ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                this._emit(msg.type, msg);
                if (msg.onlineCount !== undefined) {
                    this._emit('onlineCount', msg.onlineCount);
                }
            } catch (e) {
                console.warn('聊天消息解析失败:', e);
            }
        };

        this.ws.onclose = () => {
            this._emit('close');
        };

        this.ws.onerror = (err) => {
            this._emit('error', err);
        };
    }

    /**
     * 发送消息
     * @param {string} text
     */
    send(text) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('聊天未连接');
            return;
        }
        this.ws.send(text);
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }

    get isConnected() {
        return this.ws && this.ws.readyState === WebSocket.OPEN;
    }

    async _fetchOnlineCount() {
        try {
            const resp = await fetch(
                this.serverUrl + '/api/demo/online-count?roomName=' +
                encodeURIComponent(this.roomName)
            );
            if (resp.ok) {
                const data = await resp.json();
                this._emit('onlineCount', data.count);
            }
        } catch (e) {
            // 非关键，忽略
        }
    }
}
