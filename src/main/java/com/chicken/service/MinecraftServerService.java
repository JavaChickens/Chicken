/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Core service class for Minecraft server management operations.
 * This service provides comprehensive server lifecycle management including
 * creation, configuration, startup/shutdown, and monitoring capabilities.
 */
package com.chicken.service;

import com.chicken.config.ChickenConfiguration;
import com.chicken.model.MinecraftServer;
import com.chicken.repository.MinecraftServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service class for comprehensive Minecraft server management.
 * 
 * This service provides:
 * - Server lifecycle management (create, start, stop, delete)
 * - Configuration management and validation
 * - Process monitoring and health checks
 * - Resource allocation and port management
 * - File system operations and server setup
 * - Integration with Paper/Bukkit server implementations
 * 
 * All operations are designed for production use with proper error handling,
 * logging, and transaction management for data consistency.
 */
@Service
@Transactional
public class MinecraftServerService {

    private static final Logger logger = LoggerFactory.getLogger(MinecraftServerService.class);

    /**
     * Map to track running server processes by server ID.
     * Used for process management and monitoring.
     */
    private final ConcurrentHashMap<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    @Autowired
    private MinecraftServerRepository serverRepository;

    @Autowired
    private ChickenConfiguration config;

    @Autowired
    private FileManagerService fileManagerService;

    @Autowired
    private ServerJarService serverJarService;

