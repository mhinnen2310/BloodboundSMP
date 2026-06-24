package org.bukkit.event.enchantment;

import java.util.Map;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

public class EnchantItemEvent implements Cancellable {
    public Player getEnchanter() {
        return null;
    }

    public ItemStack getItem() {
        return null;
    }

    public int getExpLevelCost() {
        return 0;
    }

    public Map<Enchantment, Integer> getEnchantsToAdd() {
        return Map.of();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
