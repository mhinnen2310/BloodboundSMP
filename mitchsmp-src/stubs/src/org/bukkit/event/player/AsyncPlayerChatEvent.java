package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class AsyncPlayerChatEvent implements Cancellable {
    public Player getPlayer() {
        return null;
    }

    public String getMessage() {
        return "";
    }

    public void setMessage(String message) {
    }

    public void setFormat(String format) {
    }

    public String getFormat() {
        return "%1$s: %2$s";
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
