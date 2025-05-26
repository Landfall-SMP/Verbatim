package world.landfall.verbatim.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import world.landfall.verbatim.Verbatim;
import world.landfall.verbatim.VerbatimConfig;

import javax.security.auth.login.LoginException;

public class DiscordBot {

    private static JDA jdaInstance;
    private static String discordChannelId;
    private static String discordPrefix;
    private static String discordSeparator;
    private static boolean enabled;

    public static void init() {
        enabled = VerbatimConfig.DISCORD_BOT_ENABLED.get();
        if (!enabled) {
            Verbatim.LOGGER.info("[Verbatim Discord] Bot is disabled in config.");
            return;
        }

        String botToken = VerbatimConfig.DISCORD_BOT_TOKEN.get();
        discordChannelId = VerbatimConfig.DISCORD_CHANNEL_ID.get();
        discordPrefix = VerbatimConfig.DISCORD_MESSAGE_PREFIX.get();
        discordSeparator = VerbatimConfig.DISCORD_MESSAGE_SEPARATOR.get();

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

    private static void sendToDiscordInternal(String message, boolean applyPrefix) {
        if (jdaInstance == null || discordChannelId == null || !enabled) {
            return;
        }
        try {
            TextChannel channel = jdaInstance.getTextChannelById(discordChannelId);
            if (channel != null) {
                String finalMessage = message;
                channel.sendMessage(finalMessage).queue();
                if (!message.contains("Verbatim mod connected") && !message.contains("Verbatim mod disconnecting")) {
                    Verbatim.LOGGER.info("[Game -> Discord] Relayed: {}", finalMessage);
                }
            } else {
                Verbatim.LOGGER.warn("[Verbatim Discord] Configured Discord channel ID '{}' not found.", discordChannelId);
            }
        } catch (Exception e) {
            Verbatim.LOGGER.error("[Verbatim Discord] Could not send message to Discord.", e);
        }
    }

    public static void sendToDiscord(String message) {
        sendToDiscordInternal(message, true);
    }

    public static String getDiscordMessagePrefix() {
        return discordPrefix;
    }

    public static String getDiscordMessageSeparator() {
        return discordSeparator;
    }

    public static boolean isEnabled() {
        return enabled && jdaInstance != null;
    }
} 