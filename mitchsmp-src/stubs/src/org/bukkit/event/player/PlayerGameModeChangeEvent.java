package org.bukkit.event.player;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class PlayerGameModeChangeEvent {
    public Player getPlayer() {
        return null;
    }

    public GameMode getNewGameMode() {
        return GameMode.SURVIVAL;
    }
}
