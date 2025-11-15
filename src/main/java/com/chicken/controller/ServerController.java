/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * REST API controller for Minecraft server management operations.
 * Provides comprehensive HTTP endpoints for server lifecycle management,
 * configuration, monitoring, and integration with web interfaces.
 */
package com.chicken.controller;

import com.chicken.model.MinecraftServer;
import com.chicken.service.MinecraftServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST API controller for Minecraft server management.
 * 
 * This controller provides comprehensive HTTP endpoints for:
 * - Server CRUD operations (Create, Read, Update, Delete)
 * - Server lifecycle management (Start, Stop, Restart)
 * - Server monitoring and status reporting
 * - Configuration management and updates
 * - Resource usage and performance metrics
 * 
 * All endpoints follow RESTful conventions with proper HTTP status codes,
 * error handling, and JSON response formatting for web interface integration.
 */
@RestController
@RequestMapping("/api/v1/servers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ServerController {

    private static final Logger logger = LoggerFactory.getLogger(ServerController.class);

    @Autowired
    private MinecraftServerService serverService;

    /**
     * Gets all Minecraft servers with optional filtering.
     * 
     * @param owner Optional owner filter
     * @param status Optional status filter
     * @return List of servers matching the criteria
     */
    @GetMapping
    public ResponseEntity<List<MinecraftServer>> getAllServers(
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) MinecraftServer.ServerStatus status) {
        
        logger.info("GET /api/v1/servers - owner: {}, status: {}", owner, status);
        
        try {
            List<MinecraftServer> servers;
            
            if (owner != null && status != null) {
                servers = serverService.getServersByOwner(owner).stream()
                        .filter(s -> s.getStatus() == status)
                        .toList();
            } else if (owner != null) {
                servers = serverService.getServersByOwner(owner);
            } else if (status != null) {
                servers = serverService.getServersByStatus(status);
            } else {
                servers = serverService.getAllServers();
            }
            
            logger.info("Retrieved {} servers", servers.size());
            return ResponseEntity.ok(servers);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve servers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets a specific Minecraft server by ID.
     * 
     * @param id Server database ID
     * @return Server details or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<MinecraftServer> getServerById(@PathVariable Long id) {
        logger.info("GET /api/v1/servers/{}", id);
        
        try {
            Optional<MinecraftServer> server = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(id))
                    .findFirst();
            
            if (server.isPresent()) {
                logger.info("Retrieved server: {}", server.get().getName());
                return ResponseEntity.ok(server.get());
            } else {
                logger.warn("Server not found with ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve server with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets a specific Minecraft server by name.
     * 
     * @param name Server name
     * @return Server details or 404 if not found
     */
    @GetMapping("/by-name/{name}")
    public ResponseEntity<MinecraftServer> getServerByName(@PathVariable String name) {
        logger.info("GET /api/v1/servers/by-name/{}", name);
        
        try {
            Optional<MinecraftServer> server = serverService.getServerByName(name);
            
            if (server.isPresent()) {
                logger.info("Retrieved server by name: {}", name);
                return ResponseEntity.ok(server.get());
            } else {
                logger.warn("Server not found with name: {}", name);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve server with name: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Creates a new Minecraft server.
     * 
     * @param request Server creation request
     * @return Created server details or error response
     */
    @PostMapping
    public ResponseEntity<?> createServer(@RequestBody CreateServerRequest request) {
        logger.info("POST /api/v1/servers - Creating server: {}", request.getName());
        
        try {
            // Validate request
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Server name is required"));
            }
            
            // Set defaults
            if (request.getServerType() == null) {
                request.setServerType(MinecraftServer.ServerType.PAPER);
            }
            if (request.getMinecraftVersion() == null) {
                request.setMinecraftVersion("1.20.1");
            }
            if (request.getMemoryMb() == null) {
                request.setMemoryMb(2048);
            }
            if (request.getOwner() == null) {
                request.setOwner("api-user");
            }
            
            MinecraftServer server = serverService.createServer(
                    request.getName(),
                    request.getServerType(),
                    request.getMinecraftVersion(),
                    request.getMemoryMb(),
                    request.getOwner()
            );
            
            logger.info("Successfully created server: {} (ID: {})", server.getName(), server.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(server);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid server creation request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create server: {}", request.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server creation failed: " + e.getMessage()));
        }
    }

    /**
     * Starts a Minecraft server asynchronously.
     * 
     * @param id Server database ID
     * @return Accepted response for async operation
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<?> startServer(@PathVariable Long id) {
        logger.info("POST /api/v1/servers/{}/start", id);
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(id))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            MinecraftServer server = serverOpt.get();
            
            if (!server.canStart()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Server cannot be started. Current status: " + server.getStatus()));
            }
            
            // Start server asynchronously
            CompletableFuture<Void> startFuture = serverService.startServerAsync(id);
            
            logger.info("Server start initiated for: {}", server.getName());
            return ResponseEntity.accepted()
                    .body(Map.of("message", "Server start initiated", "serverId", id));
            
        } catch (Exception e) {
            logger.error("Failed to start server with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server start failed: " + e.getMessage()));
        }
    }

    /**
     * Stops a running Minecraft server.
     * 
     * @param id Server database ID
     * @return Success response or error
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stopServer(@PathVariable Long id) {
        logger.info("POST /api/v1/servers/{}/stop", id);
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(id))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            MinecraftServer server = serverOpt.get();
            
            if (!server.canStop()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Server cannot be stopped. Current status: " + server.getStatus()));
            }
            
            serverService.stopServer(id);
            
            logger.info("Successfully stopped server: {}", server.getName());
            return ResponseEntity.ok()
                    .body(Map.of("message", "Server stopped successfully", "serverId", id));
            
        } catch (Exception e) {
            logger.error("Failed to stop server with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server stop failed: " + e.getMessage()));
        }
    }

    /**
     * Restarts a Minecraft server (stop then start).
     * 
     * @param id Server database ID
     * @return Accepted response for async operation
     */
    @PostMapping("/{id}/restart")
    public ResponseEntity<?> restartServer(@PathVariable Long id) {
        logger.info("POST /api/v1/servers/{}/restart", id);
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(id))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            MinecraftServer server = serverOpt.get();
            
            // Stop server if running
            if (server.isRunning()) {
                serverService.stopServer(id);
            }
            
            // Start server asynchronously
            CompletableFuture<Void> startFuture = serverService.startServerAsync(id);
            
            logger.info("Server restart initiated for: {}", server.getName());
            return ResponseEntity.accepted()
                    .body(Map.of("message", "Server restart initiated", "serverId", id));
            
        } catch (Exception e) {
            logger.error("Failed to restart server with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server restart failed: " + e.getMessage()));
        }
    }

    /**
     * Deletes a Minecraft server and all its data.
     * 
     * @param id Server database ID
     * @return Success response or error
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteServer(@PathVariable Long id) {
        logger.info("DELETE /api/v1/servers/{}", id);
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(id))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            MinecraftServer server = serverOpt.get();
            
            if (server.isRunning()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot delete running server. Stop the server first."));
            }
            
            serverService.deleteServer(id);
            
            logger.info("Successfully deleted server: {}", server.getName());
            return ResponseEntity.ok()
                    .body(Map.of("message", "Server deleted successfully", "serverId", id));
            
        } catch (Exception e) {
            logger.error("Failed to delete server with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server deletion failed: " + e.getMessage()));
        }
    }

    /**
     * Checks if a server name is available.
     * 
     * @param name Server name to check
     * @return Availability status
     */
    @GetMapping("/check-name/{name}")
    public ResponseEntity<Map<String, Boolean>> checkServerNameAvailability(@PathVariable String name) {
        logger.info("GET /api/v1/servers/check-name/{}", name);
        
        try {
            boolean available = serverService.isServerNameAvailable(name);
            return ResponseEntity.ok(Map.of("available", available));
            
        } catch (Exception e) {
            logger.error("Failed to check server name availability: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets system statistics and resource usage.
     * 
     * @return System statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        logger.info("GET /api/v1/servers/stats");
        
        try {
            List<MinecraftServer> allServers = serverService.getAllServers();
            List<MinecraftServer> runningServers = serverService.getServersByStatus(MinecraftServer.ServerStatus.RUNNING);
            List<MinecraftServer> stoppedServers = serverService.getServersByStatus(MinecraftServer.ServerStatus.STOPPED);
            
            long totalMemory = allServers.stream().mapToLong(MinecraftServer::getMemoryMb).sum();
            long runningMemory = runningServers.stream().mapToLong(MinecraftServer::getMemoryMb).sum();
            
            Map<String, Object> stats = Map.of(
                    "totalServers", allServers.size(),
                    "runningServers", runningServers.size(),
                    "stoppedServers", stoppedServers.size(),
                    "totalMemoryMB", totalMemory,
                    "runningMemoryMB", runningMemory,
                    "availableMemoryMB", totalMemory - runningMemory
            );
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Failed to get system stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Request DTO for server creation.
     */
    public static class CreateServerRequest {
        private String name;
        private MinecraftServer.ServerType serverType;
        private String minecraftVersion;
        private Integer memoryMb;
        private String owner;
        private String displayName;
        private String description;
        private Integer maxPlayers;
        private String motd;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public MinecraftServer.ServerType getServerType() { return serverType; }
        public void setServerType(MinecraftServer.ServerType serverType) { this.serverType = serverType; }

        public String getMinecraftVersion() { return minecraftVersion; }
        public void setMinecraftVersion(String minecraftVersion) { this.minecraftVersion = minecraftVersion; }

        public Integer getMemoryMb() { return memoryMb; }
        public void setMemoryMb(Integer memoryMb) { this.memoryMb = memoryMb; }

        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Integer getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(Integer maxPlayers) { this.maxPlayers = maxPlayers; }

        public String getMotd() { return motd; }
        public void setMotd(String motd) { this.motd = motd; }
    }
}