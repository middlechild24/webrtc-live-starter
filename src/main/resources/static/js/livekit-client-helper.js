/**
 * LiveKitClientHelper — WebRTC 直播 / 监控通用封装
 * ==================================================
 * 
 * 适用场景：Spring Boot + LiveKit 后端，前端需要：
 *   1. 连接 LiveKit 房间（讲师/考生推流，学员/监考拉流）
 *   2. 管理本地摄像头 & 屏幕共享轨道
 *   3. 订阅远程参与者的音视频轨道
 * 
 * 依赖：livekit-client SDK（ESM），通过 import map 或相对路径引入
 * 
 * 用法示例：
 *   import { LiveKitClientHelper } from './js/livekit-client-helper.js';
 *   const lk = new LiveKitClientHelper({ livekitUrl: 'ws://host:7880' });
 *   await lk.connect(token);             // 连接房间
 *   await lk.publishCamera();             // 推摄像头
 *   await lk.publishScreen();             // 推屏幕
 *   lk.on('trackSubscribed', (track, pub, participant) => { ... });
 */

import { Room, RoomEvent, Track } from './livekit-client.esm.mjs';

export class LiveKitClientHelper {
    /**
     * @param {Object} options
     * @param {string} options.livekitUrl - LiveKit 服务地址，如 'ws://192.168.1.100:7880'
     * @param {string} [options.serverUrl]  - 后端 API 地址，默认 window.location.origin
     */
    constructor({ livekitUrl, serverUrl } = {}) {
        this.livekitUrl = livekitUrl || ('ws://' + window.location.hostname + ':7880');
        this.serverUrl = serverUrl || window.location.origin;
        this.room = null;
        this._listeners = {};
    }

    // ==================== 事件系统 ====================

    /**
     * 注册事件回调
     * 支持的事件：'connected', 'disconnected', 'participantConnected',
     *            'participantDisconnected', 'trackSubscribed', 'error'
     */
    on(event, callback) {
        if (!this._listeners[event]) this._listeners[event] = [];
        this._listeners[event].push(callback);
    }

    _emit(event, ...args) {
        (this._listeners[event] || []).forEach(fn => fn(...args));
    }

    // ==================== 连接管理 ====================

    /**
     * 连接 LiveKit 房间
     * @param {string} token - 由后端签发的 JWT token
     * @returns {Promise<Room>}
     */
    async connect(token) {
        this.room = new Room();
        this._setupRoomEvents(this.room);
        await this.room.connect(this.livekitUrl, token);
        this._emit('connected', this.room);
        return this.room;
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.room) {
            this.room.disconnect();
            this.room = null;
        }
    }

    get isConnected() {
        return this.room && this.room.state === 'connected';
    }

    // ==================== 本地推流 ====================

    /**
     * 开启摄像头推流
     * @param {Object} [constraints] - getUserMedia 约束，默认 { video: true, audio: false }
     * @returns {Promise<MediaStream>} 返回本地 stream，方便调用方做本地预览
     */
    async publishCamera(constraints = { video: true, audio: false }) {
        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        if (this.room && this.isConnected) {
            await this.room.localParticipant.publishTrack(
                stream.getVideoTracks()[0],
                { source: Track.Source.Camera }
            );
        }
        return stream;
    }

    /**
     * 开启屏幕共享推流
     * @param {Object} [constraints] - getDisplayMedia 约束，默认 { video: true }
     * @returns {Promise<MediaStream>}
     */
    async publishScreen(constraints = { video: true }) {
        const stream = await navigator.mediaDevices.getDisplayMedia(constraints);
        if (this.room && this.isConnected) {
            await this.room.localParticipant.publishTrack(
                stream.getVideoTracks()[0],
                { source: Track.Source.ScreenShare }
            );
        }
        // 监听用户通过浏览器停止共享
        stream.getVideoTracks()[0].addEventListener('ended', () => {
            this._emit('screenShareStopped');
        });
        return stream;
    }

    // ==================== 拉流（远程轨道处理） ====================

    /**
     * 将远程轨道附加到指定 <video> 元素
     * @param {MediaStreamTrack} track
     * @param {HTMLVideoElement} videoEl
     */
    static attachTrack(track, videoEl) {
        track.attach(videoEl);
    }

    /**
     * 判断一个 publication 是否是屏幕共享
     * @param {TrackPublication} pub
     * @returns {boolean}
     */
    static isScreenShare(pub) {
        return pub.source === Track.Source.ScreenShare;
    }

    /**
     * 判断一个 publication 是否是摄像头
     * @param {TrackPublication} pub
     * @returns {boolean}
     */
    static isCamera(pub) {
        return pub.source === Track.Source.Camera;
    }

    // ==================== API 辅助 ====================

    /**
     * 调用后端 REST API
     * @param {string} path    - 路径，如 '/api/demo/create-room'
     * @param {Object} [body]  - POST body
     * @returns {Promise<Object>} JSON 响应
     */
    async apiCall(path, body) {
        const opts = {
            method: body ? 'POST' : 'GET',
            headers: { 'Content-Type': 'application/json' }
        };
        if (body) opts.body = JSON.stringify(body);
        const resp = await fetch(this.serverUrl + path, opts);
        if (!resp.ok) {
            const text = await resp.text();
            throw new Error(`API ${path} 返回 ${resp.status}: ${text}`);
        }
        return resp.json();
    }

    // ==================== 内部 ====================

    _setupRoomEvents(room) {
        // 远程参与者加入
        room.on(RoomEvent.ParticipantConnected, (participant) => {
            this._emit('participantConnected', participant);
            // 如果参与者已有推流，立即处理
            participant.videoTrackPublications.forEach(pub => {
                if (pub.track) {
                    this._emit('trackSubscribed', pub.track, pub, participant);
                } else {
                    pub.on('subscribed', (track) => {
                        this._emit('trackSubscribed', track, pub, participant);
                    });
                }
            });
        });

        // 远程参与者离开
        room.on(RoomEvent.ParticipantDisconnected, (participant) => {
            this._emit('participantDisconnected', participant);
        });

        // 新推流事件
        room.on(RoomEvent.TrackSubscribed, (track, publication, participant) => {
            this._emit('trackSubscribed', track, publication, participant);
        });

        // 断开
        room.on(RoomEvent.Disconnected, () => {
            this._emit('disconnected');
        });
    }

    // ==================== 静态工具 ====================

    /**
     * 停止一个 MediaStream 的所有轨道
     */
    static stopStream(stream) {
        if (stream) {
            stream.getTracks().forEach(t => t.stop());
        }
    }
}
