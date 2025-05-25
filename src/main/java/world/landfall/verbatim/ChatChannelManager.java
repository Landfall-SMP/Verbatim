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

public class ChatChannelManager {
    // Stores the single channel a player is currently TYPING IN / FOCUSED ON.
    private static final Map<UUID, String> focusedChannels = new HashMap<>();
    // Stores ALL channels a player is currently LISTENING TO / JOINED.
    private static final Map<UUID, Set<String>> joinedChannels = new HashMap<>();

    // Helper class to store parsed channel configuration for easier access
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
        public final Optional<String> specialChannelType; // For special channel behaviors like "local"

        public ChannelConfig(String name, String displayPrefix, String shortcut, String permission, Number range,
                             String nameColor, String separator, String separatorColor, String messageColor, Boolean alwaysOn, String specialChannelType) {
            this.name = name;
            this.displayPrefix = displayPrefix;
            this.shortcut = shortcut;
            this.alwaysOn = (alwaysOn == null) ? false : alwaysOn;
            // If alwaysOn is true, permission is effectively ignored (channel is public)
            this.permission = (this.alwaysOn || permission == null || permission.isEmpty()) ? Optional.empty() : Optional.of(permission);
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
                String specialChannelType = channelConf.getOptional("specialChannelType").map(String::valueOf).orElse(null);

                if (name != null && !name.isEmpty() && displayPrefix != null && shortcut != null && !shortcut.isEmpty()) {
                    ChannelConfig parsedConfig = new ChannelConfig(name, displayPrefix, shortcut, permissionStr, range,
                                                                 nameColor, separator, separatorColor, messageColor, alwaysOn, specialChannelType);
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
        // After reloading configs, re-evaluate joined channels for all online players
        // This is now primarily handled by ChatEvents.onConfigReload to also handle focusing default
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
                internalJoinChannel(player, config.name, true); // Force join alwaysOn, skip permission check
            } else if (loadedJoinedChannels.contains(config.name)) {
                // If it was in their saved list and not alwaysOn, check permission now
                if (!Verbatim.permissionService.hasPermission(player, config.permission.orElse(null), 2)) {
                    Verbatim.LOGGER.info("[ChatChannelManager] Player {} lost permission for saved joined channel '{}' on login. Removing.", player.getName().getString(), config.name);
                    internalLeaveChannel(player, config.name); // Silently remove, don't message yet
                }
            }
        }

        if (loadedFocusedChannel != null && getJoinedChannels(player).contains(loadedFocusedChannel)) {
            focusedChannels.put(player.getUUID(), loadedFocusedChannel);
        } else {
             focusedChannels.remove(player.getUUID()); // Will be set by ensurePlayerIsInADefaultFocus
        }
        savePlayerChannelState(player);
    }

    private static void ensurePlayerIsInADefaultFocus(ServerPlayer player) {
        ChannelConfig currentFocus = getFocusedChannelConfig(player).orElse(null);
        if (currentFocus == null || !isJoined(player, currentFocus.name)) {
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
        String currentFocused = focusedChannels.get(player.getUUID());
        if (currentFocused != null) {
            player.getPersistentData().putString("verbatim:focused_channel", currentFocused);
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
        return Optional.ofNullable(focusedChannels.get(player.getUUID()))
                       .flatMap(ChatChannelManager::getChannelConfigByName);
    }

    public static boolean isJoined(ServerPlayer player, String channelName) {
        return getJoinedChannels(player).contains(channelName);
    }
    
    // Returns true if successfully joined, false if no permission or channel doesn't exist.
    private static boolean internalJoinChannel(ServerPlayer player, String channelName, boolean forceJoin) {
        ChannelConfig config = channelConfigsByName.get(channelName);
        if (config == null) return false;

        // alwaysOn channels bypass permission check here as per new requirement
        if (!forceJoin && !config.alwaysOn && !Verbatim.permissionService.hasPermission(player, config.permission.orElse(null), 2)) {
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

        if (config.alwaysOn || Verbatim.permissionService.hasPermission(player, config.permission.orElse(null), 2)) {
            internalJoinChannel(player, channelName, config.alwaysOn);
            player.sendSystemMessage(Component.literal("Joined channel: ").withStyle(ChatFormatting.GREEN)
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)));
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
        if (channelName.equals(focusedChannels.get(player.getUUID()))) {
            focusedChannels.remove(player.getUUID());
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
        if (channelName.equals(focusedChannels.get(player.getUUID()))) {
            focusedChannels.remove(player.getUUID());
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

        // If alwaysOn, join/focus is allowed regardless of specific permission string.
        // Otherwise, normal permission check applies.
        if (config.alwaysOn || Verbatim.permissionService.hasPermission(player, config.permission.orElse(null), 2)) {
            internalJoinChannel(player, channelName, config.alwaysOn); // Ensure joined (force if alwaysOn)
            focusedChannels.put(player.getUUID(), channelName);
            savePlayerChannelState(player);
            player.sendSystemMessage(Component.literal("Focused channel: ")
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name)).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("Cannot focus channel '")
                .append(ChatFormattingUtils.parseColors(config.displayPrefix + " " + config.name))
                .append(Component.literal("': You do not have permission.")).withStyle(ChatFormatting.RED));
        }
    }

    public static void playerLoggedOut(ServerPlayer player) {
        savePlayerChannelState(player); // Ensure state is saved on logout
        focusedChannels.remove(player.getUUID());
        joinedChannels.remove(player.getUUID());
    }
}
