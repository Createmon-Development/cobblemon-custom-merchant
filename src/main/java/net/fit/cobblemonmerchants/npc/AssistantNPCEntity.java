package net.fit.cobblemonmerchants.npc;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.action.ActionExecutor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * NPC entity focused on dialogue interactions.
 * Unlike merchants, these NPCs don't have trade menus - they only execute action dialogues.
 *
 * Immobile, invincible, and untargetable.
 */
public class AssistantNPCEntity extends Villager {
    private static final String TAG_NPC_ID = "NPCId";
    private static final String TAG_VARIANT = "Variant";
    private static final String TAG_VILLAGER_BIOME = "VillagerBiome";
    private static final String TAG_VILLAGER_PROFESSION = "VillagerProfession";

    private static final EntityDataAccessor<String> DATA_NPC_ID =
        SynchedEntityData.defineId(AssistantNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_VARIANT =
        SynchedEntityData.defineId(AssistantNPCEntity.class, EntityDataSerializers.STRING);

    private ResourceLocation npcId;
    private String variant = "default";

    public AssistantNPCEntity(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NPC_ID, "");
        builder.define(DATA_VARIANT, "default");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.0)
            .add(Attributes.FOLLOW_RANGE, 0.0)
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void setNPCId(ResourceLocation npcId) {
        this.npcId = npcId;
        this.entityData.set(DATA_NPC_ID, npcId.toString());
    }

    public ResourceLocation getNPCId() {
        return this.npcId;
    }

    public void setNPCVariant(String variant) {
        this.variant = variant != null ? variant : "default";
        this.entityData.set(DATA_VARIANT, this.variant);
    }

    public String getNPCVariant() {
        return this.variant;
    }

    /**
     * Gets the display name for this NPC from its config.
     */
    public String getNPCDisplayName() {
        if (this.npcId == null) {
            return "NPC";
        }

        NPCConfig config = NPCConfigRegistry.getConfig(this.npcId);
        if (config == null) {
            return "NPC";
        }

        // Check for variant-specific display name
        if (this.variant != null && config.variants().containsKey(this.variant)) {
            NPCConfig.VariantData variantData = config.variants().get(this.variant);
            if (variantData.displayName().isPresent()) {
                return variantData.displayName().get();
            }
        }

        return config.displayName();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.literal(getNPCDisplayName());
    }

    public void setVillagerBiome(String biome) {
        VillagerType type;
        try {
            ResourceLocation typeId = biome.contains(":")
                ? ResourceLocation.parse(biome)
                : ResourceLocation.withDefaultNamespace(biome);
            type = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.get(typeId);
            if (type == null) {
                type = VillagerType.PLAINS;
            }
        } catch (Exception e) {
            type = VillagerType.PLAINS;
        }
        VillagerData current = getVillagerData();
        setVillagerData(new VillagerData(type, current.getProfession(), current.getLevel()));
    }

    public void setVillagerProfession(String profession) {
        VillagerProfession prof;
        try {
            ResourceLocation profId = profession.contains(":")
                ? ResourceLocation.parse(profession)
                : ResourceLocation.withDefaultNamespace(profession);
            prof = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(profId);
            if (prof == null) {
                prof = VillagerProfession.NONE;
            }
        } catch (Exception e) {
            prof = VillagerProfession.NONE;
        }
        VillagerData current = getVillagerData();
        setVillagerData(new VillagerData(current.getType(), prof, current.getLevel()));
    }

    // ========== Immobility and Invincibility ==========

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Check if attacker is player with debug stick + sneaking = remove NPC
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player && player.isShiftKeyDown()) {
            if (player.getMainHandItem().getItem() instanceof net.fit.cobblemonmerchants.item.custom.MerchantDebugStick) {
                if (!this.level().isClientSide) {
                    this.discard();
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Removed NPC: " + getNPCDisplayName())
                        .withStyle(net.minecraft.ChatFormatting.RED));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    protected void registerGoals() {
        // No AI goals - NPC stands still
    }

    @Override
    protected void customServerAiStep() {
        // No AI
    }

    // ========== Interaction ==========

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        // Check if player is holding the debug stick for pickup
        if (player.getItemInHand(hand).getItem() instanceof net.fit.cobblemonmerchants.item.custom.MerchantDebugStick) {
            return InteractionResult.PASS;
        }

        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Execute dialogue action
            executeDialogue(serverPlayer);
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /**
     * Executes the dialogue sequence for this NPC.
     */
    private void executeDialogue(ServerPlayer player) {
        if (this.npcId == null) {
            CobblemonMerchants.LOGGER.warn("NPC has no NPC ID set, cannot execute dialogue");
            return;
        }

        NPCConfig config = NPCConfigRegistry.getConfig(this.npcId);
        if (config == null) {
            CobblemonMerchants.LOGGER.warn("NPC config not found: {}", this.npcId);
            return;
        }

        // Get the action ID for this variant
        var actionIdOpt = config.getActionIdForVariant(this.variant);
        if (actionIdOpt.isEmpty()) {
            CobblemonMerchants.LOGGER.debug("No action ID configured for NPC {} variant {}",
                this.npcId, this.variant);
            return;
        }

        ResourceLocation actionId = ResourceLocation.parse(actionIdOpt.get());

        // Execute the dialogue with this NPC's display name as the default speaker
        ActionExecutor.executeDialogueForNPC(player, this, actionId, null);
    }

    // ========== Persistence ==========

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (this.npcId != null) {
            tag.putString(TAG_NPC_ID, this.npcId.toString());
        }
        tag.putString(TAG_VARIANT, this.variant);

        // Save villager appearance
        VillagerData data = getVillagerData();
        ResourceLocation biomeId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType());
        ResourceLocation profId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession());
        if (biomeId != null) {
            tag.putString(TAG_VILLAGER_BIOME, biomeId.toString());
        }
        if (profId != null) {
            tag.putString(TAG_VILLAGER_PROFESSION, profId.toString());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains(TAG_NPC_ID)) {
            this.npcId = ResourceLocation.parse(tag.getString(TAG_NPC_ID));
            this.entityData.set(DATA_NPC_ID, this.npcId.toString());
        }

        if (tag.contains(TAG_VARIANT)) {
            this.variant = tag.getString(TAG_VARIANT);
            this.entityData.set(DATA_VARIANT, this.variant);
        }

        // Restore villager appearance
        if (tag.contains(TAG_VILLAGER_BIOME)) {
            setVillagerBiome(tag.getString(TAG_VILLAGER_BIOME));
        }
        if (tag.contains(TAG_VILLAGER_PROFESSION)) {
            setVillagerProfession(tag.getString(TAG_VILLAGER_PROFESSION));
        }
    }

    /**
     * Loads NPC configuration from its config file.
     */
    public void loadFromConfig() {
        if (this.npcId == null) {
            return;
        }

        NPCConfig config = NPCConfigRegistry.getConfig(this.npcId);
        if (config == null) {
            CobblemonMerchants.LOGGER.warn("Could not load NPC config for: {}", this.npcId);
            return;
        }

        // Apply villager appearance
        setVillagerBiome(config.getBiome());
        setVillagerProfession(config.getProfession());

        // Set custom name
        this.setCustomName(Component.literal(config.displayName()));
        this.setCustomNameVisible(true);

        CobblemonMerchants.LOGGER.debug("Loaded NPC config for {}", this.npcId);
    }
}
