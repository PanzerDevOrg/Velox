package net.bichal.mods.velox.physics;

import net.bichal.mods.celeris.api.CelerisFeatures;
import net.bichal.mods.celeris.physics.CelerisPhysics;
import net.bichal.mods.velox.Velox;
import net.bichal.mods.velox.config.VeloxConfig;
import net.bichal.mods.velox.parity.ParityHarness;
import java.lang.management.ManagementFactory;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Entity, chunk and level lifecycle hooks that feed {@link ItemPhysicsWorld}. */
public final class VeloxEvents {

    private static volatile Boolean available;

    private VeloxEvents() {
    }

    public static void register() {
        // LOWEST and not receiving cancelled events: only entities that really join are registered.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, VeloxEvents::onJoin);
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, VeloxEvents::onLeave);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, VeloxEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, VeloxEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> engineAvailable());
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, VeloxEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, VeloxEvents::onTagsUpdated);
    }

    /**
     * Whether items get simulated: on unless disabled in config (or Celeris's
     * engine is turned off with -Dceleris.physics.engine=off). No JVM flags are
     * needed: Celeris runs its pure-Java kernel whenever the native one cannot
     * load, and {@link #nativeKernelHint()} says which flag would change that.
     */
    static boolean engineAvailable() {
        Boolean a = available;
        if (a == null) {
            if (!VeloxConfig.ENABLED.get()) {
                a = false;
                Velox.LOGGER.info("Velox: disabled in config -- items use vanilla physics");
            } else {
                a = CelerisPhysics.isAvailable();
                if (!a) {
                    Velox.LOGGER.info("Velox: Celeris physics engine {} -- items use vanilla physics",
                            CelerisPhysics.engineName());
                } else {
                    Velox.LOGGER.info("Velox: items simulated by Celeris ({}){}", CelerisPhysics.engineName(),
                            nativeKernelHint());
                }
            }
            available = a;
        }
        return a;
    }

    /**
     * The change to the JVM arguments that would give Celeris's native kernel, or
     * "" when it already runs or no flag would help (the Java kernel picked on
     * purpose, compat mode, no library for this system: Celeris logs its reason
     * just above). Only Java 21 (Minecraft 1.21.x) needs a flag, --enable-preview,
     * and there --enable-native-access does harm: once present, the JVM refuses
     * native calls from every module it does not list, and mods are named modules
     * that ALL-UNNAMED does not cover. Java 25 needs neither.
     */
    private static String nativeKernelHint() {
        if (Runtime.version().feature() != 21 || CelerisPhysics.engineName().startsWith("native")
                || "java".equalsIgnoreCase(System.getProperty("celeris.physics.engine"))
                || Boolean.getBoolean("celeris.compatMode")) {
            return "";
        }
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean nativeAccess = jvmArgs.stream().anyMatch(arg -> arg.startsWith("--enable-native-access"));
        if (!CelerisFeatures.isFfmActive()) {
            if (jvmArgs.contains("--enable-preview")) {
                return "";
            }
            return nativeAccess
                    ? "; add --enable-preview and remove --enable-native-access in the JVM arguments for the native kernel"
                    : "; add --enable-preview to the JVM arguments for the native kernel";
        }
        return nativeAccess
                ? "; remove --enable-native-access from the JVM arguments for the native kernel"
                        + " (on Java 21 it blocks native calls from mods)"
                : "";
    }

    /** Plain vanilla items only: subclasses from other mods may move differently. */
    static boolean eligible(Entity entity) {
        return entity.getClass() == ItemEntity.class && !entity.isPassenger() && !entity.isNoGravity()
                && entity.getGravity() == 0.04 && !ParityHarness.isControl(entity);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        if (eligible(entity)) {
            ItemPhysicsWorld world = ItemPhysicsWorld.of(level);
            if (world != null) {
                world.register((ItemEntity) entity);
            }
        } else if (!(entity instanceof ItemEntity) && McCompat.mayBlockItems(entity)) {
            ItemPhysicsWorld world = ((VeloxLevel) level).velox$physics();
            if (world == null && engineAvailable()) {
                world = ItemPhysicsWorld.of(level);
            }
            if (world != null) {
                world.addBlocker(entity);
            }
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof VeloxBody body) {
            ItemPhysicsWorld owner = body.velox$owner();
            if (owner != null) {
                owner.unregister((ItemEntity) entity);
            }
        } else if (McCompat.mayBlockItems(entity) && event.getLevel() instanceof ServerLevel level) {
            // Only entities that may block items were ever added (arrows, orbs and mobs leave all the time).
            ItemPhysicsWorld world = ((VeloxLevel) level).velox$physics();
            if (world != null) {
                world.removeBlocker(entity);
            }
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ItemPhysicsWorld world = ((VeloxLevel) level).velox$physics();
            if (world != null) {
                world.onChunkUnload(event.getChunk().getPos());
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ItemPhysicsWorld world = ((VeloxLevel) level).velox$physics();
            if (world != null) {
                world.close();
            }
        }
    }

    /** Classifications use block tags (fall_damage_resetting): redo them after /reload. */
    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (!fromServerDataLoad(event)) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            ItemPhysicsWorld world = ((VeloxLevel) level).velox$physics();
            if (world != null) {
                world.onTagsChanged();
            }
        }
    }

    /**
     * Up to 26.2 the cause is {@code getUpdateCause()}; from NeoForge 26.3 it is the
     * event's subclass ({@code TagsUpdatedEvent.ServerDataLoad}), and the method is
     * gone. Only the branch for the running version is ever linked.
     */
    private static boolean fromServerDataLoad(TagsUpdatedEvent event) {
        if (event.getClass() != TagsUpdatedEvent.class) {
            return event.getClass().getSimpleName().equals("ServerDataLoad");
        }
        return event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ItemPhysicsWorld world = ((VeloxLevel) level).velox$physics();
            if (world != null) {
                world.close();
            }
        }
        available = null;
    }
}
