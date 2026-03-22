package io.qzz.nomadlife;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NomadLife implements ModInitializer {
    public static final String MOD_ID = "nomadlife";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Version bumped from old version by 0.1
    public static final String VERSION = "1.1.0";
    
    private static TeamDataManager teamDataManager;
    private static PlayerStatsManager playerStatsManager;
    private static ScheduledExecutorService executor;
    
    // Server mode detection (creative server = port 11198)
    private static boolean isCreativeServer = false;
    
    @Override
    public void onInitialize() {
        LOGGER.info("NomadLife mod v{} initializing...", VERSION);
        
        // Initialize managers
        teamDataManager = new TeamDataManager();
        playerStatsManager = new PlayerStatsManager();
        
        // Register server transfer commands (will check server mode at runtime)
        ServerTransferCommands.register();
        
        // Register events after server starts so we can detect server mode
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        
        // Cleanup when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                LOGGER.info("Data sync tasks stopped");
            }
        });
        
        LOGGER.info("NomadLife mod v{} initialized!", VERSION);
    }
    
    private void onServerStarted(MinecraftServer server) {
        // Detect server mode based on port
        int serverPort = server.getServerPort();
        isCreativeServer = (serverPort == 11198);
        
        if (isCreativeServer) {
            LOGGER.info("Running in CREATIVE server mode - Teams and stats disabled, auto creative mode enabled");
            
            // Set all players to creative mode on join
            ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
                ServerPlayerEntity player = handler.getPlayer();
                player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                LOGGER.info("Set {} to creative mode", player.getName().getString());
            });
            
            // Don't register team/stats features or join/leave messages
            return;
        }
        
        LOGGER.info("Running in ANARCHY server mode - All features enabled");
        
        // Register placeholders (only for anarchy server)
        TeamPlaceholders.register();
        
        // Register player join/leave events (only for anarchy server)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayerEntity player = handler.getPlayer();
            broadcastJoinMessage(player, s);
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayerEntity player = handler.getPlayer();
            broadcastLeaveMessage(player, s);
        });
        
        // Start background sync tasks
        LOGGER.info("Starting data sync tasks...");
        
        // Set server for stats manager
        playerStatsManager.setServer(server);
        
        // Initial syncs
        teamDataManager.syncData();
        playerStatsManager.syncStats();
        
        // Schedule periodic syncs
        executor = Executors.newSingleThreadScheduledExecutor();
        
        // Sync teams every 30 seconds
        executor.scheduleAtFixedRate(() -> {
            try {
                teamDataManager.syncData();
            } catch (Exception e) {
                LOGGER.error("Failed to sync team data", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        // Sync player stats every 5 minutes
        executor.scheduleAtFixedRate(() -> {
            try {
                playerStatsManager.syncStats();
            } catch (Exception e) {
                LOGGER.error("Failed to sync player stats", e);
            }
        }, 300, 300, TimeUnit.SECONDS);
    }
    
    private void broadcastJoinMessage(ServerPlayerEntity player, MinecraftServer server) {
        String username = player.getName().getString();
        String teamPrefix = teamDataManager.getTeamPrefix(username);
        String discordId = teamDataManager.getDiscordId(username);
        
        // If we have their Discord ID, include the ping
        String discordPing = (discordId != null) ? "<@" + discordId + ">" : username;
        
        String message = String.format(
            "§8[§6⚔§8] §7%s §a%s%s §7just stepped into the §c§lNomadLife§7 game! §8§7Let's reign!",
            discordPing,
            teamPrefix.isEmpty() ? "" : teamPrefix + " §a",
            username
        );
        
        server.getPlayerManager().broadcast(
            Text.literal(message),
            false
        );
        
        LOGGER.info("{} joined the server", username);
    }
    
    private void broadcastLeaveMessage(ServerPlayerEntity player, MinecraftServer server) {
        String username = player.getName().getString();
        String teamPrefix = teamDataManager.getTeamPrefix(username);
        String discordId = teamDataManager.getDiscordId(username);
        
        // If we have their Discord ID, include the ping
        String discordPing = (discordId != null) ? "<@" + discordId + ">" : username;
        
        String message = String.format(
            "See you soon! §8[§6⚔§8] §7%s §c%s%s §7 has §8§o quit the game.§7",
            discordPing,
            teamPrefix.isEmpty() ? "" : teamPrefix + " §c",
            username
        );
        
        server.getPlayerManager().broadcast(
            Text.literal(message),
            false
        );
        
        LOGGER.info("{} left the server", username);
    }
    
    public static TeamDataManager getTeamDataManager() {
        return teamDataManager;
    }
    
    public static PlayerStatsManager getPlayerStatsManager() {
        return playerStatsManager;
    }
    
    public static boolean isCreativeServer() {
        return isCreativeServer;
    }
}