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
 * 暴露给渲染进程的 API
 *
 * 使用方式（在 renderer.js 中）：
 *   window.overlayApi.onUpdate((data) => { ... })
 *   window.overlayApi.dragWindow(deltaX, deltaY)
 */
contextBridge.exposeInMainWorld('overlayApi', {

    /**
     * 监听推荐数据更新
     *
     * @param {Function} callback - 回调函数，接收推荐数据
     */
    onUpdate: (callback) => {
        ipcRenderer.on('update', (event, data) => {
            callback(data);
        });
    },

    /**
     * 监听加载状态
     *
     * @param {Function} callback - 回调函数
     */
    onLoading: (callback) => {
        ipcRenderer.on('loading', (event, data) => {
            callback(data);
        });
    },

    /**
     * 监听清空内容
     *
     * @param {Function} callback - 回调函数
     */
    onClear: (callback) => {
        ipcRenderer.on('clear', () => {
            callback();
        });
    },

    /**
     * 请求移动窗口
     *
     * @param {number} deltaX - X 方向移动距离
     * @param {number} deltaY - Y 方向移动距离
     */
    dragWindow: (deltaX, deltaY) => {
        ipcRenderer.send('window-drag', { deltaX, deltaY });
    },

    /**
     * 请求最小化窗口
     */
    minimizeWindow: () => {
        ipcRenderer.send('window-minimize');
    },

    /**
     * 移除所有监听器（清理用）
     */
    removeAllListeners: () => {
        ipcRenderer.removeAllListeners('update');
        ipcRenderer.removeAllListeners('loading');
    }
});