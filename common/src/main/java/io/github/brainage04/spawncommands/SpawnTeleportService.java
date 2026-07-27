package io.github.brainage04.spawncommands;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

import java.util.Set;

/** Resolves and teleports players to world or personal respawn locations. */
public final class SpawnTeleportService {
	private SpawnTeleportService() {
	}

	public static int teleportToWorldSpawn(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.overworld();
		LevelData.RespawnData spawn = level.getLevelData().getRespawnData();
		return teleport(player, level, spawn.pos(), spawn.yaw(), spawn.pitch(), "world spawn");
	}

	public static int teleportToPersonalSpawn(ServerPlayer player, ServerPlayer spawnOwner) {
		ServerPlayer.RespawnConfig config = spawnOwner.getRespawnConfig();
		if (config == null) {
			player.sendSystemMessage(Component.literal(spawnOwner == player
					? "You do not have a personal spawn point."
					: spawnOwner.getScoreboardName() + " does not have a personal spawn point."));
			return 0;
		}

		LevelData.RespawnData spawn = config.respawnData();
		ServerLevel level = player.level().getServer().getLevel(spawn.dimension());
		if (level == null) {
			player.sendSystemMessage(Component.literal("That personal spawn dimension is unavailable."));
			return 0;
		}
		BlockPos destination = spawnOwner.adjustSpawnLocation(level, spawn.pos());
		String label = spawnOwner == player ? "your personal spawn" : spawnOwner.getScoreboardName() + "'s spawn";
		return teleport(player, level, destination, spawn.yaw(), spawn.pitch(), label);
	}

	private static int teleport(
			ServerPlayer player,
			ServerLevel level,
			BlockPos destination,
			float yaw,
			float pitch,
			String label
	) {
		boolean teleported = player.teleportTo(
				level,
				destination.getX() + 0.5D,
				destination.getY(),
				destination.getZ() + 0.5D,
				Set.of(),
				yaw,
				pitch,
				false
		);
		if (!teleported) {
			player.sendSystemMessage(Component.literal("Could not teleport to " + label + "."));
			return 0;
		}
		player.sendSystemMessage(Component.literal("Teleported to " + label + "."));
		return 1;
	}
}
