/**
 * 测试 status 降级逻辑
 *
 * 模拟真实分析流程：
 * 1. loading("分析中...") → stream-start → stream-chunk × N → stream-end
 * 2. loading(tool progress) → stream-start → stream-chunk × N → stream-end
 * 3. 验证中间 loading 被降级为 status
 */

const http = require('http');
const PORT = 17532;

function post(path, body = {}) {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(body);
        const req = http.request({
            hostname: 'localhost',
            port: PORT,
            path,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json; charset=utf-8',
                'Content-Length': Buffer.byteLength(data, 'utf8')
            }
        }, (res) => {
            let buf = '';
            res.on('data', chunk => buf += chunk);
            res.on('end', () => {
                console.log(`  ${path} -> ${res.statusCode} ${buf}`);
                resolve(buf);
            });
        });
        req.on('error', reject);
        req.write(data, 'utf8');
        req.end();
    });
}

function get(path) {
    return new Promise((resolve, reject) => {
        const req = http.request({
            hostname: 'localhost',
            port: PORT,
            path,
            method: 'GET'
        }, (res) => {
            let buf = '';
            res.on('data', chunk => buf += chunk);
            res.on('end', () => resolve(buf));
        });
        req.on('error', reject);
        req.end();
    });
}

function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

async function run() {
    console.log('=== Status 降级测试 ===\n');

    // --- 第1轮：有 tool_calls 的分析 ---
    console.log('[1] sendLoading("分析中...")');
    await post('/loading', { loadingText: '分析中...' });
    await sleep(1000);

    console.log('[2] stream-start（应将"分析中..."降级为 status）');
    await post('/stream-start');
    await sleep(500);

    console.log('[3] stream-chunk × 3（模拟 LLM 思考输出）');
    await post('/stream-chunk', { text: '让我先' });
    await sleep(200);
    await post('/stream-chunk', { text: '看看当前' });
    await sleep(200);
    await post('/stream-chunk', { text: '战斗状态...' });
    await sleep(500);

    console.log('[4] stream-end（tool_calls 场景，流结束）');
    await post('/stream-end');
    await sleep(800);

    console.log('[5] sendLoading（工具进度）');
    await post('/loading', { loadingText: '🔍 获取信息中...\n→ 查看手牌\n  ✓ 5张手牌' });
    await sleep(1000);

    console.log('[6] stream-start（应将工具进度降级为 status）');
    await post('/stream-start');
    await sleep(500);

    console.log('[7] stream-chunk × 3（最终分析结果）');
    await post('/stream-chunk', { text: '第1回合建议：\n' });
    await sleep(300);
    await post('/stream-chunk', { text: '1. 使用重击攻击敌人\n' });
    await sleep(300);
    await post('/stream-chunk', { text: '2. 保留防御牌备用' });
    await sleep(500);

    console.log('[8] stream-end（最终结果完成）');
    await post('/stream-end');
    await sleep(1500);

    // --- 第2轮：直接分析，无 tool_calls ---
    console.log('\n--- 第2轮分析 ---');
    console.log('[9] sendLoading("分析中...")');
    await post('/loading', { loadingText: '分析中...' });
    await sleep(1000);

    console.log('[10] stream-start');
    await post('/stream-start');
    await sleep(500);

    console.log('[11] stream-chunk（第2轮结果）');
    await post('/stream-chunk', { text: '第2回合建议：\n优先使用易伤挂 debuff。' });
    await sleep(500);

    console.log('[12] stream-end');
    await post('/stream-end');

    console.log('\n=== 测试完成 ===');
    console.log('预期结果：');
    console.log('  - "分析中..." × 2 和 "🔍 获取信息中..." 应为灰色紧凑 status 样式');
    console.log('  - "让我先看看当前战斗状态..." 和两条最终建议应为蓝色 result 样式');
}

run().catch(e => console.error('测试失败:', e.message));
