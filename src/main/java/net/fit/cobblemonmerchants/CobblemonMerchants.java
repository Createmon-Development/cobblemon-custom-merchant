package net.fit.cobblemonmerchants;

import net.fit.cobblemonmerchants.item.ModItems;
import net.fit.cobblemonmerchants.merchant.ModEntities;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CobblemonMerchants.MODID)
public class CobblemonMerchants {
    public static final String MODID = "cobblemoncustommerchants";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CobblemonMerchants(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);

        // Register items and entities
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        net.fit.cobblemonmerchants.merchant.menu.ModMenuTypes.register(modEventBus);

        // Register entity attributes
        modEventBus.addListener(this::registerEntityAttributes);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Common setup
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("===== SERVER STARTING - BLACK MARKET INITIALIZATION =====");
        // Initialize the Black Market drop registry when server starts
        // This parses all Cobblemon species and builds the drop data
        try {
            net.fit.cobblemonmerchants.merchant.blackmarket.CobblemonDropRegistry.initialize();
            LOGGER.info("Initialized Black Market drop registry");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Black Market drop registry", e);
        }

        LOGGER.info("===== BLACK MARKET INITIALIZATION COMPLETE =====");
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        // Register merchant config reload listener
        event.addListener(net.fit.cobblemonmerchants.merchant.config.MerchantConfigRegistry.getInstance());
        LOGGER.info("Registered merchant config reload listener");
    }

    @SubscribeEvent
    public void onDatapackReload(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        // Update all merchant trades after datapack reload
        if (event.getPlayerList() != null) {
            // Server-wide reload (happens during /reload command)
            updateAllMerchantTrades(event.getPlayerList().getServer());
        }
    }

    /**
     * Updates all CustomMerchantEntity instances in all worlds with reloaded configs
     */
    private void updateAllMerchantTrades(net.minecraft.server.MinecraftServer server) {
        int updatedCount = 0;

        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof net.fit.cobblemonmerchants.merchant.CustomMerchantEntity merchant) {
                    merchant.reloadTradesFromConfig();
                    updatedCount++;
                }
            }
        }

        LOGGER.info("Updated trades for {} merchant(s) across all dimensions", updatedCount);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        net.fit.cobblemonmerchants.command.SpawnMerchantCommand.register(event.getDispatcher());
        net.fit.cobblemonmerchants.command.RefreshBlackMarketCommand.register(event.getDispatcher());
        LOGGER.info("Registered /spawnmerchant and /refreshblackmarket commands");
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CUSTOM_MERCHANT.get(),
                net.fit.cobblemonmerchants.merchant.CustomMerchantEntity.createAttributes().build());
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(
                net.fit.cobblemonmerchants.merchant.ModEntities.CUSTOM_MERCHANT.get(),
                ctx -> new net.minecraft.client.renderer.entity.VillagerRenderer(ctx)
            );
        }

        @SubscribeEvent
        public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(
                net.fit.cobblemonmerchants.merchant.menu.ModMenuTypes.MERCHANT_TRADE_MENU.get(),
                net.fit.cobblemonmerchants.merchant.client.MerchantTradeScreen::new
            );
        }
    }
}