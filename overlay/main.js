/**
 * Electron 主进程
 *
 * 职责：
 * 1. 创建透明、置顶的悬浮窗口
 * 2. 管理 HTTP 服务器接收 Mod 数据
 * 3. 处理窗口生命周期
 *
 * 运行方式：Node.js 主进程，单例模式
 */

const { app, BrowserWindow, ipcMain } = require('electron');
const http = require('http');
const path = require('path');

// ============================================================
// 配置常量
// ============================================================

const CONFIG = {
    // HTTP 服务器端口，Mod 通过此端口推送数据
    PORT: 17532,

    // 默认窗口位置（屏幕右上角）
    WINDOW: {
        width: 340,
        height: 400,
        x: null,  // null 表示自动计算
        y: null
    }
};

// ============================================================
// 全局变量
// ============================================================

/** 主窗口实例 */
let mainWindow = null;

/** HTTP 服务器实例 */
let httpServer = null;

// ============================================================
// 窗口创建
// ============================================================

/**
 * 创建透明悬浮窗口
 *
 * 关键属性说明：
 * - transparent: true  -> 窗口背景透明
 * - frame: false       -> 无标题栏边框
 * - alwaysOnTop: true  -> 始终置顶
 * - skipTaskbar: true  -> 不显示在任务栏
 */
function createWindow() {
    // 计算窗口位置：屏幕右上角
    const { screen } = require('electron');
    const primaryDisplay = screen.getPrimaryDisplay();
    const { width: screenWidth, height: screenHeight } = primaryDisplay.workAreaSize;

    const windowX = CONFIG.WINDOW.x ?? screenWidth - CONFIG.WINDOW.width - 20;
    const windowY = CONFIG.WINDOW.y ?? 20;

    mainWindow = new BrowserWindow({
        width: CONFIG.WINDOW.width,
        height: CONFIG.WINDOW.height,
        x: windowX,
        y: windowY,

        // 透明窗口配置
        transparent: true,
        frame: false,
        alwaysOnTop: true,
        skipTaskbar: true,
        resizable: true,
        hasShadow: false,
        focusable: false,  // 不可聚焦，防止抢占焦点

        // 安全配置
        webPreferences: {
            nodeIntegration: false,      // 禁用 Node 集成（安全）
            contextIsolation: true,      // 启用上下文隔离（安全）
            preload: path.join(__dirname, 'preload.js'),
            defaultEncoding: 'UTF-8'     // 默认编码
        }
    });

    // 设置更强的置顶级别（screen-saver 是最高级别）
    mainWindow.setAlwaysOnTop(true, 'screen-saver');

    // 加载页面
    mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));

    // 开发调试时取消注释：
    // mainWindow.webContents.openDevTools({ mode: 'detach' });

    // 窗口关闭时的清理
    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

// ============================================================
// HTTP 服务器
// ============================================================

/**
 * 创建 HTTP 服务器
 *
 * 接收来自 Mod 的数据推送：
 * - POST /update  -> 更新推荐内容
 * - POST /hide    -> 隐藏面板
 * - POST /show    -> 显示面板
 * - POST /loading -> 显示加载状态
 * - GET /status   -> 返回当前状态
 */
function createHttpServer() {
    httpServer = http.createServer((req, res) => {
        // 允许跨域（本地通信）
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

        // 处理预检请求
        if (req.method === 'OPTIONS') {
            res.writeHead(200);
            res.end();
            return;
        }

        const url = req.url;

        // POST 请求：接收数据
        if (req.method === 'POST') {
            let body = '';

            req.on('data', chunk => {
                body += chunk.toString('utf8');  // 显式指定 UTF-8 编码
            });

            req.on('end', () => {
                try {
                    const data = body ? JSON.parse(body) : {};
                    handlePostRequest(url, data, res);
                } catch (e) {
                    res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
                    res.end(JSON.stringify({ error: 'Invalid JSON' }));
                }
            });
            return;
        }

        // GET 请求：状态查询
        if (req.method === 'GET' && url === '/status') {
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ status: 'running', hasWindow: mainWindow !== null }));
            return;
        }

        // 其他请求返回 404
        res.writeHead(404);
        res.end();
    });

    httpServer.listen(CONFIG.PORT, () => {
        console.log(`[Overlay] HTTP 服务器启动，端口: ${CONFIG.PORT}`);
    });
}

/**
 * 处理 POST 请求
 *
 * @param {string} url - 请求路径
 * @param {object} data - 请求数据
 * @param {http.ServerResponse} res - 响应对象
 */
function handlePostRequest(url, data, res) {
    if (!mainWindow) {
        res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ error: 'Window not ready' }));
        return;
    }

    switch (url) {
        case '/update':
            // 更新推荐内容
            console.log('[Overlay] 收到更新请求，数据:', JSON.stringify(data, null, 2));
            mainWindow.webContents.send('update', data);
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;

        case '/loading':
            // 显示加载状态
            mainWindow.webContents.send('loading', data);
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;

        case '/clear':
            // 清空面板内容（不隐藏窗口）
            mainWindow.webContents.send('clear');
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;

        case '/hide':
            // 隐藏窗口
            mainWindow.hide();
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;

        case '/show':
            // 显示窗口
            mainWindow.show();
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;

        default:
            res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ error: 'Not found' }));
    }
}

// ============================================================
// IPC 通信（渲染进程 -> 主进程）
// ============================================================

/**
 * 处理来自渲染进程的拖拽请求
 *
 * 渲染进程无法直接移动窗口，需要通过 IPC 请求主进程
 */
ipcMain.on('window-drag', (event, { deltaX, deltaY }) => {
    if (mainWindow) {
        const [x, y] = mainWindow.getPosition();
        mainWindow.setPosition(x + deltaX, y + deltaY);
    }
});

/**
 * 处理窗口最小化请求
 */
ipcMain.on('window-minimize', () => {
    if (mainWindow) {
        mainWindow.minimize();
    }
});

// ============================================================
// 应用生命周期
// ============================================================

// Electron 就绪后创建窗口和服务器
app.whenReady().then(() => {
    createWindow();
    createHttpServer();

    // macOS 特殊处理：点击 Dock 图标时重新创建窗口
    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0) {
            createWindow();
        }
    });
});

// 所有窗口关闭时退出（macOS 除外）
app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

// 应用退出时清理 HTTP 服务器
app.on('will-quit', () => {
    if (httpServer) {
        httpServer.close();
    }
});