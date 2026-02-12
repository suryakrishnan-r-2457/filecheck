# Configuration Guide

This document describes all configuration options for the DLP Scanner application.

## Table of Contents

- [Configuration File](#configuration-file)
- [Configuration Sections](#configuration-sections)
  - [Scanner Targets](#scanner-targets)
  - [File Filtering](#file-filtering)
  - [Scheduling](#scheduling)
  - [Rate Limiting](#rate-limiting)
  - [Parser Limits](#parser-limits)
  - [Cache Settings](#cache-settings)
  - [Results Storage](#results-storage)
  - [Telemetry](#telemetry)
  - [Management Server](#management-server)
  - [Rules](#rules)
- [Environment Variables](#environment-variables)
- [Configuration Examples](#configuration-examples)
- [Best Practices](#best-practices)

## Configuration File

### Location

The configuration file is located at:
```
scanner-app/src/main/resources/application.yaml
```

### Format

The configuration uses YAML format. All settings are under the `scanner:` root key.

```yaml
scanner:
  targets:
    - "/path/to/scan"
  # ... other settings
```

## Configuration Sections

### Scanner Targets

Define which directories the scanner should monitor and scan.

```yaml
scanner:
  targets:
    - "${user.home}/Documents"
    - "${user.home}/Desktop"
    - "${user.home}/Downloads"
```

**Property:** `scanner.targets`  
**Type:** List of strings  
**Required:** Yes  
**Description:** List of directory paths to scan for sensitive content. Supports environment variable substitution.

### File Filtering

Control which files are scanned and which are excluded.

#### Exclude Paths

```yaml
scanner:
  excludePaths:
    - "**/node_modules/**"
    - "**/.git/**"
    - "C:/Windows/**"
    - "C:/Program Files/**"
    - "**/*.exe"
    - "**/*.dll"
```

**Property:** `scanner.excludePaths`  
**Type:** List of glob patterns  
**Required:** No  
**Description:** Glob patterns for paths to exclude from scanning. Patterns support `*` (single level) and `**` (recursive).

#### Include Extensions

```yaml
scanner:
  includeExtensions:
    - ".pdf"
    - ".doc"
    - ".docx"
    - ".pptx"
    - ".xlsx"
    - ".eml"
    - ".msg"
    - ".zip"
    - ".txt"
    - ".csv"
    - ".rtf"
```

**Property:** `scanner.includeExtensions`  
**Type:** List of strings  
**Required:** Yes  
**Description:** File extensions to include in scanning. Extensions must include the leading dot.

#### Maximum File Size

```yaml
scanner:
  maxFileSizeBytes: 104857600  # 100 MB
```

**Property:** `scanner.maxFileSizeBytes`  
**Type:** Integer (bytes)  
**Default:** 104857600 (100 MB)  
**Description:** Maximum file size to process. Files larger than this limit are skipped.

### Scheduling

Configure when and how often scans run.

```yaml
scanner:
  fullScanCronExpression: "0 2 * * SUN"   # Every Sunday at 2 AM
  incrementalScanIntervalHours: 4
  idleOnlyScanning: true
  idleThresholdMinutes: 5
```

**Property:** `scanner.fullScanCronExpression`  
**Type:** Cron expression  
**Default:** `"0 2 * * SUN"`  
**Description:** Schedule for full scans using standard cron syntax.

**Property:** `scanner.incrementalScanIntervalHours`  
**Type:** Integer (hours)  
**Default:** 4  
**Description:** Interval between incremental scans that check for changed files.

**Property:** `scanner.idleOnlyScanning`  
**Type:** Boolean  
**Default:** true  
**Description:** Whether to scan only when the system is idle.

**Property:** `scanner.idleThresholdMinutes`  
**Type:** Integer (minutes)  
**Default:** 5  
**Description:** Minutes of inactivity before system is considered idle.

### Rate Limiting

Control scanner resource usage and performance impact.

```yaml
scanner:
  maxFilesPerSecond: 10
  businessHoursCpuPercent: 20    # 08:00–18:00
  offHoursCpuPercent: 80
```

**Property:** `scanner.maxFilesPerSecond`  
**Type:** Integer  
**Default:** 10  
**Description:** Maximum number of files to process per second.

**Property:** `scanner.businessHoursCpuPercent`  
**Type:** Integer (1-100)  
**Default:** 20  
**Description:** Maximum CPU usage percentage during business hours (08:00-18:00).

**Property:** `scanner.offHoursCpuPercent`  
**Type:** Integer (1-100)  
**Default:** 80  
**Description:** Maximum CPU usage percentage during off-hours.

### Parser Limits

Configure limits for parsing different file formats to prevent resource exhaustion and security issues.

#### General Parser Settings

```yaml
scanner:
  parser:
    timeoutSeconds: 30
```

**Property:** `scanner.parser.timeoutSeconds`  
**Type:** Integer (seconds)  
**Default:** 30  
**Description:** Maximum time allowed for parsing a single file.

#### PDF Parser

```yaml
scanner:
  parser:
    pdf:
      maxPages: 500
      maxStreamSizeMB: 64
```

**Property:** `scanner.parser.pdf.maxPages`  
**Type:** Integer  
**Default:** 500  
**Description:** Maximum number of pages to process in a PDF document.

**Property:** `scanner.parser.pdf.maxStreamSizeMB`  
**Type:** Integer (MB)  
**Default:** 64  
**Description:** Maximum size of PDF streams to process.

#### OOXML Parser (Office Documents)

```yaml
scanner:
  parser:
    ooxml:
      maxEntries: 500
      disableEntityExpansion: true
      disableDTD: true
```

**Property:** `scanner.parser.ooxml.maxEntries`  
**Type:** Integer  
**Default:** 500  
**Description:** Maximum number of entries in OOXML files (.docx, .xlsx, .pptx).

**Property:** `scanner.parser.ooxml.disableEntityExpansion`  
**Type:** Boolean  
**Default:** true  
**Description:** Disable XML entity expansion to prevent XXE attacks.

**Property:** `scanner.parser.ooxml.disableDTD`  
**Type:** Boolean  
**Default:** true  
**Description:** Disable DTD processing to prevent XXE attacks.

#### ZIP Parser

```yaml
scanner:
  parser:
    zip:
      maxDepth: 3
      maxEntries: 500
      maxRatio: 100
      maxCumulativeSizeMB: 256
```

**Property:** `scanner.parser.zip.maxDepth`  
**Type:** Integer  
**Default:** 3  
**Description:** Maximum nesting depth for nested ZIP files (zip bomb protection).

**Property:** `scanner.parser.zip.maxEntries`  
**Type:** Integer  
**Default:** 500  
**Description:** Maximum number of entries in a ZIP archive.

**Property:** `scanner.parser.zip.maxRatio`  
**Type:** Integer  
**Default:** 100  
**Description:** Maximum compression ratio allowed (uncompressed/compressed).

**Property:** `scanner.parser.zip.maxCumulativeSizeMB`  
**Type:** Integer (MB)  
**Default:** 256  
**Description:** Maximum total uncompressed size of all entries.

#### OLE2 Parser (Legacy Office Documents)

```yaml
scanner:
  parser:
    ole2:
      maxStreams: 50
      skipMacros: true
```

**Property:** `scanner.parser.ole2.maxStreams`  
**Type:** Integer  
**Default:** 50  
**Description:** Maximum number of streams in OLE2 files (.doc, .xls, .ppt).

**Property:** `scanner.parser.ole2.skipMacros`  
**Type:** Boolean  
**Default:** true  
**Description:** Whether to skip processing macro streams.

#### EML Parser (Email Messages)

```yaml
scanner:
  parser:
    eml:
      maxMIMEParts: 100
      maxRecursionDepth: 5
```

**Property:** `scanner.parser.eml.maxMIMEParts`  
**Type:** Integer  
**Default:** 100  
**Description:** Maximum number of MIME parts in an email message.

**Property:** `scanner.parser.eml.maxRecursionDepth`  
**Type:** Integer  
**Default:** 5  
**Description:** Maximum recursion depth for nested MIME parts.

### Cache Settings

Configure the file hash cache to avoid re-scanning unchanged files.

```yaml
scanner:
  cache:
    path: "${programdata}/DLPScanner/cache"
    maxEntries: 1000000
```

**Property:** `scanner.cache.path`  
**Type:** String (path)  
**Required:** Yes  
**Description:** Directory path for cache storage. Supports environment variable substitution.

**Property:** `scanner.cache.maxEntries`  
**Type:** Integer  
**Default:** 1000000  
**Description:** Maximum number of entries in the cache.

### Results Storage

Configure where and how scan results are stored.

```yaml
scanner:
  results:
    dbPath: "${programdata}/DLPScanner/results.db"
    maxSizeMB: 1024
    uploadFullText: true
```

**Property:** `scanner.results.dbPath`  
**Type:** String (path)  
**Required:** Yes  
**Description:** Path to the results database file. Supports environment variable substitution.

**Property:** `scanner.results.maxSizeMB`  
**Type:** Integer (MB)  
**Default:** 1024  
**Description:** Maximum size of the results database.

**Property:** `scanner.results.uploadFullText`  
**Type:** Boolean  
**Default:** true  
**Description:** Whether to upload full text content when findings exist (for evidence).

### Telemetry

Configure telemetry data collection and storage.

```yaml
scanner:
  telemetry:
    dbPath: "${programdata}/DLPScanner/telemetry.db"
    maxSizeMB: 500
```

**Property:** `scanner.telemetry.dbPath`  
**Type:** String (path)  
**Required:** Yes  
**Description:** Path to the telemetry database file. Supports environment variable substitution.

**Property:** `scanner.telemetry.maxSizeMB`  
**Type:** Integer (MB)  
**Default:** 500  
**Description:** Maximum size of the telemetry database.

### Management Server

Configure integration with the central management server.

```yaml
scanner:
  managementServer:
    url: "https://dlp-mgmt.corp.com/api/v1"
    uploadBatchSize: 50
    uploadIntervalSeconds: 60
    apiKeyEnvVar: "DLP_MGMT_API_KEY"
```

**Property:** `scanner.managementServer.url`  
**Type:** String (URL)  
**Required:** Yes  
**Description:** Base URL of the management server API.

**Property:** `scanner.managementServer.uploadBatchSize`  
**Type:** Integer  
**Default:** 50  
**Description:** Number of results to upload in a single batch.

**Property:** `scanner.managementServer.uploadIntervalSeconds`  
**Type:** Integer (seconds)  
**Default:** 60  
**Description:** Interval between result upload attempts.

**Property:** `scanner.managementServer.apiKeyEnvVar`  
**Type:** String  
**Default:** `"DLP_MGMT_API_KEY"`  
**Description:** Name of the environment variable containing the API key for authentication.

### Rules

Configure DLP rule loading and management.

```yaml
scanner:
  rules:
    path: "${programdata}/DLPScanner/rules"
    autoReload: true
    signatureVerification: true
```

**Property:** `scanner.rules.path`  
**Type:** String (path)  
**Required:** Yes  
**Description:** Directory containing rule files. Supports environment variable substitution.

**Property:** `scanner.rules.autoReload`  
**Type:** Boolean  
**Default:** true  
**Description:** Whether to automatically reload rules when files change.

**Property:** `scanner.rules.signatureVerification`  
**Type:** Boolean  
**Default:** true  
**Description:** Whether to verify digital signatures on rule files.

## Environment Variables

The configuration supports environment variable substitution using the `${variable}` syntax.

### Supported Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `${user.home}` | User's home directory | `/home/username` or `C:\Users\username` |
| `${programdata}` | Application data directory | `/var/lib` or `C:\ProgramData` |
| `${DLP_MGMT_API_KEY}` | Management server API key | Set via environment |

### Custom Environment Variables

You can reference any environment variable in the configuration:

```yaml
scanner:
  targets:
    - "${SCAN_PATH_1}"
    - "${SCAN_PATH_2}"
  cache:
    path: "${CACHE_DIR}/scanner"
```

Then set the variables:
```bash
export SCAN_PATH_1=/home/user/documents
export SCAN_PATH_2=/home/user/downloads
export CACHE_DIR=/var/cache/dlpscanner
```

## Configuration Examples

### Minimal Configuration

For testing or development environments:

```yaml
scanner:
  targets:
    - "${user.home}/test-data"
  
  includeExtensions:
    - ".txt"
    - ".pdf"
  
  maxFileSizeBytes: 10485760  # 10 MB
  
  fullScanCronExpression: "0 * * * *"  # Every hour
  incrementalScanIntervalHours: 1
  idleOnlyScanning: false
  
  cache:
    path: "/tmp/scanner-cache"
    maxEntries: 10000
  
  results:
    dbPath: "/tmp/scanner-results.db"
    maxSizeMB: 100
  
  telemetry:
    dbPath: "/tmp/scanner-telemetry.db"
    maxSizeMB: 50
  
  managementServer:
    url: "http://localhost:8080/api/v1"
    uploadBatchSize: 10
    uploadIntervalSeconds: 300
  
  rules:
    path: "/tmp/scanner-rules"
    autoReload: true
    signatureVerification: false
```

### Enterprise Production Configuration

For production deployments with high security requirements:

```yaml
scanner:
  targets:
    - "${user.home}/Documents"
    - "${user.home}/Desktop"
    - "${user.home}/Downloads"
    - "/shares/department"
  
  excludePaths:
    - "**/node_modules/**"
    - "**/.git/**"
    - "**/target/**"
    - "**/build/**"
    - "**/.cache/**"
    - "**/temp/**"
    - "C:/Windows/**"
    - "C:/Program Files/**"
    - "**/*.exe"
    - "**/*.dll"
    - "**/*.so"
  
  includeExtensions:
    - ".pdf"
    - ".doc"
    - ".docx"
    - ".ppt"
    - ".pptx"
    - ".xls"
    - ".xlsx"
    - ".eml"
    - ".msg"
    - ".txt"
    - ".csv"
    - ".rtf"
    - ".zip"
    - ".7z"
  
  maxFileSizeBytes: 104857600  # 100 MB
  
  fullScanCronExpression: "0 2 * * SUN"  # Sunday 2 AM
  incrementalScanIntervalHours: 4
  idleOnlyScanning: true
  idleThresholdMinutes: 5
  
  maxFilesPerSecond: 10
  businessHoursCpuPercent: 20
  offHoursCpuPercent: 80
  
  parser:
    timeoutSeconds: 30
    pdf:
      maxPages: 500
      maxStreamSizeMB: 64
    ooxml:
      maxEntries: 500
      disableEntityExpansion: true
      disableDTD: true
    zip:
      maxDepth: 3
      maxEntries: 500
      maxRatio: 100
      maxCumulativeSizeMB: 256
    ole2:
      maxStreams: 50
      skipMacros: true
    eml:
      maxMIMEParts: 100
      maxRecursionDepth: 5
  
  cache:
    path: "${programdata}/DLPScanner/cache"
    maxEntries: 1000000
  
  results:
    dbPath: "${programdata}/DLPScanner/results.db"
    maxSizeMB: 2048
    uploadFullText: true
  
  telemetry:
    dbPath: "${programdata}/DLPScanner/telemetry.db"
    maxSizeMB: 500
  
  managementServer:
    url: "https://dlp-mgmt.corp.com/api/v1"
    uploadBatchSize: 50
    uploadIntervalSeconds: 60
    apiKeyEnvVar: "DLP_MGMT_API_KEY"
  
  rules:
    path: "${programdata}/DLPScanner/rules"
    autoReload: true
    signatureVerification: true
```

### High-Performance Configuration

For scanning large file repositories with minimal impact:

```yaml
scanner:
  targets:
    - "/data/file-shares"
  
  excludePaths:
    - "**/archive/**"
    - "**/backup/**"
  
  includeExtensions:
    - ".pdf"
    - ".docx"
    - ".xlsx"
  
  maxFileSizeBytes: 52428800  # 50 MB
  
  fullScanCronExpression: "0 22 * * *"  # Daily at 10 PM
  incrementalScanIntervalHours: 2
  idleOnlyScanning: false
  
  maxFilesPerSecond: 50
  businessHoursCpuPercent: 50
  offHoursCpuPercent: 100
  
  parser:
    timeoutSeconds: 15
    pdf:
      maxPages: 200
      maxStreamSizeMB: 32
    ooxml:
      maxEntries: 200
    zip:
      maxDepth: 2
      maxEntries: 200
      maxCumulativeSizeMB: 128
  
  cache:
    path: "/fast-storage/scanner-cache"
    maxEntries: 5000000
  
  results:
    dbPath: "/fast-storage/scanner-results.db"
    maxSizeMB: 4096
    uploadFullText: false
  
  managementServer:
    uploadBatchSize: 100
    uploadIntervalSeconds: 30
```

### Secure Environment Configuration

For highly sensitive environments with strict security controls:

```yaml
scanner:
  targets:
    - "${SECURE_SCAN_PATH}"
  
  excludePaths:
    - "**/.ssh/**"
    - "**/.gnupg/**"
    - "**/credentials/**"
  
  includeExtensions:
    - ".pdf"
    - ".docx"
  
  maxFileSizeBytes: 10485760  # 10 MB
  
  fullScanCronExpression: "0 3 * * *"
  incrementalScanIntervalHours: 6
  idleOnlyScanning: true
  idleThresholdMinutes: 10
  
  maxFilesPerSecond: 5
  businessHoursCpuPercent: 10
  offHoursCpuPercent: 50
  
  parser:
    timeoutSeconds: 20
    pdf:
      maxPages: 100
      maxStreamSizeMB: 16
    ooxml:
      maxEntries: 100
      disableEntityExpansion: true
      disableDTD: true
    zip:
      maxDepth: 1
      maxEntries: 100
      maxRatio: 50
      maxCumulativeSizeMB: 64
    ole2:
      maxStreams: 20
      skipMacros: true
    eml:
      maxMIMEParts: 50
      maxRecursionDepth: 2
  
  results:
    uploadFullText: false
  
  rules:
    signatureVerification: true
```

## Best Practices

### Security

1. **Enable Signature Verification**: Always keep `scanner.rules.signatureVerification: true` in production to prevent tampering with rule files.

2. **Protect API Keys**: Never hardcode API keys in the configuration file. Use environment variables:
   ```bash
   export DLP_MGMT_API_KEY="your-secure-key"
   ```

3. **Disable Dangerous XML Features**: Keep these settings enabled for security:
   - `scanner.parser.ooxml.disableEntityExpansion: true`
   - `scanner.parser.ooxml.disableDTD: true`

4. **Skip Macro Processing**: Set `scanner.parser.ole2.skipMacros: true` to avoid executing malicious macros.

5. **Limit Archive Depth**: Use `scanner.parser.zip.maxDepth: 3` or lower to prevent zip bomb attacks.

### Performance

1. **Schedule Wisely**: Run full scans during off-hours to minimize user impact:
   ```yaml
   fullScanCronExpression: "0 2 * * SUN"  # Sunday 2 AM
   ```

2. **Use Idle Detection**: Enable `idleOnlyScanning: true` to scan only when the system is idle.

3. **Adjust CPU Limits**: Set conservative CPU limits during business hours:
   ```yaml
   businessHoursCpuPercent: 20
   offHoursCpuPercent: 80
   ```

4. **Optimize Cache**: Increase cache size for large file sets:
   ```yaml
   cache:
     maxEntries: 5000000  # For millions of files
   ```

5. **Set Appropriate Timeouts**: Balance between thoroughness and performance:
   ```yaml
   parser:
     timeoutSeconds: 30  # Adjust based on file complexity
   ```

### Reliability

1. **Monitor Database Sizes**: Set appropriate limits to prevent disk exhaustion:
   ```yaml
   results:
     maxSizeMB: 2048
   telemetry:
     maxSizeMB: 500
   ```

2. **Enable Auto-Reload**: Allow rules to be updated without restart:
   ```yaml
   rules:
     autoReload: true
   ```

3. **Batch Uploads**: Configure reasonable batch sizes for network efficiency:
   ```yaml
   managementServer:
     uploadBatchSize: 50
     uploadIntervalSeconds: 60
   ```

4. **Set File Size Limits**: Prevent processing of extremely large files:
   ```yaml
   maxFileSizeBytes: 104857600  # 100 MB
   ```

### Maintenance

1. **Use Environment Variables**: Reference paths and sensitive values via environment variables for easier deployment:
   ```yaml
   targets:
     - "${SCAN_PATH}"
   cache:
     path: "${CACHE_DIR}"
   ```

2. **Document Custom Settings**: Comment your configuration changes:
   ```yaml
   # Increased for large document repository
   maxFileSizeBytes: 209715200  # 200 MB
   ```

3. **Test Configuration Changes**: Start with minimal changes and test thoroughly before rolling out to production.

4. **Version Control**: Store configuration in version control (excluding secrets) to track changes over time.

5. **Regular Reviews**: Periodically review exclusion patterns and scanner targets to ensure they remain appropriate.

### Exclusion Patterns

1. **Exclude Build Artifacts**: Skip generated files:
   ```yaml
   excludePaths:
     - "**/node_modules/**"
     - "**/target/**"
     - "**/build/**"
   ```

2. **Exclude System Directories**: Avoid scanning operating system files:
   ```yaml
   excludePaths:
     - "C:/Windows/**"
     - "C:/Program Files/**"
     - "/System/**"
   ```

3. **Exclude Binary Files**: Skip executables and libraries:
   ```yaml
   excludePaths:
     - "**/*.exe"
     - "**/*.dll"
     - "**/*.so"
   ```

4. **Use Specific Patterns**: Be as specific as possible to avoid accidental exclusions:
   ```yaml
   # Good: Specific path
   excludePaths:
     - "**/build/output/**"
   
   # Bad: Too broad
   excludePaths:
     - "**/output/**"
   ```

### File Type Selection

1. **Include Relevant Types**: Only scan file types that can contain sensitive data:
   ```yaml
   includeExtensions:
     - ".pdf"
     - ".docx"
     - ".txt"
   ```

2. **Avoid Binary Formats**: Don't include types that can't contain readable sensitive data:
   ```yaml
   # Don't include:
   # - ".exe"
   # - ".dll"
   # - ".jpg" (unless scanning for steganography)
   ```

3. **Consider Archives**: Include archive formats if they may contain sensitive files:
   ```yaml
   includeExtensions:
     - ".zip"
     - ".7z"
   ```

### Resource Management

1. **Balance Thoroughness and Performance**: Adjust parser limits based on your environment:
   ```yaml
   # Conservative (secure but slower)
   parser:
     pdf:
       maxPages: 500
   
   # Performance-focused (faster but less thorough)
   parser:
     pdf:
       maxPages: 200
   ```

2. **Monitor Resource Usage**: Track CPU and memory usage and adjust settings accordingly.

3. **Scale Based on Volume**: Adjust `maxFilesPerSecond` based on total file count and scan frequency.

---

For additional help or questions about configuration, please refer to the [README](../README.md) or contact your system administrator.
