package com.dlp.discovery.parsing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TextExtractionResult {

    public enum ParseStatus {
        OK,
        PARTIAL,
        FAILED,
        TIMEOUT,
        ENCRYPTED,
        SKIPPED
    }

    private final String extractedText;
    private final String mimeType;
    private final Map<String, String> metadata;
    private final ParseStatus status;
    private final Integer pageCount;
    private final Integer entryCount;
    private final String errorMessage;

    private TextExtractionResult(Builder builder) {
        this.extractedText = builder.extractedText;
        this.mimeType = builder.mimeType;
        this.metadata = builder.metadata != null ? 
            Collections.unmodifiableMap(new HashMap<>(builder.metadata)) : 
            Collections.emptyMap();
        this.status = builder.status;
        this.pageCount = builder.pageCount;
        this.entryCount = builder.entryCount;
        this.errorMessage = builder.errorMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TextExtractionResult ok(String extractedText, String mimeType) {
        return builder()
            .extractedText(extractedText)
            .mimeType(mimeType)
            .status(ParseStatus.OK)
            .build();
    }

    public static TextExtractionResult failed(String mimeType, String errorMessage) {
        return builder()
            .mimeType(mimeType)
            .status(ParseStatus.FAILED)
            .errorMessage(errorMessage)
            .build();
    }

    public static TextExtractionResult timeout(String mimeType) {
        return builder()
            .mimeType(mimeType)
            .status(ParseStatus.TIMEOUT)
            .errorMessage("Text extraction timed out")
            .build();
    }

    public static TextExtractionResult encrypted(String mimeType) {
        return builder()
            .mimeType(mimeType)
            .status(ParseStatus.ENCRYPTED)
            .errorMessage("File is encrypted")
            .build();
    }

    public String getExtractedText() {
        return extractedText;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public ParseStatus getStatus() {
        return status;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getEntryCount() {
        return entryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "TextExtractionResult{" +
            "status=" + status +
            ", mimeType='" + mimeType + '\'' +
            ", extractedTextLength=" + (extractedText != null ? extractedText.length() : 0) +
            ", metadataKeys=" + metadata.keySet() +
            ", pageCount=" + pageCount +
            ", entryCount=" + entryCount +
            ", errorMessage='" + errorMessage + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextExtractionResult that = (TextExtractionResult) o;
        return Objects.equals(extractedText, that.extractedText) &&
            Objects.equals(mimeType, that.mimeType) &&
            Objects.equals(metadata, that.metadata) &&
            status == that.status &&
            Objects.equals(pageCount, that.pageCount) &&
            Objects.equals(entryCount, that.entryCount) &&
            Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extractedText, mimeType, metadata, status, pageCount, entryCount, errorMessage);
    }

    public static class Builder {
        private String extractedText;
        private String mimeType;
        private Map<String, String> metadata;
        private ParseStatus status;
        private Integer pageCount;
        private Integer entryCount;
        private String errorMessage;

        private Builder() {
        }

        public Builder extractedText(String extractedText) {
            this.extractedText = extractedText;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder status(ParseStatus status) {
            this.status = status;
            return this;
        }

        public Builder pageCount(Integer pageCount) {
            this.pageCount = pageCount;
            return this;
        }

        public Builder entryCount(Integer entryCount) {
            this.entryCount = entryCount;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public TextExtractionResult build() {
            Objects.requireNonNull(status, "status is required");
            return new TextExtractionResult(this);
        }
    }
}
