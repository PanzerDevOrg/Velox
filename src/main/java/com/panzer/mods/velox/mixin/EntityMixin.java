package com.panzer.mods.velox.mixin;

import com.panzer.mods.velox.physics.VeloxBody;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices position/velocity writes to simulated items made by anything other
 * than Velox (teleports, knockback, explosions, commands, other mods), so the
 * next step re-reads that entity instead of trusting the batch.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    // Runs on every position and velocity write of every entity: a class test
    // (ItemEntity is the only VeloxBody) is a constant-time check, where a
    // failed interface instanceof walks the class's interfaces.

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"))
    private void velox$velocityWritten(Vec3 velocity, CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity item) {
            ((VeloxBody) item).velox$onExternalWrite();
        }
    }

    @Inject(method = "setPosRaw(DDD)V", at = @At("RETURN"))
    private void velox$positionWritten(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity item) {
            ((VeloxBody) item).velox$onExternalWrite();
        }
    }

    /**
     * {@code baseTick}'s fluid update, on the box before this tick's move: for
     * an item the kernel simulated this tick, the box holds no fluid, so the
     * scan over its blocks (a chunk lookup and fluid state per block) is
     * replaced by its known no-fluid result.
     */
    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;updateInWaterStateAndDoFluidPushing()Z"))
    private boolean velox$knownNoFluid(Entity self, Operation<Boolean> original) {
        if ((Object) this instanceof ItemEntity item && ((VeloxBody) item).velox$simulatedThisTick()) {
            return ((VeloxBody) item).velox$noFluid();
        }
        return original.call(self);
    }
}
