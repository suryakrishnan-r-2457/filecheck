package com.dlp.discovery.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Walks filesystem directories to find files to scan.
 * Applies filters and handles errors gracefully.
 */
public class FileInventoryWalker {
    private static final Logger logger = LoggerFactory.getLogger(FileInventoryWalker.class);

    private final List<Path> targetPaths;
    private final FileFilterChain filterChain;
    
    private final AtomicLong filesScanned = new AtomicLong(0);
    private final AtomicLong filesSkipped = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);

    /**
     * Constructs a FileInventoryWalker with specified target paths and filter chain.
     *
     * @param targetPaths list of paths to walk (files or directories)
     * @param filterChain filter chain to determine which files should be scanned
     */
    public FileInventoryWalker(List<Path> targetPaths, FileFilterChain filterChain) {
        this.targetPaths = Objects.requireNonNull(targetPaths, "targetPaths must not be null");
        this.filterChain = Objects.requireNonNull(filterChain, "filterChain must not be null");
        
        logger.info("FileInventoryWalker initialized with {} target path(s)", targetPaths.size());
    }

    /**
     * Walks all target paths and returns a stream of FileMetadata for files that pass filters.
     *
     * @return stream of FileMetadata for files to be scanned
     */
    public Stream<FileMetadata> walk() {
        logger.info("Starting filesystem walk for {} target path(s)", targetPaths.size());
        resetStatistics();

        List<FileMetadata> results = new ArrayList<>();

        for (Path targetPath : targetPaths) {
            try {
                if (!Files.exists(targetPath)) {
                    logger.warn("Target path does not exist: {}", targetPath);
                    errors.incrementAndGet();
                    continue;
                }

                if (Files.isRegularFile(targetPath)) {
                    // Single file
                    processFile(targetPath).ifPresent(results::add);
                } else if (Files.isDirectory(targetPath)) {
                    // Directory - walk the tree
                    walkDirectory(targetPath, results);
                } else {
                    logger.warn("Target path is neither file nor directory: {}", targetPath);
                    errors.incrementAndGet();
                }
            } catch (Exception e) {
                logger.error("Error processing target path {}: {}", targetPath, e.getMessage(), e);
                errors.incrementAndGet();
            }
        }

        logStatistics();
        return results.stream();
    }

    /**
     * Walks a directory tree and collects files that pass filters.
     */
    private void walkDirectory(Path directory, List<FileMetadata> results) {
        try {
            Files.walkFileTree(directory, 
                    EnumSet.noneOf(FileVisitOption.class), 
                    Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            // Skip symbolic links to avoid loops
                            if (attrs.isSymbolicLink()) {
                                logger.debug("Skipping symbolic link: {}", file);
                                filesSkipped.incrementAndGet();
                                return FileVisitResult.CONTINUE;
                            }

                            // Process regular files
                            if (attrs.isRegularFile()) {
                                processFile(file).ifPresent(results::add);
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            // Handle access denied and other errors gracefully
                            if (exc != null) {
                                logger.warn("Failed to access file {}: {}", file, exc.getMessage());
                            } else {
                                logger.warn("Failed to access file: {}", file);
                            }
                            errors.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            // Skip symbolic link directories to avoid loops
                            if (attrs.isSymbolicLink()) {
                                logger.debug("Skipping symbolic link directory: {}", dir);
                                filesSkipped.incrementAndGet();
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                            if (exc != null) {
                                logger.warn("Error after visiting directory {}: {}", dir, exc.getMessage());
                                errors.incrementAndGet();
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            logger.error("Error walking directory {}: {}", directory, e.getMessage(), e);
            errors.incrementAndGet();
        }
    }

    /**
     * Processes a single file, applying filters and creating FileMetadata if it passes.
     *
     * @param file the file to process
     * @return FileMetadata if the file passes filters, empty otherwise
     */
    private java.util.Optional<FileMetadata> processFile(Path file) {
        try {
            // Get file attributes
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long fileSize = attrs.size();
            Instant lastModified = attrs.lastModifiedTime().toInstant();

            // Apply filter chain
            if (filterChain.shouldScan(file, fileSize)) {
                filesScanned.incrementAndGet();
                
                FileMetadata metadata = FileMetadata.builder()
                        .path(file)
                        .size(fileSize)
                        .lastModified(lastModified)
                        .build();
                
                logger.trace("File accepted for scanning: {} (size={})", file, fileSize);
                return java.util.Optional.of(metadata);
            } else {
                filesSkipped.incrementAndGet();
                logger.trace("File skipped by filters: {}", file);
                return java.util.Optional.empty();
            }
        } catch (IOException e) {
            logger.warn("Error reading file attributes for {}: {}", file, e.getMessage());
            errors.incrementAndGet();
            return java.util.Optional.empty();
        }
    }

    /**
     * Resets statistics counters.
     */
    private void resetStatistics() {
        filesScanned.set(0);
        filesSkipped.set(0);
        errors.set(0);
    }

    /**
     * Logs summary statistics.
     */
    private void logStatistics() {
        logger.info("Filesystem walk completed: filesScanned={}, filesSkipped={}, errors={}",
                filesScanned.get(), filesSkipped.get(), errors.get());
    }

    /**
     * Returns the number of files that passed filters and will be scanned.
     */
    public long getFilesScanned() {
        return filesScanned.get();
    }

    /**
     * Returns the number of files that were skipped due to filters.
     */
    public long getFilesSkipped() {
        return filesSkipped.get();
    }

    /**
     * Returns the number of errors encountered during the walk.
     */
    public long getErrors() {
        return errors.get();
    }
}
