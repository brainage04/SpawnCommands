package io.github.brainage04.spawncommands;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.brainage04.brainagelib.help.HelpPage;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserNameToIdResolver;

public final class SpawnCommandRegistration {
    private SpawnCommandRegistration() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("spawn").executes(SpawnCommandRegistration::teleportToWorldSpawn));
        dispatcher.register(
                literal("myspawn").executes(SpawnCommandRegistration::teleportToOwnSpawn));
        dispatcher.register(
                literal("spawnof")
                        .then(
                                argument("player", EntityArgument.player())
                                        .executes(
                                                SpawnCommandRegistration::teleportToSharedSpawn)));
        dispatcher.register(
                literal("spawnshare")
                        .then(
                                literal("revoke")
                                        .then(
                                                argument(
                                                                "players",
                                                                GameProfileArgument.gameProfile())
                                                        .suggests(
                                                                SpawnCommandRegistration
                                                                        ::suggestGuests)
                                                        .executes(
                                                                context ->
                                                                        changeAccess(
                                                                                context, false))))
                        .then(
                                argument("players", EntityArgument.players())
                                        .executes(context -> changeAccess(context, true))));
        HelpPage.create(SpawnCommands.FEEDBACK, "spawncommands")
                .line("/spawn - teleport to the world spawn")
                .line("/myspawn - teleport to your own respawn point")
                .line("/spawnshare <players> - let players visit your spawn with /spawnof")
                .line("/spawnshare revoke <players> - take that access away again")
                .line("/spawnof <player> - teleport to a spawn someone shared with you")
                .register(dispatcher);
    }

    private static int teleportToWorldSpawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = SpawnCommands.FEEDBACK.requirePlayer(context.getSource());
        if (player == null) return 0;
        return SpawnTeleportService.teleportToWorldSpawn(player);
    }

    private static int teleportToOwnSpawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = SpawnCommands.FEEDBACK.requirePlayer(context.getSource());
        if (player == null) return 0;
        return SpawnTeleportService.teleportToPersonalSpawn(player, player);
    }

    private static int teleportToSharedSpawn(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer guest = SpawnCommands.FEEDBACK.requirePlayer(source);
        if (guest == null) return 0;
        ServerPlayer owner = EntityArgument.getPlayer(context, "player");
        if (!SpawnAccessData.get(source.getServer()).allows(owner.getUUID(), guest.getUUID())) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "%s has not shared their spawn with you.", owner.getScoreboardName());
        }
        return SpawnTeleportService.teleportToPersonalSpawn(guest, owner);
    }

    private static int changeAccess(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer owner = SpawnCommands.FEEDBACK.requirePlayer(source);
        if (owner == null) return 0;
        SpawnAccessData access = SpawnAccessData.get(source.getServer());
        Collection<UUID> playerIds =
                grant
                        ? EntityArgument.getPlayers(context, "players").stream()
                                .map(ServerPlayer::getUUID)
                                .toList()
                        : players(context, "players", access.guests(owner.getUUID())).stream()
                                .map(NameAndId::id)
                                .toList();
        int changed =
                grant
                        ? access.grant(owner.getUUID(), playerIds)
                        : access.revoke(owner.getUUID(), playerIds);
        String message =
                grant
                        ? "Shared your spawn with %d player(s)."
                        : "Revoked spawn access for %d player(s).";
        if (changed == 0) {
            SpawnCommands.FEEDBACK.neutral(source, message, changed);
        } else {
            SpawnCommands.FEEDBACK.success(source, message, false, changed);
        }
        return changed;
    }

    // A completion request from a source that is not a player gets no suggestions and no message:
    // requirePlayer would answer a Tab press with chat.
    private static CompletableFuture<Suggestions> suggestGuests(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer owner = context.getSource().getPlayer();
        if (owner == null) return Suggestions.empty();
        UserNameToIdResolver names = context.getSource().getServer().services().nameToIdCache();
        return SharedSuggestionProvider.suggest(
                SpawnAccessData.get(context.getSource().getServer())
                        .guests(owner.getUUID())
                        .stream()
                        .flatMap(guest -> names.get(guest).stream())
                        .map(NameAndId::name)
                        .toList(),
                builder);
    }

    /**
     * The players a {@link GameProfileArgument} names: a selector's online players, or those of
     * {@code candidates} whose cached name is the name typed.
     */
    private static Collection<NameAndId> players(
            CommandContext<CommandSourceStack> context,
            String argument,
            Collection<UUID> candidates)
            throws CommandSyntaxException {
        GameProfileArgument.Result result =
                context.getArgument(argument, GameProfileArgument.Result.class);
        if (result instanceof GameProfileArgument.SelectorResult) {
            return result.getNames(context.getSource());
        }
        // A typed name never goes through GameProfileArgument.getNames: for a name the server has
        // not cached it asks Mojang's profile service on the server thread and writes the answer to
        // usercache.json (GameProfileArgument.java:63-66, CachedUserNameToIdResolver.java:63-69,
        // 118-120). The lookup by UUID only reads the cache
        // (CachedUserNameToIdResolver.java:135-143).
        String typed = typedArgument(context, argument);
        UserNameToIdResolver names = context.getSource().getServer().services().nameToIdCache();
        return candidates.stream()
                .flatMap(candidate -> names.get(candidate).stream())
                .filter(player -> player.name().equalsIgnoreCase(typed))
                .toList();
    }

    private static String typedArgument(
            CommandContext<CommandSourceStack> context, String argument) {
        for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
            if (node.getNode().getName().equals(argument)) {
                return node.getRange().get(context.getInput());
            }
        }
        throw new IllegalArgumentException("No argument named " + argument);
    }
}
