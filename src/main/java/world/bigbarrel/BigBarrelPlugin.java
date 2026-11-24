package world.bigbarrel;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.craftbukkit.block.CraftBarrel;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class BigBarrelPlugin extends JavaPlugin implements Listener {
    final java.util.Set<BarrelBlockEntity> openedLids = java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Override
    public void onEnable() {
        if (!Bukkit.getMinecraftVersion().equals("26.3")) {
            getLogger().severe("This build requires Minecraft 26.3 and a compatible Mixin-enabled server.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!(net.minecraft.world.level.block.Blocks.BARREL instanceof world.bigbarrel.inject.ComparatorHookInstalled)) {
            getLogger().severe("Missing comparator mixins. Start the server with -Dirisclip.enable.mixin=true before -jar.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        world.bigbarrel.inject.ComparatorHooks.enabled = true;
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        world.bigbarrel.inject.ComparatorHooks.enabled = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        var player = event.getPlayer();
        if (player.isSneaking() && (!player.getInventory().getItemInMainHand().isEmpty()
                || !player.getInventory().getItemInOffHand().isEmpty())) {
            return;
        }
        var inventory = findPair(event.getClickedBlock());
        if (inventory == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        var handle = ((CraftPlayer) player).getHandle();
        if (!inventory.first.canOpen(handle) || !inventory.second.canOpen(handle)) {
            return;
        }
        inventory.first.unpackLootTable(handle);
        inventory.second.unpackLootTable(handle);
        Component title = inventory.first.hasCustomName() ? inventory.first.getDisplayName()
                : inventory.second.hasCustomName() ? inventory.second.getDisplayName()
                : Component.literal("大木桶");
        handle.openMenu(new SimpleMenuProvider((id, items, viewer) ->
                ChestMenu.sixRows(id, items, inventory), title));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperSearch(HopperInventorySearchEvent event) {
        // Respect inventories supplied by other plugins, and searches with no inventory.
        if (!(event.getInventory() instanceof CraftInventory current)
                || !(current.getInventory() instanceof BarrelBlockEntity)) {
            return;
        }
        var pair = findPair(event.getSearchBlock());
        if (pair != null && pair.contains(current.getInventory())) {
            event.setInventory(new CraftInventory(pair));
        }
    }

    private BarrelPair findPair(Block block) {
        if (block == null || !Bukkit.isOwnedByCurrentRegion(block)
                || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                || block.getType() != Material.BARREL) {
            return null;
        }
        BlockFace facing = ((Directional) block.getBlockData()).getFacing();
        Block other = block.getRelative(facing.getOppositeFace());
        if (!Bukkit.isOwnedByCurrentRegion(other)
                || !other.getWorld().isChunkLoaded(other.getX() >> 4, other.getZ() >> 4)
                || other.getType() != Material.BARREL
                || ((Directional) other.getBlockData()).getFacing() != facing.getOppositeFace()) {
            return null;
        }
        var first = ((CraftBarrel) block.getState(false)).getBlockEntity();
        var second = ((CraftBarrel) other.getState(false)).getBlockEntity();
        // Negative-facing barrel is always the first 27 slots, regardless of clicked half.
        return facing.getModX() + facing.getModY() + facing.getModZ() < 0
                ? new BarrelPair(this, first, second, block, other)
                : new BarrelPair(this, second, first, other, block);
    }
}
