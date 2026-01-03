package net.fit.cobblemonmerchants.item;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.item.custom.MerchantDebugStick;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CobblemonMerchants.MODID);

    // Merchant Debug Stick
    public static final DeferredItem<Item> MERCHANT_DEBUG_STICK = ITEMS.register("merchant_debug_stick",
            () -> new MerchantDebugStick(new Item.Properties().stacksTo(1)));

    // Relic Coin Bag
    public static final DeferredItem<Item> RELIC_COIN_BAG = ITEMS.register("relic_coin_bag",
            () -> new net.fit.cobblemonmerchants.item.custom.RelicCoinBagItem(new Item.Properties()
                    .stacksTo(1)
                    .component(net.fit.cobblemonmerchants.item.component.ModDataComponents.RELIC_COIN_COUNT.get(), 0)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}