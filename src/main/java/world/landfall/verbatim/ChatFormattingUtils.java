package world.landfall.verbatim;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerPlayer;

public class ChatFormattingUtils {

    public static Component parseColors(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty(); // Prefer Component.empty() over Component.literal("")
        }

        MutableComponent mainComponent = Component.literal("");
        // Split by color/formatting codes, ensuring the codes themselves are captured for processing.
        // This regex splits the string by looking ahead for an ampersand followed by a valid code character.
        String[] parts = text.split("(?i)(?=&[0-9a-fk-or])");

        Style currentStyle = Style.EMPTY;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (part.startsWith("&") && part.length() >= 2) {
                char code = part.charAt(1);
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                String textContent = part.substring(2);

                if (formatting != null) {
                    if (formatting.isColor()) {
                        currentStyle = Style.EMPTY.withColor(formatting); // Reset style for new color
                    } else if (formatting == ChatFormatting.RESET) {
                        currentStyle = Style.EMPTY; // Full reset
                    } else { // It's a formatting code (bold, italic, etc.)
                        currentStyle = applyStyle(currentStyle, formatting);
                    }
                }
                
                if (!textContent.isEmpty()) {
                    mainComponent.append(Component.literal(textContent).setStyle(currentStyle));
                }
            } else {
                // No color code at the beginning of this part, append with current style
                mainComponent.append(Component.literal(part).setStyle(currentStyle));
            }
        }
        return mainComponent;
    }

    // Helper to apply specific formatting to a style
    private static Style applyStyle(Style baseStyle, ChatFormatting format) {
        if (format == ChatFormatting.BOLD) return baseStyle.withBold(true);
        if (format == ChatFormatting.ITALIC) return baseStyle.withItalic(true);
        if (format == ChatFormatting.UNDERLINE) return baseStyle.withUnderlined(true);
        if (format == ChatFormatting.STRIKETHROUGH) return baseStyle.withStrikethrough(true);
        if (format == ChatFormatting.OBFUSCATED) return baseStyle.withObfuscated(true);
        return baseStyle;
    }

    /**
     * Creates a player name component that shows the display name with hover text showing the username
     * when they differ. For DMs, always uses the username without hover.
     * 
     * @param player The player whose name to display
     * @param colorPrefix The color/formatting prefix to apply (e.g., "&e" or "&c&l")
     * @param isDM Whether this is for a direct message (if true, always uses username)
     * @return A component with the appropriate name and hover text
     */
    public static Component createPlayerNameComponent(ServerPlayer player, String colorPrefix, boolean isDM) {
        String username = player.getName().getString();
        String displayName = player.getDisplayName().getString(); // Get raw display name
        String strippedDisplayName = stripFormattingCodes(displayName); // Strip codes for comparison and potential use

        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + username + " ");
        HoverEvent hoverEvent = null;

        String nameToShow;
        
        if (isDM) {
            // For DMs, always show the username, apply color prefix.
            nameToShow = username;
        } else {
            // For channels, if display name (stripped) is different from username, use it and set hover.
            if (!username.equals(strippedDisplayName)) {
                nameToShow = strippedDisplayName;
                hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                        Component.literal("Username: " + username).withStyle(ChatFormatting.GRAY));
            } else {
                // Otherwise, just use the username.
                nameToShow = username;
            }
        }

        // Parse the name with its color prefix first
        MutableComponent nameComponent = (MutableComponent) parseColors(colorPrefix + nameToShow);

        // Apply ClickEvent and HoverEvent (if any) to the parsed name component
        final HoverEvent finalHoverEvent = hoverEvent; // effectively final for lambda
        nameComponent = nameComponent.withStyle(style -> {
            Style updatedStyle = style.withClickEvent(clickEvent);
            if (finalHoverEvent != null) {
                updatedStyle = updatedStyle.withHoverEvent(finalHoverEvent);
            }
            return updatedStyle;
        });

        return nameComponent;
    }

    /**
     * Creates a Discord-formatted player name string that shows "username (displayname)" when they differ,
     * or just "username" when they're the same.
     * 
     * @param player The player whose name to format for Discord
     * @return A string formatted for Discord messages
     */
    public static String createDiscordPlayerName(ServerPlayer player) {
        String username = player.getName().getString();
        String displayName = player.getDisplayName().getString();
        
        // Strip Minecraft formatting codes (§ codes) from display name for Discord
        String cleanDisplayName = displayName.replaceAll("§[0-9a-fk-or]", "");
        
        if (!username.equals(cleanDisplayName)) {
            return username + " (" + cleanDisplayName + ")";
        } else {
            return username;
        }
    }

    /**
     * Strips Minecraft formatting codes (both & and §) from a string.
     * @param text The text to strip codes from.
     * @return The text with formatting codes removed.
     */
    public static String stripFormattingCodes(String text) {
        if (text == null) {
            return null;
        }
        // Regex to remove & followed by a hex char, or § followed by a hex char.
        return text.replaceAll("(?i)[&§][0-9A-FK-OR]", "");
    }
} 