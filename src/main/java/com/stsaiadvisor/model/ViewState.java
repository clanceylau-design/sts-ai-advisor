package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the view state output from ViewAgent.
 * Contains situation analysis and urgency assessment.
 */
public class ViewState {
    private String situationSummary;
    private UrgencyLevel urgencyLevel;
    private List<String> keyFocus;
    private ThreatAssessment threats;
    private OpportunityAssessment opportunities;

    public ViewState() {}

    // Getters and Setters
    public String getSituationSummary() { return situationSummary; }
    public void setSituationSummary(String situationSummary) { this.situationSummary = situationSummary; }

    public UrgencyLevel getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(UrgencyLevel urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public List<String> getKeyFocus() { return keyFocus; }
    public void setKeyFocus(List<String> keyFocus) { this.keyFocus = keyFocus; }

    public ThreatAssessment getThreats() { return threats; }
    public void setThreats(ThreatAssessment threats) { this.threats = threats; }

    public OpportunityAssessment getOpportunities() { return opportunities; }
    public void setOpportunities(OpportunityAssessment opportunities) { this.opportunities = opportunities; }

    /**
     * Urgency level enum for situation assessment.
     */
    public enum UrgencyLevel {
        LOW,       // Safe situation, can plan long-term
        MEDIUM,    // Normal situation, regular decisions needed
        HIGH,      // Dangerous situation, need careful calculation
        CRITICAL   // Survival crisis, survival first
    }
}