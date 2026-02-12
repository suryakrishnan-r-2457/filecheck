package com.dlp.discovery.detection;

import com.dlp.discovery.policy.RuleBundle;

import java.util.List;

public interface DetectionEngine {
    
    List<Finding> scan(String text, ScanContext context);
    
    void reloadRules(RuleBundle newRules);
    
    String getVersion();
}
