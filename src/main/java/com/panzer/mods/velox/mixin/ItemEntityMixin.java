package com.panzer.mods.velox.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.panzer.mods.celeris.physics.BodyBatch;
import com.panzer.mods.celeris.physics.BodyFlags;
import com.panzer.mods.velox.physics.ItemPhysicsWorld;
import com.panzer.mods.velox.physics.McCompat;
import com.panzer.mods.velox.physics.MovementRecorder;
import com.panzer.mods.velox.physics.VeloxBody;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the physics block of {@code ItemEntity.tick} -- {@code
 * applyGravity()}, {@code move(...)} and the friction/bounce {@code
 * setDeltaMovement(...)} calls -- with the result Celeris computed for this
 * entity before the level started ticking entities. Only when this tick was
 * actually simulated (stamped with the current step); otherwise, including
 * every body the kernel deferred, vanilla runs untouched. Everything else in
 * the tick (pickup delay, merging, age, despawn, the sync flag) runs as
 * in vanilla, in vanilla order.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity implements VeloxBody {

    @Unique
    private int velox$slot = -1;
    @Unique
    private ItemPhysicsWorld velox$owner;
    @Unique
    private long velox$simulatedStep = -1;
    /** This tick's physics was replaced by the batch result. */
    @Unique
    private boolean velox$applied;
    /** Velox is writing the entity; its own setter calls are not external writes. */
    @Unique
    private boolean velox$applying;

    protected ItemEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void velox$beginTick(CallbackInfo ci) {
        velox$applied = false;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;applyGravity()V"))
    private boolean velox$replacePhysics(ItemEntity self) {
        ItemPhysicsWorld world = velox$owner;
        if (world == null || velox$slot < 0 || velox$simulatedStep != world.step()) {
            return true;
        }
        velox$applying = true;
        try {
            velox$apply(world.batch(), velox$slot);
        } finally {
            velox$applying = false;
        }
        velox$applied = true;
        world.markApplied(velox$slot);
        return false;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean velox$skipMove(ItemEntity self, MoverType type, Vec3 movement) {
        return !velox$applied;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean velox$skipDrag(ItemEntity self, Vec3 velocity) {
        return !velox$applied;
    }

    /**
     * Writes the simulated state the way {@code Entity.move} and the rest of
     * the replaced block would have: position (with bounding box and section
     * tracking), collision flags, supporting block, fall bookkeeping (incl. the
     * HIT_GROUND game event sculk sensors listen for), final velocity. Up to
     * 1.21.1 {@code move} also did the fire bookkeeping; later versions do it in
     * {@code applyEffectsFromBlocks}, which {@code ItemEntity.tick} still runs
     * after the skipped {@code move}, so only the movement is recorded for it.
     */
    @Unique
    private void velox$apply(BodyBatch batch, int slot) {
        int flags = batch.flags(slot);
        double vx = batch.vx(slot), vy = batch.vy(slot), vz = batch.vz(slot);
        if ((flags & BodyFlags.HELD) == 0) {
            Vec3 from = position();
            // What vanilla passes to move(): the velocity after applyGravity().
            Vec3 requested = getDeltaMovement().add(0.0, -getGravity(), 0.0);
            double nx = batch.x(slot), ny = batch.y(slot), nz = batch.z(slot);
            boolean ground = (flags & BodyFlags.ON_GROUND) != 0;
            boolean hCollision = (flags & BodyFlags.H_COLLISION) != 0;
            boolean vCollision = (flags & BodyFlags.V_COLLISION) != 0;
            // The collided movement, as move() hands it on: an axis that did not
            // collide moved exactly as requested (applied or not).
            Vec3 moved = new Vec3(hCollision ? nx - from.x : requested.x, vCollision ? ny - from.y : requested.y,
                    hCollision ? nz - from.z : requested.z);
            boolean changed = nx != from.x || ny != from.y || nz != from.z;
            if (McCompat.WAS_ON_FIRE_IN_MOVE) {
                McCompat.setWasOnFire(this, isOnFire());
            }
            // move() applies a movement that changed the position, or (1.21.2+) one
            // the collision barely shortened (here: a tiny request, fully blocked).
            if (changed || (McCompat.appliesSmallMoves() && requested.lengthSqr() < 1.0E-7)) {
                Vec3 to = new Vec3(nx, ny, nz);
                MovementRecorder.record(this, from, to, requested);
                setPos(to.x, to.y, to.z);
            }
            this.horizontalCollision = hCollision;
            this.minorHorizontalCollision = false;
            this.verticalCollision = vCollision;
            this.verticalCollisionBelow = ground;
            McCompat.setOnGroundWithMovement(this, ground, hCollision, moved);
            BlockPos onPos = getOnPosLegacy();
            checkFallDamage(moved.y, ground, level().getBlockState(onPos), onPos);
            //? <1.21.10 {
            // Up to 1.21.1 only; later applyEffectsFromBlocks, which tick() still runs, does it.
            // The kernel only simulates boxes free of fire and lava (they are COMPLEX).
            if (McCompat.FIRE_IN_MOVE) {
            if (getRemainingFireTicks() <= 0) {
                setRemainingFireTicks(-getFireImmuneTicks());
            }
            boolean extinguishing = this.isInPowderSnow || isInWaterRainOrBubble()
                    || isInFluidType((fluidType, height) -> canFluidExtinguish(fluidType));
            if (this.wasOnFire && extinguishing) {
                playEntityOnFireExtinguishedSound();
            }
            if (isOnFire() && extinguishing) {
                setRemainingFireTicks(-getFireImmuneTicks());
            }
            }
            //?}
            if (McCompat.RESTITUTION) {
                // A collided axis ends as -v * 0 (see McCompat.RESTITUTION): a zero
                // with the opposite sign of the requested velocity.
                if (hCollision && !Mth.equal(requested.x, nx - from.x) && vx == 0.0) {
                    vx = -requested.x * 0.0;
                }
                if (hCollision && !Mth.equal(requested.z, nz - from.z) && vz == 0.0) {
                    vz = -requested.z * 0.0;
                }
                if (vCollision && vy == 0.0) {
                    vy = -requested.y * 0.0;
                }
            }
        }
        setDeltaMovement(new Vec3(vx, vy, vz));
    }

    @Override
    public int velox$slot() {
        return velox$slot;
    }

    @Override
    public void velox$setSlot(int slot) {
        velox$slot = slot;
    }

    @Override
    public ItemPhysicsWorld velox$owner() {
        return velox$owner;
    }

    @Override
    public void velox$setOwner(ItemPhysicsWorld owner) {
        velox$owner = owner;
    }

    @Override
    public void velox$setSimulatedStep(long step) {
        velox$simulatedStep = step;
    }

    @Override
    public boolean velox$appliedThisTick() {
        return velox$applied;
    }

    @Override
    public void velox$onExternalWrite() {
        if (!velox$applying && velox$owner != null && velox$slot >= 0) {
            // A result computed before this write is stale: run vanilla this
            // tick (if not ticked yet) and re-read the entity next step.
            velox$simulatedStep = -1;
            velox$owner.markDirty((ItemEntity) (Object) this);
        }
    }
}
