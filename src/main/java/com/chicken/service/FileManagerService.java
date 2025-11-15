/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Service class for file system operations and management.
 * Provides comprehensive file handling capabilities including directory management,
 * file operations, metadata extraction, and integrity verification.
 */
package com.chicken.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service class for comprehensive file system operations.
 * 
 * This service provides:
 * - Directory creation and deletion operations
 * - File copying, moving, and manipulation
 * - JAR file metadata extraction for plugins
 * - File integrity verification (checksums)
 * - Safe file operations with proper error handling
 * - Recursive directory operations
 * 
 * All operations are designed for production use with proper
 * error handling, logging, and security considerations.
 */
@Service
public class FileManagerService {

    private static final Logger logger = LoggerFactory.getLogger(FileManagerService.class);

    /**
     * Creates a directory and all necessary parent directories.
     * 
     * @param directoryPath Path to the directory to create
     * @throws IOException if directory creation fails
     */
    public void createDirectory(Path directoryPath) throws IOException {
        logger.debug("Creating directory: {}", directoryPath);
        Files.createDirectories(directoryPath);
        logger.info("Successfully created directory: {}", directoryPath);
    }

    /**
     * Deletes a directory and all its contents recursively.
     * 
     * This method safely removes directories with all subdirectories and files.
     * It handles symbolic links properly and provides detailed logging.
     * 
     * @param directoryPath Path to the directory to delete
     * @throws IOException if deletion fails
     */
    public void deleteDirectory(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath)) {
            logger.debug("Directory does not exist, skipping deletion: {}", directoryPath);
            return;
        }

        logger.info("Deleting directory recursively: {}", directoryPath);
        
        Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                logger.debug("Deleting file: {}", file);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                logger.debug("Deleting directory: {}", dir);
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        
        logger.info("Successfully deleted directory: {}", directoryPath);
    }

    /**
     * Copies a file from source to destination.
     * 
     * @param source Source file path
     * @param destination Destination file path
     * @param replaceExisting Whether to replace existing files
     * @throws IOException if copy operation fails
     */
    public void copyFile(Path source, Path destination, boolean replaceExisting) throws IOException {
        logger.debug("Copying file: {} -> {}", source, destination);
        
        // Ensure destination directory exists
        Files.createDirectories(destination.getParent());
        
        if (replaceExisting) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(source, destination);
        }
        
        logger.info("Successfully copied file: {} -> {}", source, destination);
    }

    /**
     * Moves a file from source to destination.
     * 
     * @param source Source file path
     * @param destination Destination file path
     * @param replaceExisting Whether to replace existing files
     * @throws IOException if move operation fails
     */
    public void moveFile(Path source, Path destination, boolean replaceExisting) throws IOException {
        logger.debug("Moving file: {} -> {}", source, destination);
        
        // Ensure destination directory exists
        Files.createDirectories(destination.getParent());
        
        if (replaceExisting) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(source, destination);
        }
        
        logger.info("Successfully moved file: {} -> {}", source, destination);
    }

    /**
     * Calculates MD5 checksum of a file for integrity verification.
     * 
     * @param filePath Path to the file
     * @return MD5 checksum as hexadecimal string
     * @throws IOException if file reading fails
     */
    public String calculateMD5(Path filePath) throws IOException {
        logger.debug("Calculating MD5 checksum for: {}", filePath);
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Reading file to calculate digest
                }
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            
            String checksum = sb.toString();
            logger.debug("MD5 checksum for {}: {}", filePath, checksum);
            return checksum;
            
        } catch (Exception e) {
            throw new IOException("Failed to calculate MD5 checksum", e);
        }
    }

    /**
     * Extracts metadata from a Minecraft plugin JAR file.
     * 
     * This method reads the plugin.yml file from a Bukkit/Paper plugin JAR
     * and extracts essential metadata such as name, version, description, etc.
     * 
     * @param jarFilePath Path to the plugin JAR file
     * @return Map containing plugin metadata
     * @throws IOException if JAR reading fails
     */
    public Map<String, String> extractPluginMetadata(Path jarFilePath) throws IOException {
        logger.debug("Extracting plugin metadata from: {}", jarFilePath);
        
        Map<String, String> metadata = new HashMap<>();
        
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            // Look for plugin.yml (Bukkit/Paper) or paper-plugin.yml (Paper v2)
            JarEntry pluginYml = jarFile.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                pluginYml = jarFile.getJarEntry("paper-plugin.yml");
            }
            
            if (pluginYml != null) {
                try (InputStream is = jarFile.getInputStream(pluginYml);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        
                        // Simple YAML parsing for basic fields
                        if (line.contains(":")) {
                            String[] parts = line.split(":", 2);
                            if (parts.length == 2) {
                                String key = parts[0].trim();
                                String value = parts[1].trim();
                                
                                // Remove quotes if present
                                if (value.startsWith("\"") && value.endsWith("\"")) {
                                    value = value.substring(1, value.length() - 1);
                                } else if (value.startsWith("'") && value.endsWith("'")) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                
                                metadata.put(key, value);
                            }
                        }
                    }
                }
            } else {
                logger.warn("No plugin.yml found in JAR file: {}", jarFilePath);
            }
            
            // Extract additional information from manifest if available
            if (jarFile.getManifest() != null) {
                var attributes = jarFile.getManifest().getMainAttributes();
                if (attributes.getValue("Implementation-Version") != null) {
                    metadata.putIfAbsent("version", attributes.getValue("Implementation-Version"));
                }
                if (attributes.getValue("Implementation-Title") != null) {
                    metadata.putIfAbsent("name", attributes.getValue("Implementation-Title"));
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to extract plugin metadata from: {}", jarFilePath, e);
            throw new IOException("Failed to extract plugin metadata", e);
        }
        
        logger.info("Extracted plugin metadata from {}: {}", jarFilePath, metadata);
        return metadata;
    }

    /**
     * Reads the contents of a text file.
     * 
     * @param filePath Path to the file to read
     * @return File contents as string
     * @throws IOException if file reading fails
     */
    public String readTextFile(Path filePath) throws IOException {
        logger.debug("Reading text file: {}", filePath);
        
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        logger.debug("Successfully read {} characters from: {}", content.length(), filePath);
        return content.toString();
    }

    /**
     * Writes content to a text file.
     * 
     * @param filePath Path to the file to write
     * @param content Content to write
     * @param append Whether to append to existing file or overwrite
     * @throws IOException if file writing fails
     */
    public void writeTextFile(Path filePath, String content, boolean append) throws IOException {
        logger.debug("Writing text file: {} (append: {})", filePath, append);
        
        // Ensure parent directory exists
        Files.createDirectories(filePath.getParent());
        
        if (append) {
            Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.write(filePath, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        
        logger.info("Successfully wrote {} characters to: {}", content.length(), filePath);
    }

    /**
     * Gets the size of a file in bytes.
     * 
     * @param filePath Path to the file
     * @return File size in bytes
     * @throws IOException if file access fails
     */
    public long getFileSize(Path filePath) throws IOException {
        return Files.size(filePath);
    }

    /**
     * Checks if a file exists and is readable.
     * 
     * @param filePath Path to check
     * @return true if file exists and is readable
     */
    public boolean isFileReadable(Path filePath) {
        return Files.exists(filePath) && Files.isReadable(filePath);
    }

    /**
     * Checks if a directory exists and is writable.
     * 
     * @param directoryPath Path to check
     * @return true if directory exists and is writable
     */
    public boolean isDirectoryWritable(Path directoryPath) {
        return Files.exists(directoryPath) && Files.isDirectory(directoryPath) && Files.isWritable(directoryPath);
    }

    /**
     * Creates a backup copy of a file with timestamp.
     * 
     * @param filePath Path to the file to backup
     * @return Path to the backup file
     * @throws IOException if backup creation fails
     */
    public Path createBackup(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = filePath.getFileName().toString();
        String backupName = fileName + ".backup." + timestamp;
        Path backupPath = filePath.getParent().resolve(backupName);
        
        copyFile(filePath, backupPath, false);
        logger.info("Created backup: {} -> {}", filePath, backupPath);
        
        return backupPath;
    }

    /**
     * Extracts a ZIP/JAR file to a destination directory.
     * 
     * @param zipFilePath Path to the ZIP/JAR file
     * @param destinationDir Destination directory for extraction
     * @throws IOException if extraction fails
     */
    public void extractZipFile(Path zipFilePath, Path destinationDir) throws IOException {
        logger.info("Extracting ZIP file: {} -> {}", zipFilePath, destinationDir);
        
        Files.createDirectories(destinationDir);
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destinationDir.resolve(entry.getName());
                
                // Security check to prevent zip slip attacks
                if (!entryPath.normalize().startsWith(destinationDir.normalize())) {
                    throw new IOException("Entry is outside target directory: " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                
                zis.closeEntry();
            }
        }
        
        logger.info("Successfully extracted ZIP file: {}", zipFilePath);
    }

    /**
     * Gets the total size of a directory recursively.
     * 
     * @param directoryPath Path to the directory
     * @return Total size in bytes
     * @throws IOException if directory traversal fails
     */
    public long getDirectorySize(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            return 0;
        }
        
        final long[] size = {0};
        
        Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        
        return size[0];
    }
}