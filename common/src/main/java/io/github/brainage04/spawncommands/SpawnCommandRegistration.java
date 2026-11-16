package io.github.brainage04.spawncommands;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
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
                                argument("player", StringArgumentType.greedyString())
                                        .suggests(SpawnCommandRegistration::suggestOwners)
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
        String typed = context.getArgument("player", String.class);
        if (typed.startsWith("@")) {
            return arrive(guest, List.of(selectedPlayer(source, typed)));
        }
        // Online and cached matches together: an online player who never shared must not hide an
        // offline one who did.
        List<NameAndId> cachedNames = ServerNameCache.known(server);
        List<NameAndId> known = new ArrayList<>(named(server, typed, cachedNames));
        for (NameAndId cached : cachedNames) {
            if (cached.name().equalsIgnoreCase(typed)
                    && known.stream().noneMatch(seen -> seen.id().equals(cached.id()))) {
                known.add(cached);
            }
        }
        if (!known.isEmpty()) return arrive(guest, known);
        UUID typedId = parseUuid(typed);
        if (typedId != null) {
            // A UUID names an online player, as EntityArgument.player() resolved one
            // (EntitySelector.java:214-217), and an offline player the server has seen.
            ServerPlayer byId = server.getPlayerList().getPlayer(typedId);
            if (byId != null) return arrive(guest, List.of(byId.nameAndId()));
            Optional<NameAndId> cached =
                    cachedNames.stream()
                            .filter(candidate -> candidate.id().equals(typedId))
                            .findFirst();
            if (cached.isEmpty()) {
                return SpawnCommands.FEEDBACK.failure(
                        source, "No player named '%s' has joined this server.", typed);
            }
            return arrive(guest, List.of(cached.get()));
        }
        // Nobody on this server has shared a spawn with this guest, so no name Mojang could answer
        // would let them in: refuse without spending one of the server's lookups.
        if (!SpawnAccessData.get(server).anyOwnerAllows(guest.getUUID())) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "No player named '%s' has shared their spawn with you.", typed);
        }
        // /spawnof is not a share, so it has its own allowance: a guest who loops it must not be
        // able to spend the lookups /spawnshare needs.
        long wait = ShareRateLimit.lookups(server).refuse(guest.getUUID());
        if (wait > 0) {
            return SpawnCommands.FEEDBACK.failure(
                    source,
                    "You can ask for a name to be looked up again in %s.",
                    ShareRateLimit.describe(wait));
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
                            if (answer.status() == NameLookup.Status.FOUND) {
                                outcome[0] = arrive(current, List.of(answer.profile()));
                            } else {
                                outcome[0] = reportLookupFailure(server, current, typed, answer);
                            }
                        });
        return outcome[0];
    }

    /**
     * Teleports {@code guest} to the spawn the one of {@code candidates} who shared it with them
     * owns, online or not. A name can belong to several accounts over time, so the candidate that
     * actually shared is the one meant.
     */
    private static int arrive(ServerPlayer guest, List<NameAndId> candidates) {
        CommandSourceStack source = guest.createCommandSourceStack();
        MinecraftServer server = source.getServer();
        SpawnAccessData access = SpawnAccessData.get(server);
        List<NameAndId> allowing =
                candidates.stream()
                        .filter(candidate -> access.allows(candidate.id(), guest.getUUID()))
                        .toList();
        if (allowing.isEmpty()) {
            return SpawnCommands.FEEDBACK.failure(
                    source,
                    "%s has not shared their spawn with you.",
                    candidates.getFirst().name());
        }
        NameAndId owner = allowing.getFirst();
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
        SpawnAccessData access = SpawnAccessData.get(server);
        GameProfileArgument.Result result =
                context.getArgument("players", GameProfileArgument.Result.class);
        if (result instanceof GameProfileArgument.SelectorResult) {
            Collection<NameAndId> named = result.getNames(source);
            if (!charged(server, source, owner)) return 0;
            return apply(source, owner, access, grant, named);
        }
        String typed = typedArgument(context, "players");
        return grant
                ? grantTyped(server, source, owner, access, typed)
                : revokeTyped(server, source, owner, access, typed);
    }

    /**
     * Counts this use against the player's allowance, or answers false and says how long to wait.
     * Every use that reaches a grant or a revoke is counted, whichever name it was given. The
     * ambiguous list is not, because it only prints: charging it would refuse the [Revoke] button
     * it prints whenever the button is clicked inside the same second.
     */
    private static boolean charged(
            MinecraftServer server, CommandSourceStack source, ServerPlayer owner) {
        long wait = ShareRateLimit.of(server).refuse(owner.getUUID());
        if (wait == 0) return true;
        SpawnCommands.FEEDBACK.failure(
                source, "You can use /spawnshare again in %s.", ShareRateLimit.describe(wait));
        return false;
    }

    /**
     * Grants to the player a typed name belongs to. A name the server has cached is answered at
     * once; any other name is looked up off the server thread and the grant finishes when the
     * answer arrives. A name several accounts hold grants nothing and lists them, because one
     * command must not hand access to an account the owner did not identify.
     */
    private static int grantTyped(
            MinecraftServer server,
            CommandSourceStack source,
            ServerPlayer owner,
            SpawnAccessData access,
            String typed) {
        List<NameAndId> matches = named(server, typed, ServerNameCache.known(server));
        if (matches.size() > 1) {
            return listCandidates(
                    server,
                    source,
                    owner,
                    Component.literal(
                            matches.size()
                                    + " players have been called '"
                                    + typed
                                    + "'. Choose the one to share with:"),
                    matches.stream()
                            .map(
                                    match ->
                                            candidateLine(
                                                    access,
                                                    match.id(),
                                                    displayName(server, access, match.id()),
                                                    "Share",
                                                    "/spawnshare " + match.id()))
                            .toList());
        }
        if (!matches.isEmpty()) {
            if (!charged(server, source, owner)) return 0;
            return apply(source, owner, access, true, matches);
        }
        UUID id = parseUuid(typed);
        if (id != null) {
            List<NameAndId> cached = List.copyOf(cachedNames(server, List.of(id)));
            if (cached.isEmpty()) {
                // The [Share] button names an account by id, and usercache.json keeps only the 1000
                // most recently used profiles (CachedUserNameToIdResolver.java:40,172), so an
                // account the list was built from can be gone from the cache.
                if (!access.names(id).isEmpty()) {
                    cached = List.of(new NameAndId(id, displayName(server, access, id)));
                }
            }
            if (!cached.isEmpty()) {
                if (!charged(server, source, owner)) return 0;
                return apply(source, owner, access, true, cached);
            }
            return SpawnCommands.FEEDBACK.failure(
                    source, "No player with the id '%s' has joined this server.", typed);
        }
        if (!charged(server, source, owner)) return 0;
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
        if (answer.status() != NameLookup.Status.FOUND) {
            return reportLookupFailure(server, owner, typed, answer);
        }
        CommandSourceStack source = owner.createCommandSourceStack();
        NameAndId profile = answer.profile();
        // add() also rewrites usercache.json (CachedUserNameToIdResolver.java:86-95).
        server.services().nameToIdCache().add(profile);
        return apply(source, owner, SpawnAccessData.get(server), true, List.of(profile));
    }

    /**
     * Answers a lookup that found nothing to act on. A {@code STALE} answer found the account but
     * too late, so its name is cached and the player is asked to run the command again; the other
     * three only say what happened.
     */
    private static int reportLookupFailure(
            MinecraftServer server, ServerPlayer player, String typed, NameLookup.Answer answer) {
        CommandSourceStack source = player.createCommandSourceStack();
        if (answer.status() == NameLookup.Status.STALE) {
            // Caching the name makes the retry answer at once, without another lookup.
            server.services().nameToIdCache().add(answer.profile());
            return SpawnCommands.FEEDBACK.failure(
                    source,
                    "Found '%s', but the lookup took too long to act on. Run the command again.",
                    typed);
        }
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
                                    + " couple of minutes.");
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
            if (!charged(server, source, owner)) return 0;
            return revokeOne(server, source, owner, access, id);
        }
        List<UUID> matches = new ArrayList<>(access.guestsNamed(ownerId, typed));
        for (NameAndId cached : cachedNames(server, access.guests(ownerId))) {
            if (cached.name().equalsIgnoreCase(typed) && !matches.contains(cached.id())) {
                matches.add(cached.id());
            }
        }
        if (matches.isEmpty()) return revokeFoundByName(server, source, owner, typed);
        if (matches.size() == 1) {
            // The typed name may also be the current name of someone who never had access: the
            // owner could mean that account. Nothing is revoked until the guest is named by id.
            if (!holdersOutside(server, matches, typed).isEmpty()) {
                return SpawnCommands.FEEDBACK.failure(
                        source,
                        "'%s' is also the name of a player who does not have access to your spawn."
                                + " Run '/spawnshare revoke %s' to revoke the guest.",
                        typed,
                        matches.getFirst());
            }
            if (!charged(server, source, owner)) return 0;
            return revokeOne(server, source, owner, access, matches.getFirst());
        }
        return listCandidates(
                server,
                source,
                owner,
                Component.literal(
                        matches.size()
                                + " guests of your spawn have been called '"
                                + typed
                                + "'. Choose the one to revoke:"),
                matches.stream()
                        .map(
                                guest ->
                                        candidateLine(
                                                access,
                                                guest,
                                                displayName(server, access, guest),
                                                "Revoke",
                                                "/spawnshare revoke " + guest))
                        .toList());
    }

    /**
     * Revokes from the guest a name belongs to when nothing the server has seen for the owner's
     * guests matches. The owner typed a name the server holds no history for, so the account behind
     * it is looked up at Mojang: a guest whose id that is loses access, and a name belonging to
     * anyone else revokes nothing.
     */
    private static int revokeFoundByName(
            MinecraftServer server, CommandSourceStack source, ServerPlayer owner, String typed) {
        UUID ownerId = owner.getUUID();
        if (!charged(server, source, owner)) return 0;
        SpawnCommands.FEEDBACK.neutral(source, "Looking up %s...", typed);
        int[] outcome = {1};
        NameLookup.of(server)
                .lookup(
                        typed,
                        answer -> {
                            ServerPlayer current = server.getPlayerList().getPlayer(ownerId);
                            if (current == null) return;
                            outcome[0] =
                                    revokeAnswer(
                                            server,
                                            current.createCommandSourceStack(),
                                            current,
                                            typed,
                                            answer);
                        });
        return outcome[0];
    }

    private static int revokeAnswer(
            MinecraftServer server,
            CommandSourceStack source,
            ServerPlayer owner,
            String typed,
            NameLookup.Answer answer) {
        UUID ownerId = owner.getUUID();
        SpawnAccessData access = SpawnAccessData.get(server);
        if (answer.status() == NameLookup.Status.FOUND) {
            NameAndId profile = answer.profile();
            if (!access.guests(ownerId).contains(profile.id())) {
                return SpawnCommands.FEEDBACK.failure(
                        source, "%s does not have access to your spawn.", profile.name());
            }
            // add() also rewrites usercache.json (CachedUserNameToIdResolver.java:86-95).
            server.services().nameToIdCache().add(profile);
            return revokeOne(server, source, owner, access, profile.id());
        }
        if (answer.status() != NameLookup.Status.NOT_FOUND) {
            return reportLookupFailure(server, owner, typed, answer);
        }
        return SpawnCommands.FEEDBACK.failure(
                source, "No guest of your spawn is named '%s'.", typed);
    }

    /**
     * The account's current name, its other names and when it was last seen. A name an owner typed
     * is recorded with no time, so "last seen" is the newest join the server recorded.
     */
    private static MutableComponent accountLine(
            SpawnAccessData access, UUID id, String currentName) {
        List<SpawnAccessData.SeenName> seen = access.names(id);
        MutableComponent line =
                Component.literal(" " + currentName).withStyle(ChatFormatting.WHITE);
        List<String> previous =
                seen.stream()
                        .map(SpawnAccessData.SeenName::name)
                        .filter(name -> !name.equalsIgnoreCase(currentName))
                        .toList();
        if (!previous.isEmpty()) {
            line.append(
                    Component.literal(" (previous name " + String.join(", ", previous) + ")")
                            .withStyle(ChatFormatting.GRAY));
        }
        seen.stream()
                .filter(entry -> entry.lastSeen() > 0)
                .findFirst()
                .ifPresent(
                        entry ->
                                line.append(
                                        Component.literal(
                                                        " last seen "
                                                                + LAST_SEEN.format(
                                                                        Instant.ofEpochMilli(
                                                                                entry.lastSeen())))
                                                .withStyle(ChatFormatting.GRAY)));
        return line;
    }

    /** One line of a candidate list: the account, its other names, and a button naming its id. */
    private static Component candidateLine(
            SpawnAccessData access, UUID id, String currentName, String label, String command) {
        return accountLine(access, id, currentName)
                .append(
                        Component.literal(" [" + label + "]")
                                .withStyle(
                                        style ->
                                                style.withColor(ChatFormatting.GREEN)
                                                        .withClickEvent(
                                                                new ClickEvent.RunCommand(
                                                                        command))));
    }

    /**
     * Prints the accounts a typed name could mean and changes nothing. The list has its own
     * allowance, so the button it prints stays affordable in the same second.
     */
    private static int listCandidates(
            MinecraftServer server,
            CommandSourceStack source,
            ServerPlayer owner,
            Component header,
            List<Component> lines) {
        long wait = ShareRateLimit.listing(server).refuse(owner.getUUID());
        if (wait > 0) {
            return SpawnCommands.FEEDBACK.failure(
                    source, "You can ask for the list again in %s.", ShareRateLimit.describe(wait));
        }
        SpawnCommands.FEEDBACK.neutral(source, header);
        for (Component line : lines) owner.sendSystemMessage(line);
        return 0;
    }

    /**
     * Revokes one guest and names the account, so the owner sees who lost access. The line is built
     * before the revoke, which drops the history of a guest no owner shares with any more.
     */
    private static int revokeOne(
            MinecraftServer server,
            CommandSourceStack source,
            ServerPlayer owner,
            SpawnAccessData access,
            UUID guest) {
        Component line = accountLine(access, guest, displayName(server, access, guest));
        int changed = access.revoke(owner.getUUID(), List.of(guest));
        int outcome = report(source, changed, false);
        if (changed > 0) owner.sendSystemMessage(line);
        return outcome;
    }

    /**
     * The name to show for an account: the newest one the server saw it under, else the first name
     * recorded for it, else its cached name.
     */
    private static String displayName(MinecraftServer server, SpawnAccessData access, UUID id) {
        List<SpawnAccessData.SeenName> seen = access.names(id);
        return seen.stream()
                .filter(entry -> entry.lastSeen() > 0)
                .findFirst()
                .or(() -> seen.stream().findFirst())
                .map(SpawnAccessData.SeenName::name)
                .orElseGet(
                        () ->
                                cachedNames(server, List.of(id)).stream()
                                        .map(NameAndId::name)
                                        .findFirst()
                                        .orElse(id.toString()));
    }

    private static int apply(
            CommandSourceStack source,
            ServerPlayer owner,
            SpawnAccessData access,
            boolean grant,
            Collection<NameAndId> named) {
        // A revoke records no name: the history is what finds the guest again, and a name the
        // owner typed to remove access is not a sighting of the player.
        if (grant) {
            for (NameAndId profile : named) access.recordName(profile.id(), profile.name());
        }
        Collection<UUID> playerIds = named.stream().map(NameAndId::id).toList();
        int changed =
                grant
                        ? access.grant(owner.getUUID(), playerIds)
                        : access.revoke(owner.getUUID(), playerIds);
        if (grant) {
            // A grant records a name for an account that may be neither a guest nor online, so the
            // non-guest cap is enforced here as well as on a join. It runs after the grant, so the
            // account just granted is already in the keep set.
            access.pruneNames(SpawnCommands.guestsAndOnline(owner.level().getServer(), access));
        }
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

    /**
     * The single online player a selector names. Selectors stay online-players-only, as {@code
     * EntityArgument.player()} made them: {@code findPlayers} checks the selector permission
     * (EntitySelector.java:119-124), and a selector that will not parse is refused with the
     * parser's own message.
     */
    private static NameAndId selectedPlayer(CommandSourceStack source, String typed)
            throws CommandSyntaxException {
        StringReader reader = new StringReader(typed);
        EntitySelector selector = new EntitySelectorParser(reader, true).parse();
        if (selector.includesEntities() && !selector.isSelfSelector()) {
            throw EntityArgument.ERROR_ONLY_PLAYERS_ALLOWED.createWithContext(reader);
        }
        List<ServerPlayer> players = selector.findPlayers(source);
        if (players.isEmpty()) throw EntityArgument.NO_PLAYERS_FOUND.create();
        if (players.size() > 1) throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
        return players.getFirst().nameAndId();
    }

    private static CompletableFuture<Suggestions> suggestOwners(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                ServerNameCache.known(context.getSource().getServer()).stream()
                        .map(NameAndId::name)
                        .distinct()
                        .toList(),
                builder);
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

    /**
     * The players a typed name belongs to: the online ones when any is online, otherwise the cached
     * ones. A name can belong to several accounts over time, and the player standing here now is
     * the one meant.
     */
    private static List<NameAndId> named(
            MinecraftServer server, String typed, List<NameAndId> cached) {
        List<NameAndId> online =
                server.getPlayerList().getPlayers().stream()
                        .map(ServerPlayer::nameAndId)
                        .filter(player -> player.name().equalsIgnoreCase(typed))
                        .toList();
        if (!online.isEmpty()) return online;
        return cached.stream().filter(player -> player.name().equalsIgnoreCase(typed)).toList();
    }

    /**
     * The accounts the server knows under {@code typed} that are not in {@code guests}. A name can
     * belong to a renamed guest and to whoever holds it now, and only the guest can be revoked.
     */
    private static List<NameAndId> holdersOutside(
            MinecraftServer server, List<UUID> guests, String typed) {
        List<NameAndId> holders =
                new ArrayList<>(
                        server.getPlayerList().getPlayers().stream()
                                .map(ServerPlayer::nameAndId)
                                .filter(player -> player.name().equalsIgnoreCase(typed))
                                .toList());
        holders.addAll(ServerNameCache.known(server));
        return holders.stream()
                .filter(holder -> holder.name().equalsIgnoreCase(typed))
                .filter(holder -> !guests.contains(holder.id()))
                .distinct()
                .toList();
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
