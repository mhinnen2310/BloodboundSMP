package nl.mitchsmp.core.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public interface EconomyWatchService {
    double quickSellPrice(Material material);

    double quickSellPrice(ItemStack item);

    double basePrice(Material material);

    double moneyMultiplier();

    double scarcityMultiplier(Material material);

    int onlineStock(Material material);

    double supplyScore(Material material);

    void recordItemSignal(Material material, int amount, String source);

    void recordSale(Material material, int amount, double total, String source);

    String explain(Material material);
}
