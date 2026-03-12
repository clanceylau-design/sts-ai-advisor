package com.stsaiadvisor.model;

/**
 * Represents threat assessment from ViewAgent.
 */
public class ThreatAssessment {
    private int incomingDamage;
    private int survivalRisk; // 0-100%
    private String primaryThreat;

    public ThreatAssessment() {}

    // Getters and Setters
    public int getIncomingDamage() { return incomingDamage; }
    public void setIncomingDamage(int incomingDamage) { this.incomingDamage = incomingDamage; }

    public int getSurvivalRisk() { return survivalRisk; }
    public void setSurvivalRisk(int survivalRisk) { this.survivalRisk = survivalRisk; }

    public String getPrimaryThreat() { return primaryThreat; }
    public void setPrimaryThreat(String primaryThreat) { this.primaryThreat = primaryThreat; }
}