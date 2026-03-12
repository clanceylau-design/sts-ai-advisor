package com.stsaiadvisor.agent;

import com.stsaiadvisor.config.ModConfig;
import com.stsaiadvisor.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * MultiAgentOrchestrator - 多Agent编排器
 *
 * <p>负责协调ViewAgent、SkillAgent和AdvisorAgent的执行流程。
 *
 * <p>架构设计：
 * <pre>
 *                     BattleContext
 *                          │
 *            ┌─────────────┴─────────────┐
 *            │                           │
 *            ▼                           ▼
 *     ┌───────────────┐          ┌───────────────┐
 *     │  ViewAgent    │          │  SkillAgent   │
 *     │  (状态理解)    │          │  (战术检索)    │
 *     └───────┬───────┘          └───────┬───────┘
 *             │    ←── 并行执行 ──→       │
 *             └──────────────┬────────────┘
 *                            │
 *                            ▼
 *                ┌───────────────────────┐
 *                │    AdvisorAgent       │
 *                │    (决策顾问)          │
 *                └───────────────────────┘
 *                            │
 *                            ▼
 *                  FinalRecommendation
 * </pre>
 *
 * @see ViewAgent
 * @see SkillAgent
 * @see AdvisorAgent
 */
public class MultiAgentOrchestrator {

    /** ViewAgent - 状态理解Agent */
    private final ViewAgent viewAgent;

    /** SkillAgent - 战术技能Agent */
    private final SkillAgent skillAgent;

    /** AdvisorAgent - 决策顾问Agent */
    private final AdvisorAgent advisorAgent;

    /**
     * 构造函数
     *
     * @param config Mod配置对象
     */
    public MultiAgentOrchestrator(ModConfig config) {
        this.viewAgent = new ViewAgent(config);
        this.skillAgent = new SkillAgent(config);
        this.advisorAgent = new AdvisorAgent(config);
    }

    /**
     * 异步处理战斗上下文，返回最终建议
     *
     * @param context 战斗上下文
     * @return CompletableFuture<FinalRecommendation> 异步结果
     */
    public CompletableFuture<FinalRecommendation> processAsync(BattleContext context) {
        long startTime = System.currentTimeMillis();

        // 准备SkillRequest
        SkillRequest skillRequest = createSkillRequest(context);

        // 并行启动ViewAgent和SkillAgent
        CompletableFuture<ViewState> viewFuture = viewAgent.process(context);
        CompletableFuture<TacticalSkills> skillFuture = skillAgent.process(skillRequest);

        // 等待两者完成后启动AdvisorAgent
        return CompletableFuture.allOf(viewFuture, skillFuture)
            .thenCompose(v -> {
                try {
                    ViewState viewState = viewFuture.get();
                    TacticalSkills skills = skillFuture.get();

                    AdvisorRequest advisorRequest = new AdvisorRequest();
                    advisorRequest.setViewState(viewState);
                    advisorRequest.setSkills(skills);
                    advisorRequest.setContext(context);
                    advisorRequest.setQuestion(new DecisionQuestion("CARD_PLAY", "本回合最优出牌顺序"));

                    return advisorAgent.process(advisorRequest);
                } catch (Exception e) {
                    System.err.println("[Orchestrator] Error: " + e.getMessage());
                    return CompletableFuture.completedFuture(createErrorRecommendation(e.getMessage()));
                }
            })
            .whenComplete((result, error) -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("[Orchestrator] Total: " + elapsed + "ms");
                if (error != null) {
                    System.err.println("[Orchestrator] Error: " + error.getMessage());
                }
            });
    }

    /**
     * 兼容旧接口
     */
    public CompletableFuture<Recommendation> processAsyncLegacy(BattleContext context) {
        return processAsync(context).thenApply(FinalRecommendation::toRecommendation);
    }

    /**
     * 创建SkillRequest
     */
    private SkillRequest createSkillRequest(BattleContext context) {
        SkillRequest request = new SkillRequest();

        if (context.getPlayer() != null) {
            request.setCharacterClass(context.getPlayer().getCharacterClass());
        }
        request.setAct(context.getAct());

        // 合并所有牌堆
        List<CardState> fullDeck = new ArrayList<>();
        if (context.getDrawPile() != null) fullDeck.addAll(context.getDrawPile());
        if (context.getHand() != null) fullDeck.addAll(context.getHand());
        if (context.getDiscardPile() != null) fullDeck.addAll(context.getDiscardPile());
        request.setFullDeck(fullDeck);

        request.setRelics(context.getRelics());
        request.setEnemies(context.getEnemies());

        return request;
    }

    /**
     * 创建错误建议
     */
    private FinalRecommendation createErrorRecommendation(String error) {
        FinalRecommendation rec = new FinalRecommendation();
        rec.setReasoning("分析出错: " + error);
        rec.setCompanionMessage("抱歉，分析出现问题。");
        rec.setSuggestions(new ArrayList<>());
        return rec;
    }
}