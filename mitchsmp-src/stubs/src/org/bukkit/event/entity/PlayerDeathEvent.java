package org.bukkit.event.entity;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerDeathEvent extends EntityDeathEvent {
    public Player getEntity() {
        return null;
    }

    public List<ItemStack> getDrops() {
        return List.of();
    }

    public void setDroppedExp(int exp) {
    }

    public void setKeepInventory(boolean keepInventory) {
    }

    public void setKeepLevel(boolean keepLevel) {
    }
}
