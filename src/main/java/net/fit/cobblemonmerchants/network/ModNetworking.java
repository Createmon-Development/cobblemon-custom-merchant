package net.fit.cobblemonmerchants.network;

import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CobblemonMerchants.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Client to server packets
        registrar.playToServer(
            TradeClickPacket.TYPE,
            TradeClickPacket.STREAM_CODEC,
            TradeClickPacket::handle
        );

        // Server to client packets
        registrar.playToClient(
            SyncMerchantOffersPacket.TYPE,
            SyncMerchantOffersPacket.STREAM_CODEC,
            SyncMerchantOffersPacket::handle
        );
    }
}
