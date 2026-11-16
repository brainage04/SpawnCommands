package io.github.brainage04.spawncommands.neoforge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.spawncommands.SpawnCommandRegistration;
import io.github.brainage04.spawncommands.SpawnCommands;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SpawnCommands.MOD_ID)
public final class SpawnCommandsNeoForge {
    private static final DeferredRegister<GameRule<?>> GAME_RULES =
            DeferredRegister.create(Registries.GAME_RULE, SpawnCommands.MOD_ID);
    private static final DeferredHolder<GameRule<?>, GameRule<Boolean>> RESET_FALL =
            GAME_RULES.register(
                    "reset_fall",
                    () ->
                            new GameRule<>(
                                    GameRuleCategory.PLAYER,
                                    GameRuleType.BOOL,
                                    BoolArgumentType.bool(),
                                    (visitor, rule) -> visitor.visitBoolean(rule),
                                    Codec.BOOL,
                                    value -> value ? 1 : 0,
                                    true,
                                    FeatureFlagSet.of()));

    public SpawnCommandsNeoForge(IEventBus modEventBus) {
        GAME_RULES.register(modEventBus);
        modEventBus.addListener(
                (FMLCommonSetupEvent event) -> SpawnCommands.initialize(RESET_FALL.get()));
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) ->
                        SpawnCommandRegistration.register(event.getDispatcher()));
    }
}
