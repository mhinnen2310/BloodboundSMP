package org.bukkit.event.inventory;

import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryMoveItemEvent implements Cancellable {
    public Inventory getSource() {
        return null;
    }

    public Inventory getDestination() {
        return null;
    }

    public ItemStack getItem() {
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
