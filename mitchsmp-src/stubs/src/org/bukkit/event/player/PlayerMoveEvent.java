package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerMoveEvent implements Cancellable {
    public Player getPlayer() {
        return null;
    }

    public Location getFrom() {
        return null;
    }

    public Location getTo() {
        return null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
