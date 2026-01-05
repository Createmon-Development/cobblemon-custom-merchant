package net.fit.cobblemonmerchants.network;

import io.netty.buffer.ByteBuf;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.fit.cobblemonmerchants.merchant.config.MerchantConfig;
import net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry;
import net.fit.cobblemonmerchants.merchant.menu.MerchantTradeMenu;
import net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Packet sent from client to server when a player clicks on the daily reward slot
 */
public record ClaimDailyRewardPacket() implements CustomPacketPayload {
    public static final Type<ClaimDailyRewardPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("cobblemoncustommerchants", "claim_daily_reward")
    );

    public static final StreamCodec<ByteBuf, ClaimDailyRewardPacket> STREAM_CODEC = StreamCodec.unit(new ClaimDailyRewardPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Sends this packet to the server
     */
    public static void send() {
        PacketDistributor.sendToServer(new ClaimDailyRewardPacket());
    }

    /**
     * Handles the packet on the server side
     */
    public static void handle(ClaimDailyRewardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.containerMenu instanceof MerchantTradeMenu menu) {
                    CustomMerchantEntity merchant = menu.getMerchant();
                    if (merchant != null) {
                        boolean success = tryGiveDailyReward(merchant, serverPlayer);
                        if (success) {
                            // Update the menu's claimed state so UI updates
                            menu.setDailyRewardClaimed(true);
                        }
                    }
                }
            }
        });
    }

    /**
     * Attempts to give the player their daily reward if eligible
     * @return true if reward was given, false otherwise
     */
    private static boolean tryGiveDailyReward(CustomMerchantEntity merchant, ServerPlayer player) {
        ResourceLocation traderId = merchant.getTraderId();
        if (traderId == null || !(merchant.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        // Get the merchant config
        MerchantConfig config = MerchantConfigRegistry.getConfig(traderId);
        if (config == null || config.dailyRewardConfig().isEmpty()) {
            return false;
        }

        MerchantConfig.DailyRewardConfig dailyConfig = config.dailyRewardConfig().get();
        String variant = merchant.getMerchantVariant();

        // Get the variant-specific reward
        MerchantConfig.DailyRewardVariant rewardVariant = dailyConfig.getVariant(variant);
        if (rewardVariant == null) {
            CobblemonMerchants.LOGGER.debug(
                "No daily reward variant '{}' found for merchant {}", variant, traderId);
            return false;
        }

        // Check if player has already claimed today
        DailyRewardManager rewardManager = DailyRewardManager.get(serverLevel);
        String merchantId = traderId.toString();

        // Determine the entity UUID for cooldown tracking based on sharedCooldown setting
        // If sharedCooldown is true (default), pass null so all merchants of this type share cooldown
        // If sharedCooldown is false, use the merchant's entity UUID for per-entity tracking
        java.util.UUID entityUUIDForCooldown = dailyConfig.sharedCooldown() ? null : merchant.getUUID();

        if (rewardManager.hasClaimedToday(player.getUUID(), merchantId, entityUUIDForCooldown)) {
            // Already claimed - play error sound
            player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.VILLAGER_NO,
                SoundSource.PLAYERS,
                0.5F,
                1.0F
            );
            return false;
        }

        // Calculate reward count
        int count = rewardVariant.getRandomCount(new java.util.Random());

        // Create the reward item stack
        ItemStack rewardStack = rewardVariant.item().copy();
        rewardStack.setCount(count);

        // Give the item to the player
        if (!player.getInventory().add(rewardStack)) {
            // Drop at player's feet if inventory is full
            player.drop(rewardStack, false);
        }

        // Record the claim
        rewardManager.recordClaim(player.getUUID(), merchantId, entityUUIDForCooldown);

        // Send message to player
        String message = rewardVariant.message().orElse("§aYou received your daily reward: §e%d x %s§a!");
        String itemName = rewardStack.getHoverName().getString();
        player.sendSystemMessage(Component.literal(
            message.replace("%d", String.valueOf(count)).replace("%s", itemName)
        ));

        // Play a reward sound
        player.level().playSound(
            null,
            player.blockPosition(),
            SoundEvents.PLAYER_LEVELUP,
            SoundSource.PLAYERS,
            0.5F,
            1.2F
        );

        CobblemonMerchants.LOGGER.info(
            "Gave daily reward to {}: {} x {} (merchant: {}, sharedCooldown: {}, entityUUID: {})",
            player.getName().getString(), count, itemName, merchantId, dailyConfig.sharedCooldown(),
            entityUUIDForCooldown != null ? entityUUIDForCooldown : "shared");

        return true;
    }
}
