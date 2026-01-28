package net.fit.cobblemonmerchants.item.custom;

import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.fit.cobblemonmerchants.npc.AssistantNPCEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
 * Debug stick for managing custom merchant and NPC entities.
 * - Right click on merchant/NPC: Pick up and store in stick
 * - Right click on block: Place stored entity
 * - Sneak + Left click on merchant/NPC: Remove entity
 */
public class MerchantDebugStick extends Item {
    private static final String TAG_HAS_ENTITY = "HasEntity";
    private static final String TAG_ENTITY_DATA = "EntityData";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_ENTITY_TYPE = "EntityType"; // "merchant" or "npc"

    // Legacy support
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

        boolean hasEntity = hasStoredEntity(stack);

        if (hasEntity) {
            CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            String entityName = tag.contains(TAG_ENTITY_NAME) ? tag.getString(TAG_ENTITY_NAME) : tag.getString(TAG_MERCHANT_NAME);
            String entityType = tag.contains(TAG_ENTITY_TYPE) ? tag.getString(TAG_ENTITY_TYPE) : "merchant";

            String typeLabel = entityType.equals("npc") ? "NPC" : "Merchant";
            tooltipComponents.add(Component.literal("Stored " + typeLabel + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entityName.isEmpty() ? "Unknown" : entityName)
                    .withStyle(ChatFormatting.GOLD)));
        } else {
            tooltipComponents.add(Component.literal("No entity stored")
                .withStyle(ChatFormatting.DARK_GRAY)
                .withStyle(ChatFormatting.ITALIC));
        }

        tooltipComponents.add(Component.literal(""));
        tooltipComponents.add(Component.literal("Right-click merchant/NPC: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Pick up & store").withStyle(ChatFormatting.WHITE)));
        tooltipComponents.add(Component.literal("Right-click block: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Place stored entity").withStyle(ChatFormatting.WHITE)));
        tooltipComponents.add(Component.literal("Sneak + Left-click: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Remove entity").withStyle(ChatFormatting.RED)));
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

        // If we have a stored entity, place it
        if (hasStoredEntity(stack)) {
            return placeEntity(level, stack, pos, player, hand);
        }

        return InteractionResult.PASS;
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                           @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        // Handle both CustomMerchantEntity and AssistantNPCEntity
        boolean isMerchant = entity instanceof CustomMerchantEntity;
        boolean isNPC = entity instanceof AssistantNPCEntity;

        if (!isMerchant && !isNPC) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        String entityTypeName = isNPC ? "NPC" : "merchant";

        // Sneak + interact tells player to use left-click
        if (player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal("Use Sneak + Left-click (attack) to remove " + entityTypeName).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.CONSUME;
        }

        // Regular right-click to pick up entity
        if (!hasStoredEntity(stack)) {
            if (isMerchant) {
                return pickupMerchant(stack, (CustomMerchantEntity) entity, player, hand);
            } else {
                return pickupNPC(stack, (AssistantNPCEntity) entity, player, hand);
            }
        } else {
            // Already have an entity stored
            player.sendSystemMessage(Component.literal("Debug stick already has an entity stored! Place it first."));
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

        // Create a new ItemStack with the entity data
        ItemStack newStack = stack.copy();
        CompoundTag stackTag = newStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        stackTag.putBoolean(TAG_HAS_ENTITY, true);
        stackTag.put(TAG_ENTITY_DATA, merchantData);
        stackTag.putString(TAG_ENTITY_NAME, merchantName);
        stackTag.putString(TAG_ENTITY_TYPE, "merchant");
        newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(stackTag));

        // Replace the item in the player's hand to force client sync
        player.setItemInHand(hand, newStack);

        // Remove the merchant from the world
        merchant.discard();

        player.sendSystemMessage(Component.literal("Picked up merchant: " + merchantName));

        return InteractionResult.SUCCESS;
    }

    private InteractionResult pickupNPC(ItemStack stack, AssistantNPCEntity npc, Player player, InteractionHand hand) {
        // Store NPC data in the stick
        CompoundTag npcData = new CompoundTag();
        npc.addAdditionalSaveData(npcData);

        String npcName = npc.hasCustomName() ?
            npc.getCustomName().getString() : npc.getNPCDisplayName();

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("DEBUG: Picking up NPC: {}", npcName);

        // Create a new ItemStack with the entity data
        ItemStack newStack = stack.copy();
        CompoundTag stackTag = newStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        stackTag.putBoolean(TAG_HAS_ENTITY, true);
        stackTag.put(TAG_ENTITY_DATA, npcData);
        stackTag.putString(TAG_ENTITY_NAME, npcName);
        stackTag.putString(TAG_ENTITY_TYPE, "npc");
        newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(stackTag));

        // Replace the item in the player's hand to force client sync
        player.setItemInHand(hand, newStack);

        // Remove the NPC from the world
        npc.discard();

        player.sendSystemMessage(Component.literal("Picked up NPC: " + npcName));

        return InteractionResult.SUCCESS;
    }

    private InteractionResult placeEntity(Level level, ItemStack stack, BlockPos pos, Player player, InteractionHand hand) {
        // Get entity data from stick
        CompoundTag stackTag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();

        // Support both new and legacy tag names
        CompoundTag entityData = stackTag.contains(TAG_ENTITY_DATA) ?
            stackTag.getCompound(TAG_ENTITY_DATA) : stackTag.getCompound(TAG_MERCHANT_DATA);
        String entityName = stackTag.contains(TAG_ENTITY_NAME) ?
            stackTag.getString(TAG_ENTITY_NAME) : stackTag.getString(TAG_MERCHANT_NAME);
        String entityType = stackTag.getString(TAG_ENTITY_TYPE);

        // Legacy support: if no entity type, assume merchant
        if (entityType.isEmpty()) {
            entityType = "merchant";
        }

        if (entityData.isEmpty()) {
            player.sendSystemMessage(Component.literal("No entity data found!"));
            return InteractionResult.FAIL;
        }

        // Place on top of the clicked block
        BlockPos spawnPos = pos.above();
        double x = spawnPos.getX() + 0.5;
        double y = spawnPos.getY();
        double z = spawnPos.getZ() + 0.5;

        // Calculate rotation to face the player
        double dx = player.getX() - x;
        double dz = player.getZ() - z;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;

        Entity spawnedEntity;
        if (entityType.equals("npc")) {
            // Create new NPC entity
            AssistantNPCEntity npc = new AssistantNPCEntity(
                net.fit.cobblemonmerchants.merchant.ModEntities.ASSISTANT_NPC.get(), level);
            npc.setPos(x, y, z);
            npc.readAdditionalSaveData(entityData);

            // Restore custom name
            if (!entityName.isEmpty() && !entityName.equals("NPC")) {
                npc.setCustomName(Component.literal(entityName));
                npc.setCustomNameVisible(true);
            }

            npc.setYRot(yaw);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            spawnedEntity = npc;
        } else {
            // Create new merchant entity
            CustomMerchantEntity merchant = new CustomMerchantEntity(
                net.fit.cobblemonmerchants.merchant.ModEntities.CUSTOM_MERCHANT.get(), level);
            merchant.setPos(x, y, z);
            merchant.readAdditionalSaveData(entityData);

            // Restore custom name
            if (!entityName.isEmpty() && !entityName.equals("Custom Merchant")) {
                merchant.setCustomName(Component.literal(entityName));
                merchant.setCustomNameVisible(true);
            }

            merchant.setYRot(yaw);
            merchant.setYHeadRot(yaw);
            merchant.setYBodyRot(yaw);
            spawnedEntity = merchant;
        }

        // Spawn the entity
        level.addFreshEntity(spawnedEntity);

        // Clear the stored data
        ItemStack newStack = stack.copy();
        CompoundTag newStackTag = newStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        newStackTag.putBoolean(TAG_HAS_ENTITY, false);
        newStackTag.putBoolean(TAG_HAS_MERCHANT, false); // Legacy
        newStackTag.remove(TAG_ENTITY_DATA);
        newStackTag.remove(TAG_ENTITY_NAME);
        newStackTag.remove(TAG_ENTITY_TYPE);
        newStackTag.remove(TAG_MERCHANT_DATA); // Legacy
        newStackTag.remove(TAG_MERCHANT_NAME); // Legacy
        newStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.of(newStackTag));

        // Replace the item in the player's hand to force client sync
        player.setItemInHand(hand, newStack);

        String typeLabel = entityType.equals("npc") ? "NPC" : "merchant";
        player.sendSystemMessage(Component.literal("Placed " + typeLabel + ": " +
            (entityName.isEmpty() ? "Unknown" : entityName)));

        return InteractionResult.SUCCESS;
    }

    private boolean hasStoredEntity(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        // Support both new and legacy tags
        return tag.getBoolean(TAG_HAS_ENTITY) || tag.getBoolean(TAG_HAS_MERCHANT);
    }
}