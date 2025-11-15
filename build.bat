@echo off
REM Chicken Minecraft Server Hosting Platform - Build Script
REM Copyright (c) 2025 piratebomber - Licensed under Apache 2.0

echo.
echo ========================================
echo  Chicken Server Host - Build Script
echo ========================================
echo.

REM Check if Maven is installed
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven is not installed or not in PATH
    echo Please install Maven from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo [INFO] Building Chicken Server Host...

REM Clean and compile
echo [INFO] Cleaning previous builds...
call mvn clean

REM Run tests
echo [INFO] Running tests...
call mvn test
if %errorlevel% neq 0 (
    echo [ERROR] Tests failed
    pause
    exit /b 1
)

REM Package application
echo [INFO] Packaging application...
call mvn package
if %errorlevel% neq 0 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Build completed successfully!
echo [INFO] JAR file location: target\chicken-server-host-1.0.0-RELEASE.jar
echo [INFO] To run the application: java -jar target\chicken-server-host-1.0.0-RELEASE.jar
echo [INFO] Or use the startup script: scripts\start.bat
echo.

pause