# Changelog

所有重要的项目变更都将记录在此文件中。

## [0.5.0] - 2026-03-13

### 重大更新：Electron Overlay 独立窗口

将 UI 渲染从游戏内嵌面板迁移到独立的 Electron 悬浮窗口，彻底解耦 UI 层与游戏逻辑。

#### 架构变更

```
┌─────────────────────────────────────────────────────────────┐
│                 Slay the Spire (游戏进程)                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  STS AI Advisor Mod (精简版)                         │   │
│  │  - 状态捕获、LLM 调用                                 │   │
│  │  - HTTP Client → localhost:17532                    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP (localhost:17532)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Electron Overlay (独立进程)                     │
│  - 透明置顶窗口                                             │
│  - 接收并渲染推荐内容                                       │
│  - 可拖拽移动                                               │
└─────────────────────────────────────────────────────────────┘
```

#### 优势

| 特性 | 说明 |
|------|------|
| **解耦** | UI 与游戏逻辑完全分离，无事件冲突 |
| **跨游戏复用** | Overlay 可适配其他游戏 |
| **调试便捷** | UI 和 Mod 可独立开发调试 |
| **技术栈自由** | UI 可用任何技术实现 |

#### 新增文件

**Mod 端**:
| 文件 | 用途 |
|------|------|
| `overlay/OverlayClient.java` | HTTP 客户端，推送数据到 Overlay |

**Electron Overlay** (`overlay/`):
| 文件 | 用途 |
|------|------|
| `package.json` | 项目配置 |
| `main.js` | 主进程：窗口创建、HTTP 服务器 |
| `preload.js` | 安全 API 桥接 |
| `src/index.html` | 页面结构 |
| `src/styles.css` | 样式 |
| `src/renderer.js` | 渲染逻辑 |

#### API 接口

| 端点 | 说明 |
|------|------|
| `POST /update` | 更新推荐内容 |
| `POST /loading` | 显示加载状态 |
| `POST /clear` | 清空面板内容 |
| `POST /show` | 显示窗口 |
| `POST /hide` | 隐藏窗口 |
| `GET /status` | 返回运行状态 |

#### 移除的功能

- 移除游戏内嵌 `RecommendationPanel` 渲染
- 移除 `receivePostRender()` 中的面板绘制逻辑

#### Bug 修复

- **正则解析修复**：AdvisorAgent 的出牌建议正则现在支持可选的理由部分
- **HTTP 编码修复**：所有 HTTP 响应头添加 `charset=utf-8`，解决中文乱码
- **Skill 文件加载**：修复 skills 目录为空时 SkillAgent 不调用 LLM 的问题

#### Skill 文件补充

新增示例 skill 文件：
- `skills/battle/ironclad-strength.md` - 铁甲战士力量流
- `skills/battle/ironclad-exhaust.md` - 铁甲战士消耗流
- `skills/battle/enemy-cultist.md` - 邪教徒应对策略
- `skills/reward/ironclad-priority.md` - 铁甲战士选牌优先级

---

## [0.4.0] - 2026-03-12

### 架构重构：场景驱动的通用Agent系统

将原有硬编码的3-Agent架构重构为**场景驱动**的通用系统，大幅降低新场景扩展成本。

#### 设计目标

- Agent与状态采集解耦，变成场景无关的通用能力
- 新场景扩展只需：数据采集 + 配置分支 + skill文件，无需新建Agent类
- 统一的上下文模型，所有场景共用

#### 架构对比

**旧架构（场景耦合）**：
```
BattleContext → ViewAgent(战斗专用) + SkillAgent → AdvisorAgent(战斗专用)
```

**新架构（场景驱动）**：
```
┌─────────────────────────────────────────────────────────────┐
│                    Scene Layer (场景层)                      │
│  BattleScene / RewardScene / ShopScene / EventScene         │
│                           │                                 │
│                           ▼                                 │
│            SceneContext (统一上下文模型)                      │
│              - scenario: "battle" / "reward" / ...          │
│              - player, deck, relics                         │
│              - sceneData: Map<String, Object>               │
└──────────────────────────┼──────────────────────────────────┘
                           │
┌──────────────────────────┼──────────────────────────────────┐
│                    Agent Layer (通用Agent层)                 │
│                          │                                  │
│  AnalysisAgent ──────────┼─ 内部根据 scenario 分支           │
│  SkillAgent ─────────────┼─ 内部根据 scenario 检索skill      │
│  AdvisorAgent ───────────┼─ 内部根据 scenario 输出建议       │
└─────────────────────────────────────────────────────────────┘
```

#### 扩展新场景成本

