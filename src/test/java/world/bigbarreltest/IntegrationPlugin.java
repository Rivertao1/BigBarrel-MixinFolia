package world.bigbarreltest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Barrel;
import org.bukkit.block.data.Directional;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.block.CraftHopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

public final class IntegrationPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            var world = Bukkit.getWorlds().getFirst();
            var location = world.getSpawnLocation();
            location.setY(world.getMaxHeight() - 20);
            Bukkit.getRegionScheduler().run(this, location, regionTask -> test(location.getBlock()));
        }, 40);
    }

    private Inventory search(Block block) {
        var original = ((Barrel) block.getState(false)).getInventory();
        var event = new HopperInventorySearchEvent(original,
                HopperInventorySearchEvent.ContainerType.SOURCE, block.getRelative(BlockFace.DOWN), block);
        Bukkit.getPluginManager().callEvent(event);
        return event.getInventory();
    }

    private void test(Block center) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        try {
            if (!center.getType().isAir()) throw new AssertionError("Test space occupied");
            for (var face : faces) {
                if (!center.getRelative(face).getType().isAir()) throw new AssertionError("Test space occupied");
            }
            var originalCenter = center.getState();
            try {
                for (var face : faces) {
                    var other = center.getRelative(face.getOppositeFace());
                    var originalOther = other.getState();
                    try {
                        putBarrel(center, face);
                        putBarrel(other, face.getOppositeFace());
                        Inventory combined = search(center);
                        require(combined.getSize() == 54, "combined size: " + face);
                        combined.setItem(0, new ItemStack(Material.DIAMOND, 7));
                        combined.setItem(53, new ItemStack(Material.EMERALD, 11));
                        Inventory reversed = search(other);
                        require(reversed.getItem(0).getAmount() == 7, "stable slot order");
                        require(reversed.getItem(53).getAmount() == 11, "second half shared");
                        int total = count(((Barrel) center.getState(false)).getInventory())
                                + count(((Barrel) other.getState(false)).getInventory());
                        require(total == 18, "writes reach real barrels exactly once");
                        combined.clear();
                        putBarrel(other, face);
                        require(search(center).getSize() == 27, "top-to-bottom rejected");
                        other.setType(Material.AIR, false);
                        require(search(center).getSize() == 27, "single barrel after removal");
                    } finally {
                        if (other.getState(false) instanceof Barrel barrel) barrel.getInventory().clear();
                        originalOther.update(true, false);
                    }
                }
                testHopper(center);
                testLids(center);
            } finally {
                if (center.getState(false) instanceof Barrel barrel) barrel.getInventory().clear();
                originalCenter.update(true, false);
            }
            getLogger().info("BIGBARREL_BASE_PASS: six orientations, shared slots, real storage, invalid pairs, removal, hopper transfers, two viewers, lid recheck, last viewer close, external lid ownership");
            testComparators(center);
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "BIGBARREL_TEST_FAIL", error);
        }
    }

    private void testComparators(Block center) {
        var other = center.getRelative(BlockFace.SOUTH);
        var firstComparator = center.getRelative(BlockFace.WEST);
        var secondComparator = other.getRelative(BlockFace.WEST);
        var side = firstComparator.getRelative(BlockFace.NORTH);
        Block[] blocks = {center, other, firstComparator, secondComparator,
                firstComparator.getRelative(BlockFace.DOWN), secondComparator.getRelative(BlockFace.DOWN), side};
        for (var block : blocks) {
            if (!block.getType().isAir()) throw new AssertionError("Comparator test space occupied");
        }
        var saved = java.util.Arrays.stream(blocks).map(Block::getState).toList();
        var chunks = java.util.Arrays.stream(blocks).map(Block::getChunk).distinct().toList();
        chunks.forEach(chunk -> chunk.addPluginChunkTicket(this));
        Runnable restore = () -> {
            for (var block : new Block[]{center, other}) {
                if (block.getState(false) instanceof Barrel barrel) barrel.getInventory().clear();
            }
            for (var state : saved) state.update(true, false);
            chunks.forEach(chunk -> chunk.removePluginChunkTicket(this));
        };
        class Steps implements java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> {
            int step;
            public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
                try {
                    switch (step++) {
                        case 0 -> {
                            putBarrel(center, BlockFace.NORTH);
                            putBarrel(other, BlockFace.SOUTH);
                            for (var comparator : new Block[]{firstComparator, secondComparator}) {
                                comparator.getRelative(BlockFace.DOWN).setType(Material.STONE);
                                var data = (org.bukkit.block.data.type.Comparator) Bukkit.createBlockData(Material.COMPARATOR);
                                data.setFacing(BlockFace.EAST);
                                comparator.setBlockData(data);
                            }
                            fill(center, Material.STONE);
                            var level = ((org.bukkit.craftbukkit.CraftWorld) center.getWorld()).getHandle();
                            for (var barrel : new Block[]{center, other}) {
                                var pos = new net.minecraft.core.BlockPos(barrel.getX(), barrel.getY(), barrel.getZ());
                                require(level.getBlockState(pos).getAnalogOutputSignal(level, pos, net.minecraft.core.Direction.EAST) == 8,
                                        "direct combined analog output");
                            }
                        }
                        case 1 -> {
                            signals(firstComparator, secondComparator, 8, "half full from either end");
                            fill(other, Material.STONE);
                        }
                        case 2 -> {
                            signals(firstComparator, secondComparator, 15, "full");
                            ((Barrel) center.getState(false)).getInventory().clear();
                        }
                        case 3 -> {
                            signals(firstComparator, secondComparator, 8, "remote updates");
                            ((Barrel) other.getState(false)).getInventory().clear();
                        }
                        case 4 -> {
                            signals(firstComparator, secondComparator, 0, "empty");
                            fill(center, Material.STONE);
                            other.setType(Material.AIR);
                        }
                        case 5 -> {
                            require(signal(firstComparator) == 15, "split restores single-barrel signal");
                            putBarrel(other, BlockFace.SOUTH);
                        }
                        case 6 -> {
                            signals(firstComparator, secondComparator, 8, "rejoin updates existing comparator");
                            putBarrel(other, BlockFace.NORTH);
                        }
                        case 7 -> {
                            require(signal(firstComparator) == 15, "rotation splits pair");
                            putBarrel(other, BlockFace.SOUTH);
                        }
                        case 8 -> {
                            signals(firstComparator, secondComparator, 8, "rotation rejoins pair");
                            fill(other, Material.STONE);
                            side.setType(Material.REDSTONE_BLOCK);
                        }
                        case 9 -> {
                            require(signal(firstComparator) == 15, "compare mode at equal side input");
                            var level = ((org.bukkit.craftbukkit.CraftWorld) center.getWorld()).getHandle();
                            var pos = new net.minecraft.core.BlockPos(firstComparator.getX(), firstComparator.getY(), firstComparator.getZ());
                            var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ComparatorTest"),
                                    net.minecraft.server.level.ClientInformation.createDefault());
                            // Use the real right-click path: setting MODE alone does not recalculate output.
                            level.getBlockState(pos).useWithoutItem(level, player,
                                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                                            net.minecraft.core.Direction.UP, pos, false));
                        }
                        case 10 -> {
                            require(signal(firstComparator) == 0, "subtract mode");
                            restore.run();
                            getLogger().info("BIGBARREL_TEST_PASS: base tests plus natural comparator ticks, 0/8/15 signals, remote changes, split, rejoin, rotation, compare/subtract");
                            return;
                        }
                        default -> throw new AssertionError("Unexpected test step");
                    }
                    Bukkit.getRegionScheduler().runDelayed(IntegrationPlugin.this, center.getLocation(), this, 10);
                } catch (Throwable error) {
                    restore.run();
                    getLogger().log(java.util.logging.Level.SEVERE, "BIGBARREL_TEST_FAIL comparator step " + (step - 1), error);
                }
            }
        }
        new Steps().accept(null);
    }

    private static void fill(Block block, Material material) {
        var inventory = ((Barrel) block.getState(false)).getInventory();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, new ItemStack(material, 64));
    }

    private static int signal(Block block) {
        var level = ((org.bukkit.craftbukkit.CraftWorld) block.getWorld()).getHandle();
        var pos = new net.minecraft.core.BlockPos(block.getX(), block.getY(), block.getZ());
        return ((net.minecraft.world.level.block.entity.ComparatorBlockEntity) level.getBlockEntity(pos)).getOutputSignal();
    }

    private static void signals(Block first, Block second, int expected, String description) {
        require(signal(first) == expected && signal(second) == expected,
                description + ": expected " + expected + ", got " + signal(first) + "/" + signal(second));
    }

    private void testLids(Block center) {
        var other = center.getRelative(BlockFace.SOUTH);
        var original = other.getState();
        net.minecraft.world.Container left = null;
        net.minecraft.world.Container right = null;
        net.minecraft.server.level.ServerPlayer alice = null;
        net.minecraft.server.level.ServerPlayer bob = null;
        try {
            putBarrel(center, BlockFace.NORTH);
            putBarrel(other, BlockFace.SOUTH);
            left = ((org.bukkit.craftbukkit.inventory.CraftInventory) search(center)).getInventory();
            right = ((org.bukkit.craftbukkit.inventory.CraftInventory) search(other)).getInventory();
            var level = ((org.bukkit.craftbukkit.CraftWorld) center.getWorld()).getHandle();
            alice = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BarrelTestA"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            bob = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BarrelTestB"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            alice.containerMenu = net.minecraft.world.inventory.ChestMenu.sixRows(1, alice.getInventory(), left);
            bob.containerMenu = net.minecraft.world.inventory.ChestMenu.sixRows(1, bob.getInventory(), right);
            left.onOpen(alice.getBukkitEntity());
            left.startOpen(alice);
            right.onOpen(bob.getBukkitEntity());
            right.startOpen(bob);
            var barrel = ((org.bukkit.craftbukkit.block.CraftBarrel) center.getState(false)).getBlockEntity();
            var second = ((org.bukkit.craftbukkit.block.CraftBarrel) other.getState(false)).getBlockEntity();
            barrel.recheckOpen();
            second.recheckOpen();
            require(isOpen(center) && isOpen(other), "lids survive vanilla recheck");
            left.onClose(alice.getBukkitEntity());
            left.stopOpen(alice);
            require(isOpen(center) && isOpen(other), "first viewer closing keeps both lids open");
            right.onClose(bob.getBukkitEntity());
            right.stopOpen(bob);
            require(!isOpen(center) && !isOpen(other), "last viewer closes both lids");
            ((Barrel) center.getState(false)).open();
            left.onOpen(alice.getBukkitEntity());
            left.onClose(alice.getBukkitEntity());
            require(isOpen(center) && !isOpen(other), "pre-existing API lid open preserved");
            ((Barrel) center.getState(false)).close();
            alice.containerMenu = alice.inventoryMenu;
            var cancellationListener = new org.bukkit.event.Listener() {};
            var cancelledPlayer = alice.getUUID();
            Bukkit.getPluginManager().registerEvent(org.bukkit.event.inventory.InventoryOpenEvent.class,
                    cancellationListener, org.bukkit.event.EventPriority.NORMAL, (listener, event) -> {
                        var opening = (org.bukkit.event.inventory.InventoryOpenEvent) event;
                        if (opening.getPlayer().getUniqueId().equals(cancelledPlayer)) opening.setCancelled(true);
                    }, this);
            try {
                var menu = net.minecraft.world.inventory.ChestMenu.sixRows(2, alice.getInventory(), left);
                menu.setTitle(net.minecraft.network.chat.Component.literal("Test"));
                var result = org.bukkit.craftbukkit.event.CraftEventFactory.callInventoryOpenEventWithTitle(alice, menu);
                require(result.getSecond() == null, "open event cancellation respected");
                require(!isOpen(center) && !isOpen(other), "cancelled open releases both lids");
            } finally {
                org.bukkit.event.HandlerList.unregisterAll(cancellationListener);
            }
            getLogger().info("BIGBARREL_LIDS_PASS");
        } finally {
            if (left != null && alice != null) left.onClose(alice.getBukkitEntity());
            if (right != null && bob != null) right.onClose(bob.getBukkitEntity());
            original.update(true, false);
        }
    }

    private static boolean isOpen(Block block) {
        return ((org.bukkit.block.data.type.Barrel) block.getBlockData()).isOpen();
    }

    private void testHopper(Block center) {
        var other = center.getRelative(BlockFace.SOUTH);
        var below = center.getRelative(BlockFace.DOWN);
        var above = center.getRelative(BlockFace.UP);
        var otherState = other.getState();
        var belowState = below.getState();
        var aboveState = above.getState();
        try {
            putBarrel(center, BlockFace.NORTH);
            putBarrel(other, BlockFace.SOUTH);
            var remote = ((Barrel) other.getState(false)).getInventory();
            remote.setItem(26, new ItemStack(Material.DIAMOND, 7));
            below.setType(Material.HOPPER, false);
            var hopper = ((CraftHopper) below.getState(false)).getBlockEntity();
            require(HopperBlockEntity.suckInItems(hopper.getLevel(), hopper), "extract remote half");
            require(hopper.getItem(0).getCount() == 1 && remote.getItem(26).getAmount() == 6,
                    "hopper extraction conserves items");
            hopper.clearContent();
            remote.clear();
            var near = ((Barrel) center.getState(false)).getInventory();
            for (int i = 0; i < 27; i++) near.setItem(i, new ItemStack(Material.STONE, 64));
            above.setType(Material.HOPPER, false);
            var input = ((CraftHopper) above.getState(false)).getBlockEntity();
            input.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
            input.setCooldown(0);
            HopperBlockEntity.pushItemsTick(input.getLevel(), input.getBlockPos(), input.getBlockState(), input);
            require(count(remote) == 1 && input.getItem(0).getCount() == 2,
                    "insert into remote half when local half full");
        } finally {
            for (var block : new Block[]{center, other, below, above}) {
                if (block.getState(false) instanceof org.bukkit.block.Container container) container.getInventory().clear();
            }
            otherState.update(true, false);
            belowState.update(true, false);
            aboveState.update(true, false);
        }
    }

    private static void putBarrel(Block block, BlockFace facing) {
        var data = (Directional) Bukkit.createBlockData(Material.BARREL);
        data.setFacing(facing);
        // Topology tests need normal placement notifications, as player placement does.
        block.setBlockData(data, true);
    }

    private static int count(Inventory inventory) {
        int total = 0;
        for (ItemStack stack : inventory) if (stack != null) total += stack.getAmount();
        return total;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
