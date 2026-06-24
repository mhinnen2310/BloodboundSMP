package org.bukkit.inventory;

public interface PlayerInventory extends Inventory {
    ItemStack getItemInMainHand();

    void setItemInMainHand(ItemStack item);

    ItemStack getItemInOffHand();

    void setItemInOffHand(ItemStack item);

    ItemStack getHelmet();
    void setHelmet(ItemStack item);

    ItemStack getChestplate();
    void setChestplate(ItemStack item);

    ItemStack getLeggings();
    void setLeggings(ItemStack item);

    ItemStack getBoots();
    void setBoots(ItemStack item);
}
