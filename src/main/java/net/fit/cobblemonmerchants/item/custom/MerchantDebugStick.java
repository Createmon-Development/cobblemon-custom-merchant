package net.fit.cobblemonmerchants.item.custom;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Debug stick for managing custom merchant entities.
 * - Right click on merchant: Pick up and store in stick
 * - Right click on block: Place stored merchant
 * - Sneak + Left click on merchant: Remove merchant
 */
public class MerchantDebugStick extends Item {
    private static final String TAG_HAS_MERCHANT = "HasMerchant";
    private static final String TAG_MERCHANT_DATA = "MerchantData";
    private static final String TAG_MERCHANT_NAME = "MerchantName";

    public MerchantDebugStick(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        // Always show enchanted glint
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        boolean hasMerch = hasMerchant(stack);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("DEBUG TOOLTIP: hasMerchant = {}", hasMerch);

        if (hasMerch) {
            CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            String merchantName = tag.getString(TAG_MERCHANT_NAME);
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("DEBUG TOOLTIP: merchantName = {}", merchantName);

            tooltipComponents.add(Component.literal("Stored Merchant: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(merchantName.isEmpty() ? "Unknown" : merchantName)
                    .withStyle(ChatFormatting.GOLD)));
        } else {
            tooltipComponents.add(Component.literal("No merchant stored")
                .withStyle(ChatFormatting.DARK_GRAY)
                .withStyle(ChatFormatting.ITALIC));
        }

        tooltipComponents.add(Component.literal(""));
        tooltipComponents.add(Component.literal("Right-click merchant: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Pick up & store").withStyle(ChatFormatting.WHITE)));
        tooltipComponents.add(Component.literal("Right-click block: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Place stored merchant").withStyle(ChatFormatting.WHITE)));
        tooltipComponents.add(Component.literal("Sneak + Left-click merchant: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Remove merchant").withStyle(ChatFormatting.RED)));
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        InteractionHand hand = context.getHand();

        if (player == null) {
            return InteractionResult.FAIL;
        }

        // If we have a stored merchant, place it
        if (hasMerchant(stack)) {
            return placeMerchant(level, stack, pos, player, hand);
        }

        return InteractionResult.PASS;
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                           @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        if (!(entity instanceof CustomMerchantEntity merchant)) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // Sneak + interact tells player to use left-click
        if (player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal("Use Sneak + Left-click (attack) to remove merchant").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.CONSUME;
        }

        // Regular right-click to pick up merchant
        if (!hasMerchant(stack)) {
            return pickupMerchant(stack, merchant, player, hand);
        } else {
            // Already have a merchant stored
            player.sendSystemMessage(Component.literal("Debug stick already has a merchant stored! Place it first."));
            return InteractionResult.FAIL;
        }
    }

    private InteractionResult pickupMerchant(ItemStack stack, CustomMerchantEntity merchant, Player player, InteractionHand hand) {
        // Store merchant data in the stick
        CompoundTag merchantData = new CompoundTag();
        merchant.addAdditionalSaveData(merchantData);

        String merchantName = merchant.hasCustomName() ?
            merchant.getCustomName().getString() : "Custom Merchant";

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("DEBUG: Picking up merchant: {}", merchantName);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("DEBUG: Merchant data size: {} bytes", merchantData.toString().length());

        // Create a new ItemStack with the merchant data
        ItemStack newStack = stack.copy();
        CompoundTag stackTag = newStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        stackTag.putBoolean(TAG_HAS_MERCHANT, true);
        stackTag.put(TAG_MERCHANT_DATA, merchantData);
        stackTag.putString(TAG_MERCHANT_NAME, merchantName);
        newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(stackTag));

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("DEBUG: Stored in stick, hasMerchant: {}", hasMerchant(newStack));

        // Replace the item in the player's hand to force client sync
        player.setItemInHand(hand, newStack);

        // Remove the merchant from the world
        merchant.discard();

        player.sendSystemMessage(Component.literal("Picked up merchant: " + merchantName));

        return InteractionResult.SUCCESS;
    }

    private InteractionResult placeMerchant(Level level, ItemStack stack, BlockPos pos, Player player, InteractionHand hand) {
        // Get merchant data from stick
        CompoundTag stackTag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        CompoundTag merchantData = stackTag.getCompound(TAG_MERCHANT_DATA);
        String merchantName = stackTag.getString(TAG_MERCHANT_NAME);

        if (merchantData.isEmpty()) {
            player.sendSystemMessage(Component.literal("No merchant data found!"));
            return InteractionResult.FAIL;
        }

        // Create new merchant entity
        CustomMerchantEntity merchant = new CustomMerchantEntity(
            net.fit.cobblemonmerchants.merchant.ModEntities.CUSTOM_MERCHANT.get(), level);

        // Place on top of the clicked block
        BlockPos spawnPos = pos.above();
        merchant.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // Restore merchant data
        merchant.readAdditionalSaveData(merchantData);

        // Explicitly restore custom name if it exists
        if (!merchantName.isEmpty() && !merchantName.equals("Custom Merchant")) {
            merchant.setCustomName(Component.literal(merchantName));
            merchant.setCustomNameVisible(true);
        }

        // Calculate rotation to face the player (done after data restoration to override saved rotation)
        double dx = player.getX() - merchant.getX();
        double dz = player.getZ() - merchant.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        merchant.setYRot(yaw);
        merchant.setYHeadRot(yaw);
        merchant.setYBodyRot(yaw);

        // Spawn the merchant
        level.addFreshEntity(merchant);

        // Create a new ItemStack with cleared data
        ItemStack newStack = stack.copy();
        CompoundTag newStackTag = newStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        newStackTag.putBoolean(TAG_HAS_MERCHANT, false);
        newStackTag.remove(TAG_MERCHANT_DATA);
        newStackTag.remove(TAG_MERCHANT_NAME);
        newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(newStackTag));

        // Replace the item in the player's hand to force client sync
        player.setItemInHand(hand, newStack);

        player.sendSystemMessage(Component.literal("Placed merchant: " +
            (merchantName.isEmpty() ? "Custom Merchant" : merchantName)));

        return InteractionResult.SUCCESS;
    }

    private boolean hasMerchant(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        return tag.getBoolean(TAG_HAS_MERCHANT);
    }
}