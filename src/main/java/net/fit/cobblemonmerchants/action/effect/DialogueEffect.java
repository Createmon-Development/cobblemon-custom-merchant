package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that sends a dialogue message to the player with speaker prefix.
 * Supports color codes using & prefix (e.g., &6 for gold, &f for white).
 */
public class DialogueEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        String text = data.getText("");
        if (text.isEmpty()) {
            return;
        }

        // Get speaker name from prefix or merchant
        String speaker = data.prefix().orElseGet(() -> {
            if (merchant != null) {
                return merchant.getMerchantDisplayName();
            }
            return "???";
        });

        // Strip color codes from the text - dialogue text is always white
        String cleanText = stripColorCodes(text);

        // Format: [Speaker Name] message (light yellow name, white text)
        MutableComponent message = Component.literal("[")
            .withStyle(style -> style.withColor(0xAAAAAA))
            .append(Component.literal(speaker).withStyle(style -> style.withColor(0xFFFF55))) // Light yellow
            .append(Component.literal("] ").withStyle(style -> style.withColor(0xAAAAAA)))
            .append(Component.literal(cleanText).withStyle(style -> style.withColor(0xFFFFFF))); // White

        player.sendSystemMessage(message);
    }

    /**
     * Strips Minecraft color codes (& format) from text.
     */
    private static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                // Check if it's a valid color/format code
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') ||
                    code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'r') {
                    i++; // Skip the code
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }

    /**
     * Parses Minecraft color codes (& format) in text.
     * Supports: &0-9, &a-f, &l (bold), &o (italic), &n (underline), &m (strikethrough), &r (reset)
     */
    public static MutableComponent parseColorCodes(String text) {
        MutableComponent result = Component.empty();

        int currentColor = 0xFFFFFF; // Default white
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;

        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);

                // Append current segment with current style
                if (!currentSegment.isEmpty()) {
                    final int finalColor = currentColor;
                    final boolean finalBold = bold;
                    final boolean finalItalic = italic;
                    final boolean finalUnderlined = underlined;
                    final boolean finalStrikethrough = strikethrough;

                    result = result.append(Component.literal(currentSegment.toString())
                        .withStyle(style -> style
                            .withColor(finalColor)
                            .withBold(finalBold)
                            .withItalic(finalItalic)
                            .withUnderlined(finalUnderlined)
                            .withStrikethrough(finalStrikethrough)));
                    currentSegment = new StringBuilder();
                }

                // Parse the color/format code
                switch (code) {
                    case '0' -> currentColor = 0x000000;
                    case '1' -> currentColor = 0x0000AA;
                    case '2' -> currentColor = 0x00AA00;
                    case '3' -> currentColor = 0x00AAAA;
                    case '4' -> currentColor = 0xAA0000;
                    case '5' -> currentColor = 0xAA00AA;
                    case '6' -> currentColor = 0xFFAA00;
                    case '7' -> currentColor = 0xAAAAAA;
                    case '8' -> currentColor = 0x555555;
                    case '9' -> currentColor = 0x5555FF;
                    case 'a', 'A' -> currentColor = 0x55FF55;
                    case 'b', 'B' -> currentColor = 0x55FFFF;
                    case 'c', 'C' -> currentColor = 0xFF5555;
                    case 'd', 'D' -> currentColor = 0xFF55FF;
                    case 'e', 'E' -> currentColor = 0xFFFF55;
                    case 'f', 'F' -> currentColor = 0xFFFFFF;
                    case 'l', 'L' -> bold = true;
                    case 'o', 'O' -> italic = true;
                    case 'n', 'N' -> underlined = true;
                    case 'm', 'M' -> strikethrough = true;
                    case 'r', 'R' -> {
                        currentColor = 0xFFFFFF;
                        bold = false;
                        italic = false;
                        underlined = false;
                        strikethrough = false;
                    }
                    default -> {
                        // Not a valid code, append & and code literally
                        currentSegment.append('&').append(code);
                        i++; // Skip the code character
                        continue;
                    }
                }

                i++; // Skip the code character
            } else {
                currentSegment.append(c);
            }
        }

        // Append final segment
        if (!currentSegment.isEmpty()) {
            final int finalColor = currentColor;
            final boolean finalBold = bold;
            final boolean finalItalic = italic;
            final boolean finalUnderlined = underlined;
            final boolean finalStrikethrough = strikethrough;

            result = result.append(Component.literal(currentSegment.toString())
                .withStyle(style -> style
                    .withColor(finalColor)
                    .withBold(finalBold)
                    .withItalic(finalItalic)
                    .withUnderlined(finalUnderlined)
                    .withStrikethrough(finalStrikethrough)));
        }

        return result;
    }
}
