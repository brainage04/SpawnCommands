package io.github.brainage04.spawncommands;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

@SuppressWarnings({"UnstableApiUsage", "removal"})
public final class SpawnCommandsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "SpawnCommands visual GameTest",
                server -> {
                    try {
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player =
                                            minecraftServer.getPlayerList().getPlayers().getFirst();
                                    BlockPos personalSpawn = player.blockPosition().offset(4, 0, 0);
                                    player.setRespawnPosition(
                                            new ServerPlayer.RespawnConfig(
                                                    LevelData.RespawnData.of(
                                                            player.level().dimension(),
                                                            personalSpawn,
                                                            90.0F,
                                                            0.0F),
                                                    true),
                                            false);
                                });
                        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                        assertCommandTree(context);
                        context.runOnClient(
                                client -> client.setScreenAndShow(new ChatScreen("", false)));
                        ClientGameTestRecorder.startRecording(context);

                        runCommand(context, "spawn");
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player =
                                            minecraftServer.getPlayerList().getPlayers().getFirst();
                                    BlockPos expected =
                                            minecraftServer
                                                    .overworld()
                                                    .getLevelData()
                                                    .getRespawnData()
                                                    .pos();
                                    if (!player.blockPosition().equals(expected))
                                        throw new AssertionError(
                                                "/spawn did not reach the world spawn.");
                                });
                        ClientGameTestRecorder.showStep(
                                context,
                                "spawncommands.world",
                                "World spawn teleport",
                                "/spawn resolves the configured world spawn and reports success in chat");
                        context.waitTicks(50);

                        runCommand(context, "myspawn");
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player =
                                            minecraftServer.getPlayerList().getPlayers().getFirst();
                                    if (!player.blockPosition()
                                            .equals(
                                                    player.getRespawnConfig()
                                                            .respawnData()
                                                            .pos())) {
                                        throw new AssertionError(
                                                "/myspawn did not reach the personal spawn.");
                                    }
                                });
                        ClientGameTestRecorder.showStep(
                                context,
                                "spawncommands.personal",
                                "Vanilla personal spawn",
                                "/myspawn uses the player's vanilla respawn dimension, position, yaw, pitch, and safety resolution");
                        context.waitTicks(50);

                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player =
                                            minecraftServer.getPlayerList().getPlayers().getFirst();
                                    player.teleportTo(
                                            player.getX() + 10.0D, player.getY(), player.getZ());
                                });
                        runCommand(
                                context,
                                "spawnof "
                                        + context.computeOnClient(
                                                client -> client.player.getName().getString()));
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player =
                                            minecraftServer.getPlayerList().getPlayers().getFirst();
                                    if (!player.blockPosition()
                                            .equals(
                                                    player.getRespawnConfig()
                                                            .respawnData()
                                                            .pos())) {
                                        throw new AssertionError(
                                                "/spawnof must allow access to your own spawn.");
                                    }
                                });
                        ClientGameTestRecorder.showStep(
                                context,
                                "spawncommands.sharing",
                                "Your own spawn",
                                "/spawnof <your name> lets you visit your own spawn without sharing it");
                        context.waitTicks(50);
                    } finally {
                        context.runOnClient(client -> client.setScreenAndShow(null));
                    }
                });
    }

    private static void assertCommandTree(ClientGameTestContext context) {
        context.computeOnClient(
                client -> {
                    if (client.getConnection() == null)
                        throw new AssertionError("Expected a connected client.");
                    var root = client.getConnection().getCommands().getRoot();
                    for (String command :
                            java.util.List.of("spawn", "myspawn", "spawnof", "spawnshare")) {
                        if (root.getChild(command) == null)
                            throw new AssertionError(
                                    "Expected /" + command + " in the client command tree.");
                    }
                    return null;
                });
    }

    private static void runCommand(ClientGameTestContext context, String command) {
        context.runOnClient(
                client -> {
                    if (client.getConnection() == null)
                        throw new AssertionError(
                                "Expected a connected client to run /" + command + '.');
                    client.getConnection().sendCommand(command);
                });
    }
}
