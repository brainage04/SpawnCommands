package io.github.brainage04.spawncommands.fabric;

import io.github.brainage04.spawncommands.SpawnCommandRegistration;
import io.github.brainage04.spawncommands.SpawnCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class SpawnCommandsFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		SpawnCommands.initialize();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				SpawnCommandRegistration.register(dispatcher));
	}
}
