package com.dlp.discovery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads scanner configuration from YAML file with support for:
 * - Environment variable substitution (${ENV_VAR})
 * - System property substitution (${property.name})
 * - Default configuration fallback
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final String DEFAULT_CONFIG_PATH = "config/scanner-config.yaml";
    
    /**
     * Load scanner configuration from default location or classpath with fallback to defaults
     */
    public static ScannerConfig load() {
        return load(DEFAULT_CONFIG_PATH);
    }
    
    /**
     * Load scanner configuration from specified path with fallback to defaults
     * 
     * @param configPath Path to YAML configuration file
     * @return Loaded and processed ScannerConfig
     */
    public static ScannerConfig load(String configPath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        
        try {
            String yamlContent = loadYamlContent(configPath);
            String processedContent = substituteVariables(yamlContent);
            
            ScannerConfig config = mapper.readValue(processedContent, ScannerConfig.class);
            logger.info("Configuration loaded successfully from: {}", configPath);
            return config;
            
        } catch (IOException e) {
            logger.warn("Failed to load configuration from {}: {}. Using defaults.", 
                       configPath, e.getMessage());
            return createDefaultConfig();
        }
    }
    
    /**
     * Load YAML content from file system or classpath
     */
    private static String loadYamlContent(String configPath) throws IOException {
        Path filePath = Paths.get(configPath);
        
        if (Files.exists(filePath)) {
            logger.debug("Loading configuration from file: {}", filePath);
            return Files.readString(filePath);
        }
        
        try (InputStream inputStream = ConfigLoader.class.getClassLoader()
                .getResourceAsStream(configPath)) {
            if (inputStream != null) {
                logger.debug("Loading configuration from classpath: {}", configPath);
                return new String(inputStream.readAllBytes());
            }
        }
        
        throw new IOException("Configuration file not found: " + configPath);
    }
    
    /**
     * Substitute environment variables and system properties in YAML content
     * Supports formats: ${VAR_NAME}, ${property.name}
     */
    private static String substituteVariables(String content) {
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            String variable = matcher.group(1);
            String replacement = resolveVariable(variable);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Resolve a variable by checking system properties first, then environment variables
     */
    private static String resolveVariable(String variable) {
        String systemProperty = System.getProperty(variable);
        if (systemProperty != null) {
            logger.debug("Resolved system property {}: {}", variable, systemProperty);
            return systemProperty;
        }
        
        String envVar = System.getenv(variable);
        if (envVar != null) {
            logger.debug("Resolved environment variable {}: {}", variable, envVar);
            return envVar;
        }
        
        String upperCaseEnv = System.getenv(variable.toUpperCase().replace('.', '_'));
        if (upperCaseEnv != null) {
            logger.debug("Resolved environment variable {} (uppercase): {}", 
                        variable, upperCaseEnv);
            return upperCaseEnv;
        }
        
        logger.warn("Variable {} not found, leaving as-is", variable);
        return "${" + variable + "}";
    }
    
    /**
     * Create default configuration when file loading fails
     */
    private static ScannerConfig createDefaultConfig() {
        ScannerConfig config = new ScannerConfig();
        ScannerConfig.ScannerSettings scanner = new ScannerConfig.ScannerSettings();
        
        scanner.setTargets(Arrays.asList(System.getProperty("user.home")));
        scanner.setExcludePaths(Arrays.asList(
            "**/.git/**",
            "**/node_modules/**",
            "**/.cache/**"
        ));
        scanner.setIncludeExtensions(Arrays.asList(
            ".txt", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".csv"
        ));
        scanner.setMaxFileSizeBytes(100 * 1024 * 1024); // 100MB
        scanner.setFullScanCronExpression("0 0 2 * * ?");
        scanner.setIncrementalScanIntervalHours(4);
        scanner.setIdleOnlyScanning(false);
        scanner.setIdleThresholdMinutes(5);
        scanner.setMaxFilesPerSecond(50);
        scanner.setBusinessHoursCpuPercent(20);
        scanner.setOffHoursCpuPercent(50);
        
        ScannerConfig.ParserSettings parser = new ScannerConfig.ParserSettings();
        parser.setTimeoutSeconds(30);
        
        ScannerConfig.PdfSettings pdf = new ScannerConfig.PdfSettings();
        pdf.setMaxPages(1000);
        pdf.setMaxStreamSizeMB(50);
        parser.setPdf(pdf);
        
        ScannerConfig.OoxmlSettings ooxml = new ScannerConfig.OoxmlSettings();
        ooxml.setMaxEntries(10000);
        ooxml.setDisableEntityExpansion(true);
        ooxml.setDisableDTD(true);
        parser.setOoxml(ooxml);
        
        ScannerConfig.ZipSettings zip = new ScannerConfig.ZipSettings();
        zip.setMaxDepth(10);
        zip.setMaxEntries(10000);
        zip.setMaxRatio(100);
        zip.setMaxCumulativeSizeMB(1024);
        parser.setZip(zip);
        
        ScannerConfig.Ole2Settings ole2 = new ScannerConfig.Ole2Settings();
        ole2.setMaxStreams(1000);
        ole2.setSkipMacros(false);
        parser.setOle2(ole2);
        
        ScannerConfig.EmlSettings eml = new ScannerConfig.EmlSettings();
        eml.setMaxMIMEParts(100);
        eml.setMaxRecursionDepth(10);
        parser.setEml(eml);
        
        scanner.setParser(parser);
        
        ScannerConfig.CacheSettings cache = new ScannerConfig.CacheSettings();
        String tempDir = System.getProperty("java.io.tmpdir", "/tmp");
        cache.setPath(tempDir + "/dlp-scanner/cache");
        cache.setMaxEntries(100000);
        scanner.setCache(cache);
        
        ScannerConfig.ResultsSettings results = new ScannerConfig.ResultsSettings();
        results.setDbPath(tempDir + "/dlp-scanner/results.db");
        results.setMaxSizeMB(500);
        results.setUploadFullText(false);
        scanner.setResults(results);
        
        ScannerConfig.TelemetrySettings telemetry = new ScannerConfig.TelemetrySettings();
        telemetry.setDbPath(tempDir + "/dlp-scanner/telemetry.db");
        telemetry.setMaxSizeMB(100);
        scanner.setTelemetry(telemetry);
        
        ScannerConfig.ManagementServerSettings mgmt = new ScannerConfig.ManagementServerSettings();
        mgmt.setUrl("https://api.example.com");
        mgmt.setUploadBatchSize(100);
        mgmt.setUploadIntervalSeconds(300);
        mgmt.setApiKeyEnvVar("DLP_API_KEY");
        scanner.setManagementServer(mgmt);
        
        ScannerConfig.RulesSettings rules = new ScannerConfig.RulesSettings();
        rules.setPath("rules/detection-rules.yaml");
        rules.setAutoReload(true);
        rules.setSignatureVerification(false);
        scanner.setRules(rules);
        
        config.setScanner(scanner);
        
        logger.info("Created default configuration");
        return config;
    }
}
