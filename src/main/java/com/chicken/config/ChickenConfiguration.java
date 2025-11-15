/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Core configuration class for the Chicken Minecraft Server Hosting Platform.
 * Defines application-wide configuration properties, bean definitions, and
 * system initialization parameters for production deployment.
 */
package com.chicken.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

/**
 * Primary configuration class for the Chicken application.
 * 
 * This class defines:
 * - Application-wide configuration properties
 * - Bean definitions for core services
 * - Thread pool configuration for async operations
 * - CORS configuration for web API access
 * - File system paths and directory structure
 * 
 * All configuration values can be overridden via application.yml,
 * environment variables, or command line arguments following
 * Spring Boot configuration precedence.
 */
@Configuration
@ConfigurationProperties(prefix = "chicken")
public class ChickenConfiguration implements WebMvcConfigurer {

    /**
     * Server configuration properties for Minecraft server management.
     */
    private ServerConfig server = new ServerConfig();
    
    /**
     * Web interface configuration properties.
     */
    private WebConfig web = new WebConfig();
    
    /**
     * Plugin management configuration properties.
     */
    private PluginConfig plugin = new PluginConfig();

    /**
     * Configures CORS (Cross-Origin Resource Sharing) for the web API.
     * 
     * This configuration allows the web interface to communicate with the
     * REST API from different origins, supporting development and deployment
     * scenarios where the frontend and backend may be served from different
     * domains or ports.
     * 
     * @param registry CORS registry for configuration
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:8080")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Creates a thread pool executor for asynchronous server operations.
     * 
     * This executor handles:
     * - Minecraft server startup/shutdown operations
     * - Plugin installation and updates
     * - File system operations
     * - Background monitoring tasks
     * 
     * Configuration is optimized for typical server hosting workloads
     * with reasonable defaults that can be tuned for specific deployments.
     * 
     * @return Configured thread pool executor
     */
    @Bean(name = "serverTaskExecutor")
    public Executor serverTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ChickenServer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // Getters and setters for configuration properties

    public ServerConfig getServer() {
        return server;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }

    public WebConfig getWeb() {
        return web;
    }

    public void setWeb(WebConfig web) {
        this.web = web;
    }

    public PluginConfig getPlugin() {
        return plugin;
    }

    public void setPlugin(PluginConfig plugin) {
        this.plugin = plugin;
    }

    /**
     * Configuration properties for Minecraft server management.
     * 
     * These properties control server creation, resource allocation,
     * and operational parameters for hosted Minecraft servers.
     */
    public static class ServerConfig {
        
        /**
         * Base directory for all server data and configurations.
         * Each server gets its own subdirectory under this path.
         */
        private String dataDirectory = "./servers";
        
        /**
         * Maximum number of concurrent servers that can be hosted.
         * This limit prevents resource exhaustion and ensures system stability.
         */
        private int maxServers = 10;
        
        /**
         * Default memory allocation (in MB) for new Minecraft servers.
         * Can be overridden per-server based on requirements.
         */
        private int defaultMemory = 2048;
        
        /**
         * Default Minecraft server version for new server creation.
         * Should be a valid Paper/Bukkit version identifier.
         */
        private String defaultVersion = "1.20.1";
        
        /**
         * Timeout (in seconds) for server startup operations.
         * Prevents hanging during server initialization.
         */
        private int startupTimeout = 120;
        
        /**
         * Timeout (in seconds) for server shutdown operations.
         * Ensures graceful shutdown before forced termination.
         */
        private int shutdownTimeout = 60;

        // Getters and setters
        public String getDataDirectory() { return dataDirectory; }
        public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
        
        public int getMaxServers() { return maxServers; }
        public void setMaxServers(int maxServers) { this.maxServers = maxServers; }
        
        public int getDefaultMemory() { return defaultMemory; }
        public void setDefaultMemory(int defaultMemory) { this.defaultMemory = defaultMemory; }
        
        public String getDefaultVersion() { return defaultVersion; }
        public void setDefaultVersion(String defaultVersion) { this.defaultVersion = defaultVersion; }
        
        public int getStartupTimeout() { return startupTimeout; }
        public void setStartupTimeout(int startupTimeout) { this.startupTimeout = startupTimeout; }
        
        public int getShutdownTimeout() { return shutdownTimeout; }
        public void setShutdownTimeout(int shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }
        
        /**
         * Gets the resolved data directory path.
         * @return Path object for the server data directory
         */
        public Path getDataDirectoryPath() {
            return Paths.get(dataDirectory).toAbsolutePath();
        }
    }

    /**
     * Configuration properties for the web interface and API.
     * 
     * Controls web server behavior, authentication, and UI features.
     */
    public static class WebConfig {
        
        /**
         * Web server port. Can be overridden by server.port property.
         */
        private int port = 8080;
        
        /**
         * Default administrator username for initial setup.
         * Should be changed in production deployments.
         */
        private String adminUser = "admin";
        
        /**
         * Default administrator password for initial setup.
         * Should be changed in production deployments.
         */
        private String adminPassword = "admin";
        
        /**
         * Enable/disable user registration functionality.
         * Set to false for private/corporate deployments.
         */
        private boolean allowRegistration = true;
        
        /**
         * Session timeout in minutes for web interface users.
         */
        private int sessionTimeout = 60;

        // Getters and setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getAdminUser() { return adminUser; }
        public void setAdminUser(String adminUser) { this.adminUser = adminUser; }
        
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
        
        public boolean isAllowRegistration() { return allowRegistration; }
        public void setAllowRegistration(boolean allowRegistration) { this.allowRegistration = allowRegistration; }
        
        public int getSessionTimeout() { return sessionTimeout; }
        public void setSessionTimeout(int sessionTimeout) { this.sessionTimeout = sessionTimeout; }
    }

    /**
     * Configuration properties for plugin management system.
     * 
     * Controls plugin installation, updates, and marketplace integration.
     */
    public static class PluginConfig {
        
        /**
         * Directory for caching downloaded plugins.
         * Reduces bandwidth usage for commonly installed plugins.
         */
        private String cacheDirectory = "./plugins-cache";
        
        /**
         * Enable/disable automatic plugin updates.
         * When enabled, plugins are automatically updated to latest versions.
         */
        private boolean autoUpdate = false;
        
        /**
         * Timeout (in seconds) for plugin download operations.
         */
        private int downloadTimeout = 300;
        
        /**
         * Maximum plugin file size (in MB) that can be uploaded/downloaded.
         */
        private int maxPluginSize = 100;

        // Getters and setters
        public String getCacheDirectory() { return cacheDirectory; }
        public void setCacheDirectory(String cacheDirectory) { this.cacheDirectory = cacheDirectory; }
        
        public boolean isAutoUpdate() { return autoUpdate; }
        public void setAutoUpdate(boolean autoUpdate) { this.autoUpdate = autoUpdate; }
        
        public int getDownloadTimeout() { return downloadTimeout; }
        public void setDownloadTimeout(int downloadTimeout) { this.downloadTimeout = downloadTimeout; }
        
        public int getMaxPluginSize() { return maxPluginSize; }
        public void setMaxPluginSize(int maxPluginSize) { this.maxPluginSize = maxPluginSize; }
        
        /**
         * Gets the resolved plugin cache directory path.
         * @return Path object for the plugin cache directory
         */
        public Path getCacheDirectoryPath() {
            return Paths.get(cacheDirectory).toAbsolutePath();
        }
    }
}