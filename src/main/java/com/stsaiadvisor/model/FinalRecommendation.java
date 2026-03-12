package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the final recommendation from AdvisorAgent.
 */
public class FinalRecommendation {
    private List<CardPlaySuggestion> suggestions;
    private String reasoning;
    private String companionMessage;
    private RiskWarning risks;
    private AlternativePlan alternative;

    public FinalRecommendation() {}

    // Getters and Setters
    public List<CardPlaySuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<CardPlaySuggestion> suggestions) { this.suggestions = suggestions; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getCompanionMessage() { return companionMessage; }
    public void setCompanionMessage(String companionMessage) { this.companionMessage = companionMessage; }

    public RiskWarning getRisks() { return risks; }
    public void setRisks(RiskWarning risks) { this.risks = risks; }

    public AlternativePlan getAlternative() { return alternative; }
    public void setAlternative(AlternativePlan alternative) { this.alternative = alternative; }

    public boolean hasSuggestions() {
        return suggestions != null && !suggestions.isEmpty();
    }

    /**
     * Convert to legacy Recommendation format for backward compatibility.
     */
    public Recommendation toRecommendation() {
        Recommendation rec = new Recommendation();
        rec.setSuggestions(suggestions);
        rec.setReasoning(reasoning);
        rec.setCompanionMessage(companionMessage);
        return rec;
    }
}