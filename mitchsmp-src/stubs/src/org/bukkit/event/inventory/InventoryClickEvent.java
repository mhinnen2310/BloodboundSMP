package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;

public class InventoryClickEvent implements Cancellable {
    public InventoryView getView() {
        return null;
    }

    public int getRawSlot() {
        return 0;
    }

    public boolean isShiftClick() {
        return false;
    }

    public HumanEntity getWhoClicked() {
        return null;
    }

    public ItemStack getCurrentItem() {
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
