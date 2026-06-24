package org.bukkit.entity;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

public interface Player extends HumanEntity, LivingEntity, CommandSender, OfflinePlayer, ProjectileSource {
    UUID getUniqueId();

    String getName();

    Location getEyeLocation();

    boolean teleport(Location location);

    void setFallDistance(float distance);

    void playSound(Location location, Sound sound, float volume, float pitch);

    void sendBlockChange(Location location, BlockData blockData);

    void sendBlockChange(Location location, Material material, byte data);

    void sendSignChange(Location location, String[] lines);

    void sendActionBar(String message);

    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void setLevel(int level);

    boolean isOp();

    void setGameMode(GameMode gameMode);

    void setPlayerListName(String name);

    PermissionAttachment addAttachment(Plugin plugin);

    void removeAttachment(PermissionAttachment attachment);

    PlayerInventory getInventory();

    Inventory getEnderChest();

    GameMode getGameMode();

    boolean isOnGround();

    float getFallDistance();

    boolean getAllowFlight();

    void setAllowFlight(boolean allowFlight);

    boolean isFlying();

    void setFlying(boolean flying);

    int getFoodLevel();

    void setFoodLevel(int foodLevel);

    float getSaturation();

    void setSaturation(float saturation);

    void setWalkSpeed(float value);

    void setFlySpeed(float value);

    boolean isGliding();

    boolean isInsideVehicle();

    boolean isSwimming();

    void hidePlayer(Plugin plugin, Player player);

    void showPlayer(Plugin plugin, Player player);

    void setSpectatorTarget(Entity entity);

    Entity getSpectatorTarget();

    void setScoreboard(org.bukkit.scoreboard.Scoreboard scoreboard);
}
