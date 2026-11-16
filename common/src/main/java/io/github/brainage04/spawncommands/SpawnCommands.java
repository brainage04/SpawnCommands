package io.github.brainage04.spawncommands;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpawnCommands {
    public static final String MOD_ID = "spawncommands";
    public static final String MOD_NAME = "SpawnCommands";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final ModFeedback FEEDBACK = ModFeedback.create(MOD_NAME);

    private SpawnCommands() {}

    public static void initialize() {
        ServerModHelpRegistry.register(
                ServerModHelpEntry.playerOnly(
                        MOD_ID,
                        MOD_NAME,
                        "Adds world, personal, and shared player spawn commands.",
                        "/spawncommands help"));
        LOGGER.info("{} initialized.", MOD_NAME);
    }

    /**
     * Records the name a player joined under, so "last seen" in the share lists is their real last
     * login rather than the last time an owner shared or revoked under that name. Every join is
     * recorded, not only a guest's: a player granted access later must still show the login they
     * made before it. {@link SpawnAccessData#pruneNames} keeps the map bounded.
     */
    public static void playerJoined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        SpawnAccessData access = SpawnAccessData.get(server);
        access.recordJoin(player.getUUID(), player.getScoreboardName(), System.currentTimeMillis());
        access.pruneNames(guestsAndOnline(server, access));
    }

    /**
     * The accounts whose history must survive a prune: the guests of any owner, and the players
     * online now. A cached account that is neither keeps its history only while it is among the
     * newest 1000 entries.
     */
    static Set<UUID> guestsAndOnline(MinecraftServer server, SpawnAccessData access) {
        Set<UUID> keep = new HashSet<>(access.allGuests());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            keep.add(online.getUUID());
        }
        return keep;
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
