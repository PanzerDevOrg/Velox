package com.panzer.mods.velox.physics;

import com.panzer.mods.celeris.physics.BodyBatch;
import com.panzer.mods.celeris.physics.CelerisPhysics;
import com.panzer.mods.celeris.physics.CellClass;
import com.panzer.mods.celeris.physics.EntityBodyBridge;
import com.panzer.mods.celeris.physics.PhysicsMode;
import com.panzer.mods.celeris.physics.TerrainView;
import com.panzer.mods.velox.Velox;
import com.panzer.mods.velox.config.VeloxConfig;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Item physics of one {@link ServerLevel}: a Celeris body batch, the terrain
 * snapshot it collides against, and the bridge that keeps both in sync with
 * the {@link ItemEntity} objects.
 *
 * <p>Per tick ({@link #tick}, called by {@code ServerLevelMixin} right before
 * the level ticks its entities):
 * <ol>
 *   <li>bodies whose entity did not apply last step's result (not ticked,
 *       deferred, tick cancelled) are re-read from the entity;</li>
 *   <li>cells around collidable entities (boats, shulkers, minecarts) are
 *       masked COMPLEX for the step, so items touching them run vanilla;</li>
 *   <li>Celeris steps the whole batch (native SIMD kernel, worker threads);</li>
 *   <li>sections that deferred bodies need are copied into the snapshot,
 *       within a per-tick budget.</li>
 * </ol>
 * Each entity then applies its own result inside its tick.
 */
public final class ItemPhysicsWorld implements AutoCloseable {

    private static final int RECENTER_INTERVAL = 200;

    private final ServerLevel level;
    private final TerrainView terrain;
    private final TerrainSampler sampler;
    private final EntityBodyBridge<ItemEntity> bridge;
    private final BodyBatch batch;
    private final ItemEntity[] bySlot;
    private final boolean[] applied;
    private final LongOpenHashSet loadedSections = new LongOpenHashSet();
    private final LongOpenHashSet pendingSet = new LongOpenHashSet();
    private final LongArrayFIFOQueue pending = new LongArrayFIFOQueue();
    private final List<Entity> blockers = new ArrayList<>();
    private int[] maskedCells = new int[3 * 256];
    private byte[] maskedOld = new byte[256];
    private int maskedCount;
    private final int sectionBudget;
    private long step;
    /** This step's results may still be applied: between the step and the end of the entity ticks. */
    private boolean resultsLive;
    /** Furthest a body's swept box reaches from it (kernel limit: 512 cells), so the furthest a block change can matter. */
    private static final double RESULT_REACH = 9.0;
    private boolean windowPlaced;
    private boolean closed;

    private ItemPhysicsWorld(ServerLevel level) {
        this.level = level;
        int width = VeloxConfig.WINDOW_SECTIONS.get();
        this.terrain = CelerisPhysics.newTerrain(width, level.getSectionsCount(), width);
        this.sampler = new TerrainSampler(level, terrain);
        this.batch = CelerisPhysics.newBatch(VeloxConfig.MAX_ITEMS_PER_LEVEL.get(), PhysicsMode.VANILLA, 0);
        this.bridge = new EntityBodyBridge<>(batch, new ItemBodyAccessor(this));
        this.bySlot = new ItemEntity[batch.capacity()];
        this.applied = new boolean[batch.capacity()];
        this.sectionBudget = VeloxConfig.SECTION_FILLS_PER_TICK.get();
    }

    /** The level's physics, created on first use; null when Velox or the Celeris engine is off. */
    public static ItemPhysicsWorld of(ServerLevel level) {
        VeloxLevel holder = (VeloxLevel) level;
        ItemPhysicsWorld world = holder.velox$physics();
        if (world == null && VeloxEvents.engineAvailable()) {
            world = new ItemPhysicsWorld(level);
            holder.velox$setPhysics(world);
            Velox.LOGGER.info("Velox: item physics for {} on {} (capacity {})",
                    McCompat.dimensionId(level), world.batch.engineName(), world.batch.capacity());
        }
        return world;
    }

    public BodyBatch batch() {
        return batch;
    }

    /** Id of the current step; entities stamped with it apply the batch's result. */
    public long step() {
        return step;
    }

