package com.panzer.mods.velox.physics;

import com.panzer.mods.celeris.physics.BodyBatch;
import com.panzer.mods.celeris.physics.BodyParams;
import com.panzer.mods.celeris.physics.EntityBodyBridge;
import com.panzer.mods.velox.mixin.EntityAccessor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Glue between {@link ItemEntity} and Celeris's {@link EntityBodyBridge}.
 *
 * <p>{@link #write} does not copy anything to the entity: it only stamps the
 * entity with the step whose result it must apply. The copy happens inside
 * the entity's own tick ({@code ItemEntityMixin}), at the exact point where
 * vanilla would run its physics, so the rest of {@code ItemEntity.tick} sees
 * the same state ordering as in vanilla. Deferred bodies need nothing here
 * either: unstamped entities simply run vanilla physics in their tick.
 */
final class ItemBodyAccessor implements EntityBodyBridge.Accessor<ItemEntity> {

    private final ItemPhysicsWorld world;

    ItemBodyAccessor(ItemPhysicsWorld world) {
        this.world = world;
    }

    @Override
    public int slot(ItemEntity entity) {
        return ((VeloxBody) entity).velox$slot();
    }

    @Override
    public void setSlot(ItemEntity entity, int slot) {
        VeloxBody body = (VeloxBody) entity;
        world.slotMoved(entity, body.velox$slot(), slot);
        body.velox$setSlot(slot);
    }

    @Override
    public BodyParams params(ItemEntity entity) {
        return McCompat.ITEM_PARAMS;
    }

    @Override
    public void read(ItemEntity entity, BodyBatch batch, int slot) {
        Vec3 v = entity.getDeltaMovement();
        batch.setDimensions(slot, entity.getBbWidth(), entity.getBbHeight());
        batch.setPosition(slot, entity.getX(), entity.getY(), entity.getZ());
        batch.setVelocity(slot, v.x, v.y, v.z);
        // ServerLevel increments tickCount right before entity.tick(), so the
        // throttle phase of the coming step is (tickCount + 1 + id) & 3.
        batch.setState(slot, entity.onGround(), ((EntityAccessor) entity).velox$onGroundNoBlocks(),
                (entity.tickCount + 1 + entity.getId()) & 3);
    }

    @Override
    public void write(ItemEntity entity, BodyBatch batch, int slot, int flags) {
        ((VeloxBody) entity).velox$setSimulatedStep(world.step());
    }

    @Override
    public void tickVanilla(ItemEntity entity) {
        // Nothing to do now: the entity was not stamped, so its own tick runs vanilla physics.
    }
}
