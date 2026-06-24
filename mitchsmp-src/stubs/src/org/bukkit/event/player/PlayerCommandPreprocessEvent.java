package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerCommandPreprocessEvent implements Cancellable {
    public Player getPlayer() {
        return null;
    }

    public String getMessage() {
        return "";
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
