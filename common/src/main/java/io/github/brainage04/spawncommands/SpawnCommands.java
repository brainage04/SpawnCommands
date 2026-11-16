package io.github.brainage04.spawncommands;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import net.minecraft.resources.Identifier;
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
     * login rather than the last time an owner shared or revoked under that name. Only a guest is
     * recorded: the lists can only show guests, and recording every player who ever joined would
     * grow the saved history past the guest set it is bounded by.
     */
    public static void playerJoined(ServerPlayer player) {
        SpawnAccessData access = SpawnAccessData.get(player.level().getServer());
        if (!access.anyOwnerAllows(player.getUUID())) return;
        access.sawName(player.getUUID(), player.getScoreboardName(), System.currentTimeMillis());
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
