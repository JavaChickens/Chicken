/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Entity model representing a plugin installed on a Minecraft server.
 * This class manages the relationship between servers and their installed plugins,
 * including version tracking, installation status, and configuration management.
 */
package com.chicken.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a plugin installation on a specific Minecraft server.
 * 
 * This class tracks:
 * - Plugin identification and metadata
 * - Installation status and version information
 * - Server association and configuration
 * - Installation and update timestamps
 * - Plugin-specific settings and data
 * 
 * The entity supports both Bukkit and Paper plugin ecosystems,
 * providing comprehensive plugin lifecycle management capabilities.
 */
@Entity
@Table(name = "server_plugins", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"server_id", "plugin_name"}))
public class ServerPlugin {

    /**
     * Enumeration of plugin installation states.
     * 
     * State transitions:
     * PENDING -> INSTALLING -> INSTALLED -> ENABLED
     * ENABLED -> DISABLING -> DISABLED
     * Any state -> ERROR (on failure)
     * ERROR -> PENDING (on retry)
     */
    public enum PluginStatus {
        PENDING("Plugin installation pending"),
        INSTALLING("Plugin is being installed"),
        INSTALLED("Plugin installed but not loaded"),
        ENABLED("Plugin is active and running"),
        DISABLED("Plugin is installed but disabled"),
        ERROR("Plugin installation or operation failed"),
        UPDATING("Plugin is being updated");

        private final String description;

        PluginStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the Minecraft server hosting this plugin.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private MinecraftServer server;

    /**
     * Plugin name/identifier (e.g., "EssentialsX", "WorldEdit").
     * Must be unique per server.
     */
    @Column(name = "plugin_name", nullable = false, length = 100)
    private String pluginName;

    /**
     * Human-readable display name for the plugin.
     */
    @Column(length = 150)
    private String displayName;

    /**
     * Plugin description and functionality summary.
     */
    @Column(length = 1000)
    private String description;

    /**
     * Currently installed version of the plugin.
     */
    @Column(nullable = false, length = 50)
    private String version;

    /**
     * Latest available version (for update notifications).
     */
    @Column(length = 50)
    private String latestVersion;

    /**
     * Current status of the plugin installation.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PluginStatus status = PluginStatus.PENDING;

    /**
     * Plugin author or development team.
     */
    @Column(length = 200)
    private String author;

    /**
     * Official website or documentation URL.
     */
    @Column(length = 500)
    private String website;

    /**
     * Download URL for the plugin JAR file.
     */
    @Column(length = 1000)
    private String downloadUrl;

    /**
     * File name of the plugin JAR in the plugins directory.
     */
    @Column(length = 200)
    private String fileName;

    /**
     * File size in bytes of the plugin JAR.
     */
    @Column
    private Long fileSize;

    /**
     * MD5 checksum for file integrity verification.
     */
    @Column(length = 32)
    private String checksum;

    /**
     * Whether the plugin is currently enabled on the server.
     */
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * Whether automatic updates are enabled for this plugin.
     */
    @Column(nullable = false)
    private Boolean autoUpdate = false;

    /**
     * Plugin-specific configuration data (JSON format).
     */
    @Column(columnDefinition = "TEXT")
    private String configData;

    /**
     * Error message if plugin installation or operation failed.
     */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * Timestamp when the plugin was first installed.
     */
    @Column(nullable = false)
    private LocalDateTime installedAt;

    /**
     * Timestamp when the plugin was last updated.
     */
    @Column
    private LocalDateTime updatedAt;

    /**
     * Timestamp when the plugin status was last checked.
     */
    @Column
    private LocalDateTime lastCheckedAt;

    /**
     * User who installed or last modified this plugin.
     */
    @Column(length = 50)
    private String installedBy;

    /**
     * Default constructor for JPA.
     */
    public ServerPlugin() {
        this.installedAt = LocalDateTime.now();
        this.lastCheckedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new plugin installation.
     * 
     * @param server Target Minecraft server
     * @param pluginName Plugin identifier
     * @param version Plugin version
     * @param installedBy User performing the installation
     */
    public ServerPlugin(MinecraftServer server, String pluginName, String version, String installedBy) {
        this();
        this.server = server;
        this.pluginName = pluginName;
        this.displayName = pluginName;
        this.version = version;
        this.installedBy = installedBy;
    }

    /**
     * Updates the last checked timestamp.
     * Called when plugin status is verified or updated.
     */
    @PreUpdate
    protected void onUpdate() {
        this.lastCheckedAt = LocalDateTime.now();
    }

    /**
     * Checks if the plugin is currently active and running.
     * 
     * @return true if plugin status is ENABLED
     */
    public boolean isActive() {
        return status == PluginStatus.ENABLED && enabled;
    }

    /**
     * Checks if the plugin can be enabled.
     * 
     * @return true if plugin is installed but not enabled
     */
    public boolean canEnable() {
        return status == PluginStatus.INSTALLED || status == PluginStatus.DISABLED;
    }

    /**
     * Checks if the plugin can be disabled.
     * 
     * @return true if plugin is currently enabled
     */
    public boolean canDisable() {
        return status == PluginStatus.ENABLED;
    }

    /**
     * Checks if an update is available for this plugin.
     * 
     * @return true if latest version is different from current version
     */
    public boolean hasUpdate() {
        return latestVersion != null && !latestVersion.equals(version);
    }

    /**
     * Gets the plugin JAR file name with proper formatting.
     * 
     * @return Formatted file name for the plugin JAR
     */
    public String getFormattedFileName() {
        if (fileName != null) {
            return fileName;
        }
        return pluginName.replaceAll("[^a-zA-Z0-9]", "_") + "-" + version + ".jar";
    }

    /**
     * Gets a summary string for logging and display purposes.
     * 
     * @return Human-readable plugin summary
     */
    public String getSummary() {
        return String.format("%s v%s [%s] on %s", 
                displayName != null ? displayName : pluginName,
                version,
                status.getDescription(),
                server.getName());
    }

    /**
     * Marks the plugin installation as failed with an error message.
     * 
     * @param errorMessage Description of the failure
     */
    public void markAsFailed(String errorMessage) {
        this.status = PluginStatus.ERROR;
        this.errorMessage = errorMessage;
        this.lastCheckedAt = LocalDateTime.now();
    }

    /**
     * Marks the plugin as successfully installed and ready.
     */
    public void markAsInstalled() {
        this.status = PluginStatus.INSTALLED;
        this.errorMessage = null;
        this.lastCheckedAt = LocalDateTime.now();
    }

    // Standard getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MinecraftServer getServer() { return server; }
    public void setServer(MinecraftServer server) { this.server = server; }

    public String getPluginName() { return pluginName; }
    public void setPluginName(String pluginName) { this.pluginName = pluginName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getLatestVersion() { return latestVersion; }
    public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }

    public PluginStatus getStatus() { return status; }
    public void setStatus(PluginStatus status) { this.status = status; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getAutoUpdate() { return autoUpdate; }
    public void setAutoUpdate(Boolean autoUpdate) { this.autoUpdate = autoUpdate; }

    public String getConfigData() { return configData; }
    public void setConfigData(String configData) { this.configData = configData; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDateTime installedAt) { this.installedAt = installedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }

    public String getInstalledBy() { return installedBy; }
    public void setInstalledBy(String installedBy) { this.installedBy = installedBy; }
}