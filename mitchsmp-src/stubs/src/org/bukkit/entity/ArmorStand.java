package org.bukkit.entity;

import org.bukkit.inventory.EntityEquipment;

public interface ArmorStand extends LivingEntity {
    void setArms(boolean arms);

    void setBasePlate(boolean basePlate);

    void setGravity(boolean gravity);

    void setVisible(boolean visible);

    void setCustomName(String name);

    void setCustomNameVisible(boolean visible);

    EntityEquipment getEquipment();
}
