package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
public class EntityDamageByEntityEvent extends EntityDamageEvent {
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
