package org.bukkit.inventory;

public interface PlayerInventory extends Inventory {
    ItemStack getItemInMainHand();

    void setItemInMainHand(ItemStack item);

    ItemStack getItemInOffHand();

    void setItemInOffHand(ItemStack item);

    ItemStack getHelmet();

    ItemStack getChestplate();

    ItemStack getLeggings();

    ItemStack getBoots();
}
