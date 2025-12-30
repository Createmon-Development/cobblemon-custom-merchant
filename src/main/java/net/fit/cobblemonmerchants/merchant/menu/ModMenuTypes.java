package net.fit.cobblemonmerchants.merchant.menu;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, CobblemonMerchants.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MerchantTradeMenu>> MERCHANT_TRADE_MENU =
        MENU_TYPES.register("merchant_trade_menu",
            () -> IMenuTypeExtension.create(MerchantTradeMenu::new));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
