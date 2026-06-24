package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;

public class BlockPlaceEvent implements Cancellable {
    public Player getPlayer() {
        return null;
    }

    public Block getBlock() {
        return null;
    }

    public BlockState getBlockReplacedState() {
        return null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
