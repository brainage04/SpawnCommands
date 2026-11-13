package io.github.brainage04.spawncommands;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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
        List<ChunkPos> unloaded = chunksToLoad(level, player, Vec3.atBottomCenterOf(spawn.pos()));
        if (unloaded.isEmpty()) {
            return arriveAtWorldSpawn(player, level, spawn);
        }
        // The checks below read blocks, so the chunk has to be loaded first, and loading it here
        // would generate it on the server thread. Vanilla loads a chunk it must inspect without
        // blocking instead (PlayerSpawnFinder.java:114-121); the answer reaches the player from the
        // callback a few ticks later, and the command reports that it was accepted.
        load(level, unloaded)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            if (failure != null) {
                                SpawnCommands.FEEDBACK.error(
                                        player, "Could not teleport to world spawn.");
                            } else if (!player.hasDisconnected()) {
                                arriveAtWorldSpawn(player, level, spawn);
                            }
                        },
                        server);
        return 1;
    }

    private static int arriveAtWorldSpawn(
            ServerPlayer player, ServerLevel level, LevelData.RespawnData spawn) {
        Vec3 destination = onTopOf(level, spawn.pos());
        if (!fits(level, player, destination)) {
            SpawnCommands.FEEDBACK.error(
                    player, "Could not teleport to world spawn because it is obstructed.");
            return 0;
        }
        return teleport(player, level, destination, spawn.yaw(), spawn.pitch(), "world spawn");
    }

    /**
     * Where a player stands on {@code pos}: on top of the floor in that block, by vanilla's
     * floor-height rule (BlockGetter.getBlockFloorHeight, BlockGetter.java:96-110). An empty block
     * over a fence or wall puts the player on the fence top; a full block lifts the player onto it,
     * as vanilla's world-spawn search moves a buried spawn up (PlayerSpawnFinder.java:89-104). With
     * no floor (an empty block over air or a bottom slab, BlockGetter.java:101-102) the player
     * lands on the block's bottom.
     */
    private static Vec3 onTopOf(ServerLevel level, BlockPos pos) {
        double floor = level.getBlockFloorHeight(pos);
        return Vec3.upFromBottomCenterOf(pos, Double.isInfinite(floor) ? 0.0D : floor);
    }

    /**
     * The chunks under the standing box at {@code destination} that are not loaded. Nothing here
     * reads a block: the floor lookup needs the chunk, so the question is asked about the position
     * vanilla's own world spawn uses before its height search
     * (TeleportTransition.findAdjustedSharedSpawnPos, TeleportTransition.java:93-95).
     */
    private static List<ChunkPos> chunksToLoad(
            ServerLevel level, ServerPlayer player, Vec3 destination) {
        AABB standing = player.getLocalBoundsForPose(Pose.STANDING).move(destination);
        Set<ChunkPos> unloaded = new LinkedHashSet<>();
        for (int x = SectionPos.posToSectionCoord(standing.minX);
                x <= SectionPos.posToSectionCoord(standing.maxX);
                x++) {
            for (int z = SectionPos.posToSectionCoord(standing.minZ);
                    z <= SectionPos.posToSectionCoord(standing.maxZ);
                    z++) {
                if (!level.hasChunk(x, z)) unloaded.add(new ChunkPos(x, z));
            }
        }
        return new ArrayList<>(unloaded);
    }

    /**
     * Loads {@code chunks} to full without blocking the server thread, the way vanilla's spawn
     * search loads the chunk it is about to inspect (PlayerSpawnFinder.java:114-121). The ticket
     * holds the chunk until its load finishes and then expires, because it can only expire once the
     * chunk is unloaded or ready for saving (TicketStorage.java:290-300); TicketType.FORCED would
     * keep the chunk loaded for good.
     */
    private static CompletableFuture<?> load(ServerLevel level, List<ChunkPos> chunks) {
        CompletableFuture<?>[] loads =
                chunks.stream()
                        .map(
                                pos ->
                                        level.getChunkSource()
                                                .addTicketAndLoadWithRadius(
                                                        TicketType.SPAWN_SEARCH, pos, 0))
                        .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(loads);
    }

    /**
     * Whether the player fits at {@code destination}: vanilla's check for a passenger getting off a
     * mount (DismountHelper.canDismountTo, DismountHelper.java:42-50), block collisions and the
     * world border in any of the player's dismount poses (Player.getDismountPoses), judged at the
     * destination height. Liquids do not count, as in that check.
     */
    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 destination) {
        // Only called once every chunk under the box is loaded: block collisions treat a chunk that
        // is not loaded as empty (BlockCollisions.java:86-87, Level.getChunkForCollisions).
        // canDismountTo uses the player's own collision context, which shapes scaffolding and
        // powder snow by where the player stands now (ScaffoldingBlock.java:130-139,
        // PowderSnowBlock.java:116-131). A dismount happens at about that height and a teleport
        // does not, so this context judges them at the destination height. It has no entity, so
        // powder snow never collides, whatever boots the player wears.
        CollisionContext atDestination = CollisionContext.positionContext(destination.y());
        return player.getDismountPoses().stream()
                .map(pose -> player.getLocalBoundsForPose(pose).move(destination))
                .anyMatch(
                        box ->
                                noBlockCollision(level, atDestination, box)
                                        && level.getWorldBorder().isWithinBounds(box));
    }

    private static boolean noBlockCollision(ServerLevel level, CollisionContext context, AABB box) {
        for (VoxelShape collision : level.getBlockCollisionsFromContext(context, box)) {
            if (!collision.isEmpty()) {
                return false;
            }
        }
        return true;
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
