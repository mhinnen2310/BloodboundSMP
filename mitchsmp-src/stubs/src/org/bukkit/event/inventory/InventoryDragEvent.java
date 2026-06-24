package org.bukkit.event.inventory;

import java.util.Set;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.InventoryView;

public class InventoryDragEvent implements Cancellable {
    public InventoryView getView() {
        return null;
    }

    public HumanEntity getWhoClicked() {
        return null;
    }

    public Set<Integer> getRawSlots() {
        return Set.of();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
