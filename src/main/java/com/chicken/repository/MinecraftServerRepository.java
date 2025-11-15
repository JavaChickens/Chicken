/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Repository interface for Minecraft server data access operations.
 * Provides comprehensive database operations for server management including
 * custom queries for server discovery, status monitoring, and resource management.
 */
package com.chicken.repository;

import com.chicken.model.MinecraftServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MinecraftServer entity operations.
 * 
 * This repository provides:
 * - Standard CRUD operations via JpaRepository
 * - Custom query methods for server management
 * - Performance-optimized queries for monitoring
 * - Multi-tenant support with owner-based filtering
 * - Resource allocation and port management queries
 * 
 * All methods are automatically implemented by Spring Data JPA
 * with additional custom queries for business-specific operations.
 */
@Repository
public interface MinecraftServerRepository extends JpaRepository<MinecraftServer, Long> {

    /**
     * Finds a server by its unique name.
     * 
     * @param name Server name (case-sensitive)
     * @return Optional containing the server if found
     */
    Optional<MinecraftServer> findByName(String name);

    /**
     * Finds a server by its network port.
     * Used for port conflict detection and server discovery.
     * 
     * @param port Network port number
     * @return Optional containing the server using the port
     */
    Optional<MinecraftServer> findByPort(Integer port);

    /**
     * Finds all servers owned by a specific user.
     * Supports multi-tenant deployments with user isolation.
     * 
     * @param owner Username of the server owner
     * @return List of servers owned by the user
     */
    List<MinecraftServer> findByOwner(String owner);

    /**
     * Finds all servers with a specific status.
     * Used for monitoring and bulk operations.
     * 
     * @param status Server status to filter by
     * @return List of servers with the specified status
     */
    List<MinecraftServer> findByStatus(MinecraftServer.ServerStatus status);

    /**
     * Finds all servers of a specific type.
     * Useful for version management and compatibility checks.
     * 
     * @param serverType Type of Minecraft server
     * @return List of servers of the specified type
     */
    List<MinecraftServer> findByServerType(MinecraftServer.ServerType serverType);

    /**
     * Finds servers by owner and status combination.
     * Optimized query for user-specific server management.
     * 
     * @param owner Username of the server owner
     * @param status Server status to filter by
     * @return List of matching servers
     */
    List<MinecraftServer> findByOwnerAndStatus(String owner, MinecraftServer.ServerStatus status);

    /**
     * Finds all currently running servers.
     * Convenience method for monitoring active servers.
     * 
     * @return List of servers with RUNNING status
     */
    default List<MinecraftServer> findRunningServers() {
        return findByStatus(MinecraftServer.ServerStatus.RUNNING);
    }

    /**
     * Finds all stopped servers.
     * Convenience method for identifying available servers.
     * 
     * @return List of servers with STOPPED status
     */
    default List<MinecraftServer> findStoppedServers() {
        return findByStatus(MinecraftServer.ServerStatus.STOPPED);
    }

    /**
     * Counts total number of servers owned by a user.
     * Used for quota enforcement and resource management.
     * 
     * @param owner Username of the server owner
     * @return Number of servers owned by the user
     */
    long countByOwner(String owner);

    /**
     * Counts servers by owner and status.
     * Useful for monitoring user activity and resource usage.
     * 
     * @param owner Username of the server owner
     * @param status Server status to count
     * @return Number of matching servers
     */
    long countByOwnerAndStatus(String owner, MinecraftServer.ServerStatus status);

    /**
     * Finds servers created after a specific timestamp.
     * Used for reporting and analytics.
     * 
     * @param timestamp Cutoff timestamp
     * @return List of servers created after the timestamp
     */
    List<MinecraftServer> findByCreatedAtAfter(LocalDateTime timestamp);

    /**
     * Finds servers last started after a specific timestamp.
     * Used for activity monitoring and usage analytics.
     * 
     * @param timestamp Cutoff timestamp
     * @return List of servers started after the timestamp
     */
    List<MinecraftServer> findByLastStartedAtAfter(LocalDateTime timestamp);

