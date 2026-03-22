package io.qzz.nomadlife;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.util.Identifier;

public class TeamPlaceholders {
    
    /**
     * Register all team-related placeholders
     */
    public static void register() {
        // Register %nomadlife:team_prefix% placeholder
        Placeholders.register(
            Identifier.of("nomadlife", "team_prefix"),
            (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No player!");
                }
                
                String playerName = ctx.player().getName().getString();
                TeamDataManager manager = NomadLife.getTeamDataManager();
                
                if (manager != null) {
                    String prefix = manager.getTeamPrefix(playerName);
                    if (prefix != null && !prefix.isEmpty()) {
                        return PlaceholderResult.value(prefix + " ");
                    }
                }
                
                return PlaceholderResult.value("");
            }
        );
        
        // Register %nomadlife:team_name% placeholder
        Placeholders.register(
            Identifier.of("nomadlife", "team_name"),
            (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No player!");
                }
                
                String playerName = ctx.player().getName().getString();
                TeamDataManager manager = NomadLife.getTeamDataManager();
                
                if (manager != null) {
                    String teamName = manager.getTeamName(playerName);
                    if (teamName != null && !teamName.isEmpty()) {
                        return PlaceholderResult.value(teamName);
                    }
                }
                
                return PlaceholderResult.value("No Team");
            }
        );
        
        // Register %nomadlife:has_team% placeholder
        Placeholders.register(
            Identifier.of("nomadlife", "has_team"),
            (ctx, arg) -> {
                if (!ctx.hasPlayer()) {
                    return PlaceholderResult.invalid("No player!");
                }
                
                String playerName = ctx.player().getName().getString();
                TeamDataManager manager = NomadLife.getTeamDataManager();
                
                if (manager != null) {
                    boolean hasTeam = manager.hasTeam(playerName);
                    return PlaceholderResult.value(hasTeam ? "true" : "false");
                }
                
                return PlaceholderResult.value("false");
            }
        );
        
        NomadLife.LOGGER.info("Registered team placeholders: team_prefix, team_name, has_team");
    }
}