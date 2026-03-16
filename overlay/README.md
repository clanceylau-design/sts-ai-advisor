# STS AI Advisor Overlay

独立的悬浮面板程序，与游戏内的 Mod 配合使用。

## 架构说明

```
┌─────────────────────────────────────────────────────────────┐
│                 Slay the Spire (游戏进程)                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  STS AI Advisor Mod                                  │   │
│  │  - 状态捕获、LLM 调用                                 │   │
│  │  - 通过 HTTP 推送数据到 localhost:17532              │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP (localhost:17532)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Overlay (独立进程)                              │
│  - 透明置顶窗口                                             │
│  - 接收并渲染推荐内容                                       │
│  - 可拖拽移动                                               │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 安装依赖

```bash
cd overlay
npm install
```

### 2. 启动 Overlay

```bash
npm start
```

### 3. 测试通信

使用 curl 或 Postman 发送测试请求：

```bash
# 发送测试数据
curl -X POST http://localhost:17532/update \
  -H "Content-Type: application/json" \
  -d '{
    "scenario": "battle",
    "companionMessage": "开局不错！",
    "reasoning": "建议先用Bash挂易伤",
    "suggestions": [
      {"cardIndex": 1, "cardName": "Bash", "targetName": "Cultist", "priority": 1, "reason": "挂易伤"}
    ]
  }'

# 显示加载状态
curl -X POST http://localhost:17532/loading -d '{}'

# 隐藏面板
curl -X POST http://localhost:17532/hide

# 显示面板
curl -X POST http://localhost:17532/show
```

## API 接口

### POST /update

更新推荐内容。

请求体：
```json
{
  "scenario": "battle",           // 可选：battle | reward
  "companionMessage": "鼓励消息",  // 可选：显示在顶部
  "reasoning": "策略说明",         // 可选：显示在中间
  "suggestions": [                 // 可选：建议列表
    {
      "cardIndex": 0,
      "cardName": "Strike",
      "targetName": "Cultist",     // Battle 场景需要
      "priority": 1,               // 1=推荐，其他=备选（Reward 场景）
      "reason": "理由"
    }
  ]
}
```

### POST /loading

显示加载状态。

请求体：
```json
{
  "loadingText": "分析中..."  // 可选
}
```

### POST /hide

隐藏面板。

### POST /show

显示面板。

### GET /status

返回 Overlay 状态。

响应：
```json
{
  "status": "running",
  "hasWindow": true
}
```

## 打包发布

```bash
# 打包为便携版 exe（输出到 dist/ 目录）
npm run build

# 一键打包并部署到游戏目录
npm run deploy
```

`npm run deploy` 会：
1. 打包生成 `dist/overlay.exe`
2. 自动复制到 `D:\SteamLibrary\steamapps\common\SlayTheSpire\mods\sts-ai-advisor\overlay.exe`

## 使用流程

1. 启动游戏（Mod 会自动启动）
2. 启动 Overlay（手动启动或由 Mod 自动启动）
3. **F4**: 切换 Overlay 显示/隐藏
4. **F3**: 请求 AI 建议（支持任意场景）

## 通信端口

| 组件 | 端口 | 用途 |
|------|------|------|
| Mod HTTP Server | 17533 | 接收 Overlay 的触发请求 |
| Overlay HTTP Server | 17532 | 接收 Mod 的内容推送 |

## 项目结构

```
overlay/
├── package.json      # 项目配置
├── main.js           # 主进程：窗口创建、HTTP 服务器
├── preload.js        # 预加载脚本：安全 API 桥接
├── src/
│   ├── index.html    # 页面结构
│   ├── styles.css    # 样式
│   └── renderer.js   # 渲染逻辑
└── README.md         # 本文档
```