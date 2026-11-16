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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.MinecraftServer;
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
                                argument("player", GameProfileArgument.gameProfile())
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
                                argument("players", GameProfileArgument.gameProfile())
                                        .executes(context -> changeAccess(context, true))));
        HelpPage.create(SpawnCommands.FEEDBACK, "spawncommands")
                .line("/spawn - teleport to the world spawn")
                .line("/myspawn - teleport to your own respawn point")
                .line(
                        "/spawnshare <players> - let players visit your spawn with /spawnof,"
                                + " online or offline")
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
        NameAndId owner = sharedSpawnOwner(context);
        if (owner == null) return 0;
        if (!SpawnAccessData.get(source.getServer()).allows(owner.id(), guest.getUUID())) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "%s has not shared their spawn with you.", owner.name());
        }
        ServerPlayer onlineOwner = source.getServer().getPlayerList().getPlayer(owner.id());
        if (onlineOwner != null) {
            return SpawnTeleportService.teleportToPersonalSpawn(guest, onlineOwner);
        }
        return SpawnTeleportService.teleportToPersonalSpawn(
                guest, owner, OfflineSpawn.respawnConfig(source.getServer(), owner).orElse(null));
    }

    /**
     * The player a guest named with {@code /spawnof}: a selector's single online player, or the
     * server name cache's single entry for a typed name, which may belong to an offline player.
     * Returns null after reporting a name nobody on this server has used.
     */
    private static NameAndId sharedSpawnOwner(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (context.getArgument("player", GameProfileArgument.Result.class)
                instanceof GameProfileArgument.SelectorResult selector) {
            // EntityArgument.player() refused a selector matching several players while it parsed
            // (EntityArgument.java:115-120); GameProfileArgument accepts any selector, so the same
            // refusal is made here. No match already throws NO_PLAYERS_FOUND
            // (GameProfileArgument.java:100-103), as EntityArgument.player() answered.
            Collection<NameAndId> named = selector.getNames(source);
            if (named.size() > 1) throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
            return named.iterator().next();
        }
        // A typed name or UUID parses to a lookup through the name cache, and for a name it has not
        // cached that asks Mojang's profile service on the server thread and writes the answer to
        // usercache.json (GameProfileArgument.java:63-66, CachedUserNameToIdResolver.java:63-69,
        // 118-120). A name this server has never seen must be refused instead, as /spawnshare does,
        // so the argument is matched against the cached names as the text typed.
        String typed = typedArgument(context, "player");
        List<NameAndId> known =
                ServerNameCache.known(source.getServer()).stream()
                        .filter(candidate -> candidate.name().equalsIgnoreCase(typed))
                        .toList();
        if (!known.isEmpty()) return known.getFirst();
        UUID typedId;
        try {
            typedId = UUID.fromString(typed);
        } catch (IllegalArgumentException notAUuid) {
            SpawnCommands.FEEDBACK.failure(
                    source, "No player named '%s' has joined this server.", typed);
            return null;
        }
        // A UUID names an online player, as EntityArgument.player() resolved one
        // (EntitySelector.java:214-217), and an offline player the server has seen.
        ServerPlayer byId = source.getServer().getPlayerList().getPlayer(typedId);
        if (byId != null) return byId.nameAndId();
        Optional<NameAndId> cached =
                ServerNameCache.known(source.getServer()).stream()
                        .filter(candidate -> candidate.id().equals(typedId))
                        .findFirst();
        if (cached.isEmpty()) {
            SpawnCommands.FEEDBACK.failure(
                    source, "No player named '%s' has joined this server.", typed);
        }
        return cached.orElse(null);
    }

    private static int changeAccess(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer owner = SpawnCommands.FEEDBACK.requirePlayer(source);
        if (owner == null) return 0;
        SpawnAccessData access = SpawnAccessData.get(source.getServer());
        Collection<NameAndId> named =
                players(
                        context,
                        "players",
                        grant
                                ? ServerNameCache.known(source.getServer())
                                : cachedNames(source.getServer(), access.guests(owner.getUUID())));
        if (grant && named.isEmpty()) {
            return SpawnCommands.FEEDBACK.failure(
                    source,
                    "No player named '%s' has joined this server.",
                    typedArgument(context, "players"));
        }
        Collection<UUID> playerIds = named.stream().map(NameAndId::id).toList();
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
            Collection<NameAndId> candidates)
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
        return candidates.stream().filter(player -> player.name().equalsIgnoreCase(typed)).toList();
    }

    private static Collection<NameAndId> cachedNames(MinecraftServer server, Collection<UUID> ids) {
        UserNameToIdResolver names = server.services().nameToIdCache();
        return ids.stream().flatMap(id -> names.get(id).stream()).toList();
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
