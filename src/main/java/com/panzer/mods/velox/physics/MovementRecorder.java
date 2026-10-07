package com.panzer.mods.velox.physics;

import com.panzer.mods.velox.Velox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Records a movement the way {@code Entity.move} does on the versions that
 * have {@code Entity.addMovementThisTick}: {@code Movement(from, to, requested)}
 * from 1.21.10, {@code Movement(from, to, true)} on 1.21.6 - 1.21.9.
 * {@code applyEffectsFromBlocks} walks those movements, axis by axis, to find
 * the blocks the entity went through, and on 26.1 resting items replay them.
 *
 * <p>{@code Entity.Movement} is not public, so this goes through method
 * handles. If they cannot be resolved nothing is recorded and
 * {@code applyEffectsFromBlocks} falls back to the straight path from
 * {@code oldPosition()}, which crosses the same air and full-cube cells
 * (the only ones Celeris simulates).
 */
public final class MovementRecorder {

    private static final MethodHandle NEW_MOVEMENT;
    private static final MethodHandle ADD_MOVEMENT;

    static {
        MethodHandle create = null;
        MethodHandle add = null;
        try {
            Class<?> movement = Class.forName("net.minecraft.world.entity.Entity$Movement", false,
                    Entity.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup());
            try {
                create = lookup.findConstructor(movement, MethodType.methodType(void.class, Vec3.class, Vec3.class, Vec3.class))
                        .asType(MethodType.methodType(Object.class, Vec3.class, Vec3.class, Vec3.class));
            } catch (NoSuchMethodException e) {
                // 1.21.6 - 1.21.9: Movement(from, to, axisIndependant), always true from move().
                MethodHandle axisIndependent = lookup.findConstructor(movement,
                        MethodType.methodType(void.class, Vec3.class, Vec3.class, boolean.class));
                create = MethodHandles.dropArguments(MethodHandles.insertArguments(axisIndependent, 2, true), 2, Vec3.class)
                        .asType(MethodType.methodType(Object.class, Vec3.class, Vec3.class, Vec3.class));
            }
            add = lookup.findVirtual(Entity.class, "addMovementThisTick", MethodType.methodType(void.class, movement))
                    .asType(MethodType.methodType(void.class, Entity.class, Object.class));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Before 1.21.6 there is nothing to record into (1.21.5 keeps per-axis lists of its own).
            Velox.LOGGER.debug("Velox: entity movements not recorded ({}); block effects use the straight path", e.toString());
            create = null;
            add = null;
        }
        NEW_MOVEMENT = create;
        ADD_MOVEMENT = add;
    }

    private MovementRecorder() {
    }

    /** {@code entity.addMovementThisTick(new Entity.Movement(from, to, requested))}. */
    public static void record(Entity entity, Vec3 from, Vec3 to, Vec3 requested) {
        if (ADD_MOVEMENT == null) {
            return;
        }
        try {
            Object movement = (Object) NEW_MOVEMENT.invokeExact(from, to, requested);
            ADD_MOVEMENT.invokeExact(entity, movement);
        } catch (Throwable t) {
            throw new IllegalStateException("Velox: recording a movement failed", t);
        }
    }
}
