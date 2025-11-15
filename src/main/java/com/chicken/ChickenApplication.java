/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Main application entry point for the Chicken Minecraft Server Hosting Platform.
 * This class bootstraps the Spring Boot application and provides both web server
 * and CLI interface capabilities.
 *
 * The application supports multiple operational modes:
 * - Web Server Mode: Provides REST API and web interface
 * - CLI Mode: Command-line interface for server administration
 * - Hybrid Mode: Both web and CLI capabilities (default)
 *
 * @author piratebomber
 * @version 1.0.0
 * @since 2024
 */
package com.chicken;

import com.chicken.cli.ChickenCliRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Chicken Minecraft Server Hosting Platform.
 * 
 * This enterprise-grade application provides comprehensive Minecraft server
 * hosting capabilities including:
 * - Multi-server management and orchestration
 * - Plugin installation and management (Bukkit/Paper)
 * - Web-based administration interface
 * - RESTful API for automation and integration
 * - Command-line interface for system administration
 * - Resource monitoring and management
 * - User authentication and authorization
 * 
 * The application is built on Spring Boot framework for production reliability,
 * scalability, and enterprise integration capabilities.
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
@EnableScheduling
public class ChickenApplication {

    /**
     * Application entry point supporting both web server and CLI modes.
     * 
     * Operational Modes:
     * - No arguments: Starts web server on default port (8080)
     * - CLI arguments: Executes CLI commands and exits
     * - --server flag: Forces web server mode even with other arguments
     * 
     * @param args Command line arguments for CLI mode or server configuration
     */
    public static void main(String[] args) {
        // Determine operational mode based on command line arguments
        if (args.length > 0 && !containsServerFlag(args)) {
            // CLI Mode: Execute command and exit
            runCliMode(args);
        } else {
            // Web Server Mode: Start Spring Boot application
            runWebServerMode(args);
        }
    }

    /**
     * Executes the application in CLI mode for command-line administration.
     * 
     * This mode provides full server management capabilities without starting
     * the web server, making it suitable for:
     * - Automated deployment scripts
     * - System administration tasks
     * - Batch operations
     * - Integration with external tools
     * 
     * @param args CLI command arguments
     */
    private static void runCliMode(String[] args) {
        System.out.println("Chicken CLI Mode - Minecraft Server Management");
        System.out.println("==============================================");
        
        try {
            // Initialize minimal Spring context for CLI operations
            SpringApplication app = new SpringApplication(ChickenApplication.class);
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
            
            ConfigurableApplicationContext context = app.run(args);
            
            // Execute CLI command
            ChickenCliRunner cliRunner = context.getBean(ChickenCliRunner.class);
            int exitCode = cliRunner.run(args);
            
            // Graceful shutdown
            context.close();
            System.exit(exitCode);
            
        } catch (Exception e) {
            System.err.println("CLI execution failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Starts the application in web server mode with full Spring Boot context.
     * 
     * This mode provides:
     * - RESTful API endpoints
     * - Web-based management interface
     * - Real-time server monitoring
     * - Multi-user support with authentication
     * - Plugin marketplace integration
     * 
     * @param args Application arguments (may include server configuration)
     */
    private static void runWebServerMode(String[] args) {
        System.out.println("Chicken Web Server Mode - Starting Application");
        System.out.println("==============================================");
        
        try {
            SpringApplication app = new SpringApplication(ChickenApplication.class);
            
            // Configure application for web server mode
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.SERVLET);
            
            ConfigurableApplicationContext context = app.run(args);
            
            // Display startup information
            String port = context.getEnvironment().getProperty("server.port", "8080");
            System.out.println("\n✅ Chicken Server Host started successfully!");
            System.out.println("🌐 Web Interface: http://localhost:" + port);
            System.out.println("📚 API Documentation: http://localhost:" + port + "/api/docs");
            System.out.println("🔧 Admin Panel: http://localhost:" + port + "/admin");
            System.out.println("\n📖 For CLI usage: java -jar chicken.jar --help");
            
        } catch (Exception e) {
            System.err.println("Failed to start web server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Checks if the command line arguments contain the server flag.
     * 
     * @param args Command line arguments
     * @return true if --server flag is present
     */
    private static boolean containsServerFlag(String[] args) {
        for (String arg : args) {
            if ("--server".equals(arg) || "--web".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}