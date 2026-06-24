package nl.mitchsmp.lifesteal;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class LifestealPlugin extends JavaPlugin implements Listener, HeartService, TabCompleter {
    private static final int START_HEARTS = 10;
    private static final int MIN_HEARTS = 1;
    private static final int MAX_HEARTS = 20;

    private PropertiesFile hearts;

    @Override
    public void onEnable() {
        hearts = new PropertiesFile(getDataFolder().toPath().resolve("hearts.properties"));
        MitchSMP.registerService(HeartService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        command("hearts");
        command("sethearts");

        for (Player player : Bukkit.getOnlinePlayers()) {
            ensureKnown(player.getUniqueId());
            apply(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensureKnown(event.getPlayer().getUniqueId());
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (isBedWarsWorld(event.getPlayer())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> apply(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (isBedWarsWorld(victim) || isBedWarsWorld(killer) || MitchSMP.permissions().isAdminRestricted(victim) || MitchSMP.permissions().isAdminRestricted(killer)) {
            return;
        }

        int newVictimHearts = Math.max(MIN_HEARTS, getHearts(victim.getUniqueId()) - 1);
        setHearts(victim.getUniqueId(), newVictimHearts);
        victim.sendMessage(Text.PREFIX + Text.color("&cYou lost 1 heart. You now have &f" + newVictimHearts + " &chearts."));

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            String farmReason = heartFarmBlockReason(killer, victim);
            if (farmReason != null) {
                killer.sendMessage(Text.PREFIX + Text.color("&eGeen heart gain: &f" + farmReason + "&e."));
                hearts.set("blockedGain." + killer.getUniqueId(), hearts.getInt("blockedGain." + killer.getUniqueId(), 0) + 1);
                hearts.save();
                return;
            }
            int newKillerHearts = Math.min(MAX_HEARTS, getHearts(killer.getUniqueId()) + 1);
            setHearts(killer.getUniqueId(), newKillerHearts);
            recordHeartGain(killer, victim);
            apply(killer);
            killer.sendMessage(Text.PREFIX + Text.color("&aYou gained 1 heart. You now have &f" + newKillerHearts + " &ahearts."));
        }
    }

    @Override
    public int getHearts(UUID playerId) {
        ensureKnown(playerId);
        return clamp(hearts.getInt(playerId.toString(), START_HEARTS));
    }

    @Override
    public void setHearts(UUID playerId, int amount) {
        int clamped = clamp(amount);
        hearts.set(playerId.toString(), clamped);
        hearts.save();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            apply(player);
        }
    }

    @Override
    public void apply(Player player) {
        int amount = getHearts(player.getUniqueId());
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(amount * 2.0D);
        }
        if (player.getHealth() > amount * 2.0D) {
            player.setHealth(amount * 2.0D);
        }
    }

    @Override
    public List<Map.Entry<UUID, Integer>> topHearts(int limit) {
        return hearts.keys().stream()
            .map(this::entry)
            .filter(entry -> entry != null)
            .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(limit)
            .toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("hearts")) {
            Player target;
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    Text.msg(sender, "&cGebruik: /hearts <player>");
                    return true;
                }
                target = player;
            } else {
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    Text.msg(sender, "&cSpeler niet online.");
                    return true;
                }
            }
            Text.msg(sender, "&f" + target.getName() + " &7heeft &c" + getHearts(target.getUniqueId()) + " &7hearts.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("sethearts")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.lifesteal.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
                return config(sender, args);
            }
            if (args.length < 2) {
                Text.msg(sender, "&cGebruik: /sethearts <player> <1-20> of /sethearts config <key> <value>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Text.msg(sender, "&cSpeler niet online.");
                return true;
            }
            try {
                int value = Integer.parseInt(args[1]);
                setHearts(target.getUniqueId(), value);
                Text.msg(sender, "&aHearts van &f" + target.getName() + " &agezet naar &f" + clamp(value) + "&a.");
            } catch (NumberFormatException exception) {
                Text.msg(sender, "&cAmount moet een nummer zijn.");
            }
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("hearts")) {
            return args.length == 1 ? Tab.onlinePlayers(args[0]) : List.of();
        }
        if (command.getName().equalsIgnoreCase("sethearts")) {
            if (args.length == 1) {
                List<String> result = new java.util.ArrayList<>(Tab.onlinePlayers(args[0]));
                result.addAll(Tab.complete(args[0], "config"));
                return result;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
                return Tab.complete(args[1], "farmCooldownMinutes", "maxHeartTransfersPerPairDay");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
                return Tab.complete(args[2], "0", "1", "2", "5", "15", "30", "60");
            }
            if (args.length == 1) {
                return Tab.onlinePlayers(args[0]);
            }
            return args.length == 2 ? Tab.complete(args[1], "1", "5", "10", "15", "20") : List.of();
        }
        return List.of();
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private void ensureKnown(UUID playerId) {
        if (!hearts.contains(playerId.toString())) {
            hearts.set(playerId.toString(), START_HEARTS);
            hearts.save();
        }
    }

    private int clamp(int amount) {
        return Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, amount));
    }

    private String heartFarmBlockReason(Player killer, Player victim) {
        long now = System.currentTimeMillis();
        String pair = pairKey(killer.getUniqueId(), victim.getUniqueId());
        long cooldown = Math.round(setting("farmCooldownMinutes", 30.0D) * 60_000.0D);
        long last = hearts.getLong("farmPair." + pair + ".last", 0L);
        if (cooldown > 0L && now - last < cooldown) {
            return "anti-farm cooldown between these players";
        }
        String dayKey = "farmDay." + LocalDate.now() + "." + pair;
        int limit = Math.max(0, (int) Math.round(setting("maxHeartTransfersPerPairDay", 2.0D)));
        if (limit > 0 && hearts.getInt(dayKey, 0) >= limit) {
            return "daglimiet heart transfers for dit spelerspaar";
        }
        return null;
    }

    private void recordHeartGain(Player killer, Player victim) {
        String pair = pairKey(killer.getUniqueId(), victim.getUniqueId());
        String dayKey = "farmDay." + LocalDate.now() + "." + pair;
        hearts.set("farmPair." + pair + ".last", System.currentTimeMillis());
        hearts.set(dayKey, hearts.getInt(dayKey, 0) + 1);
        purgeOldFarmDays();
        hearts.save();
    }

    private String pairKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + "." + b : b + "." + a;
    }

    private double setting(String key, double fallback) {
        return hearts.getDouble("setting." + key, fallback);
    }

    private boolean config(CommandSender sender, String[] args) {
        if (args.length == 1) {
            Text.msg(sender, "&cGebruik: /sethearts config <farmCooldownMinutes|maxHeartTransfersPerPairDay> <value>");
            return true;
        }
        if (args.length == 2) {
            Text.msg(sender, "&6Lifesteal anti-farm:");
            Text.msg(sender, "&7farmCooldownMinutes: &f" + format(setting("farmCooldownMinutes", 30.0D)));
            Text.msg(sender, "&7maxHeartTransfersPerPairDay: &f" + format(setting("maxHeartTransfersPerPairDay", 2.0D)));
            return true;
        }
        String key = args[1].toLowerCase(java.util.Locale.ROOT);
        String realKey = switch (key) {
            case "farmcooldownminutes" -> "farmCooldownMinutes";
            case "maxhearttransfersperpairday" -> "maxHeartTransfersPerPairDay";
            default -> null;
        };
        if (realKey == null) {
            Text.msg(sender, "&cUnknowne setting.");
            return true;
        }
        try {
            double value = Math.max(0.0D, Double.parseDouble(args[2]));
            hearts.set("setting." + realKey, value);
            hearts.save();
            Text.msg(sender, "&aLifesteal setting &f" + realKey + " &agezet naar &f" + format(value) + "&a.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue moet een nummer zijn.");
        }
        return true;
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private void purgeOldFarmDays() {
        String todayPrefix = "farmDay." + LocalDate.now() + ".";
        for (String key : hearts.keys()) {
            if (key.startsWith("farmDay.") && !key.startsWith(todayPrefix)) {
                hearts.set(key, null);
            }
        }
    }

    private boolean isBedWarsWorld(Player player) {
        return player != null
            && player.getWorld() != null
            && (player.getWorld().getName().toLowerCase(java.util.Locale.ROOT).startsWith("bedwars_")
                || player.getWorld().getName().toLowerCase(java.util.Locale.ROOT).startsWith("mitchtest_"));
    }

    private Map.Entry<UUID, Integer> entry(String key) {
        try {
            UUID id = UUID.fromString(key);
            return new AbstractMap.SimpleEntry<>(id, getHearts(id));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}



