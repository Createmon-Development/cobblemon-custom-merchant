package net.fit.cobblemonmerchants.item.menu;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration for custom menu types
 */
public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, CobblemonMerchants.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RelicCoinBagMenu>> RELIC_COIN_BAG_MENU =
        MENU_TYPES.register("relic_coin_bag",
            () -> IMenuTypeExtension.create((containerId, playerInventory, extraData) ->
                new RelicCoinBagMenu(containerId, playerInventory, extraData)));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
