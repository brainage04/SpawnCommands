package io.github.brainage04.spawncommands;

import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** The saved respawn point of a player who is not online, read from their player data. */
final class OfflineSpawn {
    private OfflineSpawn() {}

    /**
     * The respawn point {@code owner} last saved, exactly as the player data holds it. The server
     * reads that data the same way when it loads the player ({@code
     * PrepareSpawnTask.java:47-50}): {@code PlayerList.loadPlayerData} (which renames the
     * singleplayer owner's file, {@code PlayerList.java:280-289}) and the game's own
     * {@code RespawnConfig} codec over the tag, the codec {@code ServerPlayer} writes it with
     * ({@code ServerPlayer.java:429}, read back at {@code 408}).
     */
    static Optional<ServerPlayer.RespawnConfig> respawnConfig(
            MinecraftServer server, NameAndId owner) {
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(SpawnCommands.LOGGER)) {
            return server.getPlayerList()
                    .loadPlayerData(owner)
                    .flatMap(
                            tag ->
                                    TagValueInput.create(
                                                    reporter, server.registryAccess(), tag)
                                            .read("respawn", ServerPlayer.RespawnConfig.CODEC));
        }
    }
}
