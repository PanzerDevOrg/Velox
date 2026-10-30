package net.bichal.mods.velox;

import net.bichal.mods.celeris.physics.CelerisPhysics;
import net.bichal.mods.velox.config.VeloxConfig;
import net.bichal.mods.velox.parity.ParityHarness;
import net.bichal.mods.velox.physics.VeloxEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Velox: hands item entity physics to Celeris's batch engine. Velox itself
 * does no simulation; it only connects vanilla entities and terrain to
 * {@code net.bichal.mods.celeris.physics} and applies the results inside the
 * vanilla tick, so every other part of {@code ItemEntity.tick} (pickup delay,
 * merging, age, despawn) keeps running unchanged.
 */
@Mod(Velox.MOD_ID)
public final class Velox {

    public static final String MOD_ID = "velox";
    public static final Logger LOGGER = LoggerFactory.getLogger("Velox");

    public Velox(IEventBus modBus, ModContainer container) {
        container.registerConfig(commonConfigType(), VeloxConfig.SPEC);
        requestNativeKernel();
        VeloxEvents.register();
        if (ParityHarness.ENABLED) {
            ParityHarness.register();
        }
    }

    /**
     * Celeris runs its Java kernel unless a mod asks for the native one, and Velox does: it steps
     * thousands of items in batches, which is where the native kernel is faster. This has to run
     * before Celeris chooses its engine (the first batch), so it is the first thing the mod does.
     * The player keeps the last word with {@code -Dceleris.physics.engine=java}. A Celeris from
     * before the opt-in already prefers the native kernel and has no such method.
     */
    private static void requestNativeKernel() {
        try {
            CelerisPhysics.allowNative(MOD_ID);
        } catch (LinkageError olderCeleris) {
            LOGGER.debug("Celeris has no allowNative: its engine choice is not opt-in");
        }
    }

    /**
     * COMMON (loaded on each side, not synced); from NeoForge 26.3 the types are
     * CLIENT, SYNCED and LOCAL, where LOCAL is what COMMON was. Looked up by name
     * so one jar runs on both.
     */
    private static ModConfig.Type commonConfigType() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if (type.name().equals("COMMON")) {
                return type;
            }
        }
        return ModConfig.Type.valueOf("LOCAL");
    }
}
