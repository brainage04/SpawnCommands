package io.github.brainage04.spawncommands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;

public final class SpawnCommandRegistration {
	private SpawnCommandRegistration() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawn").executes(context ->
				SpawnTeleportService.teleportToWorldSpawn(context.getSource().getPlayerOrException())));
		dispatcher.register(Commands.literal("myspawn").executes(context -> {
			ServerPlayer player = context.getSource().getPlayerOrException();
			return SpawnTeleportService.teleportToPersonalSpawn(player, player);
		}));
		dispatcher.register(Commands.literal("spawnof")
				.then(Commands.argument("player", EntityArgument.player()).executes(context ->
						teleportToSharedSpawn(
								context.getSource(),
								EntityArgument.getPlayer(context, "player")
						))));
		dispatcher.register(Commands.literal("spawnshare")
				.then(Commands.literal("revoke")
						.then(Commands.argument("players", EntityArgument.players()).executes(context ->
								changeAccess(context.getSource(), EntityArgument.getPlayers(context, "players"), false))))
				.then(Commands.argument("players", EntityArgument.players()).executes(context ->
						changeAccess(context.getSource(), EntityArgument.getPlayers(context, "players"), true))));
	}

	private static int teleportToSharedSpawn(CommandSourceStack source, ServerPlayer owner)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer guest = source.getPlayerOrException();
		if (!SpawnAccessData.get(source.getServer()).allows(owner.getUUID(), guest.getUUID())) {
			source.sendFailure(Component.literal(owner.getScoreboardName() + " has not shared their spawn with you."));
			return 0;
		}
		return SpawnTeleportService.teleportToPersonalSpawn(guest, owner);
	}

	private static int changeAccess(
			CommandSourceStack source,
			Collection<ServerPlayer> players,
			boolean grant
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer owner = source.getPlayerOrException();
		Collection<UUID> playerIds = players.stream().map(ServerPlayer::getUUID).toList();
		SpawnAccessData access = SpawnAccessData.get(source.getServer());
		int changed = grant
				? access.grant(owner.getUUID(), playerIds)
				: access.revoke(owner.getUUID(), playerIds);
		String action = grant ? "Shared your spawn with " : "Revoked spawn access for ";
		source.sendSuccess(() -> Component.literal(action + changed + " player(s)."), false);
		return changed;
	}
}
