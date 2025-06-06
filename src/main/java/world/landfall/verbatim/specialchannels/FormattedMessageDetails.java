package world.landfall.verbatim.specialchannels;

import net.minecraft.network.chat.MutableComponent;

/**
 * Data class to hold the results of special channel message formatting.
 */
public class FormattedMessageDetails {
    public final MutableComponent formattedMessage;
    public final int effectiveRange;
    private final boolean isRoleplayMessage;
    private final String channelMessageColorForObscuring; // e.g., "&7", used if !isRoleplayMessage

    public FormattedMessageDetails(MutableComponent formattedMessage, int effectiveRange, boolean isRoleplayMessage, String channelMessageColorForObscuring) {
        this.formattedMessage = formattedMessage;
        this.effectiveRange = effectiveRange;
        this.isRoleplayMessage = isRoleplayMessage;
        this.channelMessageColorForObscuring = channelMessageColorForObscuring;
    }

    /**
     * Gets the appropriate message component for a recipient at the given distance.
     * For special local channels (non-roleplay), this may return an obscured version based on distance.
     */
    public MutableComponent getMessageForDistance(double distanceSquared) {
        if (effectiveRange < 0) return formattedMessage.copy(); // Global messages, no obscuring
        
        double distance = Math.sqrt(distanceSquared);
        if (distance <= effectiveRange) return formattedMessage.copy(); // Within clear range
        
        // If outside clear range, check if it should be obscured or not shown at all
        if (distance <= effectiveRange * LocalChannelFormatter.FADE_MULTIPLIER) {
            // This will internally check isRoleplayMessage and not obscure if true
            return LocalChannelFormatter.createDistanceObscuredMessage(
                formattedMessage, 
                distanceSquared, 
                effectiveRange, 
                isRoleplayMessage, 
                channelMessageColorForObscuring
            );
        }
        
        return null; // Too far to receive message
    }
} 