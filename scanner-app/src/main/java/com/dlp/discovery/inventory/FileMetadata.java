package com.dlp.discovery.inventory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public class FileMetadata {
    private final Path path;
    private final long size;
    private final Instant lastModified;
    private final String hash;
    private final String mimeType;

    private FileMetadata(Builder builder) {
        this.path = Objects.requireNonNull(builder.path, "path must not be null");
        this.size = builder.size;
        this.lastModified = Objects.requireNonNull(builder.lastModified, "lastModified must not be null");
        this.hash = builder.hash;
        this.mimeType = builder.mimeType;
    }

    public Path getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String getHash() {
        return hash;
    }

    public String getMimeType() {
        return mimeType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path path;
        private long size;
        private Instant lastModified;
        private String hash;
        private String mimeType;

        private Builder() {
        }

        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder hash(String hash) {
            this.hash = hash;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public FileMetadata build() {
            return new FileMetadata(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return size == that.size &&
                Objects.equals(path, that.path) &&
                Objects.equals(lastModified, that.lastModified) &&
                Objects.equals(hash, that.hash) &&
                Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, size, lastModified, hash, mimeType);
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "path=" + path +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", hash='" + hash + '\'' +
                ", mimeType='" + mimeType + '\'' +
                '}';
    }
}
