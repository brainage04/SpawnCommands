package io.github.brainage04.spawncommands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnCommandsTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void registersEveryPublicCommand() {
		CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
		SpawnCommandRegistration.register(dispatcher);

		assertNotNull(dispatcher.getRoot().getChild("spawn"));
		assertNotNull(dispatcher.getRoot().getChild("myspawn"));
		assertNotNull(dispatcher.getRoot().getChild("spawnof"));
		assertNotNull(dispatcher.getRoot().getChild("spawnshare"));
	}

	@Test
	void keepsSpawnSharesOwnerScopedAndRevocable() {
		SpawnAccessData data = new SpawnAccessData();
		UUID owner = UUID.randomUUID();
		UUID otherOwner = UUID.randomUUID();
		UUID guest = UUID.randomUUID();

		assertEquals(1, data.grant(owner, List.of(guest)));
		assertEquals(0, data.grant(owner, List.of(guest)));
		assertTrue(data.allows(owner, guest));
		assertFalse(data.allows(otherOwner, guest));
		assertEquals(1, data.revoke(owner, List.of(guest)));
		assertFalse(data.allows(owner, guest));
	}
}
