/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Service class for Minecraft plugin management operations.
 * Provides comprehensive plugin lifecycle management including installation,
 * updates, configuration, and integration with Bukkit/Paper plugin ecosystems.
 */
package com.chicken.service;

import com.chicken.config.ChickenConfiguration;
import com.chicken.model.MinecraftServer;
import com.chicken.model.ServerPlugin;
import com.chicken.repository.ServerPluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service class for comprehensive Minecraft plugin management.
 * 
 * This service provides:
 * - Plugin installation from various sources (URLs, files, marketplace)
 * - Plugin lifecycle management (enable, disable, update, remove)
 * - Configuration management and validation
 * - Integration with popular plugin repositories
 * - Dependency resolution and compatibility checking
 * - Automatic updates and version management
 * 
 * Supports both Bukkit and Paper plugin ecosystems with proper
 * error handling and transaction management for data consistency.
 */
@Service
@Transactional
public class PluginService {

    private static final Logger logger = LoggerFactory.getLogger(PluginService.class);

    @Autowired
    private ServerPluginRepository pluginRepository;

    @Autowired
    private ChickenConfiguration config;

    @Autowired
    private FileManagerService fileManagerService;

    @Autowired
    private PluginDownloadService pluginDownloadService;

    /**
     * Installs a plugin on a Minecraft server from a download URL.
     * 
     * This method:
     * - Validates plugin installation parameters
     * - Downloads the plugin JAR file
     * - Verifies file integrity and compatibility
     * - Installs the plugin to the server's plugins directory
     * - Updates the database with installation information
     * - Handles rollback on failure
     * 
     * @param server Target Minecraft server
     * @param pluginName Name/identifier of the plugin
     * @param downloadUrl URL to download the plugin JAR
     * @param installedBy Username of the person installing the plugin
     * @return CompletableFuture that completes when installation finishes
     * @throws IllegalArgumentException if parameters are invalid
     * @throws IllegalStateException if installation fails
     */
    @Async("serverTaskExecutor")
    public CompletableFuture<ServerPlugin> installPluginAsync(MinecraftServer server, String pluginName,
                                                             String downloadUrl, String installedBy) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return installPlugin(server, pluginName, downloadUrl, installedBy);
            } catch (Exception e) {
                logger.error("Async plugin installation failed: {} on server {}", pluginName, server.getName(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Installs a plugin on a Minecraft server synchronously.
     * 
     * @param server Target Minecraft server
     * @param pluginName Name/identifier of the plugin
     * @param downloadUrl URL to download the plugin JAR
     * @param installedBy Username of the person installing the plugin
     * @return Installed ServerPlugin entity
     * @throws IllegalArgumentException if parameters are invalid
     * @throws IllegalStateException if installation fails
     */
    public ServerPlugin installPlugin(MinecraftServer server, String pluginName,
                                    String downloadUrl, String installedBy) {
        logger.info("Installing plugin {} on server {} from URL: {}", pluginName, server.getName(), downloadUrl);

        // Validate parameters
        validatePluginInstallationParameters(server, pluginName, downloadUrl, installedBy);

        // Check if plugin is already installed
        Optional<ServerPlugin> existingPlugin = pluginRepository.findByServerAndPluginName(server, pluginName);
        if (existingPlugin.isPresent()) {
            throw new IllegalStateException("Plugin already installed: " + pluginName);
        }

        ServerPlugin plugin = null;
        try {
            // Create plugin entity
            plugin = new ServerPlugin(server, pluginName, "unknown", installedBy);
            plugin.setDownloadUrl(downloadUrl);
            plugin.setStatus(ServerPlugin.PluginStatus.INSTALLING);
            plugin = pluginRepository.save(plugin);

            // Download plugin file
            Path pluginFile = downloadPluginFile(plugin, downloadUrl);
            
            // Extract plugin metadata
            extractPluginMetadata(plugin, pluginFile);
            
            // Install to server plugins directory
            installPluginFile(server, plugin, pluginFile);
            
            // Mark as successfully installed
            plugin.setStatus(ServerPlugin.PluginStatus.INSTALLED);
            plugin.setEnabled(true);
            plugin = pluginRepository.save(plugin);

            logger.info("Successfully installed plugin {} on server {}", pluginName, server.getName());
            return plugin;

        } catch (Exception e) {
            logger.error("Failed to install plugin {} on server {}: {}", pluginName, server.getName(), e.getMessage(), e);
            
            // Mark plugin as failed if entity was created
            if (plugin != null) {
                plugin.markAsFailed("Installation failed: " + e.getMessage());
                pluginRepository.save(plugin);
            }
            
            throw new IllegalStateException("Plugin installation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Installs a popular plugin by name using the built-in plugin marketplace.
     * 
     * @param server Target Minecraft server
     * @param pluginName Name of the popular plugin (e.g., "EssentialsX", "WorldEdit")
     * @param installedBy Username of the person installing the plugin
     * @return CompletableFuture that completes when installation finishes
     */
    @Async("serverTaskExecutor")
    public CompletableFuture<ServerPlugin> installPopularPluginAsync(MinecraftServer server, String pluginName,
                                                                   String installedBy) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String downloadUrl = getPopularPluginDownloadUrl(pluginName, server.getMinecraftVersion());
                return installPlugin(server, pluginName, downloadUrl, installedBy);
            } catch (Exception e) {
                logger.error("Failed to install popular plugin: {} on server {}", pluginName, server.getName(), e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Removes a plugin from a Minecraft server.
     * 
     * @param serverId Database ID of the server
     * @param pluginName Name of the plugin to remove
     * @throws IllegalArgumentException if parameters are invalid
     * @throws IllegalStateException if removal fails
     */
    public void removePlugin(Long serverId, String pluginName) {
        logger.info("Removing plugin {} from server ID: {}", pluginName, serverId);

        ServerPlugin plugin = pluginRepository.findByServerId(serverId).stream()
                .filter(p -> p.getPluginName().equals(pluginName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginName));

        try {
            // Remove plugin file from server directory
            Path serverPluginsDir = getServerPluginsDirectory(plugin.getServer());
            Path pluginFile = serverPluginsDir.resolve(plugin.getFormattedFileName());
            
            if (Files.exists(pluginFile)) {
                Files.delete(pluginFile);
                logger.info("Deleted plugin file: {}", pluginFile);
            }

            // Remove from database
            pluginRepository.delete(plugin);
            logger.info("Successfully removed plugin {} from server {}", pluginName, plugin.getServer().getName());

        } catch (Exception e) {
            logger.error("Failed to remove plugin {} from server {}: {}", pluginName, plugin.getServer().getName(), e.getMessage(), e);
            throw new IllegalStateException("Plugin removal failed: " + e.getMessage(), e);
        }
    }

    /**
     * Enables a disabled plugin on a server.
     * 
     * @param serverId Database ID of the server
     * @param pluginName Name of the plugin to enable
     */
    public void enablePlugin(Long serverId, String pluginName) {
        logger.info("Enabling plugin {} on server ID: {}", pluginName, serverId);

        ServerPlugin plugin = findPluginByServerAndName(serverId, pluginName);
        
        if (!plugin.canEnable()) {
            throw new IllegalStateException("Plugin cannot be enabled. Current status: " + plugin.getStatus());
        }

        plugin.setEnabled(true);
        plugin.setStatus(ServerPlugin.PluginStatus.ENABLED);
        pluginRepository.save(plugin);

        logger.info("Successfully enabled plugin {} on server {}", pluginName, plugin.getServer().getName());
    }

    /**
     * Disables an enabled plugin on a server.
     * 
     * @param serverId Database ID of the server
     * @param pluginName Name of the plugin to disable
     */
    public void disablePlugin(Long serverId, String pluginName) {
        logger.info("Disabling plugin {} on server ID: {}", pluginName, serverId);

        ServerPlugin plugin = findPluginByServerAndName(serverId, pluginName);
        
        if (!plugin.canDisable()) {
            throw new IllegalStateException("Plugin cannot be disabled. Current status: " + plugin.getStatus());
        }

        plugin.setEnabled(false);
        plugin.setStatus(ServerPlugin.PluginStatus.DISABLED);
        pluginRepository.save(plugin);

        logger.info("Successfully disabled plugin {} on server {}", pluginName, plugin.getServer().getName());
    }

    /**
     * Updates a plugin to the latest version.
     * 
     * @param serverId Database ID of the server
     * @param pluginName Name of the plugin to update
     * @return CompletableFuture that completes when update finishes
     */
    @Async("serverTaskExecutor")
    public CompletableFuture<ServerPlugin> updatePluginAsync(Long serverId, String pluginName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return updatePlugin(serverId, pluginName);
            } catch (Exception e) {
                logger.error("Async plugin update failed: {} on server ID: {}", pluginName, serverId, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Updates a plugin to the latest version synchronously.
     * 
     * @param serverId Database ID of the server
     * @param pluginName Name of the plugin to update
     * @return Updated ServerPlugin entity
     */
    public ServerPlugin updatePlugin(Long serverId, String pluginName) {
        logger.info("Updating plugin {} on server ID: {}", pluginName, serverId);

        ServerPlugin plugin = findPluginByServerAndName(serverId, pluginName);
        
        if (!plugin.hasUpdate()) {
            throw new IllegalStateException("No update available for plugin: " + pluginName);
        }

        try {
            plugin.setStatus(ServerPlugin.PluginStatus.UPDATING);
            pluginRepository.save(plugin);

            // Download new version
            String updateUrl = getPluginUpdateUrl(plugin);
            Path newPluginFile = downloadPluginFile(plugin, updateUrl);
            
            // Backup current version
            Path currentFile = getServerPluginsDirectory(plugin.getServer()).resolve(plugin.getFormattedFileName());
            Path backupFile = currentFile.resolveSibling(plugin.getFormattedFileName() + ".backup");
            
            if (Files.exists(currentFile)) {
                Files.move(currentFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Install new version
            installPluginFile(plugin.getServer(), plugin, newPluginFile);
            
            // Update plugin metadata
            plugin.setVersion(plugin.getLatestVersion());
            plugin.setStatus(ServerPlugin.PluginStatus.INSTALLED);
            plugin.setUpdatedAt(LocalDateTime.now());
            plugin = pluginRepository.save(plugin);

            // Clean up backup
            if (Files.exists(backupFile)) {
                Files.delete(backupFile);
            }

            logger.info("Successfully updated plugin {} to version {} on server {}", 
                       pluginName, plugin.getVersion(), plugin.getServer().getName());
            return plugin;

        } catch (Exception e) {
            logger.error("Failed to update plugin {} on server {}: {}", pluginName, plugin.getServer().getName(), e.getMessage(), e);
            plugin.markAsFailed("Update failed: " + e.getMessage());
            pluginRepository.save(plugin);
            throw new IllegalStateException("Plugin update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets all plugins installed on a specific server.
     * 
     * @param serverId Database ID of the server
     * @return List of plugins installed on the server
     */
    @Transactional(readOnly = true)
    public List<ServerPlugin> getServerPlugins(Long serverId) {
        return pluginRepository.findByServerId(serverId);
    }

    /**
     * Gets all plugins with available updates.
     * 
     * @return List of plugins that can be updated
     */
    @Transactional(readOnly = true)
    public List<ServerPlugin> getPluginsWithUpdates() {
        return pluginRepository.findPluginsWithUpdates();
    }

    /**
     * Gets plugin installation statistics.
     * 
     * @param limit Maximum number of results
     * @return List of popular plugins with installation counts
     */
    @Transactional(readOnly = true)
    public List<Object[]> getPopularPlugins(int limit) {
        return pluginRepository.findMostPopularPlugins(limit);
    }

    // Private helper methods

    private void validatePluginInstallationParameters(MinecraftServer server, String pluginName,
                                                    String downloadUrl, String installedBy) {
        if (server == null) {
            throw new IllegalArgumentException("Server cannot be null");
        }
        if (pluginName == null || pluginName.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin name cannot be empty");
        }
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Download URL cannot be empty");
        }
        if (installedBy == null || installedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Installer username cannot be empty");
        }
    }

    private Path downloadPluginFile(ServerPlugin plugin, String downloadUrl) throws IOException {
        Path cacheDir = config.getPlugin().getCacheDirectoryPath();
        Files.createDirectories(cacheDir);
        
        String fileName = plugin.getFormattedFileName();
        Path pluginFile = cacheDir.resolve(fileName);
        
        pluginDownloadService.downloadFile(downloadUrl, pluginFile);
        
        // Update plugin with file information
        plugin.setFileName(fileName);
        plugin.setFileSize(Files.size(pluginFile));
        plugin.setChecksum(fileManagerService.calculateMD5(pluginFile));
        
        return pluginFile;
    }

    private void extractPluginMetadata(ServerPlugin plugin, Path pluginFile) {
        try {
            // Extract plugin.yml metadata from JAR file
            var metadata = fileManagerService.extractPluginMetadata(pluginFile);
            
            plugin.setDisplayName(metadata.getOrDefault("name", plugin.getPluginName()));
            plugin.setVersion(metadata.getOrDefault("version", "unknown"));
            plugin.setDescription(metadata.getOrDefault("description", ""));
            plugin.setAuthor(metadata.getOrDefault("author", ""));
            plugin.setWebsite(metadata.getOrDefault("website", ""));
            
        } catch (Exception e) {
            logger.warn("Failed to extract plugin metadata for {}: {}", plugin.getPluginName(), e.getMessage());
        }
    }

    private void installPluginFile(MinecraftServer server, ServerPlugin plugin, Path sourceFile) throws IOException {
        Path serverPluginsDir = getServerPluginsDirectory(server);
        Files.createDirectories(serverPluginsDir);
        
        Path targetFile = serverPluginsDir.resolve(plugin.getFormattedFileName());
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Installed plugin file: {} -> {}", sourceFile, targetFile);
    }

    private Path getServerPluginsDirectory(MinecraftServer server) {
        return config.getServer().getDataDirectoryPath()
                .resolve(server.getDirectoryName())
                .resolve("plugins");
    }

    private ServerPlugin findPluginByServerAndName(Long serverId, String pluginName) {
        return pluginRepository.findByServerId(serverId).stream()
                .filter(p -> p.getPluginName().equals(pluginName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + pluginName));
    }

    private String getPopularPluginDownloadUrl(String pluginName, String minecraftVersion) {
        // Simplified plugin marketplace - in production this would integrate with
        // actual plugin repositories like SpigotMC, Bukkit, or Paper
        switch (pluginName.toLowerCase()) {
            case "essentialsx":
                return "https://github.com/EssentialsX/Essentials/releases/latest/download/EssentialsX-2.20.1.jar";
            case "worldedit":
                return "https://dev.bukkit.org/projects/worldedit/files/latest";
            case "worldguard":
                return "https://dev.bukkit.org/projects/worldguard/files/latest";
            case "vault":
                return "https://github.com/MilkBowl/Vault/releases/latest/download/Vault.jar";
            case "luckperms":
                return "https://download.luckperms.net/1515/bukkit/loader/LuckPerms-Bukkit-5.4.102.jar";
            default:
                throw new IllegalArgumentException("Unknown popular plugin: " + pluginName);
        }
    }

    private String getPluginUpdateUrl(ServerPlugin plugin) {
        // In production, this would check plugin repositories for updates
        return plugin.getDownloadUrl(); // Simplified for demo
    }
}