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

            // TODO: Initialize components
            // PolicyStore policyStore = new PolicyStore(config.getScanner().getRules().getPath());
            // DetectionEngine detection = new RegexDetectionEngine(policyStore.currentRules());
            // TikaParserService parser = new TikaParserService(ParserConfig.fromParserSettings(config.getScanner().getParser()));
            // HashCache hashCache = new HashCache(config.getScanner().getCache().getPath());
            // ScanResultStore resultStore = new ScanResultStore(config.getScanner().getResults().getDbPath());
            // TelemetryBuffer telemetry = new TelemetryBuffer(config.getScanner().getTelemetry().getDbPath());

            // TODO: Start scheduler
            // ScanScheduler scheduler = new ScanScheduler(config, detection, parser, hashCache, resultStore, telemetry);
            // scheduler.start();

            logger.info("DLP Discovery Scanner started successfully");

            // TODO: Register shutdown hook
            // Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //     logger.info("Shutting down DLP Discovery Scanner...");
            //     scheduler.stop();
            //     logger.info("DLP Discovery Scanner stopped");
            // }));

            // For now, just keep the application running
            logger.info("Scanner initialized. Components pending full implementation.");
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
