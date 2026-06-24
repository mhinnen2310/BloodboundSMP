package io.papermc.paper.event.packet;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class UncheckedSignChangeEvent implements Cancellable {
    public Player getPlayer() {
        return null;
    }

    public List<?> lines() {
        return List.of();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void setCancelled(boolean cancelled) {
    }
}
