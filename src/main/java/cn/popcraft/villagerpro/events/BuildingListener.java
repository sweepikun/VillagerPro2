package cn.popcraft.villagerpro.events;

import cn.popcraft.villagerpro.managers.BuildingManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class BuildingListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        BuildingManager.markNearbyDirty(event.getBlockPlaced().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        BuildingManager.markNearbyDirty(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        BuildingManager.markNearbyDirty(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        markBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        markBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        BuildingManager.markNearbyDirty(event.getBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            BuildingManager.markNearbyDirty(block.getLocation());
            BuildingManager.markNearbyDirty(block.getRelative(event.getDirection()).getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            BuildingManager.markNearbyDirty(block.getLocation());
            BuildingManager.markNearbyDirty(block.getRelative(event.getDirection()).getLocation());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        BuildingManager.markChunkDirty(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        BuildingManager.markChunkDirty(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    private static void markBlocks(java.util.List<Block> blocks) {
        for (Block block : blocks) {
            BuildingManager.markNearbyDirty(block.getLocation());
        }
    }
}
