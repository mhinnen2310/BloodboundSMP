package nl.mitchsmp.core.api;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

public interface GameplayService {
    boolean isRookie(UUID playerId);

    void markTradeRestricted(ItemStack item, String reason);

    boolean isTradeRestricted(ItemStack item);

    String restrictionReason(ItemStack item);

    default void recordSkirmishParticipation(UUID playerId) {
    }
}
