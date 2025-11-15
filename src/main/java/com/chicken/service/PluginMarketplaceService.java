/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Advanced plugin marketplace service with real repository integration.
 * Connects to SpigotMC, Bukkit, Paper, and GitHub repositories for
 * comprehensive plugin discovery and installation.
 */
package com.chicken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class PluginMarketplaceService {

    private static final Logger logger = LoggerFactory.getLogger(PluginMarketplaceService.class);
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public PluginMarketplaceService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<List<PluginInfo>> searchPlugins(String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<PluginInfo> results = new ArrayList<>();
            
            try {
                // Search SpigotMC
                results.addAll(searchSpigotMC(query, limit / 3));
                
                // Search Bukkit
                results.addAll(searchBukkit(query, limit / 3));
                
                // Search GitHub
                results.addAll(searchGitHub(query, limit / 3));
                
                return results.stream()
                        .sorted((a, b) -> Integer.compare(b.getDownloads(), a.getDownloads()))
                        .limit(limit)
                        .toList();
                        
            } catch (Exception e) {
                logger.error("Failed to search plugins", e);
                return getPopularPlugins();
            }
        });
    }

    private List<PluginInfo> searchSpigotMC(String query, int limit) throws IOException, InterruptedException {
        String url = "https://api.spigotmc.org/simple/0.2/index.php?action=getResources&search=" + 
                     java.net.URLEncoder.encode(query, "UTF-8") + "&size=" + limit;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Chicken-Server-Host/1.0.0")
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            List<PluginInfo> plugins = new ArrayList<>();
            
            for (JsonNode plugin : root) {
                plugins.add(new PluginInfo(
                    plugin.get("name").asText(),
                    plugin.get("tag").asText(),
                    plugin.get("version").asText(),
                    plugin.get("author").asText(),
                    "https://www.spigotmc.org/resources/" + plugin.get("id").asText() + "/download",
                    plugin.get("downloads").asInt(),
                    "SpigotMC",
                    plugin.get("rating").asDouble()
                ));
            }
            return plugins;
        }
        return Collections.emptyList();
    }

    private List<PluginInfo> searchBukkit(String query, int limit) throws IOException, InterruptedException {
        String url = "https://servermods.forgesvc.net/servermods/projects?search=" + 
                     java.net.URLEncoder.encode(query, "UTF-8") + "&gameId=432&pageSize=" + limit;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Chicken-Server-Host/1.0.0")
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            List<PluginInfo> plugins = new ArrayList<>();
            
            for (JsonNode plugin : root) {
                plugins.add(new PluginInfo(
                    plugin.get("name").asText(),
                    plugin.get("summary").asText(),
                    plugin.get("latestFiles").get(0).get("displayName").asText(),
                    plugin.get("authors").get(0).get("name").asText(),
                    plugin.get("latestFiles").get(0).get("downloadUrl").asText(),
                    plugin.get("downloadCount").asInt(),
                    "Bukkit",
                    plugin.get("popularityScore").asDouble()
                ));
            }
            return plugins;
        }
        return Collections.emptyList();
    }

    private List<PluginInfo> searchGitHub(String query, int limit) throws IOException, InterruptedException {
        String url = "https://api.github.com/search/repositories?q=" + 
                     java.net.URLEncoder.encode(query + " minecraft plugin", "UTF-8") + 
                     "&sort=stars&per_page=" + limit;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Chicken-Server-Host/1.0.0")
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            List<PluginInfo> plugins = new ArrayList<>();
            
            for (JsonNode repo : root.get("items")) {
                String releaseUrl = getLatestReleaseUrl(repo.get("full_name").asText());
                if (releaseUrl != null) {
                    plugins.add(new PluginInfo(
                        repo.get("name").asText(),
                        repo.get("description").asText(),
                        "latest",
                        repo.get("owner").get("login").asText(),
                        releaseUrl,
                        repo.get("stargazers_count").asInt(),
                        "GitHub",
                        repo.get("stargazers_count").asDouble() / 1000.0
                    ));
                }
            }
            return plugins;
        }
        return Collections.emptyList();
    }

    private String getLatestReleaseUrl(String repoFullName) {
        try {
            String url = "https://api.github.com/repos/" + repoFullName + "/releases/latest";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Chicken-Server-Host/1.0.0")
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode release = objectMapper.readTree(response.body());
                JsonNode assets = release.get("assets");
                
                for (JsonNode asset : assets) {
                    String name = asset.get("name").asText().toLowerCase();
                    if (name.endsWith(".jar") && !name.contains("source")) {
                        return asset.get("browser_download_url").asText();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to get release URL for {}", repoFullName);
        }
        return null;
    }

    public List<PluginInfo> getPopularPlugins() {
        return List.of(
            new PluginInfo("EssentialsX", "Essential commands and features", "2.20.1", "EssentialsX Team",
                "https://github.com/EssentialsX/Essentials/releases/latest/download/EssentialsX-2.20.1.jar", 
                5000000, "GitHub", 4.8),
            new PluginInfo("WorldEdit", "In-game world editor", "7.2.15", "sk89q",
                "https://dev.bukkit.org/projects/worldedit/files/latest", 
                3000000, "Bukkit", 4.9),
            new PluginInfo("WorldGuard", "Region protection plugin", "7.0.9", "sk89q",
                "https://dev.bukkit.org/projects/worldguard/files/latest", 
                2500000, "Bukkit", 4.7),
            new PluginInfo("Vault", "Economy and permissions API", "1.7.3", "MilkBowl",
                "https://github.com/MilkBowl/Vault/releases/latest/download/Vault.jar", 
                2000000, "GitHub", 4.6),
            new PluginInfo("LuckPerms", "Advanced permissions plugin", "5.4.102", "Luck",
                "https://download.luckperms.net/1515/bukkit/loader/LuckPerms-Bukkit-5.4.102.jar", 
                1800000, "LuckPerms", 4.9),
            new PluginInfo("PlaceholderAPI", "Placeholder support for plugins", "2.11.5", "HelpChat",
                "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/latest/download/PlaceholderAPI-2.11.5.jar", 
                1500000, "GitHub", 4.5),
            new PluginInfo("Citizens", "NPC plugin", "2.0.33", "fullwall",
                "https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/artifact/dist/target/Citizens-2.0.33-SNAPSHOT.jar", 
                1200000, "Jenkins", 4.4),
            new PluginInfo("ChestShop", "Economy shop plugin", "3.12.2", "Acrobot",
                "https://github.com/ChestShop-authors/ChestShop-3/releases/latest/download/ChestShop.jar", 
                1000000, "GitHub", 4.3)
        );
    }

    public static class PluginInfo {
        private final String name;
        private final String description;
        private final String version;
        private final String author;
        private final String downloadUrl;
        private final int downloads;
        private final String source;
        private final double rating;

        public PluginInfo(String name, String description, String version, String author, 
                         String downloadUrl, int downloads, String source, double rating) {
            this.name = name;
            this.description = description;
            this.version = version;
            this.author = author;
            this.downloadUrl = downloadUrl;
            this.downloads = downloads;
            this.source = source;
            this.rating = rating;
        }

        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getVersion() { return version; }
        public String getAuthor() { return author; }
        public String getDownloadUrl() { return downloadUrl; }
        public int getDownloads() { return downloads; }
        public String getSource() { return source; }
        public double getRating() { return rating; }
    }
}