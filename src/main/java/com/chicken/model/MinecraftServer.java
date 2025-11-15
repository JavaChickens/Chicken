/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Entity model representing a Minecraft server instance in the hosting platform.
 * This class defines the data structure and business logic for managing
 * individual Minecraft servers including their configuration, state, and metadata.
 */
package com.chicken.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a hosted Minecraft server instance.
 * 
 * This class encapsulates all information required to manage a Minecraft server:
 * - Server identification and metadata
 * - Configuration parameters (memory, version, type)
 * - Runtime state and status information
 * - Plugin associations and management
 * - Resource usage tracking
 * - Audit trail for operations
 * 
 * The entity is mapped to the 'minecraft_servers' table and includes
 * comprehensive validation and business logic for server lifecycle management.
 */
@Entity
@Table(name = "minecraft_servers")
public class MinecraftServer {

    /**
     * Enumeration of possible server states in the lifecycle.
     * 
     * State transitions:
     * STOPPED -> STARTING -> RUNNING -> STOPPING -> STOPPED
     * Any state -> ERROR (on failure)
     * ERROR -> STOPPED (on recovery)
     */
    public enum ServerStatus {
        STOPPED("Server is stopped"),
        STARTING("Server is starting up"),
        RUNNING("Server is running"),
        STOPPING("Server is shutting down"),
        ERROR("Server encountered an error");

        private final String description;

        ServerStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Enumeration of supported Minecraft server types.
     * 
     * Each type has different capabilities and plugin compatibility:
     * - VANILLA: Official Minecraft server (no plugin support)
     * - BUKKIT: Legacy Bukkit server (basic plugin support)
     * - SPIGOT: Enhanced Bukkit fork (improved performance)
     * - PAPER: High-performance Spigot fork (recommended)
     */
    public enum ServerType {
        VANILLA("minecraft", "Official Minecraft Server"),
        BUKKIT("bukkit", "Bukkit Server"),
        SPIGOT("spigot", "Spigot Server"),
        PAPER("paper", "Paper Server");

        private final String identifier;
        private final String displayName;

