package io.github.brainage04.spawncommands.neoforge;

import io.github.brainage04.spawncommands.SpawnCommands;
import io.github.brainage04.spawncommands.SpawnCommandsNeoForgeGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = SpawnCommands.MOD_ID)
public final class SpawnCommandsNeoForgeGameTestRegistration {
	private SpawnCommandsNeoForgeGameTestRegistration() {
	}

	@SubscribeEvent
	public static void registerTestFunctions(RegisterEvent event) {
		SpawnCommandsNeoForgeGameTests tests = new SpawnCommandsNeoForgeGameTests();
		register(event, "all_spawn_commands_are_registered", tests::allSpawnCommandsAreRegistered);
		register(event, "world_and_personal_spawns_teleport_to_their_configured_positions", tests::worldAndPersonalSpawnsTeleportToTheirConfiguredPositions);
		register(event, "spawn_sharing_is_per_owner_and_enables_guest_teleport", tests::spawnSharingIsPerOwnerAndEnablesGuestTeleport);
	}

	private static void register(
			RegisterEvent event,
			String id,
			java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test
	) {
		event.register(BuiltInRegistries.TEST_FUNCTION.key(), SpawnCommands.of(id), () -> test);
	}
}
