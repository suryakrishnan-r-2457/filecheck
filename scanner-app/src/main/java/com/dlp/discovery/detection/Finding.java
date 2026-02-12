package com.dlp.discovery.detection;

import java.util.Objects;

public class Finding {
    
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    private String ruleId;
    private String ruleName;
    private Severity severity;
    private String category;
    private float confidence;
    private String redactedSnippet;
    private int offset;
    
    public Finding() {
    }
    
    public Finding(String ruleId, String ruleName, Severity severity, String category, 
                   float confidence, String redactedSnippet, int offset) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.severity = severity;
        this.category = category;
        this.confidence = confidence;
        this.redactedSnippet = redactedSnippet;
        this.offset = offset;
    }
    
    public String getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public Severity getSeverity() {
        return severity;
    }
    
    public void setSeverity(Severity severity) {
        this.severity = severity;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public float getConfidence() {
        return confidence;
    }
    
    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }
    
    public String getRedactedSnippet() {
        return redactedSnippet;
    }
    
    public void setRedactedSnippet(String redactedSnippet) {
        this.redactedSnippet = redactedSnippet;
    }
    
    public int getOffset() {
        return offset;
    }
    
    public void setOffset(int offset) {
        this.offset = offset;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Finding finding = (Finding) o;
        return Float.compare(finding.confidence, confidence) == 0 &&
               offset == finding.offset &&
               Objects.equals(ruleId, finding.ruleId) &&
               Objects.equals(ruleName, finding.ruleName) &&
               severity == finding.severity &&
               Objects.equals(category, finding.category) &&
               Objects.equals(redactedSnippet, finding.redactedSnippet);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ruleId, ruleName, severity, category, confidence, redactedSnippet, offset);
    }
    
    @Override
    public String toString() {
        return "Finding{" +
               "ruleId='" + ruleId + '\'' +
               ", ruleName='" + ruleName + '\'' +
               ", severity=" + severity +
               ", category='" + category + '\'' +
               ", confidence=" + confidence +
               ", redactedSnippet='" + redactedSnippet + '\'' +
               ", offset=" + offset +
               '}';
    }
}
