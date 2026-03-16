/**
 * Electron 渲染进程脚本
 *
 * IM 风格消息列表：消息累积显示，支持向上滚动加载历史
 */

// ============================================================
// DOM 元素引用
// ============================================================

const panelContent = document.getElementById('panelContent');
const panelHeader = document.getElementById('panelHeader');
const btnClose = document.getElementById('btnClose');
const customPromptInput = document.getElementById('customPromptInput');
const btnSendPrompt = document.getElementById('btnSendPrompt');

// ============================================================
// 状态
// ============================================================

/** 是否还有更早的历史可加载 */
let hasMore = false;

/** 当前渲染的最早消息 id（用于分页请求） */
let earliestId = null;

/** 是否正在加载更多 */
let loading = false;

// ============================================================
// 工具函数
// ============================================================

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatTime(ts) {
    const d = new Date(ts);
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    const s = String(d.getSeconds()).padStart(2, '0');
    return h + ':' + m + ':' + s;
}

/**
 * 判断是否滚动到底部附近（允许 30px 误差）
 */
function isNearBottom() {
    return panelContent.scrollHeight - panelContent.scrollTop - panelContent.clientHeight < 30;
}

/**
 * 滚动到底部
 */
function scrollToBottom() {
    panelContent.scrollTop = panelContent.scrollHeight;
}

// ============================================================
// 消息 DOM 创建
// ============================================================

/**
 * 创建单条消息的 DOM 元素
 */
function createMessageEl(msg) {
    const el = document.createElement('div');
    el.className = 'message ' + msg.type;
    el.dataset.id = msg.id;

    const escaped = escapeHtml(msg.text);
    const html = escaped.replace(/\n/g, '<br>');

    if (msg.type === 'prompt') {
        el.innerHTML = '<div class="message-text">' + html + '</div>'
            + '<div class="message-time">' + formatTime(msg.timestamp) + '</div>';
    } else if (msg.type === 'loading') {
        el.innerHTML = '<div class="message-indicator"></div>'
            + '<div class="message-text">' + html + '</div>';
    } else {
        el.innerHTML = '<div class="message-text">' + html + '</div>'
            + '<div class="message-time">' + formatTime(msg.timestamp) + '</div>';
    }

    return el;
}

// ============================================================
// 消息操作
// ============================================================

/**
 * 追加消息到底部
 */
function appendMessage(msg) {
    // 移除空状态提示
    const empty = panelContent.querySelector('.status-message');
    if (empty) empty.remove();

    const near = isNearBottom();
    panelContent.appendChild(createMessageEl(msg));

    if (!earliestId) earliestId = msg.id;

    // 如果用户在底部附近，自动滚动
    if (near) scrollToBottom();
}

/**
 * 更新末尾消息（loading 原地更新 或 loading→result 替换）
 */
function updateLastMessage(msg) {
    const last = panelContent.querySelector('.message:last-child');
    if (!last) {
        appendMessage(msg);
        return;
    }

    const near = isNearBottom();

    // 替换整个元素
    const el = createMessageEl(msg);
    last.replaceWith(el);

    if (near) scrollToBottom();
}

/**
 * 在顶部批量插入旧消息
 */
function prependMessages(msgs) {
    if (!msgs.length) return;

    const prevHeight = panelContent.scrollHeight;
    const prevTop = panelContent.scrollTop;

    // 移除"加载更多"提示
    const hint = panelContent.querySelector('.load-more-hint');
    if (hint) hint.remove();

    const frag = document.createDocumentFragment();
    for (const msg of msgs) {
        frag.appendChild(createMessageEl(msg));
    }
    panelContent.insertBefore(frag, panelContent.firstChild);

    earliestId = msgs[0].id;

    // 如果还有更多，插入提示
    if (hasMore) {
        insertLoadMoreHint();
    }

    // 保持滚动位置不变
    const added = panelContent.scrollHeight - prevHeight;
    panelContent.scrollTop = prevTop + added;
}

/**
 * 清空所有消息
 */
function clearMessages() {
    panelContent.innerHTML = '<div class="status-message">等待游戏数据...</div>';
    hasMore = false;
    earliestId = null;
    loading = false;
}

/**
 * 插入"加载更多"提示
 */
function insertLoadMoreHint() {
    if (panelContent.querySelector('.load-more-hint')) return;
    const hint = document.createElement('div');
    hint.className = 'load-more-hint';
    hint.textContent = '向上滚动加载更多...';
    panelContent.insertBefore(hint, panelContent.firstChild);
}

// ============================================================
// 滚动分页
// ============================================================

function initScroll() {
    panelContent.addEventListener('scroll', () => {
        if (!hasMore || loading) return;
        if (panelContent.scrollTop < 50 && earliestId) {
            loading = true;
            window.overlayApi.loadMore(earliestId);
        }
    });
}

// ============================================================
// 用户交互
// ============================================================

function initDrag() {
    let isDragging = false;
    let lastX = 0;
    let lastY = 0;

    panelHeader.addEventListener('mousedown', (e) => {
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

function initButtons() {
    btnClose.addEventListener('click', () => {
        window.overlayApi.closeWindow();
    });

    btnSendPrompt.addEventListener('click', () => {
        sendCustomPrompt();
    });

    customPromptInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendCustomPrompt();
    });
}

function sendCustomPrompt() {
    try {
        const prompt = customPromptInput.value.trim();
        if (!prompt) return;

        try {
            window.overlayApi.sendCustomPrompt(prompt);
        } catch (err) {
            console.error('[Renderer] IPC error:', err);
            return;
        }

        customPromptInput.value = '';
        // 消息由 main.js 追加 prompt 消息，通过 history-append 回传
    } catch (e) {
        console.error('[Renderer] sendCustomPrompt error:', e);
    }
}

// ============================================================
// 事件监听
// ============================================================

function registerEventListeners() {
    window.overlayApi.onHistoryInit((data) => {
        panelContent.innerHTML = '';
        hasMore = data.hasMore;
        earliestId = null;

        if (data.messages.length === 0) {
            panelContent.innerHTML = '<div class="status-message">等待游戏数据...</div>';
            return;
        }

        if (hasMore) insertLoadMoreHint();

        for (const msg of data.messages) {
            panelContent.appendChild(createMessageEl(msg));
            if (!earliestId) earliestId = msg.id;
        }
        scrollToBottom();
    });

    window.overlayApi.onHistoryAppend((msg) => {
        appendMessage(msg);
    });

    window.overlayApi.onHistoryUpdate((msg) => {
        updateLastMessage(msg);
    });

    window.overlayApi.onHistoryClear(() => {
        clearMessages();
    });

    window.overlayApi.onHistoryPrepend((data) => {
        hasMore = data.hasMore;
        prependMessages(data.messages);
        loading = false;
    });
}

// ============================================================
// 初始化
// ============================================================

function init() {
    registerEventListeners();
    initDrag();
    initButtons();
    initScroll();

    // 显示初始状态，然后请求历史
    panelContent.innerHTML = '<div class="status-message">等待游戏数据...</div>';
    window.overlayApi.requestInit();
}

// ============================================================
// 全局错误处理
// ============================================================

window.onerror = function(message, source, lineno) {
    console.error('[Renderer] Error:', message, 'at', source, ':', lineno);
    return true;
};

window.addEventListener('unhandledrejection', function(event) {
    console.error('[Renderer] Unhandled rejection:', event.reason);
    event.preventDefault();
});

document.addEventListener('DOMContentLoaded', init);
