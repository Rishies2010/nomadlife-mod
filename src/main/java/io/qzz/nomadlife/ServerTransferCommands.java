package io.qzz.nomadlife;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class ServerTransferCommands {
    
    private static final String ANARCHY_IP = "nl-01.freezehost.pro";
    private static final int ANARCHY_PORT = 8611;
    
    private static final String CREATIVE_IP = "nl-01.freezehost.pro";
    private static final int CREATIVE_PORT = 11198;
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
        NomadLife.LOGGER.info("Registered server transfer commands: /anarchy, /creative");
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Register /anarchy command
        dispatcher.register(literal("anarchy")
            .executes(context -> {
                ServerCommandSource source = context.getSource();
                try {
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    String name = player.getName().getString();

                    player.sendMessage(Text.literal("§6⚡ §7Transferring you to §c§lAnarchy§7 server..."), false);
                    
                    // console source with silent enabled
                    ServerCommandSource console = source.getServer().getCommandSource().withSilent();
                    var cmdDispatcher = source.getServer().getCommandManager().getDispatcher();

                    // 1. OP
                    cmdDispatcher.execute("op " + name, console);
                    
                    // 2. TRANSFER
                    cmdDispatcher.execute("transfer " + ANARCHY_IP + " " + ANARCHY_PORT, player.getCommandSource().withSilent());
                    
                    // 3. DEOP
                    cmdDispatcher.execute("deop " + name, console);

                    return 1;
                } catch (Exception e) {
                    source.sendError(Text.literal("§cFailed: " + e.getMessage()));
                    return 0;
                }
            })
        );
        
        // Register /creative command
        dispatcher.register(literal("creative")
            .executes(context -> {
                ServerCommandSource source = context.getSource();
                if (NomadLife.isCreativeServer()) {
                    source.sendError(Text.literal("§cThis command is not available on the creative server!"));
                    return 0;
                }
                try {
                    ServerPlayerEntity player = source.getPlayerOrThrow();
                    String name = player.getName().getString();

                    player.sendMessage(Text.literal("§6⚡ §7Transferring you to §b§lCreative§7 server..."), false);
                    
                    ServerCommandSource console = source.getServer().getCommandSource().withSilent();
                    var cmdDispatcher = source.getServer().getCommandManager().getDispatcher();

                    // 1. OP
                    cmdDispatcher.execute("op " + name, console);
                    
                    // 2. TRANSFER
                    cmdDispatcher.execute("transfer " + CREATIVE_IP + " " + CREATIVE_PORT, player.getCommandSource().withSilent());
                    
                    // 3. DEOP
                    cmdDispatcher.execute("deop " + name, console);

                    return 1;
                } catch (Exception e) {
                    source.sendError(Text.literal("§cFailed: " + e.getMessage()));
                    return 0;
                }
            })
        );
    }
}