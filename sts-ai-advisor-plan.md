# 杀戮尖塔 AI 助手 Mod 开发计划

## 一、项目概述

### 目标
开发一个杀戮尖塔（Slay the Spire）Steam版mod，作为"AI游戏伴侣"的原型：
1. 实时读取对局信息
2. 调用远程 LLM Agent API（Claude/GPT-4）
3. 提供全面的游戏辅助建议

### 核心功能
- **战斗系统**：出牌建议、目标选择、资源管理
- **决策辅助**：卡牌奖励、事件选择、地图路线、商店决策
- **战略规划**：流派构筑建议、遗物选择
- **情感交互**：游戏伴侣、闲聊、情绪价值

### 设计原则
1. **模块化**：游戏状态捕获层与 AI 决策层解耦
2. **可迁移**：抽象出通用接口，便于迁移到其他游戏
3. **渐进式**：从核心功能开始，逐步扩展

---

## 二、架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     Game Layer (STS)                        │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │
│  │ Player  │  │ Cards   │  │ Enemies │  │ Relics  │ ...   │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘       │
└───────┼────────────┼────────────┼────────────┼─────────────┘
        │            │            │            │
        ▼            ▼            ▼            ▼
┌─────────────────────────────────────────────────────────────┐
│                 State Capture Layer                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  GameStateCapture (Game-specific Implementation)     │   │
│  │  - IStateCapture interface                           │   │
│  │  - Output: Unified GameState JSON                    │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼ GameState JSON
┌─────────────────────────────────────────────────────────────┐
│                    AI Agent Layer                           │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │  LLM Client     │  │  Prompt Manager │                  │
│  │  (Claude/GPT)   │  │  (Templates)    │                  │
│  └────────┬────────┘  └────────┬────────┘                  │
│           │                    │                            │
│           └────────┬───────────┘                            │
│                    ▼                                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              AI Advisor Engine                       │   │
│  │  - Context Building                                 │   │
│  │  - Strategy Reasoning                               │   │
│  │  - Response Parsing                                 │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼ Recommendation
┌─────────────────────────────────────────────────────────────┐
│                    UI Layer                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Overlay Panel (LibGDX)                             │   │
│  │  - IUIRenderer interface                            │   │
│  │  - Toggle hotkeys                                   │   │
│  │  - Minimal intrusion design                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 可迁移架构设计

```
core-framework/                    # 通用框架（可复用）
├── interfaces/
│   ├── IStateCapture.java        # 游戏状态捕获接口
│   ├── IUIRenderer.java          # UI渲染接口
│   └── IGameConfig.java          # 游戏配置接口
├── llm/
│   ├── LLMClient.java            # 通用LLM客户端
│   ├── LLMProvider.java          # 提供商枚举(Claude/OpenAI)
│   └── PromptTemplate.java       # 提示词模板基类
├── models/
│   ├── GameState.java            # 通用游戏状态模型
│   └── Recommendation.java       # 通用建议模型
└── utils/
    ├── AsyncExecutor.java        # 异步执行器
    └── JsonUtils.java

sts-implementation/                # 杀戮尖塔实现
├── STSStateCapture.java          # 实现 IStateCapture
├── STSUIRenderer.java            # 实现 IUIRenderer
├── STSConfig.java                # 实现 IGameConfig
├── models/
│   ├── STSCard.java
│   ├── STSEnemy.java
│   └── STSPlayer.java
└── STSAIAdvisorMod.java          # Mod入口
```

---

## 三、技术栈

### 3.1 游戏端（Mod）

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 语言 | Java 8+ | 游戏原生语言 |
| 框架 | LibGDX | 游戏使用的框架 |
| Mod加载器 | ModTheSpire (MTS) | 必需的mod加载框架 |
| 核心库 | BaseMod + StSLib | 提供mod开发API |
| HTTP客户端 | OkHttp | 异步HTTP请求 |
| JSON处理 | Gson | Google JSON库 |
| 构建工具 | Gradle | 项目构建 |

### 3.2 LLM 服务端（可选）

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 后端框架 | Python FastAPI | 轻量高效，便于Prompt工程 |
| LLM API | Claude API / OpenAI API | 主流商业API |
| 部署 | Docker + Cloud | 可选，也可直接从Mod调用 |

---

## 四、功能模块详细设计

### 4.1 战斗出牌建议（核心功能）

**触发时机**：
- 每回合开始时自动分析
- 手牌变化时更新建议
- 用户按快捷键手动触发

