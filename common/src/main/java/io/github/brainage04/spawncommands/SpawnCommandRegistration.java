package io.github.brainage04.spawncommands;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainagelib.help.HelpPage;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class SpawnCommandRegistration {
    private SpawnCommandRegistration() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("spawn").executes(SpawnCommandRegistration::teleportToWorldSpawn));
        dispatcher.register(
                literal("myspawn").executes(SpawnCommandRegistration::teleportToOwnSpawn));
        dispatcher.register(
                literal("spawnof")
                        .then(
                                argument("player", EntityArgument.player())
                                        .executes(
                                                SpawnCommandRegistration::teleportToSharedSpawn)));
        dispatcher.register(
                literal("spawnshare")
                        .then(
                                literal("revoke")
                                        .then(
                                                argument("players", EntityArgument.players())
                                                        .executes(
                                                                context ->
                                                                        changeAccess(
                                                                                context, false))))
                        .then(
                                argument("players", EntityArgument.players())
                                        .executes(context -> changeAccess(context, true))));
        HelpPage.create(SpawnCommands.FEEDBACK, "spawncommands")
                .line("/spawn - teleport to the world spawn")
                .line("/myspawn - teleport to your own respawn point")
                .line("/spawnshare <players> - let players visit your spawn with /spawnof")
                .line("/spawnshare revoke <players> - take that access away again")
                .line("/spawnof <player> - teleport to a spawn someone shared with you")
                .register(dispatcher);
    }

    private static int teleportToWorldSpawn(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return SpawnTeleportService.teleportToWorldSpawn(
                context.getSource().getPlayerOrException());
    }

    private static int teleportToOwnSpawn(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return SpawnTeleportService.teleportToPersonalSpawn(player, player);
    }

    private static int teleportToSharedSpawn(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer owner = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();
        ServerPlayer guest = source.getPlayerOrException();
        if (!SpawnAccessData.get(source.getServer()).allows(owner.getUUID(), guest.getUUID())) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "%s has not shared their spawn with you.", owner.getScoreboardName());
        }
        return SpawnTeleportService.teleportToPersonalSpawn(guest, owner);
    }

    private static int changeAccess(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        CommandSourceStack source = context.getSource();
        ServerPlayer owner = source.getPlayerOrException();
        Collection<UUID> playerIds = players.stream().map(ServerPlayer::getUUID).toList();
        SpawnAccessData access = SpawnAccessData.get(source.getServer());
        int changed =
                grant
                        ? access.grant(owner.getUUID(), playerIds)
                        : access.revoke(owner.getUUID(), playerIds);
        String message =
                grant
                        ? "Shared your spawn with %d player(s)."
                        : "Revoked spawn access for %d player(s).";
        if (changed == 0) {
            SpawnCommands.FEEDBACK.neutral(source, message, changed);
        } else {
            SpawnCommands.FEEDBACK.success(source, message, false, changed);
        }
        return changed;
    }
}
