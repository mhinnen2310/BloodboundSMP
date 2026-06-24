package nl.mitchsmp.core.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

public interface HeartService {
    int getHearts(UUID playerId);

    void setHearts(UUID playerId, int hearts);

    void apply(Player player);

    List<Map.Entry<UUID, Integer>> topHearts(int limit);
}
