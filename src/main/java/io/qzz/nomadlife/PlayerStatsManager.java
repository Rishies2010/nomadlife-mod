package io.qzz.nomadlife;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class PlayerStatsManager {
    private static final String STATS_API_URL = "https://www.nomadlife.qzz.io/api/stats";
    private static final String BOT_SECRET = "nomadlifepower";
    
    private final HttpClient httpClient;
    private final Gson gson;
    private MinecraftServer server;
    
    public PlayerStatsManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }
    
    public void setServer(MinecraftServer server) {
        this.server = server;
    }
    
    /**
     * Sync all player stats to the website
     */
    public void syncStats() {
        if (server == null) {
            NomadLife.LOGGER.warn("Server not set, cannot sync stats");
            return;
        }
        
        try {
            // Get world folder (Fabric API)
            Path serverPath = server.getRunDirectory();
            String worldName = server.getSaveProperties().getLevelName();
            Path statsPath = serverPath.resolve(worldName).resolve("stats");
            Path whitelistPath = serverPath.resolve("whitelist.json");
            
            if (!Files.exists(statsPath)) {
                NomadLife.LOGGER.warn("Stats folder not found: {}", statsPath);
                return;
            }
            
            if (!Files.exists(whitelistPath)) {
                NomadLife.LOGGER.warn("Whitelist not found: {}", whitelistPath);
                return;
            }
            
            // Load whitelist (UUID -> username mapping)
            Map<String, String> uuidToUsername = loadWhitelist(whitelistPath.toFile());
            
            // Load player mappings from API (to filter only linked players)
            Set<String> linkedPlayers = fetchLinkedPlayers();
            
            // Read all stat files
            List<JsonObject> allStats = new ArrayList<>();
            
            File[] statFiles = statsPath.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (statFiles != null) {
                for (File statFile : statFiles) {
                    try {
                        // Extract UUID from filename (remove .json extension)
                        String uuid = statFile.getName().replace(".json", "");
                        
                        // Get Minecraft username from whitelist
                        String username = uuidToUsername.get(uuid);
                        
                        if (username == null) {
                            NomadLife.LOGGER.debug("UUID {} not in whitelist, skipping", uuid);
                            continue;
                        }
                        
                        // Skip if player is not linked to Discord
                        if (!linkedPlayers.contains(username)) {
                            NomadLife.LOGGER.debug("Player {} not linked to Discord, skipping", username);
                            continue;
                        }
                        
                        // Read the stats file
                        JsonObject statsData = gson.fromJson(new FileReader(statFile), JsonObject.class);
                        
                        // Create player stats object
                        JsonObject playerStats = new JsonObject();
                        playerStats.addProperty("uuid", uuid);
                        playerStats.addProperty("username", username);
                        playerStats.add("stats", statsData.get("stats"));
                        
                        allStats.add(playerStats);
                        
                    } catch (Exception e) {
                        NomadLife.LOGGER.error("Failed to read stats for {}", statFile.getName(), e);
                    }
                }
            }
            
            if (allStats.isEmpty()) {
                NomadLife.LOGGER.debug("No linked player stats to sync");
                return;
            }
            
            // Send to API
            sendStatsToAPI(allStats);
            
            NomadLife.LOGGER.info("Synced stats for {} players", allStats.size());
            
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to sync player stats", e);
        }
    }
    
    /**
     * Load whitelist.json to get UUID -> username mapping
     */
    private Map<String, String> loadWhitelist(File whitelistFile) {
        Map<String, String> mapping = new HashMap<>();
        
        try {
            JsonArray whitelist = gson.fromJson(new FileReader(whitelistFile), JsonArray.class);
            
            for (JsonElement element : whitelist) {
                JsonObject entry = element.getAsJsonObject();
                String uuid = entry.get("uuid").getAsString();
                String name = entry.get("name").getAsString();
                mapping.put(uuid, name);
            }
            
            NomadLife.LOGGER.debug("Loaded {} entries from whitelist", mapping.size());
            
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to load whitelist", e);
        }
        
        return mapping;
    }
    
    /**
     * Fetch list of linked players from the API
     */
    private Set<String> fetchLinkedPlayers() {
        Set<String> linkedPlayers = new HashSet<>();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.nomadlife.qzz.io/api/player-mappings"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                
                if (json.get("success").getAsBoolean()) {
                    JsonArray mappings = json.getAsJsonArray("mappings");
                    
                    for (JsonElement element : mappings) {
                        JsonObject mapping = element.getAsJsonObject();
                        String username = mapping.get("java").getAsString();
                        linkedPlayers.add(username);
                    }
                }
            }
            
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to fetch linked players", e);
        }
        
        return linkedPlayers;
    }
    
    /**
     * Send player stats to the API
     */
    private void sendStatsToAPI(List<JsonObject> stats) {
        try {
            JsonArray statsArray = new JsonArray();
            for (JsonObject stat : stats) {
                statsArray.add(stat);
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STATS_API_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + BOT_SECRET)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(statsArray)))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                NomadLife.LOGGER.info("✓ Player stats synced to website successfully!");
            } else {
                NomadLife.LOGGER.error("Failed to sync stats: {} - {}", 
                    response.statusCode(), response.body());
            }
            
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to send stats to API", e);
        }
    }
}
