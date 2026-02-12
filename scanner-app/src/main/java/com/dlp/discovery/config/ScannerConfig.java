package com.dlp.discovery.config;

import java.util.List;
import java.util.Map;

/**
 * Scanner configuration POJO
 */
public class ScannerConfig {
    private ScannerSettings scanner;

    public ScannerSettings getScanner() {
        return scanner;
    }

    public void setScanner(ScannerSettings scanner) {
        this.scanner = scanner;
    }

    public static class ScannerSettings {
        private List<String> targets;
        private List<String> excludePaths;
        private List<String> includeExtensions;
        private long maxFileSizeBytes;
        private String fullScanCronExpression;
        private int incrementalScanIntervalHours;
        private boolean idleOnlyScanning;
        private int idleThresholdMinutes;
        private int maxFilesPerSecond;
        private int businessHoursCpuPercent;
        private int offHoursCpuPercent;
        private ParserSettings parser;
        private CacheSettings cache;
        private ResultsSettings results;
        private TelemetrySettings telemetry;
        private ManagementServerSettings managementServer;
        private RulesSettings rules;

        // Getters and setters
        public List<String> getTargets() { return targets; }
        public void setTargets(List<String> targets) { this.targets = targets; }

        public List<String> getExcludePaths() { return excludePaths; }
        public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }

        public List<String> getIncludeExtensions() { return includeExtensions; }
        public void setIncludeExtensions(List<String> includeExtensions) { this.includeExtensions = includeExtensions; }

        public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
        public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

        public String getFullScanCronExpression() { return fullScanCronExpression; }
        public void setFullScanCronExpression(String fullScanCronExpression) { this.fullScanCronExpression = fullScanCronExpression; }

        public int getIncrementalScanIntervalHours() { return incrementalScanIntervalHours; }
        public void setIncrementalScanIntervalHours(int incrementalScanIntervalHours) { this.incrementalScanIntervalHours = incrementalScanIntervalHours; }

        public boolean isIdleOnlyScanning() { return idleOnlyScanning; }
        public void setIdleOnlyScanning(boolean idleOnlyScanning) { this.idleOnlyScanning = idleOnlyScanning; }

        public int getIdleThresholdMinutes() { return idleThresholdMinutes; }
        public void setIdleThresholdMinutes(int idleThresholdMinutes) { this.idleThresholdMinutes = idleThresholdMinutes; }

        public int getMaxFilesPerSecond() { return maxFilesPerSecond; }
        public void setMaxFilesPerSecond(int maxFilesPerSecond) { this.maxFilesPerSecond = maxFilesPerSecond; }

        public int getBusinessHoursCpuPercent() { return businessHoursCpuPercent; }
        public void setBusinessHoursCpuPercent(int businessHoursCpuPercent) { this.businessHoursCpuPercent = businessHoursCpuPercent; }

        public int getOffHoursCpuPercent() { return offHoursCpuPercent; }
        public void setOffHoursCpuPercent(int offHoursCpuPercent) { this.offHoursCpuPercent = offHoursCpuPercent; }

        public ParserSettings getParser() { return parser; }
        public void setParser(ParserSettings parser) { this.parser = parser; }

        public CacheSettings getCache() { return cache; }
        public void setCache(CacheSettings cache) { this.cache = cache; }

        public ResultsSettings getResults() { return results; }
        public void setResults(ResultsSettings results) { this.results = results; }

        public TelemetrySettings getTelemetry() { return telemetry; }
        public void setTelemetry(TelemetrySettings telemetry) { this.telemetry = telemetry; }

        public ManagementServerSettings getManagementServer() { return managementServer; }
        public void setManagementServer(ManagementServerSettings managementServer) { this.managementServer = managementServer; }

