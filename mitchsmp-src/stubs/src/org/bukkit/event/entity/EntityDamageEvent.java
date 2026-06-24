package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

public class EntityDamageEvent implements Cancellable {
    public enum DamageCause {
        CUSTOM,
        ENTITY_ATTACK,
        FALL,
        VOID
    }

    public Entity getEntity() {
        return null;
    }

    public DamageCause getCause() {
        return DamageCause.CUSTOM;
    }

    public double getFinalDamage() {
        return 0.0D;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
