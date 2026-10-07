package com.panzer.mods.velox.physics;

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
}
