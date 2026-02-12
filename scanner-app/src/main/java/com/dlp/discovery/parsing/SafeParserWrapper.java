package com.dlp.discovery.parsing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * Wrapper for TikaParserService that provides safety guards including:
 * - Timeout protection using ExecutorService
 * - Memory usage monitoring
 * - Proper resource cleanup
 * - Exception handling and logging
 */
public class SafeParserWrapper implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SafeParserWrapper.class);
    private static final long MIN_FREE_MEMORY_BYTES = 50 * 1024 * 1024; // 50MB

    private final TikaParserService parserService;
    private final ParserConfig config;
    private final ExecutorService executorService;
    private volatile boolean closed = false;

    public SafeParserWrapper(TikaParserService parserService, ParserConfig config) {
        this.parserService = parserService;
        this.config = config;
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tika-parser-thread");
            thread.setDaemon(true);
            return thread;
        });
        logger.debug("SafeParserWrapper initialized with timeout: {}s", config.getTimeoutSeconds());
    }

    /**
     * Safely parses a file with timeout and memory protection.
     * 
     * @param filePath the path to the file to parse
     * @return TextExtractionResult containing the parse result or error information
     */
    public TextExtractionResult safeParse(Path filePath) {
        if (closed) {
            logger.error("Attempted to parse file after SafeParserWrapper was closed");
            return TextExtractionResult.failed(null, "Parser wrapper has been closed");
        }

        logger.debug("Starting safe parse for file: {}", filePath);

        // Check memory before parsing
        if (!hasEnoughMemory()) {
            logger.error("Insufficient memory to parse file: {}. Free memory: {} MB", 
                    filePath, Runtime.getRuntime().freeMemory() / (1024 * 1024));
            return TextExtractionResult.failed(null, "Insufficient memory available");
        }

        Future<TextExtractionResult> future = executorService.submit(() -> {
            try {
                // Check memory again inside the task
                if (!hasEnoughMemory()) {
                    logger.warn("Insufficient memory detected during parsing: {}", filePath);
                    return TextExtractionResult.failed(null, "Insufficient memory during parsing");
                }

                logger.debug("Executing parse task for file: {}", filePath);
                TextExtractionResult result = parserService.parse(filePath);
                logger.debug("Parse task completed for file: {}, status: {}", filePath, result.getStatus());
                return result;

            } catch (Exception e) {
                logger.error("Exception occurred during parsing: {}", filePath, e);
                return TextExtractionResult.failed(null, "Parsing exception: " + e.getMessage());
            }
        });

        try {
            int timeoutSeconds = config.getTimeoutSeconds();
            logger.debug("Waiting for parse result with timeout: {}s", timeoutSeconds);
            
            TextExtractionResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            
            // Cleanup any temporary files that might have been created
            cleanupTempFiles();
            
            return result;

        } catch (TimeoutException e) {
            logger.warn("Parse operation timed out after {}s for file: {}", config.getTimeoutSeconds(), filePath);
            future.cancel(true);
            cleanupTempFiles();
            return TextExtractionResult.timeout(null);

        } catch (InterruptedException e) {
            logger.error("Parse operation was interrupted for file: {}", filePath, e);
            Thread.currentThread().interrupt();
            future.cancel(true);
            cleanupTempFiles();
            return TextExtractionResult.failed(null, "Parsing interrupted");

        } catch (ExecutionException e) {
            logger.error("Parse operation failed with execution error for file: {}", filePath, e.getCause());
            cleanupTempFiles();
            return TextExtractionResult.failed(null, "Execution error: " + 
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));

        } catch (Exception e) {
            logger.error("Unexpected error during safe parse for file: {}", filePath, e);
            cleanupTempFiles();
            return TextExtractionResult.failed(null, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Checks if the system has enough free memory to proceed with parsing.
     * 
     * @return true if free memory is above the minimum threshold, false otherwise
     */
    private boolean hasEnoughMemory() {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;

        boolean hasEnough = availableMemory >= MIN_FREE_MEMORY_BYTES;
        
        if (!hasEnough) {
            logger.warn("Low memory detected - Available: {} MB, Required: {} MB", 
                    availableMemory / (1024 * 1024), 
                    MIN_FREE_MEMORY_BYTES / (1024 * 1024));
        }

        return hasEnough;
    }

    /**
     * Cleans up any temporary files that Tika might have created during parsing.
     * This includes triggering garbage collection to help clean up temp resources.
     */
    private void cleanupTempFiles() {
        try {
            // Suggest garbage collection to help clean up any temp files or resources
            // Note: This is a suggestion to the JVM, not a guarantee
            System.gc();
            
            // Tika typically uses java.io.tmpdir for temporary files
            // Most temp files should be cleaned up automatically by Tika,
            // but we trigger GC to help with cleanup
            logger.debug("Cleanup completed and garbage collection suggested");
            
        } catch (Exception e) {
            logger.debug("Error during cleanup: {}", e.getMessage());
        }
    }

    /**
     * Closes the ExecutorService and releases resources.
     * After calling this method, no further parsing operations can be performed.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Shutting down SafeParserWrapper");

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Executor service did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
                
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Executor service did not terminate after forced shutdown");
                }
            }
            logger.info("SafeParserWrapper shut down successfully");
        } catch (InterruptedException e) {
            logger.error("Interrupted while shutting down executor service", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        cleanupTempFiles();
    }
}
