package com.dlp.discovery.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void testLoadDefaultConfig() {
        ScannerConfig config = ConfigLoader.load();
        
        assertThat(config).isNotNull();
        assertThat(config.getScanner()).isNotNull();
        assertThat(config.getScanner().getTargets()).isNotEmpty();
    }

    @Test
    void testLoadConfigWithVariableSubstitution(@TempDir Path tempDir) throws Exception {
        String yamlContent = """
            scanner:
              targets:
                - ${user.home}/documents
              excludePaths:
                - "**/.git/**"
              maxFileSizeBytes: 104857600
              parser:
                timeoutSeconds: 30
              cache:
                path: ${java.io.tmpdir}/cache
                maxEntries: 1000
            """;
        
        Path configFile = tempDir.resolve("test-config.yaml");
        Files.writeString(configFile, yamlContent);
        
        ScannerConfig config = ConfigLoader.load(configFile.toString());
        
        assertThat(config).isNotNull();
        assertThat(config.getScanner()).isNotNull();
        assertThat(config.getScanner().getTargets()).contains(System.getProperty("user.home") + "/documents");
        assertThat(config.getScanner().getCache().getPath()).contains(System.getProperty("java.io.tmpdir"));
    }

    @Test
    void testLoadConfigFileNotFound() {
        ScannerConfig config = ConfigLoader.load("nonexistent-config.yaml");
        
        assertThat(config).isNotNull();
        assertThat(config.getScanner()).isNotNull();
        assertThat(config.getScanner().getTargets()).isNotEmpty();
    }

    @Test
    void testDefaultConfigHasAllRequiredSettings() {
        ScannerConfig config = ConfigLoader.load("nonexistent.yaml");
        
        assertThat(config.getScanner()).isNotNull();
        assertThat(config.getScanner().getParser()).isNotNull();
        assertThat(config.getScanner().getCache()).isNotNull();
        assertThat(config.getScanner().getResults()).isNotNull();
        assertThat(config.getScanner().getTelemetry()).isNotNull();
        assertThat(config.getScanner().getManagementServer()).isNotNull();
        assertThat(config.getScanner().getRules()).isNotNull();
        
        assertThat(config.getScanner().getParser().getPdf()).isNotNull();
        assertThat(config.getScanner().getParser().getOoxml()).isNotNull();
        assertThat(config.getScanner().getParser().getZip()).isNotNull();
        assertThat(config.getScanner().getParser().getOle2()).isNotNull();
        assertThat(config.getScanner().getParser().getEml()).isNotNull();
    }
}
