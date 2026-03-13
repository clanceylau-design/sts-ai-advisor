package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents tactical skills output from SkillAgent.
 */
public class TacticalSkills {
    private List<TacticalSkill> skills;
    private String deckStrategy;
    private List<String> priorityTargets;

    /** SkillAgent的原始输出，直接传递给AdvisorAgent */
    private String rawOutput;

    public TacticalSkills() {}

    // Getters and Setters
    public List<TacticalSkill> getSkills() { return skills; }
    public void setSkills(List<TacticalSkill> skills) { this.skills = skills; }

    public String getDeckStrategy() { return deckStrategy; }
    public void setDeckStrategy(String deckStrategy) { this.deckStrategy = deckStrategy; }

    public List<String> getPriorityTargets() { return priorityTargets; }
    public void setPriorityTargets(List<String> priorityTargets) { this.priorityTargets = priorityTargets; }

    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }
}