package io.github.brainage04.spawncommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnCommandsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The lookup and its limiter live in BrainageLib, so every mod on the server shares one lookup thread
     * and one share of Mojang's limit. A copy here would quietly take a second share.
     */
    @Test
    void theLookupAndItsLimiterAreNotCopiedHere() {
        for (String name : List.of("NameLookup", "ShareRateLimit")) {
            assertThrows(
                    ClassNotFoundException.class,
                    () -> Class.forName("io.github.brainage04.spawncommands." + name),
                    name + " must come from BrainageLib, not from this mod");
        }
    }

    @Test
    void keepsSpawnSharesOwnerScopedAndRevocable() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = new UUID(0, 1);
        UUID otherOwner = new UUID(0, 2);
        UUID guest = new UUID(0, 3);

        assertEquals(1, data.grant(owner, List.of(guest)));
        assertEquals(0, data.grant(owner, List.of(guest)));
        assertTrue(data.allows(owner, guest));
        assertFalse(data.allows(otherOwner, guest));
        assertEquals(1, data.revoke(owner, List.of(guest)));
        assertFalse(data.allows(owner, guest));
    }

    @Test
    void persistsOwnerIsolationAndRevocationThroughTheSavedDataCodec() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = new UUID(0, 1);
        UUID otherOwner = new UUID(0, 2);
        UUID guest = new UUID(0, 3);
        UUID revokedGuest = new UUID(0, 4);
        data.grant(owner, List.of(guest, revokedGuest));
        data.grant(otherOwner, List.of(revokedGuest));
        data.revoke(owner, List.of(revokedGuest));

        var encoded = SpawnAccessData.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        SpawnAccessData restored =
                SpawnAccessData.TYPE.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.allows(owner, guest));
        assertFalse(restored.allows(owner, revokedGuest));
        assertFalse(restored.allows(otherOwner, guest));
        assertTrue(restored.allows(otherOwner, revokedGuest));
        assertEquals(1, restored.revoke(owner, List.of(guest)));
        assertFalse(restored.allows(owner, guest));
    }

    @Test
    void marksOnlyEffectiveShareChangesForSaving() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = new UUID(0, 1);
        UUID guest = new UUID(0, 2);
        assertTrue(data.allows(owner, owner));
        assertEquals(0, data.grant(owner, List.of(owner)));
        assertFalse(data.isDirty());
        assertEquals(1, data.grant(owner, List.of(guest, guest)));
        assertTrue(data.isDirty());

        data.setDirty(false);
        assertEquals(0, data.grant(owner, List.of(guest)));
        assertFalse(data.isDirty());
        assertEquals(1, data.revoke(owner, List.of(guest, guest)));
        assertTrue(data.isDirty());

        data.setDirty(false);
        assertEquals(0, data.revoke(owner, List.of(guest)));
        assertFalse(data.isDirty());
        assertTrue(data.allows(owner, owner));
    }

    @Test
    void aMalformedUuidDropsOnlyItsOwnEntry() {
        String owner = "00000000-0000-0000-0000-000000000001";
        String guest = "00000000-0000-0000-0000-000000000002";
        var json =
                JsonParser.parseString(
                        "{\"shares\": {\""
                                + owner
                                + "\": [\""
                                + guest
                                + "\", \"not-a-uuid\"], \"not-a-uuid\": [\""
                                + guest
                                + "\"]}}");
        var result = SpawnAccessData.TYPE.codec().parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        // The server loads the partial result (SavedDataStorage.java:93-95).
        SpawnAccessData data = result.resultOrPartial(error -> {}).orElseThrow();
        assertTrue(data.allows(UUID.fromString(owner), UUID.fromString(guest)));
    }

    /**
     * A dedicated server keeps {@code usercache.json} in the universe directory, and the world
     * directory is its child ({@code Main.java:126-130}, {@code LevelStorageSource.java:129-131}):
     * a server started with a non-default {@code --universe} has no cache where {@code
     * getServerDirectory()} points, so the parent of the world directory answers.
     */
    @Test
    void findsTheCacheInTheUniverseDirectoryOfAServerStartedElsewhere(@TempDir Path root)
            throws IOException {
        Path universe = Files.createDirectory(root.resolve("universe"));
        Path world = Files.createDirectory(universe.resolve("world"));
        Files.writeString(universe.resolve("usercache.json"), "[]");

        assertEquals(
                Optional.of(universe.resolve("usercache.json")),
                ServerNameCache.cacheFile(world, Optional.of(world)));
    }

    /** A server whose working directory is the universe directory finds the cache there. */
    @Test
    void findsTheCacheBesideTheWorldInTheWorkingDirectory(@TempDir Path root) throws IOException {
        Path world = Files.createDirectory(root.resolve("world"));
        Path cache = root.resolve("usercache.json");
        Files.writeString(cache, "[]");

        assertEquals(Optional.of(cache), ServerNameCache.cacheFile(root, Optional.of(world)));
    }

    /** An integrated server's game directory holds the cache; the world sits in saves/. */
    @Test
    void findsTheCacheInTheGameDirectory(@TempDir Path gameDirectory) throws IOException {
        Path world = Files.createDirectories(gameDirectory.resolve("saves/world"));
        Path cache = gameDirectory.resolve("usercache.json");
        Files.writeString(cache, "[]");

        assertEquals(
                Optional.of(cache), ServerNameCache.cacheFile(gameDirectory, Optional.of(world)));
    }

    /**
     * The production shape: {@code MinecraftServer.getWorldPath(LevelResource.ROOT)} is the world
     * directory with a {@code "/."} suffix ({@code LevelStorageSource.java:448-450,511-513}), whose
     * parent is the world directory itself, not the universe directory.
     */
    @Test
    void findsTheCacheBesideAWorldPathWithItsDotSuffix(@TempDir Path root) throws IOException {
        Path world = Files.createDirectory(root.resolve("world"));
        Files.writeString(root.resolve("usercache.json"), "[]");

        assertEquals(
                Optional.of(root.resolve("usercache.json")),
                ServerNameCache.cacheFile(
                        root.resolve("elsewhere"), Optional.of(world.resolve("."))));
    }

    @Test
    void answersNoCacheWhenNeitherPlaceHasOne(@TempDir Path root) throws IOException {
        Path world = Files.createDirectory(root.resolve("world"));

        assertEquals(Optional.empty(), ServerNameCache.cacheFile(root, Optional.of(world)));
    }

    @Test
    void aPlayerKeepsTheNewestNamesAndRepeatsMoveToTheFront() {
        SpawnAccessData data = new SpawnAccessData();
        UUID guest = UUID.randomUUID();
        data.sawName(guest, "Alex", 1_000);
        data.sawName(guest, "Steve", 2_000);
        assertEquals(
                List.of(
                        new SpawnAccessData.SeenName("Steve", 2_000),
                        new SpawnAccessData.SeenName("Alex", 1_000)),
                data.names(guest));
        data.sawName(guest, "Alex", 3_000);
        assertEquals(
                List.of(
                        new SpawnAccessData.SeenName("Alex", 3_000),
                        new SpawnAccessData.SeenName("Steve", 2_000)),
                data.names(guest));
    }

    /** The ten-name cap is what keeps a guest's history bounded, so it needs a step of its own. */
    @Test
    void aGuestKeepsOnlyTheTenNewestNames() {
        SpawnAccessData data = new SpawnAccessData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 12; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        List<String> kept = data.names(guest).stream().map(SpawnAccessData.SeenName::name).toList();
        assertEquals(10, kept.size(), "a guest must keep ten names, got " + kept);
        assertEquals(List.of("Name11", "Name10", "Name9"), kept.subList(0, 3));
        assertTrue(!kept.contains("Name0") && !kept.contains("Name1"), "the oldest must go");
    }

    /**
     * A name an owner typed is recorded without a time, so it can be matched again while "last
     * seen" stays the last join the server recorded.
     */
    @Test
    void aRecordedNameCarriesNoSighting() {
        SpawnAccessData data = new SpawnAccessData();
        UUID guest = UUID.randomUUID();
        data.sawName(guest, "Steve", 2_000);
        data.recordName(guest, "Alex");
        assertEquals(
                List.of(
                        new SpawnAccessData.SeenName("Steve", 2_000),
                        new SpawnAccessData.SeenName("Alex", 0)),
                data.names(guest));
        data.recordName(guest, "Alex");
        assertEquals(2, data.names(guest).size(), "a repeated name must not be added twice");
        data.recordName(guest, "Steve");
        assertEquals(
                new SpawnAccessData.SeenName("Steve", 2_000),
                data.names(guest).getFirst(),
                "recording a name already seen must keep its time");
    }

    /**
     * A full history of joins drops its oldest join name, so a typed name can still be recorded.
     */
    @Test
    void aFullHistoryOfJoinsMakesRoomForATypedName() {
        SpawnAccessData data = new SpawnAccessData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 10; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        data.recordName(guest, "Typed");
        List<String> kept = data.names(guest).stream().map(SpawnAccessData.SeenName::name).toList();
        assertEquals(10, kept.size(), "the history must stay at ten names, got " + kept);
        assertTrue(kept.contains("Typed"), "the typed name must be recorded, got " + kept);
        assertTrue(!kept.contains("Name0"), "the oldest join name must go, got " + kept);
    }

    /** A full history holding a typed name drops that name before any join name. */
    @Test
    void aFullHistoryDropsItsTypedNameBeforeAJoinName() {
        SpawnAccessData data = new SpawnAccessData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 9; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        data.recordName(guest, "TypedFirst");
        data.recordName(guest, "TypedSecond");
        List<String> kept = data.names(guest).stream().map(SpawnAccessData.SeenName::name).toList();
        assertEquals(10, kept.size(), "the history must stay at ten names, got " + kept);
        assertTrue(
                kept.contains("TypedSecond"), "the new typed name must be recorded, got " + kept);
        assertTrue(!kept.contains("TypedFirst"), "the older typed name must go, got " + kept);
        assertTrue(
                kept.containsAll(
                        java.util.stream.IntStream.range(0, 9)
                                .mapToObj(use -> "Name" + use)
                                .toList()),
                "every join name must survive, got " + kept);
    }

    /**
     * A full history holding two typed names drops the older of them, not the newer: the scan runs
     * from the front, where the oldest typed name sits.
     */
    @Test
    void aFullHistoryDropsTheOldestTypedName() {
        SpawnAccessData data = new SpawnAccessData();
        UUID guest = UUID.randomUUID();
        for (int use = 0; use < 8; use++) {
            data.sawName(guest, "Name" + use, 1_000L + use);
        }
        data.recordName(guest, "TypedFirst");
        data.recordName(guest, "TypedSecond");
        data.recordName(guest, "TypedThird");
        List<String> kept = data.names(guest).stream().map(SpawnAccessData.SeenName::name).toList();
        assertEquals(10, kept.size(), "the history must stay at ten names, got " + kept);
        assertTrue(
                kept.containsAll(List.of("TypedSecond", "TypedThird")),
                "the two newest typed names must survive, got " + kept);
        assertTrue(!kept.contains("TypedFirst"), "the oldest typed name must go, got " + kept);
        assertTrue(
                kept.containsAll(
                        java.util.stream.IntStream.range(0, 8)
                                .mapToObj(use -> "Name" + use)
                                .toList()),
                "every join name must survive, got " + kept);
    }

    /** Recording every join cannot grow the saved data without bound. */
    @Test
    void joinsOutsideTheGuestSetArePruned() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        data.grant(owner, List.of(guest));
        data.recordJoin(guest, "Guest", 1_000);
        List<UUID> players = new ArrayList<>();
        for (int use = 0; use < 1_100; use++) {
            UUID player = UUID.randomUUID();
            players.add(player);
            data.recordJoin(player, "Player" + use, 2_000L + use);
        }
        data.pruneNames(Set.of(guest));
        assertEquals(
                List.of(new SpawnAccessData.SeenName("Guest", 1_000)),
                data.names(guest),
                "a guest must keep their history");
        long kept = players.stream().filter(player -> !data.names(player).isEmpty()).count();
        assertEquals(1_000, kept, "the newest 1000 non-guest histories must stay, got " + kept);
        assertTrue(
                data.names(players.getFirst()).isEmpty(), "the oldest non-guest history must go");
        assertTrue(
                !data.names(players.getLast()).isEmpty(), "the newest non-guest history must stay");
    }

    /** A guest every owner has revoked cannot match a name again, so its history is dropped. */
    @Test
    void aRevokedGuestsHistoryIsDropped() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        assertEquals(1, data.grant(owner, List.of(guest)), "the owner must share");
        data.sawName(guest, "Alex", 1_000);
        data.revoke(owner, List.of(guest));
        assertTrue(data.names(guest).isEmpty(), "a revoked guest must keep no history");
        // A guest another owner still shares with keeps theirs.
        UUID other = UUID.randomUUID();
        data.grant(owner, List.of(guest));
        assertEquals(1, data.grant(other, List.of(guest)), "the other owner must share");
        data.sawName(guest, "Alex", 2_000);
        data.revoke(owner, List.of(guest));
        assertEquals(
                List.of(new SpawnAccessData.SeenName("Alex", 2_000)),
                data.names(guest),
                "a guest another owner still shares with must keep their history");
    }

    @Test
    void nameHistorySurvivesSerialization() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        data.grant(owner, List.of(guest));
        data.sawName(guest, "Alex", 1_000);
        data.sawName(guest, "Steve", 2_000);
        var json = SpawnAccessData.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        SpawnAccessData restored =
                SpawnAccessData.TYPE.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(data.names(guest), restored.names(guest));
        assertTrue(restored.allows(owner, guest));
    }

    /** A server whose data has no name history still loads; the field is optional. */
    @Test
    void sharesSavedBeforeTheHistoryLoadWithoutIt() {
        String owner = "00000000-0000-0000-0000-000000000001";
        String guest = "00000000-0000-0000-0000-000000000002";
        String json = "{\"shares\":{\"" + owner + "\":[\"" + guest + "\"]}}";
        SpawnAccessData data =
                SpawnAccessData.TYPE
                        .codec()
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                        .getOrThrow();
        assertTrue(data.allows(UUID.fromString(owner), UUID.fromString(guest)));
        assertTrue(data.names(UUID.fromString(guest)).isEmpty());
    }

    @Test
    void aGuestIsFoundByANameOnlyItsHistoryHolds() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = UUID.randomUUID();
        UUID renamed = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        data.grant(owner, List.of(renamed, other));
        data.sawName(renamed, "Alex", 1_000);
        data.sawName(renamed, "Steve", 2_000);
        assertEquals(List.of(renamed), data.guestsNamed(owner, "alex"));
        assertEquals(List.of(renamed), data.guestsNamed(owner, "Steve"));
        assertTrue(data.guestsNamed(owner, "SpawnNeverSeen").isEmpty());
    }

}
