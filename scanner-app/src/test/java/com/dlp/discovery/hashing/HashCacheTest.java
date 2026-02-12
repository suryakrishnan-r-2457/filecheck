package com.dlp.discovery.hashing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HashCacheTest {
    
    @TempDir
    Path tempDir;
    
    private HashCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new HashCache(tempDir.toString(), 10);
    }
    
    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }
    
    @Test
    void testLookupMiss() {
        Optional<HashCache.CachedScanResult> result = cache.lookup("hash1", "v1.0");
        assertThat(result).isEmpty();
        
        HashCache.CacheStats stats = cache.getStats();
        assertThat(stats.getMissCount()).isEqualTo(1);
        assertThat(stats.getHitCount()).isEqualTo(0);
    }
    
    @Test
    void testStoreAndLookup() {
        String contentHash = "abc123";
        String scanVersion = "v1.0";
        HashCache.CachedScanResult toStore = new HashCache.CachedScanResult(
            5, "HIGH", System.currentTimeMillis(), new String[]{"PII", "Credentials"}
        );
        
        cache.store(contentHash, scanVersion, toStore);
        
        Optional<HashCache.CachedScanResult> retrieved = cache.lookup(contentHash, scanVersion);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getFindingCount()).isEqualTo(5);
        assertThat(retrieved.get().getMaxSeverity()).isEqualTo("HIGH");
        assertThat(retrieved.get().getCategories()).containsExactly("PII", "Credentials");
        
        HashCache.CacheStats stats = cache.getStats();
        assertThat(stats.getHitCount()).isEqualTo(1);
        assertThat(stats.getTotalEntries()).isEqualTo(1);
    }
    
    @Test
    void testInvalidateAll() {
        cache.store("hash1", "v1.0", new HashCache.CachedScanResult(1, "LOW", System.currentTimeMillis(), new String[]{}));
        cache.store("hash2", "v1.0", new HashCache.CachedScanResult(2, "MEDIUM", System.currentTimeMillis(), new String[]{}));
        
        assertThat(cache.getStats().getTotalEntries()).isEqualTo(2);
        
        cache.invalidateAll();
        
        assertThat(cache.getStats().getTotalEntries()).isEqualTo(0);
        assertThat(cache.lookup("hash1", "v1.0")).isEmpty();
        assertThat(cache.lookup("hash2", "v1.0")).isEmpty();
    }
    
    @Test
    void testLruEviction() {
        for (int i = 0; i < 15; i++) {
            String hash = "hash" + i;
            cache.store(hash, "v1.0", new HashCache.CachedScanResult(
                i, "LOW", System.currentTimeMillis() + i, new String[]{}
            ));
            
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        HashCache.CacheStats stats = cache.getStats();
        assertThat(stats.getTotalEntries()).isLessThan(15);
    }
    
    @Test
    void testDifferentScanVersions() {
        String contentHash = "hash1";
        cache.store(contentHash, "v1.0", new HashCache.CachedScanResult(5, "HIGH", System.currentTimeMillis(), new String[]{}));
        cache.store(contentHash, "v2.0", new HashCache.CachedScanResult(3, "LOW", System.currentTimeMillis(), new String[]{}));
        
        Optional<HashCache.CachedScanResult> v1 = cache.lookup(contentHash, "v1.0");
        Optional<HashCache.CachedScanResult> v2 = cache.lookup(contentHash, "v2.0");
        
        assertThat(v1).isPresent();
        assertThat(v2).isPresent();
        assertThat(v1.get().getFindingCount()).isEqualTo(5);
        assertThat(v2.get().getFindingCount()).isEqualTo(3);
    }
    
    @Test
    void testCacheStatsHitRate() {
        cache.store("hash1", "v1.0", new HashCache.CachedScanResult(1, "LOW", System.currentTimeMillis(), new String[]{}));
        
        cache.lookup("hash1", "v1.0"); // hit
        cache.lookup("hash2", "v1.0"); // miss
        cache.lookup("hash1", "v1.0"); // hit
        
        HashCache.CacheStats stats = cache.getStats();
        assertThat(stats.getHitCount()).isEqualTo(2);
        assertThat(stats.getMissCount()).isEqualTo(1);
        assertThat(stats.getHitRate()).isEqualTo(2.0 / 3.0);
    }
    
    @Test
    void testEmptyCategories() {
        cache.store("hash1", "v1.0", new HashCache.CachedScanResult(0, null, System.currentTimeMillis(), null));
        
        Optional<HashCache.CachedScanResult> result = cache.lookup("hash1", "v1.0");
        assertThat(result).isPresent();
        assertThat(result.get().getCategories()).isEmpty();
        assertThat(result.get().getMaxSeverity()).isNull();
    }
}
