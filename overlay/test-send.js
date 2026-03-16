/**
 * 测试脚本：模拟多轮对话，验证 IM 风格消息历史
 *
 * 流程：loading → update（结果替换loading） → loading → update → clear → loading → update
 */

const http = require('http');

const PORT = 17532;

function post(path, body) {
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
        req.on('error', (e) => reject(e));
        req.write(data, 'utf8');
        req.end();
    });
}

function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

async function run() {
    console.log('=== IM 风格消息历史测试 ===\n');

    // 第1轮
    console.log('[1] 发送 loading...');
    await post('/loading', { loadingText: '正在分析第1回合...' });
    await sleep(1500);

    console.log('[1] 发送 update（替换 loading）...');
    await post('/update', { text: '第1回合建议：\n1. 使用重击攻击敌人\n2. 保留防御牌备用' });
    await sleep(1000);

    // 第2轮
    console.log('[2] 发送 loading...');
    await post('/loading', { loadingText: '正在分析第2回合...' });
    await sleep(1000);

    console.log('[2] loading 文本更新...');
    await post('/loading', { loadingText: '第2回合分析中，请稍候...' });
    await sleep(1500);

    console.log('[2] 发送 update...');
    await post('/update', { text: '第2回合建议：\n1. 先用易伤挂debuff\n2. 再用旋风斩AOE' });
    await sleep(1000);

    // 第3轮（直接 update，无 loading 前置）
    console.log('[3] 直接发送 update（无 loading）...');
    await post('/update', { text: '系统提示：检测到精英战斗，建议使用药水。' });
    await sleep(1000);

    // 测试 clear
    console.log('[4] 发送 clear...');
    await post('/clear', {});
    await sleep(1500);

    // clear 后新一轮
    console.log('[5] clear 后发送 loading...');
    await post('/loading', { loadingText: '新战斗开始，分析中...' });
    await sleep(1500);

    console.log('[5] 发送 update...');
    await post('/update', { text: '新战斗建议：\n优先消灭左侧小怪，再集火Boss。' });

    console.log('\n=== 测试完成 ===');
}

run().catch(e => console.error('测试失败:', e.message));
