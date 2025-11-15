/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Repository interface for ServerPlugin data access operations.
 * Provides comprehensive database operations for plugin management including
 * installation tracking, version management, and server-plugin relationships.
 */
package com.chicken.repository;

import com.chicken.model.MinecraftServer;
import com.chicken.model.ServerPlugin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ServerPlugin entity operations.
 * 
 * This repository provides:
 * - Standard CRUD operations for plugin management
 * - Server-specific plugin queries
 * - Plugin status and version tracking
 * - Installation and update monitoring
 * - Plugin marketplace integration support
 * 
 * All methods support the complete plugin lifecycle from installation
 * through updates and removal across multiple Minecraft servers.
 */
@Repository
public interface ServerPluginRepository extends JpaRepository<ServerPlugin, Long> {

    /**
     * Finds all plugins installed on a specific server.
     * 
     * @param server The Minecraft server instance
     * @return List of plugins installed on the server
     */
    List<ServerPlugin> findByServer(MinecraftServer server);

    /**
     * Finds all plugins installed on a server by server ID.
     * 
     * @param serverId The server's database ID
     * @return List of plugins installed on the server
     */
    List<ServerPlugin> findByServerId(Long serverId);

    /**
     * Finds a specific plugin on a specific server.
     * 
     * @param server The Minecraft server instance
     * @param pluginName Name of the plugin
     * @return Optional containing the plugin if found
     */
    Optional<ServerPlugin> findByServerAndPluginName(MinecraftServer server, String pluginName);

    /**
     * Finds all instances of a plugin across all servers.
     * Used for global plugin management and updates.
     * 
     * @param pluginName Name of the plugin
     * @return List of all installations of the plugin
     */
    List<ServerPlugin> findByPluginName(String pluginName);

    /**
     * Finds all plugins with a specific status.
     * Used for monitoring and bulk operations.
     * 
     * @param status Plugin status to filter by
     * @return List of plugins with the specified status
     */
    List<ServerPlugin> findByStatus(ServerPlugin.PluginStatus status);

    /**
     * Finds all enabled plugins on a specific server.
     * 
     * @param server The Minecraft server instance
     * @return List of enabled plugins on the server
     */
    List<ServerPlugin> findByServerAndEnabled(MinecraftServer server, Boolean enabled);

    /**
     * Finds all plugins installed by a specific user.
     * Used for audit trails and user activity tracking.
     * 
     * @param installedBy Username of the installer
     * @return List of plugins installed by the user
     */
    List<ServerPlugin> findByInstalledBy(String installedBy);

    /**
     * Finds plugins on a server with a specific status.
     * 
     * @param server The Minecraft server instance
     * @param status Plugin status to filter by
     * @return List of matching plugins
     */
    List<ServerPlugin> findByServerAndStatus(MinecraftServer server, ServerPlugin.PluginStatus status);

    /**
     * Finds all currently installing plugins.
     * Convenience method for monitoring active installations.
     * 
     * @return List of plugins with INSTALLING status
     */
    default List<ServerPlugin> findInstallingPlugins() {
        return findByStatus(ServerPlugin.PluginStatus.INSTALLING);
    }

    /**
     * Finds all plugins with errors.
     * Convenience method for identifying failed installations.
     * 
     * @return List of plugins with ERROR status
     */
    default List<ServerPlugin> findErrorPlugins() {
        return findByStatus(ServerPlugin.PluginStatus.ERROR);
    }

    /**
     * Counts total number of plugins on a server.
     * 
     * @param server The Minecraft server instance
     * @return Number of plugins installed on the server
     */
    long countByServer(MinecraftServer server);

    /**
     * Counts plugins by status on a specific server.
     * 
     * @param server The Minecraft server instance
     * @param status Plugin status to count
     * @return Number of matching plugins
     */
    long countByServerAndStatus(MinecraftServer server, ServerPlugin.PluginStatus status);

    /**
     * Counts total installations of a specific plugin.
     * Used for popularity tracking and analytics.
     * 
     * @param pluginName Name of the plugin
     * @return Number of installations across all servers
     */
    long countByPluginName(String pluginName);

    /**
     * Finds plugins installed after a specific timestamp.
     * Used for reporting and activity monitoring.
     * 
     * @param timestamp Cutoff timestamp
     * @return List of plugins installed after the timestamp
     */
    List<ServerPlugin> findByInstalledAtAfter(LocalDateTime timestamp);

    /**
     * Finds plugins updated after a specific timestamp.
     * Used for tracking recent plugin updates.
     * 
     * @param timestamp Cutoff timestamp
     * @return List of plugins updated after the timestamp
     */
    List<ServerPlugin> findByUpdatedAtAfter(LocalDateTime timestamp);