**数据捕获**：
```java
public class BattleStateCapture {
    public BattleContext capture() {
        BattleContext ctx = new BattleContext();

        // 玩家状态
        ctx.player = capturePlayerState();

        // 手牌详情
        ctx.handCards = captureHandCards();

        // 敌人状态和意图
        ctx.enemies = captureEnemies();

        // 牌组信息
        ctx.deckInfo = captureDeckInfo();

        // 遗物效果
        ctx.relics = captureRelics();

        return ctx;
    }
}
```

**LLM Prompt 示例**：
```
你是杀戮尖塔专家玩家。分析当前战斗状态，推荐最优出牌策略。

【当前状态】
玩家: HP 45/80, 能量 3/3, 格挡 0
手牌: [Strike(+2力量=8伤), Defend(5格挡), Bash(8伤+2易伤)]
敌人: Cultist (48/48 HP), 意图: 攻击6伤
牌组: 5张牌堆, 2张弃牌堆
遗物: 燃烧之血, 锚

请推荐：
1. 最优出牌顺序
2. 能量使用策略
3. 关键考虑因素
```

### 4.2 卡牌奖励选择

**触发时机**：卡牌奖励界面出现时

**数据捕获**：
- 当前牌组构成
- 可选奖励卡牌
- 当前遗物协同
- 已有流派方向

**建议内容**：
- 每张卡牌评分（S/A/B/C/D）
- 推荐选择 + 理由
- 跳过建议（如果都不适合）

### 4.3 随机事件选择

**触发时机**：事件界面出现时

**数据捕获**：
- 事件ID和选项
- 当前玩家资源
- 牌组状态

**建议内容**：
- 各选项风险评估
- 最优选择推荐

### 4.4 地图路线规划

**触发时机**：打开地图或完成房间时

**数据捕获**：
- 当前层数和位置
- 可见地图结构
- 玩家当前状态

**建议内容**：
- 推荐路线标注
- 关键节点说明（精英、商店、休息）

### 4.5 游戏伴侣/闲聊

**触发时机**：
- 用户主动对话（快捷键触发输入框）
- 特定游戏事件触发评论

**特性**：
- 记忆上下文（使用LLM的conversation能力）
- 角色扮演（可选不同的陪伴者性格）
- 提供情绪价值

---

## 五、API 设计

### 5.1 统一请求格式

```json
{
  "game": "slay_the_spire",
  "scenario": "battle",
  "version": "1.0",
  "context": {
    // 场景特定数据
  },
  "conversation_history": [
    {"role": "user", "content": "..."},
    {"role": "assistant", "content": "..."}
  ],
  "options": {
    "detail_level": "brief",  // brief/detailed
    "include_reasoning": true
  }
}
```

### 5.2 统一响应格式

```json
{
  "recommendation": {
    "action": "play_card",
    "card_index": 2,
    "target_index": 0,
    "confidence": 0.85
  },
  "reasoning": "Bash applies Vulnerable, increasing future damage...",
  "alternatives": [
    {"action": "play_card", "card_index": 0, "reasoning": "..."}
  ],
  "companion_message": "这波稳了！先手Bash挂易伤，后面伤害爆炸！"
}
```

---

## 六、开发路线图

### Phase 1: 核心框架 (Week 1-2)
**目标**：跑通最小可用版本

- [ ] 搭建 Gradle 项目结构
- [ ] 集成 BaseMod、ModTheSpire
- [ ] 实现 Mod 入口和基础生命周期
- [ ] 实现战斗状态捕获（简化版）
- [ ] 实现 Claude/OpenAI API 调用
- [ ] 实现简单的文字建议显示
- [ ] **Milestone**: 战斗中能看到AI建议

### Phase 2: 功能扩展 (Week 3-4)
**目标**：完善核心功能

- [ ] 完善战斗建议（目标选择、能量管理）
- [ ] 实现卡牌奖励建议
- [ ] 实现事件选择建议
- [ ] 优化UI显示
- [ ] 添加配置面板（API Key、模型选择）

### Phase 3: 高级功能 (Week 5-6)
**目标**：全面游戏辅助

- [ ] 地图路线规划
- [ ] 商店决策建议
- [ ] 流派构筑建议
- [ ] 游戏伴侣闲聊功能

### Phase 4: 优化与发布 (Week 7-8)
**目标**：用户体验优化

- [ ] 性能优化（缓存、预请求）
- [ ] 错误处理和降级方案
- [ ] 用户文档
- [ ] Steam Workshop 发布（可选）

---

## 七、关键技术细节

### 7.1 异步处理（避免卡顿）

