package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that sends an atmospheric/narrative message to the player.
 * These are grey italic messages without speaker prefix, used for descriptions and flavor text.
 */
public class AtmosphericEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        String text = data.getText("");
        if (text.isEmpty()) {
            return;
        }

        // Parse any color codes in the text, but default to grey italic
        Component message = Component.literal("* ")
            .withStyle(style -> style.withColor(0x7F7F7F).withItalic(true))
            .append(DialogueEffect.parseColorCodes(text)
                .withStyle(style -> style.withColor(0x7F7F7F).withItalic(true)));

        player.sendSystemMessage(message);
    }
}
