package com.dlp.discovery.detection;

import com.dlp.discovery.policy.RuleBundle;
import com.google.re2j.Pattern;
import com.google.re2j.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RegexDetectionEngine implements DetectionEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(RegexDetectionEngine.class);
    private static final int SNIPPET_CONTEXT_LENGTH = 30;
    private static final char REDACTION_CHAR = '*';
    
    private volatile RuleBundle ruleBundle;
    private volatile List<CompiledRule> compiledRules;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public RegexDetectionEngine(RuleBundle ruleBundle) {
        if (ruleBundle == null) {
            throw new IllegalArgumentException("RuleBundle cannot be null");
        }
        this.ruleBundle = ruleBundle;
        this.compiledRules = compileRules(ruleBundle);
        logger.info("RegexDetectionEngine initialized with version {}, {} rules", 
                    ruleBundle.getVersion(), compiledRules.size());
    }
    
    @Override
    public List<Finding> scan(String text, ScanContext context) {
        if (text == null || text.isEmpty()) {
            logger.debug("Scan called with null or empty text");
            return new ArrayList<>();
        }
        
        List<Finding> findings = new ArrayList<>();
        List<CompiledRule> currentRules;
        
        lock.readLock().lock();
        try {
            currentRules = compiledRules;
        } finally {
            lock.readLock().unlock();
        }
        
        for (CompiledRule compiledRule : currentRules) {
            try {
                findings.addAll(scanWithRule(text, compiledRule));
            } catch (Exception e) {
                logger.error("Error scanning with rule {}: {}", compiledRule.rule.getId(), e.getMessage(), e);
            }
        }
        
        logger.debug("Scan completed: {} findings from {} rules", findings.size(), currentRules.size());
        return findings;
    }
    
    @Override
    public void reloadRules(RuleBundle newRules) {
        if (newRules == null) {
            throw new IllegalArgumentException("RuleBundle cannot be null");
        }
        
        logger.info("Reloading rules from version {} to version {}", 
                    ruleBundle.getVersion(), newRules.getVersion());
        
        List<CompiledRule> newCompiledRules = compileRules(newRules);
        
        lock.writeLock().lock();
        try {
            this.ruleBundle = newRules;
            this.compiledRules = newCompiledRules;
        } finally {
            lock.writeLock().unlock();
        }
        
        logger.info("Rules reloaded successfully: {} rules loaded", newCompiledRules.size());
    }
    
    @Override
    public String getVersion() {
        return ruleBundle.getVersion();
    }
    
    private List<CompiledRule> compileRules(RuleBundle bundle) {
        List<CompiledRule> compiled = new ArrayList<>();
        
        for (RuleBundle.Rule rule : bundle.getRules()) {
            try {
                Pattern pattern = Pattern.compile(rule.getPattern());
                compiled.add(new CompiledRule(rule, pattern));
                logger.debug("Compiled rule {}: {}", rule.getId(), rule.getName());
            } catch (Exception e) {
                logger.error("Failed to compile pattern for rule {}: {}", rule.getId(), e.getMessage(), e);
            }
        }
        
        logger.info("Compiled {} out of {} rules", compiled.size(), bundle.getRules().size());
        return compiled;
    }
    
    private List<Finding> scanWithRule(String text, CompiledRule compiledRule) {
        List<Finding> findings = new ArrayList<>();
        Matcher matcher = compiledRule.pattern.matcher(text);
        
        while (matcher.find()) {
            int offset = matcher.start();
            String matchedText = matcher.group();
            
            if (!applyValidation(compiledRule.rule, text, offset, matchedText)) {
                logger.debug("Match at offset {} failed validation for rule {}", 
                            offset, compiledRule.rule.getId());
                continue;
            }
            
            float confidence = calculateConfidence(compiledRule.rule, matchedText);
            String redactedSnippet = createRedactedSnippet(text, offset, matchedText.length());
            
            Finding finding = new Finding(
                compiledRule.rule.getId(),
                compiledRule.rule.getName(),
                compiledRule.rule.getSeverity(),
                compiledRule.rule.getCategory(),
                confidence,
                redactedSnippet,
                offset
            );
            
            findings.add(finding);
            logger.debug("Created finding for rule {} at offset {}", compiledRule.rule.getId(), offset);
        }
        
        return findings;
    }
    
    private boolean applyValidation(RuleBundle.Rule rule, String text, int offset, String matchedText) {
        if (rule.isContextRequired()) {
            if (!Validator.validateContext(text, offset, matchedText.length())) {
                return false;
            }
        }
        
        String validatorType = rule.getValidator();
        if (validatorType == null || validatorType.isEmpty()) {
            return true;
        }
        
        switch (validatorType.toUpperCase()) {
            case "LUHN":
                return Validator.validateLuhn(matchedText);
            case "SSN":
                return Validator.validateSSN(matchedText);
            default:
                logger.warn("Unknown validator type: {}", validatorType);
                return true;
        }
    }
    
    private float calculateConfidence(RuleBundle.Rule rule, String matchedText) {
        float baseConfidence = rule.getConfidence();
        
        if (matchedText.length() < 5) {
            baseConfidence *= 0.7f;
        }
        
        return Math.min(1.0f, Math.max(0.0f, baseConfidence));
    }
    
    private String createRedactedSnippet(String text, int offset, int matchLength) {
        int start = Math.max(0, offset - SNIPPET_CONTEXT_LENGTH);
        int end = Math.min(text.length(), offset + matchLength + SNIPPET_CONTEXT_LENGTH);
        
        StringBuilder snippet = new StringBuilder();
        
        if (start > 0) {
            snippet.append("...");
        }
        
        snippet.append(text, start, offset);
        
        for (int i = 0; i < matchLength; i++) {
            snippet.append(REDACTION_CHAR);
        }
        
        snippet.append(text, offset + matchLength, end);
        
        if (end < text.length()) {
            snippet.append("...");
        }
        
        return snippet.toString();
    }
    
    private static class CompiledRule {
        final RuleBundle.Rule rule;
        final Pattern pattern;
        
        CompiledRule(RuleBundle.Rule rule, Pattern pattern) {
            this.rule = rule;
            this.pattern = pattern;
        }
    }
}
