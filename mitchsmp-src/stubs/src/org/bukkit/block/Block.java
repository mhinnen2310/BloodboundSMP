package org.bukkit.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public interface Block {
    void setType(Material material);

    void setType(Material material, boolean applyPhysics);

    Material getType();

    Location getLocation();

    BlockState getState();

    boolean breakNaturally(ItemStack tool);
}
