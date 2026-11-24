package world.bigbarrel.inject;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded by Irisclip before the Bukkit plugin classloader. */
public final class ComparatorHooks {
    public static volatile boolean enabled;

    private ComparatorHooks() {}

    public static BlockPos partner(Level level, BlockPos pos, BlockState state) {
        if (!enabled || !state.is(Blocks.BARREL) || !TickThread.isTickThreadFor(level, pos)) return null;
        var facing = state.getValue(BarrelBlock.FACING);
        var other = pos.relative(facing.getOpposite());
        if (!TickThread.isTickThreadFor(level, other)) return null;
        var otherState = level.getBlockStateIfLoaded(other);
        return otherState != null && otherState.is(Blocks.BARREL)
                && otherState.getValue(BarrelBlock.FACING) == facing.getOpposite() ? other : null;
    }

    public static void updatePartner(Level level, BlockPos pos, BlockState state) {
        var other = partner(level, pos, state);
        if (other != null) level.updateNeighbourForOutputSignal(other, Blocks.BARREL);
    }
}
