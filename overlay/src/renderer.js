/**
 * Electron 渲染进程脚本
 *
 * 职责：
 * 1. 监听来自主进程的数据更新
 * 2. 渲染推荐内容到页面
 * 3. 处理用户交互（拖拽等）
 *
 * 运行环境：浏览器渲染进程，通过 preload.js 与主进程通信
 */

// ============================================================
// DOM 元素引用
// ============================================================

const panelContent = document.getElementById('panelContent');
const panelHeader = document.getElementById('panelHeader');
const btnMinimize = document.getElementById('btnMinimize');

// ============================================================
// 配置常量
// ============================================================

const CONFIG = {
    /** 每行最大字符数（用于自动换行） */
    charsPerLine: 28,

    /** 默认场景类型 */
    defaultScenario: 'battle'
};

// ============================================================
// 状态变量
// ============================================================

/** 当前场景类型 */
let currentScenario = CONFIG.defaultScenario;

// ============================================================
// 核心渲染函数
// ============================================================

/**
 * 渲染推荐内容
 *
 * @param {object} data - 推荐数据，结构如下：
 *   {
 *     scenario: 'battle' | 'reward',
 *     companionMessage: '鼓励消息',
 *     reasoning: '策略说明',
 *     suggestions: [
 *       { cardIndex: 0, cardName: '卡牌名', priority: 1, reason: '理由', targetName: '目标' }
 *     ]
 *   }
 */
function renderRecommendation(data) {
    // 更新场景类型
    if (data.scenario) {
        currentScenario = data.scenario;
    }

    // 构建 HTML
    let html = '';

    // 鼓励消息
    if (data.companionMessage) {
        html += `<div class="companion-message">【${escapeHtml(data.companionMessage)}】</div>`;
    }

    // 策略说明
    if (data.reasoning) {
        const wrappedReasoning = wrapText(data.reasoning, CONFIG.charsPerLine);
        html += `<div class="reasoning">${escapeHtml(wrappedReasoning)}</div>`;
    }

    // 分隔线
    if ((data.companionMessage || data.reasoning) && data.suggestions?.length) {
        html += '<div class="divider"></div>';
    }

    // 建议列表
    if (data.suggestions && data.suggestions.length > 0) {
        const sectionTitle = currentScenario === 'reward' ? '选牌建议' : '出牌顺序';
        html += `<div class="section-title">【${sectionTitle}】</div>`;

        for (const suggestion of data.suggestions) {
            html += renderSuggestion(suggestion);
        }
    }

    // 更新 DOM
    panelContent.innerHTML = html || '<div class="status-message">暂无建议</div>';
}

/**
 * 渲染单条建议
 *
 * @param {object} s - 建议数据
 * @returns {string} HTML 字符串
 */
function renderSuggestion(s) {
    const cardName = s.cardName || `卡牌${s.cardIndex}`;

    if (currentScenario === 'reward') {
        // Reward 场景：推荐/备选格式
        const isRecommended = s.priority === 1;
        const prefix = isRecommended ? '★ ' : '☆ ';
        const className = isRecommended ? 'recommended' : 'alternate';

        let text = `${prefix}${escapeHtml(cardName)}`;
        if (s.reason) {
            text += `：${escapeHtml(s.reason)}`;
        }

        const wrapped = wrapText(text, CONFIG.charsPerLine);
        return `<div class="suggestion ${className}">${escapeHtml(wrapped)}</div>`;

    } else {
        // Battle 场景：出牌顺序格式
        const targetName = s.targetName || '目标';

        let text = `${s.priority}. ${escapeHtml(cardName)} <span class="arrow">→</span> ${escapeHtml(targetName)}`;
        if (s.reason) {
            text += ` <span class="reason">：${escapeHtml(s.reason)}</span>`;
        }

        return `<div class="suggestion battle">${text}</div>`;
    }
}

/**
 * 渲染加载状态
 *
 * @param {object} data - 可选，包含 loadingText 字段
 */
