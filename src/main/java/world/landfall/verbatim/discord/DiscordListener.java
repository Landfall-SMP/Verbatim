package world.landfall.verbatim.discord;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import world.landfall.verbatim.Verbatim;
import world.landfall.verbatim.VerbatimConfig;
import world.landfall.verbatim.ChatFormattingUtils;

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

        // Get content that properly formats emojis
        String originalMessageContent = event.getMessage().getContentRaw()
            .replace("[", "\\[")
            .replace("]", "\\]");
        
        // Handle custom emojis - replace with their names
        String processedContent = originalMessageContent;
        for (CustomEmoji emoji : event.getMessage().getMentions().getCustomEmojis()) {
            String emojiMention = emoji.getAsMention();
            String emojiName = ":" + emoji.getName() + ":";
            processedContent = processedContent.replace(emojiMention, emojiName);
        }
        
        if (processedContent.trim().isEmpty() && event.getMessage().getAttachments().isEmpty()) {
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
        
        finalMessage.append(Component.literal(authorName));
        currentLength += authorName.length();
        
        finalMessage.append(ChatFormattingUtils.parseColors(separatorStr));
        currentLength += ChatFormattingUtils.stripFormattingCodes(separatorStr).length();
        
        // Calculate remaining length for content
        int remainingLength = MAX_LENGTH - currentLength - TRUNCATION_MARKER_LEN;
        
        // Handle message content first
        String contentStr = processedContent.trim();
        if (!contentStr.isEmpty()) {
            if (contentStr.length() > remainingLength) {
                contentStr = contentStr.substring(0, remainingLength);
                finalMessage.append(Component.literal(contentStr));
                finalMessage.append(Component.literal(TRUNCATION_MARKER).withStyle(ChatFormatting.DARK_GRAY));
                remainingLength = 0;
            } else {
                finalMessage.append(Component.literal(contentStr));
                remainingLength -= contentStr.length();
                if (!event.getMessage().getAttachments().isEmpty() && remainingLength > 1) {
                    finalMessage.append(Component.literal(" "));
                    remainingLength--;
                }
            }
        }
        
        // Handle attachments if we have room
        if (remainingLength > 0) {
            List<Message.Attachment> attachments = event.getMessage().getAttachments();
            for (Message.Attachment attachment : attachments) {
                String fileName = attachment.getFileName();
                String proxyUrl = attachment.getProxyUrl();
                
                // Check if we have room for this attachment (+2 for brackets)
                if (fileName.length() + 2 > remainingLength) {
                    finalMessage.append(Component.literal(TRUNCATION_MARKER).withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                
                MutableComponent attachmentComponent = Component.literal("[" + fileName + "]")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.DARK_GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, proxyUrl))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            Component.literal("Click to open " + fileName))));
                
                finalMessage.append(attachmentComponent);
                remainingLength -= (fileName.length() + 2);
                
                // Add space if we have more attachments and room
                if (attachments.indexOf(attachment) < attachments.size() - 1 && remainingLength > 1) {
                    finalMessage.append(Component.literal(" "));
                    remainingLength--;
                }
            }
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