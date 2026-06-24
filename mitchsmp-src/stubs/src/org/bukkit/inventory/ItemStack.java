package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemStack implements Cloneable {
    public ItemStack(Material material) {
    }

    public ItemStack(Material material, int amount) {
    }

    public Material getType() {
        return Material.AIR;
    }

    public int getAmount() {
        return 0;
    }

    public void setAmount(int amount) {
    }

    public boolean hasItemMeta() {
        return false;
    }

    public ItemMeta getItemMeta() {
        return null;
    }

    public boolean setItemMeta(ItemMeta meta) {
        return true;
    }

    public boolean containsEnchantment(Enchantment enchantment) {
        return false;
    }

    public byte[] serializeAsBytes() {
        return new byte[0];
    }

    public static ItemStack deserializeBytes(byte[] bytes) {
        return null;
    }

    @Override
    public ItemStack clone() {
        return this;
    }
}
