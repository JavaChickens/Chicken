/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Command-line interface runner for the Chicken Minecraft Server Hosting Platform.
 * Provides comprehensive CLI commands for server management, plugin operations,
 * and system administration through PicoCLI framework integration.
 */
package com.chicken.cli;

import com.chicken.model.MinecraftServer;
import com.chicken.service.MinecraftServerService;
import com.chicken.service.PluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Main CLI runner and command dispatcher for Chicken platform.
 * 
 * This class provides a comprehensive command-line interface for:
 * - Server lifecycle management (create, start, stop, delete)
 * - Plugin installation and management
 * - System monitoring and status reporting
 * - Configuration management
 * - Bulk operations and automation support
 * 
 * Built on PicoCLI framework for professional command-line experience
 * with proper help documentation, parameter validation, and error handling.
 */
@Component
@Command(
    name = "chicken",
    description = "Chicken Minecraft Server Hosting Platform CLI",
    version = "1.0.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        ChickenCliRunner.ServerCommand.class,
        ChickenCliRunner.PluginCommand.class,
        ChickenCliRunner.StatusCommand.class,
        ChickenCliRunner.StatsCommand.class,
        ChickenCliRunner.PlayerCommand.class
    }
)
public class ChickenCliRunner implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ChickenCliRunner.class);

    @Autowired
    private MinecraftServerService serverService;

    @Autowired
    private PluginService pluginService;

    @Autowired
    private PlayerManagementService playerManagementService;

    @Autowired
    private PluginMarketplaceService marketplaceService;

    /**
     * Executes CLI commands with proper error handling and logging.
     * 
     * @param args Command line arguments
     * @return Exit code (0 for success, non-zero for errors)
     */
    public int run(String[] args) {
        try {
            CommandLine cmd = new CommandLine(this);
            cmd.setExecutionStrategy(new CommandLine.RunLast());
            return cmd.execute(args);
        } catch (Exception e) {
            logger.error("CLI execution failed", e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    @Override
    public Integer call() {
        System.out.println("Chicken Minecraft Server Hosting Platform CLI v1.0.0");
        System.out.println("Use --help for available commands");
        return 0;
    }

    /**
     * Server management commands.
     * Handles all server lifecycle operations including creation, control, and monitoring.
     */
    @Command(
        name = "server",
        description = "Minecraft server management commands",
        subcommands = {
            ServerCommand.CreateCommand.class,
            ServerCommand.StartCommand.class,
            ServerCommand.StopCommand.class,
            ServerCommand.DeleteCommand.class,
            ServerCommand.ListCommand.class,
            ServerCommand.InfoCommand.class
        }
    )
    static class ServerCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Server management commands. Use 'server --help' for available subcommands.");
            return 0;
        }

        /**
         * Creates a new Minecraft server with specified configuration.
         */
        @Command(name = "create", description = "Create a new Minecraft server")
        static class CreateCommand implements Callable<Integer> {

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-n", "--name"}, required = true, description = "Server name")
            private String name;

            @Option(names = {"-t", "--type"}, description = "Server type (PAPER, SPIGOT, BUKKIT, VANILLA)", defaultValue = "PAPER")
            private MinecraftServer.ServerType serverType;

            @Option(names = {"-v", "--version"}, description = "Minecraft version", defaultValue = "1.20.1")
            private String version;

            @Option(names = {"-m", "--memory"}, description = "Memory allocation in MB", defaultValue = "2048")
            private Integer memory;

            @Option(names = {"-o", "--owner"}, description = "Server owner", defaultValue = "admin")
            private String owner;

            @Option(names = {"-u", "--username"}, description = "Your username (will be made admin)")
            private String username;

            @Override
            public Integer call() {
                try {
                    System.out.printf("Creating server '%s' with type %s, version %s, memory %dMB...%n", 
                                    name, serverType, version, memory);
                    
                    MinecraftServer server = serverService.createServer(name, serverType, version, memory, owner);
                    
                    // Make username admin if provided
                    if (username != null && !username.trim().isEmpty()) {
                        try {
                            playerManagementService.setPlayerPermission(server.getId(), username, "admin");
                            System.out.printf("✅ Made %s admin on the new server%n", username);
                        } catch (Exception e) {
                            System.out.printf("⚠️ Warning: Failed to make %s admin: %s%n", username, e.getMessage());
                        }
                    }
                    
                    System.out.printf("✅ Successfully created server '%s' (ID: %d, Port: %d)%n", 
                                    server.getName(), server.getId(), server.getPort());
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to create server: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Starts a Minecraft server.
         */
        @Command(name = "start", description = "Start a Minecraft server")
        static class StartCommand implements Callable<Integer> {

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-n", "--name"}, required = true, description = "Server name")
            private String name;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(name);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", name);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    System.out.printf("Starting server '%s'...%n", name);
                    
                    serverService.startServer(server.getId());
                    
                    System.out.printf("✅ Successfully started server '%s' on port %d%n", 
                                    server.getName(), server.getPort());
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to start server: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Stops a running Minecraft server.
         */
        @Command(name = "stop", description = "Stop a Minecraft server")
        static class StopCommand implements Callable<Integer> {

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-n", "--name"}, required = true, description = "Server name")
            private String name;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(name);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", name);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    System.out.printf("Stopping server '%s'...%n", name);
                    
                    serverService.stopServer(server.getId());
                    
                    System.out.printf("✅ Successfully stopped server '%s'%n", server.getName());
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to stop server: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Deletes a Minecraft server and all its data.
         */
        @Command(name = "delete", description = "Delete a Minecraft server")
        static class DeleteCommand implements Callable<Integer> {

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-n", "--name"}, required = true, description = "Server name")
            private String name;

            @Option(names = {"-f", "--force"}, description = "Force deletion without confirmation")
            private boolean force;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(name);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", name);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    
                    if (!force) {
                        System.out.printf("⚠️  This will permanently delete server '%s' and all its data.%n", name);
                        System.out.print("Are you sure? (y/N): ");
                        
                        String confirmation = System.console() != null ? 
                            System.console().readLine() : "n";
                        
                        if (!"y".equalsIgnoreCase(confirmation.trim())) {
                            System.out.println("Deletion cancelled.");
                            return 0;
                        }
                    }

                    System.out.printf("Deleting server '%s'...%n", name);
                    serverService.deleteServer(server.getId());
                    
                    System.out.printf("✅ Successfully deleted server '%s'%n", name);
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to delete server: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Lists all Minecraft servers.
         */
        @Command(name = "list", description = "List all Minecraft servers")
        static class ListCommand implements Callable<Integer> {

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-o", "--owner"}, description = "Filter by owner")
            private String owner;

            @Option(names = {"-s", "--status"}, description = "Filter by status")
            private MinecraftServer.ServerStatus status;

            @Override
            public Integer call() {
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

                    if (servers.isEmpty()) {
                        System.out.println("No servers found.");
                        return 0;
                    }

                    System.out.printf("%-20s %-10s %-15s %-8s %-6s %-10s%n", 
                                    "NAME", "STATUS", "TYPE", "VERSION", "PORT", "OWNER");
                    System.out.println("─".repeat(80));

                    for (MinecraftServer server : servers) {
                        System.out.printf("%-20s %-10s %-15s %-8s %-6d %-10s%n",
                                        server.getName(),
                                        server.getStatus(),
                                        server.getServerType().getDisplayName(),
                                        server.getMinecraftVersion(),
                                        server.getPort(),
                                        server.getOwner());
                    }

                    System.out.printf("%nTotal: %d servers%n", servers.size());
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to list servers: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Shows detailed information about a specific server.
         */
        @Command(name = "info", description = "Show detailed server information")
        static class InfoCommand implements Callable<Integer> {

            @Autowired
            private MinecraftServerService serverService;

            @Parameters(index = "0", description = "Server name")
            private String name;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(name);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", name);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    
                    System.out.printf("Server Information: %s%n", server.getName());
                    System.out.println("═".repeat(50));
                    System.out.printf("ID:              %d%n", server.getId());
                    System.out.printf("Display Name:    %s%n", server.getDisplayName());
                    System.out.printf("Status:          %s%n", server.getStatus());
                    System.out.printf("Type:            %s%n", server.getServerType().getDisplayName());
                    System.out.printf("Version:         %s%n", server.getMinecraftVersion());
                    System.out.printf("Memory:          %d MB%n", server.getMemoryMb());
                    System.out.printf("Port:            %d%n", server.getPort());
                    System.out.printf("Max Players:     %d%n", server.getMaxPlayers());
                    System.out.printf("Owner:           %s%n", server.getOwner());
                    System.out.printf("Created:         %s%n", server.getCreatedAt());
                    
                    if (server.getLastStartedAt() != null) {
                        System.out.printf("Last Started:    %s%n", server.getLastStartedAt());
                    }
                    
                    if (server.getProcessId() != null) {
                        System.out.printf("Process ID:      %d%n", server.getProcessId());
                    }
                    
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to get server info: %s%n", e.getMessage());
                    return 1;
                }
            }
        }
    }

    /**
     * Plugin management commands.
     * Handles plugin installation, removal, and configuration operations.
     */
    @Command(
        name = "plugin",
        description = "Plugin management commands",
        subcommands = {
            PluginCommand.InstallCommand.class,
            PluginCommand.RemoveCommand.class,
            PluginCommand.ListCommand.class
        }
    )
    static class PluginCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Plugin management commands. Use 'plugin --help' for available subcommands.");
            return 0;
        }

        /**
         * Installs a plugin on a server.
         */
        @Command(name = "install", description = "Install a plugin on a server")
        static class InstallCommand implements Callable<Integer> {

            @Autowired
            private PluginService pluginService;

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-s", "--server"}, required = true, description = "Server name")
            private String serverName;

            @Option(names = {"-p", "--plugin"}, required = true, description = "Plugin name")
            private String pluginName;

            @Option(names = {"-u", "--url"}, description = "Plugin download URL")
            private String downloadUrl;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    System.out.printf("Installing plugin '%s' on server '%s'...%n", pluginName, serverName);

                    if (downloadUrl != null) {
                        pluginService.installPlugin(server, pluginName, downloadUrl, "cli-user");
                    } else {
                        pluginService.installPopularPluginAsync(server, pluginName, "cli-user").get();
                    }

                    System.out.printf("✅ Successfully installed plugin '%s' on server '%s'%n", 
                                    pluginName, serverName);
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to install plugin: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Removes a plugin from a server.
         */
        @Command(name = "remove", description = "Remove a plugin from a server")
        static class RemoveCommand implements Callable<Integer> {

            @Autowired
            private PluginService pluginService;

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-s", "--server"}, required = true, description = "Server name")
            private String serverName;

            @Option(names = {"-p", "--plugin"}, required = true, description = "Plugin name")
            private String pluginName;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    System.out.printf("Removing plugin '%s' from server '%s'...%n", pluginName, serverName);

                    pluginService.removePlugin(server.getId(), pluginName);

                    System.out.printf("✅ Successfully removed plugin '%s' from server '%s'%n", 
                                    pluginName, serverName);
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to remove plugin: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Lists plugins installed on a server.
         */
        @Command(name = "list", description = "List plugins on a server")
        static class ListCommand implements Callable<Integer> {

            @Autowired
            private PluginService pluginService;

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-s", "--server"}, required = true, description = "Server name")
            private String serverName;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    MinecraftServer server = serverOpt.get();
                    var plugins = pluginService.getServerPlugins(server.getId());

                    if (plugins.isEmpty()) {
                        System.out.printf("No plugins installed on server '%s'.%n", serverName);
                        return 0;
                    }

                    System.out.printf("Plugins on server '%s':%n", serverName);
                    System.out.printf("%-25s %-10s %-10s %-15s%n", "NAME", "VERSION", "STATUS", "ENABLED");
                    System.out.println("─".repeat(70));

                    for (var plugin : plugins) {
                        System.out.printf("%-25s %-10s %-10s %-15s%n",
                                        plugin.getPluginName(),
                                        plugin.getVersion(),
                                        plugin.getStatus(),
                                        plugin.getEnabled() ? "Yes" : "No");
                    }

                    System.out.printf("%nTotal: %d plugins%n", plugins.size());
                    return 0;
                    
                } catch (Exception e) {
                    System.err.printf("❌ Failed to list plugins: %s%n", e.getMessage());
                    return 1;
                }
            }
        }
    }

    /**
     * System status and monitoring commands.
     */
    @Command(name = "status", description = "Show system status")
    static class StatusCommand implements Callable<Integer> {

        @Autowired
        private MinecraftServerService serverService;

        @Override
        public Integer call() {
            try {
                var allServers = serverService.getAllServers();
                var runningServers = serverService.getServersByStatus(MinecraftServer.ServerStatus.RUNNING);
                var stoppedServers = serverService.getServersByStatus(MinecraftServer.ServerStatus.STOPPED);

                System.out.println("Chicken Server Host - System Status");
                System.out.println("═".repeat(40));
                System.out.printf("Total Servers:    %d%n", allServers.size());
                System.out.printf("Running Servers:  %d%n", runningServers.size());
                System.out.printf("Stopped Servers:  %d%n", stoppedServers.size());

                if (!runningServers.isEmpty()) {
                    System.out.println("\nRunning Servers:");
                    for (var server : runningServers) {
                        System.out.printf("  • %s (Port: %d)%n", server.getName(), server.getPort());
                    }
                }

                return 0;
                
            } catch (Exception e) {
                System.err.printf("❌ Failed to get status: %s%n", e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Server statistics and player information commands.
     */
    @Command(name = "stats", description = "Show server statistics and player information")
    static class StatsCommand implements Callable<Integer> {

        @Autowired
        private PlayerManagementService playerManagementService;

        @Autowired
        private MinecraftServerService serverService;

        @Option(names = {"-s", "--server"}, description = "Server name")
        private String serverName;

        @Override
        public Integer call() {
            try {
                if (serverName != null) {
                    // Show stats for specific server
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    var server = serverOpt.get();
                    var stats = playerManagementService.getServerStats(server.getId());

                    System.out.printf("Server Statistics: %s%n", stats.getServerName());
                    System.out.println("═".repeat(50));
                    System.out.printf("Status:           %s%n", server.getStatus());
                    System.out.printf("Online Players:   %d%n", stats.getOnlineCount());
                    System.out.printf("Server Port:      %d%n", server.getPort());
                    System.out.printf("Memory:           %d MB%n", server.getMemoryMb());
                    System.out.printf("Version:          %s %s%n", server.getServerType(), server.getMinecraftVersion());

                    if (!stats.getPlayers().isEmpty()) {
                        System.out.println("\nOnline Players:");
                        System.out.printf("%-20s %-10s %-15s%n", "NAME", "ROLE", "JOIN TIME");
                        System.out.println("─".repeat(50));
                        for (var player : stats.getPlayers()) {
                            System.out.printf("%-20s %-10s %-15s%n",
                                    player.getName(),
                                    player.getPermission(),
                                    new java.util.Date(player.getJoinTime()).toString().substring(11, 19));
                        }
                    }
                } else {
                    // Show stats for all servers
                    var allServers = serverService.getAllServers();
                    var runningServers = serverService.getServersByStatus(MinecraftServer.ServerStatus.RUNNING);

                    System.out.println("All Server Statistics");
                    System.out.println("═".repeat(60));
                    System.out.printf("%-20s %-10s %-8s %-10s %-8s%n", "SERVER", "STATUS", "PLAYERS", "VERSION", "PORT");
                    System.out.println("─".repeat(60));

                    for (var server : allServers) {
                        int playerCount = 0;
                        if (server.isRunning()) {
                            try {
                                var stats = playerManagementService.getServerStats(server.getId());
                                playerCount = stats.getOnlineCount();
                            } catch (Exception e) {
                                // Ignore errors for individual servers
                            }
                        }

                        System.out.printf("%-20s %-10s %-8d %-10s %-8d%n",
                                server.getName(),
                                server.getStatus(),
                                playerCount,
                                server.getMinecraftVersion(),
                                server.getPort());
                    }

                    int totalPlayers = runningServers.stream()
                            .mapToInt(server -> {
                                try {
                                    return playerManagementService.getServerStats(server.getId()).getOnlineCount();
                                } catch (Exception e) {
                                    return 0;
                                }
                            })
                            .sum();

                    System.out.printf("\nTotal Online Players: %d%n", totalPlayers);
                }

                return 0;

            } catch (Exception e) {
                System.err.printf("❌ Failed to get stats: %s%n", e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Player management commands.
     */
    @Command(
        name = "player",
        description = "Player management commands",
        subcommands = {
            PlayerCommand.MakeAdminCommand.class,
            PlayerCommand.MakeVipCommand.class,
            PlayerCommand.RemovePermissionsCommand.class
        }
    )
    static class PlayerCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Player management commands. Use 'player --help' for available subcommands.");
            return 0;
        }

        /**
         * Make a player admin.
         */
        @Command(name = "admin", description = "Make a player admin")
        static class MakeAdminCommand implements Callable<Integer> {

            @Autowired
            private PlayerManagementService playerManagementService;

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-s", "--server"}, required = true, description = "Server name")
            private String serverName;

            @Option(names = {"-p", "--player"}, required = true, description = "Player name")
            private String playerName;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    var server = serverOpt.get();
                    playerManagementService.setPlayerPermission(server.getId(), playerName, "admin");

                    System.out.printf("✅ Successfully made %s admin on server %s%n", playerName, serverName);
                    return 0;

                } catch (Exception e) {
                    System.err.printf("❌ Failed to make player admin: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Make a player VIP.
         */
        @Command(name = "vip", description = "Make a player VIP")
        static class MakeVipCommand implements Callable<Integer> {

            @Autowired
            private PlayerManagementService playerManagementService;

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-s", "--server"}, required = true, description = "Server name")
            private String serverName;

            @Option(names = {"-p", "--player"}, required = true, description = "Player name")
            private String playerName;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    var server = serverOpt.get();
                    playerManagementService.setPlayerPermission(server.getId(), playerName, "vip");

                    System.out.printf("✅ Successfully made %s VIP on server %s%n", playerName, serverName);
                    return 0;

                } catch (Exception e) {
                    System.err.printf("❌ Failed to make player VIP: %s%n", e.getMessage());
                    return 1;
                }
            }
        }

        /**
         * Remove player permissions.
         */
        @Command(name = "remove", description = "Remove player permissions")
        static class RemovePermissionsCommand implements Callable<Integer> {

            @Autowired
            private PlayerManagementService playerManagementService;

            @Autowired
            private MinecraftServerService serverService;

            @Option(names = {"-s", "--server"}, required = true, description = "Server name")
            private String serverName;

            @Option(names = {"-p", "--player"}, required = true, description = "Player name")
            private String playerName;

            @Override
            public Integer call() {
                try {
                    var serverOpt = serverService.getServerByName(serverName);
                    if (serverOpt.isEmpty()) {
                        System.err.printf("❌ Server not found: %s%n", serverName);
                        return 1;
                    }

                    var server = serverOpt.get();
                    playerManagementService.setPlayerPermission(server.getId(), playerName, "player");

                    System.out.printf("✅ Successfully removed permissions for %s on server %s%n", playerName, serverName);
                    return 0;

                } catch (Exception e) {
                    System.err.printf("❌ Failed to remove player permissions: %s%n", e.getMessage());
                    return 1;
                }
            }
        }
    }
}