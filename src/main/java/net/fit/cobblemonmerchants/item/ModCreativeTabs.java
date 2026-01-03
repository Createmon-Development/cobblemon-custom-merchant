package net.fit.cobblemonmerchants.item;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CobblemonMerchants.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COBBLEMON_MERCHANTS_TAB =
        CREATIVE_MODE_TABS.register("cobblemon_merchants_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.cobblemoncustommerchants"))
            .icon(() -> new ItemStack(ModItems.RELIC_COIN_BAG.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.MERCHANT_DEBUG_STICK.get());
                output.accept(ModItems.RELIC_COIN_BAG.get());
            })
            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
