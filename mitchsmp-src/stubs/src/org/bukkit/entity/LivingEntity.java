package org.bukkit.entity;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;

public interface LivingEntity extends Entity {
    AttributeInstance getAttribute(Attribute attribute);

    void setHealth(double health);

    boolean addPotionEffect(PotionEffect effect);

    double getHealth();

    void setNoDamageTicks(int ticks);

    Player getKiller();

    String getCustomName();

    void setCustomName(String name);

    void setCustomNameVisible(boolean visible);

    EntityEquipment getEquipment();
}