```java
public class AsyncLLMClient {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CompletableFuture<Recommendation> getRecommendationAsync(GameContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            return llmClient.request(ctx);
        }, executor);
    }
}

// 在游戏中使用
asyncClient.getRecommendationAsync(context)
    .thenAccept(rec -> {
        // 在主线程更新UI
        Gdx.app.postRunnable(() -> updateUI(rec));
    });
```

### 7.2 状态缓存与增量更新

```java
public class StateCache {
    private BattleContext lastContext;
    private long lastUpdateTime;

    public boolean needsUpdate() {
        // 仅在关键状态变化时更新
        return System.currentTimeMillis() - lastUpdateTime > 1000
            || handChanged()
            || enemyIntentChanged();
    }
}
```

### 7.3 游戏事件订阅

```java
public class EventSubscriber {
    public void subscribeAll() {
        // 战斗事件
        BaseMod.subscribe(BattleStartEvent.class, this::onBattleStart);
        BaseMod.subscribe(CardDrawEvent.class, this::onCardDraw);
        BaseMod.subscribe(TurnStartEvent.class, this::onTurnStart);

        // 非战斗事件
        BaseMod.subscribe(RewardScreenEvent.class, this::onRewardScreen);
        BaseMod.subscribe(EventScreenEvent.class, this::onEventScreen);
    }
}
```

---

## 八、依赖清单

```groovy
// build.gradle
dependencies {
    // === 游戏核心（需本地jar）===
    compileOnly files("libs/desktop-1.0.jar")
    compileOnly files("libs/StS.jar")

    // === Mod 框架 ===
    compileOnly "com.evacipated.cardcast:ModTheSpire:3.30.0"
    compileOnly "basemod:basemod:5.61.0"
    compileOnly "com.evacipated.cardcrawl:StSLib:1.4.0"

    // === HTTP & JSON ===
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    implementation "com.google.code.gson:gson:2.10.1"

    // === 测试 ===
    testImplementation "junit:junit:4.13.2"
}
```

---

## 九、确认的技术决策

✅ **API Key 管理**：用户在 Mod 配置界面自行配置
✅ **开发经验**：用户有 Java 开发经验
✅ **首个 Demo 范围**：最小功能 - 仅战斗出牌建议

---

## 十、实施计划（首个 Demo）

### 目标
快速验证核心架构可行性，实现战斗出牌建议功能。

### 具体步骤

#### Step 1: 环境准备
- 安装 JDK 8 或 11
- 安装 IntelliJ IDEA
- 下载杀戮尖塔游戏（Steam版）
- 下载 ModTheSpire、BaseMod、StSLib（从 Steam Workshop 或 GitHub releases）

#### Step 2: 项目创建
- 创建 Gradle 项目 `sts-ai-advisor`
- 配置 `build.gradle` 依赖
- 创建 `ModTheSpire.json` 配置文件
- 实现入口类 `STSAIAdvisorMod.java`

#### Step 3: 状态捕获
- 实现 `BattleStateCapture.java`
- 捕获：玩家状态、手牌、敌人、遗物
- 输出 JSON 格式的 `BattleContext`

#### Step 4: LLM 集成
- 实现 `LLMClient.java`（支持 Claude API）
- 实现异步请求机制
- 设计战斗建议 Prompt 模板

#### Step 5: UI 显示
- 实现简单的 `RecommendationPanel.java`
- 使用 LibGDX 文字渲染
- 添加快捷键开关

#### Step 6: 配置界面
- 实现设置面板（API Key 输入）
- 使用 BaseMod 的 ModPanel 系统

#### Step 7: 测试验证
- 进入游戏战斗，验证状态捕获
- 验证 LLM 请求和响应
- 验证 UI 显示

### 预估工时
- 有 Java 经验的开发者：约 3-5 天完成首个 Demo

---

## 十一、验证方案

### Phase 1 验证
1. 启动游戏，Mod 加载成功
2. 进入战斗，控制台输出状态捕获日志
3. 按快捷键，UI显示AI建议
4. 建议内容合理且不卡顿

### 完整验证
1. 完整游戏流程（Act 1-4）测试
2. 各种场景（精英、Boss、事件）测试
3. 性能测试（内存占用、FPS影响）
4. 长时间游戏稳定性测试

---

## 十二、参考资源

- [ModTheSpire Wiki](https://github.com/kiooeht/ModTheSpire/wiki)
- [BaseMod Wiki](https://github.com/daviscook477/BaseMod/wiki)
- [StSLib](https://github.com/kiooeht/StSLib)
- [LibGDX 文档](https://libgdx.com/wiki/)
- [杀戮尖塔 Modding 社区](https://discord.gg/SlayTheSpire)
- [Claude API 文档](https://docs.anthropic.com/)