        ServerType(String identifier, String displayName) {
            this.identifier = identifier;
            this.displayName = displayName;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique server name used for identification and directory naming.
     * Must be unique across all servers in the platform.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Human-readable display name for the server.
     * Can contain spaces and special characters.
     */
    @Column(length = 100)
    private String displayName;

    /**
     * Optional description of the server purpose or configuration.
     */
    @Column(length = 500)
    private String description;

    /**
     * Current operational status of the server.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServerStatus status = ServerStatus.STOPPED;

    /**
     * Type of Minecraft server (Paper, Spigot, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServerType serverType = ServerType.PAPER;

    /**
     * Minecraft version for this server (e.g., "1.20.1").
     */
    @Column(nullable = false, length = 20)
    private String minecraftVersion;

    /**
     * Memory allocation in megabytes for the server JVM.
     */
    @Column(nullable = false)
    private Integer memoryMb;

    /**
     * Network port for the Minecraft server.
     * Must be unique across all running servers.
     */
    @Column(nullable = false, unique = true)
    private Integer port;

    /**
     * Maximum number of players allowed on the server.
     */
    @Column(nullable = false)
    private Integer maxPlayers = 20;

    /**
     * Server difficulty level (0=Peaceful, 1=Easy, 2=Normal, 3=Hard).
     */
    @Column(nullable = false)
    private Integer difficulty = 1;

    /**
     * Game mode for new players (0=Survival, 1=Creative, 2=Adventure, 3=Spectator).
     */
    @Column(nullable = false)
    private Integer gameMode = 0;

    /**
     * Whether the server is in hardcore mode.
     */
    @Column(nullable = false)
    private Boolean hardcore = false;

    /**
     * Whether player vs player combat is enabled.
     */
    @Column(nullable = false)
    private Boolean pvp = true;

    /**
     * Whether online mode (authentication) is enabled.
     */
    @Column(nullable = false)
    private Boolean onlineMode = true;

    /**
     * Message of the day displayed in server list.
     */
    @Column(length = 200)
    private String motd = "A Chicken Hosted Minecraft Server";

    /**
     * Process ID of the running server (null if stopped).
     */
    @Column
    private Long processId;

    /**
     * Timestamp when the server was created.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the server was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Timestamp when the server was last started.
     */
    @Column
    private LocalDateTime lastStartedAt;

    /**
     * Timestamp when the server was last stopped.
     */
    @Column
    private LocalDateTime lastStoppedAt;

    /**
     * Owner/creator of the server for multi-tenant scenarios.
     */
    @Column(nullable = false, length = 50)
    private String owner;

    /**
     * List of installed plugins on this server.
     */
    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ServerPlugin> plugins = new ArrayList<>();

    /**
     * Default constructor for JPA.
     */
    public MinecraftServer() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new server with basic parameters.
     * 
     * @param name Unique server name
     * @param serverType Type of Minecraft server
     * @param minecraftVersion Minecraft version
     * @param memoryMb Memory allocation in MB
     * @param port Network port
     * @param owner Server owner/creator
     */
    public MinecraftServer(String name, ServerType serverType, String minecraftVersion, 
                          Integer memoryMb, Integer port, String owner) {
        this();
        this.name = name;
        this.displayName = name;
        this.serverType = serverType;
        this.minecraftVersion = minecraftVersion;
        this.memoryMb = memoryMb;
        this.port = port;
        this.owner = owner;
    }

    /**
     * Updates the last modified timestamp.
     * Called automatically before entity updates.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if the server is currently running.
     * 
     * @return true if server status is RUNNING
     */
    public boolean isRunning() {
        return status == ServerStatus.RUNNING;
    }

    /**
     * Checks if the server can be started.
     * 
     * @return true if server is in STOPPED state
     */
    public boolean canStart() {
        return status == ServerStatus.STOPPED;
    }

    /**
     * Checks if the server can be stopped.
     * 
     * @return true if server is in RUNNING state
     */
    public boolean canStop() {
        return status == ServerStatus.RUNNING;
    }

    /**
     * Gets the server directory name based on the server name.
     * 
     * @return Directory name for file system operations
     */
    public String getDirectoryName() {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    /**
     * Gets a summary string for logging and display purposes.
     * 
     * @return Human-readable server summary
     */
    public String getSummary() {
        return String.format("%s (%s %s) - %s:%d [%s]", 
                displayName != null ? displayName : name,
                serverType.getDisplayName(),
                minecraftVersion,
                "localhost", port,
                status.getDescription());
    }

    // Standard getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ServerStatus getStatus() { return status; }
    public void setStatus(ServerStatus status) { this.status = status; }

    public ServerType getServerType() { return serverType; }
    public void setServerType(ServerType serverType) { this.serverType = serverType; }

    public String getMinecraftVersion() { return minecraftVersion; }
    public void setMinecraftVersion(String minecraftVersion) { this.minecraftVersion = minecraftVersion; }

    public Integer getMemoryMb() { return memoryMb; }
    public void setMemoryMb(Integer memoryMb) { this.memoryMb = memoryMb; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public Integer getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }

    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public Integer getGameMode() { return gameMode; }
    public void setGameMode(Integer gameMode) { this.gameMode = gameMode; }

    public Boolean getHardcore() { return hardcore; }
    public void setHardcore(Boolean hardcore) { this.hardcore = hardcore; }

    public Boolean getPvp() { return pvp; }
    public void setPvp(Boolean pvp) { this.pvp = pvp; }

    public Boolean getOnlineMode() { return onlineMode; }
    public void setOnlineMode(Boolean onlineMode) { this.onlineMode = onlineMode; }

    public String getMotd() { return motd; }
    public void setMotd(String motd) { this.motd = motd; }

    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastStartedAt() { return lastStartedAt; }
    public void setLastStartedAt(LocalDateTime lastStartedAt) { this.lastStartedAt = lastStartedAt; }

    public LocalDateTime getLastStoppedAt() { return lastStoppedAt; }
    public void setLastStoppedAt(LocalDateTime lastStoppedAt) { this.lastStoppedAt = lastStoppedAt; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public List<ServerPlugin> getPlugins() { return plugins; }
    public void setPlugins(List<ServerPlugin> plugins) { this.plugins = plugins; }
}