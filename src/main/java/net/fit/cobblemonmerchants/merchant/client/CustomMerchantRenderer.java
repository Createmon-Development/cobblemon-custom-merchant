package net.fit.cobblemonmerchants.merchant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer for custom merchant entities using villager model.
 * Supports different villager types based on biome and profession.
 */
public class CustomMerchantRenderer extends MobRenderer<CustomMerchantEntity, VillagerModel<CustomMerchantEntity>> {

    public CustomMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager"));
        this.addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(@NotNull CustomMerchantEntity entity, float entityYaw, float partialTicks,
                      @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        // Always show hat (villager hat is part of the head model)
        this.model.hatVisible(true);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CustomMerchantEntity entity) {
        String biome = entity.getVillagerBiome();
        String profession = entity.getVillagerProfession();

        // Minecraft villager texture path format: textures/entity/villager/profession_level_X/{profession}.png or type/{biome}.png
        // But profession textures need the villager type combined
        // Simplest: just use biome type textures which work standalone
        if (biome != null && !biome.isEmpty()) {
            return ResourceLocation.withDefaultNamespace("textures/entity/villager/type/" + biome + ".png");
        } else {
            return ResourceLocation.withDefaultNamespace("textures/entity/villager/type/plains.png");
        }
    }

    @Override
    protected void scale(@NotNull CustomMerchantEntity entity, @NotNull PoseStack poseStack, float partialTickTime) {
        float scale = 0.9375F;
        poseStack.scale(scale, scale, scale);
    }
}