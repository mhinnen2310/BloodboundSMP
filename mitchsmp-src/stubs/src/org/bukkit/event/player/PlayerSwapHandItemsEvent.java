package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerSwapHandItemsEvent implements Cancellable {
    public Player getPlayer() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancelled) { }
}
