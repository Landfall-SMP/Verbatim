package world.landfall.verbatim;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import world.landfall.verbatim.chat.FocusTarget;
import world.landfall.verbatim.chat.ChatFocus;

public class ChatChannelManager {
    // Flag to track if the manager has been properly initialized
    private static boolean isInitialized = false;
    
    private static final Map<UUID, FocusTarget> playerFocus = new HashMap<>();
    private static final Map<UUID, Set<String>> joinedChannels = new HashMap<>();
    private static final Map<UUID, UUID> lastIncomingDmSender = new HashMap<>();

    public static class ChannelConfig {
        public final String name;
        public final String displayPrefix;
        public final String shortcut;
        public final Optional<String> permission;
        public final int range; // -1 for global/no range
        public final String nameColor; 
        public final String separator; 
        public final String separatorColor; 
        public final String messageColor; 
        public final boolean alwaysOn; // If true, cannot be left via /leave and permission is IGNORED (public)
        public final boolean mature; // If true, shows mature content warning when joining
        public final Optional<String> specialChannelType; // For special channel behaviors like "local"

        public ChannelConfig(String name, String displayPrefix, String shortcut, String permission, Number range,
                             String nameColor, String separator, String separatorColor, String messageColor, Boolean alwaysOn, Boolean mature, String specialChannelType) {
            this.name = name;
            this.displayPrefix = displayPrefix;
            this.shortcut = shortcut;
            this.alwaysOn = (alwaysOn == null) ? false : alwaysOn;
            this.mature = (mature == null) ? false : mature;
            // If alwaysOn is true or permission is null/empty, treat as no permission required
            this.permission = (this.alwaysOn || permission == null || permission.trim().isEmpty()) ? Optional.empty() : Optional.of(permission);
            this.range = (range == null) ? -1 : range.intValue();
            
            this.messageColor = (messageColor == null || messageColor.isEmpty()) ? "&f" : messageColor;
            this.nameColor = (nameColor == null || nameColor.isEmpty()) ? this.messageColor : nameColor;
            this.separator = (separator == null || separator.isEmpty()) ? ": " : separator;
            this.separatorColor = (separatorColor == null || separatorColor.isEmpty()) ? this.messageColor : separatorColor;
            this.specialChannelType = (specialChannelType == null || specialChannelType.isEmpty()) ? Optional.empty() : Optional.of(specialChannelType);
        }
    }

    private static final Map<String, ChannelConfig> channelConfigsByName = new HashMap<>();
    private static final Map<String, ChannelConfig> channelConfigsByShortcut = new HashMap<>();

