/*
 * Copyright (c) 2025 piratebomber
 * Licensed under the Apache License, Version 2.0
 *
 * Player management service for Minecraft servers.
 * Handles player statistics, permissions, and administrative functions.
 */
package com.chicken.service;

import com.chicken.model.MinecraftServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlayerManagementService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerManagementService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MinecraftServerService serverService;

    @Autowired
    private FileManagerService fileManagerService;

    public PlayerStats getServerStats(Long serverId) {
        try {
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(serverId))
                    .findFirst();

            if (serverOpt.isEmpty()) {
                throw new IllegalArgumentException("Server not found: " + serverId);
            }

            MinecraftServer server = serverOpt.get();
            
            if (!server.isRunning()) {
                return new PlayerStats(0, Collections.emptyList(), server.getName());
            }

            // Try RCON first, fallback to log parsing
            try {
                return getStatsViaRcon(server);
            } catch (Exception e) {
                logger.debug("RCON failed, trying log parsing", e);
                return getStatsViaLogs(server);
            }

        } catch (Exception e) {
            logger.error("Failed to get server stats for server {}", serverId, e);
            return new PlayerStats(0, Collections.emptyList(), "Unknown");
        }
    }

    private PlayerStats getStatsViaRcon(MinecraftServer server) throws IOException {
        // Simple RCON implementation
        try (Socket socket = new Socket("localhost", server.getPort())) {
            // Send list command via RCON
            String response = sendRconCommand(socket, "list");
            return parsePlayerList(response, server.getName());
        }
    }

    private PlayerStats getStatsViaLogs(MinecraftServer server) throws IOException {
        Path serverDir = Path.of("./servers/" + server.getDirectoryName());
        Path logsDir = serverDir.resolve("logs");
        Path latestLog = logsDir.resolve("latest.log");

        if (!Files.exists(latestLog)) {
            return new PlayerStats(0, Collections.emptyList(), server.getName());
        }

        List<String> logLines = Files.readAllLines(latestLog);
        Set<String> onlinePlayers = new HashSet<>();
        
        // Parse recent log entries for player join/leave events
        Pattern joinPattern = Pattern.compile("\\[.*\\] \\[.*\\]: (\\w+) joined the game");
        Pattern leavePattern = Pattern.compile("\\[.*\\] \\[.*\\]: (\\w+) left the game");
        
        for (String line : logLines) {
            Matcher joinMatcher = joinPattern.matcher(line);
            Matcher leaveMatcher = leavePattern.matcher(line);
            
            if (joinMatcher.find()) {
                onlinePlayers.add(joinMatcher.group(1));
            } else if (leaveMatcher.find()) {
                onlinePlayers.remove(leaveMatcher.group(1));
            }
        }

        List<PlayerInfo> players = onlinePlayers.stream()
                .map(name -> new PlayerInfo(name, "player", System.currentTimeMillis()))
                .toList();

        return new PlayerStats(players.size(), players, server.getName());
    }

    private String sendRconCommand(Socket socket, String command) throws IOException {
        // Simplified RCON protocol implementation
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());
        
        // Send command packet
        byte[] commandBytes = command.getBytes("UTF-8");
        out.writeInt(commandBytes.length + 10);
        out.writeInt(1); // Request ID
        out.writeInt(2); // Command type
        out.write(commandBytes);
        out.writeByte(0);
        out.writeByte(0);
        
        // Read response
        int responseLength = in.readInt();
        byte[] responseData = new byte[responseLength];
        in.readFully(responseData);
        
        return new String(responseData, "UTF-8").trim();
    }

    private PlayerStats parsePlayerList(String response, String serverName) {
        // Parse "There are X of a max of Y players online: player1, player2, ..."
        Pattern pattern = Pattern.compile("There are (\\d+) of a max of \\d+ players online:?(.*)");
        Matcher matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            String playerNames = matcher.group(2).trim();
            
            List<PlayerInfo> players = new ArrayList<>();
            if (!playerNames.isEmpty() && count > 0) {
                String[] names = playerNames.split(",\\s*");
                for (String name : names) {
                    if (!name.trim().isEmpty()) {
                        players.add(new PlayerInfo(name.trim(), "player", System.currentTimeMillis()));
                    }
                }
            }
            
            return new PlayerStats(count, players, serverName);
        }
        
        return new PlayerStats(0, Collections.emptyList(), serverName);
    }

    public void setPlayerPermission(Long serverId, String playerName, String permission) {
        try {
            Optional<MinecraftServer> serverOpt = serverService.getAllServers().stream()
                    .filter(s -> s.getId().equals(serverId))
                    .findFirst();

            if (serverOpt.isEmpty()) {
                throw new IllegalArgumentException("Server not found: " + serverId);
            }

            MinecraftServer server = serverOpt.get();
            
            switch (permission.toLowerCase()) {
                case "admin":
                    makePlayerAdmin(server, playerName);
                    break;
                case "vip":
                    makePlayerVip(server, playerName);
                    break;
                case "player":
                    removePlayerPermissions(server, playerName);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid permission: " + permission);
            }

        } catch (Exception e) {
            logger.error("Failed to set player permission", e);
            throw new RuntimeException("Failed to set player permission: " + e.getMessage());
        }
    }

    private void makePlayerAdmin(MinecraftServer server, String playerName) throws IOException {
        Path serverDir = Path.of("./servers/" + server.getDirectoryName());
        Path opsFile = serverDir.resolve("ops.json");
        
        List<Map<String, Object>> ops = new ArrayList<>();
        
        if (Files.exists(opsFile)) {
            String content = Files.readString(opsFile);
            if (!content.trim().isEmpty()) {
                JsonNode opsNode = objectMapper.readTree(content);
                for (JsonNode op : opsNode) {
                    Map<String, Object> opMap = objectMapper.convertValue(op, Map.class);
                    ops.add(opMap);
                }
            }
        }
        
        // Remove existing entry for this player
        ops.removeIf(op -> playerName.equals(op.get("name")));
        
        // Add new admin entry
        Map<String, Object> newOp = new HashMap<>();
        newOp.put("uuid", generateUUID(playerName));
        newOp.put("name", playerName);
        newOp.put("level", 4);
        newOp.put("bypassesPlayerLimit", false);
        ops.add(newOp);
        
        String opsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ops);
        Files.writeString(opsFile, opsJson);
        
        logger.info("Made player {} admin on server {}", playerName, server.getName());
    }

    private void makePlayerVip(MinecraftServer server, String playerName) throws IOException {
        Path serverDir = Path.of("./servers/" + server.getDirectoryName());
        Path whitelistFile = serverDir.resolve("whitelist.json");
        
        List<Map<String, Object>> whitelist = new ArrayList<>();
        
        if (Files.exists(whitelistFile)) {
            String content = Files.readString(whitelistFile);
            if (!content.trim().isEmpty()) {
                JsonNode whitelistNode = objectMapper.readTree(content);
                for (JsonNode player : whitelistNode) {
                    Map<String, Object> playerMap = objectMapper.convertValue(player, Map.class);
                    whitelist.add(playerMap);
                }
            }
        }
        
        // Check if player already exists
        boolean exists = whitelist.stream()
                .anyMatch(p -> playerName.equals(p.get("name")));
        
        if (!exists) {
            Map<String, Object> newPlayer = new HashMap<>();
            newPlayer.put("uuid", generateUUID(playerName));
            newPlayer.put("name", playerName);
            whitelist.add(newPlayer);
            
            String whitelistJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(whitelist);
            Files.writeString(whitelistFile, whitelistJson);
        }
        
        logger.info("Made player {} VIP on server {}", playerName, server.getName());
    }

    private void removePlayerPermissions(MinecraftServer server, String playerName) throws IOException {
        Path serverDir = Path.of("./servers/" + server.getDirectoryName());
        
        // Remove from ops
        Path opsFile = serverDir.resolve("ops.json");
        if (Files.exists(opsFile)) {
            String content = Files.readString(opsFile);
            if (!content.trim().isEmpty()) {
                JsonNode opsNode = objectMapper.readTree(content);
                List<Map<String, Object>> ops = new ArrayList<>();
                
                for (JsonNode op : opsNode) {
                    Map<String, Object> opMap = objectMapper.convertValue(op, Map.class);
                    if (!playerName.equals(opMap.get("name"))) {
                        ops.add(opMap);
                    }
                }
                
                String opsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ops);
                Files.writeString(opsFile, opsJson);
            }
        }
        
        logger.info("Removed permissions for player {} on server {}", playerName, server.getName());
    }

    private String generateUUID(String playerName) {
        // Generate offline UUID for player name
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes()).toString();
    }

    public static class PlayerStats {
        private final int onlineCount;
        private final List<PlayerInfo> players;
        private final String serverName;

        public PlayerStats(int onlineCount, List<PlayerInfo> players, String serverName) {
            this.onlineCount = onlineCount;
            this.players = players;
            this.serverName = serverName;
        }

        public int getOnlineCount() { return onlineCount; }
        public List<PlayerInfo> getPlayers() { return players; }
        public String getServerName() { return serverName; }
    }

    public static class PlayerInfo {
        private final String name;
        private final String permission;
        private final long joinTime;

        public PlayerInfo(String name, String permission, long joinTime) {
            this.name = name;
            this.permission = permission;
            this.joinTime = joinTime;
        }

        public String getName() { return name; }
        public String getPermission() { return permission; }
        public long getJoinTime() { return joinTime; }
    }
}