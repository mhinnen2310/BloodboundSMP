package nl.mitchsmp.core.api;

import org.bukkit.inventory.ItemStack;

public interface CorruptedHeartService {
    ItemStack createHeart(int amount);

    boolean isCorruptedHeart(ItemStack item);

    double getLootChancePercent(String lootKey);
}
