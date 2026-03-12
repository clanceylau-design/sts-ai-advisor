# 项目记忆

## STS AI Advisor - 杀戮尖塔AI助手Mod

### 项目概述
一个杀戮尖塔(Slay the Spire)的ModTheSpire模组，使用LLM API（Claude/GPT-4等）为玩家提供实时战斗建议。

### 关键路径

#### 游戏安装目录
- **游戏根目录**: `D:\SteamLibrary\steamapps\common\SlayTheSpire`
- **Mod目录**: `D:\SteamLibrary\steamapps\common\SlayTheSpire\mods`
- **创意工坊目录**: `D:\SteamLibrary\steamapps\workshop\content\646570`

#### 项目目录
- **项目根目录**: `C:\Users\Ali\sts-ai-advisor`
- **源代码**: `C:\Users\Ali\sts-ai-advisor\src\main\java\com\stsaiadvisor\`
- **配置文件**: `D:\SteamLibrary\steamapps\common\SlayTheSpire\mods\sts-ai-advisor\config.json`
- **日志文件**: `D:\SteamLibrary\steamapps\common\SlayTheSpire\mods\sts-ai-advisor\logs\llm_requests.log`
- **Skills目录**: `D:\SteamLibrary\steamapps\common\SlayTheSpire\mods\sts-ai-advisor\skills\`
- **GitHub仓库**: https://github.com/clanceylau-design/sts-ai-advisor

### 构建与部署

```bash
# 构建
cd C:/Users/Ali/sts-ai-advisor
./gradlew clean jar

# 部署
cp build/libs/sts-ai-advisor.jar D:/SteamLibrary/steamapps/common/SlayTheSpire/mods/sts-ai-advisor.jar
```

### 重要技术细节

#### Java版本
- **必须使用 Java 8**，游戏运行在Java 8环境
- `build.gradle` 已配置 `sourceCompatibility` 和 `targetCompatibility` 为 `VERSION_1_8`
- 不能使用 Java 11+ 特性（文本块、var关键字、record等）

#### Mod加载机制
- 使用 `@SpireInitializer` 注解
- 必须有静态 `initialize()` 方法
- SPI配置文件: `META-INF/services/com.evacipated.cardcrawl.modthespire.Mod`

#### BaseMod事件订阅
- 必须调用 `BaseMod.subscribe(this)` 注册监听器
- `EventManager` 实现了 `PostUpdateSubscriber`
- 每帧调用 `receivePostUpdate()` 检查热键和战斗状态

#### 热键
- F3 (键码61): 请求AI建议
- F4 (键码62): 切换面板显示

#### API配置
当前使用阿里云DashScope API（OpenAI兼容格式）:
```json
{
  "apiKey": "sk-xxx",
  "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "model": "qwen-plus",
  "apiProvider": "openai"
}
```

### 已解决的坑

1. **Java版本不兼容**: 游戏用Java 8，项目最初用Java 17编译导致`UnsupportedClassVersionError`
2. **Mod无法加载**: 缺少`@SpireInitializer`和静态`initialize()`方法
3. **热键无响应**: `EventManager`没有调用`BaseMod.subscribe(this)`，且使用了静态变量
4. **面板遮挡菜单**: 面板默认显示且位置居中，改为默认隐藏+右上角定位
5. **空指针异常**: `isInBattle()`检查时部分对象可能为null，需要try-catch
6. **JSON解析类型错误**: LLM返回JsonArray但代码期望JsonObject，需用`isJsonObject()`/`isJsonArray()`检查
7. **输出token过多**: 响应时间过长(39s)，改用简洁文本格式+减少max_tokens解决
8. **卡牌描述占位符**: `!B!`、`!D!`等动态数值占位符未替换，需在提示词中额外显示实际数值属性
9. **Reward场景检测**: 使用`AbstractDungeon.screen == CurrentScreen.CARD_REWARD`而非`cardRewardScreen.isOpen`

### 项目状态
- v0.4.0: 场景驱动架构重构（已完成）
  - 新增SceneContext统一模型
  - AnalysisAgent替代ViewAgent，支持场景分支
  - SceneOrchestrator替代MultiAgentOrchestrator
  - SkillManager支持子目录和场景过滤
  - 新增RewardSceneCapture用于卡牌奖励场景
  - skill文件按场景分类：battle/、reward/
  - RewardEventListener监听卡牌奖励界面
  - 面板支持多场景显示，行距38px，25字符换行
- 已发布到GitHub: https://github.com/clanceylau-design/sts-ai-advisor

### 待办事项

1. **深度优化提示面板文字排版**
   - 优化中英文混排显示
   - 增强视觉层次（标题/正文/强调）
   - 优化滚动体验

2. **优化奖励skills策略和配置便捷性**
   - 完善各角色选牌优先级skill文件
   - 支持用户自定义skill文件路径
   - 添加skill热重载功能

3. **优化LLM调用速度，考虑流式输出**
   - 实现SSE流式响应解析
   - 边生成边显示，提升用户体验
   - 考虑并行调用优化

### 代码注释规范

本项目的代码注释必须遵循以下规范：

#### 类级别注释
每个类必须有Javadoc注释，包含：
- 类的职责说明
- 输入输出说明（如果是处理类）
- 执行方式（同步/异步，依赖关系）
- 相关类的`@see`引用

示例：
```java
/**
 * ViewAgent - 状态理解Agent
 *
 * <p>职责：
 * <ul>
 *   <li>读取完整游戏状态，生成结构化局势总结</li>
 *   <li>判断当前局势紧急程度（LOW/MEDIUM/HIGH/CRITICAL）</li>
 * </ul>
 *
 * <p>输入：BattleContext（完整战斗状态）
 * <p>输出：ViewState（局势分析结果）
 *
 * <p>执行方式：与SkillAgent并行执行，互不依赖
 *
 * @see ViewState
 * @see BattleContext
 */
