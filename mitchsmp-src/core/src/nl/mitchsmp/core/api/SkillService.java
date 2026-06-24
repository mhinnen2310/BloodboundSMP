package nl.mitchsmp.core.api;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public interface SkillService {
    void addXp(UUID playerId, String category, int amount, String reason);

    int getPerkLevel(UUID playerId, String perkKey);

    String getAbilityHud(UUID playerId);

    default List<String> getAbilityHudLines(UUID playerId) {
        String line = getAbilityHud(playerId);
        return line == null || line.isBlank() ? List.of() : List.of(line);
    }

    default List<ItemStack> createRecoveryAbilitySamples(UUID playerId) {
        return List.of();
    }

    default ItemStack applyUnlockedAbility(String abilityKey, ItemStack item, String displayName) {
        return item;
    }
}
