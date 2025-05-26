package world.landfall.verbatim.specialchannels;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.verbatim.ChatChannelManager;
import world.landfall.verbatim.ChatFormattingUtils;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Handles formatting for "local" special channels with suffix-based behavior.
 */
public class LocalChannelFormatter {
    private static final Random RANDOM = new Random();
    public static final double FADE_MULTIPLIER = 2.0; // How much further beyond range until fully obscured
    private static final String OBSCURE_CHARS = "."; // Using single dot for better char-by-char replacement

    /**
     * Creates a partially obscured version of a message based on distance,
     * affecting only the message content part.
     */
    public static MutableComponent createDistanceObscuredMessage(
            MutableComponent originalMessage, // The fully formatted original message
            double distanceSquared,
            int effectiveRange,
            boolean isRoleplayMessage,
            String channelMessageColorString) { // e.g., "&7" from channelConfig.messageColor

        if (isRoleplayMessage || effectiveRange < 0) {
            return originalMessage.copy(); // Don't obscure roleplay or global messages
        }

        double distance = Math.sqrt(distanceSquared);
        if (distance <= effectiveRange) {
            return originalMessage.copy(); // Within clear range
        }

        // Calculate how obscured the message should be (0.0 to 1.0)
        double maxRangeObscureEnds = effectiveRange * FADE_MULTIPLIER;
        double obscurePercentage = (distance - effectiveRange) / (maxRangeObscureEnds - effectiveRange);
        obscurePercentage = Math.min(1.0, Math.max(0.0, obscurePercentage));

        List<Component> originalSiblings = originalMessage.getSiblings();
        if (originalSiblings.isEmpty()) {
            return originalMessage.copy(); // Should not happen with current formatting
        }

        MutableComponent reconstructedMessage = Component.empty();
        int firstMessageContentComponentIndex = -1;

        // Heuristic: find where the actual message content likely starts.
        // Standard local messages are: [Prefix] [Name] [Verb] [MessageContent]
        // The MessageContent is usually the last component appended, or after the last ": ".
        // We will assume the last component is the primary message content to be obscured.
        // If a more complex structure exists where verb and message are one component, this might need refinement.
        
        if (originalSiblings.size() > 0) {
            firstMessageContentComponentIndex = originalSiblings.size() - 1;
        }
        
        if (firstMessageContentComponentIndex == -1) { // Safety, if somehow no siblings
            return originalMessage.copy(); 
        }

        // Append all prefix components (everything before the determined message content component)
        for (int i = 0; i < firstMessageContentComponentIndex; i++) {
            reconstructedMessage.append(originalSiblings.get(i).copy());
        }

        // Process the component(s) deemed to be message content
        for (int i = firstMessageContentComponentIndex; i < originalSiblings.size(); i++) {
            Component messagePartComponent = originalSiblings.get(i);
            String textToObscure = messagePartComponent.getString();

            for (char c : textToObscure.toCharArray()) {
                if (RANDOM.nextDouble() < obscurePercentage) {
                    reconstructedMessage.append(Component.literal(String.valueOf(OBSCURE_CHARS.charAt(RANDOM.nextInt(OBSCURE_CHARS.length()))))
                        .withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    // Append original character with the channel's default message color
                    // We assume channelMessageColorString is like "&7" or "&c&l"
                    reconstructedMessage.append(ChatFormattingUtils.parseColors(channelMessageColorString + c));
                }
            }
        }
        return reconstructedMessage;
    }

    /**
     * Attempts to format a message for a local special channel.
     * 
     * @param sender The player sending the message
     * @param channelConfig The channel configuration
     * @param originalMessageContent The original message content
     * @return Optional containing formatted message details if this is a local channel, empty otherwise
     */
    public static Optional<FormattedMessageDetails> formatLocalMessage(
            ServerPlayer sender, 
            ChatChannelManager.ChannelConfig channelConfig, 
            String originalMessageContent) {
        
        // Check if this is a local special channel
        boolean isLocalSpecialChannel = channelConfig.specialChannelType
            .map("local"::equals)
            .orElse(false);
        
        if (!isLocalSpecialChannel) {
            return Optional.empty(); // Not a local channel, let other formatters handle it
        }

        // Parse suffix and determine behavior
        int effectiveRange = 50; // Default range for local channels
        String localActionText = "says:"; // Default verb
        String actualMessageContent = originalMessageContent;
        boolean applyPlusStyleFormatting = false;

        String messageAfterSuffixRemoval = originalMessageContent;

        if (originalMessageContent.endsWith("!!")) {
            effectiveRange = 100;
            localActionText = "shouts:";
            messageAfterSuffixRemoval = originalMessageContent.substring(0, originalMessageContent.length() - 2);
        } else if (originalMessageContent.endsWith("!")) {
            effectiveRange = 75;
            localActionText = "exclaims:";
            messageAfterSuffixRemoval = originalMessageContent.substring(0, originalMessageContent.length() - 1);
        } else if (originalMessageContent.endsWith("*")) {
            effectiveRange = 10;
            localActionText = "whispers:";
            messageAfterSuffixRemoval = originalMessageContent.substring(0, originalMessageContent.length() - 1);
        } else if (originalMessageContent.endsWith("$")) {
            effectiveRange = 3;
            localActionText = "mutters:";
            messageAfterSuffixRemoval = originalMessageContent.substring(0, originalMessageContent.length() - 1);
        } else if (originalMessageContent.endsWith("+")) {
            effectiveRange = 50;
            localActionText = ""; // No verb, direct action text formatting
            applyPlusStyleFormatting = true;
            messageAfterSuffixRemoval = originalMessageContent.substring(0, originalMessageContent.length() - 1);
        }
        // If no suffix matches, use defaults (range 50, " says:")

        actualMessageContent = messageAfterSuffixRemoval.trim();

        // Build the formatted message
        MutableComponent finalMessage = Component.empty();
        
        // Add channel prefix
        finalMessage.append(ChatFormattingUtils.parseColors(channelConfig.displayPrefix));
        finalMessage.append(Component.literal(" "));
        
        // Add player name
        Component playerNameComponent = ChatFormattingUtils.createPlayerNameComponent(sender, channelConfig.nameColor, false);
        finalMessage.append(playerNameComponent);
        finalMessage.append(Component.literal(" "));
        
        // Add action text (if any)
        if (!localActionText.isEmpty()) {
            finalMessage.append(Component.literal(localActionText));
            finalMessage.append(Component.literal(" "));
        }

        // Add message content with special formatting if needed
        if (applyPlusStyleFormatting) {
            formatPlusStyleMessage(finalMessage, actualMessageContent, channelConfig.messageColor);
            // For roleplay, messageColorForObscuring is not used, so pass null
            return Optional.of(new FormattedMessageDetails(finalMessage, effectiveRange, true, null)); 
        } else {
            // Standard formatting for other local types
            finalMessage.append(ChatFormattingUtils.parseColors(channelConfig.messageColor + actualMessageContent));
            // For standard local, pass the channel's message color for obscuring logic
            return Optional.of(new FormattedMessageDetails(finalMessage, effectiveRange, false, channelConfig.messageColor));
        }
    }

    /**
     * Handles the special '+' suffix formatting with italics and quote handling.
     */
    private static void formatPlusStyleMessage(MutableComponent finalMessage, String messageContent, String baseColorPrefix) {
        boolean inQuote = false;
        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < messageContent.length(); i++) {
            char c = messageContent.charAt(i);
            if (c == '"') {
                // Process any accumulated segment
                if (currentSegment.length() > 0) {
                    if (inQuote) {
                        // Segment was inside quotes, now ending - use gray color
                        finalMessage.append(Component.literal(currentSegment.toString()).copy().withStyle(ChatFormatting.GRAY));
                    } else {
                        // Segment was outside quotes, now starting quote - use white italicized
                        finalMessage.append(Component.literal(currentSegment.toString()).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
                    }
                    currentSegment.setLength(0);
                }
                // Add the quote character itself in gray
                finalMessage.append(Component.literal("\"").copy().withStyle(ChatFormatting.GRAY));
                inQuote = !inQuote; // Toggle quote state
            } else {
                currentSegment.append(c);
            }
        }

        // Handle any remaining segment
        if (currentSegment.length() > 0) {
            if (inQuote) {
                // Remainder is inside quotes - use gray
                finalMessage.append(Component.literal(currentSegment.toString()).copy().withStyle(ChatFormatting.GRAY));
            } else {
                // Remainder is outside quotes - use white italicized
                finalMessage.append(Component.literal(currentSegment.toString()).copy().withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            }
        }
    }
} 