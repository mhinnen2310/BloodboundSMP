package nl.mitchsmp.core.api;

import java.util.UUID;

public interface BountyService {
    double getBounty(UUID playerId);

    String getLastSource(UUID playerId);
}
