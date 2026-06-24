package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

public class EntitySpawnEvent implements Cancellable {
    public Entity getEntity() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancelled) { }
}
