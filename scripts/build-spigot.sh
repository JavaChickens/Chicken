#!/bin/bash
# Chicken Minecraft Server Hosting Platform - Spigot Build Script
# Copyright (c) 2025 piratebomber
# Licensed under the Apache License, Version 2.0
#
# This script builds Spigot from source using BuildTools

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

echo
echo "========================================"
echo "  Spigot Build Script - BuildTools"
echo "========================================"
echo

# Check if Java is installed
if ! command -v java &> /dev/null; then
    log_error "Java is not installed or not in PATH"
    log_error "Please install Java 17 or higher from: https://adoptium.net/"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)

if [ "$JAVA_MAJOR" -lt 17 ]; then
    log_error "Java 17 or higher is required. Found: $JAVA_VERSION"
    log_error "Please upgrade Java from: https://adoptium.net/"
    exit 1
fi

log_success "Java version: $JAVA_VERSION - OK"

# Check if Git is installed
if ! command -v git &> /dev/null; then
    log_error "Git is not installed or not in PATH"
    log_error "Please install Git: sudo apt install git (Ubuntu) or brew install git (macOS)"
    exit 1
fi

log_success "Git is available"

# Get version parameter
VERSION=${1:-1.20.4}
if [ "$1" = "" ]; then
    log_info "No version specified, using default: $VERSION"
else
    log_info "Building Spigot version: $VERSION"
fi

# Create build directory
mkdir -p spigot-build
cd spigot-build

log_info "Working directory: $(pwd)"

# Download BuildTools if not exists
if [ ! -f "BuildTools.jar" ]; then
    log_info "Downloading BuildTools.jar..."
    if command -v curl &> /dev/null; then
        curl -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
    elif command -v wget &> /dev/null; then
        wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
    else
        log_error "Neither curl nor wget is available for downloading BuildTools.jar"
        exit 1
    fi
    
    if [ $? -ne 0 ]; then
        log_error "Failed to download BuildTools.jar"
        log_error "Please check your internet connection"
        exit 1
    fi
    
    log_success "BuildTools.jar downloaded successfully"
else
    log_info "BuildTools.jar already exists, skipping download"
fi

# Detect system memory for JVM options
TOTAL_MEMORY_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo "8388608")
MEMORY_GB=$((TOTAL_MEMORY_KB / 1024 / 1024))

# Set JVM options based on available memory
if [ "$MEMORY_GB" -lt 4 ]; then
    JAVA_OPTS="-Xmx1G -Xms512M"
    log_warn "Low system memory detected: ${MEMORY_GB}GB"
    log_warn "Using reduced JVM heap size: $JAVA_OPTS"
else
    JAVA_OPTS="-Xmx2G -Xms1G"
fi

log_info "Starting Spigot build process..."
log_info "This may take 10-30 minutes depending on your system"
log_info "JVM Options: $JAVA_OPTS"
echo

# Run BuildTools
java $JAVA_OPTS -jar BuildTools.jar --rev $VERSION

if [ $? -ne 0 ]; then
    echo
    log_error "Spigot build failed"
    log_error "Check the output above for error details"
    exit 1
fi

echo
log_success "Spigot build completed successfully!"

# Find the built JAR file
if [ -f "spigot-$VERSION.jar" ]; then
    log_info "Built JAR: spigot-$VERSION.jar"
    log_info "Size: $(du -h spigot-$VERSION.jar | cut -f1)"
    
    # Copy to parent directory for easy access
    cp "spigot-$VERSION.jar" "../spigot-$VERSION.jar"
    log_info "Copied to: ../spigot-$VERSION.jar"
fi

# Check for CraftBukkit as well
if [ -f "craftbukkit-$VERSION.jar" ]; then
    log_info "Also built CraftBukkit: craftbukkit-$VERSION.jar"
    cp "craftbukkit-$VERSION.jar" "../craftbukkit-$VERSION.jar"
    log_info "Copied to: ../craftbukkit-$VERSION.jar"
fi

echo
log_info "Build artifacts are available in the current directory"
log_info "You can now use these JARs to create Minecraft servers"
echo

# Clean up build files (optional)
read -p "Clean up build files? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_info "Cleaning up build files..."
    cd ..
    rm -rf spigot-build
    log_info "Build directory cleaned up"
else
    log_info "Build files preserved in spigot-build directory"
    cd ..
fi

echo
log_success "Spigot build process completed!"