package world.landfall.verbatim;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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
} 