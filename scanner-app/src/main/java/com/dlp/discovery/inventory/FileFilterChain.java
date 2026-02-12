package com.dlp.discovery.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thread-safe filter chain for determining whether files should be scanned.
 * Applies include/exclude filters based on file extensions, paths, and size.
 */
public class FileFilterChain {
    private static final Logger logger = LoggerFactory.getLogger(FileFilterChain.class);

    private final Set<String> includeExtensions;
    private final List<PathMatcher> excludePatterns;
    private final long maxFileSize;

    /**
     * Constructs a FileFilterChain with the specified filters.
     *
     * @param includeExtensions list of file extensions to include (e.g., "txt", "log")
     * @param excludePaths list of glob patterns for paths to exclude (e.g., ".git", "temp")
     * @param maxFileSize maximum file size in bytes (files larger than this will be excluded)
     */
    public FileFilterChain(List<String> includeExtensions, List<String> excludePaths, long maxFileSize) {
        this.includeExtensions = normalizeExtensions(includeExtensions);
        this.excludePatterns = compilePatterns(excludePaths);
        this.maxFileSize = maxFileSize;

        logger.debug("FileFilterChain initialized: includeExtensions={}, excludePatterns={}, maxFileSize={}",
                this.includeExtensions, excludePaths, maxFileSize);
    }

    /**
     * Determines whether a file should be scanned based on the configured filters.
     *
     * @param filePath the path of the file to check
     * @param fileSize the size of the file in bytes
     * @return true if the file should be scanned, false otherwise
     */
    public boolean shouldScan(Path filePath, long fileSize) {
        // Check file size
        if (fileSize > maxFileSize) {
            logger.debug("File excluded due to size: {} (size={}, max={})", filePath, fileSize, maxFileSize);
            return false;
        }

        // Check file extension
        if (!hasIncludedExtension(filePath)) {
            logger.debug("File excluded due to extension: {}", filePath);
            return false;
        }

        // Check exclude patterns
        if (matchesExcludePattern(filePath)) {
            logger.debug("File excluded due to path pattern: {}", filePath);
            return false;
        }

        logger.trace("File passed all filters: {}", filePath);
        return true;
    }

    /**
     * Normalizes file extensions to lowercase and removes leading dots.
     */
    private Set<String> normalizeExtensions(List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return Collections.emptySet();
        }

        return extensions.stream()
                .map(ext -> ext.toLowerCase(Locale.ROOT))
                .map(ext -> ext.startsWith(".") ? ext.substring(1) : ext)
                .collect(Collectors.toSet());
    }

    /**
     * Compiles glob patterns into PathMatcher objects during construction.
     */
    private List<PathMatcher> compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }

        List<PathMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            try {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                matchers.add(matcher);
                logger.debug("Compiled exclude pattern: {}", pattern);
            } catch (Exception e) {
                logger.warn("Failed to compile pattern '{}': {}", pattern, e.getMessage());
            }
        }

        return Collections.unmodifiableList(matchers);
    }

    /**
     * Checks if the file has an included extension (case-insensitive).
     */
    private boolean hasIncludedExtension(Path filePath) {
        if (includeExtensions.isEmpty()) {
            return true; // No extension filter, include all
        }

        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return false; // No extension
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return includeExtensions.contains(extension);
    }

    /**
     * Checks if the file path matches any exclude pattern.
     */
    private boolean matchesExcludePattern(Path filePath) {
        for (PathMatcher matcher : excludePatterns) {
            if (matcher.matches(filePath)) {
                return true;
            }
        }
        return false;
    }
}
