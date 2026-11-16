package io.github.brainage04.spawncommands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SpawnAccessData extends SavedData {
    /** A name the server has seen for an account, and when it last saw it (epoch millis). */
    public record SeenName(String name, long lastSeen) {
        public static final Codec<SeenName> CODEC =
                RecordCodecBuilder.create(
                        instance ->
                                instance.group(
                                                Codec.STRING
                                                        .fieldOf("name")
                                                        .forGetter(SeenName::name),
                                                Codec.LONG
                                                        .fieldOf("lastSeen")
                                                        .forGetter(SeenName::lastSeen))
                                        .apply(instance, SeenName::new));
    }

    /** How many names one account keeps. A player who renames often keeps the recent ones. */
    private static final int NAMES_PER_GUEST = 10;

    private static final Codec<Map<UUID, List<SeenName>>> GUEST_NAMES_CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, SeenName.CODEC.listOf());
    private static final Codec<SpawnAccessData> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.unboundedMap(
                                                            UUIDUtil.STRING_CODEC,
                                                            UUIDUtil.STRING_CODEC.listOf())
                                                    .optionalFieldOf("shares", Map.of())
                                                    .forGetter(SpawnAccessData::snapshot),
                                            GUEST_NAMES_CODEC
                                                    .optionalFieldOf("guestNames", Map.of())
                                                    .forGetter(SpawnAccessData::namesSnapshot))
                                    .apply(instance, SpawnAccessData::new));

    public static final SavedDataType<SpawnAccessData> TYPE =
            new SavedDataType<>(
                    SpawnCommands.of("spawn_access"),
                    SpawnAccessData::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA);

    private final Map<UUID, Set<UUID>> shares = new HashMap<>();
    private final Map<UUID, List<SeenName>> guestNames = new HashMap<>();

    public SpawnAccessData() {}

    private SpawnAccessData(Map<UUID, List<UUID>> shares, Map<UUID, List<SeenName>> guestNames) {
        shares.forEach((owner, guests) -> this.shares.put(owner, new HashSet<>(guests)));
        guestNames.forEach((guest, seen) -> this.guestNames.put(guest, new ArrayList<>(seen)));
    }

    public static SpawnAccessData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean allows(UUID owner, UUID guest) {
        return owner.equals(guest) || shares.getOrDefault(owner, Set.of()).contains(guest);
    }

    /** Whether any owner has shared their spawn with {@code guest}. */
    public boolean anyOwnerAllows(UUID guest) {
        return shares.values().stream().anyMatch(guests -> guests.contains(guest));
    }

    public List<UUID> guests(UUID owner) {
        return List.copyOf(shares.getOrDefault(owner, Set.of()));
    }

    public int grant(UUID owner, Collection<UUID> guests) {
        Set<UUID> allowed = shares.computeIfAbsent(owner, ignored -> new HashSet<>());
        int before = allowed.size();
        guests.stream().filter(guest -> !owner.equals(guest)).forEach(allowed::add);
        int changed = allowed.size() - before;
        if (changed > 0) setDirty();
        return changed;
    }

    public int revoke(UUID owner, Collection<UUID> guests) {
        Set<UUID> allowed = shares.get(owner);
        if (allowed == null) return 0;
        int before = allowed.size();
        allowed.removeAll(guests);
        int changed = before - allowed.size();
        if (allowed.isEmpty()) shares.remove(owner);
        if (changed == 0) return 0;
        // A guest every owner has revoked can never match again, so its history is dropped rather
        // than rewritten into the saved data on every later change.
        guests.stream()
                .filter(guest -> shares.values().stream().noneMatch(set -> set.contains(guest)))
                .forEach(guestNames::remove);
        setDirty();
        return changed;
    }

    /**
     * Records that the server has seen {@code name} for {@code guest} now, keeping the newest
     * {@link #NAMES_PER_GUEST} names. A name seen again moves to the front with the new time.
     */
    public void sawName(UUID guest, String name, long now) {
        List<SeenName> seen = guestNames.computeIfAbsent(guest, ignored -> new ArrayList<>());
        seen.removeIf(entry -> entry.name().equalsIgnoreCase(name));
        seen.addFirst(new SeenName(name, now));
        while (seen.size() > NAMES_PER_GUEST) seen.removeLast();
        setDirty();
    }

    /**
     * Records {@code name} as one the account has held, without claiming the server saw the player
     * under it: an owner typing a name is not a sighting, and "last seen" is the last join. A name
     * already recorded keeps its place and its time, and a full history keeps the names it has.
     */
    public void recordName(UUID guest, String name) {
        List<SeenName> seen = guestNames.computeIfAbsent(guest, ignored -> new ArrayList<>());
        if (seen.size() >= NAMES_PER_GUEST) return;
        if (seen.stream().anyMatch(entry -> entry.name().equalsIgnoreCase(name))) return;
        seen.addLast(new SeenName(name, 0));
        setDirty();
    }

    /** The names the server has seen for {@code guest}, newest first. */
    public List<SeenName> names(UUID guest) {
        return List.copyOf(guestNames.getOrDefault(guest, List.of()));
    }

    /** The guests of {@code owner} whose history holds {@code typed}. */
    public List<UUID> guestsNamed(UUID owner, String typed) {
        return guests(owner).stream()
                .filter(
                        guest ->
                                names(guest).stream()
                                        .anyMatch(seen -> seen.name().equalsIgnoreCase(typed)))
                .toList();
    }

    private Map<UUID, List<SeenName>> namesSnapshot() {
        Map<UUID, List<SeenName>> copy = new HashMap<>();
        guestNames.forEach((guest, seen) -> copy.put(guest, List.copyOf(seen)));
        return Map.copyOf(copy);
    }

    private Map<UUID, List<UUID>> snapshot() {
        Map<UUID, List<UUID>> copy = new HashMap<>();
        shares.forEach((owner, guests) -> copy.put(owner, List.copyOf(guests)));
        return Map.copyOf(copy);
    }
}
