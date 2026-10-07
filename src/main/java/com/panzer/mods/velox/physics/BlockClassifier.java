package com.panzer.mods.velox.physics;

import com.panzer.mods.celeris.physics.CellClass;
import com.panzer.mods.celeris.physics.TerrainView;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * Maps block states to Celeris {@link CellClass}es. The engine simulates
 * exactly two kinds of block: empty space with no side effects, and plain
 * full cubes (by friction). Everything else is {@link CellClass#COMPLEX}, so
 * an item touching it runs vanilla code that tick:
 *
 * <ul>
 *   <li>fluids, partial or dynamic collision shapes, shapes taller than their
 *       cell (fences, walls -- whose neighbours the sampler also taints);</li>
 *   <li>blocks that reset fall distance (vines, cobwebs, scaffolding...):
 *       {@code Entity.move} ray-casts for them on fast moves;</li>
 *   <li>blocks whose class overrides an entity-interaction hook ({@code
 *       stepOn}, {@code fallOn}, {@code updateEntityAfterFallOn} (later {@code
 *       updateEntityMovementAfterFallOn}), {@code
 *       entityInside}, NeoForge's per-entity {@code getFriction}) or has a
 *       speed factor: slime, honey, magma, hay, pressure plates, cobwebs,
 *       portals, fire...</li>
 * </ul>
 *
 * Results are cached per block state id; friction classes are registered in
 * this level's {@link TerrainView}.
 */
final class BlockClassifier {

    private static final Set<String> ENTITY_HOOKS = Set.of(
            "stepOn", "fallOn", "updateEntityAfterFallOn", "updateEntityMovementAfterFallOn", "entityInside",
            "getFriction", "getSpeedFactor");

    /** Whether a block class (below Block/BlockBehaviour) overrides any entity hook. Names are Mojang's, as at runtime. */
    private static final ClassValue<Boolean> OVERRIDES_HOOKS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Block.class && c != BlockBehaviour.class && c != Object.class;
                 c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (ENTITY_HOOKS.contains(m.getName()) && !m.isSynthetic()) {
                        return true;
                    }
                }
            }
            return false;
        }
    };

    private static final short UNKNOWN = -1;

    private final TerrainView terrain;
    private short[] cache = new short[0];

    BlockClassifier(TerrainView terrain) {
        this.terrain = terrain;
    }

    /** Forgets every classification (block tags changed). */
    void clear() {
        cache = new short[0];
    }

    int classify(BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        if (id < 0) {
            return compute(state);
        }
        if (id >= cache.length) {
            int size = Math.max(id + 1, Block.BLOCK_STATE_REGISTRY.size());
            int old = cache.length;
            cache = Arrays.copyOf(cache, size);
            Arrays.fill(cache, old, size, UNKNOWN);
        }
        short c = cache[id];
        if (c == UNKNOWN) {
            c = (short) compute(state);
            cache[id] = c;
        }
        return c;
    }

    private int compute(BlockState state) {
        if (state.isAir()) {
            return CellClass.AIR;
        }
        if (!state.getFluidState().isEmpty() || state.hasLargeCollisionShape()
                || state.is(BlockTags.FALL_DAMAGE_RESETTING)) {
            return CellClass.COMPLEX;
        }
        Block block = state.getBlock();
        if (block.hasDynamicShape() || block.getSpeedFactor() != 1.0F || OVERRIDES_HOOKS.get(block.getClass())) {
            return CellClass.COMPLEX;
        }
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return terrain.solidClass(block.getFriction());
        }
        if (state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            return CellClass.AIR;
        }
        return CellClass.COMPLEX;
    }
}
