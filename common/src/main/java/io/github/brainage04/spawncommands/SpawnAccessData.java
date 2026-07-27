package io.github.brainage04.spawncommands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent owner-to-guest access lists for personal spawn points. */
public final class SpawnAccessData extends SavedData {
	private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
	private static final Codec<SpawnAccessData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUID_CODEC, UUID_CODEC.listOf())
					.optionalFieldOf("shares", Map.of())
					.forGetter(SpawnAccessData::snapshot)
	).apply(instance, SpawnAccessData::new));

	public static final SavedDataType<SpawnAccessData> TYPE = new SavedDataType<>(
			SpawnCommands.of("spawn_access"),
			SpawnAccessData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_MAP_DATA
	);

	private final Map<UUID, Set<UUID>> shares = new HashMap<>();

	public SpawnAccessData() {
	}

	private SpawnAccessData(Map<UUID, List<UUID>> shares) {
		shares.forEach((owner, guests) -> this.shares.put(owner, new HashSet<>(guests)));
	}

	public static SpawnAccessData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean allows(UUID owner, UUID guest) {
		return owner.equals(guest) || shares.getOrDefault(owner, Set.of()).contains(guest);
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
		if (changed > 0) setDirty();
		return changed;
	}

	private Map<UUID, List<UUID>> snapshot() {
		Map<UUID, List<UUID>> copy = new HashMap<>();
		shares.forEach((owner, guests) -> copy.put(owner, List.copyOf(guests)));
		return Map.copyOf(copy);
	}
}
