package nl.mitchsmp.seasons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeasonsPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final String[] KING_FRAMES = {"&6&l[KING]", "&e&l[KING]", "&f&l[KING]", "&e&l[KING]"};
    private PropertiesFile data;
    private Path hallOfFame;
    private int kingFrame;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("season.properties"));
        hallOfFame = getDataFolder().toPath().resolve("hall-of-fame.txt");
        if (!data.contains("started-at") && !data.contains("active")) {
            startSeason(30, defaultSeasonName(1));
        }
        if (getCommand("season") != null) {
            getCommand("season").setExecutor(this);
            getCommand("season").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::checkEnd, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::animateKing, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!seasonActive()) {
                Text.msg(sender, "&7Er is geen actieve season. Admins kunnen starten met &f/season start <days>&7.");
                return true;
            }
            long remaining = endAt() - System.currentTimeMillis();
            Text.msg(sender, "&aSeason #" + data.getInt("number", 1) + " &8- &6" + seasonName() + " &7ends in &f" + Math.max(0, remaining / DAY_MILLIS) + " &7days.");
            Text.msg(sender, "&7Gebruik &f/season top <hearts|money|kills|deaths> &7of &f/season stats&7.");
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            top(sender, args.length >= 2 ? args[1] : "hearts");
            return true;
        }
        if (args[0].equalsIgnoreCase("stats")) {
            stats(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("king")) {
            king(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            seasonList(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            return reset(sender, args);
        }
        if (args[0].equalsIgnoreCase("end")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.season.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            if (!seasonActive()) {
                Text.msg(sender, "&cEr is geen actieve season.");
                return true;
            }
            endSeason();
            Text.msg(sender, "&aSeason beeindigd. Start handmatig een nieuwe met &f/season start <days>&a.");
            return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.season.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            int days = 30;
            if (args.length >= 2) {
                try {
                    days = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException ignored) {
                }
            }
            String name = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : defaultSeasonName(data.getInt("number", 0) + 1);
            startSeason(days, name);
            Text.msg(sender, "&aStarted Season #" + data.getInt("number", 1) + ": &6" + seasonName() + " &afor &f" + days + " &adays.");
            return true;
        }
        if (args[0].equalsIgnoreCase("name")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.season.admin")) {
                Text.msg(sender, "&cYou do not have permission.");
                return true;
            }
            if (args.length < 2) {
                Text.msg(sender, "&7Current season name: &6" + seasonName() + "&7. Usage: &f/season name <name>");
                return true;
            }
            data.set("name", sanitizeSeasonName(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))));
            data.save();
            Text.msg(sender, "&aSeason name updated to &6" + seasonName() + "&a.");
            return true;
        }
        if (args[0].equalsIgnoreCase("delete")) {
            return deleteSeason(sender, args);
        }
        if (args[0].equalsIgnoreCase("purge")) {
            return purgeSeasons(sender);
        }
        Text.msg(sender, "&cUsage: /season, /season top <hearts|money|kills|deaths>, /season stats [player], /season king, /season list, /season name <name>, /season reset <seasonal|overall>, /season start <days> [name]|end|delete|purge");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "top", "stats", "king", "list", "name", "reset", "start", "end", "delete", "purge");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return Tab.complete(args[1], "hearts", "money", "kills", "deaths");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Tab.complete(args[1], "7", "14", "30", "60", "90");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Tab.complete(args[1], "seasonal", "overall");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return Tab.complete(args[1], String.valueOf(data.getInt("number", 1)), "1", "2", "3", "4", "5");
        }
        return List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isKing(event.getPlayer().getUniqueId())) {
            applyKingName(event.getPlayer());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (isKing(event.getPlayer().getUniqueId())) {
            event.setFormat(Text.color(KING_FRAMES[kingFrame % KING_FRAMES.length] + " ") + event.getFormat());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!seasonActive()) {
            return;
        }
        Player victim = event.getEntity();
        if (isBedWarsWorld(victim) || MitchSMP.permissions().isAdminRestricted(victim)) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer != null && (isBedWarsWorld(killer) || MitchSMP.permissions().isAdminRestricted(killer))) {
            return;
        }
        add("stats." + victim.getUniqueId() + ".deaths", 1);
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        add("stats." + killer.getUniqueId() + ".kills", 1);
        int streak = add("stats." + killer.getUniqueId() + ".streak", 1);
        data.set("stats." + victim.getUniqueId() + ".streak", 0);
        data.save();

        UUID oldKing = kingId();
        boolean killedKing = oldKing != null && oldKing.equals(victim.getUniqueId());
        if (killedKing || oldKing == null || kills(killer.getUniqueId()) > kills(oldKing)) {
            setKing(killer, killedKing);
        }
        if (streak == 5 || streak == 10 || streak == 15) {
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + killer.getName() + " heeft een &f" + streak + " killstreak&6."));
        }
    }

    private void top(CommandSender sender, String type) {
        if (type.equalsIgnoreCase("kills") || type.equalsIgnoreCase("deaths")) {
            boolean kills = type.equalsIgnoreCase("kills");
            Text.msg(sender, kills ? "&cSeason kills top:" : "&7Season deaths top:");
            int index = 1;
            for (Map.Entry<UUID, Integer> entry : topStats(kills ? "kills" : "deaths", 10)) {
                Text.msg(sender, line(index++, entry.getKey(), entry.getValue() + (kills ? " kills" : " deaths")));
            }
            return;
        }
        if (type.equalsIgnoreCase("money")) {
            EconomyService economy = MitchSMP.economy();
            if (economy == null) {
                Text.msg(sender, "&cEconomy is niet geladen.");
                return;
            }
            Text.msg(sender, "&aSeason money top:");
            int index = 1;
            for (Map.Entry<UUID, Double> entry : economy.topBalances(10)) {
                Text.msg(sender, line(index++, entry.getKey(), "$" + format(entry.getValue())));
            }
            return;
        }
        HeartService hearts = MitchSMP.hearts();
        if (hearts == null) {
            Text.msg(sender, "&cLifesteal is niet geladen.");
            return;
        }
        Text.msg(sender, "&cSeason hearts top:");
        int index = 1;
        for (Map.Entry<UUID, Integer> entry : hearts.topHearts(10)) {
            Text.msg(sender, line(index++, entry.getKey(), entry.getValue() + " hearts"));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        UUID targetId = sender instanceof Player player ? player.getUniqueId() : null;
        String name = sender instanceof Player player ? player.getName() : "Console";
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                targetId = target.getUniqueId();
                name = target.getName();
            } else {
                UUID known = MitchSMP.permissions().findKnownPlayer(args[1]);
                if (known != null) {
                    targetId = known;
                    name = Bukkit.getOfflinePlayer(known).getName();
                } else {
                    OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (cached != null && cached.getUniqueId() != null) {
                        targetId = cached.getUniqueId();
                        name = cached.getName();
                    }
                }
            }
        }
        if (targetId == null) {
            Text.msg(sender, "&cGebruik: /season stats <player>");
            return;
        }
        Text.msg(sender, "&aSeason stats van &f" + name + "&a:");
        Text.msg(sender, "&7Kills: &f" + kills(targetId) + " &7Deaths: &f" + deaths(targetId) + " &7Streak: &f" + streak(targetId));
        Text.msg(sender, isKing(targetId) ? "&6Status: KING" : "&7Status: speler");
    }

    private void king(CommandSender sender) {
        UUID king = kingId();
        if (king == null) {
            Text.msg(sender, "&7Er is nog geen King deze season.");
            return;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(king);
        Text.msg(sender, "&6Huidige King: &f" + player.getName() + " &7(" + kills(king) + " kills)");
    }

    private void seasonList(CommandSender sender) {
        Text.msg(sender, "&6Seasons:");
        Text.msg(sender, "&7Active: &f#" + data.getInt("number", 0) + " &6" + seasonName() + " &7status=&f" + (seasonActive() ? "running" : "paused/ended"));
        if (!Files.exists(hallOfFame)) {
            Text.msg(sender, "&7Hall of Fame tekstarchief is leeg.");
            return;
        }
        try {
            int shown = 0;
            for (String line : Files.readAllLines(hallOfFame)) {
                if (line.startsWith("Season #")) {
                    Text.msg(sender, "&f" + line);
                    shown++;
                }
            }
            if (shown == 0) {
                Text.msg(sender, "&7No old seasons found.");
            }
        } catch (IOException exception) {
            Text.msg(sender, "&cKon season list niet lezen.");
        }
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.season.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /season reset <seasonal|overall>");
            return true;
        }
        if (args[1].equalsIgnoreCase("seasonal")) {
            clearSeasonStats();
            data.set("king", null);
            data.save();
            Text.msg(sender, "&aSeasonal stats van de huidige season zijn gereset.");
            return true;
        }
        if (args[1].equalsIgnoreCase("overall")) {
            clearSeasonStats();
            data.set("king", null);
            data.save();
            trySyncVisualHall("progression reset overall");
            Text.msg(sender, "&aSeasonal stats zijn gereset en Progression overall reset is aangeroepen.");
            return true;
        }
        Text.msg(sender, "&cGebruik: /season reset <seasonal|overall>");
        return true;
    }

    private boolean deleteSeason(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.season.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /season delete <number>");
            return true;
        }
        int season;
        try {
            season = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cSeason moet een nummer zijn.");
            return true;
        }
        boolean removedText = removeSeasonFromHallFile(season);
        if (data.getInt("number", 0) == season) {
            clearSeasonStats();
            data.set("active", false);
            data.save();
        }
        trySyncVisualHall("legacy delete " + season);
        Text.msg(sender, removedText
            ? "&aSeason &f" + season + " &ais verwijderd uit season-data en HOF."
            : "&eNo text record found, but visual HOF deletion was attempted for season &f" + season + "&e.");
        return true;
    }

    private boolean purgeSeasons(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.season.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        try {
            Files.deleteIfExists(hallOfFame);
        } catch (IOException exception) {
            getLogger().warning("Could not delete season hall of fame: " + exception.getMessage());
        }
        clearSeasonStats();
        data.set("number", 0);
        data.set("started-at", null);
        data.set("duration-days", null);
        data.set("name", null);
        data.set("active", false);
        data.set("king", null);
        data.save();
        trySyncVisualHall("legacy purgehof");
        Text.msg(sender, "&aAlle test-seasons en HOF snapshots zijn opgeschoond. Start opnieuw met &f/season start <days>&a.");
        return true;
    }

    private void checkEnd() {
        if (seasonActive() && System.currentTimeMillis() >= endAt()) {
            endSeason();
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6Season beeindigd. Een nieuwe season start pas met /season start."));
        }
    }

    private void startSeason(int days, String name) {
        for (String key : new java.util.ArrayList<>(data.keys())) {
            if (key.startsWith("stats.")) {
                data.set(key, null);
            }
        }
        int number = data.getInt("number", 0) + 1;
        data.set("number", number);
        data.set("started-at", System.currentTimeMillis());
        data.set("duration-days", days);
        data.set("name", sanitizeSeasonName(name));
        data.set("active", true);
        data.set("king", null);
        data.save();
    }

    private void endSeason() {
        int seasonNumber = data.getInt("number", 1);
        String seasonName = seasonName();
        StringBuilder builder = new StringBuilder();
        builder.append("Season #").append(seasonNumber).append(" - ").append(seasonName).append(" ended at ").append(Instant.now()).append(System.lineSeparator());
        HeartService hearts = MitchSMP.hearts();
        if (hearts != null) {
            builder.append("Top hearts:").append(System.lineSeparator());
            appendEntries(builder, hearts.topHearts(5), " hearts");
        }
        EconomyService economy = MitchSMP.economy();
        if (economy != null) {
            builder.append("Top money:").append(System.lineSeparator());
            appendMoney(builder, economy.topBalances(5));
        }
        builder.append("Top kills:").append(System.lineSeparator());
        appendEntries(builder, topStats("kills", 5), " kills");
        builder.append("Top deaths:").append(System.lineSeparator());
        appendEntries(builder, topStats("deaths", 5), " deaths");
        builder.append(System.lineSeparator());
        try {
            Files.createDirectories(hallOfFame.getParent());
            Files.writeString(hallOfFame, builder.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            getLogger().warning("Could not write hall of fame: " + exception.getMessage());
        }
        data.set("active", false);
        data.save();
        trySyncVisualHall("legacy snapshot " + seasonNumber + " " + seasonName.replace(' ', '_'));
    }

    private String seasonName() {
        return data.getString("name", defaultSeasonName(Math.max(1, data.getInt("number", 1))));
    }

    private String defaultSeasonName(int number) {
        return switch (Math.floorMod(number - 1, 5)) {
            case 0 -> "Blood Dawn";
            case 1 -> "Crownfall";
            case 2 -> "Ashen Hunt";
            case 3 -> "Crimson Reckoning";
            default -> "Legacy of Ruin";
        };
    }

    private String sanitizeSeasonName(String input) {
        String cleaned = input == null ? "" : input.replaceAll("[\\r\\n;&]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? defaultSeasonName(Math.max(1, data.getInt("number", 1))) : cleaned.substring(0, Math.min(40, cleaned.length()));
    }

    private long endAt() {
        return data.getLong("started-at", System.currentTimeMillis()) + data.getInt("duration-days", 30) * DAY_MILLIS;
    }

    private boolean seasonActive() {
        return data.contains("started-at") && data.getString("active", "true").equalsIgnoreCase("true");
    }

    private void clearSeasonStats() {
        for (String key : new java.util.ArrayList<>(data.keys())) {
            if (key.startsWith("stats.")) {
                data.set(key, null);
            }
        }
    }

    private boolean removeSeasonFromHallFile(int season) {
        if (!Files.exists(hallOfFame)) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(hallOfFame);
            List<String> kept = new java.util.ArrayList<>();
            boolean skipping = false;
            boolean removed = false;
            String header = "Season #" + season + " ";
            for (String line : lines) {
                if (line.startsWith("Season #")) {
                    skipping = line.startsWith(header);
                    if (skipping) {
                        removed = true;
                        continue;
                    }
                }
                if (!skipping) {
                    kept.add(line);
                }
            }
            if (removed) {
                Files.writeString(hallOfFame, String.join(System.lineSeparator(), kept) + (kept.isEmpty() ? "" : System.lineSeparator()));
            }
            return removed;
        } catch (IOException exception) {
            getLogger().warning("Could not edit hall of fame: " + exception.getMessage());
            return false;
        }
    }

    private void trySyncVisualHall(String command) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException exception) {
            getLogger().warning("Could not sync visual Hall of Fame: " + exception.getMessage());
        }
    }

    private int add(String key, int amount) {
        int value = data.getInt(key, 0) + amount;
        data.set(key, value);
        return value;
    }

    private int kills(UUID id) {
        return data.getInt("stats." + id + ".kills", 0);
    }

    private int deaths(UUID id) {
        return data.getInt("stats." + id + ".deaths", 0);
    }

    private int streak(UUID id) {
        return data.getInt("stats." + id + ".streak", 0);
    }

    private UUID kingId() {
        String raw = data.getString("king", "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isKing(UUID id) {
        UUID king = kingId();
        return king != null && king.equals(id);
    }

    private void setKing(Player player, boolean kingslayer) {
        data.set("king", player.getUniqueId());
        data.save();
        applyKingName(player);
        if (kingslayer) {
            player.getInventory().addItem(kingslayerBlade());
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + player.getName() + " is de Kingslayer en nieuwe King!"));
        } else {
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + player.getName() + " is nu de Season King!"));
        }
        player.sendTitle(Text.color("&6KING"), Text.color("&fJe draagt nu de gouden tag"), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
    }

    private ItemStack kingslayerBlade() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6Kingslayer Blade"));
            meta.setLore(List.of(Text.color("&7Verdiend door de Season King te slayen."), Text.color("&7Een trophy weapon met echte bite.")));
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.SHARPNESS, 7, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(Enchantment.LOOTING, 4, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void animateKing() {
        kingFrame++;
        UUID king = kingId();
        if (king == null) {
            return;
        }
        Player player = Bukkit.getPlayer(king);
        if (player != null) {
            applyKingName(player);
        }
    }

    private void applyKingName(Player player) {
        String prefix = MitchSMP.ranks() == null ? "" : Text.color(MitchSMP.ranks().getPrefix(player.getUniqueId()));
        player.setPlayerListName(Text.color(KING_FRAMES[kingFrame % KING_FRAMES.length] + " ") + prefix + player.getName());
    }

    private List<Map.Entry<UUID, Integer>> topStats(String stat, int limit) {
        java.util.Map<UUID, Integer> values = new java.util.HashMap<>();
        String suffix = "." + stat;
        for (String key : data.keys()) {
            if (!key.startsWith("stats.") || !key.endsWith(suffix)) {
                continue;
            }
            String rawId = key.substring("stats.".length(), key.length() - suffix.length());
            try {
                values.put(UUID.fromString(rawId), data.getInt(key, 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return values.entrySet().stream()
            .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
            .limit(limit)
            .toList();
    }

    private String line(int index, UUID id, String value) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        return "&7#" + index + " &f" + player.getName() + " &7- &f" + value;
    }

    private void appendEntries(StringBuilder builder, List<Map.Entry<UUID, Integer>> entries, String suffix) {
        int index = 1;
        for (Map.Entry<UUID, Integer> entry : entries) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            builder.append(index++).append(". ").append(player.getName()).append(" - ").append(entry.getValue()).append(suffix).append(System.lineSeparator());
        }
    }

    private void appendMoney(StringBuilder builder, List<Map.Entry<UUID, Double>> entries) {
        int index = 1;
        for (Map.Entry<UUID, Double> entry : entries) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            builder.append(index++).append(". ").append(player.getName()).append(" - $").append(format(entry.getValue())).append(System.lineSeparator());
        }
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private boolean isBedWarsWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        String world = player.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        return world.startsWith("bedwars_") || world.startsWith("bw_") || world.contains("bedwars") || world.startsWith("skirmish_") || world.startsWith("mitchtest_");
    }
}


