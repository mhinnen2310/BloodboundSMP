package org.bukkit.event.inventory;

import org.bukkit.event.Cancellable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PrepareAnvilEvent implements Cancellable {
    public Inventory getInventory() {
        return null;
    }

    public ItemStack getResult() {
        return null;
    }

    public void setResult(ItemStack item) {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
