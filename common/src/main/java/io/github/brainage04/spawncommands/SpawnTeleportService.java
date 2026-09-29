package io.github.brainage04.spawncommands;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Resolves and teleports players to world or personal respawn locations. */
public final class SpawnTeleportService {
	private SpawnTeleportService() {
	}

	public static int teleportToWorldSpawn(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.overworld();
		LevelData.RespawnData spawn = level.getLevelData().getRespawnData();
		return teleport(player, level, Vec3.atBottomCenterOf(spawn.pos()), spawn.yaw(), spawn.pitch(), "world spawn");
	}

	public static int teleportToPersonalSpawn(ServerPlayer player, ServerPlayer spawnOwner) {
		ServerPlayer.RespawnConfig config = spawnOwner.getRespawnConfig();
		if (config == null) {
			player.sendSystemMessage(Component.literal(spawnOwner == player
					? "You do not have a personal spawn point."
					: spawnOwner.getScoreboardName() + " does not have a personal spawn point."));
			return 0;
		}
		if (player.level().getServer().getLevel(config.respawnData().dimension()) == null) {
			player.sendSystemMessage(Component.literal("That personal spawn dimension is unavailable."));
			return 0;
		}

		// Resolve like a vanilla respawn (bed/anchor stand-up position, exact forced position) without using an
		// anchor charge. The world-spawn spread driven by the respawn radius gamerule never applies here.
		TeleportTransition respawn = spawnOwner.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		if (respawn.missingRespawnBlock()) {
			player.sendSystemMessage(Component.literal(spawnOwner == player
					? "Your personal spawn point is missing or obstructed."
					: spawnOwner.getScoreboardName() + "'s spawn point is missing or obstructed."));
			return 0;
		}
		String label = spawnOwner == player ? "your personal spawn" : spawnOwner.getScoreboardName() + "'s spawn";
		return teleport(player, respawn.newLevel(), respawn.position(), respawn.yRot(), respawn.xRot(), label);
	}

	private static int teleport(
			ServerPlayer player,
			ServerLevel level,
			Vec3 destination,
			float yaw,
			float pitch,
			String label
	) {
		boolean teleported = player.teleportTo(
				level,
				destination.x(),
				destination.y(),
				destination.z(),
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
