@echo off
REM Chicken Minecraft Server Hosting Platform - Spigot Build Script
REM Copyright (c) 2025 piratebomber
REM Licensed under the Apache License, Version 2.0
REM
REM This script builds Spigot from source using BuildTools

setlocal enabledelayedexpansion

echo.
echo ========================================
echo  Spigot Build Script - BuildTools
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

REM Check if Git is installed
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Git is not installed or not in PATH
    echo Please install Git from: https://git-scm.com/download/win
    pause
    exit /b 1
)

REM Get version parameter
set VERSION=%1
if "%VERSION%"=="" (
    set VERSION=1.20.4
    echo [INFO] No version specified, using default: %VERSION%
) else (
    echo [INFO] Building Spigot version: %VERSION%
)

REM Create build directory
if not exist "spigot-build" mkdir spigot-build
cd spigot-build

echo [INFO] Working directory: %CD%

REM Download BuildTools if not exists
if not exist "BuildTools.jar" (
    echo [INFO] Downloading BuildTools.jar...
    curl -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
    if %errorlevel% neq 0 (
        echo [ERROR] Failed to download BuildTools.jar
        echo Please check your internet connection
        pause
        exit /b 1
    )
    echo [INFO] BuildTools.jar downloaded successfully
) else (
    echo [INFO] BuildTools.jar already exists, skipping download
)

REM Set JVM options for BuildTools
set JAVA_OPTS=-Xmx2G -Xms1G

echo [INFO] Starting Spigot build process...
echo [INFO] This may take 10-30 minutes depending on your system
echo [INFO] JVM Options: %JAVA_OPTS%
echo.

REM Run BuildTools
java %JAVA_OPTS% -jar BuildTools.jar --rev %VERSION%

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Spigot build failed
    echo Check the output above for error details
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Spigot build completed successfully!

REM Find the built JAR file
for %%f in (spigot-%VERSION%.jar) do (
    if exist "%%f" (
        echo [INFO] Built JAR: %%f
        echo [INFO] Size: 
        dir "%%f" | findstr "%%f"
        
        REM Copy to parent directory for easy access
        copy "%%f" "..\spigot-%VERSION%.jar" >nul
        echo [INFO] Copied to: ..\spigot-%VERSION%.jar
    )
)

REM Check for CraftBukkit as well
for %%f in (craftbukkit-%VERSION%.jar) do (
    if exist "%%f" (
        echo [INFO] Also built CraftBukkit: %%f
        copy "%%f" "..\craftbukkit-%VERSION%.jar" >nul
        echo [INFO] Copied to: ..\craftbukkit-%VERSION%.jar
    )
)

echo.
echo [INFO] Build artifacts are available in the current directory
echo [INFO] You can now use these JARs to create Minecraft servers
echo.

REM Clean up build files (optional)
set /p CLEANUP="Clean up build files? (y/N): "
if /i "%CLEANUP%"=="y" (
    echo [INFO] Cleaning up build files...
    cd ..
    rmdir /s /q spigot-build
    echo [INFO] Build directory cleaned up
) else (
    echo [INFO] Build files preserved in spigot-build directory
    cd ..
)

echo.
echo [SUCCESS] Spigot build process completed!
pause