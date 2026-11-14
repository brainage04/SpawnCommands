package io.github.brainage04.spawncommands.neoforge;

import io.github.brainage04.spawncommands.SpawnCommands;
import io.github.brainage04.spawncommands.SpawnTicketGameTests;
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
        register(
                event,
                "messages_use_mod_feedback_and_help_is_registered",
                SpawnCommandsGameTests::messagesUseModFeedbackAndHelpIsRegistered);
        register(
                event,
                "world_spawn_lands_on_top_of_its_block",
                SpawnCommandsGameTests::worldSpawnLandsOnTopOfItsBlock);
        register(
                event,
                "obstructed_world_spawn_fails_without_moving_the_player",
                SpawnCommandsGameTests::obstructedWorldSpawnFailsWithoutMovingThePlayer);
        register(
                event,
                "world_spawn_over_a_fence_or_wall_lands_on_its_top",
                SpawnCommandsGameTests::worldSpawnOverAFenceOrWallLandsOnItsTop);
        register(
                event,
                "world_spawn_in_powder_snow_is_judged_where_it_is",
                SpawnCommandsGameTests::worldSpawnInPowderSnowIsJudgedWhereItIs);
        register(
                event,
                "player_only_commands_answer_non_players_through_mod_feedback",
                SpawnCommandsGameTests::playerOnlyCommandsAnswerNonPlayersThroughModFeedback);
        register(
                event,
                "commands_click_for_the_player_through_mod_feedback",
                SpawnCommandsGameTests::commandsClickForThePlayerThroughModFeedback);
        register(
                event,
                "spawns_in_lava_or_over_the_void_fail_without_moving_the_player",
                SpawnCommandsGameTests::spawnsInLavaOrOverTheVoidFailWithoutMovingThePlayer);
        register(
                event,
                "a_spawn_with_lava_at_head_height_fails",
                SpawnCommandsGameTests::aSpawnWithLavaAtHeadHeightFails);
        register(
                event,
                "a_world_spawn_in_lava_in_an_unloaded_chunk_fails_once_the_chunk_loads",
                SpawnCommandsGameTests::aWorldSpawnInLavaInAnUnloadedChunkFailsOnceTheChunkLoads);
        register(
                event,
                "world_spawn_in_an_unloaded_chunk_teleports_after_loading_it",
                SpawnCommandsGameTests::worldSpawnInAnUnloadedChunkTeleportsAfterLoadingIt);
        register(
                event,
                "a_world_spawn_inside_the_ground_of_an_unloaded_chunk_still_fails",
                SpawnCommandsGameTests::aWorldSpawnInsideTheGroundOfAnUnloadedChunkStillFails);
        // vanillaTpLeavesTheDestinationChunkUnloaded runs on Fabric only: on NeoForge the
        // destination chunk was loaded right after /tp in a GameTest run on 14 Nov 2026, and the
        // test describes vanilla's /tp, not this mod.
        register(
                event,
                "a_second_spawn_request_while_the_chunk_loads_waits_for_the_load_too",
                SpawnCommandsGameTests::aSecondSpawnRequestWhileTheChunkLoadsWaitsForTheLoadToo);
        register(
                event,
                "a_finished_load_leaves_the_ticket_another_load_still_needs",
                SpawnTicketGameTests::aFinishedLoadLeavesTheTicketAnotherLoadStillNeeds);
        register(
                event,
                "a_release_without_a_load_leaves_later_tickets_alone",
                SpawnTicketGameTests::aReleaseWithoutALoadLeavesLaterTicketsAlone);
        register(
                event,
                "a_ticket_another_mod_took_away_is_taken_again_before_arriving",
                SpawnTicketGameTests::aTicketAnotherModTookAwayIsTakenAgainBeforeArriving);
        register(
                event,
                "a_failed_arrival_is_reported_and_gives_the_ticket_back",
                SpawnTicketGameTests::aFailedArrivalIsReportedAndGivesTheTicketBack);
        register(
                event,
                "a_player_who_leaves_during_the_load_gives_the_ticket_back",
                SpawnTicketGameTests::aPlayerWhoLeavesDuringTheLoadGivesTheTicketBack);
        register(
                event,
                "a_spot_over_two_chunks_loads_both_again_when_another_mod_takes_one",
                SpawnTicketGameTests::aSpotOverTwoChunksLoadsBothAgainWhenAnotherModTakesOne);
        register(
                event,
                "owners_revoke_guests_who_are_offline",
                SpawnCommandsGameTests::ownersRevokeGuestsWhoAreOffline);
    }

    private static void register(
            RegisterEvent event,
            String id,
            java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> test) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), SpawnCommands.of(id), () -> test);
    }
}
