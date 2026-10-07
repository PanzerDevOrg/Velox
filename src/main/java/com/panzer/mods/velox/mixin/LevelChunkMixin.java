package com.panzer.mods.velox.mixin;

import com.panzer.mods.velox.physics.ItemPhysicsWorld;
import com.panzer.mods.velox.physics.VeloxLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the terrain snapshot current: every block change in a loaded server chunk. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Shadow
    public abstract Level getLevel();

    // The last argument became update flags (int, 1.21.5+) in place of isMoving
    // (boolean). The 1.21.1 build also runs on 1.21.5 and 1.21.6: it injects into
    // whichever of the two exists.
    //? >=1.21.10 {
    /*@Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void velox$blockChanged(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        velox$changed(pos, state, cir.getReturnValue());
    }
    *///?} else {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), require = 0)
    private void velox$blockChanged(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        velox$changed(pos, state, cir.getReturnValue());
    }

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), require = 0)
    private void velox$blockChangedFlags(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        velox$changed(pos, state, cir.getReturnValue());
    }
    //?}

    @Unique
    private void velox$changed(BlockPos pos, BlockState state, BlockState old) {
        if (old != null && getLevel() instanceof VeloxLevel holder) {
            ItemPhysicsWorld world = holder.velox$physics();
            if (world != null) {
                world.onBlockChanged(pos, old, state);
            }
        }
    }
}
