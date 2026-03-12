package com.stsaiadvisor.agent;

import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SceneOrchestrator - 场景编排器
 *
 * <p>职责：
 * <ul>
 *   <li>根据场景类型调度Agent执行</li>
 *   <li>支持battle和reward场景</li>
 *   <li>并行执行AnalysisAgent和SkillAgent</li>
 * </ul>
 *
 * <p>架构：
 * <pre>
 *                     SceneContext
 *                          │
 *            ┌─────────────┴─────────────┐
 *            │                           │
 *            ▼                           ▼
 *     ┌───────────────┐          ┌───────────────┐
 *     │ AnalysisAgent │          │  SkillAgent   │
 *     │  (场景分析)    │          │  (战术检索)    │
 *     └───────┬───────┘          └───────┬───────┘
 *             │    ←── 并行执行 ──→       │
 *             └──────────────┬────────────┘
 *                            │
 *                            ▼
 *                ┌───────────────────────┐
 *                │    AdvisorAgent       │
 *                │    (场景建议)          │
 *                └───────────────────────┘
 *                            │
 *                            ▼
 *                  SceneRecommendation
 * </pre>
 *
 * @see SceneContext
 * @see SceneRecommendation
 */
public class SceneOrchestrator {

    /** AnalysisAgent - 场景分析Agent */
    private final AnalysisAgent analysisAgent;

    /** SkillAgent - 战术技能Agent */
    private final SkillAgent skillAgent;

    /** AdvisorAgent - 顾问Agent */
    private final AdvisorAgent advisorAgent;

    /**
     * 构造函数
     *
     * @param config Mod配置对象
     */
    public SceneOrchestrator(ModConfig config) {
        this.analysisAgent = new AnalysisAgent(config);
        this.skillAgent = new SkillAgent(config);
        this.advisorAgent = new AdvisorAgent(config);
    }

    /**
     * 异步处理场景上下文
     *
     * @param context 场景上下文
     * @return CompletableFuture<SceneRecommendation> 异步结果
     */
    public CompletableFuture<SceneRecommendation> processAsync(SceneContext context) {
        long startTime = System.currentTimeMillis();
        String scenario = context.getScenario();

        System.out.println("[SceneOrchestrator] Processing scenario: " + scenario);

        // 准备SkillRequest
        SkillRequest skillRequest = createSkillRequest(context);

        // 并行启动AnalysisAgent和SkillAgent
        CompletableFuture<AnalysisResult> analysisFuture = analysisAgent.process(context);
        CompletableFuture<TacticalSkills> skillFuture = skillAgent.process(skillRequest);

        // 等待两者完成后启动AdvisorAgent
        return CompletableFuture.allOf(analysisFuture, skillFuture)
            .thenApply(v -> {
                try {
                    AnalysisResult analysisResult = analysisFuture.get();
                    TacticalSkills skills = skillFuture.get();

                    // 创建AdvisorRequest
                    AdvisorRequest advisorRequest = new AdvisorRequest();

                    // 根据场景设置不同的分析结果
                    if ("battle".equals(scenario)) {
                        advisorRequest.setViewState(analysisResult.toViewState());
                        advisorRequest.setContext(toBattleContext(context));
                    } else {
                        // reward场景使用analysisResult作为sceneData
                        advisorRequest.setSceneData("analysisResult", analysisResult);
                        advisorRequest.setSceneContext(context);
                    }

                    // 设置技能和决策问题
                    advisorRequest.setSkills(skills);
                    advisorRequest.setQuestion(createDecisionQuestion(scenario));

                    return advisorRequest;
                } catch (Exception e) {
                    System.err.println("[SceneOrchestrator] Error preparing request: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            })
            .thenCompose(advisorRequest -> advisorAgent.process(advisorRequest))
            .thenApply(SceneRecommendation::fromFinalRecommendation)
            .whenComplete((result, error) -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("[SceneOrchestrator] Total: " + elapsed + "ms");
                if (error != null) {
                    System.err.println("[SceneOrchestrator] Error: " + error.getMessage());
                }
            });
    }

    /**
     * 创建SkillRequest
     */
    private SkillRequest createSkillRequest(SceneContext context) {
        SkillRequest request = new SkillRequest();

        // 角色信息
        if (context.getPlayer() != null) {
            request.setCharacterClass(context.getPlayer().getCharacterClass());
        }

        // 层数
        request.setAct(context.getAct());

        // 合并牌组（用于流派分析）
        List<CardState> fullDeck = new ArrayList<>();
        if (context.getDeck() != null) fullDeck.addAll(context.getDeck());
        if ("battle".equals(context.getScenario()) && context.getHand() != null) {
            fullDeck.addAll(context.getHand());
        }
        request.setFullDeck(fullDeck);

        // 遗物
        request.setRelics(context.getRelics());

        // 敌人（战斗场景）
        if ("battle".equals(context.getScenario())) {
            request.setEnemies(context.getEnemies());
        }

        // 场景类型
        request.setScenario(context.getScenario());

        return request;
    }

    /**
     * 创建决策问题
     */
    private DecisionQuestion createDecisionQuestion(String scenario) {
        switch (scenario) {
            case "battle":
                return new DecisionQuestion("CARD_PLAY", "本回合最优出牌顺序");
            case "reward":
                return new DecisionQuestion("CARD_REWARD", "选择哪张卡牌加入牌组");
            default:
                return new DecisionQuestion("UNKNOWN", "未知决策");
        }
    }

    /**
     * 创建错误建议
     */
    private SceneRecommendation createErrorRecommendation(String scenario, String error) {
        SceneRecommendation rec = new SceneRecommendation();
        rec.setScenario(scenario);
        rec.setReasoning("分析出错: " + error);
        rec.setCompanionMessage("抱歉，分析出现问题。");
        return rec;
    }

    // ========== 兼容旧接口 ==========

    /**
     * 兼容旧接口：处理BattleContext
     */
    public CompletableFuture<FinalRecommendation> processAsyncLegacy(BattleContext context) {
        SceneContext sceneContext = toSceneContext(context);
        return processAsync(sceneContext).thenApply(SceneRecommendation::toFinalRecommendation);
    }

    /**
     * BattleContext转SceneContext
     */
    private SceneContext toSceneContext(BattleContext bc) {
        if (bc == null) return null;
        SceneContext sc = new SceneContext();
        sc.setScenario(bc.getScenario());
        sc.setPlayer(bc.getPlayer());
        sc.setHand(bc.getHand());
        sc.setDrawPile(bc.getDrawPile());
        sc.setDiscardPile(bc.getDiscardPile());
        sc.setEnemies(bc.getEnemies());
        sc.setRelics(bc.getRelics());
        sc.setTurn(bc.getTurn());
        sc.setAct(bc.getAct());
        return sc;
    }

    /**
     * SceneContext转BattleContext
     */
    private BattleContext toBattleContext(SceneContext sc) {
        if (sc == null) return null;
        BattleContext bc = new BattleContext();
        bc.setScenario(sc.getScenario());
        bc.setPlayer(sc.getPlayer());
        bc.setHand(sc.getHand());
        bc.setDrawPile(sc.getDrawPile());
        bc.setDiscardPile(sc.getDiscardPile());
        bc.setEnemies(sc.getEnemies());
        bc.setRelics(sc.getRelics());
        bc.setTurn(sc.getTurn());
        bc.setAct(sc.getAct());
        return bc;
    }
}