/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * REST API controller for Minecraft plugin management operations.
 * Provides comprehensive HTTP endpoints for plugin lifecycle management,
 * installation, configuration, and marketplace integration.
 */
package com.chicken.controller;

import com.chicken.model.MinecraftServer;
import com.chicken.model.ServerPlugin;
import com.chicken.service.MinecraftServerService;
import com.chicken.service.PluginService;
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
import com.chicken.service.PluginMarketplaceService;

/**
 * REST API controller for Minecraft plugin management.
 * 
 * This controller provides comprehensive HTTP endpoints for:
 * - Plugin installation from URLs or marketplace
 * - Plugin lifecycle management (enable, disable, update, remove)
 * - Plugin discovery and marketplace integration
 * - Server-specific plugin operations
 * - Plugin statistics and monitoring
 * 
 * All endpoints follow RESTful conventions with proper HTTP status codes,
 * error handling, and JSON response formatting for web interface integration.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PluginController {

    private static final Logger logger = LoggerFactory.getLogger(PluginController.class);

    @Autowired
    private PluginService pluginService;

    @Autowired
    private MinecraftServerService serverService;
    
    @Autowired
    private PluginMarketplaceService marketplaceService;

    /**
     * Gets all plugins installed on a specific server.
     * 
     * @param serverId Server database ID
     * @return List of plugins installed on the server
     */
    @GetMapping("/servers/{serverId}/plugins")
    public ResponseEntity<?> getServerPlugins(@PathVariable Long serverId) {
        logger.info("GET /api/v1/servers/{}/plugins", serverId);
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(serverId))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            List<ServerPlugin> plugins = pluginService.getServerPlugins(serverId);
            
            logger.info("Retrieved {} plugins for server ID: {}", plugins.size(), serverId);
            return ResponseEntity.ok(plugins);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve plugins for server ID: {}", serverId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve plugins: " + e.getMessage()));
        }
    }

    /**
     * Installs a plugin on a specific server.
     * 
     * @param serverId Server database ID
     * @param request Plugin installation request
     * @return Accepted response for async operation
     */
    @PostMapping("/servers/{serverId}/plugins")
    public ResponseEntity<?> installPlugin(@PathVariable Long serverId, 
                                         @RequestBody InstallPluginRequest request) {
        logger.info("POST /api/v1/servers/{}/plugins - Installing plugin: {}", serverId, request.getPluginName());
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(serverId))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            MinecraftServer server = serverOpt.get();
            
            // Validate request
            if (request.getPluginName() == null || request.getPluginName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Plugin name is required"));
            }
            
            // Set defaults
            if (request.getInstalledBy() == null) {
                request.setInstalledBy("api-user");
            }
            
            CompletableFuture<ServerPlugin> installFuture;
            
            if (request.getDownloadUrl() != null && !request.getDownloadUrl().trim().isEmpty()) {
                // Install from URL
                installFuture = pluginService.installPluginAsync(
                        server, 
                        request.getPluginName(), 
                        request.getDownloadUrl(), 
                        request.getInstalledBy()
                );
            } else {
                // Install popular plugin
                installFuture = pluginService.installPopularPluginAsync(
                        server, 
                        request.getPluginName(), 
                        request.getInstalledBy()
                );
            }
            
            logger.info("Plugin installation initiated: {} on server {}", request.getPluginName(), server.getName());
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "message", "Plugin installation initiated",
                            "serverId", serverId,
                            "pluginName", request.getPluginName()
                    ));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid plugin installation request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to install plugin {} on server {}: {}", request.getPluginName(), serverId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Plugin installation failed: " + e.getMessage()));
        }
    }

    /**
     * Removes a plugin from a specific server.
     * 
     * @param serverId Server database ID
     * @param pluginName Name of the plugin to remove
     * @return Success response or error
     */
    @DeleteMapping("/servers/{serverId}/plugins/{pluginName}")
    public ResponseEntity<?> removePlugin(@PathVariable Long serverId, @PathVariable String pluginName) {
        logger.info("DELETE /api/v1/servers/{}/plugins/{}", serverId, pluginName);
        
        try {
            // Verify server exists
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(serverId))
                    .findFirst();
            
            if (serverOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            pluginService.removePlugin(serverId, pluginName);
            
            logger.info("Successfully removed plugin {} from server ID: {}", pluginName, serverId);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "message", "Plugin removed successfully",
                            "serverId", serverId,
                            "pluginName", pluginName
                    ));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Plugin not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to remove plugin {} from server {}: {}", pluginName, serverId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Plugin removal failed: " + e.getMessage()));
        }
    }

    /**
     * Enables a disabled plugin on a server.
     * 
     * @param serverId Server database ID
     * @param pluginName Name of the plugin to enable
     * @return Success response or error
     */
    @PostMapping("/servers/{serverId}/plugins/{pluginName}/enable")
    public ResponseEntity<?> enablePlugin(@PathVariable Long serverId, @PathVariable String pluginName) {
        logger.info("POST /api/v1/servers/{}/plugins/{}/enable", serverId, pluginName);
        
        try {
            pluginService.enablePlugin(serverId, pluginName);
            
            logger.info("Successfully enabled plugin {} on server ID: {}", pluginName, serverId);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "message", "Plugin enabled successfully",
                            "serverId", serverId,
                            "pluginName", pluginName
                    ));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Plugin not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.warn("Cannot enable plugin: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to enable plugin {} on server {}: {}", pluginName, serverId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Plugin enable failed: " + e.getMessage()));
        }
    }

    /**
     * Disables an enabled plugin on a server.
     * 
     * @param serverId Server database ID
     * @param pluginName Name of the plugin to disable
     * @return Success response or error
     */
    @PostMapping("/servers/{serverId}/plugins/{pluginName}/disable")
    public ResponseEntity<?> disablePlugin(@PathVariable Long serverId, @PathVariable String pluginName) {
        logger.info("POST /api/v1/servers/{}/plugins/{}/disable", serverId, pluginName);
        
        try {
            pluginService.disablePlugin(serverId, pluginName);
            
            logger.info("Successfully disabled plugin {} on server ID: {}", pluginName, serverId);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "message", "Plugin disabled successfully",
                            "serverId", serverId,
                            "pluginName", pluginName
                    ));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Plugin not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.warn("Cannot disable plugin: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to disable plugin {} on server {}: {}", pluginName, serverId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Plugin disable failed: " + e.getMessage()));
        }
    }

    /**
     * Updates a plugin to the latest version.
     * 
     * @param serverId Server database ID
     * @param pluginName Name of the plugin to update
     * @return Accepted response for async operation
     */
    @PostMapping("/servers/{serverId}/plugins/{pluginName}/update")
    public ResponseEntity<?> updatePlugin(@PathVariable Long serverId, @PathVariable String pluginName) {
        logger.info("POST /api/v1/servers/{}/plugins/{}/update", serverId, pluginName);
        
        try {
            CompletableFuture<ServerPlugin> updateFuture = pluginService.updatePluginAsync(serverId, pluginName);
            
            logger.info("Plugin update initiated: {} on server ID: {}", pluginName, serverId);
            return ResponseEntity.accepted()
                    .body(Map.of(
                            "message", "Plugin update initiated",
                            "serverId", serverId,
                            "pluginName", pluginName
                    ));
            
        } catch (IllegalArgumentException e) {
            logger.warn("Plugin not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.warn("Cannot update plugin: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to update plugin {} on server {}: {}", pluginName, serverId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Plugin update failed: " + e.getMessage()));
        }
    }

    /**
     * Gets all plugins with available updates across all servers.
     * 
     * @return List of plugins that can be updated
     */
    @GetMapping("/plugins/updates")
    public ResponseEntity<?> getPluginsWithUpdates() {
        logger.info("GET /api/v1/plugins/updates");
        
        try {
            List<ServerPlugin> pluginsWithUpdates = pluginService.getPluginsWithUpdates();
            
            logger.info("Found {} plugins with available updates", pluginsWithUpdates.size());
            return ResponseEntity.ok(pluginsWithUpdates);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve plugins with updates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve plugin updates: " + e.getMessage()));
        }
    }

    /**
     * Gets popular plugins from the marketplace.
     * 
     * @param limit Maximum number of results to return
     * @return List of popular plugins with installation counts
     */
    @GetMapping("/plugins/popular")
    public ResponseEntity<?> getPopularPlugins(@RequestParam(defaultValue = "20") int limit) {
        logger.info("GET /api/v1/plugins/popular?limit={}", limit);
        
        try {
            List<Object[]> popularPlugins = pluginService.getPopularPlugins(limit);
            
            // Convert to more readable format
            List<Map<String, Object>> result = popularPlugins.stream()
                    .map(row -> Map.of(
                            "pluginName", row[0],
                            "installCount", row[1]
                    ))
                    .toList();
            
            logger.info("Retrieved {} popular plugins", result.size());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve popular plugins", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve popular plugins: " + e.getMessage()));
        }
    }

    /**
     * Gets plugins from the marketplace with search functionality.
     * 
     * @param query Search query (optional)
     * @param limit Maximum number of results
     * @return List of available plugins in the marketplace
     */
    @GetMapping("/plugins/marketplace")
    public ResponseEntity<?> getMarketplacePlugins(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int limit) {
        logger.info("GET /api/v1/plugins/marketplace?query={}&limit={}", query, limit);
        
        try {
            if (query != null && !query.trim().isEmpty()) {
                // Search plugins
                var searchResults = marketplaceService.searchPlugins(query, limit).get();
                return ResponseEntity.ok(searchResults);
            } else {
                // Get popular plugins
                var popularPlugins = marketplaceService.getPopularPlugins();
                return ResponseEntity.ok(popularPlugins);
            }
            
        } catch (Exception e) {
            logger.error("Failed to retrieve marketplace plugins", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve marketplace plugins: " + e.getMessage()));
        }
    }

    /**
     * Request DTO for plugin installation.
     */
    public static class InstallPluginRequest {
        private String pluginName;
        private String downloadUrl;
        private String installedBy;
        private String version;

        // Getters and setters
        public String getPluginName() { return pluginName; }
        public void setPluginName(String pluginName) { this.pluginName = pluginName; }

        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

        public String getInstalledBy() { return installedBy; }
        public void setInstalledBy(String installedBy) { this.installedBy = installedBy; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
}