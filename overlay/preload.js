/**
 * Electron 预加载脚本
 *
 * 职责：
 * 1. 作为主进程和渲染进程之间的安全桥梁
 * 2. 暴露有限的 API 给渲染进程使用
 *
 * 安全说明：
 * - contextBridge.exposeInMainWorld 只暴露指定的 API
 * - 渲染进程无法直接访问 Node.js 或 Electron API
 */

const { contextBridge, ipcRenderer } = require('electron');

/**
 * 安全日志函数
 */
function safePreloadLog(...args) {
    try {
        process.stdout.write('[Preload] ' + args.join(' ') + '\n');
    } catch (e) {
        // 静默忽略
    }
}

/**
 * 安全的 IPC 发送函数，捕获错误
 */
function safeIpcSend(channel, data) {
    try {
        ipcRenderer.send(channel, data);
    } catch (e) {
        safePreloadLog('IPC send error:', e.message);
    }
}

/**
 * 安全的 IPC 监听封装
 */
function safeOn(channel, callback) {
    ipcRenderer.on(channel, (event, data) => {
        try {
            callback(data);
        } catch (e) {
            safePreloadLog(channel + ' callback error:', e.message);
        }
    });
}

contextBridge.exposeInMainWorld('overlayApi', {

    // ---- 历史消息协议 ----

    /** 请求初始消息 */
    requestInit: () => {
        safeIpcSend('history-init');
    },

    /** 初始消息列表 */
    onHistoryInit: (callback) => {
        safeOn('history-init', callback);
    },

    /** 新消息追加 */
    onHistoryAppend: (callback) => {
        safeOn('history-append', callback);
    },

    /** 末尾消息更新（loading 原地更新 / loading→result 替换） */
    onHistoryUpdate: (callback) => {
        safeOn('history-update', callback);
    },

    /** 历史清空 */
    onHistoryClear: (callback) => {
        safeOn('history-clear', callback);
    },

    /** 请求加载更早的消息 */
    loadMore: (beforeId) => {
        safeIpcSend('load-more', { beforeId });
    },

    /** 接收预加载的旧消息 */
    onHistoryPrepend: (callback) => {
        safeOn('history-prepend', callback);
    },

    // ---- 窗口操作 ----

    dragWindow: (deltaX, deltaY) => {
        safeIpcSend('window-drag', { deltaX, deltaY });
    },

    closeWindow: () => {
        safeIpcSend('window-close');
    },

    // ---- 自定义提示词 ----

    sendCustomPrompt: (prompt) => {
        safePreloadLog('sendCustomPrompt called');
        safeIpcSend('custom-prompt', { prompt });
    },

    // ---- 清理 ----

    removeAllListeners: () => {
        try {
            const channels = [
                'history-init', 'history-append', 'history-update',
                'history-clear', 'history-prepend'
            ];
            channels.forEach(ch => ipcRenderer.removeAllListeners(ch));
        } catch (e) {
            safePreloadLog('removeAllListeners error:', e.message);
        }
    }
});
