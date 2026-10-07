package com.panzer.mods.velox.physics;

/** Velox's state attached to a {@code ServerLevel} (added by {@code ServerLevelMixin}). */
public interface VeloxLevel {

    ItemPhysicsWorld velox$physics();

    void velox$setPhysics(ItemPhysicsWorld physics);
}
