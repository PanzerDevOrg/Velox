package com.panzer.mods.velox.physics;

import com.panzer.mods.celeris.physics.CellClass;
import com.panzer.mods.celeris.physics.TerrainView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Copies chunk sections of a level into its {@link TerrainView}, one 4 KiB
 * page each, and keeps single cells current on block changes. Never loads
 * chunks: sections of unloaded chunks stay unloaded (COMPLEX) in the view.
 */
final class TerrainSampler {

    private static final BlockState VOID = Blocks.VOID_AIR.defaultBlockState();

    private final ServerLevel level;
    private final TerrainView terrain;
    private final BlockClassifier classifier;
    private final byte[] cells = new byte[TerrainView.SECTION_BYTES];
    private final boolean[] large = new boolean[TerrainView.SECTION_BYTES];
    private LevelChunk lastChunk;

    TerrainSampler(ServerLevel level, TerrainView terrain) {
        this.level = level;
        this.terrain = terrain;
        this.classifier = new BlockClassifier(terrain);
    }

    /** Copies one section into the view. Returns false if its chunk is not loaded. */
    boolean fillSection(int sx, int sy, int sz) {
        LevelChunk chunk = chunk(sx, sz);
        if (chunk == null) {
            return false;
        }
        int index = level.getSectionIndexFromSectionY(sy);
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length) {
            return false;
        }
        LevelChunkSection section = sections[index];
        boolean anyLarge = false;
        if (section.hasOnlyAir()) {
            java.util.Arrays.fill(cells, (byte) CellClass.AIR);
            java.util.Arrays.fill(large, false);
        } else {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int i = (y << 8) | (z << 4) | x;
                        BlockState s = section.getBlockState(x, y, z);
                        cells[i] = (byte) classifier.classify(s);
                        boolean l = s.hasLargeCollisionShape();
                        large[i] = l;
                        anyLarge |= l;
                    }
                }
            }
        }
        int bx = sx << 4, by = sy << 4, bz = sz << 4;
        // Shapes taller than their cell (fences, walls) reach into neighbours:
        // taint every cell next to one, inside this section...
        if (anyLarge) {
            for (int i = 0; i < cells.length; i++) {
                if (large[i]) {
                    taintAround(i & 15, i >> 8, (i >> 4) & 15);
                }
            }
        }
        // ...and from the one-block shell of neighbouring sections.
        for (int y = -1; y <= 16; y++) {
            for (int z = -1; z <= 16; z++) {
                for (int x = -1; x <= 16; x++) {
                    if (x >= 0 && x < 16 && y >= 0 && y < 16 && z >= 0 && z < 16) {
                        x = 15; // skip the interior of this row
                        continue;
                    }
                    BlockState s = stateAt(bx + x, by + y, bz + z);
                    if (s == null || s.hasLargeCollisionShape()) {
                        taintAround(x, y, z);
                    }
                }
            }
        }
        // The world border collides like a wall; keep items near it on vanilla code.
        WorldBorder border = level.getWorldBorder();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = bx + x, wz = bz + z;
                if (wx < border.getMinX() + 1 || wx + 1 > border.getMaxX() - 1
                        || wz < border.getMinZ() + 1 || wz + 1 > border.getMaxZ() - 1) {
                    for (int y = 0; y < 16; y++) {
                        cells[(y << 8) | (z << 4) | x] = (byte) CellClass.COMPLEX;
                    }
                }
            }
        }
        terrain.setSection(sx, sy, sz, cells, 0);
        return true;
    }

    /** Recomputes the cells a block change can affect (the block, plus its neighbours if a large shape is involved). */
    void blockChanged(int x, int y, int z, BlockState oldState, BlockState newState, ItemPhysicsWorld world) {
        if (oldState.hasLargeCollisionShape() || newState.hasLargeCollisionShape()) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        refreshCell(x + dx, y + dy, z + dz, world);
                    }
                }
            }
        } else {
            refreshCell(x, y, z, world);
        }
    }

    private void refreshCell(int x, int y, int z, ItemPhysicsWorld world) {
        if (world.isSectionLoaded(x >> 4, y >> 4, z >> 4)) {
            terrain.setCell(x, y, z, cellAt(x, y, z));
        }
    }

    /** Full rule for one cell: its own class, tainted by any large shape or unloaded block around it. */
    private int cellAt(int x, int y, int z) {
        BlockState s = stateAt(x, y, z);
        if (s == null) {
            return CellClass.COMPLEX;
        }
        int c = classifier.classify(s);
        if (c == CellClass.COMPLEX) {
            return c;
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx | dy | dz) != 0) {
                        BlockState n = stateAt(x + dx, y + dy, z + dz);
                        if (n == null || n.hasLargeCollisionShape()) {
                            return CellClass.COMPLEX;
                        }
                    }
                }
            }
        }
        WorldBorder border = level.getWorldBorder();
        if (x < border.getMinX() + 1 || x + 1 > border.getMaxX() - 1 || z < border.getMinZ() + 1 || z + 1 > border.getMaxZ() - 1) {
            return CellClass.COMPLEX;
        }
        return c;
    }

    /** Marks the in-section cells adjacent to local (x, y, z) -- which may lie outside the section -- COMPLEX. */
    private void taintAround(int x, int y, int z) {
        for (int dy = -1; dy <= 1; dy++) {
            int ty = y + dy;
            if (ty < 0 || ty > 15) {
                continue;
            }
            for (int dz = -1; dz <= 1; dz++) {
                int tz = z + dz;
                if (tz < 0 || tz > 15) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    int tx = x + dx;
                    if (tx >= 0 && tx <= 15) {
                        cells[(ty << 8) | (tz << 4) | tx] = (byte) CellClass.COMPLEX;
                    }
                }
            }
        }
    }

    /** Block state without loading anything; null when the chunk is not loaded. */
    private BlockState stateAt(int x, int y, int z) {
        int minY = McCompat.minY(level);
        if (y < minY || y >= minY + level.getHeight()) {
            return VOID;
        }
        LevelChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) {
            return null;
        }
        LevelChunkSection section = chunk.getSections()[level.getSectionIndex(y)];
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    private LevelChunk chunk(int cx, int cz) {
        LevelChunk c = lastChunk;
        if (c != null && McCompat.chunkX(c.getPos()) == cx && McCompat.chunkZ(c.getPos()) == cz) {
            return c;
        }
        c = level.getChunkSource().getChunkNow(cx, cz);
        lastChunk = c;
        return c;
    }

    /** Forgets every block classification (block tags changed). */
    void reclassify() {
        classifier.clear();
    }

    /** Drops the cached chunk reference (it may have been unloaded). */
    void forgetChunks() {
        lastChunk = null;
    }
}
