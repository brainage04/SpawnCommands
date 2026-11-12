package io.github.brainage04.spawncommands.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainagelib.gametest.CapturedCommandOutput;
import io.github.brainage04.brainagelib.gametest.GameTestPlayer;
import io.github.brainage04.brainagelib.help.ServerModHelpRegistry;
import io.github.brainage04.spawncommands.SpawnAccessData;
import io.github.brainage04.spawncommands.SpawnCommands;
import io.github.brainage04.spawncommands.SpawnTeleportService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

public final class SpawnCommandsGameTests {
    /// Large enough that vanilla's world-spawn spread would move the destination on almost every
    /// teleport.
    private static final int RESPAWN_RADIUS = 10;

    private static final int TELEPORTS = 5;

    private SpawnCommandsGameTests() {}

    public static void allSpawnCommandsAreRegistered(GameTestHelper helper) {
        var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String command : List.of("spawn", "myspawn", "spawnof", "spawnshare")) {
            helper.assertTrue(
                    root.getChild(command) != null, "/" + command + " must be registered");
        }
        helper.succeed();
    }

    @SuppressWarnings("removal")
    public static void worldAndPersonalSpawnsTeleportToTheirConfiguredPositions(
            GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        LevelData.RespawnData worldSpawn = level.getLevelData().getRespawnData();
        player.teleportTo(20.0D, worldSpawn.pos().getY(), 20.0D);
        helper.assertTrue(
                SpawnTeleportService.teleportToWorldSpawn(player) == 1,
                "/spawn must teleport successfully");
        helper.assertTrue(
                player.blockPosition().equals(worldSpawn.pos()),
                "/spawn must use the configured world spawn position");

        BlockPos personalSpawn = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(level.dimension(), personalSpawn, 45.0F, 0.0F),
                        true),
                false);
        Vec3 expected =
                new Vec3(
                        personalSpawn.getX() + 0.5D,
                        personalSpawn.getY() + 0.1D,
                        personalSpawn.getZ() + 0.5D);
        withRespawnRadius(
                helper, () -> assertAlwaysLandsOn(helper, player, player, expected, "/myspawn"));
        helper.succeed();
    }

    public static void bedSpawnsResolveToTheVanillaStandUpPosition(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos foot = new BlockPos(2, 1, 1);
        BlockPos head = new BlockPos(2, 1, 2);
        helper.setBlock(
                foot,
                Blocks.BED
                        .red()
                        .defaultBlockState()
                        .setValue(BedBlock.FACING, Direction.SOUTH)
                        .setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(
                head,
                Blocks.BED
                        .red()
                        .defaultBlockState()
                        .setValue(BedBlock.FACING, Direction.SOUTH)
                        .setValue(BedBlock.PART, BedPart.HEAD));
        BlockPos bed = helper.absolutePos(head);
        float yaw = 30.0F;
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(level.dimension(), bed, yaw, 0.0F), false),
                false);
        Vec3 expected =
                BedBlock.findStandUpPosition(EntityTypes.PLAYER, level, bed, Direction.SOUTH, yaw)
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "The test bed must have a stand-up position"));
        withRespawnRadius(
                helper,
                () -> assertAlwaysLandsOn(helper, player, player, expected, "/myspawn to a bed"));
        helper.succeed();
    }

    @SuppressWarnings("removal")
    public static void spawnSharingIsPerOwnerAndEnablesGuestTeleport(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer guest = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(
                !owner.getUUID().equals(guest.getUUID()),
                "Mock players must have distinct identities");
        var level = helper.getLevel();
        BlockPos ownerSpawn = helper.absolutePos(new BlockPos(3, 2, 3));
        owner.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(level.dimension(), ownerSpawn, 90.0F, 0.0F), true),
                false);

        SpawnAccessData access = SpawnAccessData.get(level.getServer());
        access.revoke(owner.getUUID(), List.of(guest.getUUID()));
        helper.assertTrue(
                !access.allows(owner.getUUID(), guest.getUUID()),
                "Guests must not inherit global spawn access");
        access.grant(owner.getUUID(), List.of(guest.getUUID()));
        helper.assertTrue(
                access.allows(owner.getUUID(), guest.getUUID()),
                "/spawnshare must grant only the owner's access entry");
        Vec3 expected =
                new Vec3(
                        ownerSpawn.getX() + 0.5D,
                        ownerSpawn.getY() + 0.1D,
                        ownerSpawn.getZ() + 0.5D);
        withRespawnRadius(
                helper, () -> assertAlwaysLandsOn(helper, guest, owner, expected, "/spawnof"));
        helper.succeed();
    }

    public static void worldSpawnUsesItsConfiguredDimensionAndRotation(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "The Nether must be available");
        LevelData.RespawnData previous = overworld.getLevelData().getRespawnData();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos destination = new BlockPos(origin.getX(), 80, origin.getZ());
        try {
            nether.setRespawnData(
                    LevelData.RespawnData.of(nether.dimension(), destination, 123.0F, 14.0F));
            helper.assertTrue(
                    execute(player, "spawn") == 1,
                    "/spawn must reach a world spawn outside the Overworld");
            helper.assertTrue(player.level() == nether, "/spawn must use the configured dimension");
            helper.assertTrue(
                    player.position().equals(Vec3.atBottomCenterOf(destination)),
                    "/spawn must use the configured position");
            helper.assertTrue(
                    player.getYRot() == 123.0F && player.getXRot() == 14.0F,
                    "/spawn must preserve configured rotation");
        } finally {
            overworld.setRespawnData(previous);
        }
        helper.succeed();
    }

    public static void missingPersonalSpawnsFailWithoutMovingThePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 before = player.position();
        helper.assertTrue(
                SpawnTeleportService.teleportToPersonalSpawn(player, player) == 0,
                "A player without a personal spawn must not be teleported");
        helper.assertTrue(
                player.position().equals(before), "A missing spawn must not move the player");

        BlockPos missingBed = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(
                                helper.getLevel().dimension(), missingBed, 0.0F, 0.0F),
                        false),
                false);
        helper.assertTrue(
                SpawnTeleportService.teleportToPersonalSpawn(player, player) == 0,
                "A removed bed must not fall back to the world spawn");
        helper.assertTrue(
                player.position().equals(before), "An invalid bed must not move the player");

        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(
                new BlockPos(2, 2, 1),
                Blocks.BED
                        .red()
                        .defaultBlockState()
                        .setValue(BedBlock.FACING, Direction.SOUTH)
                        .setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(
                new BlockPos(2, 2, 2),
                Blocks.BED
                        .red()
                        .defaultBlockState()
                        .setValue(BedBlock.FACING, Direction.SOUTH)
                        .setValue(BedBlock.PART, BedPart.HEAD));
        helper.assertTrue(
                SpawnTeleportService.teleportToPersonalSpawn(player, player) == 0,
                "An obstructed bed must not fall back to the world spawn");
        helper.assertTrue(
                player.position().equals(before), "An obstructed bed must not move the player");
        helper.succeed();
    }

    public static void sharedSpawnCommandsEnforceGrantAndRevoke(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer guest = helper.makeMockServerPlayerInLevel();
        var server = helper.getLevel().getServer();
        String ownerTag = "spawncommands-owner-" + owner.getUUID();
        String guestTag = "spawncommands-guest-" + guest.getUUID();
        owner.addTag(ownerTag);
        guest.addTag(guestTag);
        var ownerSource =
                owner.createCommandSourceStack()
                        .withPermission(server.createCommandSourceStack().permissions());
        var guestSource =
                guest.createCommandSourceStack()
                        .withPermission(server.createCommandSourceStack().permissions());
        SpawnAccessData access = SpawnAccessData.get(server);
        BlockPos destination = helper.absolutePos(new BlockPos(3, 2, 3));
        owner.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(
                                helper.getLevel().dimension(), destination, 75.0F, 12.0F),
                        true),
                false);
        String target = "@a[tag=" + ownerTag + ",limit=1]";
        String guests = "@a[tag=" + guestTag + "]";
        try {
            Vec3 initial = guest.position();
            helper.assertTrue(
                    execute(guestSource, "spawnof " + target) == 0, "Unshared /spawnof must fail");
            helper.assertTrue(
                    guest.position().equals(initial), "Denied access must not move the guest");
            helper.assertTrue(
                    execute(ownerSource, "spawnshare " + guests) == 1,
                    "/spawnshare must grant access");
            helper.assertTrue(
                    execute(ownerSource, "spawnshare " + guests) == 0,
                    "Repeated grants must not add access twice");
            helper.assertTrue(
                    execute(guestSource, "spawnof " + target) == 1, "Shared /spawnof must succeed");
            helper.assertTrue(
                    guest.blockPosition().equals(destination),
                    "The guest must land at the owner's spawn");
            helper.assertTrue(
                    guest.getYRot() == 75.0F && guest.getXRot() == 12.0F,
                    "Spawn rotation must be preserved");
            helper.assertTrue(
                    execute(ownerSource, "spawnshare revoke " + guests) == 1,
                    "Revocation must remove access");
            Vec3 afterTeleport = guest.position();
            helper.assertTrue(
                    execute(guestSource, "spawnof " + target) == 0, "Revoked /spawnof must fail");
            helper.assertTrue(
                    guest.position().equals(afterTeleport),
                    "Revoked access must not move the guest");
            helper.assertTrue(execute(owner, "myspawn") == 1, "/myspawn must execute for a player");
            helper.assertTrue(execute(guest, "spawn") == 1, "/spawn must execute for a player");
            try {
                server.getCommands()
                        .getDispatcher()
                        .execute("myspawn", server.createCommandSourceStack());
                throw new AssertionError("Console /myspawn must reject a non-player source");
            } catch (CommandSyntaxException expected) {
            }
        } finally {
            access.revoke(owner.getUUID(), List.of(guest.getUUID()));
            owner.removeTag(ownerTag);
            guest.removeTag(guestTag);
        }
        helper.succeed();
    }

    public static void anchorSpawnsCrossDimensionsWithoutUsingACharge(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer guest = helper.makeMockServerPlayerInLevel();
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "The Nether must be available");
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos anchor = new BlockPos(origin.getX(), 80, origin.getZ());
        Map<BlockPos, BlockState> previous = new HashMap<>();
        for (BlockPos pos :
                BlockPos.betweenClosed(anchor.offset(-2, -1, -2), anchor.offset(2, 2, 2))) {
            BlockPos saved = pos.immutable();
            previous.put(saved, nether.getBlockState(saved));
        }
        try {
            previous.keySet()
                    .forEach(
                            pos ->
                                    nether.setBlockAndUpdate(
                                            pos,
                                            pos.getY() == anchor.getY() - 1
                                                    ? Blocks.STONE.defaultBlockState()
                                                    : Blocks.AIR.defaultBlockState()));
            nether.setBlockAndUpdate(
                    anchor,
                    Blocks.RESPAWN_ANCHOR
                            .defaultBlockState()
                            .setValue(RespawnAnchorBlock.CHARGE, 3));
            owner.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(nether.dimension(), anchor, 35.0F, 0.0F),
                            false),
                    false);
            Vec3 expected =
                    RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, nether, anchor)
                            .orElseThrow(
                                    () ->
                                            new AssertionError(
                                                    "The anchor must have a safe stand-up position"));
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(guest, owner) == 1,
                    "Shared anchor teleport must succeed");
            helper.assertTrue(
                    guest.level() == nether && guest.position().equals(expected),
                    "The guest must reach the Nether anchor");
            helper.assertTrue(
                    nether.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE) == 3,
                    "Teleport must not consume an anchor charge");
            nether.setBlockAndUpdate(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState());
            Vec3 beforeFailure = guest.position();
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(guest, owner) == 0,
                    "An uncharged anchor must fail");
            helper.assertTrue(
                    guest.position().equals(beforeFailure),
                    "An uncharged anchor must not move the guest");
        } finally {
            previous.forEach(nether::setBlockAndUpdate);
        }
        helper.succeed();
    }

    public static void messagesUseModFeedbackAndHelpIsRegistered(GameTestHelper helper) {
        try (var owner = GameTestPlayer.join(helper, "SpawnOwner").gameMode(GameType.SPECTATOR);
                var guest = GameTestPlayer.join(helper, "SpawnGuest").gameMode(GameType.SPECTATOR)) {
            var ownerSource = withServerPermissions(owner.player());
            String guestSelector = "@a[name=" + guest.player().getScoreboardName() + "]";
            owner.clearMessages();
            helper.assertTrue(
                    execute(ownerSource, "spawnshare " + guestSelector) == 1,
                    "Sharing must return the number of players granted");
            assertMessage(
                    helper, owner, "[SpawnCommands] Shared your spawn with 1 player(s).", 0x55FF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(ownerSource, "spawnshare " + guestSelector) == 0,
                    "Repeated sharing must return 0");
            assertMessage(
                    helper, owner, "[SpawnCommands] Shared your spawn with 0 player(s).", 0xFFFF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(ownerSource, "spawnshare revoke " + guestSelector) == 1,
                    "Revoking must return the number of players revoked");
            assertMessage(
                    helper, owner, "[SpawnCommands] Revoked spawn access for 1 player(s).", 0x55FF55);
            owner.clearMessages();
            helper.assertTrue(
                    execute(owner.player(), "myspawn") == 0, "A missing spawn must return 0");
            assertMessage(
                    helper,
                    owner,
                    "[SpawnCommands] You do not have a personal spawn point.",
                    0xFF5555);
            guest.clearMessages();
            helper.assertTrue(
                    execute(guest.player(), "spawnof SpawnOwner") == 0,
                    "An unshared spawn must return 0");
            assertMessage(
                    helper,
                    guest,
                    "[SpawnCommands] SpawnOwner has not shared their spawn with you.",
                    0xFF5555);
            guest.clearMessages();
            helper.assertTrue(execute(guest.player(), "spawn") == 1, "/spawn must return 1");
            assertMessage(helper, guest, "[SpawnCommands] Teleported to world spawn.", 0x55FF55);
            owner.clearMessages();
            owner.command("spawncommands help");
            helper.assertTrue(
                    owner.received("[SpawnCommands] /spawn - teleport to the world spawn"),
                    "/spawncommands help must list the commands, got " + owner.messageTexts());
        }
        helper.assertTrue(
                ServerModHelpRegistry.entries().stream()
                        .anyMatch(
                                entry ->
                                        entry.modId().equals(SpawnCommands.MOD_ID)
                                                && entry.helpCommand()
                                                        .equals("/spawncommands help")
                                                && entry.adminConfigCommand() == null),
                "SpawnCommands must be registered in /servermods");
        var output = new CapturedCommandOutput();
        helper.getLevel()
                .getServer()
                .getCommands()
                .performPrefixedCommand(output.source(helper.getLevel().getServer()), "servermods");
        helper.assertTrue(
                output.text()
                        .contains(
                                "SpawnCommands: Adds world, personal, and shared player spawn"
                                        + " commands. /spawncommands help"),
                "/servermods must list SpawnCommands, got " + output.text());
        helper.succeed();
    }

    private static CommandSourceStack withServerPermissions(ServerPlayer player) {
        return player.createCommandSourceStack()
                .withPermission(player.level().getServer().createCommandSourceStack().permissions());
    }

    private static int execute(ServerPlayer player, String command) {
        return execute(player.createCommandSourceStack(), command);
    }

    private static int execute(CommandSourceStack source, String command) {
        try {
            return source.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Could not execute /" + command, exception);
        }
    }

    private static void assertMessage(
            GameTestHelper helper, GameTestPlayer player, String text, int bodyColor) {
        List<Component> messages = player.messages();
        helper.assertTrue(
                messages.size() == 1 && messages.getFirst().getString().equals(text),
                "Expected only \"" + text + "\", got " + player.messageTexts());
        List<Integer> colors = new ArrayList<>();
        messages.getFirst()
                .visit(
                        (style, segment) -> {
                            if (!segment.isEmpty()) colors.add(style.getColor().getValue());
                            return Optional.empty();
                        },
                        Style.EMPTY);
        helper.assertTrue(
                colors.equals(List.of(0xAAAAAA, bodyColor)),
                "Expected a grey prefix and a coloured body for \"" + text + "\", got " + colors);
    }

    @SuppressWarnings("removal")
    private static void assertAlwaysLandsOn(
            GameTestHelper helper,
            ServerPlayer player,
            ServerPlayer spawnOwner,
            Vec3 expected,
            String command) {
        for (int attempt = 0; attempt < TELEPORTS; attempt++) {
            player.teleportTo(expected.x() + 20.0D, expected.y(), expected.z() + 20.0D);
            helper.assertTrue(
                    SpawnTeleportService.teleportToPersonalSpawn(player, spawnOwner) == 1,
                    command + " must teleport successfully");
            helper.assertTrue(
                    player.position().equals(expected),
                    command + " must land on " + expected + " but landed on " + player.position());
        }
    }

    private static void withRespawnRadius(GameTestHelper helper, Runnable body) {
        MinecraftServer server = helper.getLevel().getServer();
        int previous = server.getGameRules().get(GameRules.RESPAWN_RADIUS);
        server.getGameRules().set(GameRules.RESPAWN_RADIUS, RESPAWN_RADIUS, server);
        try {
            body.run();
        } finally {
            server.getGameRules().set(GameRules.RESPAWN_RADIUS, previous, server);
        }
    }
}
