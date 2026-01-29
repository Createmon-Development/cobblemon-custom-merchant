package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that applies a potion effect to the player.
 */
public class ApplyPotionEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (data.effect().isEmpty()) {
            CobblemonMerchants.LOGGER.warn("ApplyPotionEffect missing effect parameter");
            return;
        }

        String effectId = data.effect().get();
        int duration = data.duration().orElse(600); // Default 30 seconds (in ticks)
        int amplifier = data.amplifier().orElse(0); // Default level 1

        try {
            ResourceLocation effectLoc = ResourceLocation.parse(effectId);
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectLoc);

            if (effect == null) {
                CobblemonMerchants.LOGGER.warn("Unknown effect in ApplyPotionEffect: {}", effectId);
                return;
            }

            MobEffectInstance effectInstance = new MobEffectInstance(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                duration,
                amplifier,
                false, // ambient
                true,  // visible
                true   // show icon
            );

            player.addEffect(effectInstance);

            CobblemonMerchants.LOGGER.debug("Applied effect {} (duration: {}, amplifier: {}) to player {}",
                effectId, duration, amplifier, player.getName().getString());

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.error("Error in ApplyPotionEffect for effect {}: {}", effectId, e.getMessage());
        }
    }
}
