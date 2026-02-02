package net.fit.cobblemonmerchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fit.cobblemonmerchants.merchant.CustomMerchantEntity;
import net.fit.cobblemonmerchants.merchant.ModEntities;
import net.fit.cobblemonmerchants.merchant.config.MerchantConfig;
import net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry;
import net.fit.cobblemonmerchants.npc.AssistantNPCEntity;
import net.fit.cobblemonmerchants.npc.NPCConfig;
import net.fit.cobblemonmerchants.npc.NPCConfigRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Unified command for spawning custom merchants and NPCs:
 * /spawncm merchant <type> [biome] [profession] [variant]
 * /spawncm npc <type> [biome] [profession] [variant]
 *
 * Use "_" as a placeholder for biome/profession to use the config default.
 *
 * Examples:
 *   /spawncm merchant cobblemoncustommerchants:treasure_hunter _ _ housed
 *   /spawncm npc cobblemoncustommerchants:treasure_hunter_assistant _ _ temple
 */
public class SpawnMerchantCommand {

    private static final SuggestionProvider<CommandSourceStack> MERCHANT_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggestResource(MerchantConfigRegistry.getAllConfigs().keySet(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> NPC_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggestResource(NPCConfigRegistry.getAllConfigs().keySet(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> BIOME_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{
            "_", "minecraft:plains", "minecraft:desert", "minecraft:jungle",
            "minecraft:savanna", "minecraft:snow", "minecraft:swamp", "minecraft:taiga"
        }, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> PROFESSION_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{
            "_", "minecraft:none", "minecraft:armorer", "minecraft:butcher",
            "minecraft:cartographer", "minecraft:cleric", "minecraft:farmer",
            "minecraft:fisherman", "minecraft:fletcher", "minecraft:leatherworker",
            "minecraft:librarian", "minecraft:mason", "minecraft:nitwit",
            "minecraft:shepherd", "minecraft:toolsmith", "minecraft:weaponsmith"
        }, builder);
    };

    /**
     * Dynamic variant suggestions for merchants based on the selected merchant type.
     */
    private static final SuggestionProvider<CommandSourceStack> MERCHANT_VARIANT_SUGGESTIONS = (context, builder) -> {
        try {
            ResourceLocation typeId = ResourceLocationArgument.getId(context, "type");
            MerchantConfig config = MerchantConfigRegistry.getConfig(typeId);
            if (config != null) {
                return SharedSuggestionProvider.suggest(config.getAvailableVariants(), builder);
            }
        } catch (Exception ignored) {
            // Type argument may not be parsed yet
        }
        return SharedSuggestionProvider.suggest(new String[]{"default"}, builder);
    };

    /**
     * Dynamic variant suggestions for NPCs based on the selected NPC type.
     */
    private static final SuggestionProvider<CommandSourceStack> NPC_VARIANT_SUGGESTIONS = (context, builder) -> {
        try {
            ResourceLocation typeId = ResourceLocationArgument.getId(context, "type");
            NPCConfig config = NPCConfigRegistry.getConfig(typeId);
            if (config != null) {
                return SharedSuggestionProvider.suggest(config.getAvailableVariants(), builder);
            }
        } catch (Exception ignored) {
            // Type argument may not be parsed yet
        }
        return SharedSuggestionProvider.suggest(new String[]{"default"}, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawncm")
                .requires(source -> source.hasPermission(2))
                // /spawncm merchant <type> [biome] [profession] [variant]
                .then(Commands.literal("merchant")
                    .then(Commands.argument("type", ResourceLocationArgument.id())
                        .suggests(MERCHANT_SUGGESTIONS)
                        .executes(ctx -> spawnMerchantCmd(ctx, null, null, null))
                        .then(Commands.argument("biome", StringArgumentType.word())
                            .suggests(BIOME_SUGGESTIONS)
                            .executes(ctx -> spawnMerchantCmd(ctx,
                                StringArgumentType.getString(ctx, "biome"), null, null))
                            .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests(PROFESSION_SUGGESTIONS)
                                .executes(ctx -> spawnMerchantCmd(ctx,
                                    StringArgumentType.getString(ctx, "biome"),
                                    StringArgumentType.getString(ctx, "profession"), null))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                    .suggests(MERCHANT_VARIANT_SUGGESTIONS)
                                    .executes(ctx -> spawnMerchantCmd(ctx,
                                        StringArgumentType.getString(ctx, "biome"),
                                        StringArgumentType.getString(ctx, "profession"),
                                        StringArgumentType.getString(ctx, "variant")))
                                )
                            )
                        )
                    )
                )
                // /spawncm npc <type> [biome] [profession] [variant]
                .then(Commands.literal("npc")
                    .then(Commands.argument("type", ResourceLocationArgument.id())
                        .suggests(NPC_SUGGESTIONS)
                        .executes(ctx -> spawnNPCCmd(ctx, null, null, null))
                        .then(Commands.argument("biome", StringArgumentType.word())
                            .suggests(BIOME_SUGGESTIONS)
                            .executes(ctx -> spawnNPCCmd(ctx,
                                StringArgumentType.getString(ctx, "biome"), null, null))
                            .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests(PROFESSION_SUGGESTIONS)
                                .executes(ctx -> spawnNPCCmd(ctx,
                                    StringArgumentType.getString(ctx, "biome"),
                                    StringArgumentType.getString(ctx, "profession"), null))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                    .suggests(NPC_VARIANT_SUGGESTIONS)
                                    .executes(ctx -> spawnNPCCmd(ctx,
                                        StringArgumentType.getString(ctx, "biome"),
                                        StringArgumentType.getString(ctx, "profession"),
                                        StringArgumentType.getString(ctx, "variant")))
                                )
                            )
                        )
                    )
                )
        );
    }

    private static int spawnMerchantCmd(CommandContext<CommandSourceStack> ctx,
            String biome, String profession, String variant) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation typeId = ResourceLocationArgument.getId(ctx, "type");
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();

        // Treat "_" or "-" as placeholder
        if ("_".equals(biome) || "-".equals(biome)) biome = null;
        if ("_".equals(profession) || "-".equals(profession)) profession = null;

        MerchantConfig config = MerchantConfigRegistry.getConfig(typeId);
        if (config == null) {
            source.sendFailure(Component.literal("Unknown merchant type: " + typeId));
            return 0;
        }

        return spawnMerchant(source, level, position, typeId, config, biome, profession, variant);
    }

    private static int spawnNPCCmd(CommandContext<CommandSourceStack> ctx,
            String biome, String profession, String variant) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation typeId = ResourceLocationArgument.getId(ctx, "type");
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();

        // Treat "_" or "-" as placeholder
        if ("_".equals(biome) || "-".equals(biome)) biome = null;
        if ("_".equals(profession) || "-".equals(profession)) profession = null;

        NPCConfig config = NPCConfigRegistry.getConfig(typeId);
        if (config == null) {
            source.sendFailure(Component.literal("Unknown NPC type: " + typeId));
            return 0;
        }

        return spawnNPC(source, level, position, typeId, config, biome, profession, variant);
    }

    private static int spawnMerchant(CommandSourceStack source, ServerLevel level, Vec3 position,
            ResourceLocation merchantTypeId, MerchantConfig config,
            String overrideBiome, String overrideProfession, String variant) {

        CustomMerchantEntity merchant = new CustomMerchantEntity(ModEntities.CUSTOM_MERCHANT.get(), level);

        BlockPos spawnPos = BlockPos.containing(position);
        merchant.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        merchant.setYRot(source.getRotation().y);
        merchant.setYHeadRot(source.getRotation().y);

        merchant.setCustomName(Component.literal(config.displayName()));
        merchant.setCustomNameVisible(true);

        String biome = overrideBiome != null ? overrideBiome : config.villagerBiome().orElse("plains");
        merchant.setVillagerBiome(biome);

        String profession = overrideProfession != null ? overrideProfession : config.villagerProfession().orElse("none");
        merchant.setVillagerProfession(profession);
        merchant.setTraderId(merchantTypeId);

        if (variant != null && !variant.isEmpty()) {
            merchant.setMerchantVariant(variant);
        }

        merchant.reloadTradesFromConfig();
        level.addFreshEntity(merchant);

        String variantInfo = variant != null ? " (variant: " + variant + ")" : "";
        source.sendSuccess(() -> Component.literal("Spawned merchant: " + config.displayName() + variantInfo), true);

        return 1;
    }

    private static int spawnNPC(CommandSourceStack source, ServerLevel level, Vec3 position,
            ResourceLocation npcTypeId, NPCConfig config,
            String overrideBiome, String overrideProfession, String variant) {

        AssistantNPCEntity npc = new AssistantNPCEntity(ModEntities.ASSISTANT_NPC.get(), level);

        BlockPos spawnPos = BlockPos.containing(position);
        npc.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        npc.setYRot(source.getRotation().y);
        npc.setYHeadRot(source.getRotation().y);

        npc.setNPCId(npcTypeId);

        // Use specified variant, or fall back to config's default variant
        String effectiveVariant = (variant != null && !variant.isEmpty()) ? variant : config.getDefaultVariant();
        npc.setNPCVariant(effectiveVariant);

        // Load config to set display name and appearance (can be overridden below)
        npc.loadFromConfig();

        // Apply overrides if specified
        if (overrideBiome != null) {
            npc.setVillagerBiome(overrideBiome);
        }
        if (overrideProfession != null) {
            npc.setVillagerProfession(overrideProfession);
        }

        level.addFreshEntity(npc);

        source.sendSuccess(() -> Component.literal("Spawned NPC: " + config.displayName() + " (variant: " + effectiveVariant + ")"), true);

        return 1;
    }
}
