package io.github.brainage04.spawncommands;

import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.phys.Vec3;

/**
 * The chunk ticket GameTests, in the package of {@link SpawnTeleportService} to reach its loads.
 */
public final class SpawnTicketGameTests {
    private SpawnTicketGameTests() {}

    // The GameTest server runs its ticks as fast as it can, so a tick budget cannot bound a wait
    // for a chunk load: the load needs wall time, and the test itself checks that instead.
    private static final long LOAD_TIMEOUT_NANOS = 120_000_000_000L;

    private static void assertStillLoading(GameTestHelper helper, long started) {
        if (System.nanoTime() - started > LOAD_TIMEOUT_NANOS) {
            helper.fail("The chunk did not load or unload within 2 min of wall time");
        }
    }

    private static ChunkPos farChunk(GameTestHelper helper, int zOffset) {
        BlockPos here = helper.absolutePos(new BlockPos(5, 1, 5));
        return new ChunkPos(
                SectionPos.blockToSectionCoord(here.getX() + 100_000),
                SectionPos.blockToSectionCoord(here.getZ() + zOffset));
    }

    private static Vec3 middleOf(ChunkPos chunk) {
        return new Vec3(
                SectionPos.sectionToBlockCoord(chunk.x(), 8) + 0.5D,
                64.0D,
                SectionPos.sectionToBlockCoord(chunk.z(), 8) + 0.5D);
    }

    private static boolean holdsLoadingTicket(ServerLevel level, ChunkPos chunk) {
        TicketStorage tickets = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        return tickets.getTickets(chunk.pack()).stream()
                .anyMatch(ticket -> ticket.getType() == TicketType.PLAYER_LOADING);
    }

