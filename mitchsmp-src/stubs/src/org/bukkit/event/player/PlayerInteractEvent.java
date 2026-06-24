package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

public class PlayerInteractEvent implements Cancellable {
    public Action getAction() {
        return null;
    }

    public ItemStack getItem() {
        return null;
    }

    public Player getPlayer() {
        return null;
    }

    public Block getClickedBlock() {
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
