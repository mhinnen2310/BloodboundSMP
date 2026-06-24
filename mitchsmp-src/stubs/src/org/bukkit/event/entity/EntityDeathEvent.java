package org.bukkit.event.entity;

import java.util.List;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

public class EntityDeathEvent {
    public EntityType getEntityType() {
        return null;
    }

    public List<ItemStack> getDrops() {
        return null;
    }

    public LivingEntity getEntity() {
        return null;
    }
}
