package io.github.brainage04.spawncommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
     * a server started with a non-default {@code --universe} has no cache where
     * {@code getServerDirectory()} points, so the parent of the world directory answers.
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
                ServerNameCache.cacheFile(root.resolve("elsewhere"), Optional.of(world.resolve("."))));
    }

    @Test
    void answersNoCacheWhenNeitherPlaceHasOne(@TempDir Path root) throws IOException {
        Path world = Files.createDirectory(root.resolve("world"));

        assertEquals(Optional.empty(), ServerNameCache.cacheFile(root, Optional.of(world)));
    }
}
