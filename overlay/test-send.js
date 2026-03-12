/**
 * 测试脚本：发送 UTF-8 编码的中文数据
 */

const http = require('http');

const data = {
    scenario: 'battle',
    companionMessage: '开局不错！',
    reasoning: '建议先用Bash挂易伤。',
    suggestions: [
        { cardIndex: 1, cardName: '重刃', targetName: '敌人', priority: 1, reason: '高伤害' }
    ]
};

const postData = JSON.stringify(data);

const options = {
    hostname: 'localhost',
    port: 17532,
    path: '/update',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(postData, 'utf8')
    }
};

console.log('发送数据:', postData);
console.log('字节长度:', Buffer.byteLength(postData, 'utf8'));

const req = http.request(options, (res) => {
    console.log('状态码:', res.statusCode);

    let body = '';
    res.on('data', chunk => body += chunk);
    res.on('end', () => {
        console.log('响应:', body);
    });
});

req.on('error', (e) => {
    console.error('请求错误:', e.message);
});

req.write(postData, 'utf8');
req.end();