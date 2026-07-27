package io.github.brainage04.spawncommands;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpawnCommands {
	public static final String MOD_ID = "spawncommands";
	public static final String MOD_NAME = "SpawnCommands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private SpawnCommands() {
	}

	public static void initialize() {
		LOGGER.info("{} initialized.", MOD_NAME);
	}

	public static Identifier of(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
