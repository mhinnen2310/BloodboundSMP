package org.bukkit.event.block;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class SignChangeEvent implements Cancellable {
    public Player getPlayer() {
        return null;
    }

    public String getLine(int index) {
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
