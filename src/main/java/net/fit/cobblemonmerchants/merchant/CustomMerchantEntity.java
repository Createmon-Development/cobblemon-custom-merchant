package net.fit.cobblemonmerchants.merchant;

import net.fit.cobblemonmerchants.merchant.blackmarket.BlackMarketInventory;
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
    private static final String TAG_MERCHANT_TYPE = "MerchantType";
    private static final String TAG_PLAYER_SKIN_NAME = "PlayerSkinName";
    private static final String TAG_VILLAGER_BIOME = "VillagerBiome";
    private static final String TAG_VILLAGER_PROFESSION = "VillagerProfession";
    private static final String TAG_OFFERS = "Offers";
    private static final String TAG_VARIANT = "Variant";

    private static final EntityDataAccessor<String> DATA_PLAYER_SKIN_NAME =
        SynchedEntityData.defineId(CustomMerchantEntity.class, EntityDataSerializers.STRING);

    private ResourceLocation traderId;
    private MerchantType merchantType = MerchantType.REGULAR;
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

    public void setMerchantType(MerchantType type) {
        this.merchantType = type;
    }

    public MerchantType getMerchantType() {
        return this.merchantType;
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
        // For Black Market merchants, generate dynamic per-player inventory
        if (this.merchantType == MerchantType.BLACK_MARKET && this.tradingPlayer != null) {
            if (this.level() instanceof ServerLevel serverLevel) {
                BlackMarketInventory inventory = BlackMarketInventory.get(serverLevel);
                return inventory.getOffersForPlayer(
                    this.tradingPlayer.getUUID(),
                    serverLevel.getDayTime()
                );
            }
        }
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
        // Check if player is holding the debug stick
        if (player.getItemInHand(hand).getItem() instanceof net.fit.cobblemonmerchants.item.custom.MerchantDebugStick) {
            // If sneaking with debug stick on Black Market merchant, allow menu opening for testing
            if (player.isShiftKeyDown() && this.merchantType == MerchantType.BLACK_MARKET) {
                // Continue to open the menu below
            } else {
                // Otherwise, let the debug stick handle the interaction (pickup/place)
                return InteractionResult.PASS;
            }
        }

        // Open custom chest-style trading GUI
        if (!this.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            this.setTradingPlayer(player);
            openCustomTradeScreen(serverPlayer);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
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
            }

            // Sync daily reward info to client
            writeDailyRewardInfo(buf, serverPlayer);
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
        buf.writeUtf(net.fit.cobblemonmerchants.merchant.rewards.DailyRewardManager.getFormattedTimeUntilReset());
        buf.writeInt(rewardVariant.minCount());
        buf.writeInt(rewardVariant.maxCount());
        buf.writeBoolean(dailyConfig.sharedCooldown());
        // Write entity UUID for per-entity cooldown tracking
        buf.writeUUID(this.getUUID());

        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("writeDailyRewardInfo: SUCCESS! position={}, claimed={}, sharedCooldown={}, entityUUID={}, item={}, minCount={}, maxCount={}",
            rewardVariant.displayPosition().get(), hasClaimed, dailyConfig.sharedCooldown(), this.getUUID(), rewardVariant.item(), rewardVariant.minCount(), rewardVariant.maxCount());
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
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
        super.addAdditionalSaveData(tag);
        if (this.traderId != null) {
            tag.putString(TAG_TRADER_ID, this.traderId.toString());
        }
        tag.putString(TAG_MERCHANT_TYPE, this.merchantType.name());
        tag.putString(TAG_VARIANT, this.variant);
        String skinName = getPlayerSkinName();
        if (!skinName.isEmpty()) {
            tag.putString(TAG_PLAYER_SKIN_NAME, skinName);
        }
        VillagerData.CODEC.encodeStart(this.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), getVillagerData())
            .resultOrPartial(error -> net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.error("Failed to save villager data: {}", error))
            .ifPresent(data -> tag.put("VillagerData", data));

        // Save offers for regular merchants
        if (this.merchantType == MerchantType.REGULAR && !this.offers.isEmpty()) {
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
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_TRADER_ID)) {
            this.traderId = ResourceLocation.parse(tag.getString(TAG_TRADER_ID));
        }
        if (tag.contains(TAG_MERCHANT_TYPE)) {
            try {
                this.merchantType = MerchantType.valueOf(tag.getString(TAG_MERCHANT_TYPE));
            } catch (IllegalArgumentException e) {
                this.merchantType = MerchantType.REGULAR;
            }
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
        if (this.merchantType == MerchantType.REGULAR && this.traderId != null) {
            reloadTradesFromConfig();
        }
    }

    /**
     * Reloads trades from the config registry, filtering by this merchant's variant.
     * Only applicable for REGULAR merchants (Black Market generates trades dynamically).
     * Call this after datapack reload to update merchant trades.
     */
    public void reloadTradesFromConfig() {
        if (this.merchantType != MerchantType.REGULAR || this.traderId == null) {
            return;
        }

        net.fit.cobblemonmerchants.merchant.config.MerchantConfig config =
            net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getConfig(this.traderId);

        if (config != null) {
            // Filter trades by this merchant's variant
            this.offers = config.toMerchantOffersForVariant(this.variant);
            this.tradeEntries = new java.util.ArrayList<>(config.getTradesForVariant(this.variant));
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Reloaded {} trades for merchant: {} (variant: {})",
                this.offers.size(), this.traderId, this.variant);
        } else {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.warn("Config not found for merchant: {}", this.traderId);
        }
    }

    /**
     * Types of merchants supported by CustomMerchantEntity
     */
    public enum MerchantType {
        /**
         * Regular merchant with static trades defined in JSON files
         */
        REGULAR,

        /**
         * Black Market merchant with dynamic rotating inventory per player
         */
        BLACK_MARKET
    }
}
