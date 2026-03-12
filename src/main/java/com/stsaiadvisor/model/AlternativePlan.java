package com.stsaiadvisor.model;

/**
 * Represents alternative plan in FinalRecommendation.
 */
public class AlternativePlan {
    private String condition;
    private String action;

    public AlternativePlan() {}

    // Getters and Setters
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}