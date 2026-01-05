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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Command for spawning merchants: /spawnmerchant <merchant_type> [villager_biome] [villager_profession] [variant]
 * Use "_" as a placeholder for biome/profession to use the config default.
 * Example: /spawnmerchant cobblemoncustommerchants:gambler _ _ housed
 */
public class SpawnMerchantCommand {

    private static final SuggestionProvider<CommandSourceStack> MERCHANT_TYPE_SUGGESTIONS = (context, builder) -> {
        Map<ResourceLocation, MerchantConfig> configs = MerchantConfigRegistry.getAllConfigs();
        return SharedSuggestionProvider.suggestResource(configs.keySet(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> BIOME_SUGGESTIONS = (context, builder) -> {
        // Suggest "_" for default plus common biomes
        return SharedSuggestionProvider.suggest(new String[]{
            "_", "minecraft:plains", "minecraft:desert", "minecraft:jungle",
            "minecraft:savanna", "minecraft:snow", "minecraft:swamp", "minecraft:taiga"
        }, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> PROFESSION_SUGGESTIONS = (context, builder) -> {
        // Suggest "_" for default plus common professions
        return SharedSuggestionProvider.suggest(new String[]{
            "_", "minecraft:none", "minecraft:armorer", "minecraft:butcher",
            "minecraft:cartographer", "minecraft:cleric", "minecraft:farmer",
            "minecraft:fisherman", "minecraft:fletcher", "minecraft:leatherworker",
            "minecraft:librarian", "minecraft:mason", "minecraft:nitwit",
            "minecraft:shepherd", "minecraft:toolsmith", "minecraft:weaponsmith"
        }, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> VARIANT_SUGGESTIONS = (context, builder) -> {
        // Suggest common variant names
        return SharedSuggestionProvider.suggest(new String[]{"default", "housed"}, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawnmerchant")
                .requires(source -> source.hasPermission(2)) // OP level 2
                .then(Commands.argument("merchant_type", ResourceLocationArgument.id())
                    .suggests(MERCHANT_TYPE_SUGGESTIONS)
                    .executes(SpawnMerchantCommand::spawnMerchant)
                    .then(Commands.argument("villager_biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(SpawnMerchantCommand::spawnMerchantWithBiome)
                        .then(Commands.argument("villager_profession", StringArgumentType.word())
                            .suggests(PROFESSION_SUGGESTIONS)
                            .executes(SpawnMerchantCommand::spawnMerchantWithProfession)
                            .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests(VARIANT_SUGGESTIONS)
                                .executes(SpawnMerchantCommand::spawnMerchantWithVariant)
                            )
                        )
                    )
                )
        );
    }

    private static int spawnMerchant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return spawnMerchantInternal(context, null, null, null);
    }

    private static int spawnMerchantWithBiome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String biome = StringArgumentType.getString(context, "villager_biome");
        return spawnMerchantInternal(context, biome, null, null);
    }

    private static int spawnMerchantWithProfession(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String biome = StringArgumentType.getString(context, "villager_biome");
        String profession = StringArgumentType.getString(context, "villager_profession");
        return spawnMerchantInternal(context, biome, profession, null);
    }

    private static int spawnMerchantWithVariant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String biome = StringArgumentType.getString(context, "villager_biome");
        String profession = StringArgumentType.getString(context, "villager_profession");
        String variant = StringArgumentType.getString(context, "variant");
        return spawnMerchantInternal(context, biome, profession, variant);
    }

    private static int spawnMerchantInternal(CommandContext<CommandSourceStack> context,
            String overrideBiome, String overrideProfession, String variant) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation merchantTypeId = ResourceLocationArgument.getId(context, "merchant_type");
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();

        // Get merchant config
        MerchantConfig config = MerchantConfigRegistry.getConfig(merchantTypeId);
        if (config == null) {
            source.sendFailure(Component.literal("Unknown merchant type: " + merchantTypeId));
            return 0;
        }

        // Treat "_" or "-" as placeholder (use config default)
        if ("_".equals(overrideBiome) || "-".equals(overrideBiome)) overrideBiome = null;
        if ("_".equals(overrideProfession) || "-".equals(overrideProfession)) overrideProfession = null;

        // Create merchant entity
        CustomMerchantEntity merchant = new CustomMerchantEntity(ModEntities.CUSTOM_MERCHANT.get(), level);

        // Set position (spawn at player's position)
        BlockPos spawnPos = BlockPos.containing(position);
        merchant.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // Configure merchant
        merchant.setCustomName(Component.literal(config.displayName()));
        merchant.setCustomNameVisible(true);

        // Set villager biome (use override if provided, otherwise use config, default to plains)
        String biome = overrideBiome != null ? overrideBiome : config.villagerBiome().orElse("plains");
        merchant.setVillagerBiome(biome);

        // Set villager profession (use override if provided, otherwise use config, default to none)
        String profession = overrideProfession != null ? overrideProfession : config.villagerProfession().orElse("none");
        merchant.setVillagerProfession(profession);

        // Set merchant type based on ID
        if (merchantTypeId.equals(ResourceLocation.fromNamespaceAndPath("cobblemoncustommerchants", "black_market"))) {
            merchant.setMerchantType(CustomMerchantEntity.MerchantType.BLACK_MARKET);
        } else {
            merchant.setMerchantType(CustomMerchantEntity.MerchantType.REGULAR);
        }

        // Store the trader ID for future reference
        merchant.setTraderId(merchantTypeId);

        // Set variant (affects daily rewards and variant-specific trades)
        if (variant != null && !variant.isEmpty()) {
            merchant.setMerchantVariant(variant);
        }

        // Reload trades from config - this filters by variant and populates tradeEntries list
        merchant.reloadTradesFromConfig();

        // Spawn the merchant
        level.addFreshEntity(merchant);

        // Send success message
        String variantInfo = variant != null ? " (variant: " + variant + ")" : "";
        source.sendSuccess(() -> Component.literal("Spawned merchant: " + config.displayName() + variantInfo), true);

        return 1;
    }
}
