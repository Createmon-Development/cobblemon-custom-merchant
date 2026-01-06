package net.fit.cobblemonmerchants.item.component;

import com.mojang.serialization.Codec;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom data components for mod items
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CobblemonMerchants.MODID);

    /**
     * Stores the number of relic coins in a coin purse
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RELIC_COIN_COUNT =
        DATA_COMPONENTS.register("relic_coin_count",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                .build());

    /**
     * Stores whether vacuum mode is enabled for the coin bag
     * True = coins go directly to bag (default), False = coins go to inventory
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AUTO_PICKUP_ENABLED =
        DATA_COMPONENTS.register("auto_pickup_enabled",
            () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                .build());

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
