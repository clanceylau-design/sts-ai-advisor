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

// 安全日志函数，防止EPIPE错误
function safeLog(...args) {
    try {
        // 使用 stdout 而非 console.log，更可控
        process.stdout.write('[Overlay] ' + args.join(' ') + '\n');
    } catch (e) {
        // 静默忽略所有输出错误
    }
}

function safeError(...args) {
    try {
        // 尝试写入 stderr，如果失败则静默忽略
        process.stderr.write('[Overlay ERROR] ' + args.join(' ') + '\n');
    } catch (e) {
        // 静默忽略所有输出错误（包括 EPIPE）
    }
}

// ============================================================
// 全局错误处理
// ============================================================

/**
 * 捕获未处理的异常，防止 Electron 弹窗
 */
process.on('uncaughtException', (error) => {
    safeError('[Overlay] 未捕获异常:', error.message);
    // 不退出进程，继续运行
});

/**
 * 捕获未处理的 Promise rejection
 */
process.on('unhandledRejection', (reason, promise) => {
    safeError('[Overlay] 未处理的 Promise rejection:', reason);
});

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

/** 存储的自定义提示词 */
let customPrompt = null;

/** Mod HTTP服务器端口 */
const MOD_PORT = 17533;

/** 消息历史 */
const messages = [];
let msgId = 0;
const PAGE_SIZE = 20;

/**
 * 创建消息对象
 */
function createMessage(type, text) {
    return { id: ++msgId, type, text, timestamp: Date.now() };
}

/**
 * 将消息追加到历史并通知渲染进程
 */
function pushMessage(msg) {
    messages.push(msg);
    if (mainWindow) {
        mainWindow.webContents.send('history-append', msg);
    }
}

/**
 * 更新末尾消息并通知渲染进程
 */
