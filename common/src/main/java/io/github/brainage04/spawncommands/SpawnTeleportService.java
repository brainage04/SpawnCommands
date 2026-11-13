package io.github.brainage04.spawncommands;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
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
     * Whether the player fits at {@code destination}: vanilla's check for a passenger getting off a
     * mount (DismountHelper.canDismountTo, DismountHelper.java:42-50), block collisions and the
     * world border in any of the player's dismount poses (Player.getDismountPoses), judged at the
     * destination height. Liquids do not count, as in that check.
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
        Deadly deadly = deadlyAt(level, destination);
        if (deadly != Deadly.NONE) {
            SpawnCommands.FEEDBACK.error(
                    player,
                    deadly == Deadly.LAVA
                            ? "Could not teleport to %s because it is in lava."
                            : "Could not teleport to %s because it is over the void.",
                    label);
            return 0;
        }
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

    /**
     * Why the destination cannot be used: it is in lava, or it is over the void. Vanilla's own
     * answer for a safe teleport destination is SpreadPlayersCommand.Pos.isSafe
     * (SpreadPlayersCommand.java:364-368): the first block from the destination down to the level's
     * minimum build height must exist, found by the scan in getSpawnY
     * (SpreadPlayersCommand.java:344-362), and must not be a liquid. Running out of blocks before
     * the minimum build height is the void. Lava is the liquid that kills, the one vanilla's lava
     * damage names (FluidTags.LAVA, Entity.java:1772-1774); water is survivable, so a spawn in it
     * is still usable.
     */
    private static Deadly deadlyAt(ServerLevel level, Vec3 destination) {
        BlockPos feet = BlockPos.containing(destination);
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int y = feet.getY(); y >= level.getMinY(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            return state.getFluidState().is(FluidTags.LAVA) ? Deadly.LAVA : Deadly.NONE;
        }
        return Deadly.VOID;
    }

    private enum Deadly {
        NONE,
        LAVA,
        VOID
    }
}
