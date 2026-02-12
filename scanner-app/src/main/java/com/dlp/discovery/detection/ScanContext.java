package com.dlp.discovery.detection;

import java.nio.file.Path;
import java.util.Map;

public class ScanContext {
    
    private final Path filePath;
    private final String mimeType;
    private final Map<String, String> metadata;
    
    public ScanContext(Path filePath, String mimeType, Map<String, String> metadata) {
        this.filePath = filePath;
        this.mimeType = mimeType;
        this.metadata = metadata;
    }
    
    public Path getFilePath() {
        return filePath;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
