package org.bukkit;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import java.util.List;

public interface World {
    enum Environment {
        NORMAL,
        NETHER,
        THE_END,
        CUSTOM
    }

    String getName();

    Environment getEnvironment();

    int getMinHeight();

    Location getSpawnLocation();

    void setTime(long time);

    void setStorm(boolean storm);

    boolean createExplosion(Location location, float power, boolean setFire, boolean breakBlocks);

    Block getHighestBlockAt(int x, int z);

    Block getBlockAt(int x, int y, int z);

    Entity spawnEntity(Location location, EntityType type);

    List<Entity> getEntities();

    void spawnParticle(Particle particle, Location location, int count);

    void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra);

    <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data);

    Item dropItemNaturally(Location location, ItemStack item);
}