| 步骤 | 内容 | 代码量 |
|------|------|--------|
| 1 | 添加场景枚举值 | 1行 |
| 2 | 场景数据采集类 | ~50行 |
| 3 | Agent内添加场景分支 | 每Agent ~30行 |
| 4 | 添加skill文件 | 0行代码 |

#### 新增模型

| 文件 | 用途 |
|------|------|
| `model/SceneContext.java` | 统一场景上下文（替代BattleContext作为通用输入） |
| `model/AnalysisResult.java` | AnalysisAgent统一输出 |
| `model/SceneRecommendation.java` | AdvisorAgent统一输出 |

#### 改造的文件

| 文件 | 变更 |
|------|------|
| `agent/ViewAgent.java` | 重命名为`AnalysisAgent`，内部场景分支 |
| `agent/AdvisorAgent.java` | 内部添加场景分支，输出统一模型 |
| `agent/MultiAgentOrchestrator.java` | 接收SceneContext，场景无关调度 |
| `capture/BattleStateCapture.java` | 输出SceneContext |

#### 新增场景支持

**RewardScene（卡牌奖励选择）**：
- 数据采集：`capture/RewardSceneCapture.java`
- AnalysisAgent分支：牌组流派分析、短板识别
- SkillAgent检索：流派发展、卡牌优先级
- AdvisorAgent输出：选牌优先级、跳过建议
- skill文件：`skills/reward/*.md`

#### Skill文件结构

```
skills/
├── battle/              # 战斗场景
│   ├── archetype-*.md   # 流派战术
│   └── enemy-*.md       # 敌人对策
├── reward/              # 奖励场景（新增）
│   ├── card-priority-ironclad.md
│   ├── card-priority-silent.md
│   ├── card-priority-defect.md
│   ├── card-priority-watcher.md
│   └── deck-strategy.md
└── shop/                # 商店场景（预留）
```

**RewardEventListener**：
- 监听卡牌奖励界面打开
- 自动触发选牌建议分析
- 支持手动请求

**AdvisorAgentPromptBuilder**：
- 支持battle和reward两种场景的System Prompt
- Reward场景包含流派分析、牌组统计、可选卡牌信息

**AdvisorAgent解析逻辑**：
- Battle场景：解析出牌顺序
- Reward场景：解析推荐/备选卡牌、跳过建议

---

## [0.3.1] - 2026-03-12

### SkillAgent架构重构 & 体验优化

#### SkillAgent重新设计

将SkillAgent从"直接LLM生成"改为"本地知识库检索 + LLM提炼"模式：

```
BattleContext → SkillManager.selectRelevantSkills()
              → 读取最多3个skill内容
              → LLM提炼关键信息(max_tokens: 256)
              → TacticalSkills输出
```

**新增文件**：
| 文件 | 用途 |
|------|------|
| `knowledge/SkillManager.java` | 加载skill md文件，解析元数据，筛选相关skill |
| `mods/sts-ai-advisor/skills/*.md` | 9个skill文件（角色流派 + 敌人对策） |

**Skill文件格式**：
```markdown
# 技能名称
## 元数据
- 角色: XXX
- 核心卡牌: XXX
- 适用敌人: XXX
## 关键战术
...
```

**Skill文件清单**：
- `ironclad-strength.md` - 铁甲战士力量流
- `ironclad-exhaust.md` - 铁甲战士消耗流
- `ironclad-block.md` - 铁甲战士格挡流
- `silent-poison.md` - 静默猎人毒流
- `silent-shiv.md` - 静默猎人刀刃流
- `defect-orbs.md` - 故障机器人球流
- `watcher-stance.md` - 观者姿态流
- `enemy-slime.md` - 史莱姆对策
- `enemy-gremlin-nob.md` - 哥布林首领对策

#### 输出Token优化

所有Agent改用简洁文本格式，大幅减少输出token：
- ViewAgent: max_tokens 2048 → 256
- SkillAgent: max_tokens 256（新设计）
- AdvisorAgent: max_tokens 1024 → 512

**文本格式示例**：
```
【出牌顺序】
[4] 剑柄打击 -> 酸液史莱姆：9伤击杀
[2] 打击 -> 酸液史莱姆：补刀

【策略】先清小怪，再处理大怪

【提示】这回合稳了！
```

#### 目标显示优化

- `CardPlaySuggestion` 新增 `targetName` 字段
- 面板显示目标名称而非索引：`1. 剑柄打击 → 酸液史莱姆：9伤击杀`
- 支持同名敌人区分（带序号：酸液史莱姆(1)、酸液史莱姆(2)）

#### 修改的文件