    public static void loadConfiguredChannels() {
        channelConfigsByName.clear();
        channelConfigsByShortcut.clear();
        
        List<? extends UnmodifiableConfig> channelsFromConfig = VerbatimConfig.CHANNELS.get();
        Verbatim.LOGGER.info("Loading {} channel definitions from config.", channelsFromConfig.size());

        for (UnmodifiableConfig channelConf : channelsFromConfig) {
            try {
                String name = (String) channelConf.get("name");
                String displayPrefix = (String) channelConf.get("displayPrefix");
                String shortcut = (String) channelConf.get("shortcut");
                String permissionStr = channelConf.getOptional("permission").map(String::valueOf).orElse(null);
                Object rangeObj = channelConf.get("range"); 
                Number range = (rangeObj instanceof Number) ? (Number)rangeObj : null;
                String nameColor = channelConf.getOptional("nameColor").map(String::valueOf).orElse(null);
                String separator = channelConf.getOptional("separator").map(String::valueOf).orElse(null);
                String separatorColor = channelConf.getOptional("separatorColor").map(String::valueOf).orElse(null);
                String messageColor = channelConf.getOptional("messageColor").map(String::valueOf).orElse(null);
                Boolean alwaysOn = channelConf.getOptional("alwaysOn").map(v -> (Boolean)v).orElse(false);
                Boolean mature = channelConf.getOptional("mature").map(v -> (Boolean)v).orElse(false);
                String specialChannelType = channelConf.getOptional("specialChannelType").map(String::valueOf).orElse(null);

                if (name != null && !name.isEmpty() && displayPrefix != null && shortcut != null && !shortcut.isEmpty()) {
                    ChannelConfig parsedConfig = new ChannelConfig(name, displayPrefix, shortcut, permissionStr, range,
                                                                 nameColor, separator, separatorColor, messageColor, alwaysOn, mature, specialChannelType);
                    if (channelConfigsByName.containsKey(name)) {
                        Verbatim.LOGGER.warn("Duplicate channel name in config: '{}'. Ignoring subsequent definition.", name);
                        continue;
                    }
                    if (channelConfigsByShortcut.containsKey(shortcut)) {
                        Verbatim.LOGGER.warn("Duplicate channel shortcut in config: '{}'. Ignoring subsequent definition.", shortcut);
                        continue;
                    }
                    channelConfigsByName.put(name, parsedConfig);
                    channelConfigsByShortcut.put(shortcut, parsedConfig);
                    Verbatim.LOGGER.debug("Successfully loaded channel: {}", name);
                } else {
                    Verbatim.LOGGER.warn("Invalid channel definition (values not matching expected types or missing after validation) from UnmodifiableConfig: {}. Skipping.", channelConf.valueMap());
                }
            } catch (Exception e) { 
                Verbatim.LOGGER.error("Unexpected error parsing channel definition from UnmodifiableConfig: {}", channelConf.valueMap(), e);
            }
        }
        Verbatim.LOGGER.info("Finished loading chat channels. Total loaded: {}", channelConfigsByName.size());
        // Mark as initialized after successful loading
        isInitialized = true;
        // After reloading configs, re-evaluate joined channels for all online players
        // This is now primarily handled by ChatEvents.onConfigReload to also handle focusing default
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static java.util.Collection<ChannelConfig> getAllChannelConfigs() {
        return channelConfigsByName.values();
    }

    public static Optional<ChannelConfig> getChannelConfigByName(String name) {
        return Optional.ofNullable(channelConfigsByName.get(name));
    }

    public static Optional<ChannelConfig> getChannelConfigByShortcut(String shortcut) {
        return Optional.ofNullable(channelConfigsByShortcut.get(shortcut));
    }

    public static ChannelConfig getDefaultChannelConfig() {
        String defaultChannelName = VerbatimConfig.DEFAULT_CHANNEL_NAME.get();
        ChannelConfig defaultConfig = channelConfigsByName.get(defaultChannelName);
        if (defaultConfig == null) {
            Verbatim.LOGGER.warn("[ChatChannelManager] Default channel named '{}' not found. Falling back.", defaultChannelName);
            if (!channelConfigsByName.isEmpty()) {
                defaultConfig = channelConfigsByName.values().stream().filter(c -> c.alwaysOn).findFirst()
                                .orElse(channelConfigsByName.values().iterator().next()); // Prefer alwaysOn as default fallback
                Verbatim.LOGGER.warn("[ChatChannelManager] Using first available (preferably alwaysOn) channel '{}' as fallback default.", defaultConfig.name);
            } else {
                Verbatim.LOGGER.error("[ChatChannelManager] CRITICAL: No channels loaded. Cannot determine a default channel.");
                return null; 
            }
        }
        return defaultConfig;
    }

    public static void playerLoggedIn(ServerPlayer player) {
        loadPlayerChannelState(player);
        ensurePlayerIsInADefaultFocus(player);
    }
    
    private static void loadPlayerChannelState(ServerPlayer player) {
        Set<String> loadedJoinedChannels = new HashSet<>();
        String loadedFocusedChannel = null;
        try {
            if (player.getPersistentData().contains("verbatim:joined_channels")) {
                String[] joined = player.getPersistentData().getString("verbatim:joined_channels").split(",");
                for (String chName : joined) {
                    if (!chName.isEmpty() && channelConfigsByName.containsKey(chName)) {
                        loadedJoinedChannels.add(chName);
                    }
                }
            }
            if (player.getPersistentData().contains("verbatim:focused_channel")) {
                loadedFocusedChannel = player.getPersistentData().getString("verbatim:focused_channel");
                if (!channelConfigsByName.containsKey(loadedFocusedChannel)) {
                    loadedFocusedChannel = null; // Invalid focused channel
                }
            }
        } catch (Exception e) {
            Verbatim.LOGGER.error("[ChatChannelManager] Error loading player channel state for {}: {}", player.getName().getString(), e.getMessage());
        }

        joinedChannels.put(player.getUUID(), loadedJoinedChannels);

        // Ensure all alwaysOn channels are joined by default, and permission is checked for others
        for (ChannelConfig config : channelConfigsByName.values()) {
            if (config.alwaysOn) {
                boolean wasJoined = loadedJoinedChannels.contains(config.name);
                internalJoinChannel(player, config.name, true); // Force join alwaysOn, skip permission check
                
                // Show mature content warning if this is a mature channel and the player wasn't already joined
                if (!wasJoined && config.mature) {
                    player.sendSystemMessage(Component.literal("⚠ WARNING: This channel may contain mature content!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    player.sendSystemMessage(Component.literal("By remaining in this channel, you confirm that you are 18+ and okay with seeing messages posted here.").withStyle(ChatFormatting.YELLOW));
                    player.sendSystemMessage(Component.literal("If you are not comfortable with this, please leave immediately using: ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("/channel leave").withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)));
                }
            } else if (loadedJoinedChannels.contains(config.name)) {
                // If it was in their saved list and not alwaysOn, check permission now
                // Only check permission if the channel actually has a permission requirement
                if (config.permission.isPresent() && !Verbatim.permissionService.hasPermission(player, config.permission.get(), 2)) {
                    Verbatim.LOGGER.info("[ChatChannelManager] Player {} lost permission for saved joined channel '{}' on login. Removing.", player.getName().getString(), config.name);
                    internalLeaveChannel(player, config.name); // Silently remove, don't message yet
                }
            }
        }

        if (loadedFocusedChannel != null && getJoinedChannels(player).contains(loadedFocusedChannel)) {
            playerFocus.put(player.getUUID(), ChatFocus.createChannelFocus(loadedFocusedChannel));
        } else {
             playerFocus.remove(player.getUUID()); // Will be set by ensurePlayerIsInADefaultFocus
        }
        savePlayerChannelState(player);
    }

    private static void ensurePlayerIsInADefaultFocus(ServerPlayer player) {
        FocusTarget currentFocus = playerFocus.get(player.getUUID());
        if (currentFocus == null || !currentFocus.isValid() || 
            (currentFocus instanceof ChatFocus && !isJoined(player, ((ChatFocus) currentFocus).getChannelName()))) {
            ChannelConfig defaultChannel = getDefaultChannelConfig();
            if (defaultChannel != null) {
                Verbatim.LOGGER.info("[ChatChannelManager] Player {} focus invalid or not joined. Focusing to default '{}'.", player.getName().getString(), defaultChannel.name);
                focusChannel(player, defaultChannel.name); // This will also join if not already
            } else {
                Verbatim.LOGGER.error("[ChatChannelManager] Player {} needs focus reset, but no default channel available!", player.getName().getString());
            }
        }
    }

    private static void savePlayerChannelState(ServerPlayer player) {
        Set<String> currentJoined = joinedChannels.getOrDefault(player.getUUID(), new HashSet<>());
        player.getPersistentData().putString("verbatim:joined_channels", String.join(",", currentJoined));
        FocusTarget currentFocused = playerFocus.get(player.getUUID());
        if (currentFocused instanceof ChatFocus) {
            ChatFocus chatFocus = (ChatFocus) currentFocused;
            if (chatFocus.getType() == ChatFocus.FocusType.CHANNEL) {
                player.getPersistentData().putString("verbatim:focused_channel", chatFocus.getChannelName());
            } else {
                // For DM focus, remove the focused_channel key since it's not a channel
                player.getPersistentData().remove("verbatim:focused_channel");
            }
        } else {
            player.getPersistentData().remove("verbatim:focused_channel");
        }
    }

    public static Set<String> getJoinedChannels(ServerPlayer player) {
        return joinedChannels.getOrDefault(player.getUUID(), new HashSet<>());
    }

    public static List<ChannelConfig> getJoinedChannelConfigs(ServerPlayer player) {
        return getJoinedChannels(player).stream()
            .map(ChatChannelManager::getChannelConfigByName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    public static Optional<ChannelConfig> getFocusedChannelConfig(ServerPlayer player) {
        FocusTarget focus = playerFocus.get(player.getUUID());
        if (focus instanceof ChatFocus && ((ChatFocus) focus).getType() == ChatFocus.FocusType.CHANNEL) {
            return getChannelConfigByName(((ChatFocus) focus).getChannelName());
        }
        return Optional.empty();
    }

    public static boolean isJoined(ServerPlayer player, String channelName) {
        return getJoinedChannels(player).contains(channelName);
    }
    
    // Returns true if successfully joined, false if no permission or channel doesn't exist.
    private static boolean internalJoinChannel(ServerPlayer player, String channelName, boolean forceJoin) {
        ChannelConfig config = channelConfigsByName.get(channelName);
        if (config == null) return false;

        // Only check permission if the channel has one and isn't being force joined
        if (!forceJoin && config.permission.isPresent() && !Verbatim.permissionService.hasPermission(player, config.permission.get(), 2)) {
            return false;
        }
        joinedChannels.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(channelName);
        savePlayerChannelState(player);
        return true;
    }

    // Public facing join, with feedback messages
    public static boolean joinChannel(ServerPlayer player, String channelName) {
        ChannelConfig config = channelConfigsByName.get(channelName);
        if (config == null) {
            player.sendSystemMessage(Component.literal("Channel '" + channelName + "' not found.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (isJoined(player, channelName)) {
             player.sendSystemMessage(Component.literal("Already joined to channel: ").withStyle(ChatFormatting.YELLOW)
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)));
            return true; // Already joined
        }

        // Only check permission if the channel has one and isn't alwaysOn
        if (config.alwaysOn || !config.permission.isPresent() || Verbatim.permissionService.hasPermission(player, config.permission.get(), 2)) {
            internalJoinChannel(player, channelName, config.alwaysOn);
            player.sendSystemMessage(Component.literal("Joined channel: ").withStyle(ChatFormatting.GREEN)
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)));
            
            // Show mature content warning if this is a mature channel
            if (config.mature) {
                player.sendSystemMessage(Component.literal("⚠ WARNING: This channel may contain mature content. ⚠").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("By remaining in this channel, you confirm that you are 18+ and okay with seeing messages posted here.").withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("If you are not comfortable with this, please leave immediately using: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("/channel leave").withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)));
            }
            return true;
        } else {
            player.sendSystemMessage(Component.literal("You do not have permission to join channel: ")
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)).withStyle(ChatFormatting.RED));
            return false;
        }
    }
    
    // Internal leave, no feedback, bypasses alwaysOn check for auto-leave due to permission loss
    public static void autoLeaveChannel(ServerPlayer player, String channelName) {
        internalLeaveChannel(player, channelName);
        // If the channel they were auto-left from was their focus, reset focus
        FocusTarget currentFocus = playerFocus.get(player.getUUID());
        if (currentFocus instanceof ChatFocus && channelName.equals(((ChatFocus) currentFocus).getChannelName())) {
            playerFocus.remove(player.getUUID());
            ensurePlayerIsInADefaultFocus(player);
            player.sendSystemMessage(Component.literal("You were automatically removed from channel '")
                .append(Component.literal(channelName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("' due to permission loss and it was your focus. Focused to default.")).withStyle(ChatFormatting.RED));
        } else {
             player.sendSystemMessage(Component.literal("You were automatically removed from channel '")
                .append(Component.literal(channelName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("' due to permission loss.")).withStyle(ChatFormatting.RED));
        }
        savePlayerChannelState(player);
    }

    private static void internalLeaveChannel(ServerPlayer player, String channelName) {
        joinedChannels.computeIfPresent(player.getUUID(), (k, v) -> { 
            v.remove(channelName); 
            return v.isEmpty() ? null : v; 
        });
        if (joinedChannels.get(player.getUUID()) == null) {
            joinedChannels.remove(player.getUUID());
        }
        // Do not remove focus here, autoLeaveChannel handles focus reset if needed.
        savePlayerChannelState(player);
    }

    // Public facing leave, with feedback, respects alwaysOn
    public static boolean leaveChannelCmd(ServerPlayer player, String channelName) {
        ChannelConfig config = channelConfigsByName.get(channelName);
        if (config == null) {
            player.sendSystemMessage(Component.literal("Channel '" + channelName + "' not found.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (config.alwaysOn) {
            player.sendSystemMessage(Component.literal("Cannot leave channel '")
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name))
                .append(Component.literal("' as it is marked always-on.")).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!isJoined(player, channelName)) {
            player.sendSystemMessage(Component.literal("You are not currently in channel: ").withStyle(ChatFormatting.YELLOW)
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)));
            return false;
        }

        internalLeaveChannel(player, channelName);
        player.sendSystemMessage(Component.literal("Left channel: ").withStyle(ChatFormatting.YELLOW)
            .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)));
        
        // If they left their focused channel, reset focus to default
        FocusTarget currentFocus = playerFocus.get(player.getUUID());
        if (currentFocus instanceof ChatFocus && channelName.equals(((ChatFocus) currentFocus).getChannelName())) {
            playerFocus.remove(player.getUUID());
            ensurePlayerIsInADefaultFocus(player); // This will also message the player about new focus
        }
        savePlayerChannelState(player);
        return true;
    }

    public static void focusChannel(ServerPlayer player, String channelName) {
        ChannelConfig config = channelConfigsByName.get(channelName);
        if (config == null) {
            player.sendSystemMessage(Component.literal("Cannot focus channel '" + channelName + "': Not found.").withStyle(ChatFormatting.RED));
            return;
        }

        // Only check permission if the channel has one and isn't alwaysOn
        if (config.alwaysOn || !config.permission.isPresent() || Verbatim.permissionService.hasPermission(player, config.permission.get(), 2)) {
            boolean wasJoined = isJoined(player, channelName);
            internalJoinChannel(player, channelName, config.alwaysOn); // Ensure joined (force if alwaysOn)
            playerFocus.put(player.getUUID(), ChatFocus.createChannelFocus(channelName));
            savePlayerChannelState(player);
            player.sendSystemMessage(Component.literal("Focused channel: ")
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)).withStyle(ChatFormatting.GREEN));
            
            // Show mature content warning if this is a mature channel and the player wasn't already joined
            if (!wasJoined && config.mature) {
                player.sendSystemMessage(Component.literal("⚠ WARNING: This channel may contain mature content. ⚠").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                player.sendSystemMessage(Component.literal("By remaining in this channel, you confirm that you are 18+ and okay with seeing messages posted here.").withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("If you are not comfortable with this, please leave immediately using: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("/channel leave").withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)));
            }
        } else {
            player.sendSystemMessage(Component.literal("Cannot focus channel '")
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name))
                .append(Component.literal("': You do not have permission.")).withStyle(ChatFormatting.RED));
        }
    }

    public static void playerLoggedOut(ServerPlayer player) {
        savePlayerChannelState(player); // Ensure state is saved on logout
        playerFocus.remove(player.getUUID());
        joinedChannels.remove(player.getUUID());
        lastIncomingDmSender.remove(player.getUUID());
    }

    // New DM-related methods
    public static void focusDm(ServerPlayer player, UUID targetPlayerId) {
        ServerPlayer targetPlayer = getPlayerByUUID(targetPlayerId);
        if (targetPlayer == null) {
            player.sendSystemMessage(Component.literal("Cannot focus DM: Target player is not online.").withStyle(ChatFormatting.RED));
            return;
        }
        
        playerFocus.put(player.getUUID(), ChatFocus.createDmFocus(targetPlayerId));
        player.sendSystemMessage(Component.literal("Focused DM with: ")
            .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.YELLOW)));
    }
    
    public static void focusDm(ServerPlayer player, String targetPlayerName) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetPlayerName);
        if (targetPlayer == null) {
            player.sendSystemMessage(Component.literal("Cannot focus DM: Player '" + targetPlayerName + "' is not online.").withStyle(ChatFormatting.RED));
            return;
        }
        
