package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents tactical skills output from SkillAgent.
 */
public class TacticalSkills {
    private List<TacticalSkill> skills;
    private String deckStrategy;
    private List<String> priorityTargets;

    public TacticalSkills() {}

    // Getters and Setters
    public List<TacticalSkill> getSkills() { return skills; }
    public void setSkills(List<TacticalSkill> skills) { this.skills = skills; }

    public String getDeckStrategy() { return deckStrategy; }
    public void setDeckStrategy(String deckStrategy) { this.deckStrategy = deckStrategy; }

    public List<String> getPriorityTargets() { return priorityTargets; }
    public void setPriorityTargets(List<String> priorityTargets) { this.priorityTargets = priorityTargets; }
}