| 文件 | 变更 |
|------|------|
| `model/CardPlaySuggestion.java` | 新增targetName字段 |
| `agent/SkillAgent.java` | 完全重构，使用SkillManager |
| `agent/AdvisorAgent.java` | 解析时设置targetName |
| `agent/ViewAgent.java` | 简化输出格式 |
| `agent/MultiAgentOrchestrator.java` | 移除DeckArchetypeAnalyzer依赖 |
| `ui/RecommendationPanel.java` | 显示目标名称 |

---

## [0.3.0] - 2026-03-11

### 架构重大更新：3-Agent 多智能体系统

本次更新将原有的单次LLM调用架构升级为**3-Agent并行协作架构**，大幅提升了战斗分析的深度和质量。

#### 架构概述

```
┌─────────────────────────────────────────────────────────────────┐
│                    Multi-Agent Orchestrator                      │
│                                                                  │
│   BattleContext ──┬─────────────────────────────┐               │
│                   │                             │               │
│                   ▼                             ▼               │
│           ┌───────────────┐           ┌───────────────┐        │
│           │  ViewAgent    │           │  SkillAgent   │        │
│           │  (状态理解)    │           │  (战术技能)    │        │
│           └───────┬───────┘           └───────┬───────┘        │
│                   │    ←── 并行执行 ──→       │               │
│                   └──────────────┬────────────┘               │
│                                  ▼                             │
│                      ┌───────────────────────┐                 │
│                      │    AdvisorAgent       │                 │
│                      │    (决策顾问)          │                 │
│                      └───────────────────────┘                 │
│                                  │                             │
│                                  ▼                             │
│                      FinalRecommendation                       │
└─────────────────────────────────────────────────────────────────┘
```

#### Agent职责分工

**1. ViewAgent (状态理解Agent)**
- 读取完整游戏状态，生成结构化局势总结
- 判断局势紧急程度：LOW / MEDIUM / HIGH / CRITICAL
- 识别关键焦点（需要关注的核心问题）
- 评估威胁和机会

**2. SkillAgent (战术技能Agent)**
- 根据角色、牌组构筑、遗物检索相关战术
- 提供宏观对局维度的战术规划
- 从本地知识库检索战术建议
- **独立于ViewAgent执行，不依赖其输出**

**3. AdvisorAgent (决策顾问Agent)**
- 整合ViewAgent的状态理解和SkillAgent的战术建议
- 根据局势紧急程度调整分析深度
- 针对当前决策问题给出具体出牌建议
- 提供决策理由和潜在风险提示

#### 新增文件

**模型类** (`model/`):
| 文件 | 用途 |
|------|------|
| `ViewState.java` | ViewAgent输出，包含局势总结和紧急程度 |
| `ThreatAssessment.java` | 威胁评估（预计伤害、生存风险） |
| `OpportunityAssessment.java` | 机会评估（致死伤害、可击杀判断） |
| `TacticalSkills.java` | SkillAgent输出，战术列表 |
| `TacticalSkill.java` | 单个战术（名称、优先级、执行方式） |
| `SkillRequest.java` | SkillAgent输入模型 |
| `DeckArchetype.java` | 牌组流派分析结果 |
| `AdvisorRequest.java` | AdvisorAgent输入模型 |
| `DecisionQuestion.java` | 决策问题类型 |
| `FinalRecommendation.java` | 最终建议（含风险提示和备选方案） |
| `RiskWarning.java` | 风险提示模型 |
| `AlternativePlan.java` | 备选方案模型 |

**Agent实现** (`agent/`):
| 文件 | 用途 |
|------|------|
| `Agent.java` | 通用Agent接口 |
| `ViewAgent.java` | 状态理解Agent实现 |
| `SkillAgent.java` | 战术技能Agent实现 |
| `AdvisorAgent.java` | 决策顾问Agent实现 |
| `MultiAgentOrchestrator.java` | 多Agent并行编排器 |

**支持类**:
| 文件 | 用途 |
|------|------|
| `analysis/DeckArchetypeAnalyzer.java` | 牌组流派分析（力量流、消耗流等） |
| `knowledge/KnowledgeBase.java` | 本地知识库加载器 |
| `llm/ViewAgentPromptBuilder.java` | ViewAgent提示词构建 |
| `llm/SkillAgentPromptBuilder.java` | SkillAgent提示词构建 |
| `llm/AdvisorAgentPromptBuilder.java` | AdvisorAgent提示词构建 |

