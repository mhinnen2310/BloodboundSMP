package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

public class EntityDamageByEntityEvent implements Cancellable {
    public Entity getEntity() {
        return null;
    }

    public Entity getDamager() {
        return null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
