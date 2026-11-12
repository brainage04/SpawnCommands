package io.github.brainage04.spawncommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnCommandsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsSpawnSharesOwnerScopedAndRevocable() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = new UUID(0, 1);
        UUID otherOwner = new UUID(0, 2);
        UUID guest = new UUID(0, 3);

        assertEquals(1, data.grant(owner, List.of(guest)));
        assertEquals(0, data.grant(owner, List.of(guest)));
        assertTrue(data.allows(owner, guest));
        assertFalse(data.allows(otherOwner, guest));
        assertEquals(1, data.revoke(owner, List.of(guest)));
        assertFalse(data.allows(owner, guest));
    }

    @Test
    void persistsOwnerIsolationAndRevocationThroughTheSavedDataCodec() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = new UUID(0, 1);
        UUID otherOwner = new UUID(0, 2);
        UUID guest = new UUID(0, 3);
        UUID revokedGuest = new UUID(0, 4);
        data.grant(owner, List.of(guest, revokedGuest));
        data.grant(otherOwner, List.of(revokedGuest));
        data.revoke(owner, List.of(revokedGuest));

        var encoded = SpawnAccessData.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        SpawnAccessData restored =
                SpawnAccessData.TYPE.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(restored.allows(owner, guest));
        assertFalse(restored.allows(owner, revokedGuest));
        assertFalse(restored.allows(otherOwner, guest));
        assertTrue(restored.allows(otherOwner, revokedGuest));
        assertEquals(1, restored.revoke(owner, List.of(guest)));
        assertFalse(restored.allows(owner, guest));
    }

    @Test
    void marksOnlyEffectiveShareChangesForSaving() {
        SpawnAccessData data = new SpawnAccessData();
        UUID owner = new UUID(0, 1);
        UUID guest = new UUID(0, 2);
        assertTrue(data.allows(owner, owner));
        assertEquals(0, data.grant(owner, List.of(owner)));
        assertFalse(data.isDirty());
        assertEquals(1, data.grant(owner, List.of(guest, guest)));
        assertTrue(data.isDirty());

        data.setDirty(false);
        assertEquals(0, data.grant(owner, List.of(guest)));
        assertFalse(data.isDirty());
        assertEquals(1, data.revoke(owner, List.of(guest, guest)));
        assertTrue(data.isDirty());

        data.setDirty(false);
        assertEquals(0, data.revoke(owner, List.of(guest)));
        assertFalse(data.isDirty());
        assertTrue(data.allows(owner, owner));
    }
}
