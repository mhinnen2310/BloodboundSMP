package nl.mitchsmp.core.api;

import java.util.UUID;

public interface CombatTagService {
    boolean isTagged(UUID playerId);

    long remainingMillis(UUID playerId);

    void tag(UUID playerId, UUID opponentId);
}
