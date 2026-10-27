package net.bichal.mods.velox.physics;

/**
 * Per-entity state Velox keeps on {@code ItemEntity} (added by
 * {@code ItemEntityMixin}). The slot lives on the entity, so the bridge never
 * needs a map lookup.
 */
public interface VeloxBody {

    int velox$slot();

    void velox$setSlot(int slot);

    ItemPhysicsWorld velox$owner();

    void velox$setOwner(ItemPhysicsWorld owner);

    /** Step of {@link ItemPhysicsWorld} whose result this entity should apply in its tick. */
    void velox$setSimulatedStep(long step);

    /** This tick's physics came from the batch (reset at the start of every tick). */
    boolean velox$appliedThisTick();

    /** Position or velocity was written by code other than Velox. */
    void velox$onExternalWrite();

    /**
     * The kernel simulated this entity in the current step and nothing has
     * invalidated the result since: its box, before and after this tick's move,
     * holds no fluid and no unmodelled block.
     */
    boolean velox$simulatedThisTick();

    /**
     * What {@code updateInWaterStateAndDoFluidPushing()} does when no fluid
     * touches the box: no fluid heights, not touching water, no push.
     */
    boolean velox$noFluid();

    /**
     * {@code Level.findSupportingBlock(this, box)} while Velox applies this tick's
     * result (the call {@code checkSupportingBlock} makes from it), answered from
     * the terrain snapshot the kernel used; {@code null} when Velox is not
     * applying or the snapshot cannot answer (then the level does).
     */
    java.util.Optional<net.minecraft.core.BlockPos> velox$knownSupport(net.minecraft.world.phys.AABB box);

    /**
     * {@code checkInsideBlocks(movements, collector)} for an item that applied
     * this tick's result would find nothing: every block its box passes through
     * is air or a plain cube (no {@code entityInside}, no fluid), per the terrain
     * snapshot the kernel used. Then neither the walk nor the collector's
     * {@code applyAndClear} has anything to do.
     */
    boolean velox$insideBlocksInert(java.util.List<?> movements);
}
