package com.dlp.discovery.policy;

import com.dlp.discovery.detection.Finding;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RuleBundle {
    
    private final String version;
    private final List<Rule> rules;
    private final Instant signatureTimestamp;
    
    @JsonCreator
    public RuleBundle(
            @JsonProperty("version") String version,
            @JsonProperty("rules") List<Rule> rules,
            @JsonProperty("signatureTimestamp") Instant signatureTimestamp) {
        this.version = version;
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
        this.signatureTimestamp = signatureTimestamp;
    }
    
    private RuleBundle(Builder builder) {
        this.version = builder.version;
        this.rules = builder.rules;
        this.signatureTimestamp = builder.signatureTimestamp;
    }
    
    public String getVersion() {
        return version;
    }
    
    public List<Rule> getRules() {
        return new ArrayList<>(rules);
    }
    
    public Instant getSignatureTimestamp() {
        return signatureTimestamp;
    }
    
    public static RuleBundle fromJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(json, RuleBundle.class);
    }
    
    public static RuleBundle fromJson(InputStream inputStream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(inputStream, RuleBundle.class);
    }
    
    public static RuleBundle fromJsonFile(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return fromJson(inputStream);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleBundle that = (RuleBundle) o;
        return Objects.equals(version, that.version) &&
               Objects.equals(rules, that.rules) &&
               Objects.equals(signatureTimestamp, that.signatureTimestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(version, rules, signatureTimestamp);
    }
    
    @Override
    public String toString() {
        return "RuleBundle{" +
               "version='" + version + '\'' +
               ", rules=" + rules +
               ", signatureTimestamp=" + signatureTimestamp +
               '}';
    }
    
    public static class Builder {
        private String version;
        private List<Rule> rules = new ArrayList<>();
        private Instant signatureTimestamp;
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder rules(List<Rule> rules) {
            this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
            return this;
        }
        
        public Builder addRule(Rule rule) {
            this.rules.add(rule);
            return this;
        }
        
        public Builder signatureTimestamp(Instant signatureTimestamp) {
            this.signatureTimestamp = signatureTimestamp;
            return this;
        }
        
        public RuleBundle build() {
            return new RuleBundle(this);
        }
    }
    
    public static class Rule {
        
        private final String id;
        private final String name;
        private final String category;
        private final Finding.Severity severity;
        private final String pattern;
        private final String validator;
        private final float confidence;
        private final boolean contextRequired;
        
        @JsonCreator
        public Rule(
                @JsonProperty("id") String id,
                @JsonProperty("name") String name,
                @JsonProperty("category") String category,
                @JsonProperty("severity") Finding.Severity severity,
                @JsonProperty("pattern") String pattern,
                @JsonProperty("validator") String validator,
                @JsonProperty("confidence") float confidence,
                @JsonProperty("contextRequired") boolean contextRequired) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.severity = severity;
            this.pattern = pattern;
            this.validator = validator;
            this.confidence = confidence;
            this.contextRequired = contextRequired;
        }
        
        private Rule(Builder builder) {
            this.id = builder.id;
            this.name = builder.name;
            this.category = builder.category;
            this.severity = builder.severity;
            this.pattern = builder.pattern;
            this.validator = builder.validator;
            this.confidence = builder.confidence;
            this.contextRequired = builder.contextRequired;
        }
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getCategory() {
            return category;
        }
        
        public Finding.Severity getSeverity() {
            return severity;
        }
        
        public String getPattern() {
            return pattern;
        }
        
        public String getValidator() {
            return validator;
        }
        
        public float getConfidence() {
            return confidence;
        }
        
        public boolean isContextRequired() {
            return contextRequired;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Rule rule = (Rule) o;
            return Float.compare(rule.confidence, confidence) == 0 &&
                   contextRequired == rule.contextRequired &&
                   Objects.equals(id, rule.id) &&
                   Objects.equals(name, rule.name) &&
                   Objects.equals(category, rule.category) &&
                   severity == rule.severity &&
                   Objects.equals(pattern, rule.pattern) &&
                   Objects.equals(validator, rule.validator);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(id, name, category, severity, pattern, validator, confidence, contextRequired);
        }
        
        @Override
        public String toString() {
            return "Rule{" +
                   "id='" + id + '\'' +
                   ", name='" + name + '\'' +
                   ", category='" + category + '\'' +
                   ", severity=" + severity +
                   ", pattern='" + pattern + '\'' +
                   ", validator='" + validator + '\'' +
                   ", confidence=" + confidence +
                   ", contextRequired=" + contextRequired +
                   '}';
        }
        
        public static class Builder {
            private String id;
            private String name;
            private String category;
            private Finding.Severity severity;
            private String pattern;
            private String validator;
            private float confidence;
            private boolean contextRequired;
            
            public Builder id(String id) {
                this.id = id;
                return this;
            }
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder category(String category) {
                this.category = category;
                return this;
            }
            
            public Builder severity(Finding.Severity severity) {
                this.severity = severity;
                return this;
            }
            
            public Builder pattern(String pattern) {
                this.pattern = pattern;
                return this;
            }
            
            public Builder validator(String validator) {
                this.validator = validator;
                return this;
            }
            
            public Builder confidence(float confidence) {
                this.confidence = confidence;
                return this;
            }
            
            public Builder contextRequired(boolean contextRequired) {
                this.contextRequired = contextRequired;
                return this;
            }
            
            public Rule build() {
                return new Rule(this);
            }
        }
    }
}