        public RulesSettings getRules() { return rules; }
        public void setRules(RulesSettings rules) { this.rules = rules; }
    }

    public static class ParserSettings {
        private int timeoutSeconds;
        private PdfSettings pdf;
        private OoxmlSettings ooxml;
        private ZipSettings zip;
        private Ole2Settings ole2;
        private EmlSettings eml;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public PdfSettings getPdf() { return pdf; }
        public void setPdf(PdfSettings pdf) { this.pdf = pdf; }

        public OoxmlSettings getOoxml() { return ooxml; }
        public void setOoxml(OoxmlSettings ooxml) { this.ooxml = ooxml; }

        public ZipSettings getZip() { return zip; }
        public void setZip(ZipSettings zip) { this.zip = zip; }

        public Ole2Settings getOle2() { return ole2; }
        public void setOle2(Ole2Settings ole2) { this.ole2 = ole2; }

        public EmlSettings getEml() { return eml; }
        public void setEml(EmlSettings eml) { this.eml = eml; }
    }

    public static class PdfSettings {
        private int maxPages;
        private int maxStreamSizeMB;

        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }

        public int getMaxStreamSizeMB() { return maxStreamSizeMB; }
        public void setMaxStreamSizeMB(int maxStreamSizeMB) { this.maxStreamSizeMB = maxStreamSizeMB; }
    }

    public static class OoxmlSettings {
        private int maxEntries;
        private boolean disableEntityExpansion;
        private boolean disableDTD;

        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }

        public boolean isDisableEntityExpansion() { return disableEntityExpansion; }
        public void setDisableEntityExpansion(boolean disableEntityExpansion) { this.disableEntityExpansion = disableEntityExpansion; }

        public boolean isDisableDTD() { return disableDTD; }
        public void setDisableDTD(boolean disableDTD) { this.disableDTD = disableDTD; }
    }

    public static class ZipSettings {
        private int maxDepth;
        private int maxEntries;
        private int maxRatio;
        private int maxCumulativeSizeMB;

        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }

        public int getMaxRatio() { return maxRatio; }
        public void setMaxRatio(int maxRatio) { this.maxRatio = maxRatio; }

        public int getMaxCumulativeSizeMB() { return maxCumulativeSizeMB; }
        public void setMaxCumulativeSizeMB(int maxCumulativeSizeMB) { this.maxCumulativeSizeMB = maxCumulativeSizeMB; }
    }

    public static class Ole2Settings {
        private int maxStreams;
        private boolean skipMacros;

        public int getMaxStreams() { return maxStreams; }
        public void setMaxStreams(int maxStreams) { this.maxStreams = maxStreams; }

        public boolean isSkipMacros() { return skipMacros; }
        public void setSkipMacros(boolean skipMacros) { this.skipMacros = skipMacros; }
    }

    public static class EmlSettings {
        private int maxMIMEParts;
        private int maxRecursionDepth;

        public int getMaxMIMEParts() { return maxMIMEParts; }
        public void setMaxMIMEParts(int maxMIMEParts) { this.maxMIMEParts = maxMIMEParts; }

        public int getMaxRecursionDepth() { return maxRecursionDepth; }
        public void setMaxRecursionDepth(int maxRecursionDepth) { this.maxRecursionDepth = maxRecursionDepth; }
    }

    public static class CacheSettings {
        private String path;
        private int maxEntries;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }

    public static class ResultsSettings {
        private String dbPath;
        private int maxSizeMB;
        private boolean uploadFullText;

        public String getDbPath() { return dbPath; }
        public void setDbPath(String dbPath) { this.dbPath = dbPath; }

        public int getMaxSizeMB() { return maxSizeMB; }
        public void setMaxSizeMB(int maxSizeMB) { this.maxSizeMB = maxSizeMB; }

        public boolean isUploadFullText() { return uploadFullText; }
        public void setUploadFullText(boolean uploadFullText) { this.uploadFullText = uploadFullText; }
    }

    public static class TelemetrySettings {
        private String dbPath;
        private int maxSizeMB;

        public String getDbPath() { return dbPath; }
        public void setDbPath(String dbPath) { this.dbPath = dbPath; }

        public int getMaxSizeMB() { return maxSizeMB; }
        public void setMaxSizeMB(int maxSizeMB) { this.maxSizeMB = maxSizeMB; }
    }

    public static class ManagementServerSettings {
        private String url;
        private int uploadBatchSize;
        private int uploadIntervalSeconds;
        private String apiKeyEnvVar;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getUploadBatchSize() { return uploadBatchSize; }
        public void setUploadBatchSize(int uploadBatchSize) { this.uploadBatchSize = uploadBatchSize; }

        public int getUploadIntervalSeconds() { return uploadIntervalSeconds; }
        public void setUploadIntervalSeconds(int uploadIntervalSeconds) { this.uploadIntervalSeconds = uploadIntervalSeconds; }

        public String getApiKeyEnvVar() { return apiKeyEnvVar; }
        public void setApiKeyEnvVar(String apiKeyEnvVar) { this.apiKeyEnvVar = apiKeyEnvVar; }
    }

    public static class RulesSettings {
        private String path;
        private boolean autoReload;
        private boolean signatureVerification;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public boolean isAutoReload() { return autoReload; }
        public void setAutoReload(boolean autoReload) { this.autoReload = autoReload; }

        public boolean isSignatureVerification() { return signatureVerification; }
        public void setSignatureVerification(boolean signatureVerification) { this.signatureVerification = signatureVerification; }
    }
}
