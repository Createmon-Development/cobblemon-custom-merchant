package net.fit.cobblemonmerchants.item.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

/**
 * Custom renderer for displaying coin counts in "1k", "1.1k" format
 */
public class CoinCountRenderer {

    /**
     * Formats a coin count for display
     * - 1-999: exact count
     * - 1000-9999: "1k", "1.1k", "1.2k", etc. (increments of 100)
     * - 10000+: "10k", "11k", etc. (increments of 1000)
     */
    public static String formatCoinCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 10000) {
            // Show one decimal place for thousands
            int thousands = count / 1000;
            int hundreds = (count % 1000) / 100;
            if (hundreds == 0) {
                return thousands + "k";
            } else {
                return thousands + "." + hundreds + "k";
            }
        } else {
            // For 10k+, just show whole thousands
            int thousands = count / 1000;
            return thousands + "k";
        }
    }

    /**
     * Renders the formatted count on an item stack
     * This is used by the GUI to display the coin count properly
     */
    public static void renderCount(PoseStack poseStack, Font font, ItemStack stack, int x, int y, int count) {
        if (count > 1) {
            String text = formatCoinCount(count);
            poseStack.pushPose();
            poseStack.translate(0.0, 0.0, 200.0); // Render on top

            MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
            font.drawInBatch(
                text,
                (float)(x + 19 - 2 - font.width(text)),
                (float)(y + 6 + 3),
                16777215, // White color
                true, // Drop shadow
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                15728880 // Full brightness
            );
            buffer.endBatch();

            poseStack.popPose();
        }
    }
}
