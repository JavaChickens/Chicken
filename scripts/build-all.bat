@echo off
REM Chicken Minecraft Server Hosting Platform - Complete Build Script
REM Copyright (c) 2025 piratebomber
REM Licensed under the Apache License, Version 2.0

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Chicken Complete Build Script
echo ========================================
echo.

REM Check prerequisites
echo [INFO] Checking prerequisites...

REM Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH
    echo Please install Java 17 or higher from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Maven is not installed or not in PATH
    echo Please install Maven from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Check Git
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Git is not installed or not in PATH
    echo Please install Git from: https://git-scm.com/download/win
    pause
    exit /b 1
)

echo [SUCCESS] All prerequisites met!
echo.

REM Build main application
echo [INFO] Building main Chicken application...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Main application build failed
    pause
    exit /b 1
)
echo [SUCCESS] Main application built successfully!
echo.

REM Build Spigot (optional)
set /p BUILD_SPIGOT="Build Spigot from source? (y/N): "
if /i "%BUILD_SPIGOT%"=="y" (
    echo [INFO] Building Spigot from source...
    call scripts\build-spigot.bat 1.20.4
    if %errorlevel% neq 0 (
        echo [WARNING] Spigot build failed, continuing without it
    ) else (
        echo [SUCCESS] Spigot built successfully!
    )
    echo.
)

REM Create distribution package
echo [INFO] Creating distribution package...
if not exist "dist" mkdir dist

REM Copy main JAR
copy "target\chicken-server-host-*.jar" "dist\" >nul
echo [INFO] Copied main application JAR

REM Copy scripts
xcopy "scripts" "dist\scripts\" /E /I /Q >nul
echo [INFO] Copied scripts

REM Copy documentation
copy "README.md" "dist\" >nul
copy "LICENSE" "dist\" >nul
copy "DEPLOYMENT.md" "dist\" >nul
echo [INFO] Copied documentation

REM Copy Docker files
copy "Dockerfile" "dist\" >nul
copy "docker-compose.yml" "dist\" >nul
echo [INFO] Copied Docker configuration

REM Copy Spigot if built
if exist "spigot-*.jar" (
    copy "spigot-*.jar" "dist\" >nul
    echo [INFO] Copied Spigot JAR
)

REM Create startup scripts in dist
echo @echo off > "dist\start.bat"
echo echo Starting Chicken Server Host... >> "dist\start.bat"
echo java -jar chicken-server-host-*.jar >> "dist\start.bat"

echo #!/bin/bash > "dist\start.sh"
echo echo "Starting Chicken Server Host..." >> "dist\start.sh"
echo java -jar chicken-server-host-*.jar >> "dist\start.sh"

echo [INFO] Created distribution startup scripts

REM Create version info
echo Chicken Server Host > "dist\VERSION.txt"
echo Version: 1.0.0-RELEASE >> "dist\VERSION.txt"
echo Build Date: %date% %time% >> "dist\VERSION.txt"
echo Built by: piratebomber >> "dist\VERSION.txt"
echo.
echo Features: >> "dist\VERSION.txt"
echo - Multi-server Minecraft hosting >> "dist\VERSION.txt"
echo - Advanced plugin marketplace >> "dist\VERSION.txt"
echo - Player management and statistics >> "dist\VERSION.txt"
echo - CLI and web interface >> "dist\VERSION.txt"
echo - Self-hosting with Netlify integration >> "dist\VERSION.txt"
echo - Support for all Minecraft versions >> "dist\VERSION.txt"

echo [SUCCESS] Distribution package created in 'dist' directory!
echo.

REM Show build summary
echo ========================================
echo  Build Summary
echo ========================================
echo Main Application: ✓ Built
if /i "%BUILD_SPIGOT%"=="y" (
    echo Spigot from Source: ✓ Built
) else (
    echo Spigot from Source: - Skipped
)
echo Distribution Package: ✓ Created
echo.
echo Files in dist directory:
dir /b dist
echo.
echo [INFO] To run: cd dist && start.bat
echo [INFO] For Docker: docker-compose up -d
echo [INFO] For production deployment, see DEPLOYMENT.md
echo.

pause