package org.bukkit.entity;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public interface HumanEntity {
    InventoryView openInventory(Inventory inventory);

    void closeInventory();
}
