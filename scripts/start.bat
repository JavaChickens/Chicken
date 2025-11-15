@echo off
REM Chicken Minecraft Server Hosting Platform - Windows Startup Script
REM Copyright (c) 2025 piratebomber - Licensed under Apache 2.0
REM
REM This script provides easy startup for the Chicken platform on Windows
REM with automatic dependency checking and environment setup.

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Chicken Server Host - Startup Script
echo ========================================
echo.

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH
    echo Please install Java 17 or higher from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check Java version
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
set JAVA_VERSION=%JAVA_VERSION:"=%
for /f "delims=. tokens=1-3" %%v in ("%JAVA_VERSION%") do (
    set MAJOR=%%v
    set MINOR=%%w
)

if %MAJOR% lss 17 (
    echo [ERROR] Java 17 or higher is required. Found: %JAVA_VERSION%
    echo Please upgrade Java from: https://adoptium.net/
    pause
    exit /b 1
)

echo [INFO] Java version: %JAVA_VERSION% - OK

REM Check if Maven is available for building
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARNING] Maven not found. Checking for pre-built JAR...
    
    if not exist "target\chicken-server-host-*.jar" (
        echo [ERROR] No pre-built JAR found and Maven is not available
        echo Please install Maven from: https://maven.apache.org/download.cgi
        echo Or download a pre-built release from: https://github.com/chicken-project/chicken/releases
        pause
        exit /b 1
    )
) else (
    echo [INFO] Maven found - building application...
    call mvn clean package -DskipTests
    if %errorlevel% neq 0 (
        echo [ERROR] Build failed
        pause
        exit /b 1
    )
    echo [INFO] Build completed successfully
)

REM Find the JAR file
for %%f in (target\chicken-server-host-*.jar) do set JAR_FILE=%%f

if not exist "%JAR_FILE%" (
    echo [ERROR] JAR file not found: %JAR_FILE%
    pause
    exit /b 1
)

echo [INFO] Found JAR file: %JAR_FILE%

REM Create necessary directories
if not exist "servers" mkdir servers
if not exist "plugins-cache" mkdir plugins-cache
if not exist "logs" mkdir logs
if not exist "data" mkdir data

echo [INFO] Created necessary directories

REM Set JVM options for optimal performance
set JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication

REM Check available memory
for /f "skip=1" %%p in ('wmic computersystem get TotalPhysicalMemory') do (
    set TOTAL_MEMORY=%%p
    goto :break
)
:break

REM Convert bytes to GB (approximate)
set /a MEMORY_GB=%TOTAL_MEMORY:~0,-9%

if %MEMORY_GB% lss 8 (
    echo [WARNING] Low system memory detected: %MEMORY_GB%GB
    echo [INFO] Reducing JVM heap size for better performance
    set JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
)

echo [INFO] System memory: %MEMORY_GB%GB
echo [INFO] JVM options: %JAVA_OPTS%

REM Start the application
echo.
echo ========================================
echo  Starting Chicken Server Host...
echo ========================================
echo.
echo [INFO] Web Interface: http://localhost:8080
echo [INFO] API Documentation: http://localhost:8080/actuator
echo [INFO] Database Console: http://localhost:8080/h2-console
echo [INFO] Health Check: http://localhost:8080/actuator/health
echo.
echo [INFO] Press Ctrl+C to stop the server
echo.

java %JAVA_OPTS% -jar "%JAR_FILE%" --spring.profiles.active=dev

echo.
echo [INFO] Chicken Server Host has stopped
pause