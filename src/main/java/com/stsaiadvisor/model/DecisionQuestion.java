package com.stsaiadvisor.model;

/**
 * Represents the decision question for AdvisorAgent.
 */
public class DecisionQuestion {
    private String type; // "CARD_PLAY" | "TARGET_SELECTION" | "ENERGY_MANAGEMENT"
    private String description;

    public DecisionQuestion() {}

    public DecisionQuestion(String type, String description) {
        this.type = type;
        this.description = description;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}