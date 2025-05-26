package world.landfall.verbatim.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import world.landfall.verbatim.Verbatim;
import world.landfall.verbatim.VerbatimConfig;
import world.landfall.verbatim.ChatFormattingUtils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordBot {

    private static final Map<UUID, Color> SPECIAL_UUID_COLORS = new HashMap<>();

    static {
        SPECIAL_UUID_COLORS.put(UUID.fromString("7755ac32-2fba-4ef6-a85b-93c354267a91"), new Color(155, 89, 182)); // cant blame a gal for liking purple ;)
    }

    private static JDA jdaInstance;
    private static String discordChannelId;
    private static boolean enabled;
    private static boolean useEmbedMode;
    private static ScheduledExecutorService presenceScheduler;

    public static void init() {
        enabled = VerbatimConfig.DISCORD_BOT_ENABLED.get();
        useEmbedMode = VerbatimConfig.DISCORD_USE_EMBED_MODE.get();
        if (!enabled) {
            Verbatim.LOGGER.info("[Verbatim Discord] Bot is disabled in config.");
            return;
        }

        String botToken = VerbatimConfig.DISCORD_BOT_TOKEN.get();
        discordChannelId = VerbatimConfig.DISCORD_CHANNEL_ID.get();

        if (botToken == null || botToken.isEmpty() || botToken.equals("YOUR_DISCORD_BOT_TOKEN_HERE")) {
            Verbatim.LOGGER.error("[Verbatim Discord] Bot token is not configured. Discord bot will not start.");
            return;
        }
        if (discordChannelId == null || discordChannelId.isEmpty() || discordChannelId.equals("YOUR_DISCORD_CHANNEL_ID_HERE")) {
            Verbatim.LOGGER.error("[Verbatim Discord] Discord channel ID is not configured. Discord bot will not start.");
            return;
        }

        try {
            jdaInstance = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .addEventListeners(new DiscordListener())
                    .build();
            jdaInstance.awaitReady();
            Verbatim.LOGGER.info("[Verbatim Discord] Bot connected and ready!");

            // Register Discord slash commands
            jdaInstance.upsertCommand("list", "Lists online players on the Minecraft server.").queue();
            Verbatim.LOGGER.info("[Verbatim Discord] /list slash command registered/updated.");

            // Initialize and start presence scheduler
            updatePlayerCountStatus(); // Set initial status
            presenceScheduler = Executors.newSingleThreadScheduledExecutor();
            presenceScheduler.scheduleAtFixedRate(DiscordBot::updatePlayerCountStatus, 1, 1, TimeUnit.MINUTES);
            Verbatim.LOGGER.info("[Verbatim Discord] Presence update scheduler started.");

        } catch (InterruptedException e) {
            Verbatim.LOGGER.error("[Verbatim Discord] JDA initialization was interrupted.", e);
            Thread.currentThread().interrupt();
            jdaInstance = null;
        } catch (Exception e) {
            Verbatim.LOGGER.error("[Verbatim Discord] Failed to initialize JDA or log in.", e);
            jdaInstance = null;
        }
    }

    public static void shutdown() {
        if (presenceScheduler != null && !presenceScheduler.isShutdown()) {
            presenceScheduler.shutdown();
            try {
                if (!presenceScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    presenceScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                presenceScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            Verbatim.LOGGER.info("[Verbatim Discord] Presence update scheduler shut down.");
        }
        if (jdaInstance != null) {
            Verbatim.LOGGER.info("[Verbatim Discord] Shutting down Discord bot...");
            jdaInstance.shutdown();
            try {
                if (!jdaInstance.awaitShutdown(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    Verbatim.LOGGER.warn("[Verbatim Discord] Bot did not shut down in 10 seconds, forcing shutdown.");
                    jdaInstance.shutdownNow();
                }
            } catch (InterruptedException e) {
                Verbatim.LOGGER.error("[Verbatim Discord] Interrupted while awaiting bot shutdown.", e);
                jdaInstance.shutdownNow();
                Thread.currentThread().interrupt();
            }
            Verbatim.LOGGER.info("[Verbatim Discord] Bot has been shut down.");
            jdaInstance = null;
        }
    }

    public static void sendToDiscord(String message) {
        if (jdaInstance == null || discordChannelId == null || !enabled) {
            return;
        }
        try {
            TextChannel channel = jdaInstance.getTextChannelById(discordChannelId);
            if (channel != null) {
                channel.sendMessage(message).queue();
                Verbatim.LOGGER.debug("[Verbatim Discord Generic] Relayed: {}", message);
            } else {
                Verbatim.LOGGER.warn("[Verbatim Discord] Configured Discord channel ID '{}' not found for generic message.", discordChannelId);
            }
        } catch (Exception e) {
            Verbatim.LOGGER.error("[Verbatim Discord] Could not send generic message to Discord.", e);
        }
    }

    private static Color generateColorFromUUID(UUID uuid) {
        // Check for the special UUID in the map first
        if (SPECIAL_UUID_COLORS.containsKey(uuid)) {
            return SPECIAL_UUID_COLORS.get(uuid);
        }
        
        // Existing HSB-based generation for all other UUIDs
        int hash = uuid.hashCode();
        float hue = (Math.abs(hash) % 360) / 360.0f;
        return Color.getHSBColor(hue, 0.7f, 0.85f);
    }

    public static void sendPlayerChatMessageToDiscord(ServerPlayer player, String messageContent) {
        if (!isEnabled()) {
            return;
        }

        try {
            TextChannel channel = jdaInstance.getTextChannelById(discordChannelId);
            if (channel == null) {
                Verbatim.LOGGER.warn("[Verbatim Discord] Configured Discord channel ID '{}' not found for player message.", discordChannelId);
                return;
            }

            String username = player.getName().getString();
            String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
            String cleanMessageContent = ChatFormattingUtils.stripFormattingCodes(messageContent);

            if (useEmbedMode) {
                String playerUUID = player.getUUID().toString().replace("-", "");
                String avatarUrl = "https://minotar.net/avatar/" + playerUUID;
                
                String authorName = username;
                if (!username.equals(strippedDisplayName)) {
                    authorName = strippedDisplayName + " (" + username + ")";
                }

                EmbedBuilder embed = new EmbedBuilder();
                embed.setAuthor(authorName, null, avatarUrl);
                embed.setDescription(cleanMessageContent);
                embed.setColor(generateColorFromUUID(player.getUUID()));

                channel.sendMessageEmbeds(embed.build()).queue();
                Verbatim.LOGGER.debug("[Game -> Discord Embed] Relayed for {}: {}", authorName, cleanMessageContent);
            } else {
                String plainTextMessage = username + ": " + cleanMessageContent;
                channel.sendMessage(plainTextMessage).queue();
                Verbatim.LOGGER.debug("[Game -> Discord Plain] Relayed for {}: {}", username, cleanMessageContent);
            }
        } catch (Exception e) {
            Verbatim.LOGGER.error("[Verbatim Discord] Could not send player chat message to Discord for {}.", player.getName().getString(), e);
        }
    }

    public static void sendPlayerConnectionStatusToDiscord(ServerPlayer player, boolean joined) {
        if (!isEnabled()) {
            return;
        }

        try {
            TextChannel channel = jdaInstance.getTextChannelById(discordChannelId);
            if (channel == null) {
                Verbatim.LOGGER.warn("[Verbatim Discord] Configured Discord channel ID '{}' not found for connection status.", discordChannelId);
                return;
            }
            
            String username = player.getName().getString();
            String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
            String effectiveName = username;
            if (!username.equals(strippedDisplayName)) {
                effectiveName = strippedDisplayName + " (" + username + ")";
            }

            if (useEmbedMode) {
                String playerUUID = player.getUUID().toString().replace("-", "");
                String avatarUrl = "https://minotar.net/avatar/" + playerUUID + "/100";

                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(joined ? new Color(0x4CAF50) : new Color(0xF44336));
                embed.setAuthor(effectiveName + (joined ? " has joined the server." : " has left the server."), null, avatarUrl);
                
                channel.sendMessageEmbeds(embed.build()).queue();
                Verbatim.LOGGER.debug("[Game -> Discord Embed] Connection Status: {} {}", effectiveName, (joined ? "joined" : "left"));
            } else {
                String statusEmoji = joined ? "➕" : "➖";
                String plainTextMessage = statusEmoji + " " + username + " has " + (joined ? "joined" : "left") + " the server.";
                channel.sendMessage(plainTextMessage).queue();
                Verbatim.LOGGER.debug("[Game -> Discord Plain] Connection Status: {} {}", username, (joined ? "joined" : "left"));
            }
            // Update presence immediately after join/leave event
            updatePlayerCountStatus();

        } catch (Exception e) {
            Verbatim.LOGGER.error("[Verbatim Discord] Could not send player connection status to Discord for {}.", player.getName().getString(), e);
        }
    }

    public static String getDiscordMessagePrefix() {
        return VerbatimConfig.DISCORD_MESSAGE_PREFIX.get();
    }

    public static String getDiscordMessageSeparator() {
        return VerbatimConfig.DISCORD_MESSAGE_SEPARATOR.get();
    }

    public static boolean isEnabled() {
        return enabled && jdaInstance != null;
    }

    public static void updatePlayerCountStatus() {
        if (jdaInstance == null || !jdaInstance.getStatus().isInit()) {
            return; // JDA not ready
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        String statusMessage;
        if (server != null) {
            int playerCount = server.getPlayerList().getPlayerCount();
            if (playerCount == 1) {
                statusMessage = "1 player online";
            } else {
                statusMessage = playerCount + " players online";
            }
        } else {
            statusMessage = "Server Offline";
        }
        
        try {
            jdaInstance.getPresence().setActivity(Activity.watching(statusMessage));
        } catch (Exception e) {
            Verbatim.LOGGER.warn("[Verbatim Discord] Could not update bot presence: {}", e.getMessage());
        }
    }
} 