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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserNameToIdResolver;

public final class SpawnCommandRegistration {
    private static final DateTimeFormatter LAST_SEEN =
            DateTimeFormatter.ofPattern("d MMM", Locale.ROOT).withZone(ZoneId.systemDefault());

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
        MinecraftServer server = source.getServer();
        if (context.getArgument("player", GameProfileArgument.Result.class)
                instanceof GameProfileArgument.SelectorResult selector) {
            // EntityArgument.player() refused a selector matching several players while it parsed
            // (EntityArgument.java:115-120); GameProfileArgument accepts any selector, so the same
            // refusal is made here. No match already throws NO_PLAYERS_FOUND
            // (GameProfileArgument.java:100-103), as EntityArgument.player() answered.
            Collection<NameAndId> named = selector.getNames(source);
            if (named.size() > 1) throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
            return arrive(guest, named.iterator().next());
        }
        String typed = typedArgument(context, "player");
        List<NameAndId> known =
                ServerNameCache.known(server).stream()
                        .filter(candidate -> candidate.name().equalsIgnoreCase(typed))
                        .toList();
        if (!known.isEmpty()) return arrive(guest, known.getFirst());
        UUID typedId = parseUuid(typed);
        if (typedId != null) {
            // A UUID names an online player, as EntityArgument.player() resolved one
            // (EntitySelector.java:214-217), and an offline player the server has seen.
            ServerPlayer byId = server.getPlayerList().getPlayer(typedId);
            if (byId != null) return arrive(guest, byId.nameAndId());
            Optional<NameAndId> cached =
                    ServerNameCache.known(server).stream()
                            .filter(candidate -> candidate.id().equals(typedId))
                            .findFirst();
            if (cached.isEmpty()) {
                return SpawnCommands.FEEDBACK.failure(
                        source, "No player named '%s' has joined this server.", typed);
            }
            return arrive(guest, cached.get());
        }
        SpawnCommands.FEEDBACK.neutral(source, "Looking up %s...", typed);
        UUID guestId = guest.getUUID();
        int[] outcome = {1};
        NameLookup.of(server)
                .lookup(
                        typed,
                        answer -> {
                            ServerPlayer current = server.getPlayerList().getPlayer(guestId);
                            if (current == null) return;
                            outcome[0] =
                                    answer.status() == NameLookup.Status.FOUND
                                            ? arrive(current, answer.profile())
                                            : reportLookupFailure(
                                                    current.createCommandSourceStack(), typed, answer);
                        });
        return outcome[0];
    }

    /** Teleports {@code guest} to the spawn {@code owner} shared, online or not. */
    private static int arrive(ServerPlayer guest, NameAndId owner) {
        CommandSourceStack source = guest.createCommandSourceStack();
        MinecraftServer server = source.getServer();
        if (!SpawnAccessData.get(server).allows(owner.id(), guest.getUUID())) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "%s has not shared their spawn with you.", owner.name());
        }
        ServerPlayer onlineOwner = server.getPlayerList().getPlayer(owner.id());
        if (onlineOwner != null) {
            return SpawnTeleportService.teleportToPersonalSpawn(guest, onlineOwner);
        }
        return SpawnTeleportService.teleportToPersonalSpawn(
                guest, owner, OfflineSpawn.respawnConfig(server, owner).orElse(null));
    }

    private static int changeAccess(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer owner = SpawnCommands.FEEDBACK.requirePlayer(source);
        if (owner == null) return 0;
        MinecraftServer server = source.getServer();
        long wait = ShareRateLimit.of(server).refuse(owner.getUUID());
        if (wait > 0) {
            return SpawnCommands.FEEDBACK.failure(
                    source,
                    "You can use /spawnshare again in %s.",
                    ShareRateLimit.describe(wait));
        }
        SpawnAccessData access = SpawnAccessData.get(server);
        GameProfileArgument.Result result =
                context.getArgument("players", GameProfileArgument.Result.class);
        if (result instanceof GameProfileArgument.SelectorResult) {
            return apply(source, owner, access, grant, result.getNames(source));
        }
        String typed = typedArgument(context, "players");
        return grant
                ? grantTyped(server, source, owner, access, typed)
                : revokeTyped(server, source, owner, access, typed);
    }

    /**
     * Grants to the player a typed name belongs to. A name the server has cached is answered at
     * once; any other name is looked up off the server thread and the grant finishes when the
     * answer arrives.
     */
    private static int grantTyped(
            MinecraftServer server,
            CommandSourceStack source,
            ServerPlayer owner,
            SpawnAccessData access,
            String typed) {
        List<NameAndId> matches =
                ServerNameCache.known(server).stream()
                        .filter(player -> player.name().equalsIgnoreCase(typed))
                        .toList();
        if (!matches.isEmpty()) return apply(source, owner, access, true, matches);
        UUID id = parseUuid(typed);
        if (id != null) {
            List<NameAndId> cached = List.copyOf(cachedNames(server, List.of(id)));
            if (!cached.isEmpty()) return apply(source, owner, access, true, cached);
            return SpawnCommands.FEEDBACK.failure(
                    source, "No player with the id '%s' has joined this server.", typed);
        }
        SpawnCommands.FEEDBACK.neutral(source, "Looking up %s...", typed);
        UUID ownerId = owner.getUUID();
        // The answer arrives on the server thread later, so the command's result is 1 (accepted)
        // unless the lookup answered before it returned, as a test's stub does.
        int[] outcome = {1};
        NameLookup.of(server)
                .lookup(
                        typed,
                        answer -> {
                            ServerPlayer current = server.getPlayerList().getPlayer(ownerId);
                            if (current == null) return;
                            outcome[0] = report(server, current, typed, answer);
                        });
        return outcome[0];
    }

    private static int report(
            MinecraftServer server, ServerPlayer owner, String typed, NameLookup.Answer answer) {
        CommandSourceStack source = owner.createCommandSourceStack();
        if (answer.status() != NameLookup.Status.FOUND) {
            return reportLookupFailure(source, typed, answer);
        }
        NameAndId profile = answer.profile();
        // The cache is written on the server thread only
        // (CachedUserNameToIdResolver.java:86-95 saves the file).
        server.services().nameToIdCache().add(profile);
        return apply(source, owner, SpawnAccessData.get(server), true, List.of(profile));
    }

    private static int reportLookupFailure(
            CommandSourceStack source, String typed, NameLookup.Answer answer) {
        return switch (answer.status()) {
            case NOT_FOUND ->
                    SpawnCommands.FEEDBACK.failure(
                            source, "Mojang has no player named '%s'.", typed);
            case FAILED ->
                    SpawnCommands.FEEDBACK.failure(
                            source, "Could not look up '%s' right now. Try again later.", typed);
            default ->
                    SpawnCommands.FEEDBACK.failure(
                            source,
                            "Too many name lookups on this server right now. Try again in a"
                                    + " minute.");
        };
    }

    /**
     * Revokes from the guest a typed name belongs to. A name that matches more than one guest
     * revokes nothing and lists each match with a button that revokes by id, because a renamed
     * player and a new holder of the name are different accounts.
     */
    private static int revokeTyped(
            MinecraftServer server,
            CommandSourceStack source,
            ServerPlayer owner,
            SpawnAccessData access,
            String typed) {
        UUID ownerId = owner.getUUID();
        UUID id = parseUuid(typed);
        if (id != null) {
            if (!access.guests(ownerId).contains(id)) {
                return SpawnCommands.FEEDBACK.failure(
                        source, "That player does not have access to your spawn.");
            }
            return report(source, access.revoke(ownerId, List.of(id)), false);
        }
        List<UUID> matches = new ArrayList<>(access.guestsNamed(ownerId, typed));
        for (NameAndId cached : cachedNames(server, access.guests(ownerId))) {
            if (cached.name().equalsIgnoreCase(typed) && !matches.contains(cached.id())) {
                matches.add(cached.id());
            }
        }
        if (matches.isEmpty()) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "No guest of your spawn is named '%s'.", typed);
        }
        if (matches.size() == 1) {
            return apply(
                    source, owner, access, false, List.of(new NameAndId(matches.getFirst(), typed)));
        }
        SpawnCommands.FEEDBACK.neutral(
                source,
                "%d guests of your spawn have been called '%s'. Choose the one to revoke:",
                matches.size(),
                typed);
        for (UUID guest : matches) owner.sendSystemMessage(describe(access, guest));
        return 0;
    }

    /** One line of the ambiguous revoke list: the guest, its other names, and a revoke button. */
    private static Component describe(SpawnAccessData access, UUID guest) {
        List<SpawnAccessData.SeenName> seen = access.names(guest);
        String current = seen.isEmpty() ? guest.toString() : seen.getFirst().name();
        MutableComponent line = Component.literal(" " + current).withStyle(ChatFormatting.WHITE);
        List<String> previous =
                seen.stream()
                        .map(SpawnAccessData.SeenName::name)
                        .filter(name -> !name.equalsIgnoreCase(current))
                        .toList();
        if (!previous.isEmpty()) {
            line.append(
                    Component.literal(" (previous name " + String.join(", ", previous) + ")")
                            .withStyle(ChatFormatting.GRAY));
        }
        if (!seen.isEmpty()) {
            line.append(
                    Component.literal(
                                    " last seen "
                                            + LAST_SEEN.format(
                                                    Instant.ofEpochMilli(
                                                            seen.getFirst().lastSeen())))
                            .withStyle(ChatFormatting.GRAY));
        }
        line.append(
                Component.literal(" [Revoke]")
                        .withStyle(
                                style ->
                                        style.withColor(ChatFormatting.GREEN)
                                                .withClickEvent(
                                                        new ClickEvent.RunCommand(
                                                                "/spawnshare revoke " + guest))));
        return line;
    }

    private static int apply(
            CommandSourceStack source,
            ServerPlayer owner,
            SpawnAccessData access,
            boolean grant,
            Collection<NameAndId> named) {
        long now = System.currentTimeMillis();
        for (NameAndId profile : named) access.sawName(profile.id(), profile.name(), now);
        Collection<UUID> playerIds = named.stream().map(NameAndId::id).toList();
        int changed =
                grant
                        ? access.grant(owner.getUUID(), playerIds)
                        : access.revoke(owner.getUUID(), playerIds);
        return report(source, changed, grant);
    }

    private static int report(CommandSourceStack source, int changed, boolean grant) {
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
        SpawnAccessData access = SpawnAccessData.get(context.getSource().getServer());
        List<String> names = new ArrayList<>();
        for (UUID guest : access.guests(owner.getUUID())) {
            access.names(guest).forEach(seen -> names.add(seen.name()));
        }
        names.addAll(
                cachedNames(context.getSource().getServer(), access.guests(owner.getUUID()))
                        .stream()
                        .map(NameAndId::name)
                        .toList());
        return SharedSuggestionProvider.suggest(names.stream().distinct().toList(), builder);
    }

    private static Collection<NameAndId> cachedNames(MinecraftServer server, Collection<UUID> ids) {
        UserNameToIdResolver names = server.services().nameToIdCache();
        return ids.stream().flatMap(id -> names.get(id).stream()).toList();
    }

    private static UUID parseUuid(String typed) {
        try {
            return UUID.fromString(typed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
