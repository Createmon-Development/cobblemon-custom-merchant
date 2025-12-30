package net.fit.cobblemonmerchants.merchant.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Renderer for custom merchant entities.
 * Uses the humanoid model (same as player) and fetches player skins.
 */
public class CustomMerchantRenderer extends MobRenderer<CustomMerchantEntity, HumanoidModel<CustomMerchantEntity>> {

    public CustomMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CustomMerchantEntity entity) {
        String playerName = entity.getPlayerSkinName();

        if (playerName == null || playerName.isEmpty()) {
            // Return default Steve skin if no player name is set
            return DefaultPlayerSkin.getDefaultTexture();
        }

        // Try to get the player's skin from Minecraft's skin cache
        Minecraft minecraft = Minecraft.getInstance();
        // Generate a deterministic UUID based on the player name for offline mode
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
        GameProfile gameProfile = new GameProfile(uuid, playerName);

        // Fetch player skin (this will cache it for future use)
        PlayerSkin skin = minecraft.getSkinManager().getInsecureSkin(gameProfile);
        return skin.texture();
    }

    @Override
    protected void scale(@NotNull CustomMerchantEntity entity, @NotNull PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }
}