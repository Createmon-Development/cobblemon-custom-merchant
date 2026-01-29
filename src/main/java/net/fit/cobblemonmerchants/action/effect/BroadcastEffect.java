package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that broadcasts a message to all players on the server.
 * Used for server-wide announcements related to quest progress.
 */
public class BroadcastEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        String text = data.getText("");
        if (text.isEmpty()) {
            CobblemonMerchants.LOGGER.warn("BroadcastEffect missing text parameter");
            return;
        }

        // Parse color codes and create message
        Component message = DialogueEffect.parseColorCodes(text);

        // Send to all players on the server
        player.getServer().getPlayerList().getPlayers().forEach(p -> {
            p.sendSystemMessage(message);
        });

        CobblemonMerchants.LOGGER.debug("Broadcast message: {}", text);
    }
}