function updateLast(msg) {
    if (mainWindow) {
        mainWindow.webContents.send('history-update', msg);
    }
}

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
        focusable: true,  // 可聚焦，支持输入

        // 安全配置
        webPreferences: {
            nodeIntegration: false,      // 禁用 Node 集成（安全）
            contextIsolation: true,      // 启用上下文隔离（安全）
            preload: path.join(__dirname, 'preload.js'),
            defaultEncoding: 'UTF-8'     // 默认编码
        }
    });

    // 设置最高置顶级别，确保在全屏游戏上方显示
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
            const MAX_BODY = 1024 * 1024;  // 1MB 限制
            let aborted = false;

            req.on('data', chunk => {
                body += chunk.toString('utf8');
                if (body.length > MAX_BODY) {
                    aborted = true;
                    res.writeHead(413, { 'Content-Type': 'application/json; charset=utf-8' });
                    res.end(JSON.stringify({ error: 'Request body too large' }));
                    req.destroy();
                }
            });

            req.on('end', () => {
                if (aborted) return;
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

        // GET 请求：获取自定义提示词
        if (req.method === 'GET' && url === '/custom-prompt') {
            safeLog('[Overlay] GET /custom-prompt 收到请求');
            safeLog('[Overlay] 当前 customPrompt:', customPrompt ? '已设置' : '未设置');
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            if (customPrompt) {
                const promptToSend = customPrompt;
                customPrompt = null;  // 发送后清空，避免重复使用
                safeLog('[Overlay] 返回自定义提示词，已清空');
                res.end(JSON.stringify({ success: true, prompt: promptToSend }));
            } else {
                safeLog('[Overlay] 无自定义提示词');
                res.end(JSON.stringify({ success: false, prompt: null }));
            }
            return;
        }

        // 其他请求返回 404
        res.writeHead(404);
        res.end();
    });

    httpServer.listen(CONFIG.PORT, () => {
        safeLog(`[Overlay] HTTP 服务器启动，端口: ${CONFIG.PORT}`);
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
        case '/update': {
            safeLog('[Overlay] 收到更新请求');
            const text = data.text || '';
            const last = messages[messages.length - 1];
            if (last && last.type === 'loading') {
                last.type = 'result';
                last.text = text;
                last.timestamp = Date.now();
                updateLast(last);
            } else {
                pushMessage(createMessage('result', text));
            }
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;
        }

        case '/loading': {
            const text = data.loadingText || '分析中...';
            const last = messages[messages.length - 1];
            if (last && last.type === 'loading') {
                last.text = text;
                last.timestamp = Date.now();
                updateLast(last);
            } else {
                pushMessage(createMessage('loading', text));
            }
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;
        }

        case '/clear':
            messages.length = 0;
            if (mainWindow) {
                mainWindow.webContents.send('history-clear');
            }
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

        case '/stream-start': {
            // 流式开始：将前一条 loading 降级为 status（紧凑显示），再创建新 loading
            safeLog('[Overlay] 流式开始');
            const prev = messages[messages.length - 1];
            if (prev && prev.type === 'loading') {
                prev.type = 'status';
                updateLast(prev);
            }
            pushMessage(createMessage('loading', ''));
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;
        }

        case '/stream-chunk':
            // 流式追加文本
            const chunkText = data.text || '';
            if (chunkText && mainWindow) {
                const last = messages[messages.length - 1];
                if (last && last.type === 'loading') {
                    last.text = (last.text || '') + chunkText;
                    last.timestamp = Date.now();
                    mainWindow.webContents.send('stream-append', { text: chunkText });
                }
            }
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify({ success: true }));
            break;

        case '/stream-end':
            // 流式结束：转换为 result 消息
            safeLog('[Overlay] 流式结束');
            const lastMsg = messages[messages.length - 1];
            if (lastMsg && lastMsg.type === 'loading') {
                lastMsg.type = 'result';
                lastMsg.timestamp = Date.now();
                if (mainWindow) {
                    mainWindow.webContents.send('stream-end', { text: lastMsg.text });
                }
            }
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
 * 处理窗口关闭请求（退出应用）
 */
ipcMain.on('window-close', () => {
    app.quit();
});

/**
 * 渲染进程就绪，发送初始消息
 */
ipcMain.on('history-init', () => {
    if (!mainWindow) return;
    const start = Math.max(0, messages.length - PAGE_SIZE);
    const slice = messages.slice(start);
    mainWindow.webContents.send('history-init', {
        messages: slice,
        hasMore: start > 0
    });
});

/**
 * 加载更早的消息
 */
ipcMain.on('load-more', (event, { beforeId }) => {
    if (!mainWindow) return;
    const idx = messages.findIndex(m => m.id === beforeId);
    if (idx <= 0) {
        mainWindow.webContents.send('history-prepend', { messages: [], hasMore: false });
        return;
    }
    const start = Math.max(0, idx - PAGE_SIZE);
    const slice = messages.slice(start, idx);
    mainWindow.webContents.send('history-prepend', {
        messages: slice,
        hasMore: start > 0
    });
});

/**
 * 处理自定义提示词请求
 */
ipcMain.on('custom-prompt', (event, { prompt }) => {
    safeLog('[Overlay] 收到自定义提示词:', prompt);

    if (!prompt || prompt.trim() === '') {
        safeError('[Overlay] 提示词为空，忽略');
        return;
    }

    customPrompt = prompt.trim();

    // 追加 prompt 消息到历史
    pushMessage(createMessage('prompt', customPrompt));

    // 触发Mod端分析
    try {
        triggerAnalysis();
    } catch (e) {
        safeError('[Overlay] triggerAnalysis 异常:', e.message);
    }
});

/**
 * 触发Mod端分析
 */
function triggerAnalysis() {
    const http = require('http');

    const options = {
        hostname: 'localhost',
        port: MOD_PORT,
        path: '/trigger-analysis',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        // 添加超时设置，避免长时间等待
        timeout: 3000
    };

    const req = http.request(options, (res) => {
        safeLog('[Overlay] 触发分析响应状态:', res.statusCode);

        // 消费响应数据，避免内存泄漏
        res.on('data', () => {});
        res.on('end', () => {});
    });

    req.on('error', (e) => {
        // 静默处理错误，避免弹窗
        // 常见错误：ECONNREFUSED（Mod未启动）、EPIPE（连接断开）
        safeError('[Overlay] 触发分析失败:', e.message);
    });

    req.on('timeout', () => {
        req.destroy();
        safeError('[Overlay] 触发分析超时');
    });

    // 写入空请求体并结束请求
    req.write('{}');
    req.end();
}

// ============================================================
// 父进程监控（防止僵尸进程）
// ============================================================

/**
 * 监控 Mod HTTP 服务器是否存活
 *
 * Overlay 由游戏进程（Mod）启动，当游戏关闭后 Mod 的 HTTP 服务器不再响应。
 * 定期轮询 Mod 端口，连续失败则自动退出，避免僵尸进程。
 */
function startParentWatchdog() {
    let failures = 0;
    const MAX_FAILURES = 3;
    const INTERVAL = 5000;  // 每 5 秒检测一次

    setInterval(() => {
        const req = http.request({
            hostname: 'localhost',
            port: MOD_PORT,
            path: '/status',
            method: 'GET',
            timeout: 2000
        }, (res) => {
            failures = 0;
            res.on('data', () => {});
            res.on('end', () => {});
        });

        req.on('error', () => {
            failures++;
            safeLog('父进程检测失败 (' + failures + '/' + MAX_FAILURES + ')');
            if (failures >= MAX_FAILURES) {
                safeLog('父进程已退出，Overlay 自动关闭');
                app.quit();
            }
        });

        req.on('timeout', () => {
            req.destroy();
            failures++;
            safeLog('父进程检测超时 (' + failures + '/' + MAX_FAILURES + ')');
            if (failures >= MAX_FAILURES) {
                safeLog('父进程已退出，Overlay 自动关闭');
                app.quit();
            }
        });

        req.end();
    }, INTERVAL);
}

// ============================================================
// 应用生命周期
// ============================================================

// Electron 就绪后创建窗口和服务器
app.whenReady().then(() => {
    createWindow();
    createHttpServer();
    startParentWatchdog();

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