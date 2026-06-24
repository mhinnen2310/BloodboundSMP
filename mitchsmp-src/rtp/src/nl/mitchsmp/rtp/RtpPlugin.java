package nl.mitchsmp.rtp;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import nl.mitchsmp.core.api.CombatTagService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpPlugin extends JavaPlugin implements TabCompleter {
    private static final int DEFAULT_RADIUS = 5000;
    private static final int MAX_RADIUS = 10000;
    private static final long COOLDOWN_MILLIS = 60_000L;

    private final Random random = new Random();
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("cooldowns.properties"));
        if (getCommand("rtp") != null) {
            getCommand("rtp").setExecutor(this);
            getCommand("rtp").setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.rtp.use")) {
            Text.msg(player, "&cGeen permissie.");
            return true;
        }
        if (blocked(player)) {
            Text.msg(player, "&cRTP kan niet tijdens combat.");
            return true;
        }
        long remaining = remainingCooldown(player);
        if (remaining > 0 && !MitchSMP.permissions().has(player, "mitchsmp.rtp.bypass")) {
            Text.msg(player, "&cWacht nog &f" + ((remaining + 999L) / 1000L) + " &cseconden for RTP.");
            return true;
        }

        int radius = radius(args);
        World targetWorld = targetWorld(player);
        Location location = findLocation(targetWorld, radius);
        if (location == null) {
            Text.msg(player, "&cKon geen veilige RTP plek vinden. Probeer opnieuw.");
            return true;
        }

        data.set("last." + player.getUniqueId(), System.currentTimeMillis());
        data.save();
        player.teleport(location);
        Text.msg(player, "&aRandom teleport naar &fX:" + location.getBlockX() + " Z:" + location.getBlockZ() + "&a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Tab.complete(args[0], "1000", "2500", "5000", "10000") : List.of();
    }

    private Location findLocation(World world, int radius) {
        if (world == null) {
            return null;
        }
        Location spawn = world.getSpawnLocation();
        for (int attempt = 0; attempt < 35; attempt++) {
            int x = spawn.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int z = spawn.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            Block block = world.getHighestBlockAt(x, z);
            if (!safe(block)) {
                continue;
            }
            Location base = block.getLocation();
            return new Location(world, base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
        }
        return null;
    }

    private World targetWorld(Player player) {
        PropertiesFile shared = new PropertiesFile(Path.of("plugins", "MitchSMP-Essentials", "essentials.properties"));
        String configured = shared.getString("smpworld.active", "world");
        World world = Bukkit.getWorld(configured);
        if (world != null) {
            return world;
        }
        World defaultWorld = Bukkit.getWorld("world");
        return defaultWorld == null ? player.getWorld() : defaultWorld;
    }

    private boolean safe(Block block) {
        if (block == null || block.getType() == null) {
            return false;
        }
        String type = block.getType().name().toLowerCase(Locale.ROOT);
        return !type.contains("water")
            && !type.contains("lava")
            && !type.contains("fire")
            && !type.contains("cactus")
            && !type.contains("magma")
            && !type.contains("powder_snow")
            && block.getType() != Material.AIR;
    }

    private boolean blocked(Player player) {
        CombatTagService combat = MitchSMP.combatTags();
        return combat != null && combat.isTagged(player.getUniqueId()) && !MitchSMP.permissions().has(player, "mitchsmp.rtp.bypass");
    }

    private long remainingCooldown(Player player) {
        long last = data.getLong("last." + player.getUniqueId(), 0L);
        return Math.max(0L, last + COOLDOWN_MILLIS - System.currentTimeMillis());
    }

    private int radius(String[] args) {
        if (args.length == 0) {
            return DEFAULT_RADIUS;
        }
        try {
            return Math.max(100, Math.min(MAX_RADIUS, Integer.parseInt(args[0])));
        } catch (NumberFormatException exception) {
            return DEFAULT_RADIUS;
        }
    }
}


