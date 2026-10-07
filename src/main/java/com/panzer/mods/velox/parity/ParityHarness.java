package com.panzer.mods.velox.parity;

import com.panzer.mods.velox.Velox;
import com.panzer.mods.velox.physics.VeloxBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bit-exact check of Velox against vanilla, run on a headless server in CI
 * ({@code -Dvelox.parity=true}, Gradle task {@code parity}).
 *
 * <p>The same course is built twice, {@link #OFFSET} blocks apart on x, and
 * the same items are thrown into both: region A is simulated by Celeris,
 * region B's items are {@link #isControl controls} and stay vanilla. Every tick each
 * pair must agree bit for bit: position (A's x plus the offset), velocity,
 * ground and collision flags, fall distance and fire ticks. Both regions lie
 * in x [512, 1024), one binade, so the translation is exact in double
 * arithmetic and vanilla itself gives identical bits in both. Twins get entity
 * ids equal mod 4 (ItemEntity's resting rule uses (tickCount + id) % 4) and
 * never merge or get picked up.
 *
 * <p>Writes {@code velox-parity.txt} in the game directory and stops the server.
 */
public final class ParityHarness {

    public static final boolean ENABLED = Boolean.getBoolean("velox.parity");

    static final int OFFSET = 256;
    private static final int OX = 528, OY = 96, OZ = 528, SIZE = 64;
    private static final int WARMUP = 40, TICKS = 400, GIVE_UP = 1200, MAX_REPORTED = 40;
    private static final int NEVER_PICKUP = 32767;

    private static final int HISTORY = 4;

    /** {@code history}: the twins' last {@link #HISTORY} states, A and B interleaved, by tick. */
    private record Pair(String label, ItemEntity a, ItemEntity b, Snap[] history) {
    }

    /** One twin after a tick, for the trajectory printed with a divergence. */
    private record Snap(int tick, double x, double y, double z, double vx, double vy, double vz,
                        boolean ground, boolean viaCeleris) {

        static Snap of(int tick, ItemEntity e, int offset, boolean viaCeleris) {
            Vec3 v = e.getDeltaMovement();
            return new Snap(tick, e.getX() - OX - offset, e.getY() - OY, e.getZ() - OZ, v.x, v.y, v.z,
                    e.onGround(), viaCeleris);
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "t%d (%.6f, %.6f, %.6f) v (%.6f, %.6f, %.6f)%s%s",
                    tick, x, y, z, vx, vy, vz, ground ? " ground" : "", viaCeleris ? " C" : "");
        }
    }

    private static final List<Pair> PAIRS = new ArrayList<>();
    private static final java.util.Set<Entity> CONTROLS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final List<String> DIVERGENCES = new ArrayList<>();
    private static final java.util.Set<Pair> DIVERGED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static int firstInCeleris, firstInVanilla;
    private static final Random RANDOM = new Random(0x5E10C17L);
    private static int serverTicks = -1;
    private static int testTick = -1;
    private static long compared, simulated, divergent;
    private static boolean done;

    private ParityHarness() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> {
            Velox.LOGGER.info("Velox parity: preparing the course");
            ServerLevel level = e.getServer().overworld();
            for (int cx = (OX >> 4) - 1; cx <= (OX + OFFSET + SIZE) >> 4; cx++) {
                for (int cz = (OZ >> 4) - 1; cz <= (OZ + SIZE) >> 4; cz++) {
                    level.setChunkForced(cx, cz, true);
                }
            }
            serverTicks = 0;
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
    }

    /** Region B's items: never handed to Celeris. */
    public static boolean isControl(Entity entity) {
        return ENABLED && CONTROLS.contains(entity);
    }

    private static void tick(MinecraftServer server) {
        if (serverTicks < 0 || done) {
            return;
        }
        serverTicks++;
        ServerLevel level = server.overworld();
        if (serverTicks == WARMUP) {
            buildCourse(level);
            testTick = 0;
            return;
        }
        if (testTick < 0) {
            return;
        }
        if (testTick > 0) {
            compare(testTick);
        }
        switch (testTick) {
            case 2 -> firstWave(level);
            case 80 -> apexWave(level);
            case 120 -> {
                // Pull the floor out from under resting items...
                for (int x = 30; x <= 33; x++) {
                    for (int z = 50; z <= 53; z++) {
                        set(level, x, 0, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            case 160 -> {
                // ...and put a wall in the way of sliding ones.
                for (int x = 0; x < SIZE; x++) {
                    set(level, x, 1, 60, Blocks.STONE.defaultBlockState());
                    set(level, x, 2, 60, Blocks.STONE.defaultBlockState());
                }
            }
            case 200 -> wallWave(level);
            default -> {
            }
        }
        testTick++;
        if (testTick > TICKS || serverTicks > GIVE_UP) {
            finish(server);
        }
    }

    // ------------------------------------------------------------------ course

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(OX + x, OY + y, OZ + z), state, 3);
        level.setBlock(new BlockPos(OX + OFFSET + x, OY + y, OZ + z), state, 3);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    set(level, x, y, z, state);
                }
            }
        }
    }

    /** Full cubes of several frictions, the shapes Celeris hands back to vanilla, steps, a pit, walls and a roof. */
    private static void buildCourse(ServerLevel level) {
        fill(level, 0, 0, 0, SIZE - 1, 0, SIZE - 1, Blocks.STONE.defaultBlockState());
        fill(level, 8, 0, 8, 15, 0, 15, Blocks.ICE.defaultBlockState());
        fill(level, 16, 0, 8, 23, 0, 15, Blocks.PACKED_ICE.defaultBlockState());
        fill(level, 24, 0, 8, 31, 0, 15, Blocks.BLUE_ICE.defaultBlockState());
        fill(level, 32, 0, 8, 39, 0, 15, Blocks.SLIME_BLOCK.defaultBlockState());
        fill(level, 40, 0, 8, 47, 0, 15, Blocks.SOUL_SAND.defaultBlockState());
        fill(level, 48, 0, 8, 55, 0, 15, Blocks.HONEY_BLOCK.defaultBlockState());
        fill(level, 8, -3, 24, 13, 0, 29, Blocks.AIR.defaultBlockState());
        fill(level, 8, -4, 24, 13, -4, 29, Blocks.STONE.defaultBlockState());
        for (int step = 0; step < 4; step++) {
            fill(level, 20 + 2 * step, 1, 24, 21 + 2 * step, 1 + step, 31, Blocks.STONE.defaultBlockState());
        }
        fill(level, 36, 1, 20, 37, 3, 44, Blocks.STONE.defaultBlockState());
        fill(level, 44, 1, 24, 52, 1, 24, Blocks.OAK_FENCE.defaultBlockState());
        fill(level, 44, 1, 30, 52, 1, 31, Blocks.STONE_SLAB.defaultBlockState());
        fill(level, 56, 1, 40, 57, 6, 41, Blocks.GLASS.defaultBlockState());
        fill(level, 54, 7, 38, 60, 7, 44, Blocks.GLASS.defaultBlockState());
        fill(level, 10, 1, 40, 12, 1, 42, Blocks.COBWEB.defaultBlockState());
    }

    // ------------------------------------------------------------------ items

    private static double between(double lo, double hi) {
        return lo + (hi - lo) * RANDOM.nextDouble();
    }

    /** Twins with ids equal mod 4, B tagged as control, neither ever merged or picked up. */
    private static void spawn(ServerLevel level, String label, double x, double y, double z, double vx, double vy, double vz) {
        ItemStack stack = new ItemStack(PAIRS.size() % 2 == 0 ? Items.STONE : Items.IRON_INGOT);
        ItemEntity a = new ItemEntity(level, OX + x, OY + y, OZ + z, stack.copy(), vx, vy, vz);
        ItemEntity b;
        do {
            b = new ItemEntity(level, OX + OFFSET + x, OY + y, OZ + z, stack.copy(), vx, vy, vz);
        } while (Math.floorMod(b.getId() - a.getId(), 4) != 0);
        // 32767: never picked up, and never merged (isMergable requires another delay).
        a.setPickUpDelay(NEVER_PICKUP);
        b.setPickUpDelay(NEVER_PICKUP);
        // Same random stream: an item pushed out of a block picks its direction at random.
        long seed = RANDOM.nextLong();
        a.getRandom().setSeed(seed);
        b.getRandom().setSeed(seed);
        CONTROLS.add(b);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        PAIRS.add(new Pair(label, a, b, new Snap[2 * HISTORY]));
    }

    private static void firstWave(ServerLevel level) {
        for (int i = 0; i < 240; i++) {
            spawn(level, "thrown#" + i, between(2, 62), between(1.0, 10.0), between(2, 62),
                    between(-0.35, 0.35), between(-0.3, 0.45), between(-0.35, 0.35));
        }
        for (int i = 0; i < 24; i++) {
            spawn(level, "resting#" + i, 2.5 + 2.5 * i, 1.0, 50.5 + (i % 4), 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 6; i++) {
            spawn(level, "embedded#" + i, 36.6 + 0.2 * i, 1.5, 30.5, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Requests barely above zero: vy = gravity +- a few 1e-4. Older versions
     * skip such a move (|moved|^2 <= 1e-7), 1.21.10+ apply it; Velox must pick
     * the rule of the version it runs on.
     */
    private static void apexWave(ServerLevel level) {
        for (int k = -4; k <= 4; k++) {
            spawn(level, "apex" + k, 3.5 + 6 * (k + 4), 1.5, 3.5, 0.0, 0.04 + 1.0E-4 * k, 0.0);
            spawn(level, "apex-drift" + k, 3.5 + 6 * (k + 4), 1.5, 5.5, 1.0E-4, 0.04 + 1.0E-4 * k, -1.0E-4);
        }
    }

    /** Shallow-angle slides into the wall at z = 60 and along the one at x = 36. */
    private static void wallWave(ServerLevel level) {
        for (int i = 0; i < 24; i++) {
            spawn(level, "wall#" + i, 2.5 + 2.5 * i, 1.0, 55.5, between(-0.05, 0.05), 0.0, between(0.2, 0.6));
            spawn(level, "side#" + i, between(30, 35.5), 1.0, 21 + i, between(0.2, 0.5), 0.0, between(-0.02, 0.02));
        }
    }

    // ------------------------------------------------------------------ check

    private static void compare(int tick) {
        for (Pair p : PAIRS) {
            ItemEntity a = p.a(), b = p.b();
            if (a.isRemoved() || b.isRemoved()) {
                if (a.isRemoved() != b.isRemoved()) {
                    diverged(tick, p, "removed " + a.isRemoved() + " vs " + b.isRemoved());
                }
                continue;
            }
            compared++;
            if (((VeloxBody) a).velox$appliedThisTick()) {
                simulated++;
            }
            String diff = difference(a, b);
            if (diff != null) {
                diverged(tick, p, diff);
            }
            if (!DIVERGED.contains(p)) {
                int k = 2 * (tick % HISTORY);
                p.history()[k] = Snap.of(tick, a, 0, ((VeloxBody) a).velox$appliedThisTick());
                p.history()[k + 1] = Snap.of(tick, b, OFFSET, false);
            }
        }
    }

    private static String difference(ItemEntity a, ItemEntity b) {
        StringBuilder out = new StringBuilder();
        same(out, "x", a.getX() + OFFSET, b.getX());
        same(out, "y", a.getY(), b.getY());
        same(out, "z", a.getZ(), b.getZ());
        same(out, "vx", a.getDeltaMovement().x, b.getDeltaMovement().x);
        same(out, "vy", a.getDeltaMovement().y, b.getDeltaMovement().y);
        same(out, "vz", a.getDeltaMovement().z, b.getDeltaMovement().z);
        same(out, "fallDistance", fallDistance(a), fallDistance(b));
        same(out, "onGround", a.onGround(), b.onGround());
        same(out, "horizontalCollision", a.horizontalCollision, b.horizontalCollision);
        same(out, "verticalCollision", a.verticalCollision, b.verticalCollision);
        same(out, "fireTicks", a.getRemainingFireTicks(), b.getRemainingFireTicks());
        same(out, "tickCount", a.tickCount, b.tickCount);
        return out.isEmpty() ? null : out.toString();
    }

    /** A float up to 1.21.4, a double after: read whatever this version has (the 1.21.1 build runs on both). */
    private static final java.lang.reflect.Field FALL_DISTANCE;

    static {
        try {
            FALL_DISTANCE = Entity.class.getDeclaredField("fallDistance");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double fallDistance(Entity e) {
        try {
            return ((Number) FALL_DISTANCE.get(e)).doubleValue();
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void same(StringBuilder out, String what, double a, double b) {
        if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)) {
            out.append(' ').append(what).append(' ').append(a).append(" vs ").append(b);
        }
    }

    private static void same(StringBuilder out, String what, Object a, Object b) {
        if (!a.equals(b)) {
            out.append(' ').append(what).append(' ').append(a).append(" vs ").append(b);
        }
    }

    /** Counts every divergent item-tick; reports the first one of each pair, with where it happened. */
    private static void diverged(int tick, Pair p, String what) {
        divergent++;
        if (!DIVERGED.add(p)) {
            return;
        }
        boolean viaCeleris = ((VeloxBody) p.a()).velox$appliedThisTick();
        if (viaCeleris) {
            firstInCeleris++;
        } else {
            firstInVanilla++;
        }
        if (DIVERGED.size() <= MAX_REPORTED) {
            DIVERGENCES.add("tick " + tick + " " + p.label() + (viaCeleris ? " (Celeris)" : " (vanilla)") + ":" + what
                    + " | A " + describe(p.a(), 0) + " | B " + describe(p.b(), OFFSET));
            // The ticks before, oldest first (C: that tick of A went through Celeris).
            for (int back = HISTORY - 1; back >= 1; back--) {
                int k = 2 * Math.floorMod(tick - back, HISTORY);
                Snap a = p.history()[k];
                if (a != null && a.tick() == tick - back) {
                    DIVERGENCES.add("      A " + a + " | B " + p.history()[k + 1]);
                }
            }
        }
    }

    /** Position relative to the course, velocity, ground, and the blocks at and half a block below the item. */
    private static String describe(ItemEntity e, int offset) {
        BlockPos at = e.blockPosition();
        BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.500001, e.getZ());
        return String.format(java.util.Locale.ROOT, "(%.6f, %.6f, %.6f) v (%.6f, %.6f, %.6f)%s in %s over %s",
                e.getX() - OX - offset, e.getY() - OY, e.getZ() - OZ,
                e.getDeltaMovement().x, e.getDeltaMovement().y, e.getDeltaMovement().z,
                e.onGround() ? " ground" : "", name(e.level().getBlockState(at)), name(e.level().getBlockState(below)));
    }

    private static String name(BlockState state) {
        String s = state.getBlock().toString();
        return s.startsWith("Block{") ? s.substring(6, s.length() - 1) : s;
    }

    private static void finish(MinecraftServer server) {
        done = true;
        boolean finished = testTick > TICKS;
        double share = compared == 0 ? 0.0 : (double) simulated / compared;
        // Most item-ticks must really go through Celeris, or the comparison proves nothing.
        boolean pass = finished && divergent == 0 && share >= 0.25;
        List<String> lines = new ArrayList<>();
        lines.add("velox parity " + server.getServerVersion() + ": " + (pass ? "PASS" : "FAIL"));
        lines.add("pairs " + PAIRS.size() + ", ticks " + Math.max(0, testTick - 1) + (finished ? "" : " (gave up: the course never started ticking)"));
        lines.add("item-ticks compared " + compared + ", simulated by Celeris " + simulated
                + String.format(" (%.1f%%, at least 25%% required)", 100 * share));
        lines.add("divergent item-ticks " + divergent + ", pairs " + DIVERGED.size()
                + " (first in a Celeris tick: " + firstInCeleris + ", in a vanilla tick: " + firstInVanilla + ")");
        DIVERGENCES.forEach(d -> lines.add("  " + d));
        lines.forEach(Velox.LOGGER::info);
        try {
            Files.write(Path.of("velox-parity.txt"), lines);
        } catch (IOException e) {
            Velox.LOGGER.error("Velox parity: cannot write the report", e);
        }
        server.halt(false);
    }
}
