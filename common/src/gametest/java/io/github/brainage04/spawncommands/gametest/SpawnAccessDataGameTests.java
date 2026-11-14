package io.github.brainage04.spawncommands.gametest;

import io.github.brainage04.spawncommands.SpawnAccessData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.storage.SavedDataStorage;

public final class SpawnAccessDataGameTests {
    private SpawnAccessDataGameTests() {}

    /**
     * A spawn access file is written back byte for byte, and one from before data versions keeps
     * its data: it holds only mod data, which no vanilla fix may rewrite.
     */
    public static void aSpawnAccessFileKeepsItsDataThroughALoadAndASave(GameTestHelper helper) {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID guest = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        Path folder = createDataFolder();
        try {
            Path file = writeAccessFile(folder, owner, guest);
            byte[] before = Files.readAllBytes(file);
            try (SavedDataStorage storage = dataStorage(helper, folder)) {
                SpawnAccessData data = storage.computeIfAbsent(SpawnAccessData.TYPE);
                helper.assertTrue(
                        data.allows(owner, guest),
                        "The saved share must load, read " + readTag(file));
                data.setDirty();
            }
            helper.assertTrue(
                    Arrays.equals(before, Files.readAllBytes(file)),
                    "Loading and saving an existing spawn access file must write the same bytes,"
                            + " read "
                            + readTag(file));

            // NbtUtils.getDataVersion reads 1343 for a file without one
            // (SavedDataStorage.java:123).
            CompoundTag written = readTag(file);
            written.remove("DataVersion");
            NbtIo.writeCompressed(written, file);
            try (SavedDataStorage storage = dataStorage(helper, folder)) {
                storage.computeIfAbsent(SpawnAccessData.TYPE).setDirty();
            }
            CompoundTag rewritten = readTag(file);
            helper.assertTrue(
                    written.get("data").equals(rewritten.get("data")),
                    "Loading and saving a spawn access file without a data version must keep its"
                            + " data, read "
                            + rewritten);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } finally {
            deleteFolder(folder);
        }
        helper.succeed();
    }

    /** Writes the file the way SavedDataStorage.tryWrite does (SavedDataStorage.java:198-215). */
    private static Path writeAccessFile(Path folder, UUID owner, UUID guest) throws IOException {
        ListTag guests = new ListTag();
        guests.add(StringTag.valueOf(guest.toString()));
        CompoundTag shares = new CompoundTag();
        shares.put(owner.toString(), guests);
        CompoundTag data = new CompoundTag();
        data.put("shares", shares);
        CompoundTag file = new CompoundTag();
        file.put("data", data);
        NbtUtils.addCurrentDataVersion(file);
        Path path = SpawnAccessData.TYPE.id().withSuffix(".dat").resolveAgainst(folder);
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(file, path);
        return path;
    }

    private static CompoundTag readTag(Path file) {
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // The server makes its own storage with this level-less constructor (MinecraftServer.java:325).
    // It is the only one Fabric and vanilla have, and the common source set compiles against both.
    // deprecated-api: only NeoForge deprecates it, for one taking a level (SavedDataStorage patch)
    @SuppressWarnings("deprecation")
    private static SavedDataStorage dataStorage(GameTestHelper helper, Path folder) {
        var server = helper.getLevel().getServer();
        return new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess());
    }

    private static Path createDataFolder() {
        try {
            return Files.createTempDirectory("spawncommands-gametest");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void deleteFolder(Path folder) {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
