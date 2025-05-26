package world.landfall.verbatim.discord;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import world.landfall.verbatim.Verbatim;
import world.landfall.verbatim.ChatFormattingUtils;

public class DiscordListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!DiscordBot.isEnabled()) return;

        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        String configuredChannelId = world.landfall.verbatim.VerbatimConfig.DISCORD_CHANNEL_ID.get();
        if (!event.getChannel().getId().equals(configuredChannelId)) {
            return;
        }

        String messageContent = event.getMessage().getContentDisplay();
        if (messageContent.trim().isEmpty()) {
            return;
        }

        String authorName;
        Member member = event.getMember(); // Get member object to access nickname
        if (member != null && member.getNickname() != null && !member.getNickname().isEmpty()) {
            authorName = member.getNickname();
        } else {
            authorName = author.getName(); // Fallback to global username
        }

        String prefixStr = DiscordBot.getDiscordMessagePrefix(); 
        if (prefixStr == null) prefixStr = "";
        
        String separatorStr = DiscordBot.getDiscordMessageSeparator();
        if (separatorStr == null) separatorStr = ": "; 

        MutableComponent finalMessage = Component.empty();
        
        if (!prefixStr.isEmpty()) {
            finalMessage.append(ChatFormattingUtils.parseColors(prefixStr + " "));
        }
        
        finalMessage.append(Component.literal(authorName)); // Use effective name
        
        finalMessage.append(ChatFormattingUtils.parseColors(separatorStr)); 
        
        finalMessage.append(Component.literal(messageContent));

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(finalMessage, false);
            Verbatim.LOGGER.info("[Discord -> Game] {} ({}) relayed to game chat.", authorName, author.getId());
        } else {
            Verbatim.LOGGER.warn("[Verbatim Discord] MinecraftServer instance is null, cannot send message to game.");
        }
    }
}