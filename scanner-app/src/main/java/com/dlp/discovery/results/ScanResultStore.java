package com.dlp.discovery.results;

import com.dlp.discovery.detection.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ScanResultStore implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(ScanResultStore.class);
    private static final String SCAN_VERSION = "1.0";
    
    private final String dbPath;
    private final long maxSizeBytes;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock;
    private Connection connection;
    
    public ScanResultStore(String dbPath) {
        this(dbPath, 500); // Default 500 MB
    }
    
    public ScanResultStore(String dbPath, int maxSizeMB) {
        this.dbPath = Objects.requireNonNull(dbPath, "dbPath must not be null");
        this.maxSizeBytes = maxSizeMB * 1024L * 1024L;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.lock = new ReentrantReadWriteLock();
        
        try {
            initializeDatabase();
            log.info("Initialized ScanResultStore at {} with max size {}MB", dbPath, maxSizeMB);
        } catch (SQLException e) {
            log.error("Failed to initialize database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }
    
    private void initializeDatabase() throws SQLException {
        Connection conn = getConnection();
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS scan_results (" +
                "  scan_id TEXT PRIMARY KEY," +
                "  file_path TEXT NOT NULL," +
                "  file_hash TEXT," +
                "  file_size INTEGER," +
                "  file_mtime INTEGER," +
                "  mime_type TEXT," +
                "  scan_timestamp INTEGER NOT NULL," +
                "  scan_version TEXT NOT NULL," +
                "  finding_count INTEGER NOT NULL," +
                "  max_severity TEXT," +
                "  parse_status TEXT NOT NULL," +
                "  findings_json TEXT," +
                "  metadata_json TEXT" +
                ")"
            );
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON scan_results(file_path)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scan_timestamp ON scan_results(scan_timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_max_severity ON scan_results(max_severity)");
            
            log.debug("Database schema initialized");
        }
    }
    
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
        }
        return connection;
    }
    
    public void store(ScanResult result) throws SQLException {
        Objects.requireNonNull(result, "result must not be null");
        
        lock.writeLock().lock();
        try {
            evictIfNeeded();
            
            String sql = 
                "INSERT OR REPLACE INTO scan_results " +
                "(scan_id, file_path, file_hash, file_size, file_mtime, mime_type, " +
                "scan_timestamp, scan_version, finding_count, max_severity, parse_status, " +
                "findings_json, metadata_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, result.getScanId());
                pstmt.setString(2, result.getFilePath());
                pstmt.setString(3, result.getFileHash());
                pstmt.setLong(4, result.getFileSize());
                pstmt.setLong(5, result.getFileMtime() != null ? result.getFileMtime().toEpochMilli() : 0);
                pstmt.setString(6, result.getMimeType());
                pstmt.setLong(7, result.getScanTimestamp().toEpochMilli());
                pstmt.setString(8, result.getScanVersion());
                pstmt.setInt(9, result.getFindingCount());
                pstmt.setString(10, result.getMaxSeverity() != null ? result.getMaxSeverity().name() : null);
                pstmt.setString(11, result.getParseStatus());
                pstmt.setString(12, serializeFindings(result.getFindings()));
                pstmt.setString(13, serializeMetadata(result.getMetadata()));
                
                pstmt.executeUpdate();
                log.debug("Stored scan result: {}", result.getScanId());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize scan result", e);
            throw new SQLException("Failed to serialize scan result", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public List<ScanResult> query(QueryParams params) throws SQLException {
        Objects.requireNonNull(params, "params must not be null");
        
        lock.readLock().lock();
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM scan_results WHERE 1=1");
            List<Object> parameters = new ArrayList<>();
            
            if (params.getStartTime() != null) {
                sql.append(" AND scan_timestamp >= ?");
                parameters.add(params.getStartTime().toEpochMilli());
            }
            
            if (params.getEndTime() != null) {
                sql.append(" AND scan_timestamp <= ?");
                parameters.add(params.getEndTime().toEpochMilli());
            }
            
            if (params.getMinSeverity() != null) {
                sql.append(" AND max_severity IN (");
                List<Finding.Severity> severities = getSeveritiesFrom(params.getMinSeverity());
                for (int i = 0; i < severities.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("?");
                    parameters.add(severities.get(i).name());
                }
                sql.append(")");
            }
            
            if (params.getFilePathPattern() != null && !params.getFilePathPattern().isEmpty()) {
                sql.append(" AND file_path LIKE ?");
                parameters.add(params.getFilePathPattern().replace("*", "%"));
            }
            
            sql.append(" ORDER BY scan_timestamp DESC");
            
            if (params.getLimit() > 0) {
                sql.append(" LIMIT ?");
                parameters.add(params.getLimit());
            }
            
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql.toString())) {
                for (int i = 0; i < parameters.size(); i++) {
                    pstmt.setObject(i + 1, parameters.get(i));
                }
                
                ResultSet rs = pstmt.executeQuery();
                List<ScanResult> results = new ArrayList<>();
                
                while (rs.next()) {
                    results.add(mapResultSet(rs));
                }
                
                log.debug("Query returned {} results", results.size());
                return results;
            }
        } catch (IOException e) {
            log.error("Failed to deserialize scan result", e);
            throw new SQLException("Failed to deserialize scan result", e);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Stats getStats() throws SQLException {
        lock.readLock().lock();
        try {
            String sql = 
                "SELECT " +
                "  COUNT(*) as total_scans, " +
                "  SUM(CASE WHEN finding_count > 0 THEN 1 ELSE 0 END) as scans_with_findings, " +
                "  SUM(finding_count) as total_findings, " +
                "  SUM(CASE WHEN max_severity = 'CRITICAL' THEN 1 ELSE 0 END) as critical_scans, " +
                "  SUM(CASE WHEN max_severity = 'HIGH' THEN 1 ELSE 0 END) as high_scans, " +
                "  SUM(CASE WHEN max_severity = 'MEDIUM' THEN 1 ELSE 0 END) as medium_scans, " +
                "  SUM(CASE WHEN max_severity = 'LOW' THEN 1 ELSE 0 END) as low_scans, " +
                "  MIN(scan_timestamp) as earliest_scan, " +
                "  MAX(scan_timestamp) as latest_scan " +
                "FROM scan_results";
            
            try (Statement stmt = getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                if (rs.next()) {
                    Stats stats = new Stats();
                    stats.setTotalScans(rs.getLong("total_scans"));
                    stats.setScansWithFindings(rs.getLong("scans_with_findings"));
                    stats.setTotalFindings(rs.getLong("total_findings"));
                    stats.setCriticalScans(rs.getLong("critical_scans"));
                    stats.setHighScans(rs.getLong("high_scans"));
                    stats.setMediumScans(rs.getLong("medium_scans"));
                    stats.setLowScans(rs.getLong("low_scans"));
                    
                    long earliest = rs.getLong("earliest_scan");
                    stats.setEarliestScan(earliest > 0 ? Instant.ofEpochMilli(earliest) : null);
                    
                    long latest = rs.getLong("latest_scan");
                    stats.setLatestScan(latest > 0 ? Instant.ofEpochMilli(latest) : null);
                    
                    stats.setDatabaseSizeBytes(getDatabaseSize());
                    
                    log.debug("Retrieved stats: {} total scans", stats.getTotalScans());
                    return stats;
                }
                
                return new Stats();
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void evictIfNeeded() throws SQLException {
        long currentSize = getDatabaseSize();
        
        if (currentSize <= maxSizeBytes) {
            return;
        }
        
        log.info("Database size {}MB exceeds max {}MB, evicting oldest entries", 
                 currentSize / (1024 * 1024), maxSizeBytes / (1024 * 1024));
        
        String deleteSql = 
            "DELETE FROM scan_results WHERE scan_id IN (" +
            "  SELECT scan_id FROM scan_results " +
            "  ORDER BY scan_timestamp ASC " +
            "  LIMIT ?" +
            ")";
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(deleteSql)) {
            int batchSize = Math.max(100, (int)((currentSize - maxSizeBytes) / (currentSize / getRowCount())));
            pstmt.setInt(1, batchSize);
            int deleted = pstmt.executeUpdate();
            
            log.info("Evicted {} oldest scan results", deleted);
            
            vacuumDatabase();
        }
    }
    
    private void vacuumDatabase() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("VACUUM");
            log.debug("Database vacuumed");
        }
    }
    
    private long getDatabaseSize() {
        try {
            Path path = Paths.get(dbPath);
            if (Files.exists(path)) {
                return Files.size(path);
            }
        } catch (IOException e) {
            log.warn("Failed to get database size", e);
        }
        return 0;
    }
    
    private long getRowCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM scan_results";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }
    
    private List<Finding.Severity> getSeveritiesFrom(Finding.Severity minSeverity) {
        List<Finding.Severity> severities = new ArrayList<>();
        switch (minSeverity) {
            case CRITICAL:
                severities.add(Finding.Severity.CRITICAL);
                break;
            case HIGH:
                severities.add(Finding.Severity.CRITICAL);
                severities.add(Finding.Severity.HIGH);
                break;
            case MEDIUM:
                severities.add(Finding.Severity.CRITICAL);
                severities.add(Finding.Severity.HIGH);
                severities.add(Finding.Severity.MEDIUM);
                break;
            case LOW:
                severities.add(Finding.Severity.CRITICAL);
                severities.add(Finding.Severity.HIGH);
                severities.add(Finding.Severity.MEDIUM);
                severities.add(Finding.Severity.LOW);
                break;
        }
        return severities;
    }
    
    private String serializeFindings(List<Finding> findings) throws JsonProcessingException {
        if (findings == null || findings.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(findings);
    }
    
    private String serializeMetadata(Map<String, String> metadata) throws JsonProcessingException {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(metadata);
    }
    
    private List<Finding> deserializeFindings(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        return objectMapper.readValue(json, new TypeReference<List<Finding>>() {});
    }
    
    private Map<String, String> deserializeMetadata(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }
    
    private ScanResult mapResultSet(ResultSet rs) throws SQLException, IOException {
        ScanResult result = new ScanResult();
        result.setScanId(rs.getString("scan_id"));
        result.setFilePath(rs.getString("file_path"));
        result.setFileHash(rs.getString("file_hash"));
        result.setFileSize(rs.getLong("file_size"));
        
        long mtime = rs.getLong("file_mtime");
        result.setFileMtime(mtime > 0 ? Instant.ofEpochMilli(mtime) : null);
        
        result.setMimeType(rs.getString("mime_type"));
        result.setScanTimestamp(Instant.ofEpochMilli(rs.getLong("scan_timestamp")));
        result.setScanVersion(rs.getString("scan_version"));
        result.setFindingCount(rs.getInt("finding_count"));
        
        String severity = rs.getString("max_severity");
        result.setMaxSeverity(severity != null ? Finding.Severity.valueOf(severity) : null);
        
        result.setParseStatus(rs.getString("parse_status"));
        result.setFindings(deserializeFindings(rs.getString("findings_json")));
        result.setMetadata(deserializeMetadata(rs.getString("metadata_json")));
        
        return result;
    }
    
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("Closed ScanResultStore connection");
            }
        } catch (SQLException e) {
            log.error("Error closing database connection", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public static class ScanResult {
        private String scanId;
        private String filePath;
        private String fileHash;
        private long fileSize;
        private Instant fileMtime;
        private String mimeType;
        private Instant scanTimestamp;
        private String scanVersion;
        private int findingCount;
        private Finding.Severity maxSeverity;
        private String parseStatus;
        private List<Finding> findings;
        private Map<String, String> metadata;
        
        public ScanResult() {
            this.findings = new ArrayList<>();
            this.metadata = new HashMap<>();
            this.scanVersion = SCAN_VERSION;
            this.scanTimestamp = Instant.now();
            this.scanId = UUID.randomUUID().toString();
        }
        
        public String getScanId() {
            return scanId;
        }
        
        public void setScanId(String scanId) {
            this.scanId = scanId;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public String getFileHash() {
            return fileHash;
        }
        
        public void setFileHash(String fileHash) {
            this.fileHash = fileHash;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }
        
        public Instant getFileMtime() {
            return fileMtime;
        }
        
        public void setFileMtime(Instant fileMtime) {
            this.fileMtime = fileMtime;
        }
        
        public String getMimeType() {
            return mimeType;
        }
        
        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
        
        public Instant getScanTimestamp() {
            return scanTimestamp;
        }
        
        public void setScanTimestamp(Instant scanTimestamp) {
            this.scanTimestamp = scanTimestamp;
        }
        
        public String getScanVersion() {
            return scanVersion;
        }
        
        public void setScanVersion(String scanVersion) {
            this.scanVersion = scanVersion;
        }
        
        public int getFindingCount() {
            return findingCount;
        }
        
        public void setFindingCount(int findingCount) {
            this.findingCount = findingCount;
        }
        
        public Finding.Severity getMaxSeverity() {
            return maxSeverity;
        }
        
        public void setMaxSeverity(Finding.Severity maxSeverity) {
            this.maxSeverity = maxSeverity;
        }
        
        public String getParseStatus() {
            return parseStatus;
        }
        
        public void setParseStatus(String parseStatus) {
            this.parseStatus = parseStatus;
        }
        
        public List<Finding> getFindings() {
            return findings;
        }
        
        public void setFindings(List<Finding> findings) {
            this.findings = findings;
            this.findingCount = findings != null ? findings.size() : 0;
            this.maxSeverity = calculateMaxSeverity(findings);
        }
        
        public Map<String, String> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
        
        private Finding.Severity calculateMaxSeverity(List<Finding> findings) {
            if (findings == null || findings.isEmpty()) {
                return null;
            }
            
            Finding.Severity max = Finding.Severity.LOW;
            for (Finding finding : findings) {
                if (finding.getSeverity().ordinal() > max.ordinal()) {
                    max = finding.getSeverity();
                }
            }
            return max;
        }
        
        @Override
        public String toString() {
            return "ScanResult{" +
                   "scanId='" + scanId + '\'' +
                   ", filePath='" + filePath + '\'' +
                   ", findingCount=" + findingCount +
                   ", maxSeverity=" + maxSeverity +
                   ", scanTimestamp=" + scanTimestamp +
                   '}';
        }
    }
    
    public static class QueryParams {
        private Instant startTime;
        private Instant endTime;
        private Finding.Severity minSeverity;
        private String filePathPattern;
        private int limit;
        
        public QueryParams() {
            this.limit = 1000; // Default limit
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }
        
        public Instant getEndTime() {
            return endTime;
        }
        
        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }
        
        public Finding.Severity getMinSeverity() {
            return minSeverity;
        }
        
        public void setMinSeverity(Finding.Severity minSeverity) {
            this.minSeverity = minSeverity;
        }
        
        public String getFilePathPattern() {
            return filePathPattern;
        }
        
        public void setFilePathPattern(String filePathPattern) {
            this.filePathPattern = filePathPattern;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public void setLimit(int limit) {
            this.limit = limit;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private final QueryParams params;
            
            private Builder() {
                this.params = new QueryParams();
            }
            
            public Builder startTime(Instant startTime) {
                params.startTime = startTime;
                return this;
            }
            
            public Builder endTime(Instant endTime) {
                params.endTime = endTime;
                return this;
            }
            
            public Builder minSeverity(Finding.Severity minSeverity) {
                params.minSeverity = minSeverity;
                return this;
            }
            
            public Builder filePathPattern(String filePathPattern) {
                params.filePathPattern = filePathPattern;
                return this;
            }
            
            public Builder limit(int limit) {
                params.limit = limit;
                return this;
            }
            
            public QueryParams build() {
                return params;
            }
        }
        
        @Override
        public String toString() {
            return "QueryParams{" +
                   "startTime=" + startTime +
                   ", endTime=" + endTime +
                   ", minSeverity=" + minSeverity +
                   ", filePathPattern='" + filePathPattern + '\'' +
                   ", limit=" + limit +
                   '}';
        }
    }
    
    public static class Stats {
        private long totalScans;
        private long scansWithFindings;
        private long totalFindings;
        private long criticalScans;
        private long highScans;
        private long mediumScans;
        private long lowScans;
        private Instant earliestScan;
        private Instant latestScan;
        private long databaseSizeBytes;
        
        public Stats() {
        }
        
        public long getTotalScans() {
            return totalScans;
        }
        
        public void setTotalScans(long totalScans) {
            this.totalScans = totalScans;
        }
        
        public long getScansWithFindings() {
            return scansWithFindings;
        }
        
        public void setScansWithFindings(long scansWithFindings) {
            this.scansWithFindings = scansWithFindings;
        }
        
        public long getTotalFindings() {
            return totalFindings;
        }
        
        public void setTotalFindings(long totalFindings) {
            this.totalFindings = totalFindings;
        }
        
        public long getCriticalScans() {
            return criticalScans;
        }
        
        public void setCriticalScans(long criticalScans) {
            this.criticalScans = criticalScans;
        }
        
        public long getHighScans() {
            return highScans;
        }
        
        public void setHighScans(long highScans) {
            this.highScans = highScans;
        }
        
        public long getMediumScans() {
            return mediumScans;
        }
        
        public void setMediumScans(long mediumScans) {
            this.mediumScans = mediumScans;
        }
        
        public long getLowScans() {
            return lowScans;
        }
        
        public void setLowScans(long lowScans) {
            this.lowScans = lowScans;
        }
        
        public Instant getEarliestScan() {
            return earliestScan;
        }
        
        public void setEarliestScan(Instant earliestScan) {
            this.earliestScan = earliestScan;
        }
        
        public Instant getLatestScan() {
            return latestScan;
        }
        
        public void setLatestScan(Instant latestScan) {
            this.latestScan = latestScan;
        }
        
        public long getDatabaseSizeBytes() {
            return databaseSizeBytes;
        }
        
        public void setDatabaseSizeBytes(long databaseSizeBytes) {
            this.databaseSizeBytes = databaseSizeBytes;
        }
        
        @Override
        public String toString() {
            return "Stats{" +
                   "totalScans=" + totalScans +
                   ", scansWithFindings=" + scansWithFindings +
                   ", totalFindings=" + totalFindings +
                   ", criticalScans=" + criticalScans +
                   ", highScans=" + highScans +
                   ", mediumScans=" + mediumScans +
                   ", lowScans=" + lowScans +
                   ", earliestScan=" + earliestScan +
                   ", latestScan=" + latestScan +
                   ", databaseSizeBytes=" + databaseSizeBytes +
                   '}';
        }
    }
}
