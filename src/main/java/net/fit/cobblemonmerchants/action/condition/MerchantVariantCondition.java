package net.fit.cobblemonmerchants.action.condition;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Condition that checks if the merchant has a specific variant.
 * Used for dialogue that depends on whether the Treasure Hunter is "housed" vs "default".
 */
public class MerchantVariantCondition implements Condition {

    @Override
    public boolean evaluate(ConditionData data, Player player, @Nullable CustomMerchantEntity merchant) {
        if (merchant == null || data.variant().isEmpty()) {
            return data.invert();
        }

        String expectedVariant = data.variant().get();
        String actualVariant = merchant.getMerchantVariant();

        boolean result = expectedVariant.equals(actualVariant);
        return data.invert() ? !result : result;
    }
}
