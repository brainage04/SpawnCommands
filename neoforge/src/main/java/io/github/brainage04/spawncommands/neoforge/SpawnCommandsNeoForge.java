package io.github.brainage04.spawncommands.neoforge;

import io.github.brainage04.spawncommands.SpawnCommandRegistration;
import io.github.brainage04.spawncommands.SpawnCommands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(SpawnCommands.MOD_ID)
public final class SpawnCommandsNeoForge {
    public SpawnCommandsNeoForge(IEventBus modEventBus) {
        SpawnCommands.initialize();
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) ->
                        SpawnCommandRegistration.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(SpawnCommandsNeoForge::playerLoggedIn);
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpawnCommands.playerJoined(player);
    }
}
