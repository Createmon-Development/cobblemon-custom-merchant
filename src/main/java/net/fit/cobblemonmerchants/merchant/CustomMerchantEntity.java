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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom merchant entity that looks like a player with custom trades.
 * This entity is immobile, invincible, and untargetable.
 * Supports both regular traders (with JSON-defined trades) and Black Market merchants (with dynamic rotating inventory).
 */
public class CustomMerchantEntity extends PathfinderMob {
    private static final String TAG_TRADER_ID = "TraderId";
    private static final String TAG_MERCHANT_TYPE = "MerchantType";
    private static final String TAG_PLAYER_SKIN_NAME = "PlayerSkinName";
    private static final String TAG_OFFERS = "Offers";

    private static final EntityDataAccessor<String> DATA_PLAYER_SKIN_NAME =
        SynchedEntityData.defineId(CustomMerchantEntity.class, EntityDataSerializers.STRING);

    private ResourceLocation traderId;
    private MerchantType merchantType = MerchantType.REGULAR;
    private MerchantOffers offers = new MerchantOffers();
    private Player tradingPlayer;

    public CustomMerchantEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
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

    public void setTradingPlayer(Player player) {
        this.tradingPlayer = player;
    }

    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public void setOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    public MerchantOffers getOffers() {
        // For Black Market merchants, generate dynamic per-player inventory
        if (this.merchantType == MerchantType.BLACK_MARKET && this.tradingPlayer != null) {
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("===== BLACK MARKET getOffers() called for player: {} =====", this.tradingPlayer.getName().getString());
            if (this.level() instanceof ServerLevel serverLevel) {
                BlackMarketInventory inventory = BlackMarketInventory.get(serverLevel);
                MerchantOffers blackMarketOffers = inventory.getOffersForPlayer(
                    this.tradingPlayer.getUUID(),
                    serverLevel.getDayTime()
                );
                net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Returning {} offers", blackMarketOffers.size());
                return blackMarketOffers;
            }
        }

        // For regular merchants, use the stored offers
        return this.offers;
    }

    @Override
    protected void registerGoals() {
        // No AI goals - the entity should not move or have any behavior
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

        // Open trading GUI when player interacts (right-clicks) with the merchant
        if (!this.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            this.setTradingPlayer(player);
            openCustomTradeScreen(serverPlayer);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /**
     * Opens the custom chest-style trading screen
     */
    private void openCustomTradeScreen(net.minecraft.server.level.ServerPlayer serverPlayer) {
        net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Opening custom trade screen for player: {}", serverPlayer.getName().getString());

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
        }, buf -> buf.writeInt(this.getId()));
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

    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void aiStep() {
        // Call parent to handle gravity and other physics, but prevent AI movement
        super.aiStep();

        // Force the entity to stay in place (prevent any movement from AI)
        this.setDeltaMovement(this.getDeltaMovement().multiply(0, 1, 0));
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
        String skinName = getPlayerSkinName();
        if (!skinName.isEmpty()) {
            tag.putString(TAG_PLAYER_SKIN_NAME, skinName);
        }

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
        if (tag.contains(TAG_PLAYER_SKIN_NAME)) {
            setPlayerSkinName(tag.getString(TAG_PLAYER_SKIN_NAME));
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
     * Reloads trades from the config registry.
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
            this.offers = config.toMerchantOffers();
            net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("Reloaded {} trades for merchant: {}",
                this.offers.size(), this.traderId);
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
