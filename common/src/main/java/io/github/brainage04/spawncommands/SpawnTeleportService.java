package io.github.brainage04.spawncommands;

import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

public final class SpawnTeleportService {
    private SpawnTeleportService() {}

    public static int teleportToWorldSpawn(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        LevelData.RespawnData spawn = server.overworld().getLevelData().getRespawnData();
        ServerLevel level = server.getLevel(spawn.dimension());
        if (level == null) {
            SpawnCommands.FEEDBACK.error(player, "The world spawn dimension is unavailable.");
            return 0;
        }
        return teleport(
                player,
                level,
                Vec3.atBottomCenterOf(spawn.pos()),
                spawn.yaw(),
                spawn.pitch(),
                "world spawn");
    }

    public static int teleportToPersonalSpawn(ServerPlayer player, ServerPlayer spawnOwner) {
        ServerPlayer.RespawnConfig config = spawnOwner.getRespawnConfig();
        if (config == null) {
            if (spawnOwner == player) {
                SpawnCommands.FEEDBACK.error(player, "You do not have a personal spawn point.");
            } else {
                SpawnCommands.FEEDBACK.error(
                        player,
                        "%s does not have a personal spawn point.",
                        spawnOwner.getScoreboardName());
            }
            return 0;
        }
        if (player.level().getServer().getLevel(config.respawnData().dimension()) == null) {
            SpawnCommands.FEEDBACK.error(player, "That personal spawn dimension is unavailable.");
            return 0;
        }

        // Resolve like a vanilla respawn (bed/anchor stand-up position, exact forced position)
        // without using an anchor charge. The world-spawn spread driven by the respawn radius
        // gamerule never applies here.
        TeleportTransition respawn =
                spawnOwner.findRespawnPositionAndUseSpawnBlock(
                        false, TeleportTransition.DO_NOTHING);
        if (respawn.missingRespawnBlock()) {
            if (spawnOwner == player) {
                SpawnCommands.FEEDBACK.error(
                        player, "Your personal spawn point is missing or obstructed.");
            } else {
                SpawnCommands.FEEDBACK.error(
                        player,
                        "%s's spawn point is missing or obstructed.",
                        spawnOwner.getScoreboardName());
            }
            return 0;
        }
        String label =
                spawnOwner == player
                        ? "your personal spawn"
                        : spawnOwner.getScoreboardName() + "'s spawn";
        return teleport(
                player,
                respawn.newLevel(),
                respawn.position(),
                respawn.yRot(),
                respawn.xRot(),
                label);
    }

    private static int teleport(
            ServerPlayer player,
            ServerLevel level,
            Vec3 destination,
            float yaw,
            float pitch,
            String label) {
        boolean teleported =
                player.teleportTo(
                        level,
                        destination.x(),
                        destination.y(),
                        destination.z(),
                        Set.of(),
                        yaw,
                        pitch,
                        false);
        if (!teleported) {
            SpawnCommands.FEEDBACK.error(player, "Could not teleport to %s.", label);
            return 0;
        }
        SpawnCommands.FEEDBACK.success(player, "Teleported to %s.", label);
        return 1;
    }
}