    public static void aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos far = farChunk(helper, 72_000);
        helper.assertTrue(
                level.getChunkSource().getChunkNow(far.x(), far.z()) == null,
                "The chunk must not be loaded before the test");
        // Two teleports wait on the same chunk, and the second one ends first, as one does whose
        // player left or whose other chunks were already there.
        CompletableFuture<?> first = SpawnTeleportService.load(level, List.of(far));
        SpawnTeleportService.load(level, List.of(far));
        SpawnTeleportService.release(level, List.of(far));
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    first.isDone(), "The first teleport's load must finish");
                        })
                // Room for the distance manager to drop the chunk if its ticket had gone.
                .thenIdle(5)
                .thenExecute(
                        () -> {
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(far.x(), far.z()) != null,
                                    "The chunk must stay loaded while the first teleport still"
                                            + " needs it");
                            SpawnTeleportService.release(level, List.of(far));
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    level.getChunkSource().getChunkNow(far.x(), far.z()) == null,
                                    "The last teleport to end must give the ticket back");
                        })
                .thenSucceed();
    }

    public static void aReleaseWithoutALoadLeavesLaterTicketsAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos far = farChunk(helper, 168_000);
        SpawnTeleportService.release(level, List.of(far));
        SpawnTeleportService.load(level, List.of(far));
        helper.assertTrue(holdsLoadingTicket(level, far), "The load must take a ticket");
        SpawnTeleportService.release(level, List.of(far));
        helper.assertTrue(
                !holdsLoadingTicket(level, far),
                "The release matching the load must give the ticket back, whatever was released"
                        + " before it");
        helper.succeed();
    }

    public static void aTicketAnotherModTookAwayIsTakenAgainBeforeArriving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final steps, which run after this method has returned.
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnTicketTaken");
        ChunkPos far = farChunk(helper, 216_000);
        helper.assertTrue(
                level.getChunkSource().getChunkNow(far.x(), far.z()) == null,
                "The chunk must not be loaded before the test");
        boolean[] arrived = new boolean[1];
        boolean[] loadedOnArrival = new boolean[1];
        SpawnTeleportService.loadThenArrive(
                level,
                joined.player(),
                middleOf(far),
                "the test spot",
                1,
                () -> {
                    arrived[0] = true;
                    loadedOnArrival[0] =
                            level.getChunkSource().getChunkNow(far.x(), far.z()) != null;
                });
        // Another mod's teleport waiting on the same chunk ends: tickets of one type and level are
        // one ticket, so its release removes the ticket this load took as well.
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, far, 0);
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(arrived[0], "The teleport must arrive");
                        })
                .thenExecute(
                        () -> {
                            joined.close();
                            helper.assertTrue(
                                    loadedOnArrival[0],
                                    "The teleport must arrive only once its chunk is loaded again,"
                                            + " not on a chunk that lost its ticket");
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, far),
                                    "The ticket taken again must be given back");
                        })
                .thenSucceed();
    }

    public static void aFailedArrivalIsReportedAndGivesTheTicketBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final step, which runs after this method has returned.
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnArrivalFails");
        ChunkPos far = farChunk(helper, 120_000);
        SpawnTeleportService.loadThenArrive(
                level,
                joined.player(),
                middleOf(far),
                "the test spot",
                1,
                () -> {
                    throw new IllegalStateException("A GameTest arrival that fails on purpose");
                });
        helper.assertTrue(holdsLoadingTicket(level, far), "The load must take a ticket");
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    joined.received(
                                            "[SpawnCommands] Could not teleport to the test spot."),
                                    "A failed arrival must be reported to the player, got "
                                            + joined.messageTexts());
                        })
                .thenExecute(
                        () -> {
                            joined.close();
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, far),
                                    "A failed arrival must still give the ticket back");
                        })
                .thenSucceed();
    }

    private static boolean isFull(ServerLevel level, ChunkPos chunk) {
        return level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null;
    }

    public static void aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Closed inside the final steps, which run after this method has returned.
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnTwoChunks");
        ChunkPos west = farChunk(helper, 240_000);
        ChunkPos east = new ChunkPos(west.x() + 1, west.z());
        // On the edge between the two chunks, so the standing box lies over both.
        Vec3 edge =
                new Vec3(
                        SectionPos.sectionToBlockCoord(east.x()),
                        64.0D,
                        SectionPos.sectionToBlockCoord(west.z(), 8) + 0.5D);
        helper.assertTrue(
                !isFull(level, west) && !isFull(level, east),
                "The chunks must not be loaded before the test");
        boolean[] arrived = new boolean[1];
        boolean[] loadedOnArrival = new boolean[1];
        SpawnTeleportService.loadThenArrive(
                level,
                joined.player(),
                edge,
                "the test spot",
                1,
                () -> {
                    arrived[0] = true;
                    loadedOnArrival[0] = isFull(level, west) && isFull(level, east);
                });
        helper.assertTrue(
                holdsLoadingTicket(level, west) && holdsLoadingTicket(level, east),
                "The load must take a ticket on both chunks under the spot");
        // Another mod's teleport waiting on the east chunk only ends and takes the shared ticket.
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, east, 0);
        String refusal =
                "[SpawnCommands] Could not teleport to the test spot because its chunk would not"
                        + " stay loaded.";
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    arrived[0] || joined.received(refusal),
                                    "The teleport must arrive or give up");
                        })
                .thenExecute(
                        () -> {
                            List<String> messages = joined.messageTexts();
                            joined.close();
                            helper.assertTrue(
                                    arrived[0],
                                    "Both chunks must be loaded again, not given up on, got "
                                            + messages);
                            helper.assertTrue(
                                    loadedOnArrival[0],
                                    "The teleport must arrive only once both chunks are loaded");
                        })
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, west)
                                            && !holdsLoadingTicket(level, east),
                                    "Both tickets must be given back");
                        })
                .thenSucceed();
    }

    public static void aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameTestPlayer joined = GameTestPlayer.join(helper, "SpawnLeavesDuringLoad");
        ChunkPos far = farChunk(helper, 144_000);
        boolean[] arrived = new boolean[1];
        SpawnTeleportService.loadThenArrive(
                level,
                joined.player(),
                middleOf(far),
                "the test spot",
                1,
                () -> arrived[0] = true);
        helper.assertTrue(holdsLoadingTicket(level, far), "The load must take a ticket");
        joined.close();
        long started = System.nanoTime();
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            assertStillLoading(helper, started);
                            helper.assertTrue(
                                    !holdsLoadingTicket(level, far),
                                    "The ticket must be given back when the player has left");
                        })
                .thenExecute(
                        () ->
                                helper.assertTrue(
                                        !arrived[0],
                                        "A player who left during the load must not be teleported"))
                .thenSucceed();
    }
}
