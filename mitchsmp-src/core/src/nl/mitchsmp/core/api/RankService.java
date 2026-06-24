package nl.mitchsmp.core.api;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

public interface RankService {
    MitchRank getRank(UUID playerId);

    default MitchRank getRank(Player player) {
        return getRank(player.getUniqueId());
    }

    void setRank(UUID playerId, MitchRank rank);

    String getPrefix(UUID playerId);

    int getHomeLimit(UUID playerId);

    Map<UUID, MitchRank> allRanks();
}
