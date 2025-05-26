package world.landfall.verbatim.chat;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.verbatim.ChatChannelManager;

public class ChatFocus extends FocusTarget {
    private final FocusType type;
    private final Object identifier; // UUID for DM, String for channel

    public enum FocusType {
        DM,
        CHANNEL
    }

    private ChatFocus(FocusType type, Object identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    public static ChatFocus createDmFocus(UUID targetPlayerId) {
        return new ChatFocus(FocusType.DM, targetPlayerId);
    }

    public static ChatFocus createChannelFocus(String channelName) {
        return new ChatFocus(FocusType.CHANNEL, channelName);
    }

    public FocusType getType() {
        return type;
    }

    public UUID getTargetPlayerId() {
        return type == FocusType.DM ? (UUID) identifier : null;
    }

    public String getChannelName() {
        return type == FocusType.CHANNEL ? (String) identifier : null;
    }

    @Override
    public String getDisplayName() {
        if (type == FocusType.DM) {
            ServerPlayer player = ChatChannelManager.getPlayerByUUID((UUID) identifier);
            return player != null ? "DM with " + player.getName().getString() : "DM with offline player";
        } else {
            return ChatChannelManager.getChannelConfigByName((String) identifier)
                .map(config -> config.displayPrefix + " " + config.name)
                .orElse("Unknown channel: " + identifier);
        }
    }

    @Override
    public boolean isValid() {
        if (type == FocusType.DM) {
            return ChatChannelManager.getPlayerByUUID((UUID) identifier) != null;
        } else {
            return ChatChannelManager.getChannelConfigByName((String) identifier).isPresent();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ChatFocus)) return false;
        ChatFocus other = (ChatFocus) obj;
        return this.type == other.type && this.identifier.equals(other.identifier);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + identifier.hashCode();
    }
} 