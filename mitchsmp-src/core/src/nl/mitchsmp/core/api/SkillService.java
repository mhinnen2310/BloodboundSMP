package nl.mitchsmp.core.api;

import java.util.List;
import java.util.UUID;

public interface SkillService {
    void addXp(UUID playerId, String category, int amount, String reason);

    int getPerkLevel(UUID playerId, String perkKey);

    String getAbilityHud(UUID playerId);

    default List<String> getAbilityHudLines(UUID playerId) {
        String line = getAbilityHud(playerId);
        return line == null || line.isBlank() ? List.of() : List.of(line);
    }
}
