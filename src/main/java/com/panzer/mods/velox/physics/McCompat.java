package com.panzer.mods.velox.physics;

import com.panzer.mods.celeris.physics.PhysicsMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * The Minecraft API differences between the versions Velox is built for, in
 * one place (Stonecutter comments pick the branch per version).
 *
 * <p>The 1.21.1 build also runs on 1.21 - 1.21.6 (the versions Celeris's
 * 1.21.1 jar serves): what changed in between is detected once at startup
 * (methods and fields present) rather than assumed.
 */
public final class McCompat {

    //? <1.21.10 {
    /** 1.21.2+: block effects after the move ({@code applyEffectsFromBlocks}), small-move rule, getMinY. */
    private static final boolean EFFECTS_AFTER_MOVE = hasMethod(Entity.class, "applyEffectsFromBlocks");
    /** 1.21.5+: movements are recorded for the effects, and move() no longer writes wasOnFire. */
    private static final boolean RECORDED_MOVEMENTS = hasField(Entity.class, "finalMovementsThisTick");
    private static final MethodHandle MIN_Y = handle(LevelHeightAccessor.class, int.class, "getMinY", "getMinBuildHeight");
    private static final MethodHandle MIN_SECTION_Y = handle(LevelHeightAccessor.class, int.class, "getMinSectionY", "getMinSection");
    private static final MethodHandle COLLIDABLE_WITH = collidableWith();
    private static final MethodHandle ON_GROUND_WITH_MOVEMENT = onGroundWithMovement();
    //?} else {
    /*private static final boolean EFFECTS_AFTER_MOVE = true;
    private static final boolean RECORDED_MOVEMENTS = true;
    *///?}

    /**
     * Celeris rule bits for {@code Entity.move}'s "apply this movement" test.
     * From 1.21.2 it also applies a movement the collision barely shortened
     * ({@code moved > 1e-7 || requested - moved < 1e-7}).
     */
    public static final int MOVE_RULES = EFFECTS_AFTER_MOVE ? PhysicsMode.RULE_SMALL_MOVES : 0;

    /**
     * {@code ItemEntity}'s constants. From 26.2 its drag is {@code getAirDrag()}, a
     * float: vertically {@code (double) 0.98F} instead of {@code 0.98} (the 26.1
     * build also runs on 26.2+).
     */
    public static final com.panzer.mods.celeris.physics.BodyParams ITEM_PARAMS =
            hasMethodIn(net.minecraft.world.entity.item.ItemEntity.class, "getAirDrag")
                    ? new com.panzer.mods.celeris.physics.BodyParams(0.04, (double) 0.98F, (double) 0.98F, 0.98F,
                    com.panzer.mods.celeris.physics.BodyParams.ITEM.belowOffset(), true)
                    : com.panzer.mods.celeris.physics.BodyParams.ITEM;

    /**
     * 26.2+: {@code move} ends with {@code restituteMovementAfterCollisions}, which
     * sets a collided axis to {@code -v * restitution} (0 for an item on plain
     * blocks) where earlier versions zeroed it as {@code v * 0} (vertical) or
     * {@code 0.0} (horizontal). Same value, but the zero's sign is the opposite
     * of {@code v}.
     */
    public static final boolean RESTITUTION = hasMethodIn(Entity.class, "restituteMovementAfterCollisions");

    /** Up to 1.21.1 {@code move} itself does the fire bookkeeping (later: applyEffectsFromBlocks). */
    public static final boolean FIRE_IN_MOVE = !EFFECTS_AFTER_MOVE;

    /** Up to 1.21.4 {@code move} starts with {@code wasOnFire = isOnFire()}. */
    public static final boolean WAS_ON_FIRE_IN_MOVE = !RECORDED_MOVEMENTS;

    /**
     * {@code Entity.wasOnFire}: protected on 1.21.1, private on 1.21.2 - 1.21.4 (only
     * written there). A static final handle in a holder class, resolved on first
     * use: the JIT treats it as a constant and inlines the write (a handle read
     * from a field is invoked generically, every time).
     */
    private static final class WasOnFire {
        static final MethodHandle SETTER;

