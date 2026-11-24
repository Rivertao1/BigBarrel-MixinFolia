package world.bigbarrel.inject.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import world.bigbarrel.inject.ComparatorHooks;

@Mixin(value = BlockEntity.class, remap = false)
public abstract class BarrelChangeMixin {
    @Inject(method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private static void bigbarrel$changed(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        ComparatorHooks.updatePartner(level, pos, state);
    }

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void bigbarrel$rotated(BlockState state, CallbackInfo ci) {
        var self = (BlockEntity) (Object) this;
        var old = self.getBlockState();
        if (self.getLevel() != null && old.is(Blocks.BARREL) && state.is(Blocks.BARREL)
                && old.getValue(BarrelBlock.FACING) != state.getValue(BarrelBlock.FACING)) {
            ComparatorHooks.updatePartner(self.getLevel(), self.getBlockPos(), old);
            ComparatorHooks.updatePartner(self.getLevel(), self.getBlockPos(), state);
        }
    }
}
