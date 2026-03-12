package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the AI's recommendation for the current battle.
 */
public class Recommendation {
    private List<CardPlaySuggestion> suggestions;
    private String reasoning;
    private String companionMessage;

    public Recommendation() {}

    // Getters and Setters
    public List<CardPlaySuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<CardPlaySuggestion> suggestions) { this.suggestions = suggestions; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getCompanionMessage() { return companionMessage; }
    public void setCompanionMessage(String companionMessage) { this.companionMessage = companionMessage; }

    public boolean hasSuggestions() {
        return suggestions != null && !suggestions.isEmpty();
    }
}