    public boolean register(ItemEntity entity) {
        if (closed) {
            return false;
        }
        VeloxBody body = (VeloxBody) entity;
        body.velox$setOwner(this);
        if (!bridge.register(entity)) {
            body.velox$setOwner(null);
            return false;
        }
        if (!windowPlaced) {
            placeWindow(entity.getX(), entity.getZ());
        }
        return true;
    }

    public void unregister(ItemEntity entity) {
        if (!closed) {
            bridge.unregister(entity);
        }
        ((VeloxBody) entity).velox$setOwner(null);
    }

    public void markDirty(ItemEntity entity) {
        if (!closed) {
            bridge.markDirty(entity);
        }
    }

    /** Called by the entity when it applied this step's result in its tick. */
    public void markApplied(int slot) {
        applied[slot] = true;
    }

    void slotMoved(ItemEntity entity, int from, int to) {
        if (to >= 0) {
            bySlot[to] = entity;
            applied[to] = false;
        }
    }

    public void addBlocker(Entity entity) {
        blockers.add(entity);
    }

    public void removeBlocker(Entity entity) {
        blockers.remove(entity);
    }

    public void tick() {
        int n = batch.size();
        if (closed || n == 0) {
            return;
        }
        if (step % RECENTER_INTERVAL == 0) {
            maybeRecenter();
        }
        // Entities that did not apply the previous result have state the batch
        // does not know about (or the batch moved on without them): re-read.
        for (int slot = 0; slot < n; slot++) {
            if (!applied[slot]) {
                bridge.markDirty(bySlot[slot]);
            }
        }
        Arrays.fill(applied, 0, n, false);

        step++;
        maskBlockers();
        int deferred;
        try {
            deferred = bridge.tick(terrain, McCompat.MOVE_RULES);
            resultsLive = true;
        } finally {
            unmaskBlockers();
        }
        requestSections(deferred);
        fillSections();
    }

    // ------------------------------------------------------------------ terrain

    boolean isSectionLoaded(int sx, int sy, int sz) {
        return loadedSections.contains(SectionPos.asLong(sx, sy, sz));
    }

