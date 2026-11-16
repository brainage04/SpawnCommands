package io.github.brainage04.spawncommands.fabric;

import io.github.brainage04.spawncommands.SpawnCommandRegistration;
import io.github.brainage04.spawncommands.SpawnCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class SpawnCommandsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SpawnCommands.initialize(
                GameRuleBuilder.forBoolean(true)
                        .category(GameRuleCategory.PLAYER)
                        .buildAndRegister(
                                Identifier.fromNamespaceAndPath(
                                        SpawnCommands.MOD_ID, "reset_fall")));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        SpawnCommandRegistration.register(dispatcher));
    }
}
