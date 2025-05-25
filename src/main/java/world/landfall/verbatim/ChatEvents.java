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

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Mod.EventBusSubscriber(modid = Verbatim.MODID)
public class ChatEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatChannelManager.playerLoggedIn(player); // Handles loading saved state, joining alwaysOn, permission checks for saved
            
            Optional<ChatChannelManager.ChannelConfig> focusedChannelOpt = ChatChannelManager.getFocusedChannelConfig(player);
            
            focusedChannelOpt.ifPresent(config -> 
                player.sendSystemMessage(Component.literal("🗨 Focused channel: ")
                    .append(ChatFormattingUtils.parseColors(config.displayPrefix))
                    .append(Component.literal(" " + config.name).withStyle(ChatFormatting.YELLOW))
            ));

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
        Optional<ChatChannelManager.ChannelConfig> channelForMessageOpt = Optional.empty();

        // --- Shortcut Processing ---
        if (rawMessageText.contains(":")) {
            String potentialShortcut = rawMessageText.substring(0, rawMessageText.indexOf(":"));
            Optional<ChatChannelManager.ChannelConfig> targetChannelByShortcut = ChatChannelManager.getChannelConfigByShortcut(potentialShortcut);

            if (targetChannelByShortcut.isPresent()) {
                ChatChannelManager.ChannelConfig prospectiveChannel = targetChannelByShortcut.get();
                Verbatim.LOGGER.debug("[Verbatim ChatEvent] Shortcut '{}' targets channel: {}. Checking permission...", potentialShortcut, prospectiveChannel.name);
                
                // alwaysOn channels bypass permission check for joining/focusing via shortcut
                if (prospectiveChannel.alwaysOn || Verbatim.permissionService.hasPermission(sender, prospectiveChannel.permission.orElse(null), 2)) {
                    ChatChannelManager.focusChannel(sender, prospectiveChannel.name); // This also joins if not already
                    channelForMessageOpt = Optional.of(prospectiveChannel);
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
                Verbatim.LOGGER.debug("[Verbatim ChatEvent] No channel found for shortcut: {}", potentialShortcut);
                // Treat as normal message, not a shortcut failure if no channel matches
            }
        }

        // --- Determine Target Channel for Non-Shortcut or Post-Shortcut Message ---
        if (channelForMessageOpt.isEmpty()) { // No valid shortcut was processed, or it was just for focusing
            channelForMessageOpt = ChatChannelManager.getFocusedChannelConfig(sender);
            if (channelForMessageOpt.isEmpty()) {
                 Verbatim.LOGGER.error("[Verbatim ChatEvent] Player {} has no focused channel and no shortcut used. Attempting to set to default.", sender.getName().getString());
                 ChatChannelManager.ChannelConfig defaultChannel = ChatChannelManager.getDefaultChannelConfig();
                 if (defaultChannel != null) {
                    ChatChannelManager.focusChannel(sender, defaultChannel.name);
                    channelForMessageOpt = Optional.of(defaultChannel);
                    sender.sendSystemMessage(Component.literal("You were not focused on any channel. Message sent to default: ")
                        .append(ChatFormattingUtils.parseColors(defaultChannel.displayPrefix))
                        .append(Component.literal(" " + defaultChannel.name).withStyle(ChatFormatting.YELLOW)));
                 } else {
                    Verbatim.LOGGER.error("[Verbatim ChatEvent] CRITICAL: No default channel to focus for {}. Cannot send message.", sender.getName().getString());
                    sender.sendSystemMessage(Component.literal("Error: No active or default channel. Message not sent.").withStyle(ChatFormatting.RED));
                    return;
                 }
            }
        }
        
        ChatChannelManager.ChannelConfig finalTargetChannel = channelForMessageOpt.get(); // Should be present by now

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
