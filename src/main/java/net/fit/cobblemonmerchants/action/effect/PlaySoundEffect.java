package net.fit.cobblemonmerchants.action.effect;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Effect that plays a sound at the merchant's or player's location.
 */
public class PlaySoundEffect implements ActionEffect {

    @Override
    public void execute(ActionEffectData data, ServerPlayer player, @Nullable CustomMerchantEntity merchant) {
        if (data.sound().isEmpty()) {
            CobblemonMerchants.LOGGER.warn("PlaySoundEffect missing sound parameter");
            return;
        }

        String soundId = data.sound().get();
        float volume = data.getVolume(1.0f);
        float pitch = data.getPitch(1.0f);

        try {
            ResourceLocation soundLoc = ResourceLocation.parse(soundId);
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLoc);

            if (soundEvent == null) {
                // Try creating a sound event from the resource location
                soundEvent = SoundEvent.createVariableRangeEvent(soundLoc);
            }

            // Play at merchant location if available, otherwise at player
            double x, y, z;
            if (merchant != null) {
                x = merchant.getX();
                y = merchant.getY();
                z = merchant.getZ();
            } else {
                x = player.getX();
                y = player.getY();
                z = player.getZ();
            }

            player.serverLevel().playSound(
                null, // No excluding player - everyone hears it
                x, y, z,
                soundEvent,
                SoundSource.NEUTRAL,
                volume,
                pitch
            );

            CobblemonMerchants.LOGGER.debug("Played sound {} at ({}, {}, {})", soundId, x, y, z);

        } catch (Exception e) {
            CobblemonMerchants.LOGGER.error("Error in PlaySoundEffect for sound {}: {}", soundId, e.getMessage());
        }
    }
}
