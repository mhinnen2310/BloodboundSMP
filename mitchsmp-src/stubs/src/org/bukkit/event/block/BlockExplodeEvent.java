package org.bukkit.event.block;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;

public class BlockExplodeEvent implements Cancellable {
    public List<Block> blockList() {
        return List.of();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
