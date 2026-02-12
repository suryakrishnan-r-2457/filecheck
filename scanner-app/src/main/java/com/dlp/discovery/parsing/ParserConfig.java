package com.dlp.discovery.parsing;

import com.dlp.discovery.config.ScannerConfig;

/**
 * Configuration POJO for passing parser limits to TikaParserService.
 * Holds per-format parsing limits extracted from ScannerConfig.ParserSettings.
 */
public class ParserConfig {
    private final int timeoutSeconds;
    private final PdfLimits pdf;
    private final OoxmlLimits ooxml;
    private final ZipLimits zip;
    private final Ole2Limits ole2;
    private final EmlLimits eml;

    private ParserConfig(Builder builder) {
        this.timeoutSeconds = builder.timeoutSeconds;
        this.pdf = builder.pdf;
        this.ooxml = builder.ooxml;
        this.zip = builder.zip;
        this.ole2 = builder.ole2;
        this.eml = builder.eml;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public PdfLimits getPdf() {
        return pdf;
    }

    public OoxmlLimits getOoxml() {
        return ooxml;
    }

    public ZipLimits getZip() {
        return zip;
    }

    public Ole2Limits getOle2() {
        return ole2;
    }

    public EmlLimits getEml() {
        return eml;
    }

    public static ParserConfig fromParserSettings(ScannerConfig.ParserSettings settings) {
        if (settings == null) {
            return builder().build();
        }

        Builder builder = builder()
                .timeoutSeconds(settings.getTimeoutSeconds());

        if (settings.getPdf() != null) {
            builder.pdf(PdfLimits.fromSettings(settings.getPdf()));
        }

        if (settings.getOoxml() != null) {
            builder.ooxml(OoxmlLimits.fromSettings(settings.getOoxml()));
        }

        if (settings.getZip() != null) {
            builder.zip(ZipLimits.fromSettings(settings.getZip()));
        }

        if (settings.getOle2() != null) {
            builder.ole2(Ole2Limits.fromSettings(settings.getOle2()));
        }

        if (settings.getEml() != null) {
            builder.eml(EmlLimits.fromSettings(settings.getEml()));
        }

        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int timeoutSeconds;
        private PdfLimits pdf;
        private OoxmlLimits ooxml;
        private ZipLimits zip;
        private Ole2Limits ole2;
        private EmlLimits eml;

        private Builder() {
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder pdf(PdfLimits pdf) {
            this.pdf = pdf;
            return this;
        }

        public Builder ooxml(OoxmlLimits ooxml) {
            this.ooxml = ooxml;
            return this;
        }

        public Builder zip(ZipLimits zip) {
            this.zip = zip;
            return this;
        }

        public Builder ole2(Ole2Limits ole2) {
            this.ole2 = ole2;
            return this;
        }

        public Builder eml(EmlLimits eml) {
            this.eml = eml;
            return this;
        }

        public ParserConfig build() {
            return new ParserConfig(this);
        }
    }

    public static class PdfLimits {
        private final int maxPages;
        private final int maxStreamSizeMB;

        private PdfLimits(int maxPages, int maxStreamSizeMB) {
            this.maxPages = maxPages;
            this.maxStreamSizeMB = maxStreamSizeMB;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public int getMaxStreamSizeMB() {
            return maxStreamSizeMB;
        }

        public static PdfLimits fromSettings(ScannerConfig.PdfSettings settings) {
            return new PdfLimits(
                    settings.getMaxPages(),
                    settings.getMaxStreamSizeMB()
            );
        }

        public static PdfLimits of(int maxPages, int maxStreamSizeMB) {
            return new PdfLimits(maxPages, maxStreamSizeMB);
        }
    }

    public static class OoxmlLimits {
        private final int maxEntries;
        private final boolean disableEntityExpansion;
        private final boolean disableDTD;

        private OoxmlLimits(int maxEntries, boolean disableEntityExpansion, boolean disableDTD) {
            this.maxEntries = maxEntries;
            this.disableEntityExpansion = disableEntityExpansion;
            this.disableDTD = disableDTD;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public boolean isDisableEntityExpansion() {
            return disableEntityExpansion;
        }

        public boolean isDisableDTD() {
            return disableDTD;
        }

        public static OoxmlLimits fromSettings(ScannerConfig.OoxmlSettings settings) {
            return new OoxmlLimits(
                    settings.getMaxEntries(),
                    settings.isDisableEntityExpansion(),
                    settings.isDisableDTD()
            );
        }

        public static OoxmlLimits of(int maxEntries, boolean disableEntityExpansion, boolean disableDTD) {
            return new OoxmlLimits(maxEntries, disableEntityExpansion, disableDTD);
        }
    }

    public static class ZipLimits {
        private final int maxDepth;
        private final int maxEntries;
        private final int maxRatio;
        private final int maxCumulativeSizeMB;

        private ZipLimits(int maxDepth, int maxEntries, int maxRatio, int maxCumulativeSizeMB) {
            this.maxDepth = maxDepth;
            this.maxEntries = maxEntries;
            this.maxRatio = maxRatio;
            this.maxCumulativeSizeMB = maxCumulativeSizeMB;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public int getMaxRatio() {
            return maxRatio;
        }

        public int getMaxCumulativeSizeMB() {
            return maxCumulativeSizeMB;
        }

        public static ZipLimits fromSettings(ScannerConfig.ZipSettings settings) {
            return new ZipLimits(
                    settings.getMaxDepth(),
                    settings.getMaxEntries(),
                    settings.getMaxRatio(),
                    settings.getMaxCumulativeSizeMB()
            );
        }

        public static ZipLimits of(int maxDepth, int maxEntries, int maxRatio, int maxCumulativeSizeMB) {
            return new ZipLimits(maxDepth, maxEntries, maxRatio, maxCumulativeSizeMB);
        }
    }

    public static class Ole2Limits {
        private final int maxStreams;
        private final boolean skipMacros;

        private Ole2Limits(int maxStreams, boolean skipMacros) {
            this.maxStreams = maxStreams;
            this.skipMacros = skipMacros;
        }

        public int getMaxStreams() {
            return maxStreams;
        }

        public boolean isSkipMacros() {
            return skipMacros;
        }

        public static Ole2Limits fromSettings(ScannerConfig.Ole2Settings settings) {
            return new Ole2Limits(
                    settings.getMaxStreams(),
                    settings.isSkipMacros()
            );
        }

        public static Ole2Limits of(int maxStreams, boolean skipMacros) {
            return new Ole2Limits(maxStreams, skipMacros);
        }
    }

    public static class EmlLimits {
        private final int maxMIMEParts;
        private final int maxRecursionDepth;

        private EmlLimits(int maxMIMEParts, int maxRecursionDepth) {
            this.maxMIMEParts = maxMIMEParts;
            this.maxRecursionDepth = maxRecursionDepth;
        }

        public int getMaxMIMEParts() {
            return maxMIMEParts;
        }

        public int getMaxRecursionDepth() {
            return maxRecursionDepth;
        }

        public static EmlLimits fromSettings(ScannerConfig.EmlSettings settings) {
            return new EmlLimits(
                    settings.getMaxMIMEParts(),
                    settings.getMaxRecursionDepth()
            );
        }

        public static EmlLimits of(int maxMIMEParts, int maxRecursionDepth) {
            return new EmlLimits(maxMIMEParts, maxRecursionDepth);
        }
    }
}
