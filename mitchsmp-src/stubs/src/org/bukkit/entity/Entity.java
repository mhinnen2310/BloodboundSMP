package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.UUID;
import org.bukkit.persistence.PersistentDataContainer;

public interface Entity {
    Location getLocation();

    World getWorld();

    EntityType getType();

    UUID getUniqueId();

    boolean teleport(Location location);

    void setFireTicks(int ticks);

    void remove();

    PersistentDataContainer getPersistentDataContainer();
}
