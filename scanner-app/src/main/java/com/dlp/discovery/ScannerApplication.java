package com.dlp.discovery;

import com.dlp.discovery.config.ConfigLoader;
import com.dlp.discovery.config.ScannerConfig;
import com.dlp.discovery.platform.ProcessPriority;
import com.dlp.discovery.platform.IOPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point for the DLP Discovery Scanner application.
 * Initializes all services and starts the scan scheduler.
 */
public class ScannerApplication {
    private static final Logger logger = LoggerFactory.getLogger(ScannerApplication.class);

    public static void main(String[] args) {
        logger.info("Starting DLP Discovery Scanner...");

        try {
            // Set process priority (Windows)
            ProcessPriority.setBelowNormal();
            IOPriority.setLow();

            // Load configuration
            String configPath = args.length > 0 ? args[0] : "application.yaml";
            ScannerConfig config = ConfigLoader.load(configPath);
            logger.info("Configuration loaded successfully");

            // Ensure required directories exist
            ensureDirectories(config);

            // Initialize components
            logger.info("Initializing scanner components...");
            
            ScannerConfig.ScannerSettings settings = config.getScanner();
            
            // Load detection rules
            com.dlp.discovery.policy.PolicyStore policyStore = 
                new com.dlp.discovery.policy.PolicyStore(settings.getRules().getPath());
            logger.info("Rules loaded: version {}", policyStore.currentRules().getVersion());
            
            // Initialize detection engine
            com.dlp.discovery.detection.DetectionEngine detection = 
                new com.dlp.discovery.detection.RegexDetectionEngine(policyStore.currentRules());
            
            // Initialize parser with safety wrapper
            com.dlp.discovery.parsing.ParserConfig parserConfig = 
                com.dlp.discovery.parsing.ParserConfig.fromParserSettings(settings.getParser());
            com.dlp.discovery.parsing.TikaParserService tikaParser = 
                new com.dlp.discovery.parsing.TikaParserService(parserConfig);
            com.dlp.discovery.parsing.SafeParserWrapper parser = 
                new com.dlp.discovery.parsing.SafeParserWrapper(tikaParser, parserConfig);
            
            // Initialize hash cache
            com.dlp.discovery.hashing.HashCache hashCache = 
                new com.dlp.discovery.hashing.HashCache(settings.getCache().getPath());
            
            // Initialize result store
            com.dlp.discovery.results.ScanResultStore resultStore = 
                new com.dlp.discovery.results.ScanResultStore(settings.getResults().getDbPath());
            
            // Initialize content hasher
            com.dlp.discovery.hashing.ContentHasher contentHasher = 
                new com.dlp.discovery.hashing.ContentHasher();
            
            // Initialize file filter and walker
            com.dlp.discovery.inventory.FileFilterChain filterChain = 
                new com.dlp.discovery.inventory.FileFilterChain(
                    settings.getIncludeExtensions(),
                    settings.getExcludePaths(),
                    settings.getMaxFileSizeBytes()
                );
            
            java.util.List<java.nio.file.Path> targetPaths = settings.getTargets().stream()
                .map(java.nio.file.Paths::get)
                .collect(java.util.stream.Collectors.toList());
            
            com.dlp.discovery.inventory.FileInventoryWalker inventoryWalker = 
                new com.dlp.discovery.inventory.FileInventoryWalker(targetPaths, filterChain);
            
            logger.info("All components initialized successfully");

            // Start scheduler
            com.dlp.discovery.scheduler.ScanScheduler scheduler = 
                new com.dlp.discovery.scheduler.ScanScheduler(
                    config, detection, parser, hashCache, resultStore, 
                    contentHasher, inventoryWalker
                );
            scheduler.start();

            logger.info("DLP Discovery Scanner started successfully");

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down DLP Discovery Scanner...");
                try {
                    scheduler.stop();
                    parser.close();
                    hashCache.close();
                    resultStore.close();
                    policyStore.close();
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
                logger.info("DLP Discovery Scanner stopped");
            }));

            // Keep the application running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start scanner", e);
            System.exit(1);
        }
    }

    private static void ensureDirectories(ScannerConfig config) {
        try {
            ScannerConfig.ScannerSettings settings = config.getScanner();

            // Create cache directory
            createDirectoryIfNeeded(settings.getCache().getPath(), "cache");

            // Create results directory
            Path resultsDbPath = Paths.get(settings.getResults().getDbPath());
            createDirectoryIfNeeded(resultsDbPath.getParent().toString(), "results");

            // Create telemetry directory
            Path telemetryDbPath = Paths.get(settings.getTelemetry().getDbPath());
            createDirectoryIfNeeded(telemetryDbPath.getParent().toString(), "telemetry");

            // Create rules directory
            createDirectoryIfNeeded(settings.getRules().getPath(), "rules");

        } catch (Exception e) {
            logger.warn("Could not create all required directories", e);
        }
    }

    private static void createDirectoryIfNeeded(String pathStr, String name) {
        try {
            if (pathStr != null && !pathStr.isEmpty()) {
                Path path = Paths.get(pathStr);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    logger.info("Created {} directory: {}", name, path);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not create {} directory: {}", name, pathStr, e);
        }
    }
}
