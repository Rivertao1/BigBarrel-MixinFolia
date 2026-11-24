package world.bigbarrel;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.craftbukkit.block.CraftBarrel;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import net.minecraft.world.entity.ContainerUser;

final class BarrelPair extends CompoundContainer {
    final BarrelBlockEntity first;
    final BarrelBlockEntity second;
    private final Block firstBlock;
    private final Block secondBlock;
    private final BigBarrelPlugin plugin;

    BarrelPair(BigBarrelPlugin plugin, BarrelBlockEntity first, BarrelBlockEntity second, Block firstBlock, Block secondBlock) {
        super(first, second);
        this.plugin = plugin;
        this.first = first;
        this.second = second;
        this.firstBlock = firstBlock;
        this.secondBlock = secondBlock;
    }

    @Override
    public void onOpen(CraftHumanEntity viewer) {
        super.onOpen(viewer);
        if (viewer.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            openLid(first, firstBlock);
            openLid(second, secondBlock);
        }
    }

    @Override
    public void onClose(CraftHumanEntity viewer) {
        super.onClose(viewer);
        closeLid(first, firstBlock);
        closeLid(second, secondBlock);
    }

    // Vanilla's opener counter only recognises a menu backed by ONE barrel. The
    // Lidded API's opened flag survives its periodic checks for a compound menu.
    @Override
    public void startOpen(ContainerUser user) {}

    @Override
    public void stopOpen(ContainerUser user) {}

    private void openLid(BarrelBlockEntity barrel, Block block) {
        if (!barrel.openersCounter.opened && !barrel.isRemoved()) {
            ((CraftBarrel) block.getState(false)).open();
            plugin.openedLids.add(barrel);
        }
    }

    private void closeLid(BarrelBlockEntity barrel, Block block) {
        if (!Bukkit.isOwnedByCurrentRegion(block)) {
            if (plugin.isEnabled()) {
                Bukkit.getRegionScheduler().run(plugin, block.getLocation(), task -> closeLid(barrel, block));
            }
            return;
        }
        if (barrel.getViewers().stream().anyMatch(viewer ->
                viewer.getGameMode() != org.bukkit.GameMode.SPECTATOR
                && viewer.getOpenInventory().getTopInventory() instanceof org.bukkit.craftbukkit.inventory.CraftInventory inventory
                && inventory.getInventory() instanceof BarrelPair pair && pair.contains(barrel))) {
            return;
        }
        if (!plugin.openedLids.remove(barrel) || barrel.isRemoved()
                || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        if (block.getState(false) instanceof CraftBarrel current && current.getBlockEntity() == barrel) {
            current.close();
            barrel.recheckOpen();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!Bukkit.isOwnedByCurrentRegion(firstBlock) || !Bukkit.isOwnedByCurrentRegion(secondBlock)
                || !firstBlock.getWorld().isChunkLoaded(firstBlock.getX() >> 4, firstBlock.getZ() >> 4)
                || !secondBlock.getWorld().isChunkLoaded(secondBlock.getX() >> 4, secondBlock.getZ() >> 4)
                || first.isRemoved() || second.isRemoved()
                || firstBlock.getType() != Material.BARREL || secondBlock.getType() != Material.BARREL) {
            return false;
        }
        var facing = ((Directional) firstBlock.getBlockData()).getFacing();
        return firstBlock.getRelative(facing.getOppositeFace()).equals(secondBlock)
                && ((Directional) secondBlock.getBlockData()).getFacing() == facing.getOppositeFace()
                && super.stillValid(player);
    }
}
