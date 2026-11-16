package io.github.brainage04.spawncommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.services.ProfileNotFoundException;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.players.NameAndId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnCommandsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
    void aFoundNameReachesTheServerThread() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        NameLookup lookup = lookup(name -> found(profile));
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("steve", answer::set);
        assertEquals(NameLookup.Status.FOUND, answer.get().status());
        assertEquals(profile, answer.get().profile());
    }

    @Test
    void anUnknownNameIsNotFoundAndNothingIsInvented() {
        NameLookup lookup = lookup(name -> notFound());
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("SpawnNeverSeen", answer::set);
        assertEquals(NameLookup.Status.NOT_FOUND, answer.get().status());
        assertEquals(null, answer.get().profile());
    }

    @Test
    void aRepositoryThatThrowsIsReportedAsFailed() {
        NameLookup lookup =
                lookup(
                        name -> {
                            throw new IllegalStateException("the service is down");
                        });
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        assertEquals(NameLookup.Status.FAILED, answer.get().status());
    }

    @Test
    void theServerStopsAskingMojangAfterItsShareOfTheLimit() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        NameLookup lookup = lookup(name -> found(profile));
        for (int use = 0; use < 33; use++) {
            AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
            lookup.lookup("Steve", answer::set);
            assertEquals(NameLookup.Status.FOUND, answer.get().status(), "lookup " + use);
        }
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        assertEquals(NameLookup.Status.TOO_MANY, answer.get().status());
    }

    /**
     * A lookup waiting in the queue has not spent the server's share yet, so a backlog that built
     * up during an outage cannot drain into one window of requests.
     */
    @Test
    void aQueuedLookupSpendsNothingUntilItRuns() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        List<Runnable> queue = new java.util.ArrayList<>();
        List<NameLookup.Answer> answers = new java.util.ArrayList<>();
        NameLookup lookup =
                new NameLookup(name -> found(profile), queue::add, Runnable::run, () -> true);
        for (int use = 0; use < 40; use++) {
            lookup.lookup("Steve", answers::add);
        }
        assertEquals(40, queue.size(), "every lookup waits its turn");
        assertEquals(0, answers.size(), "a queued lookup has not spent anything yet");
        for (Runnable task : queue) {
            task.run();
        }
        assertEquals(
                33, answers.stream().filter(a -> a.status() == NameLookup.Status.FOUND).count());
        assertEquals(
                7, answers.stream().filter(a -> a.status() == NameLookup.Status.TOO_MANY).count());
    }

    @Test
    void theAnswerArrivesOnlyWhenTheWorkerAndTheServerThreadRun() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        NameLookup lookup =
                new NameLookup(
                        name -> found(profile),
                        queued::set,
                        runnable -> runnable.run(),
                        () -> true);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        assertEquals(null, answer.get(), "nothing may reach the player before the lookup runs");
        queued.get().run();
        assertEquals(NameLookup.Status.FOUND, answer.get().status());
    }

    /**
     * The repository reports an unknown name and a failed request differently, and the two must not
     * be confused: a timeout must not tell the player that Mojang has no such account.
     */
    @Test
    void anUnknownNameAndAFailedRequestAreToldApart() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        assertEquals(
                new NameLookup.Answer(NameLookup.Status.FOUND, profile),
                NameLookup.find(
                        repository(
                                callback ->
                                        callback.onProfileLookupSucceeded("Steve", profile.id())),
                        "steve"));
        assertEquals(
                NameLookup.Status.NOT_FOUND,
                NameLookup.find(
                                repository(
                                        callback ->
                                                callback.onProfileLookupFailed(
                                                        "steve",
                                                        new ProfileNotFoundException(
                                                                "no such name"))),
                                "steve")
                        .status());
        assertEquals(
                NameLookup.Status.FAILED,
                NameLookup.find(
                                repository(
                                        callback ->
                                                callback.onProfileLookupFailed(
                                                        "steve",
                                                        new AuthenticationUnavailableException(
                                                                "the service is down"))),
                                "steve")
                        .status());
        assertEquals(
                NameLookup.Status.FAILED,
                NameLookup.find(repository(callback -> {}), "steve").status(),
                "a repository that never calls back must not read as an unknown name");
    }

    /** A word that cannot be a player name is refused without spending a lookup. */
    @Test
    void aNameThatCannotBelongToAnAccountNeverReachesTheRepository() {
        int[] calls = {0};
        NameLookup lookup =
                lookup(
                        name -> {
                            calls[0]++;
                            return notFound();
                        });
        for (String name : List.of("a".repeat(17), "bad name", "na\u00efve")) {
            AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
            lookup.lookup(name, answer::set);
            assertEquals(NameLookup.Status.NOT_FOUND, answer.get().status(), name);
        }
        assertEquals(0, calls[0], "no invalid name may reach the repository");
    }

    /** An answer that arrives after the server has stopped is dropped, not applied. */
    @Test
    void anAnswerIsDroppedWhenTheServerThreadIsGone() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        NameLookup lookup =
                new NameLookup(name -> found(profile), queued::set, Runnable::run, () -> false);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        queued.get().run();
        assertEquals(null, answer.get(), "nothing may be applied off the server thread");
    }

    /**
     * A found name that arrives too late to act on is reported as stale, so a share cannot land
     * minutes after the command that asked for it.
     */
    @Test
    void aFoundNameThatArrivesTooLateIsNotActedOn() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        long[] now = {0};
        LongSupplier clock = () -> now[0];
        NameLookup lookup =
                new NameLookup(
                        name -> found(profile), queued::set, Runnable::run, () -> true, clock);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        // The clock moves while the lookup waits in the queue, which is the wait the bound is for.
        now[0] = Duration.ofSeconds(31).toNanos();
        queued.get().run();
        assertEquals(NameLookup.Status.STALE, answer.get().status());
        assertEquals(profile, answer.get().profile(), "the player is told which account was found");
    }

    /** An answer inside the bound is acted on, so the bound is not simply "always stale". */
    @Test
    void aFoundNameInsideTheBoundIsActedOn() {
        NameAndId profile = new NameAndId(UUID.randomUUID(), "Steve");
        AtomicReference<Runnable> queued = new AtomicReference<>();
        long[] now = {0};
        LongSupplier clock = () -> now[0];
        NameLookup lookup =
                new NameLookup(
                        name -> found(profile), queued::set, Runnable::run, () -> true, clock);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("Steve", answer::set);
        now[0] = Duration.ofSeconds(29).toNanos();
        queued.get().run();
        assertEquals(NameLookup.Status.FOUND, answer.get().status());
    }

    /** A late answer that only sends a message is still delivered. */
    @Test
    void aLateFailureIsStillReported() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        long[] now = {0};
        LongSupplier clock = () -> now[0];
        NameLookup lookup =
                new NameLookup(name -> notFound(), queued::set, Runnable::run, () -> true, clock);
        AtomicReference<NameLookup.Answer> answer = new AtomicReference<>();
        lookup.lookup("HomesNeverSeen", answer::set);
        now[0] = Duration.ofSeconds(31).toNanos();
        queued.get().run();
        assertEquals(NameLookup.Status.NOT_FOUND, answer.get().status());
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

    @Test
    void aSecondUseInTheSameSecondIsRefused() {
        long[] now = {0};
        ShareRateLimit limit = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        assertEquals(0, limit.refuse(player));
        assertEquals(1_000_000_000L, limit.refuse(player));
        assertEquals(0, limit.refuse(UUID.randomUUID()), "another player has their own allowance");
        now[0] = 1_000_000_000L;
        assertEquals(0, limit.refuse(player));
    }

    @Test
    void theMinuteLimitCountsTwenty() {
        long[] now = {0};
        ShareRateLimit limit = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        for (int use = 0; use < 20; use++) {
            assertEquals(0, limit.refuse(player), "use " + use);
            now[0] += 1_000_000_000L;
        }
        assertEquals(40_000_000_000L, limit.refuse(player), "the first use leaves the minute");
        // A refused use is not counted, so trying again does not push the wait further out.
        assertEquals(40_000_000_000L, limit.refuse(player));
    }

    @Test
    void theHourLimitCountsFourHundred() {
        long[] now = {0};
        ShareRateLimit limit = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        // Four seconds apart, so the minute limit (20) is never reached before the hour's 400.
        for (int use = 0; use < 400; use++) {
            assertEquals(0, limit.refuse(player), "use " + use);
            now[0] += 4_000_000_000L;
        }
        assertEquals(2_000_000_000_000L, limit.refuse(player), "the first use leaves the hour");
    }

    /**
     * The ambiguous list and the share itself are charged against different allowances, so listing
     * cannot spend the click, and listing in a loop is still bounded.
     */
    @Test
    void theListOfAnAmbiguousRevokeHasItsOwnAllowance() {
        long[] now = {0};
        ShareRateLimit share = new ShareRateLimit(() -> now[0]);
        ShareRateLimit listing = new ShareRateLimit(() -> now[0]);
        UUID player = UUID.randomUUID();
        assertEquals(0, listing.refuse(player), "the list is allowed");
        assertEquals(0, share.refuse(player), "the list must not have spent the share");
        assertEquals(1_000_000_000L, listing.refuse(player), "a second list is refused");
        assertEquals(1_000_000_000L, share.refuse(player), "the share has its own second");
    }

    @Test
    void theWaitIsDescribedInWholeSeconds() {
        assertEquals("1 second", ShareRateLimit.describe(1));
        assertEquals("1 second", ShareRateLimit.describe(1_000_000_000L));
        assertEquals("2 seconds", ShareRateLimit.describe(1_500_000_000L));
    }

    private static NameLookup lookup(NameLookup.Repository repository) {
        java.util.concurrent.Executor direct = Runnable::run;
        return new NameLookup(repository, direct, direct, () -> true);
    }

    /** A repository that runs {@code lookup} against the callback it is given. */
    private static GameProfileRepository repository(Consumer<ProfileLookupCallback> lookup) {
        return new GameProfileRepository() {
            @Override
            public void findProfilesByNames(String[] names, ProfileLookupCallback callback) {
                lookup.accept(callback);
            }

            @Override
            public Optional<com.mojang.authlib.services.response.NameAndId> findProfileByName(
                    String name) {
                throw new UnsupportedOperationException("the lookup must not use this");
            }
        };
    }

    private static NameLookup.Answer found(NameAndId profile) {
        return new NameLookup.Answer(NameLookup.Status.FOUND, profile);
    }

    private static NameLookup.Answer notFound() {
        return new NameLookup.Answer(NameLookup.Status.NOT_FOUND, null);
    }
}
