package com.panzer.mods.velox.mixin;

import com.panzer.mods.velox.physics.ItemPhysicsWorld;
import com.panzer.mods.velox.physics.VeloxLevel;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Runs the item batch once per tick, right before the level ticks its
 * entities: after block ticks, fluid ticks and block events, so the terrain
 * the batch saw is the terrain the entities tick in.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements VeloxLevel {

    @Unique
    private ItemPhysicsWorld velox$physics;

    @Override
    public ItemPhysicsWorld velox$physics() {
        return velox$physics;
    }

    @Override
    public void velox$setPhysics(ItemPhysicsWorld physics) {
        velox$physics = physics;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void velox$stepItems(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ItemPhysicsWorld world = velox$physics;
        if (world != null) {
            world.tick();
        }
    }
}
