package org.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

public final class Bukkit {
    private Bukkit() {
    }

    public static PluginManager getPluginManager() {
        return null;
    }

    public static BukkitScheduler getScheduler() {
        return null;
    }

    public static boolean isPrimaryThread() {
        return true;
    }

    public static Collection<? extends Player> getOnlinePlayers() {
        return List.of();
    }

    public static Player getPlayer(UUID id) {
        return null;
    }

    public static Player getPlayerExact(String name) {
        return null;
    }

    public static OfflinePlayer getOfflinePlayer(UUID id) {
        return null;
    }

    public static OfflinePlayer getOfflinePlayerIfCached(String name) {
        return null;
    }

    public static List<World> getWorlds() {
        return List.of();
    }

    public static World getWorld(String name) {
        return null;
    }

    public static int broadcastMessage(String message) {
        return 0;
    }

    public static boolean dispatchCommand(org.bukkit.command.CommandSender sender, String commandLine) {
        return true;
    }

    public static ConsoleCommandSender getConsoleSender() {
        return null;
    }

    public static Inventory createInventory(InventoryHolder owner, int size, String title) {
        return null;
    }

    public static BlockData createBlockData(Material material) {
        return null;
    }

    public static BlockData createBlockData(String data) {
        return null;
    }
}
