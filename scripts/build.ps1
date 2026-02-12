# Build script for DLP Discovery Scanner
# This script builds the scanner application and creates a distribution package

$ErrorActionPreference = "Stop"

Write-Host "Building DLP Discovery Scanner..." -ForegroundColor Cyan

# Set location to project root
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptPath
Set-Location $projectRoot

# Clean previous builds
Write-Host "Cleaning previous builds..." -ForegroundColor Yellow
& .\gradlew.bat clean

# Compile and test
Write-Host "Compiling and running tests..." -ForegroundColor Yellow
& .\gradlew.bat build

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# Create distribution
Write-Host "Creating distribution..." -ForegroundColor Yellow
& .\gradlew.bat :scanner-app:distZip

if ($LASTEXITCODE -ne 0) {
    Write-Host "Distribution creation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Build completed successfully!" -ForegroundColor Green
Write-Host "Distribution package: scanner-app/build/distributions/scanner-app.zip" -ForegroundColor Cyan
