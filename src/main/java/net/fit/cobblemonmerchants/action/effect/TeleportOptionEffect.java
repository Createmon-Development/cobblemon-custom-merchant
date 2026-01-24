package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that displays a clickable teleport option in chat.
 * When clicked, executes a command to teleport the player.
 */
public class TeleportOptionEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (data.destination().isEmpty()) {
            CobblemonMerchants.LOGGER.warn("TeleportOptionEffect missing destination parameter");
            return;
        }

        String displayText = data.getText("[Teleport]");
        ActionEffectData.DestinationData dest = data.destination().get();

        // Build the teleport command
        String command = String.format("/npcteleport %s %d %d %d %s",
            player.getName().getString(),
            dest.x(),
            dest.y(),
            dest.z(),
            dest.getDimension());

        // Build hover text
        String hoverText = data.hoverText().orElse(
            String.format("Click to travel to %d, %d, %d", dest.x(), dest.y(), dest.z())
        );

        // Create clickable component
        MutableComponent message = DialogueEffect.parseColorCodes(displayText)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                .withUnderlined(true));

        // Add visual indicator that it's clickable
        MutableComponent fullMessage = Component.literal("  >> ")
            .withStyle(style -> style.withColor(0x55FFFF))
            .append(message)
            .append(Component.literal(" <<").withStyle(style -> style.withColor(0x55FFFF)));

        player.sendSystemMessage(fullMessage);
    }
}
