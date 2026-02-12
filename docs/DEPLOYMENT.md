# Deployment Guide

This document provides comprehensive instructions for deploying, configuring, and maintaining the DLP Scanner application in production environments.

## Table of Contents

- [System Requirements](#system-requirements)
- [Pre-Installation Checklist](#pre-installation-checklist)
- [Installation Steps](#installation-steps)
- [Windows Service Installation](#windows-service-installation)
- [Service Management](#service-management)
- [Log File Locations](#log-file-locations)
- [Troubleshooting](#troubleshooting)
- [Upgrading](#upgrading)
- [Uninstallation](#uninstallation)

## System Requirements

### Operating System

- **Windows**: Windows 10 or later, Windows Server 2016 or later
- **Linux**: Any modern distribution with systemd support (Ubuntu 20.04+, RHEL 8+, etc.)

### Hardware Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| **CPU** | 2 cores | 4+ cores |
| **RAM** | 4 GB | 8 GB or more |
| **Disk Space** | 10 GB free | 50 GB free |
| **Network** | 1 Mbps | 10 Mbps |

### Software Requirements

- **Java Runtime**: JRE 17 or later (OpenJDK or Oracle JDK)
- **Hyperscan Library**: Version 5.4 or later (bundled with application)
- **Administrator/Root Access**: Required for service installation

### Additional Considerations

- **Disk I/O**: SSD recommended for cache and database storage
- **Network Access**: Outbound HTTPS access to management server
- **Permissions**: Read access to target scan directories
- **Antivirus Exclusions**: May need to exclude scanner directories from real-time scanning

## Pre-Installation Checklist

Before installing the DLP Scanner, ensure you have:

- [ ] Verified system meets minimum requirements
- [ ] Downloaded the latest release package
- [ ] Obtained the management server URL
- [ ] Obtained the API key for management server authentication
- [ ] Identified target directories to scan
- [ ] Planned installation directory location
- [ ] Reviewed firewall requirements
- [ ] Backed up any existing configuration (for upgrades)

## Installation Steps

### Step 1: Install Java Runtime

#### Windows

1. Download Java 17 or later from:
   - [Eclipse Temurin](https://adoptium.net/) (recommended)
   - [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)

2. Run the installer with default options

3. Verify installation:
   ```cmd
   java -version
   ```
   
   Expected output:
   ```
   openjdk version "17.0.x" ...
   ```

#### Linux

For Ubuntu/Debian:
```bash
sudo apt update
sudo apt install openjdk-17-jre
```

For RHEL/CentOS:
```bash
sudo yum install java-17-openjdk
```

Verify installation:
```bash
java -version
```

### Step 2: Create Installation Directory

#### Windows

```cmd
mkdir C:\Program Files\DLPScanner
mkdir C:\ProgramData\DLPScanner
mkdir C:\ProgramData\DLPScanner\logs
mkdir C:\ProgramData\DLPScanner\cache
mkdir C:\ProgramData\DLPScanner\rules
```

#### Linux

```bash
sudo mkdir -p /opt/dlpscanner
sudo mkdir -p /var/lib/DLPScanner/{logs,cache,rules}
sudo mkdir -p /etc/dlpscanner
```

### Step 3: Extract Application Files

#### Windows

1. Download the release package (e.g., `dlpscanner-1.0.0.zip`)

2. Extract to installation directory:
   ```cmd
   cd C:\Program Files\DLPScanner
   tar -xf dlpscanner-1.0.0.zip
   ```
   
   Or use Windows Explorer to extract the files.

3. Verify the directory structure:
   ```
   C:\Program Files\DLPScanner\
   ├── bin/
   │   ├── scanner.bat
   │   └── install-service.bat
   ├── lib/
   │   ├── scanner-app.jar
   │   ├── hyperscan-jni.jar
   │   └── [other dependencies]
   └── config/
       └── application.yaml
   ```

#### Linux

```bash
cd /opt/dlpscanner
sudo tar -xzf dlpscanner-1.0.0.tar.gz
sudo chown -R root:root /opt/dlpscanner
sudo chmod +x /opt/dlpscanner/bin/scanner.sh
```

Verify the directory structure:
```
/opt/dlpscanner/
├── bin/
│   ├── scanner.sh
│   └── install-service.sh
├── lib/
│   ├── scanner-app.jar
│   ├── hyperscan-jni.jar
│   └── [other dependencies]
└── config/
    └── application.yaml
```

### Step 4: Configure Application

1. Navigate to the configuration directory:
   
   **Windows:**
   ```cmd
   cd C:\Program Files\DLPScanner\config
   ```
   
   **Linux:**
   ```bash
   cd /opt/dlpscanner/config
   ```

2. Edit `application.yaml` with your preferred text editor:
   
   **Windows:**
   ```cmd
   notepad application.yaml
   ```
   
   **Linux:**
   ```bash
   sudo nano application.yaml
   ```

3. Configure essential settings:

   ```yaml
   scanner:
     # Define directories to scan
     targets:
       - "${user.home}/Documents"
       - "${user.home}/Desktop"
       - "${user.home}/Downloads"
     
     # File types to scan
     includeExtensions:
       - ".pdf"
       - ".doc"
       - ".docx"
       - ".pptx"
       - ".xlsx"
       - ".txt"
     
     # Exclude paths (Windows example)
     excludePaths:
       - "C:/Windows/**"
       - "C:/Program Files/**"
       - "**/node_modules/**"
       - "**/.git/**"
     
     # Management server configuration
     managementServer:
       url: "https://dlp-mgmt.your-company.com/api/v1"
       uploadBatchSize: 50
       uploadIntervalSeconds: 60
       apiKeyEnvVar: "DLP_MGMT_API_KEY"
     
     # Cache and results storage
     cache:
       path: "${programdata}/DLPScanner/cache"
       maxEntries: 1000000
     
     results:
       dbPath: "${programdata}/DLPScanner/results.db"
       maxSizeMB: 1024
     
     telemetry:
       dbPath: "${programdata}/DLPScanner/telemetry.db"
       maxSizeMB: 500
     
     # Rules directory
     rules:
       path: "${programdata}/DLPScanner/rules"
       autoReload: true
       signatureVerification: true
   ```

4. Save and close the file.

For detailed configuration options, see [CONFIGURATION.md](CONFIGURATION.md).

### Step 5: Set Up Rules Directory

1. Copy rule files to the rules directory:

   **Windows:**
   ```cmd
   copy rules\*.dlpr C:\ProgramData\DLPScanner\rules\
   ```
   
   **Linux:**
   ```bash
   sudo cp rules/*.dlpr /var/lib/DLPScanner/rules/
   ```

2. Verify rules are in place:
   
   **Windows:**
   ```cmd
   dir C:\ProgramData\DLPScanner\rules
   ```
   
   **Linux:**
   ```bash
   ls -l /var/lib/DLPScanner/rules
   ```

### Step 6: Set Environment Variables

Set the API key for management server authentication:

#### Windows (System-wide)

1. Open System Properties → Advanced → Environment Variables

2. Under "System variables", click "New"

3. Set:
   - Variable name: `DLP_MGMT_API_KEY`
   - Variable value: `your-api-key-here`

4. Click OK to save

Alternatively, use PowerShell (requires administrator):
```powershell
[System.Environment]::SetEnvironmentVariable('DLP_MGMT_API_KEY', 'your-api-key-here', 'Machine')
```

#### Linux (System-wide)

Add to `/etc/environment`:
```bash
sudo sh -c 'echo "DLP_MGMT_API_KEY=your-api-key-here" >> /etc/environment'
```

Or for systemd service, create an environment file:
```bash
sudo sh -c 'echo "DLP_MGMT_API_KEY=your-api-key-here" > /etc/dlpscanner/environment'
sudo chmod 600 /etc/dlpscanner/environment
```

### Step 7: Test the Installation

Before installing as a service, test the application manually:

#### Windows

```cmd
cd C:\Program Files\DLPScanner
java -jar lib\scanner-app.jar --spring.config.location=config\application.yaml
```

#### Linux

```bash
cd /opt/dlpscanner
java -jar lib/scanner-app.jar --spring.config.location=config/application.yaml
```

The application should start and display log output. Press `Ctrl+C` to stop.

Check for:
- ✓ No error messages during startup
- ✓ Successful connection to management server
- ✓ Rules loaded successfully
- ✓ Scanner initialized

## Windows Service Installation

### Using Windows Service Wrapper (WinSW)

1. Download WinSW (Windows Service Wrapper):
   - [WinSW Releases](https://github.com/winsw/winsw/releases)
   - Download `WinSW-x64.exe`

2. Copy to installation directory and rename:
   ```cmd
   copy WinSW-x64.exe "C:\Program Files\DLPScanner\DLPScanner-service.exe"
   ```

3. Create service configuration file `DLPScanner-service.xml`:

   ```xml
   <service>
     <id>DLPScanner</id>
     <name>DLP Scanner Service</name>
     <description>Data Loss Prevention Scanner for endpoint monitoring</description>
     
     <executable>java</executable>
     <arguments>-Xms512m -Xmx2048m -jar "%BASE%\lib\scanner-app.jar" --spring.config.location="%BASE%\config\application.yaml"</arguments>
     
     <workingdirectory>C:\Program Files\DLPScanner</workingdirectory>
     
     <logpath>C:\ProgramData\DLPScanner\logs</logpath>
     <log mode="roll-by-size">
       <sizeThreshold>10240</sizeThreshold>
       <keepFiles>8</keepFiles>
     </log>
     
     <env name="DLP_MGMT_API_KEY" value="%DLP_MGMT_API_KEY%"/>
     
     <onfailure action="restart" delay="10 sec"/>
     <onfailure action="restart" delay="20 sec"/>
     <onfailure action="none"/>
     
     <resetfailure>1 hour</resetfailure>
     
     <startmode>Automatic</startmode>
     <delayedAutoStart>true</delayedAutoStart>
   </service>
   ```

4. Install the service (run as Administrator):
   ```cmd
   cd C:\Program Files\DLPScanner
   DLPScanner-service.exe install
   ```

5. Start the service:
   ```cmd
   DLPScanner-service.exe start
   ```

6. Verify service is running:
   ```cmd
   sc query DLPScanner
   ```

### Using NSSM (Alternative Method)

1. Download NSSM from [nssm.cc](https://nssm.cc/download)

2. Extract and run (as Administrator):
   ```cmd
   nssm install DLPScanner
   ```

3. In the NSSM GUI:
   - **Path**: `C:\Program Files\Java\jdk-17\bin\java.exe`
   - **Startup directory**: `C:\Program Files\DLPScanner`
   - **Arguments**: `-jar lib\scanner-app.jar --spring.config.location=config\application.yaml`
   - **Service name**: `DLPScanner`

4. Go to the "Details" tab:
   - **Display name**: `DLP Scanner Service`
   - **Description**: `Data Loss Prevention Scanner`
   - **Startup type**: `Automatic (Delayed Start)`

5. Go to the "Environment" tab and add:
   ```
   DLP_MGMT_API_KEY=your-api-key-here
   ```

6. Click "Install service"

## Linux Service Installation (systemd)

1. Create a systemd service file:
   ```bash
   sudo nano /etc/systemd/system/dlpscanner.service
   ```

2. Add the following content:

   ```ini
   [Unit]
   Description=DLP Scanner Service
   After=network.target
   
   [Service]
   Type=simple
   User=root
   WorkingDirectory=/opt/dlpscanner
   
   EnvironmentFile=/etc/dlpscanner/environment
   
   ExecStart=/usr/bin/java \
     -Xms512m \
     -Xmx2048m \
     -jar /opt/dlpscanner/lib/scanner-app.jar \
     --spring.config.location=/opt/dlpscanner/config/application.yaml
   
   Restart=on-failure
   RestartSec=10s
   
   StandardOutput=journal
   StandardError=journal
   SyslogIdentifier=dlpscanner
   
   [Install]
   WantedBy=multi-user.target
   ```

3. Reload systemd to recognize the new service:
   ```bash
   sudo systemctl daemon-reload
   ```

4. Enable the service to start on boot:
   ```bash
   sudo systemctl enable dlpscanner
   ```

5. Start the service:
   ```bash
   sudo systemctl start dlpscanner
   ```

6. Verify service is running:
   ```bash
   sudo systemctl status dlpscanner
   ```

## Service Management

### Windows

#### Start Service
```cmd
# Using sc
sc start DLPScanner

# Using net
net start DLPScanner

# Using Services GUI
services.msc
# Right-click "DLP Scanner Service" → Start
```

#### Stop Service
```cmd
# Using sc
sc stop DLPScanner

# Using net
net stop DLPScanner
```

#### Restart Service
```cmd
sc stop DLPScanner && sc start DLPScanner
```

#### Check Service Status
```cmd
sc query DLPScanner
```

#### View Service Configuration
```cmd
sc qc DLPScanner
```

### Linux

#### Start Service
```bash
sudo systemctl start dlpscanner
```

#### Stop Service
```bash
sudo systemctl stop dlpscanner
```

#### Restart Service
```bash
sudo systemctl restart dlpscanner
```

#### Check Service Status
```bash
sudo systemctl status dlpscanner
```

#### View Service Logs
```bash
# View recent logs
sudo journalctl -u dlpscanner -n 100

# Follow logs in real-time
sudo journalctl -u dlpscanner -f

# View logs from specific time
sudo journalctl -u dlpscanner --since "1 hour ago"
```

#### Enable Service (start on boot)
```bash
sudo systemctl enable dlpscanner
```

#### Disable Service (don't start on boot)
```bash
sudo systemctl disable dlpscanner
```

## Log File Locations

### Windows

| Log Type | Location |
|----------|----------|
| **Application Logs** | `C:\ProgramData\DLPScanner\logs\scanner.log` |
| **Archived Logs** | `C:\ProgramData\DLPScanner\logs\scanner.YYYY-MM-DD.log` |
| **Service Wrapper Logs** | `C:\ProgramData\DLPScanner\logs\DLPScanner-service.*.log` |

### Linux

| Log Type | Location |
|----------|----------|
| **Application Logs** | `/var/lib/DLPScanner/logs/scanner.log` |
| **Archived Logs** | `/var/lib/DLPScanner/logs/scanner.YYYY-MM-DD.log` |
| **System Logs (journald)** | `journalctl -u dlpscanner` |

### Log Rotation

Application logs are automatically rotated based on the following policy:

- **Rotation**: Daily
- **Retention**: 30 days
- **Maximum Total Size**: 1 GB
- **Format**: `scanner.YYYY-MM-DD.log`

To view current log:
```bash
# Windows
type C:\ProgramData\DLPScanner\logs\scanner.log

# Linux
tail -f /var/lib/DLPScanner/logs/scanner.log
```

### Log Levels

Log levels can be adjusted in `application.yaml` (requires restart):

```yaml
logging:
  level:
    root: INFO
    com.dlp.discovery: DEBUG
    org.apache.tika: WARN
```

## Troubleshooting

### Service Won't Start

#### Symptom
Service fails to start or immediately stops after starting.

#### Solutions

1. **Check Java Installation**
   ```cmd
   # Windows
   java -version
   
   # Linux
   java -version
   ```
   Ensure Java 17 or later is installed.

2. **Verify Configuration File**
   - Check `application.yaml` for syntax errors
   - Validate YAML using online validators
   - Ensure all required fields are present

3. **Check Permissions**
   
   **Windows:**
   - Ensure service account has read/write access to:
     - `C:\Program Files\DLPScanner`
     - `C:\ProgramData\DLPScanner`
   
   **Linux:**
   ```bash
   sudo chown -R root:root /opt/dlpscanner
   sudo chown -R root:root /var/lib/DLPScanner
   ```

4. **Review Logs**
   
   **Windows:**
   ```cmd
   type C:\ProgramData\DLPScanner\logs\scanner.log
   type C:\ProgramData\DLPScanner\logs\DLPScanner-service.err.log
   ```
   
   **Linux:**
   ```bash
   sudo journalctl -u dlpscanner -n 100 --no-pager
   ```

5. **Test Manual Startup**
   Run the application manually to see detailed error messages:
   
   **Windows:**
   ```cmd
   cd C:\Program Files\DLPScanner
   java -jar lib\scanner-app.jar
   ```
   
   **Linux:**
   ```bash
   cd /opt/dlpscanner
   java -jar lib/scanner-app.jar
   ```

### Cannot Connect to Management Server

#### Symptom
Service starts but cannot connect to the management server.

#### Solutions

1. **Verify URL Configuration**
   Check `application.yaml`:
   ```yaml
   scanner:
     managementServer:
       url: "https://dlp-mgmt.your-company.com/api/v1"
   ```

2. **Check Network Connectivity**
   ```cmd
   # Windows
   ping dlp-mgmt.your-company.com
   curl https://dlp-mgmt.your-company.com/api/v1/health
   
   # Linux
   ping dlp-mgmt.your-company.com
   curl https://dlp-mgmt.your-company.com/api/v1/health
   ```

3. **Verify API Key**
   Ensure environment variable is set correctly:
   
   **Windows:**
   ```cmd
   echo %DLP_MGMT_API_KEY%
   ```
   
   **Linux:**
   ```bash
   echo $DLP_MGMT_API_KEY
   ```

4. **Check Firewall Rules**
   - Ensure outbound HTTPS (port 443) is allowed
   - Verify proxy settings if applicable

5. **Review SSL/TLS Certificates**
   If using self-signed certificates, you may need to import them into the Java keystore.

### High CPU Usage

#### Symptom
DLP Scanner consuming excessive CPU resources.

#### Solutions

1. **Adjust Rate Limiting**
   Edit `application.yaml`:
   ```yaml
   scanner:
     maxFilesPerSecond: 5  # Reduce from default 10
     businessHoursCpuPercent: 10  # Reduce from default 20
   ```

2. **Enable Idle-Only Scanning**
   ```yaml
   scanner:
     idleOnlyScanning: true
     idleThresholdMinutes: 10
   ```

3. **Reduce Parser Timeouts**
   ```yaml
   scanner:
     parser:
       timeoutSeconds: 15  # Reduce from default 30
   ```

4. **Add More Exclusions**
   Exclude directories that don't need scanning:
   ```yaml
   scanner:
     excludePaths:
       - "**/node_modules/**"
       - "**/build/**"
       - "**/cache/**"
   ```

5. **Restart Service**
   ```cmd
   # Windows
   sc stop DLPScanner && sc start DLPScanner
   
   # Linux
   sudo systemctl restart dlpscanner
   ```

### Disk Space Issues

#### Symptom
Service stops or performs poorly due to disk space exhaustion.

#### Solutions

1. **Check Database Sizes**
   
   **Windows:**
   ```cmd
   dir C:\ProgramData\DLPScanner\*.db
   ```
   
   **Linux:**
   ```bash
   du -h /var/lib/DLPScanner/*.db
   ```

2. **Reduce Database Size Limits**
   Edit `application.yaml`:
   ```yaml
   scanner:
     results:
       maxSizeMB: 512  # Reduce from default 1024
     telemetry:
       maxSizeMB: 256  # Reduce from default 500
   ```

3. **Clear Old Logs**
   
   **Windows:**
   ```cmd
   del C:\ProgramData\DLPScanner\logs\scanner.*.log
   ```
   
   **Linux:**
   ```bash
   sudo rm /var/lib/DLPScanner/logs/scanner.*.log
   ```

4. **Clear Cache**
   
   **Windows:**
   ```cmd
   rmdir /s /q C:\ProgramData\DLPScanner\cache
   mkdir C:\ProgramData\DLPScanner\cache
   ```
   
   **Linux:**
   ```bash
   sudo rm -rf /var/lib/DLPScanner/cache/*
   ```

### Rules Not Loading

#### Symptom
Scanner reports no rules loaded or cannot find rule files.

#### Solutions

1. **Verify Rules Directory**
   
   **Windows:**
   ```cmd
   dir C:\ProgramData\DLPScanner\rules
   ```
   
   **Linux:**
   ```bash
   ls -l /var/lib/DLPScanner/rules
   ```

2. **Check Configuration**
   Verify `application.yaml`:
   ```yaml
   scanner:
     rules:
       path: "${programdata}/DLPScanner/rules"
   ```

3. **Verify Rule File Format**
   - Rule files should have `.dlpr` extension
   - Files should not be corrupted

4. **Check Permissions**
   
   **Windows:**
   - Ensure service account can read the rules directory
   
   **Linux:**
   ```bash
   sudo chown root:root /var/lib/DLPScanner/rules/*
   sudo chmod 644 /var/lib/DLPScanner/rules/*
   ```

5. **Disable Signature Verification (Testing Only)**
   Temporarily disable to test:
   ```yaml
   scanner:
     rules:
       signatureVerification: false
   ```

### Out of Memory Errors

#### Symptom
Service crashes with `OutOfMemoryError` messages.

#### Solutions

1. **Increase JVM Heap Size**
   
   **Windows (WinSW):**
   Edit `DLPScanner-service.xml`:
   ```xml
   <arguments>-Xms1024m -Xmx4096m -jar "%BASE%\lib\scanner-app.jar"</arguments>
   ```
   
   **Linux (systemd):**
   Edit `/etc/systemd/system/dlpscanner.service`:
   ```ini
   ExecStart=/usr/bin/java \
     -Xms1024m \
     -Xmx4096m \
     -jar /opt/dlpscanner/lib/scanner-app.jar
   ```
   
   Then reload and restart:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl restart dlpscanner
   ```

2. **Reduce File Size Limits**
   Edit `application.yaml`:
   ```yaml
   scanner:
     maxFileSizeBytes: 52428800  # 50 MB instead of 100 MB
   ```

3. **Reduce Parser Limits**
   ```yaml
   scanner:
     parser:
       pdf:
         maxPages: 200
         maxStreamSizeMB: 32
       zip:
         maxCumulativeSizeMB: 128
   ```

## Upgrading

### Pre-Upgrade Steps

1. **Backup Configuration**
   
   **Windows:**
   ```cmd
   copy "C:\Program Files\DLPScanner\config\application.yaml" "%USERPROFILE%\Desktop\application.yaml.backup"
   ```
   
   **Linux:**
   ```bash
   sudo cp /opt/dlpscanner/config/application.yaml ~/application.yaml.backup
   ```

2. **Backup Databases** (optional but recommended)
   
   **Windows:**
   ```cmd
   copy C:\ProgramData\DLPScanner\*.db %USERPROFILE%\Desktop\backup\
   ```
   
   **Linux:**
   ```bash
   sudo cp /var/lib/DLPScanner/*.db ~/backup/
   ```

3. **Stop the Service**
   
   **Windows:**
   ```cmd
   sc stop DLPScanner
   ```
   
   **Linux:**
   ```bash
   sudo systemctl stop dlpscanner
   ```

### Upgrade Process

1. **Extract New Version**
   
   **Windows:**
   ```cmd
   cd C:\Program Files\DLPScanner
   # Rename old installation
   rename lib lib.old
   rename bin bin.old
   
   # Extract new version
   tar -xf dlpscanner-1.1.0.zip
   ```
   
   **Linux:**
   ```bash
   cd /opt/dlpscanner
   # Backup old installation
   sudo mv lib lib.old
   sudo mv bin bin.old
   
   # Extract new version
   sudo tar -xzf dlpscanner-1.1.0.tar.gz
   ```

2. **Restore Configuration**
   
   Compare your backed-up configuration with the new default configuration:
   ```cmd
   # Windows
   fc %USERPROFILE%\Desktop\application.yaml.backup config\application.yaml
   
   # Linux
   diff ~/application.yaml.backup config/application.yaml
   ```
   
   Merge any necessary changes, then copy:
   ```cmd
   # Windows
   copy %USERPROFILE%\Desktop\application.yaml.backup config\application.yaml
   
   # Linux
   sudo cp ~/application.yaml.backup config/application.yaml
   ```

3. **Update Service Configuration (if needed)**
   
   Check release notes for any required service configuration changes.

4. **Start the Service**
   
   **Windows:**
   ```cmd
   sc start DLPScanner
   ```
   
   **Linux:**
   ```bash
   sudo systemctl start dlpscanner
   ```

5. **Verify Upgrade**
   
   **Windows:**
   ```cmd
   sc query DLPScanner
   type C:\ProgramData\DLPScanner\logs\scanner.log
   ```
   
   **Linux:**
   ```bash
   sudo systemctl status dlpscanner
   sudo journalctl -u dlpscanner -n 50
   ```

6. **Remove Old Files** (after verification)
   
   **Windows:**
   ```cmd
   rmdir /s /q "C:\Program Files\DLPScanner\lib.old"
   rmdir /s /q "C:\Program Files\DLPScanner\bin.old"
   ```
   
   **Linux:**
   ```bash
   sudo rm -rf /opt/dlpscanner/lib.old
   sudo rm -rf /opt/dlpscanner/bin.old
   ```

### Rolling Back an Upgrade

If the upgrade fails:

1. **Stop the Service**
   ```cmd
   # Windows
   sc stop DLPScanner
   
   # Linux
   sudo systemctl stop dlpscanner
   ```

2. **Restore Old Version**
   
   **Windows:**
   ```cmd
   cd C:\Program Files\DLPScanner
   rmdir /s /q lib
   rmdir /s /q bin
   rename lib.old lib
   rename bin.old bin
   ```
   
   **Linux:**
   ```bash
   cd /opt/dlpscanner
   sudo rm -rf lib bin
   sudo mv lib.old lib
   sudo mv bin.old bin
   ```

3. **Restore Configuration**
   ```cmd
   # Windows
   copy %USERPROFILE%\Desktop\application.yaml.backup config\application.yaml
   
   # Linux
   sudo cp ~/application.yaml.backup config/application.yaml
   ```

4. **Start the Service**
   ```cmd
   # Windows
   sc start DLPScanner
   
   # Linux
   sudo systemctl start dlpscanner
   ```

## Uninstallation

### Windows

1. **Stop and Remove Service**
   ```cmd
   # Using WinSW
   cd C:\Program Files\DLPScanner
   DLPScanner-service.exe stop
   DLPScanner-service.exe uninstall
   
   # Using NSSM
   nssm stop DLPScanner
   nssm remove DLPScanner confirm
   ```

2. **Remove Application Files**
   ```cmd
   rmdir /s /q "C:\Program Files\DLPScanner"
   ```

3. **Remove Data Files** (optional - includes logs and databases)
   ```cmd
   rmdir /s /q C:\ProgramData\DLPScanner
   ```

4. **Remove Environment Variables**
   ```powershell
   [System.Environment]::SetEnvironmentVariable('DLP_MGMT_API_KEY', $null, 'Machine')
   ```

### Linux

1. **Stop and Disable Service**
   ```bash
   sudo systemctl stop dlpscanner
   sudo systemctl disable dlpscanner
   ```

2. **Remove Service File**
   ```bash
   sudo rm /etc/systemd/system/dlpscanner.service
   sudo systemctl daemon-reload
   ```

3. **Remove Application Files**
   ```bash
   sudo rm -rf /opt/dlpscanner
   ```

4. **Remove Data Files** (optional - includes logs and databases)
   ```bash
   sudo rm -rf /var/lib/DLPScanner
   ```

5. **Remove Configuration Files**
   ```bash
   sudo rm -rf /etc/dlpscanner
   ```

6. **Remove Environment Variables**
   ```bash
   sudo sed -i '/DLP_MGMT_API_KEY/d' /etc/environment
   ```

### Cleanup Verification

After uninstallation, verify all components are removed:

**Windows:**
```cmd
# Check service
sc query DLPScanner

# Check files
dir "C:\Program Files\DLPScanner"
dir C:\ProgramData\DLPScanner

# Check environment variables
echo %DLP_MGMT_API_KEY%
```

**Linux:**
```bash
# Check service
systemctl status dlpscanner

# Check files
ls /opt/dlpscanner
ls /var/lib/DLPScanner
ls /etc/dlpscanner

# Check environment variables
grep DLP_MGMT_API_KEY /etc/environment
```

## Additional Resources

- **Configuration Guide**: [CONFIGURATION.md](CONFIGURATION.md)
- **Architecture Documentation**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Project README**: [README.md](../README.md)

## Support

For additional assistance:

1. Review application logs for error messages
2. Check the troubleshooting section above
3. Consult your system administrator
4. Contact technical support with:
   - DLP Scanner version
   - Operating system and version
   - Relevant log excerpts
   - Description of the issue

---

**Document Version**: 1.0  
**Last Updated**: 2024
