package net.fit.cobblemonmerchants.item;

import net.fit.cobblemonmerchants.item.custom.MerchantDebugStick;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = net.fit.cobblemonmerchants.CobblemonMerchants.MODID)
public class MerchantDebugStickHandler {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        // Check if player is holding debug stick
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof MerchantDebugStick debugStick)) {
            return;
        }

        // Check if attacking a merchant
        if (!(event.getTarget() instanceof CustomMerchantEntity merchant)) {
            return;
        }

        // Cancel the attack event so merchant doesn't get damaged
        event.setCanceled(true);

        // If sneaking, remove the merchant
        if (player.isShiftKeyDown()) {
            String merchantName = merchant.hasCustomName() ?
                merchant.getCustomName().getString() : "Custom Merchant";
            merchant.discard();
            player.sendSystemMessage(Component.literal("Removed merchant: " + merchantName));
        } else {
            player.sendSystemMessage(Component.literal("Hold Sneak while attacking to remove the merchant"));
        }
    }
}