        focusDm(player, targetPlayer.getUUID());
    }
    
    public static Optional<FocusTarget> getFocus(ServerPlayer player) {
        return Optional.ofNullable(playerFocus.get(player.getUUID()));
    }
    
    public static void setLastIncomingDmSender(ServerPlayer recipient, UUID senderId) {
        lastIncomingDmSender.put(recipient.getUUID(), senderId);
    }
    
    public static Optional<UUID> getLastIncomingDmSender(ServerPlayer player) {
        return Optional.ofNullable(lastIncomingDmSender.get(player.getUUID()));
    }
    
    public static void handleDPrefix(ServerPlayer player) {
        FocusTarget currentFocus = playerFocus.get(player.getUUID());
        UUID lastSender = lastIncomingDmSender.get(player.getUUID());
        
        if (lastSender == null) {
            player.sendSystemMessage(Component.literal("No recent DMs to reply to.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        
        // If currently in DM mode with someone different than last sender, switch to last sender
        if (currentFocus instanceof ChatFocus && ((ChatFocus) currentFocus).getType() == ChatFocus.FocusType.DM) {
            UUID currentDmTarget = ((ChatFocus) currentFocus).getTargetPlayerId();
            if (!currentDmTarget.equals(lastSender)) {
                focusDm(player, lastSender);
                return;
            }
        }
        
        // If not in DM mode or already DMing the last sender, focus on last sender
        focusDm(player, lastSender);
    }

    public static List<ServerPlayer> getPlayersInChannel(MinecraftServer server, String channelName) {
        List<ServerPlayer> playersInChannel = new ArrayList<>();
        if (server == null || channelName == null || channelName.isEmpty()) {
            return playersInChannel; // Return empty list if server or channelName is invalid
        }
        // Check if the channel itself exists, otherwise no point iterating players
        if (!channelConfigsByName.containsKey(channelName)) {
            return playersInChannel; 
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isJoined(player, channelName)) {
                playersInChannel.add(player);
            }
        }
        return playersInChannel;
    }

    public static boolean adminKickPlayerFromChannel(ServerPlayer playerToKick, String channelName, ServerPlayer executor) {
        Optional<ChannelConfig> channelConfigOpt = getChannelConfigByName(channelName);

        if (!channelConfigOpt.isPresent()) {
            if (executor != null) {
                executor.sendSystemMessage(Component.literal("Channel '" + channelName + "' not found.").withStyle(ChatFormatting.RED));
            }
            return false;
        }

        ChannelConfig channelConfig = channelConfigOpt.get();

        if (channelConfig.alwaysOn) {
            if (executor != null) {
                executor.sendSystemMessage(Component.literal("Cannot kick players from '" + channelName + "' as it is an always-on channel.").withStyle(ChatFormatting.RED));
            }
            return false;
        }

        if (!isJoined(playerToKick, channelName)) {
            if (executor != null) {
                executor.sendSystemMessage(Component.literal(playerToKick.getName().getString() + " is not in channel '" + channelName + "'.").withStyle(ChatFormatting.RED));
            }
            return false;
        }

        // Perform the kick
        internalLeaveChannel(playerToKick, channelName); // This handles removing from joinedChannels and saving state

        // Notify the kicked player
        playerToKick.sendSystemMessage(Component.literal("You have been kicked from channel '" + channelName + "' by " + (executor != null ? executor.getName().getString() : "an administrator") + ".").withStyle(ChatFormatting.YELLOW));

        // Check if the kicked channel was the player's focus and reset if necessary
        FocusTarget currentFocus = playerFocus.get(playerToKick.getUUID());
        if (currentFocus instanceof ChatFocus && channelName.equals(((ChatFocus) currentFocus).getChannelName())) {
            playerFocus.remove(playerToKick.getUUID());
            ensurePlayerIsInADefaultFocus(playerToKick); // This will also save player state
            playerToKick.sendSystemMessage(Component.literal("Your focus was reset as you were kicked from your focused channel.").withStyle(ChatFormatting.YELLOW));
        } else {
             savePlayerChannelState(playerToKick); // Ensure state is saved even if focus didn't change
        }
        
        Verbatim.LOGGER.info("Player {} was kicked from channel {} by {}.", playerToKick.getName().getString(), channelName, (executor != null ? executor.getName().getString() : "CONSOLE"));
        return true;
    }

    public static ServerPlayer getPlayerByUUID(UUID playerId) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(playerId) : null;
    }
}
