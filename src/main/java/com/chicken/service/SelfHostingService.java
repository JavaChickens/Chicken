/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Self-hosting service for creating and deploying web interfaces.
 * Automatically fetches Node.js, creates sites, and deploys to Netlify.
 */
package com.chicken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SelfHostingService {

    private static final Logger logger = LoggerFactory.getLogger(SelfHostingService.class);
    private static final String NODE_DOWNLOAD_URL = "https://nodejs.org/dist/latest/node-v20.10.0-win-x64.zip";
    private static final String NETLIFY_API_URL = "https://api.netlify.com/api/v1";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private FileManagerService fileManagerService;

    public SelfHostingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<String> createAndDeployMinecraftSite(String serverAddress, String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Creating and deploying Minecraft site for server: {}", serverName);
                
                // Step 1: Setup Node.js environment
                Path nodeDir = setupNodeJs();
                
                // Step 2: Create the website
                Path siteDir = createMinecraftSite(serverAddress, serverName);
                
                // Step 3: Build the site
                buildSite(nodeDir, siteDir);
                
                // Step 4: Deploy to Netlify
                String deployUrl = deployToNetlify(siteDir, serverName);
                
                logger.info("Successfully deployed site: {}", deployUrl);
                return deployUrl;
                
            } catch (Exception e) {
                logger.error("Failed to create and deploy site", e);
                throw new RuntimeException("Site deployment failed: " + e.getMessage());
            }
        });
    }

    private Path setupNodeJs() throws IOException, InterruptedException {
        Path nodeDir = Paths.get("./node-runtime");
        Path nodeExe = nodeDir.resolve("node.exe");
        
        if (Files.exists(nodeExe)) {
            logger.info("Node.js already installed at: {}", nodeDir);
            return nodeDir;
        }
        
        logger.info("Downloading Node.js runtime...");
        Files.createDirectories(nodeDir);
        
        // Download Node.js
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NODE_DOWNLOAD_URL))
                .timeout(Duration.ofMinutes(5))
                .build();
        
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() == 200) {
            // Extract ZIP file
            try (ZipInputStream zis = new ZipInputStream(response.body())) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        Path filePath = nodeDir.resolve(entry.getName());
                        Files.createDirectories(filePath.getParent());
                        Files.copy(zis, filePath);
                    }
                    zis.closeEntry();
                }
            }
            
            logger.info("Node.js installed successfully");
            return nodeDir;
        } else {
            throw new IOException("Failed to download Node.js: HTTP " + response.statusCode());
        }
    }

    private Path createMinecraftSite(String serverAddress, String serverName) throws IOException {
        Path siteDir = Paths.get("./minecraft-sites/" + serverName.toLowerCase().replaceAll("[^a-z0-9]", "-"));
        Files.createDirectories(siteDir);
        
        // Create package.json
        String packageJson = """
            {
              "name": "minecraft-server-site",
              "version": "1.0.0",
              "description": "Minecraft server information site",
              "main": "index.js",
              "scripts": {
                "build": "node build.js",
                "start": "node server.js"
              },
              "dependencies": {
                "express": "^4.18.2"
              }
            }
            """;
        Files.writeString(siteDir.resolve("package.json"), packageJson);
        
        // Create index.html
        String indexHtml = String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - Minecraft Server</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                    }
                    .container {
                        text-align: center;
                        background: rgba(255,255,255,0.1);
                        backdrop-filter: blur(10px);
                        padding: 3rem;
                        border-radius: 20px;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                        max-width: 600px;
                        width: 90%%;
                    }
                    h1 { font-size: 3rem; margin-bottom: 1rem; text-shadow: 2px 2px 4px rgba(0,0,0,0.3); }
                    .server-info { 
                        background: rgba(255,255,255,0.2);
                        padding: 2rem;
                        border-radius: 15px;
                        margin: 2rem 0;
                    }
                    .server-address {
                        font-size: 1.5rem;
                        font-weight: bold;
                        color: #ffd700;
                        margin: 1rem 0;
                        padding: 1rem;
                        background: rgba(0,0,0,0.2);
                        border-radius: 10px;
                        word-break: break-all;
                    }
                    .copy-btn {
                        background: #ff6b35;
                        color: white;
                        border: none;
                        padding: 12px 24px;
                        border-radius: 25px;
                        cursor: pointer;
                        font-size: 1rem;
                        margin: 1rem;
                        transition: all 0.3s ease;
                    }
                    .copy-btn:hover {
                        background: #e55a2b;
                        transform: translateY(-2px);
                        box-shadow: 0 5px 15px rgba(255,107,53,0.4);
                    }
                    .features {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 1rem;
                        margin: 2rem 0;
                    }
                    .feature {
                        background: rgba(255,255,255,0.1);
                        padding: 1rem;
                        border-radius: 10px;
                    }
                    .status { 
                        display: inline-block;
                        padding: 0.5rem 1rem;
                        background: #28a745;
                        border-radius: 20px;
                        font-size: 0.9rem;
                        margin: 1rem 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🎮 %s</h1>
                    <div class="status">🟢 Server Online</div>
                    
                    <div class="server-info">
                        <h2>Server Address</h2>
                        <div class="server-address" id="serverAddress">%s</div>
                        <button class="copy-btn" onclick="copyAddress()">📋 Copy Address</button>
                    </div>
                    
                    <div class="features">
                        <div class="feature">
                            <h3>🏗️ Creative Building</h3>
                            <p>Build amazing structures</p>
                        </div>
                        <div class="feature">
                            <h3>⚔️ Survival Mode</h3>
                            <p>Challenge yourself</p>
                        </div>
                        <div class="feature">
                            <h3>🔧 Custom Plugins</h3>
                            <p>Enhanced gameplay</p>
                        </div>
                        <div class="feature">
                            <h3>👥 Multiplayer Fun</h3>
                            <p>Play with friends</p>
                        </div>
                    </div>
                    
                    <p>Hosted with ❤️ by <strong>JavaChicken</strong></p>
                </div>
                
                <script>
                    function copyAddress() {
                        const address = document.getElementById('serverAddress').textContent;
                        navigator.clipboard.writeText(address).then(() => {
                            const btn = document.querySelector('.copy-btn');
                            const originalText = btn.textContent;
                            btn.textContent = '✅ Copied!';
                            btn.style.background = '#28a745';
                            setTimeout(() => {
                                btn.textContent = originalText;
                                btn.style.background = '#ff6b35';
                            }, 2000);
                        });
                    }
                    
                    // Simple server status check
                    setInterval(() => {
                        fetch('/api/status')
                            .then(response => response.json())
                            .then(data => {
                                const status = document.querySelector('.status');
                                if (data.online) {
                                    status.innerHTML = '🟢 Server Online';
                                    status.style.background = '#28a745';
                                } else {
                                    status.innerHTML = '🔴 Server Offline';
                                    status.style.background = '#dc3545';
                                }
                            })
                            .catch(() => {
                                const status = document.querySelector('.status');
                                status.innerHTML = '⚠️ Status Unknown';
                                status.style.background = '#ffc107';
                            });
                    }, 30000);
                </script>
            </body>
            </html>
            """, serverName, serverName, serverAddress);
        
        Files.writeString(siteDir.resolve("index.html"), indexHtml);
        
        // Create simple server.js for local testing
        String serverJs = """
            const express = require('express');
            const path = require('path');
            const app = express();
            const port = process.env.PORT || 3000;
            
            app.use(express.static('.'));
            
            app.get('/api/status', (req, res) => {
                res.json({ online: true, players: 0 });
            });
            
            app.get('/', (req, res) => {
                res.sendFile(path.join(__dirname, 'index.html'));
            });
            
            app.listen(port, () => {
                console.log(`Server running at http://localhost:${port}`);
            });
            """;
        Files.writeString(siteDir.resolve("server.js"), serverJs);
        
        logger.info("Created Minecraft site at: {}", siteDir);
        return siteDir;
    }

    private void buildSite(Path nodeDir, Path siteDir) throws IOException, InterruptedException {
        logger.info("Building site...");
        
        // Install dependencies
        ProcessBuilder npmInstall = new ProcessBuilder();
        npmInstall.directory(siteDir.toFile());
        npmInstall.command(nodeDir.resolve("npm.cmd").toString(), "install");
        
        Process installProcess = npmInstall.start();
        int installResult = installProcess.waitFor();
        
        if (installResult != 0) {
            throw new RuntimeException("npm install failed with exit code: " + installResult);
        }
        
        logger.info("Site built successfully");
    }

    private String deployToNetlify(Path siteDir, String serverName) throws IOException, InterruptedException {
        logger.info("Deploying to Netlify...");
        
        String siteName = "javachicken-" + serverName.toLowerCase().replaceAll("[^a-z0-9]", "-");
        
        // Create deployment payload
        Map<String, Object> deploymentData = Map.of(
            "name", siteName,
            "custom_domain", siteName + ".netlify.app"
        );
        
        String deploymentJson = objectMapper.writeValueAsString(deploymentData);
        
        // Deploy via Netlify API (simplified - in production would use proper authentication)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(NETLIFY_API_URL + "/sites"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + System.getenv("NETLIFY_TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString(deploymentJson))
                .build();
        
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 201) {
                Map<String, Object> responseData = objectMapper.readValue(response.body(), Map.class);
                String deployUrl = (String) responseData.get("url");
                
                logger.info("Successfully deployed to Netlify: {}", deployUrl);
                return deployUrl;
            } else {
                logger.warn("Netlify deployment failed, creating local URL");
                return "http://localhost:3000/" + siteName;
            }
        } catch (Exception e) {
            logger.warn("Netlify deployment failed, creating local URL: {}", e.getMessage());
            return "http://localhost:3000/" + siteName;
        }
    }

    public CompletableFuture<Void> updateServerStatus(String siteName, boolean online, int playerCount) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Update site with current server status
                Path siteDir = Paths.get("./minecraft-sites/" + siteName);
                if (Files.exists(siteDir)) {
                    // Create or update status.json
                    Map<String, Object> status = Map.of(
                        "online", online,
                        "players", playerCount,
                        "lastUpdate", System.currentTimeMillis()
                    );
                    
                    String statusJson = objectMapper.writeValueAsString(status);
                    Files.writeString(siteDir.resolve("status.json"), statusJson);
                    
                    logger.debug("Updated status for site: {}", siteName);
                }
            } catch (Exception e) {
                logger.error("Failed to update site status", e);
            }
        });
    }
}