    /**
     * Custom query to find plugins that have available updates.
     * Compares current version with latest available version.
     * 
     * @return List of plugins with available updates
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.latestVersion IS NOT NULL " +
           "AND p.latestVersion != p.version AND p.autoUpdate = true")
    List<ServerPlugin> findPluginsWithUpdates();

    /**
     * Custom query to find plugins that haven't been checked recently.
     * Used for maintenance and update checking.
     * 
     * @param cutoffDate Timestamp before which plugins need checking
     * @return List of plugins that need status updates
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.lastCheckedAt < :cutoffDate")
    List<ServerPlugin> findPluginsNeedingCheck(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Custom query to find the most popular plugins.
     * Returns plugins ordered by installation count.
     * 
     * @param limit Maximum number of results to return
     * @return List of most popular plugins with installation counts
     */
    @Query("SELECT p.pluginName, COUNT(p) as installCount FROM ServerPlugin p " +
           "GROUP BY p.pluginName ORDER BY installCount DESC")
    List<Object[]> findMostPopularPlugins(@Param("limit") int limit);

    /**
     * Custom query to find plugins by version pattern.
     * Supports version-specific operations and compatibility checks.
     * 
     * @param versionPattern SQL LIKE pattern for version matching
     * @return List of plugins with matching versions
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.version LIKE :versionPattern")
    List<ServerPlugin> findByVersionPattern(@Param("versionPattern") String versionPattern);

    /**
     * Custom query to find plugins with large file sizes.
     * Used for storage management and cleanup operations.
     * 
     * @param minSize Minimum file size in bytes
     * @return List of plugins with file size >= minSize
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.fileSize >= :minSize")
    List<ServerPlugin> findLargePlugins(@Param("minSize") Long minSize);

    /**
     * Custom query to find duplicate plugins across servers.
     * Identifies servers running the same plugin for optimization.
     * 
     * @param pluginName Name of the plugin to check
     * @return List of servers running the specified plugin
     */
    @Query("SELECT p.server FROM ServerPlugin p WHERE p.pluginName = :pluginName")
    List<MinecraftServer> findServersWithPlugin(@Param("pluginName") String pluginName);

    /**
     * Custom query to get plugin statistics for a server.
     * Returns counts by status for monitoring dashboards.
     * 
     * @param serverId The server's database ID
     * @return List of status counts [status, count]
     */
    @Query("SELECT p.status, COUNT(p) FROM ServerPlugin p WHERE p.server.id = :serverId GROUP BY p.status")
    List<Object[]> getPluginStatsByServer(@Param("serverId") Long serverId);

    /**
     * Custom query to find plugins installed by author.
     * Used for author-based plugin management and updates.
     * 
     * @param author Plugin author name
     * @return List of plugins by the specified author
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.author LIKE :author")
    List<ServerPlugin> findByAuthor(@Param("author") String author);

    /**
     * Custom query to find plugins that failed installation recently.
     * Used for troubleshooting and retry operations.
     * 
     * @param cutoffDate Timestamp for recent failures
     * @return List of plugins that failed after the cutoff date
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.status = 'ERROR' AND p.lastCheckedAt >= :cutoffDate")
    List<ServerPlugin> findRecentFailures(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Custom query to search plugins by name pattern.
     * Supports partial matching for plugin discovery.
     * 
     * @param namePattern SQL LIKE pattern for plugin name matching
     * @return List of plugins with names matching the pattern
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.pluginName LIKE :namePattern OR p.displayName LIKE :namePattern")
    List<ServerPlugin> findByNamePattern(@Param("namePattern") String namePattern);

    /**
     * Custom query to find plugins with configuration data.
     * Used for configuration management and backup operations.
     * 
     * @return List of plugins that have custom configuration
     */
    @Query("SELECT p FROM ServerPlugin p WHERE p.configData IS NOT NULL AND p.configData != ''")
    List<ServerPlugin> findPluginsWithConfig();

    /**
     * Checks if a plugin is already installed on a server.
     * 
     * @param server The Minecraft server instance
     * @param pluginName Name of the plugin
     * @return true if the plugin is installed
     */
    default boolean isPluginInstalled(MinecraftServer server, String pluginName) {
        return findByServerAndPluginName(server, pluginName).isPresent();
    }

    /**
     * Gets the count of enabled plugins on a server.
     * 
     * @param server The Minecraft server instance
     * @return Number of enabled plugins
     */
    default long getEnabledPluginCount(MinecraftServer server) {
        return countByServerAndStatus(server, ServerPlugin.PluginStatus.ENABLED);
    }
}