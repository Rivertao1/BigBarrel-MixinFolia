package world.bigbarrel.inject.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import world.bigbarrel.inject.ComparatorHookInstalled;
import world.bigbarrel.inject.ComparatorHooks;

@Mixin(value = BarrelBlock.class, remap = false)
public abstract class BarrelComparatorMixin extends BaseEntityBlock implements ComparatorHookInstalled {
    protected BarrelComparatorMixin(Properties properties) { super(properties); }

    @Inject(method = "getAnalogOutputSignal", at = @At("HEAD"), cancellable = true)
    private void bigbarrel$signal(BlockState state, Level level, BlockPos pos, Direction direction,
                                 CallbackInfoReturnable<Integer> result) {
        var other = ComparatorHooks.partner(level, pos, state);
        if (other != null && level.getBlockEntity(pos) instanceof BarrelBlockEntity first
                && level.getBlockEntity(other) instanceof BarrelBlockEntity second) {
            result.setReturnValue(AbstractContainerMenu.getRedstoneSignalFromContainer(new CompoundContainer(first, second)));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        ComparatorHooks.updatePartner(level, pos, state);
    }

    @Inject(method = "affectNeighborsAfterRemoval", at = @At("TAIL"))
    private void bigbarrel$removed(BlockState state, ServerLevel level, BlockPos pos, boolean moved, CallbackInfo ci) {
        ComparatorHooks.updatePartner(level, pos, state);
    }
}