    /**
     * Creates a new Minecraft server with the specified configuration.
     * 
     * This method:
     * - Validates server parameters and resource availability
     * - Allocates a unique port for the server
     * - Creates the server directory structure
     * - Downloads the appropriate server JAR file
     * - Generates initial configuration files
     * - Persists the server entity to the database
     * 
     * @param name Unique server name
     * @param serverType Type of Minecraft server (Paper, Spigot, etc.)
     * @param minecraftVersion Minecraft version to use
     * @param memoryMb Memory allocation in megabytes
     * @param owner Username of the server owner
     * @return Created MinecraftServer entity
     * @throws IllegalArgumentException if parameters are invalid
     * @throws IllegalStateException if server creation fails
     */
    public MinecraftServer createServer(String name, MinecraftServer.ServerType serverType,
                                       String minecraftVersion, Integer memoryMb, String owner) {
        logger.info("Creating new Minecraft server: {} (Type: {}, Version: {}, Memory: {}MB, Owner: {})",
                name, serverType, minecraftVersion, memoryMb, owner);

        // Validate input parameters
        validateServerCreationParameters(name, serverType, minecraftVersion, memoryMb, owner);

        // Check server count limits
        long userServerCount = serverRepository.countByOwner(owner);
        if (userServerCount >= config.getServer().getMaxServers()) {
            throw new IllegalStateException("Maximum server limit reached for user: " + owner);
        }

        // Allocate unique port
        Integer port = allocatePort();
        if (port == null) {
            throw new IllegalStateException("No available ports for new server");
        }

        try {
            // Create server entity
            MinecraftServer server = new MinecraftServer(name, serverType, minecraftVersion, memoryMb, port, owner);
            server.setStatus(MinecraftServer.ServerStatus.STOPPED);

            // Save to database first to get ID
            server = serverRepository.save(server);
            logger.info("Server entity created with ID: {}", server.getId());

            // Create server directory structure
            createServerDirectories(server);

            // Download and setup server JAR
            setupServerJar(server);

            // Generate initial configuration files
            generateServerConfiguration(server);

            // Accept EULA automatically for convenience
            acceptEula(server);

            logger.info("Successfully created Minecraft server: {} with ID: {}", name, server.getId());
            return server;

        } catch (Exception e) {
            logger.error("Failed to create server: {}", name, e);
            // Cleanup on failure
            try {
                cleanupFailedServerCreation(name);
            } catch (Exception cleanupException) {
                logger.warn("Failed to cleanup after server creation failure", cleanupException);
            }
            throw new IllegalStateException("Server creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Starts a Minecraft server asynchronously.
     * 
     * This method:
     * - Validates server can be started
     * - Updates server status to STARTING
     * - Launches the Minecraft server process
     * - Monitors startup progress
     * - Updates status to RUNNING on success
     * 
     * @param serverId Database ID of the server to start
     * @return CompletableFuture that completes when startup finishes
     * @throws IllegalArgumentException if server ID is invalid
     * @throws IllegalStateException if server cannot be started
     */
    @Async("serverTaskExecutor")
    public CompletableFuture<Void> startServerAsync(Long serverId) {
        return CompletableFuture.runAsync(() -> {
            try {
                startServer(serverId);
            } catch (Exception e) {
                logger.error("Async server start failed for server ID: {}", serverId, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Starts a Minecraft server synchronously.
     * 
     * @param serverId Database ID of the server to start
     * @throws IllegalArgumentException if server ID is invalid
     * @throws IllegalStateException if server cannot be started
     */
    public void startServer(Long serverId) {
        logger.info("Starting Minecraft server with ID: {}", serverId);

        MinecraftServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        if (!server.canStart()) {
            throw new IllegalStateException("Server cannot be started. Current status: " + server.getStatus());
        }

        try {
            // Update status to starting
            server.setStatus(MinecraftServer.ServerStatus.STARTING);
            server.setLastStartedAt(LocalDateTime.now());
            serverRepository.save(server);

            // Build and execute server command
            Process process = launchServerProcess(server);
            runningProcesses.put(serverId, process);

            // Monitor startup
            boolean startupSuccessful = monitorServerStartup(server, process);

            if (startupSuccessful) {
                server.setStatus(MinecraftServer.ServerStatus.RUNNING);
                server.setProcessId((long) process.pid());
                logger.info("Successfully started server: {} (PID: {})", server.getName(), process.pid());
            } else {
                server.setStatus(MinecraftServer.ServerStatus.ERROR);
                runningProcesses.remove(serverId);
                logger.error("Server startup failed or timed out: {}", server.getName());
                throw new IllegalStateException("Server startup failed or timed out");
            }

            serverRepository.save(server);

        } catch (Exception e) {
            logger.error("Failed to start server: {}", server.getName(), e);
            server.setStatus(MinecraftServer.ServerStatus.ERROR);
            serverRepository.save(server);
            runningProcesses.remove(serverId);
            throw new IllegalStateException("Server start failed: " + e.getMessage(), e);
        }
    }

    /**
     * Stops a Minecraft server gracefully.
     * 
     * @param serverId Database ID of the server to stop
     * @throws IllegalArgumentException if server ID is invalid
     * @throws IllegalStateException if server cannot be stopped
     */
    public void stopServer(Long serverId) {
        logger.info("Stopping Minecraft server with ID: {}", serverId);

        MinecraftServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        if (!server.canStop()) {
            throw new IllegalStateException("Server cannot be stopped. Current status: " + server.getStatus());
        }

        try {
            server.setStatus(MinecraftServer.ServerStatus.STOPPING);
            server.setLastStoppedAt(LocalDateTime.now());
            serverRepository.save(server);

            Process process = runningProcesses.get(serverId);
            if (process != null && process.isAlive()) {
                // Send stop command to server
                sendStopCommand(process);

                // Wait for graceful shutdown
                boolean stopped = process.waitFor(config.getServer().getShutdownTimeout(), TimeUnit.SECONDS);
                
                if (!stopped) {
                    logger.warn("Server did not stop gracefully, forcing termination: {}", server.getName());
                    process.destroyForcibly();
                }
            }

            server.setStatus(MinecraftServer.ServerStatus.STOPPED);
            server.setProcessId(null);
            runningProcesses.remove(serverId);
            serverRepository.save(server);

            logger.info("Successfully stopped server: {}", server.getName());

        } catch (Exception e) {
            logger.error("Failed to stop server: {}", server.getName(), e);
            server.setStatus(MinecraftServer.ServerStatus.ERROR);
            serverRepository.save(server);
            throw new IllegalStateException("Server stop failed: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a Minecraft server and all associated data.
     * 
     * @param serverId Database ID of the server to delete
     * @throws IllegalArgumentException if server ID is invalid
     * @throws IllegalStateException if server is running
     */
    public void deleteServer(Long serverId) {
        logger.info("Deleting Minecraft server with ID: {}", serverId);

        MinecraftServer server = serverRepository.findById(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + serverId));

        if (server.isRunning()) {
            throw new IllegalStateException("Cannot delete running server. Stop the server first.");
        }

        try {
            // Remove server files
            Path serverDir = getServerDirectory(server);
            if (Files.exists(serverDir)) {
                fileManagerService.deleteDirectory(serverDir);
                logger.info("Deleted server directory: {}", serverDir);
            }

            // Remove from database
            serverRepository.delete(server);
            logger.info("Successfully deleted server: {}", server.getName());

        } catch (Exception e) {
            logger.error("Failed to delete server: {}", server.getName(), e);
            throw new IllegalStateException("Server deletion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets all servers owned by a specific user.
     * 
     * @param owner Username of the server owner
     * @return List of servers owned by the user
     */
    @Transactional(readOnly = true)
    public List<MinecraftServer> getServersByOwner(String owner) {
        return serverRepository.findByOwner(owner);
    }

    /**
     * Gets all servers with a specific status.
     * 
     * @param status Server status to filter by
     * @return List of servers with the specified status
     */
    @Transactional(readOnly = true)
    public List<MinecraftServer> getServersByStatus(MinecraftServer.ServerStatus status) {
        return serverRepository.findByStatus(status);
    }

    /**
     * Gets a server by its unique name.
     * 
     * @param name Server name
     * @return Optional containing the server if found
     */
    @Transactional(readOnly = true)
    public Optional<MinecraftServer> getServerByName(String name) {
        return serverRepository.findByName(name);
    }

    /**
     * Gets all servers in the system.
     * 
     * @return List of all servers
     */
    @Transactional(readOnly = true)
    public List<MinecraftServer> getAllServers() {
        return serverRepository.findAll();
    }

    /**
     * Checks if a server name is available.
     * 
     * @param name Server name to check
     * @return true if the name is available
     */
    @Transactional(readOnly = true)
    public boolean isServerNameAvailable(String name) {
        return serverRepository.isNameAvailable(name);
    }

    // Private helper methods

    private void validateServerCreationParameters(String name, MinecraftServer.ServerType serverType,
                                                 String minecraftVersion, Integer memoryMb, String owner) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Server name cannot be empty");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("Server name too long (max 50 characters)");
        }
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Server name can only contain letters, numbers, underscores, and hyphens");
        }
        if (!serverRepository.isNameAvailable(name)) {
            throw new IllegalArgumentException("Server name already exists: " + name);
        }
        if (serverType == null) {
            throw new IllegalArgumentException("Server type cannot be null");
        }
        if (minecraftVersion == null || minecraftVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("Minecraft version cannot be empty");
        }
        if (memoryMb == null || memoryMb < 512) {
            throw new IllegalArgumentException("Memory allocation must be at least 512MB");
        }
        if (memoryMb > 8192) {
            throw new IllegalArgumentException("Memory allocation cannot exceed 8192MB");
        }
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Server owner cannot be empty");
        }
    }

    private Integer allocatePort() {
        Integer startPort = 25565;
        Integer endPort = 25665;
        
        for (int port = startPort; port <= endPort; port++) {
            if (serverRepository.isPortAvailable(port)) {
                return port;
            }
        }
        return null;
    }

    private void createServerDirectories(MinecraftServer server) throws IOException {
        Path serverDir = getServerDirectory(server);
        Files.createDirectories(serverDir);
        Files.createDirectories(serverDir.resolve("plugins"));
        Files.createDirectories(serverDir.resolve("world"));
        Files.createDirectories(serverDir.resolve("logs"));
        logger.info("Created directory structure for server: {}", server.getName());
    }

    private void setupServerJar(MinecraftServer server) throws IOException {
        Path serverDir = getServerDirectory(server);
        Path jarFile = serverDir.resolve("server.jar");
        
        serverJarService.downloadServerJar(server.getServerType(), server.getMinecraftVersion(), jarFile);
        logger.info("Downloaded server JAR for: {}", server.getName());
    }

    private void generateServerConfiguration(MinecraftServer server) throws IOException {
        Path serverDir = getServerDirectory(server);
        Path propsFile = serverDir.resolve("server.properties");
        
        StringBuilder props = new StringBuilder();
        props.append("server-port=").append(server.getPort()).append("\n");
        props.append("max-players=").append(server.getMaxPlayers()).append("\n");
        props.append("motd=").append(server.getMotd()).append("\n");
        props.append("difficulty=").append(server.getDifficulty()).append("\n");
        props.append("gamemode=").append(server.getGameMode()).append("\n");
        props.append("hardcore=").append(server.getHardcore()).append("\n");
        props.append("pvp=").append(server.getPvp()).append("\n");
        props.append("online-mode=").append(server.getOnlineMode()).append("\n");
        props.append("enable-rcon=false\n");
        props.append("spawn-protection=16\n");
        props.append("view-distance=10\n");
        
        Files.write(propsFile, props.toString().getBytes());
        logger.info("Generated server.properties for: {}", server.getName());
    }

    private void acceptEula(MinecraftServer server) throws IOException {
        Path serverDir = getServerDirectory(server);
        Path eulaFile = serverDir.resolve("eula.txt");
        
        String eulaContent = "# EULA accepted automatically by Chicken Server Host\n" +
                           "# " + LocalDateTime.now() + "\n" +
                           "eula=true\n";
        
        Files.write(eulaFile, eulaContent.getBytes());
        logger.info("Accepted EULA for server: {}", server.getName());
    }

    private Process launchServerProcess(MinecraftServer server) throws IOException {
        Path serverDir = getServerDirectory(server);
        Path jarFile = serverDir.resolve("server.jar");
        
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-Xmx" + server.getMemoryMb() + "M",
                "-Xms" + Math.min(server.getMemoryMb(), 1024) + "M",
                "-jar", jarFile.toString(),
                "--nogui"
        );
        
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        
        return pb.start();
    }

    private boolean monitorServerStartup(MinecraftServer server, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            long startTime = System.currentTimeMillis();
            long timeout = config.getServer().getStartupTimeout() * 1000L;
            
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Server {}: {}", server.getName(), line);
                
                if (line.contains("Done") && line.contains("For help, type \"help\"")) {
                    return true;
                }
                
                if (System.currentTimeMillis() - startTime > timeout) {
                    logger.warn("Server startup timeout for: {}", server.getName());
                    return false;
                }
                
                if (!process.isAlive()) {
                    logger.error("Server process died during startup: {}", server.getName());
                    return false;
                }
            }
        } catch (IOException e) {
            logger.error("Error monitoring server startup: {}", server.getName(), e);
        }
        
        return false;
    }

    private void sendStopCommand(Process process) {
        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println("stop");
            writer.flush();
        } catch (Exception e) {
            logger.warn("Failed to send stop command to server process", e);
        }
    }

    private Path getServerDirectory(MinecraftServer server) {
        return config.getServer().getDataDirectoryPath().resolve(server.getDirectoryName());
    }

    private void cleanupFailedServerCreation(String serverName) {
        try {
            Optional<MinecraftServer> server = serverRepository.findByName(serverName);
            if (server.isPresent()) {
                Path serverDir = getServerDirectory(server.get());
                if (Files.exists(serverDir)) {
                    fileManagerService.deleteDirectory(serverDir);
                }
                serverRepository.delete(server.get());
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup server creation: {}", serverName, e);
        }
    }
}