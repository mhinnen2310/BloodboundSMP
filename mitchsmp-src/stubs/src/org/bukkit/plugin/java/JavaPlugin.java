package org.bukkit.plugin.java;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

public class JavaPlugin implements Plugin, CommandExecutor {
    public void onEnable() {
    }

    public void onDisable() {
    }

    public File getDataFolder() {
        return null;
    }

    public void saveDefaultConfig() {
    }

    public PluginCommand getCommand(String name) {
        return null;
    }

    public Logger getLogger() {
        return Logger.getLogger("stub");
    }

    public PluginDescriptionFile getDescription() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }
}
