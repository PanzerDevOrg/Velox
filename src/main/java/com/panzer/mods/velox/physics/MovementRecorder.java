package com.panzer.mods.velox.physics;

import com.panzer.mods.velox.Velox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

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
    /** {@code Movement.from()} and {@code to()}, as (Object) -> Vec3. */
    private static final MethodHandle FROM;
    private static final MethodHandle TO;

    static {
        MethodHandle create = null;
        MethodHandle add = null;
        MethodHandle from = null;
        MethodHandle to = null;
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
            MethodType accessor = MethodType.methodType(Vec3.class, Object.class);
            from = lookup.findVirtual(movement, "from", MethodType.methodType(Vec3.class)).asType(accessor);
            to = lookup.findVirtual(movement, "to", MethodType.methodType(Vec3.class)).asType(accessor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Before 1.21.6 there is nothing to record into (1.21.5 keeps per-axis lists of its own).
            Velox.LOGGER.debug("Velox: entity movements not recorded ({}); block effects use the straight path", e.toString());
            create = null;
            add = null;
            from = null;
            to = null;
        }
        NEW_MOVEMENT = create;
        ADD_MOVEMENT = add;
        FROM = from;
        TO = to;
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

    /**
     * The box hull of a list of {@code Entity.Movement}s, as {@code out = {minX,
     * minY, minZ, maxX, maxY, maxZ}} of their end points, for an entity whose box
     * is {@code width} wide and {@code height} tall around the position. Every box
     * {@code checkInsideBlocks} looks at along those movements (each axis step,
     * each swept segment) lies inside it. False when the movements cannot be read.
     */
    public static boolean hull(List<?> movements, double width, double height, double[] out) {
        if (FROM == null || movements.isEmpty()) {
            return false;
        }
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0;
        double x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        try {
            for (int i = 0, n = movements.size(); i < n; i++) {
                Object m = movements.get(i);
                for (int end = 0; end < 2; end++) {
                    Vec3 p = end == 0 ? (Vec3) FROM.invokeExact(m) : (Vec3) TO.invokeExact(m);
                    x0 = Math.min(x0, p.x);
                    y0 = Math.min(y0, p.y);
                    z0 = Math.min(z0, p.z);
                    x1 = Math.max(x1, p.x);
                    y1 = Math.max(y1, p.y);
                    z1 = Math.max(z1, p.z);
                }
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Velox: reading a movement failed", t);
        }
        double half = width / 2.0;
        out[0] = x0 - half;
        out[1] = y0;
        out[2] = z0 - half;
        out[3] = x1 + half;
        out[4] = y1 + height;
        out[5] = z1 + half;
        return true;
    }
}
