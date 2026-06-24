package nl.mitchsmp.core.api;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

public interface InventorySnapshotService {
    String capture(Player player, String trigger);

    List<SnapshotInfo> list(UUID playerId, int limit);

    boolean restore(Player player, String snapshotId);

    record SnapshotInfo(String id, long timestamp, String trigger, String world, String location, String gameMode, boolean adminMode) {
    }
}