**知识库文件** (`mods/sts-ai-advisor/knowledge/`):
| 文件 | 内容 |
|------|------|
| `characters/ironclad.json` | 铁甲战士战术（力量流、消耗流、格挡流、狂暴流） |
| `characters/silent.json` | 静默猎人战术（毒流、刀刃流、弃牌流） |
| `characters/defect.json` | 故障机器人战术（球流、能力流、集中流） |
| `characters/watcher.json` | 观者战术（姿态流、真言流、压力流） |
| `enemies/common.json` | 普通敌人对策 |
| `enemies/elites.json` | 精英敌人对策 |

#### 性能优化

- **并行执行**：ViewAgent和SkillAgent并行启动，使用`CompletableFuture.allOf()`等待
- **延迟优化**：总延迟约为单Agent的2倍（而非3倍），相比串行节省约30-40%时间
- **预期总延迟**：2-4秒

#### 牌组流派分析

新增`DeckArchetypeAnalyzer`支持自动识别牌组流派：

**铁甲战士**：
- 力量流 (Flex, Inflame, Limit Break...)
- 消耗流 (Feel No Pain, Corruption...)
- 格挡流 (Body Slam, Barricade...)
- 狂暴流 (Rampage, Double Tap...)

**静默猎人**：
- 毒流 (Catalyst, Noxious Fumes...)
- 刀刃流 (Blade Dance, Accuracy...)
- 弃牌流 (Tactician, Reflex...)

**故障机器人**：
- 球流 (Ball Lightning, Glacier...)
- 能力流 (Defragment, Capacitor...)
- 集中流

**观者**：
- 姿态流 (Rushdown, Flurry of Blows...)
- 真言流 (Prostrate, Prayer...)
- 压力流 (Pressure Points...)

#### 修改的文件

| 文件 | 变更 |
|------|------|
| `BattleEventListener.java` | 使用`MultiAgentOrchestrator`替代单次LLM调用 |
| `EventManager.java` | 接收`orchestrator`参数 |
| `STSAIAdvisorMod.java` | 初始化多Agent系统 |

---

## [0.2.0] - 2026-03-10

### 新增功能

#### 控制台日志编码修复
- **问题**：游戏日志中出现中文乱码
- **修复**：在 `STSAIAdvisorMod.initialize()` 中设置 `System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()))`

#### 手牌信息捕获时机优化
- **问题**：分析请求时手牌数为0，因为请求时机过早
- **修复**：使用 `PostDrawSubscriber` 监听每张牌的抽取事件，等待1秒无新抽牌后再发起分析请求
- 新增 `receivePostDraw()` 方法记录最后抽牌时间
- 新增 `receivePostUpdate()` 检测抽牌完成时机

#### 热键检测机制重构
- **问题**：F3/F4热键无响应
- **原因**：游戏使用的是扫描码(scan code)而非LibGDX键码
- **修复**：
  - 将热键常量改为扫描码：F3=246, F4=247
  - 使用 `isKeyPressed()` 配合状态跟踪替代 `isKeyJustPressed()`
  - 添加 `f3WasPressed`/`f4WasPressed` 状态变量

#### UI面板优化
- **面板尺寸**：宽度320px，高度450px
- **文本换行**：实现自动换行（28字符/行，缩进25字符/行）
- **鼠标拖拽**：支持拖动面板标题栏移动面板位置
- **滚动区域**：
  - Recommended区域：180px高度，独立滚动条
  - Strategy区域：120px高度，独立滚动条
  - 支持鼠标滚轮滚动
- **字体调整**：
  - 普通行高：18px
  - 大标题行高：24px

#### LLM深度思考禁用
- **问题**：阿里云Qwen模型默认开启深度思考，响应时间过长
- **修复**：在请求体中添加 `"enable_thinking": false`

#### 敌人意图捕获完善
- 新增 `DEBUG` 意图类型处理
- 默认返回"未知"而非null

### 技术细节

#### 扫描码映射
```
F3 -> scan code 246 (LibGDX键码61)
F4 -> scan code 247 (LibGDX键码62)
```

#### 坐标系统
- 游戏使用Y轴从顶部开始的坐标系
- LibGDX ShapeRenderer使用Y轴从底部开始
- 转换公式：`renderY = Gdx.graphics.getHeight() - screenY - height`

---

## [0.1.0] - 2026-03-10

### 新增功能

#### LLM请求日志记录
- 新增 `src/main/java/com/stsaiadvisor/util/LLMLogger.java`
- 所有LLM API请求和响应现在会记录到独立日志文件
- 日志文件位置：`mods/sts-ai-advisor/logs/llm_requests.log`
- 记录内容包括：
  - 提供商名称、API URL、模型名称
  - 系统提示词 (System Prompt)
  - 用户提示词 (User Prompt)
  - 请求体 (Request Body，格式化JSON)
  - 响应体 (Response Body，格式化JSON)
  - 请求耗时和成功状态

