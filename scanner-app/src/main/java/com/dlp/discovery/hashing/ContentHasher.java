package com.dlp.discovery.hashing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes SHA-256 hashes of file content with optimized handling for large files.
 */
public class ContentHasher {
    private static final Logger logger = LoggerFactory.getLogger(ContentHasher.class);
    
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer
    private static final long LARGE_FILE_THRESHOLD = 50 * 1024 * 1024L; // 50MB
    private static final int SAMPLE_SIZE = 1024 * 1024; // 1MB
    
    /**
     * Computes SHA-256 hash of a file.
     * For files larger than 50MB, uses probabilistic hashing (first 1MB + last 1MB + file size).
     * 
     * @param filePath the path to the file
     * @return SHA-256 hash as lowercase hex string
     * @throws IOException if an I/O error occurs
     */
    public String hashFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("Not a regular file: " + filePath);
        }
        
        long fileSize = Files.size(filePath);
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            if (fileSize > LARGE_FILE_THRESHOLD) {
                logger.debug("Using probabilistic hash for large file: {} (size: {} bytes)", 
                            filePath, fileSize);
                return hashLargeFile(filePath, fileSize, digest);
            } else {
                return hashEntireFile(filePath, digest);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Computes SHA-256 hash of a string.
     * 
     * @param content the string content to hash
     * @return SHA-256 hash as lowercase hex string
     */
    public String hashString(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private String hashEntireFile(Path filePath, MessageDigest digest) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        }
    }
    
    private String hashLargeFile(Path filePath, long fileSize, MessageDigest digest) throws IOException {
        // Hash first 1MB
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesRemaining = Math.min(SAMPLE_SIZE, fileSize);
            
            while (bytesRemaining > 0) {
                int toRead = (int) Math.min(buffer.length, bytesRemaining);
                int bytesRead = inputStream.read(buffer, 0, toRead);
                if (bytesRead == -1) {
                    break;
                }
                digest.update(buffer, 0, bytesRead);
                bytesRemaining -= bytesRead;
            }
        }
        
        // Hash last 1MB if file is large enough
        if (fileSize > SAMPLE_SIZE * 2) {
            long skipToPosition = fileSize - SAMPLE_SIZE;
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                long totalSkipped = 0;
                while (totalSkipped < skipToPosition) {
                    long skipped = inputStream.skip(skipToPosition - totalSkipped);
                    if (skipped == 0) {
                        throw new IOException("Could not skip to position " + skipToPosition + " in file " + filePath);
                    }
                    totalSkipped += skipped;
                }
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
        }
        
        // Include file size in hash to differentiate files with same start/end
        String sizeString = String.valueOf(fileSize);
        digest.update(sizeString.getBytes(StandardCharsets.UTF_8));
        
        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
