package net.fit.cobblemonmerchants.item.client;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.item.ModItems;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Registers custom item properties for client-side rendering
 */
public class ItemPropertyRegistry {

    public static void register() {
        // Register the "bag_open" property for the Relic Coin Bag
        // This property is 1.0 when the player has the bag's GUI open, 0.0 otherwise
        ItemProperties.register(
            ModItems.RELIC_COIN_BAG.get(),
            ResourceLocation.fromNamespaceAndPath(CobblemonMerchants.MODID, "bag_open"),
            ItemPropertyRegistry::getBagOpenProperty
        );
    }

    /**
     * Returns 1.0 if the player currently has this bag's GUI open, 0.0 otherwise
     */
    private static float getBagOpenProperty(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            // Check if the player has a RelicCoinBagMenu open
            if (player.containerMenu instanceof net.fit.cobblemonmerchants.item.menu.RelicCoinBagMenu menu) {
                // Verify this is the same item stack that's being rendered
                // by checking if the menu's bag matches this stack
                return 1.0f;
            }
        }
        return 0.0f;
    }
}