#### 中文化支持
- 所有提示词和UI文本已翻译为中文
- `PromptBuilder.java` - 系统提示词和战斗状态描述
- `EnemyIntent.java` - 敌人意图描述（攻击、防御、增益、减益、睡眠、眩晕、逃跑）
- `BattleEventListener.java` - 状态消息提示
- `RecommendationPanel.java` - 面板UI标签

### Bug修复

#### 热键功能修复
- **问题**：F3/F4热键无响应
- **原因**：`EventManager.java` 未正确注册到 BaseMod 事件系统
- **修复**：
  - 在 `EventManager` 构造函数中添加 `BaseMod.subscribe(this)`
  - 将 `battleEventListener` 和 `keyInputListener` 从静态变量改为实例变量

#### 面板显示问题修复
- **问题**：面板遮挡游戏主菜单
- **修复**：
  - 面板默认隐藏 (`visible = false`)
  - 面板位置调整到屏幕右上角
  - 仅在战斗开始时自动显示

#### Java版本兼容性修复
- **问题**：`UnsupportedClassVersionError` (class file version 61.0)
- **原因**：游戏运行在 Java 8 环境
- **修复**：
  - `build.gradle` 中设置 `sourceCompatibility` 和 `targetCompatibility` 为 `VERSION_1_8`
  - 移除 Java 17+ 特性（文本块、`var` 关键字等）

#### Mod加载问题修复
- **问题**：Mod无法加载，提示找不到 `initialize()` 方法
- **修复**：
  - 添加 `@SpireInitializer` 注解到主类
  - 添加静态 `initialize()` 方法
  - 创建 SPI 配置文件 `META-INF/services/com.evacipated.cardcrawl.modthespire.Mod`

#### 空指针异常修复
- **问题**：`BattleStateCapture.isInBattle()` 抛出 `NullPointerException`
- **修复**：添加 try-catch 包装，安全检查所有可能为 null 的对象

### 技术细节

#### 热键映射
- F3 (键码 61)：手动请求AI建议
- F4 (键码 62)：切换面板显示/隐藏

#### API配置
- 支持 OpenAI 兼容的 API（如阿里云 DashScope）
- 配置文件位置：`mods/sts-ai-advisor/config.json`
- 配置项：
  - `apiKey`: API密钥
  - `baseUrl`: API基础URL
  - `model`: 模型名称
  - `apiProvider`: API提供商 (`openai` / `anthropic`)

#### 项目结构
```
src/main/java/com/stsaiadvisor/
├── STSAIAdvisorMod.java      # 主入口类
├── capture/
│   └── BattleStateCapture.java  # 战斗状态捕获
├── config/
│   └── ModConfig.java           # 配置管理
├── event/
│   ├── EventManager.java        # 事件管理器
│   └── BattleEventListener.java # 战斗事件监听
├── llm/
│   ├── LLMClient.java           # LLM客户端接口
│   ├── OpenAIClient.java        # OpenAI实现
│   ├── PromptBuilder.java       # 提示词构建
│   └── ResponseParser.java      # 响应解析
├── model/
│   ├── BattleContext.java       # 战斗上下文
│   ├── CardState.java           # 卡牌状态
│   ├── EnemyIntent.java         # 敌人意图
│   ├── EnemyState.java          # 敌人状态
│   ├── PlayerState.java         # 玩家状态
│   ├── Recommendation.java      # AI建议
│   └── CardPlaySuggestion.java  # 出牌建议
├── ui/
│   ├── RecommendationPanel.java # 建议面板UI
│   └── KeyInputListener.java    # 热键监听
└── util/
    ├── AsyncExecutor.java       # 异步执行器
    ├── Constants.java           # 常量定义
    └── LLMLogger.java           # LLM日志记录器
```

---

## 待办事项

- [ ] **[优先] 鼠标事件监听修复**：面板拖拽和滚轮滚动不生效，怀疑游戏内图层接管了鼠标事件
- [ ] **[优先] 手牌特殊效果描述**：将卡牌的特殊效果、关键词等信息补充到LLM请求中
- [ ] 添加Boss战术知识库 (`knowledge/enemies/bosses.json`)
- [ ] 添加卡牌协同知识库 (`knowledge/synergies/card_combos.json`)
- [ ] 支持更多 LLM 提供商
- [ ] 添加设置界面，支持游戏内配置
- [ ] 优化提示词，提高建议质量
- [ ] 添加卡牌中文名称映射
- [ ] 支持自定义热键配置
- [ ] UI面板支持显示风险提示和备选方案