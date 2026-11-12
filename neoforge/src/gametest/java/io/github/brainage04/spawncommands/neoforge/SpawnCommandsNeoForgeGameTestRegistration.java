package io.github.brainage04.spawncommands.neoforge;

import io.github.brainage04.spawncommands.SpawnCommands;
import io.github.brainage04.spawncommands.gametest.SpawnCommandsGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = SpawnCommands.MOD_ID)
public final class SpawnCommandsNeoForgeGameTestRegistration {
    private SpawnCommandsNeoForgeGameTestRegistration() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        register(
                event,
                "all_spawn_commands_are_registered",
                SpawnCommandsGameTests::allSpawnCommandsAreRegistered);
        register(
                event,
                "world_and_personal_spawns_teleport_to_their_configured_positions",
                SpawnCommandsGameTests::worldAndPersonalSpawnsTeleportToTheirConfiguredPositions);
        register(
                event,
                "bed_spawns_resolve_to_the_vanilla_stand_up_position",
                SpawnCommandsGameTests::bedSpawnsResolveToTheVanillaStandUpPosition);
        register(
                event,
                "spawn_sharing_is_per_owner_and_enables_guest_teleport",
                SpawnCommandsGameTests::spawnSharingIsPerOwnerAndEnablesGuestTeleport);
        register(
                event,
                "missing_personal_spawns_fail_without_moving_the_player",
                SpawnCommandsGameTests::missingPersonalSpawnsFailWithoutMovingThePlayer);
        register(
                event,
                "shared_spawn_commands_enforce_grant_and_revoke",
                SpawnCommandsGameTests::sharedSpawnCommandsEnforceGrantAndRevoke);
        register(
                event,
                "anchor_spawns_cross_dimensions_without_using_a_charge",
                SpawnCommandsGameTests::anchorSpawnsCrossDimensionsWithoutUsingACharge);
        register(
                event,
                "world_spawn_uses_its_configured_dimension_and_rotation",
                SpawnCommandsGameTests::worldSpawnUsesItsConfiguredDimensionAndRotation);
    }

    private static void register(
            RegisterEvent event,
            String id,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), SpawnCommands.of(id), () -> test);
    }
}
