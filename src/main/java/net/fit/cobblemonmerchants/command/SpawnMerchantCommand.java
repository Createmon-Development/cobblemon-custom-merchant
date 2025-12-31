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
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Command for spawning merchants: /spawnmerchant <merchant_type> [player_skin_name]
 */
public class SpawnMerchantCommand {

    private static final SuggestionProvider<CommandSourceStack> MERCHANT_TYPE_SUGGESTIONS = (context, builder) -> {
        Map<ResourceLocation, MerchantConfig> configs = MerchantConfigRegistry.getAllConfigs();
        return SharedSuggestionProvider.suggestResource(configs.keySet(), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spawnmerchant")
                .requires(source -> source.hasPermission(2)) // OP level 2
                .then(Commands.argument("merchant_type", ResourceLocationArgument.id())
                    .suggests(MERCHANT_TYPE_SUGGESTIONS)
                    .executes(SpawnMerchantCommand::spawnMerchant)
                    .then(Commands.argument("player_skin_name", StringArgumentType.word())
                        .executes(SpawnMerchantCommand::spawnMerchantWithSkin)
                        .then(Commands.argument("villager_biome", StringArgumentType.word())
                            .executes(SpawnMerchantCommand::spawnMerchantWithBiome)
                            .then(Commands.argument("villager_profession", StringArgumentType.word())
                                .executes(SpawnMerchantCommand::spawnMerchantWithProfession)
                            )
                        )
                    )
                )
        );
    }

    private static int spawnMerchant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return spawnMerchantInternal(context, null, null, null);
    }

    private static int spawnMerchantWithSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String skinName = StringArgumentType.getString(context, "player_skin_name");
        return spawnMerchantInternal(context, skinName, null, null);
    }

    private static int spawnMerchantWithBiome(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String skinName = StringArgumentType.getString(context, "player_skin_name");
        String biome = StringArgumentType.getString(context, "villager_biome");
        return spawnMerchantInternal(context, skinName, biome, null);
    }

    private static int spawnMerchantWithProfession(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String skinName = StringArgumentType.getString(context, "player_skin_name");
        String biome = StringArgumentType.getString(context, "villager_biome");
        String profession = StringArgumentType.getString(context, "villager_profession");
        return spawnMerchantInternal(context, skinName, biome, profession);
    }

    private static int spawnMerchantInternal(CommandContext<CommandSourceStack> context, String overrideSkinName,
            String overrideBiome, String overrideProfession) throws CommandSyntaxException {
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

        // Treat "-" as placeholder (use config default)
        if ("-".equals(overrideSkinName)) overrideSkinName = null;
        if ("-".equals(overrideBiome)) overrideBiome = null;
        if ("-".equals(overrideProfession)) overrideProfession = null;

        // Create merchant entity
        CustomMerchantEntity merchant = new CustomMerchantEntity(ModEntities.CUSTOM_MERCHANT.get(), level);

        // Set position (spawn at player's position)
        BlockPos spawnPos = BlockPos.containing(position);
        merchant.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // Configure merchant
        merchant.setCustomName(Component.literal(config.displayName()));
        merchant.setCustomNameVisible(true);

        // Set player skin (use override if provided, otherwise use config)
        String skinName = overrideSkinName != null ? overrideSkinName : config.playerSkinName().orElse("");
        merchant.setPlayerSkinName(skinName);

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
            // Set static trades from config
            MerchantOffers offers = config.toMerchantOffers();
            merchant.setOffers(offers);
        }

        // Store the trader ID for future reference
        merchant.setTraderId(merchantTypeId);

        // Reload trades to populate tradeEntries list
        merchant.reloadTradesFromConfig();

        // Spawn the merchant
        level.addFreshEntity(merchant);

        // Send success message
        source.sendSuccess(() -> Component.literal("Spawned merchant: " + config.displayName() +
            (skinName.isEmpty() ? "" : " (skin: " + skinName + ")")), true);

        return 1;
    }
}
