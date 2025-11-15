#!/bin/bash
# Chicken Minecraft Server Hosting Platform - Linux/macOS Startup Script
# Copyright (c) 2025 piratebomber - Licensed under Apache 2.0
#
# This script provides easy startup for the Chicken platform on Unix-like systems
# with automatic dependency checking and environment setup.

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
echo "  Chicken Server Host - Startup Script"
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

# Check if Maven is available for building
if ! command -v mvn &> /dev/null; then
    log_warn "Maven not found. Checking for pre-built JAR..."
    
    if ! ls target/chicken-server-host-*.jar 1> /dev/null 2>&1; then
        log_error "No pre-built JAR found and Maven is not available"
        log_error "Please install Maven from: https://maven.apache.org/download.cgi"
        log_error "Or download a pre-built release from: https://github.com/chicken-project/chicken/releases"
        exit 1
    fi
else
    log_info "Maven found - building application..."
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        log_error "Build failed"
        exit 1
    fi
    log_success "Build completed successfully"
fi

# Find the JAR file
JAR_FILE=$(ls target/chicken-server-host-*.jar 2>/dev/null | head -n1)

if [ ! -f "$JAR_FILE" ]; then
    log_error "JAR file not found: $JAR_FILE"
    exit 1
fi

log_info "Found JAR file: $JAR_FILE"

# Create necessary directories
mkdir -p servers plugins-cache logs data
log_info "Created necessary directories"

# Detect system memory
TOTAL_MEMORY_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo "8388608")
MEMORY_GB=$((TOTAL_MEMORY_KB / 1024 / 1024))

# Set JVM options based on available memory
if [ "$MEMORY_GB" -lt 8 ]; then
    log_warn "Low system memory detected: ${MEMORY_GB}GB"
    log_info "Reducing JVM heap size for better performance"
    JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
else
    JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
fi

log_info "System memory: ${MEMORY_GB}GB"
log_info "JVM options: $JAVA_OPTS"

# Check for port availability
if command -v netstat &> /dev/null; then
    if netstat -tuln | grep -q ":8080 "; then
        log_warn "Port 8080 is already in use. The application may fail to start."
        log_warn "Stop any existing services on port 8080 or configure a different port."
    fi
fi

# Start the application
echo
echo "========================================"
echo "  Starting Chicken Server Host..."
echo "========================================"
echo
log_info "Web Interface: http://localhost:8080"
log_info "API Documentation: http://localhost:8080/actuator"
log_info "Database Console: http://localhost:8080/h2-console"
log_info "Health Check: http://localhost:8080/actuator/health"
echo
log_info "Press Ctrl+C to stop the server"
echo

# Handle cleanup on exit
cleanup() {
    echo
    log_info "Shutting down Chicken Server Host..."
    exit 0
}

trap cleanup SIGINT SIGTERM

# Start the application with development profile
java $JAVA_OPTS -jar "$JAR_FILE" --spring.profiles.active=dev

echo
log_info "Chicken Server Host has stopped"