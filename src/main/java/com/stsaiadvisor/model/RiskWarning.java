package com.stsaiadvisor.model;

/**
 * Represents risk warning in FinalRecommendation.
 */
public class RiskWarning {
    private String risk;
    private int riskLevel; // 1-5
    private String mitigation;

    public RiskWarning() {}

    // Getters and Setters
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }

    public int getRiskLevel() { return riskLevel; }
    public void setRiskLevel(int riskLevel) { this.riskLevel = riskLevel; }

    public String getMitigation() { return mitigation; }
    public void setMitigation(String mitigation) { this.mitigation = mitigation; }
}