```

#### 方法级别注释
每个public方法必须有Javadoc注释，包含：
- 方法功能描述
- 参数说明（`@param`）
- 返回值说明（`@return`）
- 可能的异常（`@throws`）

示例：
```java
/**
 * 异步处理战斗状态，返回ViewState
 *
 * <p>实现Agent接口的process方法，通过AsyncExecutor提交到线程池执行
 *
 * @param context 战斗上下文，包含玩家、手牌、敌人等信息
 * @return CompletableFuture<ViewState> 异步结果
 */
```

#### 复杂逻辑注释
对于复杂的业务逻辑，必须在代码块上方添加注释说明：
- 为什么这样做
- 处理流程是什么

示例：
```java
// Step 1: 分析牌组流派（同步操作，通常<10ms）
// 优先使用抽牌堆分析流派，因为手牌可能不完整
DeckArchetype archetype = archetypeAnalyzer.analyze(
    context.getDrawPile() != null ? context.getDrawPile() : context.getHand(),
    context.getPlayer() != null ? context.getPlayer().getCharacterClass() : null
);
```

#### JSON格式注释
对于LLM期望返回的JSON格式，使用`<pre>`标签展示示例：

```java
/**
 * 期望的JSON格式：
 * <pre>
 * {
 *   "suggestions": [
 *     { "cardIndex": 0, "cardName": "Strike", "priority": 1 }
 *   ],
 *   "reasoning": "策略说明"
 * }
 * </pre>
 */
```

#### 注释语言
- 类和方法注释：**必须使用中文**
- 代码行内注释：**必须使用中文**
- 变量名/方法名：使用英文

### SkillAgent设计模式

SkillAgent采用"本地知识库检索 + LLM提炼"模式：

```
BattleContext → SkillManager.selectRelevantSkills() → 读取skill内容 → LLM提炼 → TacticalSkills
```

**Skill文件位置**: `mods/sts-ai-advisor/skills/*.md`

**Skill文件格式**:
```markdown
# 技能名称
## 元数据
- 角色: XXX
- 核心卡牌: XXX
- 适用敌人: XXX
- 类型: archetype/enemy/synergy
## 关键战术
...
```

**SkillManager职责**:
- `loadMetadata()`: 扫描skills目录，解析所有md文件的元数据
- `selectRelevantSkills()`: 根据角色/卡牌/敌人筛选相关skill
- `getSkillContent()`: 读取完整skill内容

**SkillAgent流程**:
1. 从SkillRequest提取角色、卡牌名、敌人名
2. 调用SkillManager筛选最多3个相关skill
3. 用LLM提炼关键信息（max_tokens: 256）
4. 输出TacticalSkills给AdvisorAgent

### 场景驱动架构设计

**核心思路**：Agent变成场景无关的通用能力，场景只负责数据采集和结果展示

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

**扩展新场景步骤**：
1. 添加场景枚举值
2. 场景数据采集类（~50行）
3. Agent内添加场景分支（每Agent ~30行）
4. 添加skill文件（0行代码，仅md文件）