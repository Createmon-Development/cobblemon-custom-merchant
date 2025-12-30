package net.fit.cobblemonmerchants.network;

import io.netty.buffer.ByteBuf;
import net.fit.cobblemonmerchants.merchant.menu.MerchantTradeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Packet sent from server to client to synchronize merchant offers
 */
public record SyncMerchantOffersPacket(int containerId, MerchantOffers offers) implements CustomPacketPayload {
    public static final Type<SyncMerchantOffersPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("cobblemoncustommerchants", "sync_merchant_offers")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMerchantOffersPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncMerchantOffersPacket packet) {
            buf.writeInt(packet.containerId);
            buf.writeInt(packet.offers.size());

            for (MerchantOffer offer : packet.offers) {
                // Write ItemCost A
                ItemStack.STREAM_CODEC.encode(buf, offer.getItemCostA().itemStack());

                // Write optional ItemStack B
                ItemStack costB = offer.getCostB();
                buf.writeBoolean(!costB.isEmpty());
                if (!costB.isEmpty()) {
                    ItemStack.STREAM_CODEC.encode(buf, costB);
                }

                // Write result
                ItemStack.STREAM_CODEC.encode(buf, offer.getResult());

                // Write other fields
                buf.writeInt(offer.getUses());
                buf.writeInt(offer.getMaxUses());
                buf.writeInt(offer.getXp());
                buf.writeFloat(offer.getPriceMultiplier());
                buf.writeInt(offer.getDemand());
            }
        }

        @Override
        public SyncMerchantOffersPacket decode(RegistryFriendlyByteBuf buf) {
            int containerId = buf.readInt();
            int size = buf.readInt();
            MerchantOffers offers = new MerchantOffers();

            for (int i = 0; i < size; i++) {
                // Read ItemCost A
                ItemStack costAStack = ItemStack.STREAM_CODEC.decode(buf);
                ItemCost costA = new ItemCost(costAStack.getItemHolder().value(), costAStack.getCount());

                // Read optional ItemStack B
                Optional<ItemCost> costB = Optional.empty();
                if (buf.readBoolean()) {
                    ItemStack costBStack = ItemStack.STREAM_CODEC.decode(buf);
                    costB = Optional.of(new ItemCost(costBStack.getItemHolder().value(), costBStack.getCount()));
                }

                // Read result
                ItemStack result = ItemStack.STREAM_CODEC.decode(buf);

                // Read other fields
                int uses = buf.readInt();
                int maxUses = buf.readInt();
                int xp = buf.readInt();
                float priceMultiplier = buf.readFloat();
                int demand = buf.readInt();

                // Create the merchant offer
                MerchantOffer offer = new MerchantOffer(costA, costB, result, uses, maxUses, xp, priceMultiplier, demand);
                offers.add(offer);
            }

            return new SyncMerchantOffersPacket(containerId, offers);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the packet on the client side
     */
    public static void handle(SyncMerchantOffersPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MerchantTradeMenu menu) {
                if (menu.containerId == packet.containerId) {
                    menu.setOffers(packet.offers);
                    net.fit.cobblemonmerchants.CobblemonMerchants.LOGGER.info("CLIENT: Received {} offers from server", packet.offers.size());
                }
            }
        });
    }
}
