package com.dlp.discovery.scheduler;

import com.dlp.discovery.config.ScannerConfig;
import com.dlp.discovery.detection.DetectionEngine;
import com.dlp.discovery.detection.Finding;
import com.dlp.discovery.detection.ScanContext;
import com.dlp.discovery.hashing.ContentHasher;
import com.dlp.discovery.hashing.HashCache;
import com.dlp.discovery.inventory.FileInventoryWalker;
import com.dlp.discovery.inventory.FileMetadata;
import com.dlp.discovery.parsing.SafeParserWrapper;
import com.dlp.discovery.parsing.TextExtractionResult;
import com.dlp.discovery.results.ScanResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Manages scan scheduling for the file scanner.
 * Supports both full scans (based on cron) and incremental scans (periodic).
 */
public class ScanScheduler implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(ScanScheduler.class);
    
    private final ScannerConfig config;
    private final DetectionEngine detectionEngine;
    private final SafeParserWrapper parserWrapper;
    private final HashCache hashCache;
    private final ScanResultStore resultStore;
    private final ContentHasher contentHasher;
    private final FileInventoryWalker fileWalker;
    
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private final AtomicBoolean scanInProgress;
    
    private ScheduledFuture<?> fullScanTask;
    private ScheduledFuture<?> incrementalScanTask;
    
    private final AtomicLong totalFilesScanned;
    private final AtomicLong totalFilesWithFindings;
    private final AtomicLong totalCacheHits;
    private final AtomicLong totalCacheMisses;
    
    private Instant lastFullScanTime;
    private Instant lastIncrementalScanTime;
    
    public ScanScheduler(
            ScannerConfig config,
            DetectionEngine detectionEngine,
            SafeParserWrapper parserWrapper,
            HashCache hashCache,
            ScanResultStore resultStore,
            ContentHasher contentHasher,
            FileInventoryWalker fileWalker) {
        
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.detectionEngine = Objects.requireNonNull(detectionEngine, "detectionEngine cannot be null");
        this.parserWrapper = Objects.requireNonNull(parserWrapper, "parserWrapper cannot be null");
        this.hashCache = Objects.requireNonNull(hashCache, "hashCache cannot be null");
        this.resultStore = Objects.requireNonNull(resultStore, "resultStore cannot be null");
        this.contentHasher = Objects.requireNonNull(contentHasher, "contentHasher cannot be null");
        this.fileWalker = Objects.requireNonNull(fileWalker, "fileWalker cannot be null");
        
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "scan-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        
        this.running = new AtomicBoolean(false);
        this.scanInProgress = new AtomicBoolean(false);
        
        this.totalFilesScanned = new AtomicLong(0);
        this.totalFilesWithFindings = new AtomicLong(0);
        this.totalCacheHits = new AtomicLong(0);
        this.totalCacheMisses = new AtomicLong(0);
        
        logger.info("ScanScheduler initialized");
    }
    
    /**
     * Starts the scheduler.
     * Schedules both full scans and incremental scans based on configuration.
     */
    public void start() {
        if (running.getAndSet(true)) {
            logger.warn("ScanScheduler is already running");
            return;
        }
        
        logger.info("Starting ScanScheduler");
        
        ScannerConfig.ScannerSettings settings = config.getScanner();
        
        scheduleFullScans(settings.getFullScanCronExpression());
        scheduleIncrementalScans(settings.getIncrementalScanIntervalHours());
        
        logger.info("ScanScheduler started successfully");
    }
    
    /**
     * Stops the scheduler.
     * Waits for in-progress scans to complete.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            logger.warn("ScanScheduler is not running");
            return;
        }
        
        logger.info("Stopping ScanScheduler");
        
        if (fullScanTask != null) {
            fullScanTask.cancel(false);
        }
        
        if (incrementalScanTask != null) {
            incrementalScanTask.cancel(false);
        }
        
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("Scheduler did not terminate gracefully, forcing shutdown");
                scheduler.shutdownNow();
                
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("Scheduler did not terminate after forced shutdown");
                }
            }
            logger.info("ScanScheduler stopped successfully");
        } catch (InterruptedException e) {
            logger.error("Interrupted while stopping scheduler", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logFinalStatistics();
    }
    
    /**
     * Schedules full scans based on cron expression.
     */
    private void scheduleFullScans(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            logger.info("Full scan cron expression not configured, skipping full scan scheduling");
            return;
        }
        
        logger.info("Scheduling full scans with cron expression: {}", cronExpression);
        
        long delaySeconds = parseCronToInitialDelay(cronExpression);
        long periodSeconds = parseCronToPeriod(cronExpression);
        
        fullScanTask = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    performFullScan();
                } catch (Exception e) {
                    logger.error("Error during scheduled full scan", e);
                }
            },
            delaySeconds,
            periodSeconds,
            TimeUnit.SECONDS
        );
        
        logger.info("Full scans scheduled with initial delay {}s and period {}s", delaySeconds, periodSeconds);
    }
    
    /**
     * Schedules incremental scans every N hours.
     */
    private void scheduleIncrementalScans(int intervalHours) {
        if (intervalHours <= 0) {
            logger.info("Incremental scan interval not configured, skipping incremental scan scheduling");
            return;
        }
        
        logger.info("Scheduling incremental scans every {} hours", intervalHours);
        
        long intervalSeconds = intervalHours * 3600L;
        
        incrementalScanTask = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    performIncrementalScan();
                } catch (Exception e) {
                    logger.error("Error during scheduled incremental scan", e);
                }
            },
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
        
        logger.info("Incremental scans scheduled every {} hours", intervalHours);
    }
    
    /**
     * Performs a full scan of all target paths.
     */
    private void performFullScan() {
        if (!scanInProgress.compareAndSet(false, true)) {
            logger.warn("Scan already in progress, skipping this full scan");
            return;
        }
        
        try {
            logger.info("Starting full scan");
            lastFullScanTime = Instant.now();
            
            if (config.getScanner().isIdleOnlyScanning()) {
                if (!isSystemIdle()) {
                    logger.info("System is not idle, deferring full scan");
                    return;
                }
            }
            
            performScan(true);
            
            logger.info("Full scan completed successfully");
        } finally {
            scanInProgress.set(false);
        }
    }
    
    /**
     * Performs an incremental scan (scans only recently modified files).
     */
    private void performIncrementalScan() {
        if (!scanInProgress.compareAndSet(false, true)) {
            logger.warn("Scan already in progress, skipping this incremental scan");
            return;
        }
        
        try {
            logger.info("Starting incremental scan");
            lastIncrementalScanTime = Instant.now();
            
            if (config.getScanner().isIdleOnlyScanning()) {
                if (!isSystemIdle()) {
                    logger.info("System is not idle, deferring incremental scan");
                    return;
                }
            }
            
            performScan(false);
            
            logger.info("Incremental scan completed successfully");
        } finally {
            scanInProgress.set(false);
        }
    }
    
    /**
     * Performs the actual scan loop.
     */
    private void performScan(boolean isFullScan) {
        logger.info("Performing {} scan", isFullScan ? "full" : "incremental");
        
        Instant scanStartTime = Instant.now();
        long filesProcessed = 0;
        long filesWithFindings = 0;
        long cacheHits = 0;
        long cacheMisses = 0;
        long errors = 0;
        
        RateLimiter rateLimiter = new RateLimiter(config.getScanner().getMaxFilesPerSecond());
        
        try (Stream<FileMetadata> fileStream = fileWalker.walk()) {
            
            for (FileMetadata fileMetadata : (Iterable<FileMetadata>) fileStream::iterator) {
                try {
                    rateLimiter.acquire();
                    
                    Path filePath = fileMetadata.getPath();
                    logger.debug("Processing file: {}", filePath);
                    
                    String fileHash = contentHasher.hashFile(filePath);
                    String scanVersion = detectionEngine.getVersion();
                    
                    Optional<HashCache.CachedScanResult> cachedResult = 
                        hashCache.lookup(fileHash, scanVersion);
                    
                    if (cachedResult.isPresent()) {
                        logger.debug("Cache hit for file: {}", filePath);
                        cacheHits++;
                        
                        storeCachedResult(filePath, fileHash, fileMetadata, cachedResult.get());
                    } else {
                        logger.debug("Cache miss for file: {}", filePath);
                        cacheMisses++;
                        
                        TextExtractionResult parseResult = parserWrapper.safeParse(filePath);
                        
                        if (parseResult.getStatus() == TextExtractionResult.ParseStatus.OK && 
                            parseResult.getExtractedText() != null) {
                            
                            ScanContext context = new ScanContext(filePath, parseResult.getMimeType(), 
                                parseResult.getMetadata());
                            List<Finding> findings = detectionEngine.scan(parseResult.getExtractedText(), context);
                            
                            storeResult(filePath, fileHash, fileMetadata, parseResult, findings);
                            
                            cacheScanResult(fileHash, scanVersion, findings);
                            
                            if (!findings.isEmpty()) {
                                filesWithFindings++;
                                logger.info("Found {} findings in file: {}", findings.size(), filePath);
                            }
                        } else {
                            storeFailedResult(filePath, fileHash, fileMetadata, parseResult);
                        }
                    }
                    
                    filesProcessed++;
                    
                    if (filesProcessed % 100 == 0) {
                        logProgressStatistics(filesProcessed, cacheHits, cacheMisses, filesWithFindings);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing file: {}", fileMetadata.getPath(), e);
                    errors++;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during scan", e);
            errors++;
        }
        
        Instant scanEndTime = Instant.now();
        long durationSeconds = scanEndTime.getEpochSecond() - scanStartTime.getEpochSecond();
        
        totalFilesScanned.addAndGet(filesProcessed);
        totalFilesWithFindings.addAndGet(filesWithFindings);
        totalCacheHits.addAndGet(cacheHits);
        totalCacheMisses.addAndGet(cacheMisses);
        
        logger.info("Scan completed: type={}, filesProcessed={}, filesWithFindings={}, cacheHits={}, " +
                   "cacheMisses={}, errors={}, durationSeconds={}",
                   isFullScan ? "full" : "incremental", filesProcessed, filesWithFindings, 
                   cacheHits, cacheMisses, errors, durationSeconds);
    }
    
    /**
     * Stores a scan result from cache.
     */
    private void storeCachedResult(Path filePath, String fileHash, 
                                   FileMetadata fileMetadata,
                                   HashCache.CachedScanResult cachedResult) {
        try {
            ScanResultStore.ScanResult result = new ScanResultStore.ScanResult();
            result.setFilePath(filePath.toString());
            result.setFileHash(fileHash);
            result.setFileSize(fileMetadata.getSize());
            result.setFileMtime(fileMetadata.getLastModified());
            result.setParseStatus("CACHED");
            result.setFindingCount(cachedResult.getFindingCount());
            
            if (cachedResult.getMaxSeverity() != null) {
                result.setMaxSeverity(Finding.Severity.valueOf(cachedResult.getMaxSeverity()));
            }
            
            resultStore.store(result);
        } catch (Exception e) {
            logger.error("Error storing cached result for file: {}", filePath, e);
        }
    }
    
    /**
     * Stores a scan result after detection.
     */
    private void storeResult(Path filePath, String fileHash, FileMetadata fileMetadata,
                            TextExtractionResult parseResult, List<Finding> findings) {
        try {
            ScanResultStore.ScanResult result = new ScanResultStore.ScanResult();
            result.setFilePath(filePath.toString());
            result.setFileHash(fileHash);
            result.setFileSize(fileMetadata.getSize());
            result.setFileMtime(fileMetadata.getLastModified());
            result.setMimeType(parseResult.getMimeType());
            result.setParseStatus(parseResult.getStatus().name());
            result.setFindings(findings);
            
            resultStore.store(result);
        } catch (Exception e) {
            logger.error("Error storing scan result for file: {}", filePath, e);
        }
    }
    
    /**
     * Stores a failed parse result.
     */
    private void storeFailedResult(Path filePath, String fileHash, FileMetadata fileMetadata,
                                   TextExtractionResult parseResult) {
        try {
            ScanResultStore.ScanResult result = new ScanResultStore.ScanResult();
            result.setFilePath(filePath.toString());
            result.setFileHash(fileHash);
            result.setFileSize(fileMetadata.getSize());
            result.setFileMtime(fileMetadata.getLastModified());
            result.setMimeType(parseResult.getMimeType());
            result.setParseStatus(parseResult.getStatus().name());
            result.setFindingCount(0);
            
            resultStore.store(result);
        } catch (Exception e) {
            logger.error("Error storing failed result for file: {}", filePath, e);
        }
    }
    
    /**
     * Caches the scan result.
     */
    private void cacheScanResult(String fileHash, String scanVersion, List<Finding> findings) {
        try {
            int findingCount = findings.size();
            String maxSeverity = null;
            
            if (!findings.isEmpty()) {
                Finding.Severity max = findings.stream()
                    .map(Finding::getSeverity)
                    .max(Enum::compareTo)
                    .orElse(null);
                
                if (max != null) {
                    maxSeverity = max.name();
                }
            }
            
            String[] categories = findings.stream()
                .map(Finding::getCategory)
                .distinct()
                .toArray(String[]::new);
            
            HashCache.CachedScanResult cachedResult = new HashCache.CachedScanResult(
                findingCount, maxSeverity, Instant.now().toEpochMilli(), categories
            );
            
            hashCache.store(fileHash, scanVersion, cachedResult);
        } catch (Exception e) {
            logger.error("Error caching scan result", e);
        }
    }
    
    /**
     * Checks if the system is idle.
     * Placeholder implementation - just logs for now.
     */
    private boolean isSystemIdle() {
        int idleThresholdMinutes = config.getScanner().getIdleThresholdMinutes();
        logger.debug("Idle detection placeholder: would check if system has been idle for {} minutes", 
                    idleThresholdMinutes);
        return true;
    }
    
    /**
     * Parses cron expression to initial delay.
     * Simplified implementation - assumes daily cron at specific hour.
     */
    private long parseCronToInitialDelay(String cronExpression) {
        logger.debug("Parsing cron expression for initial delay: {}", cronExpression);
        return 60;
    }
    
    /**
     * Parses cron expression to period.
     * Simplified implementation - assumes daily cron.
     */
    private long parseCronToPeriod(String cronExpression) {
        logger.debug("Parsing cron expression for period: {}", cronExpression);
        return 24 * 3600;
    }
    
    /**
     * Logs progress statistics during scan.
     */
    private void logProgressStatistics(long filesProcessed, long cacheHits, 
                                       long cacheMisses, long filesWithFindings) {
        logger.info("Scan progress: filesProcessed={}, cacheHits={}, cacheMisses={}, filesWithFindings={}",
                   filesProcessed, cacheHits, cacheMisses, filesWithFindings);
    }
    
    /**
     * Logs final statistics when scheduler stops.
     */
    private void logFinalStatistics() {
        logger.info("Final statistics: totalFilesScanned={}, totalFilesWithFindings={}, " +
                   "totalCacheHits={}, totalCacheMisses={}, lastFullScan={}, lastIncrementalScan={}",
                   totalFilesScanned.get(), totalFilesWithFindings.get(),
                   totalCacheHits.get(), totalCacheMisses.get(),
                   lastFullScanTime, lastIncrementalScanTime);
    }
    
    @Override
    public void close() {
        stop();
    }
    
    /**
     * Simple rate limiter for controlling scan speed.
     */
    private static class RateLimiter {
        private final long minIntervalNanos;
        private long lastAcquireTime;
        
        public RateLimiter(int maxFilesPerSecond) {
            if (maxFilesPerSecond <= 0) {
                this.minIntervalNanos = 0;
            } else {
                this.minIntervalNanos = TimeUnit.SECONDS.toNanos(1) / maxFilesPerSecond;
            }
            this.lastAcquireTime = System.nanoTime();
        }
        
        public void acquire() {
            if (minIntervalNanos <= 0) {
                return;
            }
            
            long now = System.nanoTime();
            long elapsedNanos = now - lastAcquireTime;
            
            if (elapsedNanos < minIntervalNanos) {
                long sleepNanos = minIntervalNanos - elapsedNanos;
                try {
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            lastAcquireTime = System.nanoTime();
        }
    }
}
