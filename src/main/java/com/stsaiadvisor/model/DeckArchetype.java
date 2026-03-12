package com.stsaiadvisor.model;

import java.util.List;

/**
 * Represents the deck archetype analysis result.
 */
public class DeckArchetype {
    private String primaryArchetype;
    private List<String> secondaryArchetypes;
    private List<String> archetypeTags;
    private int archetypeStrength; // 0-100

    public DeckArchetype() {}

    // Getters and Setters
    public String getPrimaryArchetype() { return primaryArchetype; }
    public void setPrimaryArchetype(String primaryArchetype) { this.primaryArchetype = primaryArchetype; }

    public List<String> getSecondaryArchetypes() { return secondaryArchetypes; }
    public void setSecondaryArchetypes(List<String> secondaryArchetypes) { this.secondaryArchetypes = secondaryArchetypes; }

    public List<String> getArchetypeTags() { return archetypeTags; }
    public void setArchetypeTags(List<String> archetypeTags) { this.archetypeTags = archetypeTags; }

    public int getArchetypeStrength() { return archetypeStrength; }
    public void setArchetypeStrength(int archetypeStrength) { this.archetypeStrength = archetypeStrength; }
}