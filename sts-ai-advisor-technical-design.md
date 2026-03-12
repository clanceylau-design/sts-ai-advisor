# 杀戮尖塔 AI 助手 Mod 技术设计方案

## 文档信息
- **版本**: v1.0
- **日期**: 2026-03-09
- **状态**: 待评审

---

## 一、项目概述

### 1.1 项目背景
开发一个杀戮尖塔（Slay the Spire）Steam版mod，作为"AI游戏伴侣"的原型验证。

### 1.2 核心目标
1. 实时读取对局信息
2. 调用远程 LLM Agent API（Claude/GPT-4）
3. 提供全面的游戏辅助建议

### 1.3 功能范围（按优先级）

| 优先级 | 功能模块 | 描述 | 首期包含 |
|--------|----------|------|----------|
| P0 | 战斗出牌建议 | 分析手牌推荐最优出牌策略 | ✅ |
| P1 | 卡牌奖励选择 | 奖励界面的卡牌选择建议 | ⬜ |
| P1 | 随机事件选择 | 事件选项风险评估 | ⬜ |
| P2 | 地图路线规划 | 推荐最优行进路线 | ⬜ |
| P2 | 商店决策建议 | 购买/删牌建议 | ⬜ |
| P3 | 流派构筑建议 | 长期牌组规划 | ⬜ |
| P3 | 游戏伴侣 | 闲聊、情绪价值 | ⬜ |

### 1.4 设计原则
1. **模块化**：游戏状态捕获层与 AI 决策层解耦
2. **可迁移**：抽象通用接口，便于迁移到其他游戏
3. **渐进式**：从核心功能开始，逐步扩展
4. **高性能**：异步处理，不影响游戏帧率

---

## 二、技术选型

### 2.1 技术选型决策表

#### 2.1.1 开发语言与框架

| 决策项 | 选型 | 备选方案 | 决策理由 |
|--------|------|----------|----------|
| 开发语言 | **Java 8** | Java 11, Kotlin | 游戏基于Java 8，兼容性最佳 |
| 构建工具 | **Gradle 7.x** | Maven | 更灵活的依赖管理，社区主流 |
| IDE | **IntelliJ IDEA** | Eclipse | 更好的Java/Kotlin支持 |
| 版本控制 | **Git + GitHub** | GitLab, Gitee | 社区协作方便 |

#### 2.1.2 Mod 框架

