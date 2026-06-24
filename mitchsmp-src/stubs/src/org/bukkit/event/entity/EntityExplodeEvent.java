package org.bukkit.event.entity;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;

public class EntityExplodeEvent implements Cancellable {
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
