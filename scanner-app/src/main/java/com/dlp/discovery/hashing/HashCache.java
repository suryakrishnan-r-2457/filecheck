package com.dlp.discovery.hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class HashCache implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(HashCache.class);
    private static final String DB_NAME = "hash_cache.db";
    private static final int DEFAULT_MAX_ENTRIES = 100000;
    
    private final Path cacheDir;
    private final int maxEntries;
    private final String jdbcUrl;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    
    public HashCache(String cacheDirPath) {
        this(cacheDirPath, DEFAULT_MAX_ENTRIES);
    }
    
    public HashCache(String cacheDirPath, int maxEntries) {
        this.cacheDir = Paths.get(cacheDirPath);
        this.maxEntries = maxEntries;
        this.jdbcUrl = "jdbc:sqlite:" + cacheDir.resolve(DB_NAME).toString();
        
        try {
            Files.createDirectories(cacheDir);
            initializeDatabase();
            logger.info("Initialized hash cache at {} with max entries: {}", cacheDir, maxEntries);
        } catch (IOException e) {
            logger.error("Failed to create cache directory: {}", cacheDir, e);
            throw new RuntimeException("Failed to initialize hash cache", e);
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Failed to initialize hash cache database", e);
        }
    }
    
    private void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS cache_entries (
                    content_hash TEXT NOT NULL,
                    scan_version TEXT NOT NULL,
                    finding_count INTEGER NOT NULL,
                    max_severity TEXT,
                    timestamp INTEGER NOT NULL,
                    categories TEXT,
                    PRIMARY KEY (content_hash, scan_version)
                )
                """;
            stmt.execute(createTableSql);
            
            String createIndexSql = "CREATE INDEX IF NOT EXISTS idx_timestamp ON cache_entries(timestamp)";
            stmt.execute(createIndexSql);
            
            logger.debug("Database schema initialized");
        }
    }
    
    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        conn.setAutoCommit(true);
        return conn;
    }
    
    public synchronized Optional<CachedScanResult> lookup(String contentHash, String scanVersion) {
        Objects.requireNonNull(contentHash, "contentHash cannot be null");
        Objects.requireNonNull(scanVersion, "scanVersion cannot be null");
        
        String sql = "SELECT finding_count, max_severity, timestamp, categories " +
                     "FROM cache_entries WHERE content_hash = ? AND scan_version = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, contentHash);
            pstmt.setString(2, scanVersion);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int findingCount = rs.getInt("finding_count");
                    String maxSeverity = rs.getString("max_severity");
                    long timestamp = rs.getLong("timestamp");
                    String categoriesStr = rs.getString("categories");
                    
                    String[] categories = categoriesStr != null && !categoriesStr.isEmpty() 
                        ? categoriesStr.split(",") 
                        : new String[0];
                    
                    hitCount.incrementAndGet();
                    logger.debug("Cache hit for contentHash={}, scanVersion={}", contentHash, scanVersion);
                    
                    return Optional.of(new CachedScanResult(findingCount, maxSeverity, timestamp, categories));
                } else {
                    missCount.incrementAndGet();
                    logger.debug("Cache miss for contentHash={}, scanVersion={}", contentHash, scanVersion);
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            logger.error("Error looking up cache entry for contentHash={}, scanVersion={}", 
                        contentHash, scanVersion, e);
            missCount.incrementAndGet();
            return Optional.empty();
        }
    }
    
    public synchronized void store(String contentHash, String scanVersion, CachedScanResult result) {
        Objects.requireNonNull(contentHash, "contentHash cannot be null");
        Objects.requireNonNull(scanVersion, "scanVersion cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
        
        try {
            evictIfNecessary();
            
            String sql = """
                INSERT OR REPLACE INTO cache_entries 
                (content_hash, scan_version, finding_count, max_severity, timestamp, categories)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, contentHash);
                pstmt.setString(2, scanVersion);
                pstmt.setInt(3, result.getFindingCount());
                pstmt.setString(4, result.getMaxSeverity());
                pstmt.setLong(5, result.getTimestamp());
                
                String categoriesStr = result.getCategories() != null 
                    ? Arrays.stream(result.getCategories()).collect(Collectors.joining(","))
                    : "";
                pstmt.setString(6, categoriesStr);
                
                pstmt.executeUpdate();
                logger.debug("Stored cache entry for contentHash={}, scanVersion={}", contentHash, scanVersion);
            }
        } catch (SQLException e) {
            logger.error("Error storing cache entry for contentHash={}, scanVersion={}", 
                        contentHash, scanVersion, e);
        }
    }
    
    private void evictIfNecessary() throws SQLException {
        long totalEntries = getTotalEntries();
        
        if (totalEntries >= maxEntries) {
            int entriesToDelete = (int) (maxEntries * 0.1);
            String deleteSql = """
                DELETE FROM cache_entries WHERE rowid IN (
                    SELECT rowid FROM cache_entries ORDER BY timestamp ASC LIMIT ?
                )
                """;
            
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                
                pstmt.setInt(1, entriesToDelete);
                int deleted = pstmt.executeUpdate();
                logger.info("Evicted {} oldest cache entries (total was {})", deleted, totalEntries);
            }
        }
    }
    
    public synchronized void invalidateAll() {
        String sql = "DELETE FROM cache_entries";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            int deleted = stmt.executeUpdate(sql);
            logger.info("Invalidated all cache entries: {} deleted", deleted);
        } catch (SQLException e) {
            logger.error("Error invalidating cache", e);
            throw new RuntimeException("Failed to invalidate cache", e);
        }
    }
    
    public synchronized CacheStats getStats() {
        try {
            long totalEntries = getTotalEntries();
            return new CacheStats(hitCount.get(), missCount.get(), totalEntries);
        } catch (SQLException e) {
            logger.error("Error getting cache stats", e);
            return new CacheStats(hitCount.get(), missCount.get(), 0);
        }
    }
    
    private long getTotalEntries() throws SQLException {
        String sql = "SELECT COUNT(*) FROM cache_entries";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }
    
    @Override
    public void close() {
        logger.info("Closing hash cache. Final stats: {}", getStats());
    }
    
    public static class CachedScanResult {
        private final int findingCount;
        private final String maxSeverity;
        private final long timestamp;
        private final String[] categories;
        
        public CachedScanResult(int findingCount, String maxSeverity, long timestamp, String[] categories) {
            this.findingCount = findingCount;
            this.maxSeverity = maxSeverity;
            this.timestamp = timestamp;
            this.categories = categories != null ? categories : new String[0];
        }
        
        public int getFindingCount() {
            return findingCount;
        }
        
        public String getMaxSeverity() {
            return maxSeverity;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String[] getCategories() {
            return categories;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CachedScanResult that = (CachedScanResult) o;
            return findingCount == that.findingCount &&
                   timestamp == that.timestamp &&
                   Objects.equals(maxSeverity, that.maxSeverity) &&
                   Arrays.equals(categories, that.categories);
        }
        
        @Override
        public int hashCode() {
            int result = Objects.hash(findingCount, maxSeverity, timestamp);
            result = 31 * result + Arrays.hashCode(categories);
            return result;
        }
        
        @Override
        public String toString() {
            return "CachedScanResult{" +
                   "findingCount=" + findingCount +
                   ", maxSeverity='" + maxSeverity + '\'' +
                   ", timestamp=" + timestamp +
                   ", categories=" + Arrays.toString(categories) +
                   '}';
        }
    }
    
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long totalEntries;
        
        public CacheStats(long hitCount, long missCount, long totalEntries) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.totalEntries = totalEntries;
        }
        
        public long getHitCount() {
            return hitCount;
        }
        
        public long getMissCount() {
            return missCount;
        }
        
        public long getTotalEntries() {
            return totalEntries;
        }
        
        public double getHitRate() {
            long total = hitCount + missCount;
            return total > 0 ? (double) hitCount / total : 0.0;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheStats that = (CacheStats) o;
            return hitCount == that.hitCount &&
                   missCount == that.missCount &&
                   totalEntries == that.totalEntries;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(hitCount, missCount, totalEntries);
        }
        
        @Override
        public String toString() {
            return "CacheStats{" +
                   "hitCount=" + hitCount +
                   ", missCount=" + missCount +
                   ", totalEntries=" + totalEntries +
                   ", hitRate=" + String.format("%.2f%%", getHitRate() * 100) +
                   '}';
        }
    }
}
