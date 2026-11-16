package io.github.brainage04.spawncommands;

import io.github.brainage04.brainagelib.feedback.ModFeedback;
import io.github.brainage04.brainagelib.help.ServerModHelpEntry;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpawnCommands {
    public static final String MOD_ID = "spawncommands";
    public static final String MOD_NAME = "SpawnCommands";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final ModFeedback FEEDBACK = ModFeedback.create(MOD_NAME);
    public static GameRule<Boolean> RESET_FALL;

    private SpawnCommands() {}

    public static void initialize(GameRule<Boolean> resetFall) {
        RESET_FALL = resetFall;
        ServerModHelpRegistry.register(
                new ServerModHelpEntry(
                        MOD_ID,
                        MOD_NAME,
                        "Adds world, personal, and shared player spawn commands.",
                        "/spawncommands help",
                        "/gamerule spawncommands:reset_fall"));
        LOGGER.info("{} initialized.", MOD_NAME);
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
