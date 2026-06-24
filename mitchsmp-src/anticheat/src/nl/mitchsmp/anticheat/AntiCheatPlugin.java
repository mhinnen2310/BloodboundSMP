package nl.mitchsmp.anticheat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public final class AntiCheatPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final int SUS_NOTIFY_SCORE = 8;
    private static final List<Material> TRACKED_DUPE_ITEMS = List.of(
        Material.DIAMOND,
        Material.DIAMOND_BLOCK,
        Material.EMERALD,
        Material.EMERALD_BLOCK,
        Material.GOLD_INGOT,
        Material.GOLD_BLOCK,
        Material.ENCHANTED_GOLDEN_APPLE,
        Material.TOTEM_OF_UNDYING,
        Material.NETHER_STAR,
        Material.DRAGON_EGG,
        Material.ELYTRA,
        Material.SPAWNER
    );
    private static final Set<String> SENSITIVE_COMMANDS = Set.of(
        "/plugins", "/pl", "/bukkit:plugins", "/bukkit:pl",
        "/version", "/ver", "/bukkit:version", "/bukkit:ver",
        "/op", "/deop", "/seed", "/give", "/minecraft:give",
        "/item", "/summon", "/data", "/execute", "/minecraft:execute"
    );

    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, PlaceWindow> places = new HashMap<>();
    private final Map<UUID, TargetWindow> targets = new HashMap<>();
    private final Map<UUID, DropWindow> drops = new HashMap<>();
    private final Map<UUID, CommandWindow> commands = new HashMap<>();
    private final Map<UUID, InventorySnapshot> inventorySnapshots = new HashMap<>();
    private final Map<String, Long> alertCooldowns = new HashMap<>();
    private PropertiesFile data;
    private long detectionSequence;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("anticheat.properties"));
        detectionSequence = data.getLong("detections.sequence", 0L);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("anticheat") != null) {
            getCommand("anticheat").setExecutor(this);
            getCommand("anticheat").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scanInventory(player, "periodic");
            }
        }, 200L, 200L);
    }

    @Override
    public void onDisable() {
        data.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> scanInventory(event.getPlayer(), "join"), 20L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || MitchSMP.permissions().isAdminRestricted(attacker)) {
            return;
        }
        if (event.isCancelled() || ignoreCombatClick(attacker)) {
            return;
        }
        double distance = attacker.getEyeLocation().distance(victim.getLocation().add(0, 1, 0));
        if (distance > 4.2D) {
            alert(attacker, "Reach", String.format(Locale.US, "%.2f blocks", distance), distance > 5.2D ? 4 : 2);
        }

        long now = System.currentTimeMillis();
        TargetWindow window = targets.computeIfAbsent(attacker.getUniqueId(), ignored -> new TargetWindow(now, new HashSet<>()));
        if (now - window.startedAt() > 1200L) {
            window.startedAt(now);
            window.targets().clear();
        }
        window.targets().add(victim.getUniqueId());
        if (window.targets().size() >= 3) {
            alert(attacker, "KillAura", "rapid target switches", 3);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (ignoreMovement(player) || event.getTo() == null || !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            airTicks.remove(player.getUniqueId());
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 1.15D) {
            alert(player, "Speed", String.format(Locale.US, "%.2f/tick", horizontal), horizontal > 1.8D ? 3 : 1);
        }

        if (player.isOnGround() || player.getFallDistance() > 0.0F) {
            airTicks.remove(player.getUniqueId());
            return;
        }
        if (to.getY() >= from.getY() - 0.01D) {
            int ticks = airTicks.merge(player.getUniqueId(), 1, Integer::sum);
            if (ticks > 80) {
                alert(player, "Fly", ticks + " air ticks", ticks > 140 ? 4 : 2);
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (MitchSMP.permissions().isAdminRestricted(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        PlaceWindow window = places.computeIfAbsent(player.getUniqueId(), ignored -> new PlaceWindow(now, 0));
        if (now - window.startedAt() > 3000L) {
            window.startedAt(now);
            window.count(0);
        }
        window.count(window.count() + 1);
        if (window.count() > 12 && player.getLocation().getPitch() < 45.0F) {
            alert(player, "Scaffold", window.count() + " placements/3s", 2);
        }
        if (window.count() > 24) {
            alert(player, "FastPlace", window.count() + " placements/3s", 2);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop() == null ? null : event.getItemDrop().getItemStack();
        if (dropped == null || dropped.getType() == Material.AIR || MitchSMP.permissions().isAdminRestricted(player)) {
            return;
        }
        checkStack(player, dropped, "drop");
        long now = System.currentTimeMillis();
        DropWindow window = drops.computeIfAbsent(player.getUniqueId(), ignored -> new DropWindow(now, 0, 0, 0));
        if (now - window.startedAt() > 5000L) {
            window.startedAt(now);
            window.count(0);
            window.amount(0);
            window.trackedAmount(0);
        }
        window.count(window.count() + 1);
        window.amount(window.amount() + Math.max(1, dropped.getAmount()));
        if (TRACKED_DUPE_ITEMS.contains(dropped.getType())) {
            window.trackedAmount(window.trackedAmount() + Math.max(1, dropped.getAmount()));
        }
        if (window.count() > 18) {
            alert(player, "DropBurst", window.count() + " item drops/5s", 1);
        }
        if (window.trackedAmount() >= 128) {
            alert(player, "DupeDrop", window.trackedAmount() + " tracked items dropped/5s", 4);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> scanInventory(player, "drop"), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || MitchSMP.permissions().isAdminRestricted(player)) {
            return;
        }
        checkStack(player, event.getCurrentItem(), "inventory click");
        Bukkit.getScheduler().runTaskLater(this, () -> scanInventory(player, "inventory click"), 1L);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (MitchSMP.permissions().isAdminRestricted(player)) {
            return;
        }
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        String command = message.split("\\s+")[0].toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CommandWindow window = commands.computeIfAbsent(player.getUniqueId(), ignored -> new CommandWindow(now, 0));
        if (now - window.startedAt() > 5000L) {
            window.startedAt(now);
            window.count(0);
        }
        window.count(window.count() + 1);
        if (window.count() > 12) {
            alert(player, "CommandSpam", window.count() + " commands/5s", 1);
        }
        if (SENSITIVE_COMMANDS.contains(command)) {
            alert(player, "SensitiveCommand", message, 1);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canSeeAlerts(sender)) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("alerts") || args[0].equalsIgnoreCase("interface")) {
            Text.msg(sender, "&aAnti-cheat is running in warnings-only mode.");
            Text.msg(sender, "&7Use &f/ac recent [count]&7, &f/ac review <id> [note]&7, &f/ac sus&7, or &f/ac check <player>&7.");
            return true;
        }
        if (args[0].equalsIgnoreCase("recent")) {
            int count = args.length >= 2 ? parseCount(args[1], 10) : 10;
            listRecent(sender, count);
            return true;
        }
        if (args[0].equalsIgnoreCase("review")) {
            if (args.length < 2) {
                Text.msg(sender, "&cUsage: /ac review <id> [staff note]");
                return true;
            }
            review(sender, args[1], args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "reviewed");
            return true;
        }
        if (args[0].equalsIgnoreCase("sus")) {
            listSus(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("check")) {
            if (args.length < 2) {
                Text.msg(sender, "&cUsage: /ac check <player>");
                return true;
            }
            showSus(sender, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            if (args.length < 2) {
                Text.msg(sender, "&cUsage: /ac clear <player>");
                return true;
            }
            clearSus(sender, args[1]);
            return true;
        }
        Text.msg(sender, "&cUsage: /ac <alerts|recent|review|sus|check|clear>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "alerts", "interface", "recent", "review", "sus", "check", "clear");
        }
        if (args.length == 2 && args[0].matches("(?i)check|clear")) {
            return Tab.onlinePlayers(args[1]);
        }
        return List.of();
    }

    private void scanInventory(Player player, String source) {
        if (player == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            checkStack(player, item, source);
        }
        Map<Material, Integer> totals = trackedTotals(contents);
        InventorySnapshot previous = inventorySnapshots.put(player.getUniqueId(), new InventorySnapshot(System.currentTimeMillis(), totals));
        if (previous == null || ignoreInventorySpike(player)) {
            return;
        }
        long age = System.currentTimeMillis() - previous.createdAt();
        if (age <= 0L || age > 30_000L) {
            return;
        }
        for (Map.Entry<Material, Integer> entry : totals.entrySet()) {
            int before = previous.totals().getOrDefault(entry.getKey(), 0);
            int increase = entry.getValue() - before;
            int threshold = spikeThreshold(entry.getKey());
            if (increase >= threshold) {
                alert(player, "DupeSpike", "+" + increase + " " + entry.getKey().name() + " in " + (age / 1000) + "s via " + source, 4);
            }
        }
    }

    private Map<Material, Integer> trackedTotals(ItemStack[] contents) {
        Map<Material, Integer> totals = new HashMap<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR || !TRACKED_DUPE_ITEMS.contains(item.getType())) {
                continue;
            }
            totals.merge(item.getType(), Math.max(1, item.getAmount()), Integer::sum);
        }
        return totals;
    }

    private void checkStack(Player player, ItemStack item, String source) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (item.getAmount() > 64) {
            alert(player, "IllegalStack", item.getAmount() + "x " + item.getType().name() + " via " + source, 5);
        }
        if (item.getAmount() > 1 && isNormallyUnstackable(item.getType())) {
            alert(player, "StackedUnstackable", item.getAmount() + "x " + item.getType().name() + " via " + source, 4);
        }
    }

    private boolean isNormallyUnstackable(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD")
            || name.endsWith("_PICKAXE")
            || name.endsWith("_AXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || material == Material.BOW
            || material == Material.ELYTRA
            || material == Material.SHIELD
            || material == Material.TOTEM_OF_UNDYING;
    }

    private int spikeThreshold(Material material) {
        return switch (material) {
            case DRAGON_EGG, NETHER_STAR, ELYTRA -> 2;
            case ENCHANTED_GOLDEN_APPLE, SPAWNER -> 4;
            case TOTEM_OF_UNDYING -> 8;
            case DIAMOND_BLOCK, EMERALD_BLOCK -> 16;
            case GOLD_BLOCK -> 24;
            default -> 192;
        };
    }

    private boolean ignoreInventorySpike(Player player) {
        return MitchSMP.permissions().isAdminRestricted(player)
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean ignoreCombatClick(Player player) {
        return player == null
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR
            || MitchSMP.permissions().isAdminRestricted(player);
    }

    private void alert(Player suspect, String check, String detail, int severity) {
        if (suspect == null) {
            return;
        }
        String cooldownKey = suspect.getUniqueId() + ":" + check;
        long now = System.currentTimeMillis();
        Long last = alertCooldowns.get(cooldownKey);
        if (last != null && now - last < 2500L) {
            return;
        }
        alertCooldowns.put(cooldownKey, now);
        int score = data.getInt("sus." + suspect.getUniqueId() + ".score", 0) + Math.max(1, severity);
        data.set("sus." + suspect.getUniqueId() + ".name", suspect.getName());
        data.set("sus." + suspect.getUniqueId() + ".score", score);
        data.set("sus." + suspect.getUniqueId() + ".last", System.currentTimeMillis());
        data.set("sus." + suspect.getUniqueId() + ".lastCheck", check);
        data.set("sus." + suspect.getUniqueId() + ".lastDetail", detail);
        data.set("sus." + suspect.getUniqueId() + ".checks." + key(check), data.getInt("sus." + suspect.getUniqueId() + ".checks." + key(check), 0) + 1);
        long detectionId = ++detectionSequence;
        String detection = "detections." + detectionId + ".";
        data.set("detections.sequence", detectionSequence);
        data.set(detection + "time", now);
        data.set(detection + "player", suspect.getUniqueId());
        data.set(detection + "name", suspect.getName());
        data.set(detection + "check", check);
        data.set(detection + "detail", detail);
        data.set(detection + "severity", severity);
        data.set(detection + "status", "OPEN");
        trimDetections(now);
        data.save();

        Text.msg(suspect, "&cAnti-cheat warning: &f" + check + " &7was flagged. No automatic punishment was applied.");

        String message = Text.color("&8[&cAC #" + detectionId + "&8] &f" + suspect.getName() + " &7failed &c" + check + " &8(&7" + detail + "&8) &7sus=&f" + score);
        boolean sent = false;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (canSeeAlerts(staff)) {
                staff.sendMessage(message);
                sent = true;
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
        if (!sent && score >= SUS_NOTIFY_SCORE) {
            getLogger().warning(Text.stripColorCodes(message));
        }
    }

    private void listRecent(CommandSender sender, int requestedCount) {
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        List<Long> ids = detectionIds().stream()
            .filter(id -> data.getLong("detections." + id + ".time", 0L) >= cutoff)
            .sorted(Comparator.reverseOrder())
            .limit(Math.max(1, Math.min(50, requestedCount)))
            .toList();
        if (ids.isEmpty()) {
            Text.msg(sender, "&aNo anti-cheat detections in the last 24 hours.");
            return;
        }
        Text.msg(sender, "&cRecent anti-cheat detections (24h):");
        for (long id : ids) {
            String base = "detections." + id + ".";
            Text.msg(sender, "&8#" + id + " &f" + data.getString(base + "name", "unknown") + " &c" + data.getString(base + "check", "?")
                + " &7sev=" + data.getInt(base + "severity", 0) + " status=" + data.getString(base + "status", "OPEN") + " &8" + data.getString(base + "detail", ""));
        }
    }

    private void review(CommandSender sender, String rawId, String note) {
        long id;
        try {
            id = Long.parseLong(rawId);
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cDetection ID must be a number.");
            return;
        }
        String base = "detections." + id + ".";
        if (!data.contains(base + "time")) {
            Text.msg(sender, "&cUnknown detection #" + id + ".");
            return;
        }
        data.set(base + "status", "REVIEWED");
        data.set(base + "reviewer", sender.getName());
        data.set(base + "reviewedAt", System.currentTimeMillis());
        data.set(base + "note", note.replaceAll("[\\r\\n]", " "));
        data.save();
        getLogger().info("AC detection #" + id + " reviewed by " + sender.getName() + ": " + note);
        Text.msg(sender, "&aDetection #" + id + " marked as reviewed.");
    }

    private List<Long> detectionIds() {
        Set<Long> ids = new HashSet<>();
        for (String dataKey : data.keys()) {
            if (!dataKey.startsWith("detections.") || !dataKey.endsWith(".time")) {
                continue;
            }
            String raw = dataKey.substring("detections.".length(), dataKey.length() - ".time".length());
            try {
                ids.add(Long.parseLong(raw));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids.stream().sorted().toList();
    }

    private void trimDetections(long now) {
        long cutoff = now - 7L * 24L * 60L * 60L * 1000L;
        List<Long> ids = detectionIds();
        int excess = Math.max(0, ids.size() - 2000);
        for (int index = 0; index < ids.size(); index++) {
            long id = ids.get(index);
            if (index < excess || data.getLong("detections." + id + ".time", 0L) < cutoff) {
                String prefix = "detections." + id + ".";
                for (String dataKey : new ArrayList<>(data.keys())) {
                    if (dataKey.startsWith(prefix)) {
                        data.set(dataKey, null);
                    }
                }
            }
        }
    }

    private int parseCount(String raw, int fallback) {
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void listSus(CommandSender sender) {
        List<SusEntry> entries = susEntries();
        if (entries.isEmpty()) {
            Text.msg(sender, "&aNo suspicious players logged.");
            return;
        }
        Text.msg(sender, "&cSuspicious players:");
        int index = 1;
        for (SusEntry entry : entries.stream().limit(10).toList()) {
            Text.msg(sender, "&7#" + index++ + " &f" + entry.name() + " &7score=&c" + entry.score() + " &8last=" + data.getString("sus." + entry.id() + ".lastCheck", "?"));
        }
    }

    private void showSus(CommandSender sender, String name) {
        UUID id = findSusId(name);
        if (id == null) {
            Text.msg(sender, "&cGeen sus-data for deze speler.");
            return;
        }
        Text.msg(sender, "&cSus-data for &f" + data.getString("sus." + id + ".name", id.toString()) + "&c:");
        Text.msg(sender, "&7Score: &f" + data.getInt("sus." + id + ".score", 0));
        Text.msg(sender, "&7Laatste check: &f" + data.getString("sus." + id + ".lastCheck", "?"));
        Text.msg(sender, "&7Detail: &f" + data.getString("sus." + id + ".lastDetail", "?"));
    }

    private void clearSus(CommandSender sender, String name) {
        UUID id = findSusId(name);
        if (id == null) {
            Text.msg(sender, "&cGeen sus-data for deze speler.");
            return;
        }
        String prefix = "sus." + id + ".";
        for (String key : new ArrayList<>(data.keys())) {
            if (key.startsWith(prefix)) {
                data.set(key, null);
            }
        }
        data.save();
        Text.msg(sender, "&aSus-data gewist for &f" + name + "&a.");
    }

    private UUID findSusId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (SusEntry entry : susEntries()) {
            if (entry.name().equalsIgnoreCase(name) || entry.id().toString().equalsIgnoreCase(name)) {
                return entry.id();
            }
        }
        return null;
    }

    private List<SusEntry> susEntries() {
        List<SusEntry> entries = new ArrayList<>();
        for (String dataKey : data.keys()) {
            if (!dataKey.startsWith("sus.") || !dataKey.endsWith(".score")) {
                continue;
            }
            String rawId = dataKey.substring("sus.".length(), dataKey.length() - ".score".length());
            try {
                UUID id = UUID.fromString(rawId);
                entries.add(new SusEntry(id, data.getString("sus." + id + ".name", id.toString()), data.getInt(dataKey, 0)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        entries.sort(Comparator.comparingInt(SusEntry::score).reversed());
        return entries;
    }

    private boolean canSeeAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return MitchSMP.ranks().getRank(player.getUniqueId()).weight() >= MitchRank.MODERATOR.weight();
    }

    private boolean ignoreMovement(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR
            || player.getAllowFlight()
            || player.isFlying()
            || player.isGliding()
            || player.isInsideVehicle()
            || player.isSwimming()
            || MitchSMP.permissions().isAdminRestricted(player);
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private String key(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static class ClickWindow {
        private long startedAt;
        private int count;

        ClickWindow(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }

        long startedAt() {
            return startedAt;
        }

        void startedAt(long value) {
            startedAt = value;
        }

        int count() {
            return count;
        }

        void count(int value) {
            count = value;
        }
    }

    private static final class PlaceWindow extends ClickWindow {
        PlaceWindow(long startedAt, int count) {
            super(startedAt, count);
        }
    }

    private static final class CommandWindow extends ClickWindow {
        CommandWindow(long startedAt, int count) {
            super(startedAt, count);
        }
    }

    private static final class DropWindow extends ClickWindow {
        private int amount;
        private int trackedAmount;

        DropWindow(long startedAt, int count, int amount, int trackedAmount) {
            super(startedAt, count);
            this.amount = amount;
            this.trackedAmount = trackedAmount;
        }

        int amount() {
            return amount;
        }

        void amount(int value) {
            amount = value;
        }

        int trackedAmount() {
            return trackedAmount;
        }

        void trackedAmount(int value) {
            trackedAmount = value;
        }
    }

    private static final class TargetWindow {
        private long startedAt;
        private final Set<UUID> targets;

        TargetWindow(long startedAt, Set<UUID> targets) {
            this.startedAt = startedAt;
            this.targets = targets;
        }

        long startedAt() {
            return startedAt;
        }

        void startedAt(long value) {
            startedAt = value;
        }

        Set<UUID> targets() {
            return targets;
        }
    }

    private record InventorySnapshot(long createdAt, Map<Material, Integer> totals) {
    }

    private record SusEntry(UUID id, String name, int score) {
    }
}