        static {
            try {
                SETTER = MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup())
                        .findSetter(Entity.class, "wasOnFire", boolean.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    public static void setWasOnFire(Entity entity, boolean value) {
        try {
            WasOnFire.SETTER.invokeExact(entity, value);
        } catch (Throwable t) {
            throw new IllegalStateException("Velox: writing wasOnFire failed", t);
        }
    }

    /** Whether {@code move} applies a movement the collision barely shortened (with the small-move rule). */
    public static boolean appliesSmallMoves() {
        return EFFECTS_AFTER_MOVE;
    }

    /** Whether a class overrides {@code canBeCollidedWith} (Entity's own answer is always false). */
    private static final ClassValue<Boolean> COLLIDABLE_TYPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Entity.class && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("canBeCollidedWith") && !m.isSynthetic()) {
                        return true;
                    }
                }
            }
            return false;
        }
    };

    private McCompat() {
    }

    /** A method named {@code name} on {@code type} or a superclass. */
    private static boolean hasMethodIn(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lowest block y of the level. */
    public static int minY(LevelHeightAccessor level) {
        //? >=1.21.10 {
        /*return level.getMinY();
        *///?} else
        return invokeInt(MIN_Y, level);
    }

    /** Lowest section y of the level. */
    public static int minSectionY(LevelHeightAccessor level) {
        //? >=1.21.10 {
        /*return level.getMinSectionY();
        *///?} else
        return invokeInt(MIN_SECTION_Y, level);
    }

    public static int chunkX(ChunkPos pos) {
        //? >=26.1 {
        /*return pos.x();
        *///?} else
        return pos.x;
    }

    public static int chunkZ(ChunkPos pos) {
        //? >=26.1 {
        /*return pos.z();
        *///?} else
        return pos.z;
    }

    /** "minecraft:overworld" and the like. */
    public static String dimensionId(Level level) {
        //? >=1.21.11 {
        /*return level.dimension().identifier().toString();
        *///?} else
        return level.dimension().location().toString();
    }

    /**
     * Entities that may block items at some point (boats, shulkers, happy
     * ghasts, modded ones): their class overrides {@code canBeCollidedWith}.
     * Whether one blocks right now is {@link #blocksItems}.
     */
    public static boolean mayBlockItems(Entity entity) {
        return COLLIDABLE_TYPE.get(entity.getClass());
    }

    /**
     * Whether vanilla would collide an item with {@code entity} this tick. No
     * vanilla override looks at the item on the server side (happy ghasts only
     * check for other happy ghasts), so no particular item needs to be passed.
     */
    public static boolean blocksItems(Entity entity) {
        //? >=1.21.10 {
        /*return entity.canBeCollidedWith(null);
        *///?} else {
        try {
            return COLLIDABLE_WITH.type().parameterCount() == 2
                    ? (boolean) COLLIDABLE_WITH.invokeExact(entity, (Entity) null)
                    : (boolean) COLLIDABLE_WITH.invokeExact(entity);
        } catch (Throwable t) {
            throw new IllegalStateException("Velox: canBeCollidedWith failed", t);
        }
        //?}
    }

    /**
     * {@code Entity.setOnGroundWithMovement}: {@code (onGround, movement)}, except on
     * 1.21.2 - 1.21.3 where it also takes (and sets) {@code horizontalCollision}.
     */
    public static void setOnGroundWithMovement(Entity entity, boolean ground, boolean horizontalCollision,
                                               net.minecraft.world.phys.Vec3 movement) {
        //? >=1.21.10 {
        /*entity.setOnGroundWithMovement(ground, movement);
        *///?} else {
        try {
            ON_GROUND_WITH_MOVEMENT.invokeExact(entity, ground, horizontalCollision, movement);
        } catch (Throwable t) {
            throw new IllegalStateException("Velox: setOnGroundWithMovement failed", t);
        }
        //?}
    }

    //? <1.21.10 {
    private static boolean hasMethod(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /** The first of {@code names} that exists, as {@code (owner) -> ret}. */
    private static MethodHandle handle(Class<?> owner, Class<?> ret, String... names) {
        for (String name : names) {
            try {
                return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(ret))
                        .asType(MethodType.methodType(ret, owner));
            } catch (ReflectiveOperationException ignored) {
                // older or newer name
            }
        }
        throw new IllegalStateException("Velox: none of " + String.join(", ", names) + " on " + owner.getName());
    }

    /** {@code canBeCollidedWith(Entity)} from 1.21.6, {@code canBeCollidedWith()} before. */
    private static MethodHandle collidableWith() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try {
            return lookup.findVirtual(Entity.class, "canBeCollidedWith", MethodType.methodType(boolean.class, Entity.class))
                    .asType(MethodType.methodType(boolean.class, Entity.class, Entity.class));
        } catch (ReflectiveOperationException e) {
            try {
                return lookup.findVirtual(Entity.class, "canBeCollidedWith", MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, Entity.class));
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException("Velox: no Entity.canBeCollidedWith", e2);
            }
        }
    }

    /** As {@code (Entity, onGround, horizontalCollision, movement)}, whichever form exists. */
    private static MethodHandle onGroundWithMovement() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> vec3 = net.minecraft.world.phys.Vec3.class;
        MethodType shape = MethodType.methodType(void.class, Entity.class, boolean.class, boolean.class, vec3);
        try {
            MethodHandle twoArgs = lookup.findVirtual(Entity.class, "setOnGroundWithMovement",
                    MethodType.methodType(void.class, boolean.class, vec3));
            // (entity, onGround, movement) -> (entity, onGround, horizontalCollision, movement)
            return MethodHandles.dropArguments(twoArgs, 2, boolean.class).asType(shape);
        } catch (ReflectiveOperationException e) {
            try {
                return lookup.findVirtual(Entity.class, "setOnGroundWithMovement",
                        MethodType.methodType(void.class, boolean.class, boolean.class, vec3)).asType(shape);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException("Velox: no Entity.setOnGroundWithMovement", e2);
            }
        }
    }

    private static int invokeInt(MethodHandle handle, LevelHeightAccessor level) {
        try {
            return (int) handle.invokeExact(level);
        } catch (Throwable t) {
            throw new IllegalStateException("Velox: level height query failed", t);
        }
    }
    //?}
}