| 组件 | 版本 | 来源 | 用途 |
|------|------|------|------|
| ModTheSpire | 3.30.0+ | [GitHub](https://github.com/kiooeht/ModTheSpire) | Mod加载器 |
| BaseMod | 5.61.0+ | [GitHub](https://github.com/daviscook477/BaseMod) | 核心API |
| StSLib | 1.4.0+ | [GitHub](https://github.com/kiooeht/StSLib) | 工具类库 |

#### 2.1.3 网络与数据处理

| 组件 | 版本 | 用途 | 选型理由 |
|------|------|------|----------|
| OkHttp | 4.12.0 | HTTP客户端 | 成熟稳定，支持异步 |
| Gson | 2.10.1 | JSON处理 | Google出品，简单易用 |
| Java.Util.Concurrent | 内置 | 异步执行 | 无额外依赖 |

#### 2.1.4 LLM 服务

| 服务商 | API | 优先级 | 说明 |
|--------|-----|--------|------|
| Anthropic | Claude 3.5/4 | 首选 | 推理能力强 |
| OpenAI | GPT-4o | 备选 | 生态成熟 |

### 2.2 技术风险与应对

| 风险 | 概率 | 影响 | 应对方案 |
|------|------|------|----------|
| 游戏版本更新导致API变更 | 中 | 高 | 使用BaseMod抽象层，关注社区更新 |
| LLM API响应延迟 | 高 | 中 | 异步调用 + 本地缓存 + 降级显示 |
| 内存占用过高 | 低 | 中 | 限制缓存大小，及时释放资源 |
| 网络请求失败 | 中 | 低 | 重试机制 + 错误提示 |

---

## 三、系统架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Slay the Spire Game                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ AbstractPlayer│  │AbstractDungeon│  │AbstractRoom │  ...            │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                  │
└─────────┼─────────────────┼─────────────────┼──────────────────────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        STS AI Advisor Mod                               │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Event Listener Layer                          │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │   │
│  │  │BattleListener│ │RewardListener│ │EventListener│ ...          │   │
│  │  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘               │   │
│  └─────────┼───────────────┼───────────────┼────────────────────────┘   │
│            │               │               │                            │
│            ▼               ▼               ▼                            │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    State Capture Layer                           │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │              BattleStateCapture                           │   │   │
│  │  │  - capturePlayer(): PlayerState                           │   │   │
│  │  │  - captureHand(): List<CardState>                         │   │   │
│  │  │  - captureEnemies(): List<EnemyState>                     │   │   │
│  │  │  - captureRelics(): List<RelicState>                      │   │   │
│  │  │  - captureDeckInfo(): DeckInfo                            │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                             │                                            │
│                             ▼ BattleContext (JSON)                       │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    AI Advisor Layer                              │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │   │
│  │  │PromptBuilder │  │  LLMClient   │  │ResponseParser│          │   │
│  │  │              │─▶│  (Async)     │─▶│              │          │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘          │   │
│  └──────────────────────────┬──────────────────────────────────────┘   │
│                             │                                            │
│                             ▼ Recommendation                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    UI Rendering Layer                            │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │              RecommendationPanel                          │   │   │
│  │  │  - render(SpriteBatch): void                              │   │   │
│  │  │  - update(Recommendation): void                           │   │   │
│  │  │  - toggle(): void                                         │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Configuration Layer                           │   │
│  │  ┌──────────────────────────────────────────────────────────┐   │   │
│  │  │              ModConfig (BaseMod ModPanel)                 │   │   │
│  │  │  - apiKey: String                                         │   │   │
│  │  │  - model: String (claude-3-5-sonnet/gpt-4o)              │   │   │
│  │  │  - enableAutoAdvice: boolean                              │   │   │
│  │  │  - showReasoning: boolean                                 │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 模块职责划分

#### 3.2.1 核心模块

| 模块名 | 包路径 | 职责 | 核心类 |
|--------|--------|------|--------|
| Mod入口 | `com.stsaiadvisor` | Mod生命周期管理 | `STSAIAdvisorMod` |
| 事件监听 | `com.stsaiadvisor.event` | 游戏事件订阅 | `BattleEventListener` |
| 状态捕获 | `com.stsaiadvisor.capture` | 游戏状态提取 | `BattleStateCapture` |
| LLM通信 | `com.stsaiadvisor.llm` | API调用与响应处理 | `LLMClient`, `PromptBuilder` |
| UI渲染 | `com.stsaiadvisor.ui` | 建议面板显示 | `RecommendationPanel` |
| 配置管理 | `com.stsaiadvisor.config` | 用户设置 | `ModConfig` |
| 数据模型 | `com.stsaiadvisor.model` | 状态/建议数据结构 | `BattleContext`, `Recommendation` |

#### 3.2.2 类设计详细

```
com.stsaiadvisor/
├── STSAIAdvisorMod.java              # Mod入口，实现PostInitializeSubscriber
│
├── config/
│   ├── ModConfig.java                # 配置管理，持久化到文件
│   └── ConfigPanel.java              # BaseMod配置面板
│
├── event/
│   ├── EventManager.java             # 事件分发管理器
│   ├── BattleEventListener.java      # 战斗事件监听
│   ├── RewardEventListener.java      # 奖励界面监听
│   └── GameEventListener.java        # 通用游戏事件
│
├── capture/
│   ├── StateCaptureFactory.java      # 状态捕获工厂
│   ├── BattleStateCapture.java       # 战斗状态捕获
│   └── RewardStateCapture.java       # 奖励状态捕获
│
├── model/
│   ├── PlayerState.java              # 玩家状态
│   ├── CardState.java                # 卡牌状态
│   ├── EnemyState.java               # 敌人状态
│   ├── BattleContext.java            # 战斗上下文（序列化）
│   └── Recommendation.java           # AI建议（反序列化）
│
├── llm/
│   ├── LLMClient.java                # HTTP客户端
│   ├── LLMClientFactory.java         # 客户端工厂（支持多provider）
│   ├── ClaudeClient.java             # Claude API实现
│   ├── OpenAIClient.java             # OpenAI API实现
│   ├── PromptBuilder.java            # Prompt模板构建
│   └── ResponseParser.java           # 响应解析
│
├── ui/
│   ├── RecommendationPanel.java      # 建议面板
│   ├── UIHelper.java                 # UI工具类
│   └── ChatInputPanel.java           # 聊天输入（P3）
│
└── util/
    ├── AsyncExecutor.java            # 异步执行器
    ├── JsonUtils.java                # JSON工具
    ├── CacheManager.java             # 缓存管理
    └── Constants.java                # 常量定义
```

---

## 四、数据模型设计

### 4.1 核心数据结构

#### 4.1.1 BattleContext（发送给LLM）

```java
public class BattleContext {
    private String scenario = "battle";
    private PlayerState player;
    private List<CardState> hand;
    private List<CardState> drawPile;      // 可选，简化版可省略
    private List<CardState> discardPile;   // 可选
    private List<EnemyState> enemies;
    private List<String> relics;
    private int turn;
    private int act;
}

public class PlayerState {
    private int currentHealth;
    private int maxHealth;
    private int energy;
    private int maxEnergy;
    private int block;
    private int strength;
    private int dexterity;
    private int focus;
    private int gold;
    private String characterClass;  // IRONCLAD, SILENT, DEFECT, WATCHER
}

public class CardState {
    private String id;              // 游戏内卡牌ID，如 "Strike_R"
    private String name;            // 显示名称
    private int cost;
    private String type;            // ATTACK, SKILL, POWER, CURSE, STATUS
    private int damage;             // 基础伤害
    private int block;              // 基础格挡
    private String description;     // 效果描述
    private boolean upgraded;
    private boolean ethereal;
    private boolean exhausts;
    private List<String> keywords;  // 关键词：Strike, Defend等
}

public class EnemyState {
    private String id;
    private String name;
    private int currentHealth;
    private int maxHealth;
    private int block;
    private List<EnemyIntent> intents;
    private List<String> powers;    // 当前debuff/power
}

public class EnemyIntent {
    private String type;            // ATTACK, DEFEND, BUFF, DEBUFF, SLEEP, UNKNOWN
    private int damage;             // 攻击伤害
    private int multiplier;         // 攻击次数
}
```

### 4.1.2 Recommendation（LLM响应）

```java
public class Recommendation {
    private List<CardPlaySuggestion> suggestions;
    private String reasoning;
    private String companionMessage;    // 陪伴者风格的评论
}

public class CardPlaySuggestion {
    private int cardIndex;              // 手牌索引
    private int targetIndex;            // 目标索引（-1表示无目标）
    private String cardName;
    private int priority;               // 优先级 1-5
    private String reason;
}
```

### 4.2 JSON 示例

#### 请求示例
```json
{
  "scenario": "battle",
  "player": {
    "currentHealth": 45,
    "maxHealth": 80,
    "energy": 3,
    "maxEnergy": 3,
    "block": 0,
    "strength": 2,
    "dexterity": 0,
    "characterClass": "IRONCLAD"
  },
  "hand": [
    {
      "id": "Strike_R",
      "name": "Strike",
      "cost": 1,
      "type": "ATTACK",
      "damage": 6,
      "keywords": ["Strike"]
    },
    {
      "id": "Bash",
      "name": "Bash",
      "cost": 2,
      "type": "ATTACK",
      "damage": 8,
      "description": "Apply 2 Vulnerable"
    }
  ],
  "enemies": [
    {
      "id": "Cultist",
      "name": "Cultist",
      "currentHealth": 48,
      "maxHealth": 48,
      "intents": [{"type": "ATTACK", "damage": 6}]
    }
  ],
  "relics": ["Burning Blood", "Anchor"],
  "turn": 1,
  "act": 1
}
```

#### 响应示例
```json
{
  "suggestions": [
    {
      "cardIndex": 1,
      "targetIndex": 0,
      "cardName": "Bash",
      "priority": 1,
      "reason": "Applies Vulnerable for 2 turns, increasing all damage by 50%"
    },
    {
      "cardIndex": 0,
      "targetIndex": 0,
      "cardName": "Strike",
      "priority": 2,
      "reason": "8 damage with your +2 Strength"
    }
  ],
  "reasoning": "Start with Bash to apply Vulnerable, then follow up with Strike for maximum damage.",
  "companionMessage": "开局Bash挂易伤，这波稳了！"
}
```

---

## 五、LLM Prompt 设计

### 5.1 System Prompt 模板

```
你是杀戮尖塔（Slay the Spire）的专家级AI助手。
你的任务是分析当前游戏状态，为玩家提供最优的决策建议。

## 你的能力
- 深度理解游戏机制：卡牌效果、遗物协同、敌人行为模式
- 考虑短期战术与长期战略的平衡
- 提供清晰的推理过程

## 输出格式要求
请以JSON格式输出建议，包含以下字段：
- suggestions: 建议的出牌/操作列表，按优先级排序
- reasoning: 整体策略说明（1-2句话）
- companionMessage: 友好的评论（可选）

## 分析要点
1. 当前回合的最优解
2. 能量效率
3. 敌人意图应对
4. 牌组和遗物的协同效应
5. 长期资源管理
```

### 5.2 战斗场景 Prompt

```
当前战斗状态：
玩家：{{player_summary}}
手牌：{{hand_cards}}
敌人：{{enemies}}
遗物：{{relics}}

请分析并推荐出牌策略。输出JSON格式。
```

---

## 六、部署方案

### 6.1 开发环境部署

#### 6.1.1 前置条件
- Windows 10/11
- Steam 已安装
- 杀戮尖塔游戏已购买并安装
- JDK 8 或 JDK 11
- IntelliJ IDEA Community 或 Ultimate

#### 6.1.2 环境搭建步骤

```bash
# 1. 安装 JDK（如果未安装）
# 下载并安装：https://adoptium.net/

# 2. 克隆项目
git clone https://github.com/your-repo/sts-ai-advisor.git
cd sts-ai-advisor

# 3. 安装 ModTheSpire（首次）
# 方法A: 通过 Steam Workshop 订阅
# 方法B: 手动下载
# 下载地址: https://github.com/kiooeht/ModTheSpire/releases

# 4. 准备游戏 JAR 文件
# 找到游戏安装目录（通常在 SteamLibrary/steamapps/common/SlayTheSpire）
# 复制以下文件到项目的 libs/ 目录：
#   - desktop-1.0.jar
#   - StS.jar (如果存在)
```

#### 6.1.3 IntelliJ 项目配置

```groovy
// settings.gradle
rootProject.name = 'sts-ai-advisor'

// build.gradle 完整配置
plugins {
    id 'java'
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    // 游戏核心（compileOnly 表示编译时需要，运行时由游戏提供）
    compileOnly files("libs/desktop-1.0.jar")

    // Mod 框架
    compileOnly files("libs/ModTheSpire.jar")
    compileOnly files("libs/BaseMod.jar")
    compileOnly files("libs/StSLib.jar")

    // HTTP 客户端
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'

    // JSON 处理
    implementation 'com.google.code.gson:gson:2.10.1'

    // 日志
    implementation 'org.slf4j:slf4j-api:2.0.9'
    implementation 'ch.qos.logback:logback-classic:1.4.11'

    // 测试
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.5.0'
}

// 打包配置
jar {
    from {
        configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
    manifest {
        attributes 'Main-Class': 'com.stsaiadvisor.STSAIAdvisorMod'
    }
}
```

### 6.2 ModTheSpire.json 配置

```json
{
  "modid": "sts-ai-advisor",
  "name": "AI Advisor",
  "author": "YourName",
  "description": "An AI-powered advisor that provides real-time battle suggestions for Slay the Spire",
  "version": "0.1.0",
  "sts_version": "12-01-2024",
  "mts_version": "3.30.0",
  "dependencies": [
    "basemod",
    "stslib"
  ],
  "update_json": ""
}
```

### 6.3 构建与安装流程

```
┌─────────────────────────────────────────────────────────────┐
│                      构建流程                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 编译源码                                                │
│     ./gradlew build                                         │
│                                                             │
│  2. 生成 JAR                                                │
│     输出: build/libs/sts-ai-advisor-0.1.0.jar              │
│                                                             │
│  3. 复制到 Mod 目录                                         │
│     目标: SteamLibrary/steamapps/common/SlayTheSpire/mods/ │
│                                                             │
│  4. 启动 ModTheSpire                                        │
│     运行: ModTheSpire.exe 或通过 Steam 启动                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 用户配置存储

```java
// 配置文件位置
// Windows: %APPDATA%/SlayTheSpire/mods/sts-ai-advisor/config.json

public class ModConfig {
    private static final String CONFIG_FILE = "mods/sts-ai-advisor/config.json";

    private String apiKey = "";
    private String model = "claude-3-5-sonnet-20241022";
    private boolean enableAutoAdvice = true;
    private boolean showReasoning = true;
    private int requestTimeout = 30;  // 秒

    // 加载配置
    public static ModConfig load() {
        // 从文件读取 JSON 并反序列化
    }

    // 保存配置
    public void save() {
        // 序列化为 JSON 并写入文件
    }
}
```

---

## 七、测试方案

### 7.1 测试策略

| 测试类型 | 目的 | 方法 | 自动化程度 |
|----------|------|------|------------|
| 单元测试 | 验证各模块独立功能 | JUnit | 自动化 |
| 集成测试 | 验证模块间协作 | 手动 + 半自动 | 半自动 |
| 端到端测试 | 完整游戏流程验证 | 手动测试 | 手动 |
| 性能测试 | 验证对游戏帧率影响 | FPS 监控 | 半自动 |

### 7.2 单元测试

#### 7.2.1 状态捕获测试

```java
public class BattleStateCaptureTest {

    @Test
    public void testCapturePlayerState() {
        // Mock 玩家对象
        AbstractPlayer mockPlayer = createMockPlayer(50, 80, 3);

        BattleStateCapture capture = new BattleStateCapture();
        PlayerState state = capture.capturePlayerState(mockPlayer);

        assertEquals(50, state.currentHealth);
        assertEquals(80, state.maxHealth);
        assertEquals(3, state.energy);
    }

    @Test
    public void testCaptureHandCards() {
        // 创建模拟手牌
        List<AbstractCard> mockHand = createMockHand();

        BattleStateCapture capture = new BattleStateCapture();
        List<CardState> cards = capture.captureHandCards(mockHand);

        assertEquals(5, cards.size());
        assertNotNull(cards.get(0).id);
    }
}
```

#### 7.2.2 LLM 客户端测试

```java
public class LLMClientTest {

    @Test
    public void testBuildPrompt() {
        BattleContext context = createTestContext();
        PromptBuilder builder = new PromptBuilder();

        String prompt = builder.buildBattlePrompt(context);

        assertTrue(prompt.contains("HP"));
        assertTrue(prompt.contains("energy"));
    }

    @Test
    public void testParseResponse() {
        String jsonResponse = """
            {
              "suggestions": [{"cardIndex": 0, "reason": "test"}],
              "reasoning": "test reasoning"
            }
            """;

        ResponseParser parser = new ResponseParser();
        Recommendation rec = parser.parse(jsonResponse);

        assertEquals(1, rec.suggestions.size());
    }
}
```

### 7.3 集成测试清单

| 测试场景 | 预期结果 | 验证方法 |
|----------|----------|----------|
| Mod 加载 | 控制台显示 "AI Advisor loaded" | 查看日志 |
| 配置保存 | API Key 正确保存到文件 | 检查配置文件 |
| 战斗状态捕获 | 正确输出玩家/手牌/敌人状态 | 控制台日志 |
| LLM 请求 | 成功调用 API 并获取响应 | 网络抓包/日志 |
| UI 渲染 | 建议面板正确显示 | 视觉检查 |
| 快捷键响应 | 按键后面板切换显示 | 手动测试 |

### 7.4 端到端测试场景

```
测试场景 1：基础战斗流程
────────────────────────
1. 启动游戏 + Mod
2. 开始新游戏（铁甲战士）
3. 进入第一场战斗
4. 验证：
   - 状态捕获日志输出
   - AI 建议面板显示
   - 建议内容合理
5. 按出牌建议操作
6. 战斗结束
7. 验证无崩溃、无内存泄漏

测试场景 2：精英战斗
────────────────────────
1. 到达精英房间
2. 验证 AI 建议适应更强的敌人
3. 完成战斗

测试场景 3：Boss 战斗
────────────────────────
1. 到达 Act Boss
2. 验证多阶段敌人的处理
3. 完成战斗

测试场景 4：长时运行
────────────────────────
1. 完整游戏一局（Act 1-4）
2. 监控：
   - FPS 变化
   - 内存使用
   - API 调用次数
3. 验证无崩溃
```

### 7.5 性能测试

#### 7.5.1 性能指标要求

| 指标 | 目标值 | 测试方法 |
|------|--------|----------|
| 帧率影响 | < 5 FPS 下降 | 对比有无 Mod 的 FPS |
| 内存增量 | < 100 MB | 任务管理器监控 |
| API 响应时间 | < 3 秒（95%分位） | 日志统计 |
| UI 渲染耗时 | < 5 ms/帧 | 代码插桩 |

---

## 八、核心代码实现参考

### 8.1 Mod 入口类

```java
package com.stsaiadvisor;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.RenderSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.event.EventManager;
import com.stsaiadvisor.llm.LLMClient;
import com.stsaiadvisor.ui.RecommendationPanel;

public class STSAIAdvisorMod implements PostInitializeSubscriber, RenderSubscriber {

    public static final String MOD_ID = "sts-ai-advisor";
    private static ModConfig config;
    private static LLMClient llmClient;
    private static RecommendationPanel panel;

    public STSAIAdvisorMod() {
        BaseMod.subscribe(this);
    }

    @Override
    public void receivePostInitialize() {
        // 加载配置
        config = ModConfig.load();

        // 初始化 LLM 客户端
        llmClient = new LLMClient(config);

        // 初始化 UI
        panel = new RecommendationPanel();

        // 注册事件监听
        EventManager.registerAll();

        System.out.println("[AI Advisor] Mod loaded successfully!");
    }

    @Override
    public void render(SpriteBatch sb) {
        if (panel.isVisible()) {
            panel.render(sb);
        }
    }
}
```

### 8.2 战斗状态捕获

```java
package com.stsaiadvisor.capture;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.stsaiadvisor.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BattleStateCapture {

    public BattleContext capture() {
        if (AbstractDungeon.player == null || AbstractDungeon.getCurrRoom() == null) {
            return null;
        }

        BattleContext context = new BattleContext();
        context.setScenario("battle");
        context.setPlayer(capturePlayer());
        context.setHand(captureHand());
        context.setEnemies(captureEnemies());
        context.setRelics(captureRelics());
        context.setTurn(AbstractDungeon.actionManager.turn);
        context.setAct(AbstractDungeon.actNum);

        return context;
    }

    private PlayerState capturePlayer() {
        PlayerState state = new PlayerState();
        state.setCurrentHealth(AbstractDungeon.player.currentHealth);
        state.setMaxHealth(AbstractDungeon.player.maxHealth);
        state.setEnergy(AbstractDungeon.player.energy.energy);
        state.setBlock(AbstractDungeon.player.currentBlock);
        state.setCharacterClass(AbstractDungeon.player.chosenClass.name());
        return state;
    }

    private List<CardState> captureHand() {
        return AbstractDungeon.player.hand.group.stream()
            .map(this::convertCard)
            .collect(Collectors.toList());
    }

    private CardState convertCard(com.megacrit.cardcrawl.cards.AbstractCard card) {
        CardState state = new CardState();
        state.setId(card.cardID);
        state.setName(card.name);
        state.setCost(card.costForTurn);
        state.setType(card.type.name());
        state.setDamage(card.baseDamage);
        state.setBlock(card.baseBlock);
        state.setUpgraded(card.upgraded);
        return state;
    }

    private List<EnemyState> captureEnemies() {
        List<EnemyState> enemies = new ArrayList<>();
        for (AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (!monster.isDeadOrEscaped()) {
                enemies.add(convertEnemy(monster));
            }
        }
        return enemies;
    }

    private List<String> captureRelics() {
        return AbstractDungeon.player.relics.stream()
            .map(r -> r.name)
            .collect(Collectors.toList());
    }
}
```

### 8.3 Claude API 客户端

```java
package com.stsaiadvisor.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.model.BattleContext;
import com.stsaiadvisor.model.Recommendation;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClaudeClient implements LLMClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.parse("application/json");

    private final ModConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executor;
    private final PromptBuilder promptBuilder;

    public ClaudeClient(ModConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(config.getRequestTimeout(), java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
        this.executor = Executors.newCachedThreadPool();
        this.promptBuilder = new PromptBuilder();
    }

    @Override
    public CompletableFuture<Recommendation> requestAsync(BattleContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return request(context);
            } catch (IOException e) {
                throw new RuntimeException("LLM request failed", e);
            }
        }, executor);
    }

    @Override
    public Recommendation request(BattleContext context) throws IOException {
        String prompt = promptBuilder.buildBattlePrompt(context);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", 1024);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);

        requestBody.add("messages", gson.toJsonTree(List.of(userMsg)));

        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", config.getApiKey())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API request failed: " + response.code());
            }
            String responseBody = response.body().string();
            return parseResponse(responseBody);
        }
    }

    private Recommendation parseResponse(String responseBody) {
        JsonObject json = gson.fromJson(responseBody, JsonObject.class);
        String content = json.getAsJsonArray("content")
            .get(0).getAsJsonObject()
            .get("text").getAsString();
        return gson.fromJson(content, Recommendation.class);
    }
}
```

---

## 九、开发里程碑

### Milestone 1: 基础框架 (Day 1-2)
**完成标准**: Mod 能成功加载并显示文字

| 任务 | 预估 | 产出 |
|------|------|------|
| 创建 Gradle 项目 | 1h | 项目骨架 |
| 配置 ModTheSpire.json | 0.5h | 配置文件 |
| 实现入口类 | 2h | STSAIAdvisorMod.java |
| 实现基础 UI 面板 | 2h | RecommendationPanel.java |
| 测试加载 | 1h | 验证通过 |

### Milestone 2: 状态捕获 (Day 3-4)
**完成标准**: 能正确捕获并序列化战斗状态

| 任务 | 预估 | 产出 |
|------|------|------|
| 实现数据模型 | 2h | model/* |
| 实现玩家状态捕获 | 2h | capturePlayerState() |
| 实现手牌捕获 | 2h | captureHand() |
| 实现敌人状态捕获 | 2h | captureEnemies() |
| 单元测试 | 2h | *Test.java |

### Milestone 3: LLM 集成 (Day 5-6)
**完成标准**: 能成功调用 Claude API 并获取建议

| 任务 | 预估 | 产出 |
|------|------|------|
| 实现 LLMClient 接口 | 1h | LLMClient.java |
| 实现 Claude 客户端 | 3h | ClaudeClient.java |
| 实现 Prompt 构建 | 2h | PromptBuilder.java |
| 实现响应解析 | 1h | ResponseParser.java |
| 异步调用机制 | 2h | AsyncExecutor.java |
| 测试 API 调用 | 2h | 验证通过 |

### Milestone 4: UI 完善 (Day 7)
**完成标准**: UI 能正确显示 AI 建议

| 任务 | 预估 | 产出 |
|------|------|------|
| 完善面板渲染 | 3h | RecommendationPanel.java |
| 添加快捷键支持 | 1h | KeyInputListener.java |
| 实现配置面板 | 2h | ConfigPanel.java |
| UI 测试 | 1h | 验证通过 |

### Milestone 5: 集成测试 (Day 8-10)
**完成标准**: 完整战斗流程可用

| 任务 | 预估 | 产出 |
|------|------|------|
| 端到端测试 | 4h | 测试报告 |
| Bug 修复 | 4h | 代码修复 |
| 性能优化 | 2h | 优化代码 |
| 文档完善 | 2h | README.md |

---

## 十、确认的技术决策

| 决策项 | 决策内容 |
|--------|----------|
| API Key 管理 | 用户在 Mod 配置界面自行配置 |
| 开发经验 | 用户有 Java 开发经验 |
| 首个 Demo 范围 | 最小功能 - 仅战斗出牌建议 |
| 技术栈 | Java 8 + Gradle + BaseMod + OkHttp + Gson |
| LLM 服务 | Claude API（备选 GPT-4o） |

---

## 十一、参考资源

### 官方文档
- [ModTheSpire Wiki](https://github.com/kiooeht/ModTheSpire/wiki)
- [BaseMod Wiki](https://github.com/daviscook477/BaseMod/wiki)
- [StSLib](https://github.com/kiooeht/StSLib)
- [LibGDX 文档](https://libgdx.com/wiki/)

### API 文档
- [Claude API 文档](https://docs.anthropic.com/)
- [OpenAI API 文档](https://platform.openai.com/docs)

### 社区资源
- [杀戮尖塔 Modding Discord](https://discord.gg/SlayTheSpire)
- [STS Modding Subreddit](https://www.reddit.com/r/slaythespiremodding/)

### 参考项目
- [STS Sample Mod](https://github.com/gogo82101/STS-Sample-Mod)
- [CommunicationMod](https://github.com/ForgotteDWr/CoolerSlayTheSpire) - 外部通信示例