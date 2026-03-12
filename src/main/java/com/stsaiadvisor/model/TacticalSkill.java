package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents a single tactical skill from SkillAgent.
 */
public class TacticalSkill {
    private String name;
    private String description;
    private SkillPriority priority;
    private List<String> relatedCards;
    private String execution;

    public TacticalSkill() {}

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SkillPriority getPriority() { return priority; }
    public void setPriority(SkillPriority priority) { this.priority = priority; }

    public List<String> getRelatedCards() { return relatedCards; }
    public void setRelatedCards(List<String> relatedCards) { this.relatedCards = relatedCards; }

    public String getExecution() { return execution; }
    public void setExecution(String execution) { this.execution = execution; }

    /**
     * Priority level for tactical skills.
     */
    public enum SkillPriority {
        CORE,      // Core tactic, must execute
        IMPORTANT, // Important tactic, prioritize
        OPTIONAL   // Optional tactic, execute if opportunity arises
    }
}