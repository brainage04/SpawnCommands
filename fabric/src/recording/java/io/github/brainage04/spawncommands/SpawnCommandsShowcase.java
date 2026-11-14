package io.github.brainage04.spawncommands;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;

/** The film around SpawnCommands' client GameTest: the open chat, the captions and their shots. */
@SuppressWarnings("UnstableApiUsage")
public final class SpawnCommandsShowcase {
    private static final int SHOT_TICKS = 50;

    private SpawnCommandsShowcase() {}

    /** Keeps the chat open, so the film shows each command's reply. */
    public static void openChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreenAndShow(new ChatScreen("", false)));
    }

    public static void closeChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.setScreenAndShow(null));
    }

    public static void worldSpawn(ClientGameTestContext context) {
        ClientGameTestRecorder.showStepAndHold(
                context,
                "spawncommands.world",
                "World spawn teleport",
                "/spawn resolves the configured world spawn and reports success in chat",
                SHOT_TICKS);
    }

    public static void personalSpawn(ClientGameTestContext context) {
        ClientGameTestRecorder.showStepAndHold(
                context,
                "spawncommands.personal",
                "Vanilla personal spawn",
                "/myspawn uses the player's vanilla respawn dimension, position, yaw, pitch, and"
                        + " safety resolution",
                SHOT_TICKS);
    }

    public static void ownSpawn(ClientGameTestContext context) {
        ClientGameTestRecorder.showStepAndHold(
                context,
                "spawncommands.sharing",
                "Your own spawn",
                "/spawnof <your name> lets you visit your own spawn without sharing it",
                SHOT_TICKS);
    }
}
