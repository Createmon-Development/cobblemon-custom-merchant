package net.fit.cobblemonmerchants.merchant;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CobblemonMerchants.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CustomMerchantEntity>> CUSTOM_MERCHANT =
            ENTITY_TYPES.register("custom_merchant", () -> EntityType.Builder
                    .of(CustomMerchantEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .ridingOffset(-0.6F)
                    .clientTrackingRange(10)
                    .build(CobblemonMerchants.MODID + ":custom_merchant"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}