package net.fit.cobblemonmerchants.merchant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom merchant entity with Hypixel Skyblock-style chest GUI.
 * Displays result items in chest; tooltips show cost. Supports item tag matching.
 * Immobile, invincible, untargetable.
 */
public class CustomMerchantEntity extends Villager {
    private static final String TAG_TRADER_ID = "TraderId";
    private static final String TAG_PLAYER_SKIN_NAME = "PlayerSkinName";
    private static final String TAG_VILLAGER_BIOME = "VillagerBiome";
    private static final String TAG_VILLAGER_PROFESSION = "VillagerProfession";
    private static final String TAG_OFFERS = "Offers";
    private static final String TAG_VARIANT = "Variant";

    private static final EntityDataAccessor<String> DATA_PLAYER_SKIN_NAME =
        SynchedEntityData.defineId(CustomMerchantEntity.class, EntityDataSerializers.STRING);

    private ResourceLocation traderId;
    private MerchantOffers offers = new MerchantOffers();
    private java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> tradeEntries = new java.util.ArrayList<>();
    private Player tradingPlayer;
    private String variant = "default"; // The variant of this merchant (affects daily rewards)

    public CustomMerchantEntity(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PLAYER_SKIN_NAME, "");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.0) // No movement (immobile)
                .add(Attributes.FOLLOW_RANGE, 0.0)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0); // Cannot be pushed
    }

    public void setTraderId(ResourceLocation traderId) {
        this.traderId = traderId;
    }

    public ResourceLocation getTraderId() {
        return this.traderId;
    }

    public void setPlayerSkinName(String name) {
        this.entityData.set(DATA_PLAYER_SKIN_NAME, name);
    }

    public String getPlayerSkinName() {
        return this.entityData.get(DATA_PLAYER_SKIN_NAME);
    }

    public void setVillagerBiome(String biome) {
        VillagerType type;
        try {
            ResourceLocation typeId = biome.contains(":")
                ? ResourceLocation.parse(biome)
                : ResourceLocation.withDefaultNamespace(biome);
            type = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.get(typeId);
            if (type == null) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Unknown villager type: {}, defaulting to plains", typeId);
                type = VillagerType.PLAINS;
            }
        } catch (Exception e) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Error parsing villager type: {}", biome, e);
            type = VillagerType.PLAINS;
        }
        VillagerData current = getVillagerData();
        VillagerData newData = new VillagerData(type, current.getProfession(), current.getLevel());
        setVillagerData(newData);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Set villager biome to {} (type: {})", biome, type);
    }

    public String getVillagerBiome() {
        ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE
            .getKey(getVillagerData().getType());
        return typeId != null ? typeId.getPath() : "plains";
    }

    public void setVillagerProfession(String profession) {
        VillagerProfession prof;
        try {
            // Handle both "mason" and "minecraft:mason" formats
            ResourceLocation profId = profession.contains(":")
                ? ResourceLocation.parse(profession)
                : ResourceLocation.withDefaultNamespace(profession);
            prof = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(profId);
            if (prof == null) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Unknown villager profession: {}, defaulting to none", profId);
                prof = VillagerProfession.NONE;
            }
        } catch (Exception e) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Error parsing villager profession: {}", profession, e);
            prof = VillagerProfession.NONE;
        }
        VillagerData current = getVillagerData();
        VillagerData newData = new VillagerData(current.getType(), prof, current.getLevel());
        setVillagerData(newData);
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Set villager profession to {} (profession: {})", profession, prof);
    }

    public String getVillagerProfession() {
        ResourceLocation profId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
            .getKey(getVillagerData().getProfession());
        return profId != null ? profId.getPath() : "none";
    }

    public void setTradingPlayer(Player player) {
        this.tradingPlayer = player;
    }

    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public void setMerchantVariant(String variant) {
        this.variant = variant != null ? variant : "default";
    }

    public String getMerchantVariant() {
        return this.variant;
    }

    public void setOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    public MerchantOffers getOffers() {
        return this.offers;
    }

    public java.util.List<net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry> getTradeEntries() {
        return this.tradeEntries;
    }

    @Override
    protected void registerGoals() {
        // No AI goals - the entity should not move or have any behavior
        // Clear all goals that vanilla Villager adds
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
    }

    @Override
    protected void customServerAiStep() {
        // Don't call super - prevents villager from running AI that changes profession
        // Gravity and physics are handled in aiStep()
    }

    @Override
    public void tick() {
        // Call LivingEntity.tick() which includes gravity, but skip Villager-specific logic
        // We override aiStep() below to prevent horizontal movement while allowing vertical (gravity)
        super.tick();
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        // Check if player is holding the debug stick - let it handle the interaction (pickup/place)
        if (player.getItemInHand(hand).getItem() instanceof net.fit.cobblemonmerchants.item.custom.MerchantDebugStick) {
            return InteractionResult.PASS;
        }

        if (!this.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            this.setTradingPlayer(player);

            // Check for action configuration
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig config =
                net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(this.traderId);

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "[MobInteract] traderId={}, config={}, actionId={}",
                this.traderId,
                config != null ? "present" : "null",
                config != null ? config.actionId().orElse("none") : "N/A");

            if (config != null && config.actionId().isPresent()) {
                net.minecraft.resources.ResourceLocation actionId =
                    net.minecraft.resources.ResourceLocation.parse(config.actionId().get());

                if (config.actionBeforeTrade()) {
                    // Execute dialogue with conditions required
                    // If quest dialogue conditions match: show dialogue, do NOT open menu
                    // If no quest conditions match: open menu silently
                    net.fit.cobblemonmerchants.action.ActionExecutor.executeDialogueOrFallback(
                        serverPlayer, this, actionId,
                        () -> openCustomTradeScreen(serverPlayer)  // Only opens when no dialogue found
                    );
                } else {
                    // Dialogue only, no trade menu - show any matching dialogue including default greeting
                    net.fit.cobblemonmerchants.action.ActionExecutor.executeDialogue(
                        serverPlayer, this, actionId, null
                    );
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            // Default: open trade screen directly
            openCustomTradeScreen(serverPlayer);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /**
     * Gets the merchant's display name for dialogue speakers.
     */
    public String getMerchantDisplayName() {
        net.fit.cobblemonmerchants.merchant.config.MerchantConfig config =
            net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(this.traderId);
        if (config != null) {
            return config.displayName();
        }
        Component customName = this.getCustomName();
        if (customName != null) {
            return customName.getString();
        }
        return "Merchant";
    }

    /**
     * Opens Hypixel Skyblock-style chest trading screen
     */
    private void openCustomTradeScreen(net.minecraft.server.level.ServerPlayer serverPlayer) {
        serverPlayer.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return CustomMerchantEntity.this.getDisplayName();
            }

            @Override
            public @NotNull net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId,
                    @NotNull net.minecraft.world.entity.player.Inventory playerInventory,
                    @NotNull net.minecraft.world.entity.player.Player player) {
                return new net.fit.cobblemonmerchants.merchant.menu.MerchantTradeMenu(
                    containerId, playerInventory, CustomMerchantEntity.this);
            }
        }, buf -> {
            buf.writeInt(this.getId());
            // Sync trade entries to client
            buf.writeInt(tradeEntries.size());
            for (net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry entry : tradeEntries) {
                buf.writeJsonWithCodec(net.fit.cobblemonmerchants.merchant.config.ItemRequirement.CODEC, entry.input());
                buf.writeBoolean(entry.secondInput().isPresent());
                if (entry.secondInput().isPresent()) {
                    buf.writeJsonWithCodec(net.fit.cobblemonmerchants.merchant.config.ItemRequirement.CODEC, entry.secondInput().get());
                }
                net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buf, entry.output());
                buf.writeInt(entry.outputCount()); // Uncapped output count for counts > 64
                buf.writeInt(entry.maxUses());
                buf.writeInt(entry.villagerXp());
                buf.writeFloat(entry.priceMultiplier());
                buf.writeBoolean(entry.tradeDisplayName().isPresent());
                if (entry.tradeDisplayName().isPresent()) {
                    buf.writeUtf(entry.tradeDisplayName().get());
                }
                buf.writeBoolean(entry.position().isPresent());
                if (entry.position().isPresent()) {
                    buf.writeInt(entry.position().get());
                }
                // Lucky trade info for client display
                buf.writeBoolean(entry.isLucky());
                buf.writeDouble(entry.luckyOutputMultiplier());
                buf.writeDouble(entry.luckyMaxUsesMultiplier());
            }

            // Sync daily reward info to client
            writeDailyRewardInfo(buf, serverPlayer);

            // Sync reset timer position from config
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig config =
                net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(this.traderId);
            int resetTimerPosition = config != null ? config.resetTimerPosition()
                : net.fit.cobblemonmerchants.merchant.config.MerchantConfig.DEFAULT_RESET_TIMER_POSITION;
            buf.writeInt(resetTimerPosition);

            // Always sync reset timer countdown (even if no daily reward is configured)
            long millisUntilReset = net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager.getTimeUntilReset().toMillis();
            buf.writeLong(millisUntilReset);
        });
    }

    /**
     * Writes daily reward display info to the packet buffer
     */
    private void writeDailyRewardInfo(net.minecraft.network.FriendlyByteBuf buf, net.minecraft.server.level.ServerPlayer player) {
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: traderId={}, variant={}", this.traderId, this.variant);

        if (this.traderId == null || !(this.level() instanceof ServerLevel serverLevel)) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: No traderId or not ServerLevel");
            buf.writeBoolean(false); // No daily reward
            return;
        }

        net.fit.cobblemonmerchants.merchant.config.MerchantConfig config =
            net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(this.traderId);

        if (config == null || config.dailyRewardConfig().isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: No config or no dailyRewardConfig. config={}, hasDailyReward={}",
                config != null, config != null && config.dailyRewardConfig().isPresent());
            buf.writeBoolean(false); // No daily reward
            return;
        }

        net.fit.cobblemonmerchants.merchant.config.MerchantConfig.DailyRewardConfig dailyConfig =
            config.dailyRewardConfig().get();

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: Looking for variant '{}' in variants: {}",
            this.variant, dailyConfig.variants().keySet());

        net.fit.cobblemonmerchants.merchant.config.MerchantConfig.DailyRewardVariant rewardVariant =
            dailyConfig.getVariant(this.variant);

        if (rewardVariant == null || rewardVariant.displayPosition().isEmpty()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: No rewardVariant or no displayPosition. rewardVariant={}, hasDisplayPos={}",
                rewardVariant != null, rewardVariant != null && rewardVariant.displayPosition().isPresent());
            buf.writeBoolean(false); // No display position configured
            return;
        }

        // Check if player has already claimed today
        net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager rewardManager =
            net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager.get(serverLevel);

        String merchantId = this.traderId.toString();
        // If sharedCooldown is true (default), pass null for entityUUID so all merchants share cooldown
        // If sharedCooldown is false, pass this entity's UUID for per-entity tracking
        java.util.UUID entityUUIDForCooldown = dailyConfig.sharedCooldown() ? null : this.getUUID();
        boolean hasClaimed = rewardManager.hasClaimedToday(player.getUUID(), merchantId, entityUUIDForCooldown);

        buf.writeBoolean(true); // Has daily reward display
        net.minecraft.network.RegistryFriendlyByteBuf registryBuf = (net.minecraft.network.RegistryFriendlyByteBuf) buf;
        net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(registryBuf, rewardVariant.item());
        buf.writeInt(rewardVariant.displayPosition().get());
        buf.writeBoolean(hasClaimed);
        // Write milliseconds until reset (server timezone) instead of formatted string
        long millisUntilReset = net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager.getTimeUntilReset().toMillis();
        buf.writeLong(millisUntilReset);
        buf.writeInt(rewardVariant.minCount());
        buf.writeInt(rewardVariant.maxCount());
        buf.writeBoolean(dailyConfig.sharedCooldown());
        // Write entity UUID for per-entity cooldown tracking
        buf.writeUUID(this.getUUID());

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: SUCCESS! position={}, claimed={}, resetIn={}ms, sharedCooldown={}, entityUUID={}, item={}, minCount={}, maxCount={}",
            rewardVariant.displayPosition().get(), hasClaimed, millisUntilReset, dailyConfig.sharedCooldown(), this.getUUID(), rewardVariant.item(), rewardVariant.minCount(), rewardVariant.maxCount());
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // Check if attacker is player with debug stick + sneaking = remove merchant
        if (source.getEntity() instanceof Player player && player.isShiftKeyDown()) {
            if (player.getMainHandItem().getItem() instanceof net.fit.cobblemonmerchants.item.custom.MerchantDebugStick) {
                if (!this.level().isClientSide) {
                    this.discard();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Removed merchant: " + getMerchantDisplayName())
                        .withStyle(net.minecraft.ChatFormatting.RED));
                }
                return true;
            }
        }
        // Invincible - cannot be hurt
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(@NotNull net.minecraft.world.entity.Entity entity) {
        // Don't push other entities
    }

    @Override
    protected void pushEntities() {
        // Don't push entities
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return false; // Ensure gravity is enabled
    }

    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void aiStep() {
        // Call parent to handle gravity and other physics, but prevent AI movement
        super.aiStep();

        // Force the entity to stay in place horizontally, but allow gravity
        this.setDeltaMovement(this.getDeltaMovement().multiply(0, 1, 0));

        // Lock rotation - prevent the entity from rotating its body back to south
        this.setYBodyRot(this.getYRot());
        this.yBodyRotO = this.getYRot();
        this.setYHeadRot(this.getYRot());
        this.yHeadRotO = this.getYRot();
    }

    @Override
    public boolean requiresCustomPersistence() {
        // Always persist - never despawn naturally
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Never remove due to distance from players
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        // Cannot be targeted by hostile mobs
        return false;
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return true; // Visible to players
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        // Wrap super call in try-catch to prevent save failures
        // The Villager parent class may try to save brain/gossip data that isn't properly initialized
        try {
            super.addAdditionalSaveData(tag);
        } catch (Exception e) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.debug(
                "Villager parent save had issues (expected for custom merchants): {}", e.getMessage());
            // Continue saving our custom data even if parent save fails
        }

        if (this.traderId != null) {
            tag.putString(TAG_TRADER_ID, this.traderId.toString());
        }
        tag.putString(TAG_VARIANT, this.variant);
        String skinName = getPlayerSkinName();
        if (!skinName.isEmpty()) {
            tag.putString(TAG_PLAYER_SKIN_NAME, skinName);
        }
        VillagerData.CODEC.encodeStart(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), getVillagerData())
            .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to save villager data: {}", error))
            .ifPresent(data -> tag.put("VillagerData", data));

        // Save offers
        if (!this.offers.isEmpty()) {
            ListTag offersList = new ListTag();
            for (MerchantOffer offer : this.offers) {
                MerchantOffer.CODEC.encodeStart(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), offer)
                    .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to save offer: {}", error))
                    .ifPresent(offersList::add);
            }
            tag.put(TAG_OFFERS, offersList);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        // Wrap super call in try-catch to prevent load failures
        try {
            super.readAdditionalSaveData(tag);
        } catch (Exception e) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.debug(
                "Villager parent load had issues (expected for custom merchants): {}", e.getMessage());
            // Continue loading our custom data even if parent load fails
        }

        if (tag.contains(TAG_TRADER_ID)) {
            this.traderId = ResourceLocation.parse(tag.getString(TAG_TRADER_ID));
        }
        if (tag.contains(TAG_VARIANT)) {
            this.variant = tag.getString(TAG_VARIANT);
        }
        if (tag.contains(TAG_PLAYER_SKIN_NAME)) {
            setPlayerSkinName(tag.getString(TAG_PLAYER_SKIN_NAME));
        }
        if (tag.contains("VillagerData")) {
            VillagerData.CODEC.parse(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get("VillagerData"))
                .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to load villager data: {}", error))
                .ifPresent(this::setVillagerData);
        } else {
            // Legacy support: load from old string format
            if (tag.contains(TAG_VILLAGER_BIOME)) {
                setVillagerBiome(tag.getString(TAG_VILLAGER_BIOME));
            }
            if (tag.contains(TAG_VILLAGER_PROFESSION)) {
                setVillagerProfession(tag.getString(TAG_VILLAGER_PROFESSION));
            }
        }

        // Load offers from saved NBT
        if (tag.contains(TAG_OFFERS)) {
            this.offers = new MerchantOffers();
            ListTag offersList = tag.getList(TAG_OFFERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < offersList.size(); i++) {
                CompoundTag offerTag = offersList.getCompound(i);
                MerchantOffer.CODEC.parse(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), offerTag)
                    .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to load offer: {}", error))
                    .ifPresent(this.offers::add);
            }
        }

        // After loading from NBT, refresh trades from config if available
        // This ensures merchants in unloaded chunks get updated when they load
        if (this.traderId != null) {
            reloadTradesFromConfig();
        }
    }

    /**
     * Reloads trades from the config registry, filtering by this merchant's variant.
     * Call this after datapack reload to update merchant trades.
     * Preserves usage counts from previously saved offers.
     */
    public void reloadTradesFromConfig() {
        if (this.traderId == null) {
            return;
        }

        net.fit.cobblemonmerchants.merchant.config.MerchantConfig config =
            net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(this.traderId);

        if (config != null) {
            // Save old offers to preserve usage counts
            MerchantOffers oldOffers = this.offers;

            // Filter trades by this merchant's variant
            this.offers = config.toMerchantOffersForVariant(this.variant);
            this.tradeEntries = new java.util.ArrayList<>(config.getTradesForVariant(this.variant));

            // Restore usage counts from old offers for static trades
            restoreUsageCounts(oldOffers);

            // Add daily rotating trades if configured and on server side
            if (config.dailyRotatingTrades().isPresent() && this.level() instanceof ServerLevel serverLevel) {
                addDailyRotatingTrades(serverLevel, config.dailyRotatingTrades().get(), config);
            }

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Reloaded {} trades for merchant: {} (variant: {})",
                this.offers.size(), this.traderId, this.variant);
        } else {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Config not found for merchant: {}", this.traderId);
        }
    }

    /**
     * Restores usage counts from old offers to new offers by matching trade items.
     * This preserves player progress on limited-use trades across config reloads.
     */
    private void restoreUsageCounts(MerchantOffers oldOffers) {
        if (oldOffers == null || oldOffers.isEmpty()) {
            return;
        }

        int restoredCount = 0;
        for (MerchantOffer newOffer : this.offers) {
            for (MerchantOffer oldOffer : oldOffers) {
                if (offersMatch(newOffer, oldOffer)) {
                    int oldUses = oldOffer.getUses();
                    if (oldUses > 0) {
                        // Set uses on the new offer to match the old one
                        for (int i = 0; i < oldUses; i++) {
                            newOffer.increaseUses();
                        }
                        restoredCount++;
                        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.debug(
                            "Restored {} uses for trade: {} -> {}",
                            oldUses,
                            newOffer.getItemCostA().itemStack().getItem(),
                            newOffer.getResult().getItem());
                    }
                    break;
                }
            }
        }

        if (restoredCount > 0) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "Restored usage counts for {} trades on merchant: {}",
                restoredCount, this.traderId);
        }
    }

    /**
     * Checks if two offers represent the same trade by comparing their items and counts.
     */
    private boolean offersMatch(MerchantOffer a, MerchantOffer b) {
        // Compare input items
        if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(
                a.getItemCostA().itemStack(), b.getItemCostA().itemStack())) {
            return false;
        }
        if (a.getItemCostA().itemStack().getCount() != b.getItemCostA().itemStack().getCount()) {
            return false;
        }

        // Compare second input if present
        if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(a.getCostB(), b.getCostB())) {
            return false;
        }
        if (a.getCostB().getCount() != b.getCostB().getCount()) {
            return false;
        }

        // Compare output items
        if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(a.getResult(), b.getResult())) {
            return false;
        }
        if (a.getResult().getCount() != b.getResult().getCount()) {
            return false;
        }

        // Compare max uses (to distinguish otherwise identical trades)
        if (a.getMaxUses() != b.getMaxUses()) {
            return false;
        }

        return true;
    }

    /**
     * Adds daily rotating trades to this merchant's offers.
     * These trades are dynamically generated based on the current day and pool configuration.
     * Supports count > 1 for selecting multiple unique items from a pool.
     * Applies variant bonuses (extra trades, output multiplier, lucky trades) if configured.
     *
     * When sync_rotating_trades is false in the merchant config, each merchant entity gets unique:
     * - Trade selections (different items from pools)
     * - Lucky trade rolls (independent chance per entity)
     */
    private void addDailyRotatingTrades(ServerLevel level,
            java.util.List<net.fit.cobblemonmerchants.merchant.config.DailyRotatingTradeConfig> rotatingConfigs,
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig merchantConfig) {

        net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager manager =
            net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager.get(level);

        // Get variant bonus if applicable
        net.fit.cobblemonmerchants.merchant.config.MerchantConfig.VariantBonusConfig variantBonus =
            merchantConfig.getVariantBonus(this.variant);

        // Check if rotating trade selections are synchronized across all merchants of this type
        boolean syncRotatingTrades = merchantConfig.syncRotatingTrades();
        String entityUuidPrefix = syncRotatingTrades ? "" : this.getUUID().toString() + ":";

        if (!syncRotatingTrades) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "Merchant '{}' has sync_rotating_trades=false, using entity-specific trades (UUID: {})",
                merchantConfig.displayName(), this.getUUID());
        }

        for (net.fit.cobblemonmerchants.merchant.config.DailyRotatingTradeConfig config : rotatingConfigs) {
            // Skip if this rotating trade doesn't apply to this merchant's variant
            if (!config.appliesToVariant(this.variant)) {
                continue;
            }

            try {
                // Calculate effective count (base count + extra trades from variant bonus)
                int baseCount = config.getEffectiveCount();
                int extraTrades = variantBonus != null ? variantBonus.getExtraTradesForPool(config.poolId()) : 0;
                int totalCount = baseCount + extraTrades;

                // Create a modified config with the adjusted count and entity-specific slot ID if needed
                net.fit.cobblemonmerchants.merchant.config.DailyRotatingTradeConfig effectiveConfig = config;
                String effectiveSlotId = entityUuidPrefix + config.slotId();

                if (extraTrades > 0 || !syncRotatingTrades) {
                    effectiveConfig = new net.fit.cobblemonmerchants.merchant.config.DailyRotatingTradeConfig(
                        config.poolId(),
                        config.position(),
                        effectiveSlotId, // Use entity-specific slot ID when sync_rotating_trades=false
                        totalCount,
                        config.variants(),
                        config.tradeType(),
                        config.customInput(),
                        config.customOutput(),
                        config.inputCount(),
                        config.outputCount()
                    );
                    if (extraTrades > 0) {
                        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                            "Variant '{}' bonus: adding {} extra trades from pool '{}' (total: {})",
                            this.variant, extraTrades, config.poolId(), totalCount);
                    }
                }

                // Get all selected trades for this config (handles count > 1)
                var selectedTrades = manager.getSelectedTrades(effectiveConfig);
                if (selectedTrades.isEmpty()) {
                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                        "No trades selected for rotating slot '{}' - pool may be invalid or too small for count {}",
                        effectiveConfig.slotId(), effectiveConfig.getEffectiveCount());
                    continue;
                }

                // Add each selected trade with variant bonuses applied
                // Each trade gets its own deterministic seed based on date + indexed slot ID + rotation counter
                // This ensures: 1) each trade has independent lucky chance, 2) same slot ID = same luck across variants
                // 3) lucky status changes when trades are refreshed (rotation counter increments)
                // 4) when sync_rotating_trades=false, entity UUID is included for unique per-entity results
                String dateStr = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString();
                long rotationCounter = manager.getRotationCounter();

                int tradeIndex = 0;
                for (var tradeMeta : selectedTrades) {
                    // Seed based on the INDEXED slot ID for independent rolls
                    // When sync_rotating_trades=false, the slot ID already includes entity UUID prefix
                    String seedString = dateStr + ":" + config.poolId() + ":" + tradeMeta.slotId() + ":" + tradeIndex + ":" + rotationCounter + ":lucky";
                    long luckySeed = seedString.hashCode();
                    java.util.Random luckyRandom = new java.util.Random(luckySeed);

                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                        "Creating lucky random for trade {}: seedString='{}', seed={}, rotationCounter={}",
                        tradeIndex, seedString, luckySeed, rotationCounter);

                    addSingleRotatingTrade(effectiveConfig, tradeMeta, variantBonus, luckyRandom, baseCount);
                    tradeIndex++;
                }

            } catch (Exception e) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error(
                    "Failed to add daily rotating trade for slot '{}'", config.slotId(), e);
            }
        }

        // Log summary of lucky trades
        long luckyCount = this.tradeEntries.stream()
            .filter(entry -> entry.isLucky())
            .count();
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "SERVER: Finished adding rotating trades for variant '{}'. Total trades: {}, Lucky trades: {}, syncRotatingTrades: {}",
            this.variant, this.tradeEntries.size(), luckyCount, syncRotatingTrades);
    }

    /**
     * Adds a single rotating trade to this merchant's offers.
     * Applies variant bonuses (output multiplier, lucky trades) if configured.
     *
     * @param config The rotating trade configuration
     * @param tradeMeta The selected trade with metadata
     * @param variantBonus The variant bonus config (may be null)
     * @param luckyRandom Deterministic random for lucky rolls (seeded by date + slot_id)
     * @param baseCount The number of base trades (for tracking which trades are extra/variant-specific)
     */
    private void addSingleRotatingTrade(
            net.fit.cobblemonmerchants.merchant.config.DailyRotatingTradeConfig config,
            net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager.SelectedTradeWithMeta tradeMeta,
            net.fit.cobblemonmerchants.merchant.config.MerchantConfig.VariantBonusConfig variantBonus,
            java.util.Random luckyRandom,
            int baseCount) {

        var selectedTrade = tradeMeta.trade();
        String slotId = tradeMeta.slotId();
        java.util.Optional<Integer> position = tradeMeta.position();

        // Determine trade type and build trade accordingly
        // Pool-level sell configuration (output_item) takes precedence over config trade_type
        String poolItemId = selectedTrade.itemId();
        String defaultInputItemId = net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager
            .getInputItemForPool(selectedTrade.poolId());
        boolean isPoolSellType = net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager
            .isSellPool(selectedTrade.poolId());
        java.util.Optional<String> poolOutputItem = net.fit.cobblemonmerchants.merchant.rotation.DailyRotatingTradeManager
            .getOutputItemForPool(selectedTrade.poolId());

        String actualInputItemId;
        String actualOutputItemId;
        int actualInputAmount;
        int actualOutputAmount;

        if (config.isFree()) {
            // FREE: Pool item is output, no cost
            actualInputItemId = null;
            actualInputAmount = 0;
            actualOutputItemId = poolItemId;
            actualOutputAmount = config.outputCount().orElse(selectedTrade.outputAmount());
        } else if (isPoolSellType || config.isPoolItemInput()) {
            // SELL: Pool item is input (player sells it), receives output
            // If pool has output_item, use that; otherwise fall back to input_item or custom_output
            actualInputItemId = poolItemId;
            actualInputAmount = config.inputCount().orElse(selectedTrade.inputAmount());
            actualOutputItemId = config.customOutput().orElse(poolOutputItem.orElse(defaultInputItemId));
            actualOutputAmount = config.outputCount().orElse(selectedTrade.outputAmount());
        } else {
            // BUY (default): Pool item is output, player pays input
            actualInputItemId = config.customInput().orElse(defaultInputItemId);
            actualInputAmount = config.inputCount().orElse(selectedTrade.inputAmount());
            actualOutputItemId = config.customOutput().orElse(poolItemId);
            actualOutputAmount = config.outputCount().orElse(selectedTrade.outputAmount());
        }

        // Apply variant bonuses
        int effectiveMaxUses = selectedTrade.maxUses();
        boolean isLucky = false;
        String bonusInfo = "";

        if (variantBonus != null) {
            // Apply base output multiplier from variant bonus
            actualOutputAmount = variantBonus.applyOutputMultiplier(actualOutputAmount);

            // Check for lucky trade
            var luckyConfig = variantBonus.luckyTradeConfig();

            // Get the random value BEFORE consuming it, for logging
            double randomValue = luckyRandom.nextDouble();
            boolean luckyRoll = randomValue < luckyConfig.chance();

            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "Lucky trade check for slot '{}': variant={}, luckyEnabled={}, chance={}, randomValue={}, result={}",
                slotId, this.variant, luckyConfig.isEnabled(), luckyConfig.chance(),
                String.format("%.4f", randomValue), luckyRoll);

            if (luckyConfig.isEnabled() && luckyRoll) {
                isLucky = true;
                // Apply lucky trade multipliers (stacks with variant output multiplier)
                actualOutputAmount = luckyConfig.applyOutputMultiplier(actualOutputAmount);
                effectiveMaxUses = luckyConfig.applyMaxUsesMultiplier(effectiveMaxUses);
                bonusInfo = " [LUCKY: output x" + luckyConfig.outputMultiplier() +
                    ", maxUses x" + luckyConfig.maxUsesMultiplier() + "]";
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                    "LUCKY TRADE ROLLED for slot '{}': outputMult={}, maxUsesMult={}",
                    slotId, luckyConfig.outputMultiplier(), luckyConfig.maxUsesMultiplier());
            }

            if (variantBonus.outputMultiplier() != 1.0 || isLucky) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                    "Variant bonus applied to slot '{}': outputMult={}, lucky={}, finalOutput={}",
                    slotId, variantBonus.outputMultiplier(), isLucky, actualOutputAmount);
            }
        } else {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
                "No variant bonus for slot '{}': variant='{}' has no bonus config",
                slotId, this.variant);
        }

        // Create input ItemRequirement
        net.fit.cobblemonmerchants.merchant.config.ItemRequirement inputReq;
        if (config.isFree()) {
            inputReq = net.fit.cobblemonmerchants.merchant.config.ItemRequirement.createFreeTradeMarker();
        } else {
            inputReq = net.fit.cobblemonmerchants.merchant.config.ItemRequirement.fromItemId(
                actualInputItemId, actualInputAmount);
        }

        // Create output ItemStack
        net.minecraft.world.item.ItemStack outputStack = createItemStackFromId(
            actualOutputItemId, actualOutputAmount);

        if (outputStack.isEmpty() && !config.isFree()) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                "Failed to create output item for rotating trade: {}", actualOutputItemId);
            return;
        }

        // For free trades, ensure we have valid output
        if (config.isFree() && outputStack.isEmpty()) {
            outputStack = createItemStackFromId(poolItemId, selectedTrade.outputAmount());
            if (outputStack.isEmpty()) {
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn(
                    "Failed to create output item for free rotating trade: {}", poolItemId);
                return;
            }
        }

        // Create the MerchantOffer
        MerchantOffer offer;
        if (config.isFree()) {
            offer = new MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.STRUCTURE_VOID, 1),
                outputStack,
                effectiveMaxUses,
                0, 0.0f
            );
        } else {
            offer = new MerchantOffer(
                inputReq.toItemCostWithCount(actualInputAmount),
                outputStack,
                effectiveMaxUses,
                0, 0.0f
            );
        }

        this.offers.add(offer);

        // Get lucky trade multipliers for tooltip display
        double luckyOutputMult = 1.0;
        double luckyMaxUsesMult = 1.0;
        if (isLucky && variantBonus != null) {
            var luckyConfig = variantBonus.luckyTradeConfig();
            luckyOutputMult = luckyConfig.outputMultiplier();
            luckyMaxUsesMult = luckyConfig.maxUsesMultiplier();
        }

        // Create a synthetic TradeEntry for client sync
        net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry syntheticEntry =
            new net.fit.cobblemonmerchants.merchant.config.MerchantConfig.TradeEntry(
                inputReq,
                java.util.Optional.empty(),
                outputStack,
                actualOutputAmount,
                effectiveMaxUses,
                0, 0.0f,
                java.util.Optional.ofNullable(selectedTrade.displayName()),
                position,
                java.util.Optional.empty(),
                true, // daily reset
                java.util.Optional.empty(),
                java.util.Optional.of(slotId), // slot_id for usage tracking
                isLucky, // lucky trade flag
                luckyOutputMult, // lucky output multiplier for tooltip
                luckyMaxUsesMult // lucky max uses multiplier for tooltip
            );

        this.tradeEntries.add(syntheticEntry);

        String effectiveTradeType = config.isFree() ? "free" : (isPoolSellType || config.isPoolItemInput() ? "sell" : "buy");
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info(
            "Added daily rotating trade for slot '{}' (type={}): {} x{} for {} x{}, maxUses={}, dailyReset=true, isLucky={}{}",
            slotId, effectiveTradeType, actualOutputItemId, actualOutputAmount,
            actualInputItemId, actualInputAmount, effectiveMaxUses, isLucky, bonusInfo);
    }

    /**
     * Creates an ItemStack from an item ID string.
     */
    private static net.minecraft.world.item.ItemStack createItemStackFromId(String itemId, int count) {
        try {
            ResourceLocation itemLoc = ResourceLocation.parse(itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLoc);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            return new net.minecraft.world.item.ItemStack(item, count);
        } catch (Exception e) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Failed to parse item ID: {}", itemId);
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

}
