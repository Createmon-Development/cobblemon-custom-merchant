package net.fit.cobblemonmerchants.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fit.cobblemonmerchants.CobblemonMerchants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Hidden command for handling NPC dialogue teleport options.
 * This is executed when players click on teleport links in chat.
 *
 * Command format: /npcteleport <player> <x> <y> <z> <dimension>
 *
 * This command:
 * - Requires no permission (so players can click teleport options)
 * - Only teleports the player who executed the command (security measure)
 * - Validates the player argument matches the executor
 */
public class NPCTeleportCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npcteleport")
            // No permission required - players need to be able to execute this via chat click
            .then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .then(Commands.argument("dimension", StringArgumentType.greedyString())
                                .executes(NPCTeleportCommand::executeTeleport))))))
        );
    }

    private static int executeTeleport(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        // Verify this is a player executing the command
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        // Get arguments
        String targetPlayerName = StringArgumentType.getString(ctx, "player");
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        String dimensionStr = StringArgumentType.getString(ctx, "dimension");

        // Security check: only allow teleporting yourself
        if (!player.getName().getString().equals(targetPlayerName)) {
            source.sendFailure(Component.literal("You can only teleport yourself"));
            CobblemonMerchants.LOGGER.warn("Player {} attempted to teleport {} via npcteleport command",
                player.getName().getString(), targetPlayerName);
            return 0;
        }

        // Parse dimension
        ResourceLocation dimensionLoc;
        try {
            dimensionLoc = ResourceLocation.parse(dimensionStr);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Invalid dimension: " + dimensionStr));
            return 0;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLoc);
        ServerLevel targetLevel = source.getServer().getLevel(dimensionKey);

        if (targetLevel == null) {
            source.sendFailure(Component.literal("Dimension not found: " + dimensionStr));
            return 0;
        }

        // Perform the teleport
        try {
            // If different dimension, transfer first
            if (player.level() != targetLevel) {
                player.teleportTo(targetLevel, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
            } else {
                player.teleportTo(x + 0.5, y, z + 0.5);
            }

            // Send feedback
            player.sendSystemMessage(Component.literal("* You feel a strange sensation as you are transported... *")
                .withStyle(style -> style.withColor(0x7F7F7F).withItalic(true)));

            CobblemonMerchants.LOGGER.debug("Teleported {} to {}, {}, {} in {}",
                player.getName().getString(), x, y, z, dimensionStr);

            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("Teleport failed: " + e.getMessage()));
            CobblemonMerchants.LOGGER.error("Teleport failed for player {}", player.getName().getString(), e);
            return 0;
        }
    }
}
