package com.stsaiadvisor.model;

/**
 * Represents opportunity assessment from ViewAgent.
 */
public class OpportunityAssessment {
    private int lethalDamage;
    private boolean canKillThisTurn;
    private String primaryOpportunity;

    public OpportunityAssessment() {}

    // Getters and Setters
    public int getLethalDamage() { return lethalDamage; }
    public void setLethalDamage(int lethalDamage) { this.lethalDamage = lethalDamage; }

    public boolean isCanKillThisTurn() { return canKillThisTurn; }
    public void setCanKillThisTurn(boolean canKillThisTurn) { this.canKillThisTurn = canKillThisTurn; }

    public String getPrimaryOpportunity() { return primaryOpportunity; }
    public void setPrimaryOpportunity(String primaryOpportunity) { this.primaryOpportunity = primaryOpportunity; }
}