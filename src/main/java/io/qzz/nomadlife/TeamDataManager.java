package io.qzz.nomadlife;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamDataManager {
    private static final String TEAMS_API_URL = "https://www.nomadlife.qzz.io/api/teams";
    private static final String PLAYERS_API_URL = "https://www.nomadlife.qzz.io/api/player-mappings";
    
    private final HttpClient httpClient;
    private final Gson gson;
    
    // Cache: Minecraft username -> team prefix
    private final Map<String, String> playerTeamPrefixes = new ConcurrentHashMap<>();
    
    // Cache: Minecraft username -> team name
    private final Map<String, String> playerTeamNames = new ConcurrentHashMap<>();
    
    // Cache: Discord ID -> Minecraft username
    private final Map<String, String> discordToMinecraft = new ConcurrentHashMap<>();
    
    // Cache: Minecraft username -> Discord ID (reverse lookup)
    private final Map<String, String> minecraftToDiscord = new ConcurrentHashMap<>();
    
    public TeamDataManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Sync team and player data from the API
     */
    public void syncData() {
        try {
            // First, fetch player mappings (Discord ID -> MC username)
            fetchPlayerMappings();
            
            // Then, fetch teams and match them with players
            fetchTeams();
            
            NomadLife.LOGGER.debug("Team data synced: {} players mapped to teams", 
                playerTeamPrefixes.size());
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to sync team data", e);
        }
    }
    
    /**
     * Fetch player mappings from API
     */
    private void fetchPlayerMappings() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PLAYERS_API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                
                if (json.get("success").getAsBoolean()) {
                    JsonArray mappings = json.getAsJsonArray("mappings");
                    
                    discordToMinecraft.clear();
                    minecraftToDiscord.clear();
                    for (JsonElement element : mappings) {
                        JsonObject mapping = element.getAsJsonObject();
                        String discordId = mapping.get("discordId").getAsString();
                        String minecraftName = mapping.get("java").getAsString();
                        
                        discordToMinecraft.put(discordId, minecraftName);
                        minecraftToDiscord.put(minecraftName, discordId);
                    }
                    
                    NomadLife.LOGGER.info("Loaded {} player mappings", discordToMinecraft.size());
                }
            } else {
                NomadLife.LOGGER.warn("Player mappings API returned status: {}", response.statusCode());
            }
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to fetch player mappings", e);
        }
    }
    
    /**
     * Fetch teams from API and map players to their teams
     */
    private void fetchTeams() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TEAMS_API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                
                if (json.get("success").getAsBoolean()) {
                    JsonArray teams = json.getAsJsonArray("teams");
                    
                    // Clear old data
                    playerTeamPrefixes.clear();
                    playerTeamNames.clear();
                    
                    // Process each team
                    for (JsonElement element : teams) {
                        JsonObject team = element.getAsJsonObject();
                        String teamName = team.get("name").getAsString();
                        
                        // Generate team prefix (first 3 letters, uppercase)
                        String teamPrefix = generateTeamPrefix(teamName);
                        
                        NomadLife.LOGGER.debug("Processing team: {} [{}]", teamName, teamPrefix);
                        
                        // Get team members
                        JsonArray members = team.getAsJsonArray("members");
                        
                        for (JsonElement memberElement : members) {
                            JsonObject member = memberElement.getAsJsonObject();
                            String discordId = member.get("id").getAsString();
                            
                            // Look up their Minecraft username
                            String minecraftName = discordToMinecraft.get(discordId);
                            
                            if (minecraftName != null) {
                                playerTeamPrefixes.put(minecraftName, teamPrefix);
                                playerTeamNames.put(minecraftName, teamName);
                                NomadLife.LOGGER.debug("  Mapped player {} to team {} [{}]", 
                                    minecraftName, teamName, teamPrefix);
                            } else {
                                NomadLife.LOGGER.debug("  No Minecraft username found for Discord ID: {}", discordId);
                            }
                        }
                    }
                    
                    NomadLife.LOGGER.debug("Processed {} teams with {} total players", 
                        teams.size(), playerTeamPrefixes.size());
                }
            } else {
                NomadLife.LOGGER.warn("Teams API returned status: {}", response.statusCode());
            }
        } catch (Exception e) {
            NomadLife.LOGGER.error("Failed to fetch teams", e);
        }
    }
    
    /**
     * Generate a team prefix from the team name
     * Takes first 3 letters, uppercase, wrapped in brackets
     */
    private String generateTeamPrefix(String teamName) {
        if (teamName == null || teamName.isEmpty()) {
            return "[???]";
        }
        
        // Remove special characters and spaces
        String cleaned = teamName.replaceAll("[^a-zA-Z0-9]", "");
        
        if (cleaned.length() >= 3) {
            return "[" + cleaned.substring(0, 3).toUpperCase() + "]";
        } else if (cleaned.length() > 0) {
            return "[" + cleaned.toUpperCase() + "]";
        } else {
            // Fallback: use first 3 chars of original name
            return "[" + teamName.substring(0, Math.min(3, teamName.length())).toUpperCase() + "]";
        }
    }
    
    /**
     * Get the team prefix for a player
     * @param minecraftUsername The player's Minecraft username
     * @return The team prefix, or empty string if not in a team
     */
    public String getTeamPrefix(String minecraftUsername) {
        return playerTeamPrefixes.getOrDefault(minecraftUsername, "");
    }
    
    /**
     * Get the team name for a player
     * @param minecraftUsername The player's Minecraft username
     * @return The team name, or empty string if not in a team
     */
    public String getTeamName(String minecraftUsername) {
        return playerTeamNames.getOrDefault(minecraftUsername, "");
    }
    
    /**
     * Check if a player is in a team
     * @param minecraftUsername The player's Minecraft username
     * @return true if player is in a team
     */
    public boolean hasTeam(String minecraftUsername) {
        return playerTeamPrefixes.containsKey(minecraftUsername);
    }
    
    /**
     * Get the Discord ID for a player
     * @param minecraftUsername The player's Minecraft username
     * @return The Discord ID, or null if not found
     */
    public String getDiscordId(String minecraftUsername) {
        return minecraftToDiscord.get(minecraftUsername);
    }
}
