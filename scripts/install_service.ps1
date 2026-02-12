# Install DLP Discovery Scanner as Windows Service
# Requires Administrator privileges

#Requires -RunAsAdministrator

param(
    [string]$InstallPath = "C:\Program Files\DLPScanner",
    [string]$ServiceName = "DLPDiscoveryScanner",
    [string]$DisplayName = "DLP Discovery Scanner",
    [string]$Description = "Data Loss Prevention discovery scanner for sensitive data detection"
)

$ErrorActionPreference = "Stop"

Write-Host "Installing DLP Discovery Scanner as Windows Service..." -ForegroundColor Cyan

# Check if Java 17+ is installed
$javaVersion = (java -version 2>&1) | Select-String -Pattern "version"
if (-not $javaVersion) {
    Write-Host "ERROR: Java is not installed or not in PATH" -ForegroundColor Red
    exit 1
}
Write-Host "Found Java: $javaVersion" -ForegroundColor Green

# Create installation directory
if (-not (Test-Path $InstallPath)) {
    Write-Host "Creating installation directory: $InstallPath" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $InstallPath | Out-Null
}

# Copy distribution files
$distZip = "scanner-app\build\distributions\scanner-app.zip"
if (-not (Test-Path $distZip)) {
    Write-Host "ERROR: Distribution package not found. Run build.ps1 first." -ForegroundColor Red
    exit 1
}

Write-Host "Extracting distribution..." -ForegroundColor Yellow
Expand-Archive -Path $distZip -DestinationPath $InstallPath -Force

# Create data directories
$dataPath = "$env:ProgramData\DLPScanner"
@("cache", "rules", "logs") | ForEach-Object {
    $path = Join-Path $dataPath $_
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
}

# Copy default configuration
$configSource = "scanner-app\src\main\resources\application.yaml"
$configDest = Join-Path $dataPath "application.yaml"
if (-not (Test-Path $configDest)) {
    Copy-Item $configSource $configDest
    Write-Host "Created default configuration: $configDest" -ForegroundColor Green
}

# Copy default rules
$rulesSource = "scanner-app\src\main\resources\default_rules.json"
$rulesDest = Join-Path $dataPath "rules\default_rules.json"
if (-not (Test-Path $rulesDest)) {
    Copy-Item $rulesSource $rulesDest
    Write-Host "Created default rules: $rulesDest" -ForegroundColor Green
}

Write-Host @"

Installation completed!

To install as a Windows Service, use one of these tools:
1. WinSW (Windows Service Wrapper): https://github.com/winsw/winsw
2. NSSM (Non-Sucking Service Manager): https://nssm.cc/

Example NSSM installation:
  nssm install $ServiceName "$InstallPath\scanner-app\bin\scanner-app.bat"
  nssm set $ServiceName AppDirectory "$InstallPath\scanner-app"
  nssm set $ServiceName DisplayName "$DisplayName"
  nssm set $ServiceName Description "$Description"
  nssm set $ServiceName Start SERVICE_AUTO_START
  nssm start $ServiceName

See docs\DEPLOYMENT.md for detailed instructions.
"@ -ForegroundColor Cyan
