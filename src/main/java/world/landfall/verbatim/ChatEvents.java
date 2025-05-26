package world.landfall.verbatim;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import world.landfall.verbatim.specialchannels.FormattedMessageDetails;
import world.landfall.verbatim.specialchannels.LocalChannelFormatter;
import world.landfall.verbatim.discord.DiscordBot;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Mod.EventBusSubscriber(modid = Verbatim.MODID)
public class ChatEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatChannelManager.playerLoggedIn(player); // Handles loading saved state, joining alwaysOn, permission checks for saved
            
            if (DiscordBot.isEnabled()) {
                String playerName = player.getName().getString();
                DiscordBot.sendToDiscord("**" + playerName + " has joined the server.**");
            }

            // Show current focus (channel or DM)
            Optional<ChatChannelManager.FocusTarget> focusOpt = ChatChannelManager.getFocus(player);
            focusOpt.ifPresent(focus -> {
                if (focus instanceof ChatChannelManager.ChannelFocus) {
                    ChatChannelManager.ChannelConfig config = ChatChannelManager.getChannelConfigByName(((ChatChannelManager.ChannelFocus) focus).channelName).orElse(null);
                    if (config != null) {
                        player.sendSystemMessage(Component.literal("🗨 Focused channel: ")
                            .append(ChatFormattingUtils.parseColors(config.displayPrefix))
                            .append(Component.literal(" " + config.name).withStyle(ChatFormatting.YELLOW))
                        );
                    }
                } else if (focus instanceof ChatChannelManager.DmFocus) {
                    player.sendSystemMessage(Component.literal("💬 Focused DM: ")
                        .append(Component.literal(focus.getDisplayName()).withStyle(ChatFormatting.YELLOW))
                    );
                }
            });

            Set<String> joinedChannels = ChatChannelManager.getJoinedChannels(player);
            if (!joinedChannels.isEmpty()) {
                player.sendSystemMessage(Component.literal("📞 Joined channels: ").withStyle(ChatFormatting.GRAY));
                for (String joinedChannelName : joinedChannels) {
                    ChatChannelManager.getChannelConfigByName(joinedChannelName).ifPresent(jc -> {
                         player.sendSystemMessage(Component.literal("  - ")
                            .append(ChatFormattingUtils.parseColors(jc.displayPrefix))
                            .append(Component.literal(" " + jc.name).withStyle(ChatFormatting.DARK_AQUA)));
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (DiscordBot.isEnabled()) {
                String playerName = player.getName().getString();
                DiscordBot.sendToDiscord("**" + playerName + " has left the server.**");
            }
            ChatChannelManager.playerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String rawMessageText = event.getMessage().getString();
        Verbatim.LOGGER.debug("[Verbatim ChatEvent] Raw message from {}: {}", sender.getName().getString(), rawMessageText);
        event.setCanceled(true);

        String messageContent = rawMessageText;
        Optional<ChatChannelManager.FocusTarget> targetFocusOpt = Optional.empty();

        // --- Prefix Processing ---
        if (rawMessageText.contains(":")) {
            String potentialPrefix = rawMessageText.substring(0, rawMessageText.indexOf(":"));
            
            // Handle d: prefix (DM reply)
            if ("d".equals(potentialPrefix)) {
                ChatChannelManager.handleDPrefix(sender);
                messageContent = rawMessageText.substring(rawMessageText.indexOf(":") + 1).trim();
                
                if (messageContent.isEmpty()) {
                    Verbatim.LOGGER.debug("[Verbatim ChatEvent] d: prefix used with no message. Focus changed only.");
                    return; // Only focused, no message to send
                }
                
                // Check if the d: prefix actually resulted in a valid DM focus
                Optional<ChatChannelManager.FocusTarget> currentFocus = ChatChannelManager.getFocus(sender);
                if (currentFocus.isPresent() && currentFocus.get() instanceof ChatChannelManager.DmFocus) {
                    targetFocusOpt = currentFocus;
                } else {
                    // d: prefix failed (no recent DM or player offline), don't send message
                    Verbatim.LOGGER.debug("[Verbatim ChatEvent] d: prefix failed to establish DM focus. Message not sent.");
                    return;
                }
            }
            // Handle g: prefix (global channel)
            else if ("g".equals(potentialPrefix)) {
                ChatChannelManager.ChannelConfig defaultChannel = ChatChannelManager.getDefaultChannelConfig();
                if (defaultChannel != null) {
                    ChatChannelManager.focusChannel(sender, defaultChannel.name);
                    targetFocusOpt = Optional.of(new ChatChannelManager.ChannelFocus(defaultChannel.name));
                    messageContent = rawMessageText.substring(rawMessageText.indexOf(":") + 1).trim();
                    
                    if (messageContent.isEmpty()) {
                        Verbatim.LOGGER.debug("[Verbatim ChatEvent] g: prefix used with no message. Focus changed only.");
                        return; // Only focused, no message to send
                    }
                } else {
                    sender.sendSystemMessage(Component.literal("No default channel configured.").withStyle(ChatFormatting.RED));
                    return;
                }
            }
            // Handle channel shortcuts
            else {
                Optional<ChatChannelManager.ChannelConfig> targetChannelByShortcut = ChatChannelManager.getChannelConfigByShortcut(potentialPrefix);

                if (targetChannelByShortcut.isPresent()) {
                    ChatChannelManager.ChannelConfig prospectiveChannel = targetChannelByShortcut.get();
                    Verbatim.LOGGER.debug("[Verbatim ChatEvent] Shortcut '{}' targets channel: {}. Checking permission...", potentialPrefix, prospectiveChannel.name);
                    
                    // alwaysOn channels bypass permission check for joining/focusing via shortcut
                    if (prospectiveChannel.alwaysOn || Verbatim.permissionService.hasPermission(sender, prospectiveChannel.permission.orElse(null), 2)) {
                        ChatChannelManager.focusChannel(sender, prospectiveChannel.name); // This also joins if not already
                        targetFocusOpt = Optional.of(new ChatChannelManager.ChannelFocus(prospectiveChannel.name));
                        messageContent = rawMessageText.substring(rawMessageText.indexOf(":") + 1).trim();
                        Verbatim.LOGGER.debug("[Verbatim ChatEvent] Shortcut permission GRANTED for '{}'. Player focused. Message content: \"{}\"", prospectiveChannel.name, messageContent);
                        
                        if (messageContent.isEmpty()) {
                            Verbatim.LOGGER.debug("[Verbatim ChatEvent] Message content empty after shortcut processing for '{}'. No message to send.", prospectiveChannel.name);
                            return; // Only focused, no message to send further
                        }
                    } else {
                        Verbatim.LOGGER.debug("[Verbatim ChatEvent] No shortcut permission for channel '{}'. Sending denial.", prospectiveChannel.name);
                        sender.sendSystemMessage(Component.literal("You don't have permission for channel shortcut '")
                            .append(ChatFormattingUtils.parseColors(prospectiveChannel.displayPrefix))
                            .append(Component.literal(" " + prospectiveChannel.name + "'.")).withStyle(ChatFormatting.RED));
                        return; 
                    }
                } else {
                    Verbatim.LOGGER.debug("[Verbatim ChatEvent] No channel found for shortcut: {}", potentialPrefix);
                    // Treat as normal message, not a shortcut failure if no channel matches
                }
            }
        }

        // --- Determine Target Focus for Non-Prefix or Post-Prefix Message ---
        if (targetFocusOpt.isEmpty()) {
            targetFocusOpt = ChatChannelManager.getFocus(sender);
            if (targetFocusOpt.isEmpty()) {
                Verbatim.LOGGER.error("[Verbatim ChatEvent] Player {} has no focus. Attempting to set to default.", sender.getName().getString());
                ChatChannelManager.ChannelConfig defaultChannel = ChatChannelManager.getDefaultChannelConfig();
                if (defaultChannel != null) {
                    ChatChannelManager.focusChannel(sender, defaultChannel.name);
                    targetFocusOpt = Optional.of(new ChatChannelManager.ChannelFocus(defaultChannel.name));
                    sender.sendSystemMessage(Component.literal("You were not focused on anything. Message sent to default: ")
                        .append(ChatFormattingUtils.parseColors(defaultChannel.displayPrefix))
                        .append(Component.literal(" " + defaultChannel.name).withStyle(ChatFormatting.YELLOW)));
                } else {
                    Verbatim.LOGGER.error("[Verbatim ChatEvent] CRITICAL: No default channel to focus for {}. Cannot send message.", sender.getName().getString());
                    sender.sendSystemMessage(Component.literal("Error: No active or default channel. Message not sent.").withStyle(ChatFormatting.RED));
                    return;
                }
            }
        }
        
        ChatChannelManager.FocusTarget finalTarget = targetFocusOpt.get(); // Should be present by now

        // --- Handle DM Messages ---
        if (finalTarget instanceof ChatChannelManager.DmFocus) {
            ChatChannelManager.DmFocus dmFocus = (ChatChannelManager.DmFocus) finalTarget;
            ServerPlayer targetPlayer = ChatChannelManager.getPlayerByUUID(dmFocus.targetPlayerId);
            
            if (targetPlayer == null) {
                sender.sendSystemMessage(Component.literal("Cannot send DM: Target player is not online.").withStyle(ChatFormatting.RED));
                return;
            }
            
            // Update recipient's last incoming DM sender
            ChatChannelManager.setLastIncomingDmSender(targetPlayer, sender.getUUID());
            
            // Format and send DM messages
            MutableComponent senderMessage = Component.literal("[You -> ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("]: ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(messageContent).withStyle(ChatFormatting.WHITE));
                
            MutableComponent recipientMessage = Component.literal("[")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(sender.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" -> You]: ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(messageContent).withStyle(ChatFormatting.WHITE));
            
            sender.sendSystemMessage(senderMessage);
            targetPlayer.sendSystemMessage(recipientMessage);
            
            Verbatim.LOGGER.debug("[Verbatim ChatEvent] DM sent from {} to {}: {}", sender.getName().getString(), targetPlayer.getName().getString(), messageContent);
            return;
        }

        // --- Handle Channel Messages ---
        if (finalTarget instanceof ChatChannelManager.ChannelFocus) {
            ChatChannelManager.ChannelFocus channelFocus = (ChatChannelManager.ChannelFocus) finalTarget;
            Optional<ChatChannelManager.ChannelConfig> channelConfigOpt = ChatChannelManager.getChannelConfigByName(channelFocus.channelName);
            
            if (channelConfigOpt.isEmpty()) {
                sender.sendSystemMessage(Component.literal("Error: Focused channel no longer exists.").withStyle(ChatFormatting.RED));
                return;
            }
            
            ChatChannelManager.ChannelConfig finalTargetChannel = channelConfigOpt.get();

            // --- Permission Check for Sending to Final Target Channel ---
            // alwaysOn channels bypass this specific send permission check
            if (!finalTargetChannel.alwaysOn && !Verbatim.permissionService.hasPermission(sender, finalTargetChannel.permission.orElse(null), 2)) {
                Verbatim.LOGGER.info("[Verbatim ChatEvent] Player {} lost permission to send to target channel '{}'. Auto-leaving & focusing default.", sender.getName().getString(), finalTargetChannel.name);
                ChatChannelManager.autoLeaveChannel(sender, finalTargetChannel.name); // This handles refocusing to default if needed and messages player
                sender.sendSystemMessage(Component.literal("You no longer have permission to send messages in '")
                    .append(ChatFormattingUtils.parseColors(finalTargetChannel.displayPrefix + " " + finalTargetChannel.name))
                    .append(Component.literal("'. Message not sent.")).withStyle(ChatFormatting.RED));
                return;
            }

            Verbatim.LOGGER.debug("[Verbatim ChatEvent] Send permission GRANTED for channel {}. Formatting and sending message.", finalTargetChannel.name);

            // --- Discord Integration ---
            if (DiscordBot.isEnabled() && "global".equals(finalTargetChannel.name)) {
                // Bold the player's name and the colon for Discord
                DiscordBot.sendToDiscord("**" + sender.getName().getString() + ":** " + messageContent);
            }

            // --- Special Channel Processing ---
            Optional<FormattedMessageDetails> specialFormatResult = LocalChannelFormatter.formatLocalMessage(sender, finalTargetChannel, messageContent);
            
            MutableComponent finalMessage;
            int effectiveRange;
            
            if (specialFormatResult.isPresent()) {
                // Special channel formatting was applied
                FormattedMessageDetails details = specialFormatResult.get();
                finalMessage = details.formattedMessage;
                effectiveRange = details.effectiveRange;
            } else {
                // Standard channel formatting
                effectiveRange = finalTargetChannel.range;
                finalMessage = Component.empty();
                finalMessage.append(ChatFormattingUtils.parseColors(finalTargetChannel.displayPrefix));
                finalMessage.append(Component.literal(" "));
                Component playerNameComponent = ChatFormattingUtils.parseColors(finalTargetChannel.nameColor + sender.getName().getString());
                finalMessage.append(playerNameComponent);
                finalMessage.append(ChatFormattingUtils.parseColors(finalTargetChannel.separatorColor + finalTargetChannel.separator));
                finalMessage.append(ChatFormattingUtils.parseColors(finalTargetChannel.messageColor + messageContent));
            }

            MinecraftServer server = sender.getServer();

            for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
                // Check if recipient is joined to the target channel AND has permission (unless alwaysOn)
                if (ChatChannelManager.isJoined(recipient, finalTargetChannel.name)) {
                    if (finalTargetChannel.alwaysOn || Verbatim.permissionService.hasPermission(recipient, finalTargetChannel.permission.orElse(null), 2)) {
                        // Handle message sending based on range
                        if (effectiveRange >= 0) {
                            double distSqr = recipient.distanceToSqr(sender);
                            if (recipient.equals(sender)) {
                                recipient.sendSystemMessage(finalMessage); // Sender always sees their own message clearly
                            } else {
                                MutableComponent messageToSend = specialFormatResult
                                    .map(details -> details.getMessageForDistance(distSqr))
                                    .orElseGet(() -> distSqr <= effectiveRange * effectiveRange ? finalMessage : null);
                                
                                if (messageToSend != null) {
                                    recipient.sendSystemMessage(messageToSend);
                                }
                            }
                        } else { // Global range
                            recipient.sendSystemMessage(finalMessage);
                        }
                    } else {
                        // Recipient is joined but lost permission: auto-leave them from this channel
                        Verbatim.LOGGER.info("[Verbatim ChatEvent] Recipient {} is joined to '{}' but lost permission. Auto-leaving.", recipient.getName().getString(), finalTargetChannel.name);
                        ChatChannelManager.autoLeaveChannel(recipient, finalTargetChannel.name);
                    }
                } else {
                     // Verbatim.LOGGER.trace("[Verbatim ChatEvent] Recipient {} is not joined to channel {}. Message not sent to them.", recipient.getName().getString(), finalTargetChannel.name);
                }
            }
        }
    }
    
    public static void onConfigReload() {
        ChatChannelManager.loadConfiguredChannels();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Re-evaluate joined channels against new config (permissions might have changed, or alwaysOn status)
                Set<String> currentJoined = ChatChannelManager.getJoinedChannels(player);
                for (String joinedChannelName : new HashSet<>(currentJoined)) { // Iterate copy as original might be modified
                    ChatChannelManager.getChannelConfigByName(joinedChannelName).ifPresent(config -> {
                        if (!config.alwaysOn && !Verbatim.permissionService.hasPermission(player, config.permission.orElse(null), 2)) {
                            Verbatim.LOGGER.info("[Verbatim ConfigReload] Player {} lost permission for joined channel '{}' after config reload. Auto-leaving.", player.getName().getString(), config.name);
                            ChatChannelManager.autoLeaveChannel(player, config.name); // This handles refocus if needed
                        }
                    });
                }
                // Ensure all alwaysOn channels are joined
                for (ChatChannelManager.ChannelConfig config : ChatChannelManager.getAllChannelConfigs()){
                    if(config.alwaysOn){
                        ChatChannelManager.joinChannel(player, config.name); // joinChannel handles "already joined" and messages if newly joined
                    }
                }
                // Ensure player has a valid focus after potential changes
                ChatChannelManager.getFocusedChannelConfig(player).ifPresentOrElse(focusedConfig -> {
                    if (!ChatChannelManager.isJoined(player, focusedConfig.name)) {
                        Verbatim.LOGGER.info("[Verbatim ConfigReload] Player {}'s focused channel '{}' is no longer joined. Resetting focus.", player.getName().getString(), focusedConfig.name);
                        ChatChannelManager.ChannelConfig defaultChannel = ChatChannelManager.getDefaultChannelConfig();
                        if (defaultChannel != null) ChatChannelManager.focusChannel(player, defaultChannel.name);
                    }
                }, () -> {
                    Verbatim.LOGGER.info("[Verbatim ConfigReload] Player {} has no focused channel. Resetting focus.", player.getName().getString());
                    ChatChannelManager.ChannelConfig defaultChannel = ChatChannelManager.getDefaultChannelConfig();
                    if (defaultChannel != null) ChatChannelManager.focusChannel(player, defaultChannel.name);
                });
            }
            Verbatim.LOGGER.info("[Verbatim ConfigReload] Player channel states re-evaluated.");
        }
    }
}
