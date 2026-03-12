/**
 * 简单测试服务器
 *
 * 用于测试 Mod 到 Overlay 的 HTTP 通信
 *
 * 使用方法：
 *   node test-server.js
 *
 * 然后在另一个终端发送测试请求：
 *   curl -X POST http://localhost:17532/update -H "Content-Type: application/json" -d '{"companionMessage":"测试消息"}'
 */

const http = require('http');

const PORT = 17532;

const server = http.createServer((req, res) => {
    // CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    const url = req.url;
    console.log(`[${new Date().toLocaleTimeString()}] ${req.method} ${url}`);

    if (req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            console.log('  Body:', body);
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ success: true }));
        });
        return;
    }

    if (req.method === 'GET' && url === '/status') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'running' }));
        return;
    }

    res.writeHead(404);
    res.end();
});

server.listen(PORT, () => {
    console.log(`\n========================================`);
    console.log(`  Overlay Test Server`);
    console.log(`  Listening on http://localhost:${PORT}`);
    console.log(`========================================\n`);
    console.log('Waiting for Mod requests...\n');
});