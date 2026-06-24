package nl.mitchsmp.core.api;

import java.util.UUID;

public interface SkillService {
    void addXp(UUID playerId, String category, int amount, String reason);

    int getPerkLevel(UUID playerId, String perkKey);

    String getAbilityHud(UUID playerId);
}
