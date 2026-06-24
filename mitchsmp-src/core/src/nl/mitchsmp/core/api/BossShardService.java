package nl.mitchsmp.core.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface BossShardService {
    ItemStack createShard(int amount);

    boolean isShard(ItemStack item);

    void giveShards(Player player, int amount);
}
