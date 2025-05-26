package world.landfall.verbatim.discord;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.server.ServerLifecycleHooks;
import world.landfall.verbatim.Verbatim;
import world.landfall.verbatim.ChatFormattingUtils;
import net.minecraft.ChatFormatting;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

public class DiscordListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!DiscordBot.isEnabled()) return;

        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        String configuredChannelId = world.landfall.verbatim.VerbatimConfig.DISCORD_CHANNEL_ID.get();
        if (!event.getChannel().getId().equals(configuredChannelId)) {
            return;
        }

        String originalMessageContent = event.getMessage().getContentDisplay();
        if (originalMessageContent.trim().isEmpty()) {
            return;
        }

        String authorName;
        Member member = event.getMember();
        String nickname = member != null ? member.getNickname() : null;
        if (nickname != null && !nickname.isEmpty()) {
            authorName = nickname;
        } else {
            authorName = author.getName();
        }

        String prefixStr = DiscordBot.getDiscordMessagePrefix(); 
        if (prefixStr == null) prefixStr = "";
        
        String separatorStr = DiscordBot.getDiscordMessageSeparator();
        if (separatorStr == null) separatorStr = ": "; 

        MutableComponent finalMessage = Component.empty();
        int currentLength = 0;
        final int MAX_LENGTH = 256;
        final String TRUNCATION_MARKER = "...";
        final int TRUNCATION_MARKER_LEN = TRUNCATION_MARKER.length();

        if (!prefixStr.isEmpty()) {
            Component prefixComponent = ChatFormattingUtils.parseColors(prefixStr + " ");
            finalMessage.append(prefixComponent);
            currentLength += ChatFormattingUtils.stripFormattingCodes(prefixComponent.getString()).length();
        }
        
        Component authorComponent = Component.literal(authorName);
        finalMessage.append(authorComponent);
        currentLength += authorName.length(); 
        
        Component separatorComponent = ChatFormattingUtils.parseColors(separatorStr);
        finalMessage.append(separatorComponent);
        currentLength += ChatFormattingUtils.stripFormattingCodes(separatorComponent.getString()).length();
        
        int maxContentLength = MAX_LENGTH - currentLength - TRUNCATION_MARKER_LEN;
        String messageContentToAppend = originalMessageContent;

        if (originalMessageContent.length() > maxContentLength) {
            if (maxContentLength > 0) {
                messageContentToAppend = originalMessageContent.substring(0, maxContentLength);
                finalMessage.append(Component.literal(messageContentToAppend));
                finalMessage.append(Component.literal(TRUNCATION_MARKER).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                if (MAX_LENGTH - currentLength >= TRUNCATION_MARKER_LEN) {
                    finalMessage.append(Component.literal(TRUNCATION_MARKER).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else {
            finalMessage.append(Component.literal(originalMessageContent));
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(finalMessage, false);
            Verbatim.LOGGER.debug("[Discord -> Game] {} ({}) relayed to game chat.", authorName, author.getId());
        } else {
            Verbatim.LOGGER.warn("[Verbatim Discord] MinecraftServer instance is null, cannot send message to game.");
        }
    }

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!DiscordBot.isEnabled()) {
            event.reply("The Discord bot integration is currently disabled.").setEphemeral(true).queue();
            return;
        }

        if (event.getName().equals("list")) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                event.reply("Could not connect to the Minecraft server to fetch the player list.").setEphemeral(true).queue();
                return;
            }

            PlayerList mcPlayerList = server.getPlayerList();
            List<ServerPlayer> onlinePlayers = mcPlayerList.getPlayers();

            if (onlinePlayers.isEmpty()) {
                event.reply("There are no players currently online on the Minecraft server.").setEphemeral(true).queue();
                return;
            }

            String playerListString = onlinePlayers.stream()
                .map(player -> {
                    String username = player.getName().getString();
                    String strippedDisplayName = ChatFormattingUtils.stripFormattingCodes(player.getDisplayName().getString());
                    if (!username.equals(strippedDisplayName)) {
                        return strippedDisplayName + " (" + username + ")";
                    }
                    return username;
                })
                .collect(Collectors.joining("\n- ", "**Online Players (" + onlinePlayers.size() + "):**\n- ", ""));
            
            if (playerListString.length() > 1990) {
                playerListString = playerListString.substring(0, 1990) + "... (list truncated)";
            }

            event.reply(playerListString).setEphemeral(true).queue();
        }
    }
}