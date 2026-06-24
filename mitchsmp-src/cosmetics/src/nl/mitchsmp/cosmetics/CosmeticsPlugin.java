package nl.mitchsmp.cosmetics;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

import nl.mitchsmp.core.api.CosmeticService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CosmeticsPlugin extends JavaPlugin implements Listener, CosmeticService, TabCompleter {
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("cosmetics.properties"));
        MitchSMP.registerService(CosmeticService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        command("cosmetics");
        command("emote");
        Bukkit.getScheduler().runTaskTimer(this, this::trails, 10L, 10L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, event.getPlayer().getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.02);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killer.getWorld().spawnParticle(Particle.EXPLOSION, event.getEntity().getLocation(), 1);
            killer.getWorld().spawnParticle(Particle.FLAME, event.getEntity().getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.02);
        }
    }

    @Override
    public String tag(UUID playerId) {
        String raw = data.getString("tag." + playerId, "");
        if (raw.isBlank()) {
            return "";
        }
        return Text.color("&8[&f" + raw + "&8] ");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("emote")) {
            emote(player, args);
            return true;
        }
        if (args.length < 1) {
            Text.msg(player, "&7/cos tag <text|off>, /cos trail <flame|heart|crit|off>");
            return true;
        }
        if (args[0].equalsIgnoreCase("tag")) {
            if (args.length < 2) {
                Text.msg(player, "&cGebruik: /cos tag <text|off>");
                return true;
            }
            String value = args[1];
            if (value.equalsIgnoreCase("off")) {
                data.set("tag." + player.getUniqueId(), null);
                data.save();
                Text.msg(player, "&aTag uitgezet.");
                return true;
            }
            if (value.length() > 16) {
                Text.msg(player, "&cTag mag maximaal 16 tekens zijn.");
                return true;
            }
            data.set("tag." + player.getUniqueId(), value.replaceAll("[^A-Za-z0-9_&]", ""));
            data.save();
            Text.msg(player, "&aTag ingesteld.");
            return true;
        }
        if (args[0].equalsIgnoreCase("trail")) {
            if (args.length < 2) {
                Text.msg(player, "&cGebruik: /cos trail <flame|heart|crit|off>");
                return true;
            }
            String trail = args[1].toLowerCase(Locale.ROOT);
            if (!trail.matches("flame|heart|crit|off")) {
                Text.msg(player, "&cTrail moet flame, heart, crit of off zijn.");
                return true;
            }
            data.set("trail." + player.getUniqueId(), trail.equals("off") ? null : trail);
            data.save();
            Text.msg(player, "&aTrail bijgewerkt.");
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("emote")) {
            return args.length == 1 ? Tab.complete(args[0], "wave", "dance", "laugh") : List.of();
        }
        if (args.length == 1) {
            return Tab.complete(args[0], "tag", "trail");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tag")) {
            return Tab.complete(args[1], "off", "Mitch", "PvP", "OG");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trail")) {
            return Tab.complete(args[1], "flame", "heart", "crit", "off");
        }
        return List.of();
    }

    private void emote(Player player, String[] args) {
        if (args.length < 1) {
            Text.msg(player, "&cGebruik: /emote <wave|dance|laugh>");
            return;
        }
        String emote = args[0].toLowerCase(Locale.ROOT);
        switch (emote) {
            case "wave" -> Bukkit.broadcastMessage(Text.PREFIX + Text.color("&f" + player.getName() + " &7zwaait."));
            case "dance" -> {
                Bukkit.broadcastMessage(Text.PREFIX + Text.color("&f" + player.getName() + " &7danst."));
                player.getWorld().spawnParticle(Particle.NOTE, player.getLocation().add(0, 2, 0), 10, 0.4, 0.4, 0.4, 0.05);
            }
            case "laugh" -> Bukkit.broadcastMessage(Text.PREFIX + Text.color("&f" + player.getName() + " &7lacht."));
            default -> Text.msg(player, "&cUnknowne emote.");
        }
    }

    private void trails() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String trail = data.getString("trail." + player.getUniqueId(), "");
            Particle particle = switch (trail) {
                case "flame" -> Particle.FLAME;
                case "heart" -> Particle.HEART;
                case "crit" -> Particle.CRIT;
                default -> null;
            };
            if (particle != null) {
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 0.2, 0), 3, 0.25, 0.1, 0.25, 0.0);
            }
        }
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }
}