function renderLoading(data = {}) {
    const text = data.loadingText || '分析中...';
    panelContent.innerHTML = `<div class="loading-message">${escapeHtml(text)}</div>`;
}

/**
 * 渲染状态消息
 *
 * @param {string} message - 消息内容
 */
function renderStatus(message) {
    panelContent.innerHTML = `<div class="status-message">${escapeHtml(message)}</div>`;
}

// ============================================================
// 文本处理工具
// ============================================================

/**
 * 自动换行
 *
 * 中文字符算 2 个宽度，英文算 1 个
 *
 * @param {string} text - 原始文本
 * @param {number} maxChars - 每行最大字符宽度
 * @returns {string} 换行后的文本
 */
function wrapText(text, maxChars) {
    if (!text) return '';

    const lines = [];
    const paragraphs = text.split('\n');

    for (const para of paragraphs) {
        if (!para) {
            lines.push('');
            continue;
        }

        let currentLine = '';
        let charWidth = 0;

        for (const char of para) {
            const width = char.charCodeAt(0) > 127 ? 2 : 1;  // 中文 2，英文 1

            if (charWidth + width > maxChars && currentLine) {
                lines.push(currentLine);
                currentLine = char;
                charWidth = width;
            } else {
                currentLine += char;
                charWidth += width;
            }
        }

        if (currentLine) {
            lines.push(currentLine);
        }
    }

    return lines.join('\n');
}

/**
 * HTML 转义（防止 XSS）
 *
 * @param {string} text - 原始文本
 * @returns {string} 转义后的文本
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============================================================
// 用户交互处理
// ============================================================

/**
 * 初始化拖拽功能
 *
 * 通过记录鼠标移动距离，通知主进程移动窗口
 */
function initDrag() {
    let isDragging = false;
    let lastX = 0;
    let lastY = 0;

    panelHeader.addEventListener('mousedown', (e) => {
        // 只响应左键
        if (e.button !== 0) return;

        isDragging = true;
        lastX = e.screenX;
        lastY = e.screenY;

        e.preventDefault();
    });

    document.addEventListener('mousemove', (e) => {
        if (!isDragging) return;

        const deltaX = e.screenX - lastX;
        const deltaY = e.screenY - lastY;

        if (deltaX !== 0 || deltaY !== 0) {
            window.overlayApi.dragWindow(deltaX, deltaY);
            lastX = e.screenX;
            lastY = e.screenY;
        }
    });

    document.addEventListener('mouseup', () => {
        isDragging = false;
    });
}

/**
 * 初始化按钮事件
 */
function initButtons() {
    // 最小化按钮
    btnMinimize.addEventListener('click', () => {
        window.overlayApi.minimizeWindow();
    });
}

// ============================================================
// 事件监听注册
// ============================================================

/**
 * 注册来自主进程的事件监听
 */
function registerEventListeners() {
    // 监听推荐数据更新
    window.overlayApi.onUpdate((data) => {
        console.log('[Renderer] 收到更新数据:', data);
        console.log('[Renderer] companionMessage:', data.companionMessage);
        console.log('[Renderer] reasoning:', data.reasoning);
        console.log('[Renderer] suggestions:', JSON.stringify(data.suggestions, null, 2));
        renderRecommendation(data);
    });

    // 监听加载状态
    window.overlayApi.onLoading((data) => {
        console.log('[Overlay] 加载状态:', data);
        renderLoading(data);
    });

    // 监听清空内容
    window.overlayApi.onClear(() => {
        console.log('[Overlay] 清空内容');
        renderStatus('等待游戏数据...');
    });
}

// ============================================================
// 初始化
// ============================================================

function init() {
    console.log('[Overlay] 初始化...');

    // 注册事件监听
    registerEventListeners();

    // 初始化交互
    initDrag();
    initButtons();

    // 显示初始状态
    renderStatus('等待游戏数据...');
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);