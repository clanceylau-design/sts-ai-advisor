package com.stsaiadvisor.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the input request for AdvisorAgent.
 *
 * <p>支持多场景输入，通过sceneData承载场景特定数据
 */
public class AdvisorRequest {
    private ViewState viewState;
    private TacticalSkills skills;
    private BattleContext context;  // 兼容旧代码
    private DecisionQuestion question;

    /** 场景上下文（新架构） */
    private SceneContext sceneContext;

    /** 场景特定数据 */
    private Map<String, Object> sceneData;

    public AdvisorRequest() {
        this.sceneData = new HashMap<>();
    }

    // Getters and Setters
    public ViewState getViewState() { return viewState; }
    public void setViewState(ViewState viewState) { this.viewState = viewState; }

    public TacticalSkills getSkills() { return skills; }
    public void setSkills(TacticalSkills skills) { this.skills = skills; }

    public BattleContext getContext() { return context; }
    public void setContext(BattleContext context) { this.context = context; }

    public DecisionQuestion getQuestion() { return question; }
    public void setQuestion(DecisionQuestion question) { this.question = question; }

    public SceneContext getSceneContext() { return sceneContext; }
    public void setSceneContext(SceneContext sceneContext) { this.sceneContext = sceneContext; }

    public Map<String, Object> getSceneData() {
        if (sceneData == null) sceneData = new HashMap<>();
        return sceneData;
    }

    @SuppressWarnings("unchecked")
    public <T> T getSceneData(String key) {
        return (T) getSceneData().get(key);
    }

    public void setSceneData(String key, Object value) {
        getSceneData().put(key, value);
    }
}