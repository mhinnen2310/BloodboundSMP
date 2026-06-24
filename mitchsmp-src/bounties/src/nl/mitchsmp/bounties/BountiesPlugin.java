package nl.mitchsmp.bounties;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BountiesPlugin extends JavaPlugin implements Listener, TabCompleter {
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("bounties.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        command("bounty");
        command("bounties");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        if (isBedWarsWorld(victim) || isBedWarsWorld(killer) || MitchSMP.permissions().isAdminRestricted(victim) || MitchSMP.permissions().isAdminRestricted(killer)) {
            return;
        }
        double value = bounty(victim.getUniqueId());
        if (value <= 0.0D) {
            return;
        }
        String blockReason = bountyFarmBlockReason(killer, victim);
        if (blockReason != null) {
            Text.msg(killer, "&eBounty niet uitbetaald: &f" + blockReason + "&e.");
            data.set("blockedClaims." + killer.getUniqueId(), data.getInt("blockedClaims." + killer.getUniqueId(), 0) + 1);
            data.save();
            return;
        }
        EconomyService economy = MitchSMP.economy();
        if (economy != null) {
            economy.deposit(killer.getUniqueId(), value, "bounty " + victim.getName());
            Text.msg(killer, "&aJe claimde een bounty van &f$" + format(value) + " &aop &f" + victim.getName() + "&a.");
        }
        recordBountyClaim(killer, victim);
        data.set("manual." + victim.getUniqueId(), null);
        data.set("twentySince." + victim.getUniqueId(), null);
        data.set("claims." + killer.getUniqueId(), data.getInt("claims." + killer.getUniqueId(), 0) + 1);
        data.save();
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + killer.getName() + " claimde de bounty op " + victim.getName() + " ($" + format(value) + ")."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        HeartService hearts = MitchSMP.hearts();
        if (hearts == null) {
            Text.msg(sender, "&cLifesteal is niet geladen.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("bounty")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
                return config(sender, args);
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("place")) {
                return placeBounty(sender, args);
            }
            OfflinePlayer target;
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    Text.msg(sender, "&cGebruik: /bounty <player>");
                    return true;
                }
                target = player;
            } else {
                target = offlinePlayer(args[0]);
                if (target == null) {
                    Text.msg(sender, "&cUnknown player. They must have joined the server at least once.");
                    return true;
                }
            }
            Text.msg(sender, "&f" + safeName(target) + " &7has a bounty of &6$" + format(bounty(target.getUniqueId())) + "&7.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("bounties")) {
            Text.msg(sender, "&6Top bounties:");
            int index = 1;
            for (Map.Entry<UUID, Integer> entry : hearts.topHearts(10)) {
                double value = bounty(entry.getKey());
                if (value <= 0.0D) {
                    continue;
                }
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                Text.msg(sender, "&7#" + index++ + " &f" + player.getName() + " &7- &c" + entry.getValue() + " hearts &7- &6$" + format(value));
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bounty") && args.length == 1) {
            List<String> result = new java.util.ArrayList<>(Tab.onlinePlayers(args[0]));
            result.addAll(Tab.complete(args[0], "place"));
            if (MitchSMP.permissions().has(sender, "mitchsmp.bounties.admin")) {
                result.addAll(Tab.complete(args[0], "config"));
            }
            return result;
        }
        if (command.getName().equalsIgnoreCase("bounty") && args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (command.getName().equalsIgnoreCase("bounty") && args.length == 3 && args[0].equalsIgnoreCase("place")) {
            return Tab.complete(args[2], "100", "250", "500", "1000", "5000");
        }
        if (command.getName().equalsIgnoreCase("bounty") && args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[1], "basePerHeart", "twentyBonusPerHour", "twentyBonusCap", "minimumPlacedBounty", "pairCooldownMinutes", "victimCooldownMinutes", "maxClaimsPerPairDay");
        }
        if (command.getName().equalsIgnoreCase("bounty") && args.length == 3 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[2], "100", "150", "250", "500", "1000", "5000");
        }
        return List.of();
    }

    private double bounty(UUID playerId) {
        HeartService hearts = MitchSMP.hearts();
        if (hearts == null) {
            return 0.0D;
        }
        int heartCount = hearts.getHearts(playerId);
        if (heartCount < 20) {
            data.set("twentySince." + playerId, null);
        } else if (!data.contains("twentySince." + playerId)) {
            data.set("twentySince." + playerId, System.currentTimeMillis());
        }
        data.save();
        double manual = Math.max(0.0D, data.getDouble("manual." + playerId, 0.0D));
        double base = Math.max(0, heartCount - 10) * setting("basePerHeart", 150.0D) + manual;
        if (heartCount < 20) {
            return base;
        }
        long since = data.getLong("twentySince." + playerId, System.currentTimeMillis());
        double hours = Math.max(0.0D, (System.currentTimeMillis() - since) / 3_600_000.0D);
        double bonus = Math.min(setting("twentyBonusCap", 5000.0D), hours * setting("twentyBonusPerHour", 250.0D));
        return base + bonus;
    }

    private boolean placeBounty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (MitchSMP.permissions().isAdminRestricted(player)) {
            Text.msg(player, "&cLeave admin mode before placing a bounty.");
            return true;
        }
        if (args.length < 3) {
            Text.msg(player, "&cUsage: /bounty place <player> <amount>");
            return true;
        }
        OfflinePlayer target = offlinePlayer(args[1]);
        if (target == null) {
            Text.msg(player, "&cUnknown player. They must have joined the server at least once.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Text.msg(player, "&cYou cannot place a bounty on yourself.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException exception) {
            Text.msg(player, "&cAmount must be a number.");
            return true;
        }
        double minimum = Math.max(1.0D, setting("minimumPlacedBounty", 100.0D));
        if (!Double.isFinite(amount) || amount < minimum) {
            Text.msg(player, "&cMinimum placed bounty: &f$" + format(minimum) + "&c.");
            return true;
        }
        EconomyService economy = MitchSMP.economy();
        if (economy == null || !economy.withdraw(player.getUniqueId(), amount, "placed bounty on " + safeName(target))) {
            Text.msg(player, "&cYou do not have enough money.");
            return true;
        }
        String key = "manual." + target.getUniqueId();
        data.set(key, data.getDouble(key, 0.0D) + amount);
        data.save();
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + player.getName() + " placed a &c$" + format(amount) + " &6bounty on &f" + safeName(target) + "&6."));
        return true;
    }

    private OfflinePlayer offlinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private String safeName(OfflinePlayer player) {
        return player == null || player.getName() == null ? "Unknown" : player.getName();
    }

    private boolean config(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.bounties.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length == 1) {
            Text.msg(sender, "&6Bounty tuning:");
            Text.msg(sender, "&7basePerHeart: &f" + format(setting("basePerHeart", 150.0D)));
            Text.msg(sender, "&7twentyBonusPerHour: &f" + format(setting("twentyBonusPerHour", 250.0D)));
            Text.msg(sender, "&7twentyBonusCap: &f" + format(setting("twentyBonusCap", 5000.0D)));
            Text.msg(sender, "&7minimumPlacedBounty: &f" + format(setting("minimumPlacedBounty", 100.0D)));
            Text.msg(sender, "&7pairCooldownMinutes: &f" + format(setting("pairCooldownMinutes", 60.0D)));
            Text.msg(sender, "&7victimCooldownMinutes: &f" + format(setting("victimCooldownMinutes", 15.0D)));
            Text.msg(sender, "&7maxClaimsPerPairDay: &f" + format(setting("maxClaimsPerPairDay", 1.0D)));
            return true;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cUsage: /bounty config <basePerHeart|twentyBonusPerHour|twentyBonusCap|minimumPlacedBounty|pairCooldownMinutes|victimCooldownMinutes|maxClaimsPerPairDay> <value>");
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!key.matches("baseperheart|twentybonusperhour|twentybonuscap|minimumplacedbounty|paircooldownminutes|victimcooldownminutes|maxclaimsperpairday")) {
            Text.msg(sender, "&cUnknowne setting.");
            return true;
        }
        try {
            double value = Math.max(0.0D, Double.parseDouble(args[2]));
            String realKey = switch (key) {
                case "baseperheart" -> "basePerHeart";
                case "twentybonusperhour" -> "twentyBonusPerHour";
                case "twentybonuscap" -> "twentyBonusCap";
                case "minimumplacedbounty" -> "minimumPlacedBounty";
                case "paircooldownminutes" -> "pairCooldownMinutes";
                case "victimcooldownminutes" -> "victimCooldownMinutes";
                default -> "maxClaimsPerPairDay";
            };
            data.set("setting." + realKey, value);
            data.save();
            Text.msg(sender, "&aBounty setting &f" + realKey + " &agezet naar &f" + format(value) + "&a.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue moet een nummer zijn.");
        }
        return true;
    }

    private double setting(String key, double fallback) {
        return data.getDouble("setting." + key, fallback);
    }

    private String bountyFarmBlockReason(Player killer, Player victim) {
        long now = System.currentTimeMillis();
        String pair = pairKey(killer.getUniqueId(), victim.getUniqueId());
        long pairCooldown = Math.round(setting("pairCooldownMinutes", 60.0D) * 60_000.0D);
        long lastPair = data.getLong("claimPair." + pair + ".last", 0L);
        if (pairCooldown > 0L && now - lastPair < pairCooldown) {
            return "same players are still in bounty cooldown";
        }
        long victimCooldown = Math.round(setting("victimCooldownMinutes", 15.0D) * 60_000.0D);
        long lastVictim = data.getLong("claimVictim." + victim.getUniqueId() + ".last", 0L);
        if (victimCooldown > 0L && now - lastVictim < victimCooldown) {
            return "deze target is net al geclaimd";
        }
        String dayKey = "claimDay." + LocalDate.now() + "." + pair;
        int limit = Math.max(0, (int) Math.round(setting("maxClaimsPerPairDay", 1.0D)));
        if (limit > 0 && data.getInt(dayKey, 0) >= limit) {
            return "daglimiet for dit spelerspaar bereikt";
        }
        return null;
    }

    private void recordBountyClaim(Player killer, Player victim) {
        String pair = pairKey(killer.getUniqueId(), victim.getUniqueId());
        String dayKey = "claimDay." + LocalDate.now() + "." + pair;
        data.set("claimPair." + pair + ".last", System.currentTimeMillis());
        data.set("claimVictim." + victim.getUniqueId() + ".last", System.currentTimeMillis());
        data.set(dayKey, data.getInt(dayKey, 0) + 1);
        purgeOldDailyClaims();
    }

    private String pairKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + "." + b : b + "." + a;
    }

    private void purgeOldDailyClaims() {
        String todayPrefix = "claimDay." + LocalDate.now() + ".";
        for (String key : data.keys()) {
            if (key.startsWith("claimDay.") && !key.startsWith(todayPrefix)) {
                data.set(key, null);
            }
        }
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private boolean isBedWarsWorld(Player player) {
        return player != null
            && player.getWorld() != null
            && player.getWorld().getName().toLowerCase(java.util.Locale.ROOT).startsWith("bedwars_");
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }
}