    public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!closed && isSectionLoaded(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4)) {
            sampler.blockChanged(pos.getX(), pos.getY(), pos.getZ(), oldState, newState, this);
            if (resultsLive) {
                invalidateNear(pos);
            }
        }
    }

    /** The level finished ticking its entities: every result of this step has been applied or dropped. */
    public void entitiesTicked() {
        resultsLive = false;
    }

    /**
     * A block changed while entities tick (a falling block landing, an explosion,
     * a mob placing a block): items near it that tick later this tick must not
     * apply a result worked out against the old terrain. They run vanilla this
     * tick, and the next step reads them again. Rare, so a plain entity query.
     */
    private void invalidateNear(BlockPos pos) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        AABB around = new AABB(x - RESULT_REACH, y - RESULT_REACH, z - RESULT_REACH,
                x + 1 + RESULT_REACH, y + 1 + RESULT_REACH, z + 1 + RESULT_REACH);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around,
                e -> ((VeloxBody) e).velox$owner() == this)) {
            ((VeloxBody) item).velox$onExternalWrite();
        }
    }

    public void onChunkUnload(ChunkPos pos) {
        if (closed) {
            return;
        }
        sampler.forgetChunks();
        int cx = McCompat.chunkX(pos), cz = McCompat.chunkZ(pos);
        int minSection = McCompat.minSectionY(level);
        for (int sy = minSection; sy < minSection + level.getSectionsCount(); sy++) {
            if (loadedSections.remove(SectionPos.asLong(cx, sy, cz))) {
                terrain.clearSection(cx, sy, cz);
            }
        }
    }

    /**
     * Block tags were reloaded: classifications may have changed, so every
     * section is classified again as bodies need it.
     */
    public void onTagsChanged() {
        if (closed) {
            return;
        }
        sampler.reclassify();
        for (LongIterator it = loadedSections.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            terrain.clearSection(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
        }
        loadedSections.clear();
        pendingSet.clear();
        pending.clear();
    }

    /** Sections a deferred body may need next step: its box, grown by its velocity and a block of margin. */
    private void requestSections(int deferred) {
        for (int k = 0; k < deferred; k++) {
            int slot = batch.deferred(k);
            if (slot >= batch.size()) {
                continue;
            }
            double x = batch.x(slot), y = batch.y(slot), z = batch.z(slot);
            double rx = 1.5 + Math.abs(batch.vx(slot));
            double ry = 1.5 + Math.abs(batch.vy(slot));
            double rz = 1.5 + Math.abs(batch.vz(slot));
            int x0 = (int) Math.floor(x - rx) >> 4, x1 = (int) Math.floor(x + rx) >> 4;
            int y0 = (int) Math.floor(y - ry) >> 4, y1 = (int) Math.floor(y + 2 + ry) >> 4;
            int z0 = (int) Math.floor(z - rz) >> 4, z1 = (int) Math.floor(z + rz) >> 4;
            for (int sy = y0; sy <= y1; sy++) {
                for (int sz = z0; sz <= z1; sz++) {
                    for (int sx = x0; sx <= x1; sx++) {
                        long key = SectionPos.asLong(sx, sy, sz);
                        if (terrain.covers(sx, sy, sz) && !loadedSections.contains(key) && pendingSet.add(key)) {
                            pending.enqueue(key);
                        }
                    }
                }
            }
        }
    }

    private void fillSections() {
        sampler.forgetChunks();
        for (int i = 0; i < sectionBudget && !pending.isEmpty(); i++) {
            long key = pending.dequeueLong();
            pendingSet.remove(key);
            int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
            if (terrain.covers(sx, sy, sz) && sampler.fillSection(sx, sy, sz)) {
                loadedSections.add(key);
            }
        }
    }

    private void placeWindow(double x, double z) {
        int half = terrain.sizeX() / 2;
        terrain.setOrigin(SectionPos.blockToSectionCoord(x) - half, McCompat.minSectionY(level),
                SectionPos.blockToSectionCoord(z) - half);
        loadedSections.clear();
        pendingSet.clear();
        pending.clear();
        windowPlaced = true;
    }

    /** Moves the window onto the items when a quarter of them have drifted outside it. */
    private void maybeRecenter() {
        int n = batch.size();
        int outside = 0;
        double sumX = 0, sumZ = 0;
        for (int slot = 0; slot < n; slot++) {
            double x = batch.x(slot), z = batch.z(slot);
            sumX += x;
            sumZ += z;
            if (!terrain.covers(SectionPos.blockToSectionCoord(x), terrain.originY(), SectionPos.blockToSectionCoord(z))) {
                outside++;
            }
        }
        if (outside * 4 > n) {
            placeWindow(sumX / n, sumZ / n);
        }
    }

    // ----------------------------------------------------------------- blockers

    /** Vanilla also collides items with boats, shulkers and the like; Celeris does not model them. */
    private void maskBlockers() {
        maskedCount = 0;
        for (int b = 0; b < blockers.size(); b++) {
            Entity e = blockers.get(b);
            if (e.isRemoved() || !McCompat.blocksItems(e)) {
                continue;
            }
            AABB box = e.getBoundingBox().inflate(1.0);
            int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX);
            int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY);
            int z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        int old = terrain.cell(x, y, z);
                        if (old == CellClass.COMPLEX) {
                            continue;
                        }
                        if (maskedCount == maskedOld.length) {
                            maskedOld = Arrays.copyOf(maskedOld, maskedCount * 2);
                            maskedCells = Arrays.copyOf(maskedCells, maskedCount * 6);
                        }
                        maskedCells[3 * maskedCount] = x;
                        maskedCells[3 * maskedCount + 1] = y;
                        maskedCells[3 * maskedCount + 2] = z;
                        maskedOld[maskedCount++] = (byte) old;
                        terrain.setCell(x, y, z, CellClass.COMPLEX);
                    }
                }
            }
        }
    }

    private void unmaskBlockers() {
        for (int i = maskedCount - 1; i >= 0; i--) {
            terrain.setCell(maskedCells[3 * i], maskedCells[3 * i + 1], maskedCells[3 * i + 2], maskedOld[i] & 0xFF);
        }
        maskedCount = 0;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int slot = 0; slot < batch.size(); slot++) {
            VeloxBody body = (VeloxBody) bySlot[slot];
            body.velox$setSlot(-1);
            body.velox$setOwner(null);
        }
        bridge.close();
        terrain.close();
        ((VeloxLevel) level).velox$setPhysics(null);
    }
}
