package org.bukkit.inventory;

import java.util.HashMap;

public interface Inventory {
    HashMap<Integer, ItemStack> addItem(ItemStack... items);

    void setItem(int index, ItemStack item);

    ItemStack getItem(int index);

    int getSize();

    InventoryHolder getHolder();

    ItemStack[] getContents();

    void setContents(ItemStack[] contents);

    void clear();
}
