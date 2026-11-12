package io.github.brainage04.spawncommands;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
        Vec3 destination = onTopOf(level, spawn.pos());
        if (!fits(level, player, destination)) {
            SpawnCommands.FEEDBACK.error(
                    player, "Could not teleport to world spawn because it is obstructed.");
            return 0;
        }
        return teleport(player, level, destination, spawn.yaw(), spawn.pitch(), "world spawn");
    }

    /**
     * Where a player stands on {@code pos}: on top of its collision shape, the floor height vanilla
     * measures before it stands a player up beside a bed or anchor
     * (DismountHelper.findSafeDismountLocation, BlockGetter.getBlockFloorHeight), or the block's
     * bottom when it has none. A full block lifts the player onto it, as vanilla's world-spawn
     * search moves a buried spawn up (PlayerSpawnFinder.java:89-104).
     */
    private static Vec3 onTopOf(ServerLevel level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return Vec3.upFromBottomCenterOf(pos, shape.isEmpty() ? 0.0D : shape.max(Direction.Axis.Y));
    }

    /**
     * Whether the player fits at {@code destination} in one of the poses vanilla tries when it puts
     * a player down from a vehicle: the same block-collision and world-border test
     * (DismountHelper.canDismountTo, Player.getDismountPoses). Liquids do not count, as in that
     * test.
     */
    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 destination) {
        // Block collisions treat a chunk that is not loaded as empty (BlockCollisions.java:86-87,
        // Level.getChunkForCollisions), so load the chunks under the standing box first.
        AABB standing = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        for (int x = SectionPos.posToSectionCoord(standing.minX);
                x <= SectionPos.posToSectionCoord(standing.maxX);
                x++) {
            for (int z = SectionPos.posToSectionCoord(standing.minZ);
                    z <= SectionPos.posToSectionCoord(standing.maxZ);
                    z++) {
                level.getChunk(x, z);
            }
        }
        return player.getDismountPoses().stream()
                .anyMatch(pose -> DismountHelper.canDismountTo(level, destination, player, pose));
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
