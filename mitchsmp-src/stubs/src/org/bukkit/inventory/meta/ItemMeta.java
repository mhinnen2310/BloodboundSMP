package org.bukkit.inventory.meta;

import java.util.List;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;

public interface ItemMeta {
    void setDisplayName(String name);

    String getDisplayName();

    boolean hasDisplayName();

    List<String> getLore();

    void setLore(List<String> lore);

    boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction);

    void setCustomModelData(Integer data);

    void setItemModel(NamespacedKey model);

    int getCustomModelData();

    void setUnbreakable(boolean unbreakable);

    void addItemFlags(ItemFlag... flags);

    PersistentDataContainer getPersistentDataContainer();
}
