package nl.mitchsmp.homes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import nl.mitchsmp.core.api.CombatTagService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomesPlugin extends JavaPlugin implements TabCompleter {
    private PropertiesFile homes;

    @Override
    public void onEnable() {
        homes = new PropertiesFile(getDataFolder().toPath().resolve("homes.properties"));
        command("sethome");
        command("home");
        command("homes");
        command("delhome");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (blocked(player)) {
            Text.msg(player, "&cHomes kunnen niet tijdens combat.");
            return true;
        }

        String name = command.getName().toLowerCase();
        if (name.equals("sethome")) {
            String homeName = normalize(args.length == 0 ? "home" : args[0]);
            List<String> list = list(player.getUniqueId());
            int limit = MitchSMP.ranks().getHomeLimit(player.getUniqueId());
            if (!list.contains(homeName) && list.size() >= limit) {
                Text.msg(player, "&cJe hebt al het maximum van &f" + limit + " &chomes.");
                return true;
            }
            homes.set(key(player.getUniqueId(), homeName), encode(player.getLocation()));
            homes.save();
            Text.msg(player, "&aHome &f" + homeName + " &aopgeslagen.");
            return true;
        }

        if (name.equals("home")) {
            String homeName = normalize(args.length == 0 ? "home" : args[0]);
            Location location = decode(homes.getString(key(player.getUniqueId(), homeName), ""));
            if (location == null) {
                Text.msg(player, "&cHome not found.");
                return true;
            }
            player.teleport(location);
            Text.msg(player, "&aGeteleporteerd naar &f" + homeName + "&a.");
            return true;
        }

        if (name.equals("homes")) {
            Text.msg(player, "&7Homes: &f" + String.join("&7, &f", list(player.getUniqueId())));
            return true;
        }

        if (name.equals("delhome")) {
            String homeName = normalize(args.length == 0 ? "home" : args[0]);
            homes.set(key(player.getUniqueId(), homeName), null);
            homes.save();
            Text.msg(player, "&aHome verwijderd.");
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1 || args.length > 1) {
            return List.of();
        }
        String name = command.getName().toLowerCase();
        if (name.equals("home") || name.equals("delhome")) {
            return Tab.complete(args[0], list(player.getUniqueId()));
        }
        if (name.equals("sethome")) {
            return Tab.complete(args[0], "home", "base", "farm", "mine");
        }
        return List.of();
    }

    private boolean blocked(Player player) {
        CombatTagService combat = MitchSMP.combatTags();
        return combat != null && combat.isTagged(player.getUniqueId()) && !MitchSMP.permissions().has(player, "mitchsmp.homes.bypass");
    }

    private List<String> list(UUID playerId) {
        String prefix = playerId + ".";
        List<String> result = new ArrayList<>();
        for (String key : homes.keys()) {
            if (key.startsWith(prefix)) {
                result.add(key.substring(prefix.length()));
            }
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    private String key(UUID playerId, String name) {
        return playerId + "." + normalize(name);
    }

    private String normalize(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.isBlank() ? "home" : normalized;
    }

    private String encode(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location decode(String encoded) {
        String[] parts = encoded.split(";");
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }
}


