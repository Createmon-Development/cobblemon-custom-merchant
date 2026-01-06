package net.fit.cobblemonmerchants.network;

import io.netty.buffer.ByteBuf;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.fit.cobblemonmerchants.merchant.menu.MerchantTradeMenu;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Packet sent from client to server when a player clicks on a trade in the merchant GUI
 */
public record TradeClickPacket(int tradeIndex) implements CustomPacketPayload {
    public static final Type<TradeClickPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("cobblemoncustommerchants", "trade_click")
    );

    public static final StreamCodec<ByteBuf, TradeClickPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        TradeClickPacket::tradeIndex,
        TradeClickPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Sends this packet to the server
     */
    public static void send(int tradeIndex) {
        PacketDistributor.sendToServer(new TradeClickPacket(tradeIndex));
    }

    /**
     * Handles the packet on the server side
     */
    public static void handle(TradeClickPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.containerMenu instanceof MerchantTradeMenu menu) {
                    boolean success = menu.executeTrade(packet.tradeIndex, serverPlayer);

                    if (success) {
                        // Play success sound at merchant location (80% volume)
                        if (menu.getMerchant() != null) {
                            serverPlayer.level().playSound(
                                null,
                                menu.getMerchant().blockPosition(),
                                SoundEvents.EXPERIENCE_ORB_PICKUP,
                                SoundSource.BLOCKS,
                                0.8F,
                                1.0F
                            );
                        }
                    } else {
                        // Play failure sound
                        serverPlayer.level().playSound(
                            null,
                            serverPlayer.blockPosition(),
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            SoundSource.BLOCKS,
                            1.0F,
                            0.8F
                        );
                    }
                }
            }
        });
    }
}