    /**
     * Custom query to find servers using a specific port range.
     * Used for port allocation and conflict detection.
     * 
     * @param minPort Minimum port number (inclusive)
     * @param maxPort Maximum port number (inclusive)
     * @return List of servers using ports in the specified range
     */
    @Query("SELECT s FROM MinecraftServer s WHERE s.port BETWEEN :minPort AND :maxPort")
    List<MinecraftServer> findByPortRange(@Param("minPort") Integer minPort, 
                                         @Param("maxPort") Integer maxPort);

    /**
     * Custom query to find the next available port for server allocation.
     * Returns the lowest unused port starting from the specified minimum.
     * 
     * @param minPort Minimum port to consider
     * @return Next available port number, or null if none found
     */
    @Query("SELECT MIN(s.port + 1) FROM MinecraftServer s WHERE s.port >= :minPort " +
           "AND (s.port + 1) NOT IN (SELECT s2.port FROM MinecraftServer s2)")
    Integer findNextAvailablePort(@Param("minPort") Integer minPort);

    /**
     * Custom query to get server resource usage summary.
     * Returns aggregated memory usage across all servers.
     * 
     * @return Total memory allocation in MB across all servers
     */
    @Query("SELECT COALESCE(SUM(s.memoryMb), 0) FROM MinecraftServer s")
    Long getTotalMemoryUsage();

    /**
     * Custom query to get resource usage by owner.
     * Used for quota enforcement and billing calculations.
     * 
     * @param owner Username of the server owner
     * @return Total memory allocation for the owner's servers
     */
    @Query("SELECT COALESCE(SUM(s.memoryMb), 0) FROM MinecraftServer s WHERE s.owner = :owner")
    Long getMemoryUsageByOwner(@Param("owner") String owner);

    /**
     * Custom query to find servers that haven't been started recently.
     * Used for cleanup and resource optimization.
     * 
     * @param cutoffDate Timestamp before which servers are considered inactive
     * @return List of servers not started since the cutoff date
     */
    @Query("SELECT s FROM MinecraftServer s WHERE s.lastStartedAt IS NULL OR s.lastStartedAt < :cutoffDate")
    List<MinecraftServer> findInactiveServers(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Custom query to find servers with potential issues.
     * Identifies servers that have been in transitional states too long.
     * 
     * @param cutoffDate Timestamp for detecting stuck operations
     * @return List of servers potentially stuck in transitional states
     */
    @Query("SELECT s FROM MinecraftServer s WHERE " +
           "(s.status = 'STARTING' OR s.status = 'STOPPING') AND s.updatedAt < :cutoffDate")
    List<MinecraftServer> findServersWithPotentialIssues(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Custom query to search servers by name pattern.
     * Supports partial matching for server discovery.
     * 
     * @param namePattern SQL LIKE pattern for server name matching
     * @return List of servers with names matching the pattern
     */
    @Query("SELECT s FROM MinecraftServer s WHERE s.name LIKE :namePattern OR s.displayName LIKE :namePattern")
    List<MinecraftServer> findByNamePattern(@Param("namePattern") String namePattern);

    /**
     * Custom query to find servers by Minecraft version pattern.
     * Useful for version-specific operations and compatibility checks.
     * 
     * @param versionPattern SQL LIKE pattern for version matching
     * @return List of servers with matching Minecraft versions
     */
    @Query("SELECT s FROM MinecraftServer s WHERE s.minecraftVersion LIKE :versionPattern")
    List<MinecraftServer> findByMinecraftVersionPattern(@Param("versionPattern") String versionPattern);

    /**
     * Checks if a server name is available (not already taken).
     * 
     * @param name Server name to check
     * @return true if the name is available
     */
    default boolean isNameAvailable(String name) {
        return !findByName(name).isPresent();
    }

    /**
     * Checks if a port is available (not already in use).
     * 
     * @param port Port number to check
     * @return true if the port is available
     */
    default boolean isPortAvailable(Integer port) {
        return !findByPort(port).isPresent();
    }
}