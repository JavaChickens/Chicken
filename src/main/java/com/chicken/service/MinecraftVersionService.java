/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Service for managing Minecraft versions from latest to earliest snapshots.
 * Integrates with Mojang's version manifest API for comprehensive version support.
 */
package com.chicken.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class MinecraftVersionService {

    private static final Logger logger = LoggerFactory.getLogger(MinecraftVersionService.class);
    private static final String MOJANG_VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private List<MinecraftVersion> cachedVersions;
    private LocalDateTime lastCacheUpdate;

    public MinecraftVersionService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<List<MinecraftVersion>> getAllVersions() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (shouldRefreshCache()) {
                    refreshVersionCache();
                }
                return cachedVersions != null ? cachedVersions : getDefaultVersions();
            } catch (Exception e) {
                logger.error("Failed to fetch Minecraft versions", e);
                return getDefaultVersions();
            }
        });
    }

    public CompletableFuture<List<MinecraftVersion>> getReleaseVersions() {
        return getAllVersions().thenApply(versions -> 
            versions.stream()
                    .filter(v -> "release".equals(v.getType()))
                    .toList()
        );
    }

    public CompletableFuture<List<MinecraftVersion>> getSnapshotVersions() {
        return getAllVersions().thenApply(versions -> 
            versions.stream()
                    .filter(v -> "snapshot".equals(v.getType()))
                    .toList()
        );
    }

    public CompletableFuture<MinecraftVersion> getLatestRelease() {
        return getReleaseVersions().thenApply(versions -> 
            versions.isEmpty() ? null : versions.get(0)
        );
    }

    public CompletableFuture<MinecraftVersion> getLatestSnapshot() {
        return getSnapshotVersions().thenApply(versions -> 
            versions.isEmpty() ? null : versions.get(0)
        );
    }

    private boolean shouldRefreshCache() {
        return cachedVersions == null || 
               lastCacheUpdate == null || 
               lastCacheUpdate.isBefore(LocalDateTime.now().minusHours(1));
    }

    private void refreshVersionCache() throws IOException, InterruptedException {
        logger.info("Refreshing Minecraft version cache from Mojang API");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOJANG_VERSION_MANIFEST))
                .header("User-Agent", "Chicken-Server-Host/1.0.0")
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode manifest = objectMapper.readTree(response.body());
            List<MinecraftVersion> versions = new ArrayList<>();
            
            JsonNode versionsArray = manifest.get("versions");
            for (JsonNode versionNode : versionsArray) {
                versions.add(new MinecraftVersion(
                    versionNode.get("id").asText(),
                    versionNode.get("type").asText(),
                    versionNode.get("url").asText(),
                    versionNode.get("time").asText(),
                    versionNode.get("releaseTime").asText()
                ));
            }
            
            cachedVersions = versions;
            lastCacheUpdate = LocalDateTime.now();
            
            logger.info("Successfully cached {} Minecraft versions", versions.size());
        } else {
            throw new IOException("Failed to fetch version manifest: HTTP " + response.statusCode());
        }
    }

    private List<MinecraftVersion> getDefaultVersions() {
        // Fallback versions if API is unavailable
        return List.of(
            new MinecraftVersion("1.20.4", "release", "", "2023-12-07T12:00:00+00:00", "2023-12-07T12:00:00+00:00"),
            new MinecraftVersion("1.20.3", "release", "", "2023-12-05T12:00:00+00:00", "2023-12-05T12:00:00+00:00"),
            new MinecraftVersion("1.20.2", "release", "", "2023-09-21T12:00:00+00:00", "2023-09-21T12:00:00+00:00"),
            new MinecraftVersion("1.20.1", "release", "", "2023-06-12T12:00:00+00:00", "2023-06-12T12:00:00+00:00"),
            new MinecraftVersion("1.20", "release", "", "2023-06-07T12:00:00+00:00", "2023-06-07T12:00:00+00:00"),
            new MinecraftVersion("1.19.4", "release", "", "2023-03-14T12:00:00+00:00", "2023-03-14T12:00:00+00:00"),
            new MinecraftVersion("1.19.3", "release", "", "2022-12-07T12:00:00+00:00", "2022-12-07T12:00:00+00:00"),
            new MinecraftVersion("1.19.2", "release", "", "2022-08-05T12:00:00+00:00", "2022-08-05T12:00:00+00:00"),
            new MinecraftVersion("1.19.1", "release", "", "2022-07-27T12:00:00+00:00", "2022-07-27T12:00:00+00:00"),
            new MinecraftVersion("1.19", "release", "", "2022-06-07T12:00:00+00:00", "2022-06-07T12:00:00+00:00"),
            new MinecraftVersion("1.18.2", "release", "", "2022-02-28T12:00:00+00:00", "2022-02-28T12:00:00+00:00"),
            new MinecraftVersion("1.18.1", "release", "", "2021-12-10T12:00:00+00:00", "2021-12-10T12:00:00+00:00"),
            new MinecraftVersion("1.18", "release", "", "2021-11-30T12:00:00+00:00", "2021-11-30T12:00:00+00:00"),
            new MinecraftVersion("1.17.1", "release", "", "2021-07-06T12:00:00+00:00", "2021-07-06T12:00:00+00:00"),
            new MinecraftVersion("1.17", "release", "", "2021-06-08T12:00:00+00:00", "2021-06-08T12:00:00+00:00"),
            new MinecraftVersion("1.16.5", "release", "", "2021-01-15T12:00:00+00:00", "2021-01-15T12:00:00+00:00"),
            new MinecraftVersion("1.16.4", "release", "", "2020-11-02T12:00:00+00:00", "2020-11-02T12:00:00+00:00"),
            new MinecraftVersion("1.16.3", "release", "", "2020-09-10T12:00:00+00:00", "2020-09-10T12:00:00+00:00"),
            new MinecraftVersion("1.16.2", "release", "", "2020-08-11T12:00:00+00:00", "2020-08-11T12:00:00+00:00"),
            new MinecraftVersion("1.16.1", "release", "", "2020-06-24T12:00:00+00:00", "2020-06-24T12:00:00+00:00"),
            new MinecraftVersion("1.16", "release", "", "2020-06-23T12:00:00+00:00", "2020-06-23T12:00:00+00:00"),
            new MinecraftVersion("1.15.2", "release", "", "2020-01-21T12:00:00+00:00", "2020-01-21T12:00:00+00:00"),
            new MinecraftVersion("1.15.1", "release", "", "2019-12-17T12:00:00+00:00", "2019-12-17T12:00:00+00:00"),
            new MinecraftVersion("1.15", "release", "", "2019-12-10T12:00:00+00:00", "2019-12-10T12:00:00+00:00"),
            new MinecraftVersion("1.14.4", "release", "", "2019-07-19T12:00:00+00:00", "2019-07-19T12:00:00+00:00"),
            new MinecraftVersion("1.14.3", "release", "", "2019-06-24T12:00:00+00:00", "2019-06-24T12:00:00+00:00"),
            new MinecraftVersion("1.14.2", "release", "", "2019-05-27T12:00:00+00:00", "2019-05-27T12:00:00+00:00"),
            new MinecraftVersion("1.14.1", "release", "", "2019-05-13T12:00:00+00:00", "2019-05-13T12:00:00+00:00"),
            new MinecraftVersion("1.14", "release", "", "2019-04-23T12:00:00+00:00", "2019-04-23T12:00:00+00:00"),
            new MinecraftVersion("1.13.2", "release", "", "2018-10-22T12:00:00+00:00", "2018-10-22T12:00:00+00:00"),
            new MinecraftVersion("1.13.1", "release", "", "2018-08-22T12:00:00+00:00", "2018-08-22T12:00:00+00:00"),
            new MinecraftVersion("1.13", "release", "", "2018-07-18T12:00:00+00:00", "2018-07-18T12:00:00+00:00"),
            new MinecraftVersion("1.12.2", "release", "", "2017-09-18T12:00:00+00:00", "2017-09-18T12:00:00+00:00"),
            new MinecraftVersion("1.12.1", "release", "", "2017-06-02T12:00:00+00:00", "2017-06-02T12:00:00+00:00"),
            new MinecraftVersion("1.12", "release", "", "2017-06-07T12:00:00+00:00", "2017-06-07T12:00:00+00:00"),
            new MinecraftVersion("1.11.2", "release", "", "2016-12-21T12:00:00+00:00", "2016-12-21T12:00:00+00:00"),
            new MinecraftVersion("1.11.1", "release", "", "2016-12-20T12:00:00+00:00", "2016-12-20T12:00:00+00:00"),
            new MinecraftVersion("1.11", "release", "", "2016-11-14T12:00:00+00:00", "2016-11-14T12:00:00+00:00"),
            new MinecraftVersion("1.10.2", "release", "", "2016-06-23T12:00:00+00:00", "2016-06-23T12:00:00+00:00"),
            new MinecraftVersion("1.10.1", "release", "", "2016-06-22T12:00:00+00:00", "2016-06-22T12:00:00+00:00"),
            new MinecraftVersion("1.10", "release", "", "2016-06-08T12:00:00+00:00", "2016-06-08T12:00:00+00:00"),
            new MinecraftVersion("1.9.4", "release", "", "2016-05-10T12:00:00+00:00", "2016-05-10T12:00:00+00:00"),
            new MinecraftVersion("1.9.3", "release", "", "2016-05-10T12:00:00+00:00", "2016-05-10T12:00:00+00:00"),
            new MinecraftVersion("1.9.2", "release", "", "2016-03-30T12:00:00+00:00", "2016-03-30T12:00:00+00:00"),
            new MinecraftVersion("1.9.1", "release", "", "2016-03-30T12:00:00+00:00", "2016-03-30T12:00:00+00:00"),
            new MinecraftVersion("1.9", "release", "", "2016-02-29T12:00:00+00:00", "2016-02-29T12:00:00+00:00"),
            new MinecraftVersion("1.8.9", "release", "", "2015-12-09T12:00:00+00:00", "2015-12-09T12:00:00+00:00"),
            new MinecraftVersion("1.8.8", "release", "", "2015-07-28T12:00:00+00:00", "2015-07-28T12:00:00+00:00"),
            new MinecraftVersion("1.8.7", "release", "", "2015-06-05T12:00:00+00:00", "2015-06-05T12:00:00+00:00"),
            new MinecraftVersion("1.8.6", "release", "", "2015-05-25T12:00:00+00:00", "2015-05-25T12:00:00+00:00"),
            new MinecraftVersion("1.8.5", "release", "", "2015-05-22T12:00:00+00:00", "2015-05-22T12:00:00+00:00"),
            new MinecraftVersion("1.8.4", "release", "", "2015-04-17T12:00:00+00:00", "2015-04-17T12:00:00+00:00"),
            new MinecraftVersion("1.8.3", "release", "", "2015-02-20T12:00:00+00:00", "2015-02-20T12:00:00+00:00"),
            new MinecraftVersion("1.8.2", "release", "", "2015-02-19T12:00:00+00:00", "2015-02-19T12:00:00+00:00"),
            new MinecraftVersion("1.8.1", "release", "", "2014-11-24T12:00:00+00:00", "2014-11-24T12:00:00+00:00"),
            new MinecraftVersion("1.8", "release", "", "2014-09-02T12:00:00+00:00", "2014-09-02T12:00:00+00:00"),
            new MinecraftVersion("1.7.10", "release", "", "2014-06-26T12:00:00+00:00", "2014-06-26T12:00:00+00:00"),
            new MinecraftVersion("1.7.9", "release", "", "2014-04-14T12:00:00+00:00", "2014-04-14T12:00:00+00:00"),
            new MinecraftVersion("1.7.8", "release", "", "2014-04-11T12:00:00+00:00", "2014-04-11T12:00:00+00:00"),
            new MinecraftVersion("1.7.7", "release", "", "2014-04-09T12:00:00+00:00", "2014-04-09T12:00:00+00:00"),
            new MinecraftVersion("1.7.6", "release", "", "2014-04-09T12:00:00+00:00", "2014-04-09T12:00:00+00:00"),
            new MinecraftVersion("1.7.5", "release", "", "2014-02-26T12:00:00+00:00", "2014-02-26T12:00:00+00:00"),
            new MinecraftVersion("1.7.4", "release", "", "2013-12-10T12:00:00+00:00", "2013-12-10T12:00:00+00:00"),
            new MinecraftVersion("1.7.3", "release", "", "2013-12-06T12:00:00+00:00", "2013-12-06T12:00:00+00:00"),
            new MinecraftVersion("1.7.2", "release", "", "2013-10-25T12:00:00+00:00", "2013-10-25T12:00:00+00:00")
        );
    }

    public static class MinecraftVersion {
        private final String id;
        private final String type;
        private final String url;
        private final String time;
        private final String releaseTime;

        public MinecraftVersion(String id, String type, String url, String time, String releaseTime) {
            this.id = id;
            this.type = type;
            this.url = url;
            this.time = time;
            this.releaseTime = releaseTime;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getUrl() { return url; }
        public String getTime() { return time; }
        public String getReleaseTime() { return releaseTime; }
        
        public boolean isRelease() { return "release".equals(type); }
        public boolean isSnapshot() { return "snapshot".equals(type); }
        public boolean isOldBeta() { return "old_beta".equals(type); }
        public boolean isOldAlpha() { return "old_alpha".equals(type); }
    }
}