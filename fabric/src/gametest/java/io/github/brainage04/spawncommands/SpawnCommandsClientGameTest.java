package io.github.brainage04.spawncommands;

import io.github.brainage04.minecraftmodconventions.ClientGameTestCommands;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;

@SuppressWarnings("UnstableApiUsage")
public final class SpawnCommandsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ClientGameTestServers.withDedicatedServer(
                context,
                ClientGameTestServers.flatServerProperties(),
                "SpawnCommands visual GameTest",
                server -> {
                    try {
                        server.runOnServer(SpawnCommandsClientGameTest::setPersonalSpawn);
                        ClientGameTestCommands.assertCommandTree(
                                context, "spawn", "myspawn", "spawnof", "spawnshare");
                        SpawnCommandsShowcase.openChat(context);

                        ClientGameTestCommands.runCommand(context, "spawn");
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    BlockPos expected =
                                            minecraftServer
                                                    .overworld()
                                                    .getLevelData()
                                                    .getRespawnData()
                                                    .pos();
                                    if (!player(minecraftServer).blockPosition().equals(expected))
                                        throw new AssertionError(
                                                "/spawn did not reach the world spawn.");
                                });
                        SpawnCommandsShowcase.worldSpawn(context);

                        ClientGameTestCommands.runCommand(context, "myspawn");
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    if (!isOnPersonalSpawn(player(minecraftServer))) {
                                        throw new AssertionError(
                                                "/myspawn did not reach the personal spawn.");
                                    }
                                });
                        SpawnCommandsShowcase.personalSpawn(context);

                        server.runOnServer(
                                minecraftServer -> {
                                    ServerPlayer player = player(minecraftServer);
                                    player.teleportTo(
                                            player.getX() + 10.0D, player.getY(), player.getZ());
                                });
                        ClientGameTestCommands.runCommand(
                                context,
                                "spawnof "
                                        + context.computeOnClient(
                                                client -> client.player.getName().getString()));
                        context.waitTicks(20);
                        server.runOnServer(
                                minecraftServer -> {
                                    if (!isOnPersonalSpawn(player(minecraftServer))) {
                                        throw new AssertionError(
                                                "/spawnof must allow access to your own spawn.");
                                    }
                                });
                        SpawnCommandsShowcase.ownSpawn(context);
                    } finally {
                        SpawnCommandsShowcase.closeChat(context);
                    }
                });
    }

    private static void setPersonalSpawn(MinecraftServer minecraftServer) {
        ServerPlayer player = player(minecraftServer);
        BlockPos personalSpawn = player.blockPosition().offset(4, 0, 0);
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(
                                player.level().dimension(), personalSpawn, 90.0F, 0.0F),
                        true),
                false);
    }

    private static boolean isOnPersonalSpawn(ServerPlayer player) {
        return player.blockPosition().equals(player.getRespawnConfig().respawnData().pos());
    }

    private static ServerPlayer player(MinecraftServer minecraftServer) {
        return minecraftServer.getPlayerList().getPlayers().getFirst();
    }
}
