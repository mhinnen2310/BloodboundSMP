package nl.mitchsmp.gameplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.BountyService;
import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.GameplayService;
import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.InventorySnapshotService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.SkillService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public final class GameplayPlugin extends JavaPlugin implements Listener, TabCompleter, GameplayService {
    private static final long THREE_HOURS = 3L * 60L * 60L * 1000L;
    private static final long ASSIST_WINDOW = 15_000L;
    private static final long ASSIST_PAIR_COOLDOWN = 30L * 60L * 1000L;
    private static final long RECOVERY_COOLDOWN = 24L * 60L * 60L * 1000L;
    private static final Set<String> HOSTILE_HINTS = Set.of("ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "WITCH", "PILLAGER", "VINDICATOR", "EVOKER", "RAVAGER", "ENDERMAN", "BLAZE", "GHAST", "SLIME", "MAGMA_CUBE", "DROWNED", "HUSK", "STRAY", "PHANTOM", "WARDEN", "WITHER");

    private final Map<UUID, Long> joinedAt = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Map<UUID, DamageContribution>> damage = new HashMap<>();
    private PropertiesFile data;
    private PropertiesFile progression;
    private PropertiesFile homes;
    private PropertiesFile essentials;
    private NamespacedKey restrictedKey;
    private NamespacedKey restrictionReasonKey;
    private boolean dirty;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("gameplay.properties"));
        progression = new PropertiesFile(getDataFolder().toPath().getParent().resolve("MitchSMP-Progression").resolve("progression.properties"));
        homes = new PropertiesFile(getDataFolder().toPath().getParent().resolve("MitchSMP-Homes").resolve("homes.properties"));
        essentials = new PropertiesFile(getDataFolder().toPath().getParent().resolve("MitchSMP-Essentials").resolve("essentials.properties"));
        restrictedKey = new NamespacedKey("bloodbound", "trade_restricted");
        restrictionReasonKey = new NamespacedKey("bloodbound", "restriction_reason");
        MitchSMP.registerService(GameplayService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String name : List.of("goals", "rookie", "recoverykit", "report", "reports", "staffprofile", "staffnote")) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            beginSession(player);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::survivalMinute, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::flushProgress, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, progression::load, 100L, 100L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            endSession(player);
        }
        data.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "goals" -> goals(sender);
            case "rookie" -> rookie(sender);
            case "recoverykit" -> recoveryKit(sender);
            case "report" -> report(sender, args);
            case "reports" -> reports(sender, args);
            case "staffprofile" -> staffProfile(sender, args);
            case "staffnote" -> staffNote(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("report")) {
            if (isReportStaff(sender)) {
                if (args.length == 1) {
                    List<String> values = new ArrayList<>(Tab.onlinePlayers(args[0]));
                    values.addAll(Tab.complete(args[0], "view", "close"));
                    return values;
                }
                if (args.length == 2 && args[0].matches("(?i)view|close")) {
                    return Tab.complete(args[1], reportIds("OPEN"));
                }
            }
            return args.length == 1 ? Tab.onlinePlayers(args[0]) : List.of();
        }
        if (command.getName().equalsIgnoreCase("reports") && args.length == 1 && isReportStaff(sender)) {
            return Tab.complete(args[0], "open", "closed", "all");
        }
        if (command.getName().equalsIgnoreCase("staffprofile") && args.length == 1 && isReportStaff(sender)) {
            return Tab.onlinePlayers(args[0]);
        }
        if (command.getName().equalsIgnoreCase("staffnote") && isReportStaff(sender)) {
            if (args.length == 1) {
                return Tab.complete(args[0], "add", "view");
            }
            if (args.length == 2) {
                return Tab.onlinePlayers(args[1]);
            }
        }
        return List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        beginSession(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer());
        damage.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage() == null ? "" : event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (command.equals("/rtp") || command.startsWith("/rtp ")) {
            data.set("rookie." + event.getPlayer().getUniqueId() + ".rtpActive", true);
            data.set("rookie." + event.getPlayer().getUniqueId() + ".rtpTravel", 0.0D);
            data.save();
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null || !sameWorld(event.getFrom(), event.getTo()) || restrictedWorld(player)) {
            lastLocations.put(player.getUniqueId(), event.getTo());
            return;
        }
        double distance = horizontalDistance(event.getFrom(), event.getTo());
        if (distance <= 0.0D || distance > 20.0D) {
            return;
        }
        if (Boolean.parseBoolean(data.getString("rookie." + player.getUniqueId() + ".rtpActive", "false"))) {
            addProgress(player, "travel", distance, 1500.0D);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        String type = event.getBlock().getType().name();
        if (type.equals("IRON_ORE") || type.equals("DEEPSLATE_IRON_ORE")) {
            addProgress(event.getPlayer(), "iron", 1.0D, 24.0D);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity() == null ? null : event.getEntity().getKiller();
        if (killer != null && !(event.getEntity() instanceof Player) && isHostile(event.getEntity())) {
            addProgress(killer, "mobs", 1.0D, 20.0D);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim) || restrictedWorld(attacker) || MitchSMP.permissions().isAdminRestricted(attacker)) {
            return;
        }
        double dealt = damageAmount(event);
        damage.computeIfAbsent(victim.getUniqueId(), ignored -> new HashMap<>())
            .put(attacker.getUniqueId(), new DamageContribution(System.currentTimeMillis(), dealt));
        BountyService bounties = MitchSMP.bounties();
        if (bounties != null && bounties.getBounty(victim.getUniqueId()) > 0.0D) {
            addProgress(attacker, "bounty_damage", 1.0D, 1.0D);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        data.set("lastDeath." + victim.getUniqueId(), System.currentTimeMillis() + "|" + (killer == null ? "environment" : "killed by " + killer.getName()));
        if (killer != null && !killer.equals(victim) && !restrictedWorld(victim) && !MitchSMP.permissions().isAdminRestricted(killer)) {
            rewardFirstBlood(killer, victim);
            rewardAssists(victim, killer);
        }
        damage.remove(victim.getUniqueId());
        data.save();
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ReportsMenu reportsMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && isReportStaff(player)) {
                handleReportsClick(player, reportsMenu, event.getRawSlot(), event.isShiftClick());
            }
            return;
        }
        if (!(holder instanceof RookieMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String contract = switch (event.getRawSlot()) {
            case 10 -> "iron";
            case 12 -> "mobs";
            case 14 -> "travel";
            case 16 -> "bounty_damage";
            case 22 -> "survive";
            case 24 -> "skirmish";
            default -> null;
        };
        if (contract != null) {
            claimRookieContract(player, contract);
            openRookieMenu(player);
        }
    }

    private boolean goals(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        progression.load();
        int hearts = hearts(player.getUniqueId());
        int kills = kills(player.getUniqueId());
        double bounty = MitchSMP.bounties() == null ? 0.0D : MitchSMP.bounties().getBounty(player.getUniqueId());
        Text.msg(player, "&4Bloodbound Goals &8- &7your next steps");
        int index = 1;
        if (isRookie(player.getUniqueId())) {
            Text.msg(player, "&6" + index++ + ". &fUse &c/rtp &fto enter the wilderness.");
            Text.msg(player, "&6" + index++ + ". &fOpen &c/rookie &fand complete a combat-ready contract.");
            Text.msg(player, "&6" + index++ + ". &fEarn your first real PvP kill for First Blood.");
        }
        if (hearts <= 10) {
            Text.msg(player, "&6" + index++ + ". &fFind or craft a Corrupted Heart.");
            Text.msg(player, "&6" + index++ + ". &fDamage an active bounty target for a rookie reward.");
        }
        if (bounty > 0.0D) {
            Text.msg(player, "&6" + index++ + ". &fDefend your &c$" + money(bounty) + " &fbounty or prepare an ambush.");
        }
        if (kills >= 3 && hearts > 10) {
            Text.msg(player, "&6" + index++ + ". &fHunt a high bounty through &c/bounties&f.");
            Text.msg(player, "&6" + index++ + ". &fAdvance your collection and prepare the Endboss ritual.");
        }
        return true;
    }

    private boolean rookie(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!isRookie(player.getUniqueId())) {
            Text.msg(player, "&7You have graduated from rookie contracts. Use &f/contracts &7for high-risk objectives.");
            return true;
        }
        openRookieMenu(player);
        return true;
    }

    private boolean recoveryKit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!isRookie(player.getUniqueId()) && hearts(player.getUniqueId()) > 10) {
            Text.msg(player, "&cRecovery kits are only available to rookies or players at 10 hearts or fewer.");
            return true;
        }
        long last = data.getLong("recovery." + player.getUniqueId(), 0L);
        long remaining = RECOVERY_COOLDOWN - (System.currentTimeMillis() - last);
        if (remaining > 0L) {
            Text.msg(player, "&cRecovery kit cooldown: &f" + formatDuration(remaining) + "&c.");
            return true;
        }
        List<ItemStack> kit = new ArrayList<>(List.of(
            restricted(new ItemStack(Material.STONE_PICKAXE), "Recovery Kit"),
            restricted(new ItemStack(Material.STONE_AXE), "Recovery Kit"),
            restricted(new ItemStack(Material.IRON_SWORD), "Recovery Kit"),
            restricted(new ItemStack(Material.SHIELD), "Recovery Kit"),
            restricted(new ItemStack(Material.COOKED_BEEF, 16), "Recovery Kit"),
            restricted(new ItemStack(Material.ARROW, 8), "Recovery Kit")
        ));
        SkillService skills = MitchSMP.skills();
        if (skills != null) {
            for (ItemStack sample : skills.createRecoveryAbilitySamples(player.getUniqueId())) {
                kit.add(restricted(sample, "30-minute Recovery Ability Trial"));
            }
        }
        give(player, kit);
        data.set("recovery." + player.getUniqueId(), System.currentTimeMillis());
        data.save();
        Text.msg(player, "&aRecovery kit received. &7These items cannot be sold or listed.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3F, 1.4F);
        return true;
    }

    private boolean report(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].matches("(?i)view|close")) {
            if (!isReportStaff(sender)) {
                Text.msg(sender, "&cYou do not have permission.");
                return true;
            }
            if (args.length < 2) {
                Text.msg(sender, "&cUsage: /report " + args[0].toLowerCase(Locale.ROOT) + " <id>");
                return true;
            }
            return args[0].equalsIgnoreCase("view") ? viewReport(sender, args[1]) : closeReport(sender, args[1]);
        }
        if (!(sender instanceof Player reporter)) {
            Text.msg(sender, "&cUsage: /report <player> <reason>");
            return true;
        }
        if (args.length < 2) {
            Text.msg(reporter, "&cUsage: /report <player> <reason>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(reporter)) {
            Text.msg(reporter, "&cChoose another online player.");
            return true;
        }
        String cooldownKey = "reportCooldown." + reporter.getUniqueId() + "." + target.getUniqueId();
        if (System.currentTimeMillis() - data.getLong(cooldownKey, 0L) < 5L * 60L * 1000L) {
            Text.msg(reporter, "&cYou recently reported this player. Add urgent evidence through staff if needed.");
            return true;
        }
        long id = data.getLong("reports.sequence", 0L) + 1L;
        String base = "reports." + id + ".";
        data.set("reports.sequence", id);
        data.set(base + "status", "OPEN");
        data.set(base + "time", System.currentTimeMillis());
        data.set(base + "reporter", reporter.getUniqueId());
        data.set(base + "reporterName", reporter.getName());
        data.set(base + "target", target.getUniqueId());
        data.set(base + "targetName", target.getName());
        data.set(base + "reason", String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).replaceAll("[\\r\\n]", " "));
        data.set(base + "location", shortLocation(target.getLocation()));
        data.set(base + "nearby", nearby(target));
        data.set(base + "lastDeath", data.getString("lastDeath." + target.getUniqueId(), "none recorded"));
        InventorySnapshotService snapshots = MitchSMP.snapshots();
        if (snapshots != null) {
            data.set(base + "snapshot", snapshots.capture(target, "player-report-" + id));
        }
        data.set(cooldownKey, System.currentTimeMillis());
        data.save();
        Text.msg(reporter, "&aReport #" + id + " submitted. Staff received location, nearby-player and inventory-snapshot context.");
        alertReportStaff("&c[Report #" + id + "] &f" + reporter.getName() + " &7reported &f" + target.getName() + "&7: &f" + data.getString(base + "reason", ""));
        return true;
    }

    private boolean reports(CommandSender sender, String[] args) {
        if (!isReportStaff(sender)) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        String filter = args.length == 0 ? "OPEN" : args[0].toUpperCase(Locale.ROOT);
        if (filter.equals("CLOSED")) {
            filter = "CLOSED";
        } else if (!filter.equals("ALL")) {
            filter = "OPEN";
        }
        if (sender instanceof Player player) {
            openReportsMenu(player, filter, 0);
            return true;
        }
        List<String> ids = reportIds(filter);
        Text.msg(sender, "&6Reports: &f" + ids.size() + " &7(" + filter.toLowerCase(Locale.ROOT) + ")");
        for (String id : ids.stream().limit(15).toList()) {
            String base = "reports." + id + ".";
            Text.msg(sender, "&8#" + id + " &f" + data.getString(base + "targetName", "?") + " &7by &f" + data.getString(base + "reporterName", "?") + " &8- &7" + data.getString(base + "reason", ""));
        }
        return true;
    }

    private void openReportsMenu(Player player, String filter, int page) {
        List<String> ids = reportIds(filter);
        int maxPage = Math.max(0, (ids.size() - 1) / 45);
        int current = Math.max(0, Math.min(page, maxPage));
        ReportsMenu holder = new ReportsMenu(filter, current);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8Reports: " + filter.toLowerCase(Locale.ROOT)));
        holder.inventory = inventory;
        int offset = current * 45;
        for (int slot = 0; slot < 45 && offset + slot < ids.size(); slot++) {
            String id = ids.get(offset + slot);
            holder.ids.put(slot, id);
            String base = "reports." + id + ".";
            inventory.setItem(slot, named(new ItemStack(Material.PAPER), "&6Report #" + id + " &8- &f" + data.getString(base + "targetName", "?"), List.of(
                "&7Status: &f" + data.getString(base + "status", "OPEN"),
                "&7Reporter: &f" + data.getString(base + "reporterName", "?"),
                "&7Reason: &f" + clip(data.getString(base + "reason", ""), 48),
                "&7Location: &f" + data.getString(base + "location", "?"),
                "&7Nearby: &f" + clip(data.getString(base + "nearby", "none"), 48),
                "&7Last death: &f" + clip(data.getString(base + "lastDeath", "none"), 48),
                "&7Snapshot: &f" + data.getString(base + "snapshot", "none"),
                "&eClick for full details.",
                "&cShift-click to close an open report."
            )));
        }
        inventory.setItem(45, named(new ItemStack(Material.ARROW), "&aPrevious", List.of("&7Previous page.")));
        inventory.setItem(46, named(new ItemStack(Material.PAPER), "&cOpen", List.of("&7Show unresolved reports.")));
        inventory.setItem(47, named(new ItemStack(Material.CHEST), "&eAll", List.of("&7Show the full report archive.")));
        inventory.setItem(48, named(new ItemStack(Material.BARRIER), "&7Closed", List.of("&7Show resolved reports.")));
        inventory.setItem(53, named(new ItemStack(Material.ARROW), "&aNext", List.of("&7Next page.")));
        player.openInventory(inventory);
    }

    private void handleReportsClick(Player player, ReportsMenu menu, int slot, boolean shiftClick) {
        if (slot == 45) {
            openReportsMenu(player, menu.filter, menu.page - 1);
        } else if (slot == 46) {
            openReportsMenu(player, "OPEN", 0);
        } else if (slot == 47) {
            openReportsMenu(player, "ALL", 0);
        } else if (slot == 48) {
            openReportsMenu(player, "CLOSED", 0);
        } else if (slot == 53) {
            openReportsMenu(player, menu.filter, menu.page + 1);
        } else {
            String id = menu.ids.get(slot);
            if (id == null) {
                return;
            }
            if (shiftClick && data.getString("reports." + id + ".status", "OPEN").equalsIgnoreCase("OPEN")) {
                closeReport(player, id);
                openReportsMenu(player, menu.filter, menu.page);
            } else {
                player.closeInventory();
                viewReport(player, id);
            }
        }
    }

    private boolean staffProfile(CommandSender sender, String[] args) {
        if (!isReportStaff(sender)) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cUsage: /staffprofile <player>");
            return true;
        }
        OfflinePlayer target = knownPlayer(args[0]);
        if (target == null) {
            Text.msg(sender, "&cUnknown player. They must have joined before.");
            return true;
        }
        UUID id = target.getUniqueId();
        progression.load();
        homes.load();
        essentials.load();
        EconomyService economy = MitchSMP.economy();
        BountyService bounties = MitchSMP.bounties();
        List<String> homeNames = homes.keys().stream().filter(key -> key.startsWith(id + ".")).map(key -> key.substring((id + ".").length())).sorted().toList();
        Text.msg(sender, "&6Player Profile: &f" + safeName(target) + " &8(&7" + id + "&8)");
        Text.msg(sender, "&7Rank: &f" + MitchSMP.ranks().getRank(id).displayName() + " &8| &7Hearts: &c" + hearts(id) + " &8| &7Rookie: &f" + isRookie(id));
        Text.msg(sender, "&7Balance: &a$" + money(economy == null ? 0.0D : economy.getBalance(id)) + " &8| &7Bounty: &6$" + money(bounties == null ? 0.0D : bounties.getBounty(id)));
        Text.msg(sender, "&7Overall K/D: &f" + progression.getInt("legacy.kills." + id, 0) + "&7/&f" + progression.getInt("legacy.deaths." + id, 0) + " &8| &7Assists: &f" + data.getInt("stats." + id + ".assists", 0));
        Text.msg(sender, "&7Homes: &f" + (homeNames.isEmpty() ? "none" : String.join(", ", homeNames)));
        InventorySnapshotService snapshots = MitchSMP.snapshots();
        if (snapshots != null) {
            List<InventorySnapshotService.SnapshotInfo> recent = snapshots.list(id, 3);
            Text.msg(sender, "&7Recent snapshots: &f" + (recent.isEmpty() ? "none" : recent.stream().map(info -> info.id() + "(" + info.trigger() + ")").collect(java.util.stream.Collectors.joining(", "))));
        }
        Text.msg(sender, "&7Staff notes: &f" + data.getInt("notes." + id + ".count", 0) + " &8| &7Open reports: &f" + openReportCount(id));
        Text.msg(sender, "&7Recent punishments: &f" + recentPunishments(safeName(target)));
        return true;
    }

    private String recentPunishments(String playerName) {
        int next = essentials.getInt("audit.next", 0);
        List<String> matches = new ArrayList<>();
        for (int sequence = next - 1; sequence >= Math.max(0, next - 500) && matches.size() < 3; sequence--) {
            int index = Math.floorMod(sequence, 5000);
            String action = essentials.getString("audit." + index + ".action", "");
            String detail = essentials.getString("audit." + index + ".detail", "");
            String haystack = (action + " " + detail).toLowerCase(Locale.ROOT);
            if (haystack.contains(playerName.toLowerCase(Locale.ROOT))
                && (haystack.contains("jail") || haystack.contains("freeze") || haystack.contains("lockdown") || haystack.contains("release") || haystack.contains("punish"))) {
                matches.add(action + " (" + clip(detail, 28) + ")");
            }
        }
        return matches.isEmpty() ? "none" : String.join(", ", matches);
    }

    private boolean staffNote(CommandSender sender, String[] args) {
        if (!isReportStaff(sender)) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length < 2 || !args[0].matches("(?i)add|view")) {
            Text.msg(sender, "&cUsage: /staffnote <add|view> <player> [note]");
            return true;
        }
        OfflinePlayer target = knownPlayer(args[1]);
        if (target == null) {
            Text.msg(sender, "&cUnknown player.");
            return true;
        }
        UUID id = target.getUniqueId();
        if (args[0].equalsIgnoreCase("view")) {
            int count = data.getInt("notes." + id + ".count", 0);
            Text.msg(sender, "&6Staff notes for &f" + safeName(target) + "&6: &f" + count);
            for (int index = Math.max(0, count - 10); index < count; index++) {
                String base = "notes." + id + "." + index + ".";
                Text.msg(sender, "&8#" + (index + 1) + " &7" + data.getString(base + "author", "?") + ": &f" + data.getString(base + "text", ""));
            }
            return true;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cUsage: /staffnote add <player> <note>");
            return true;
        }
        int index = data.getInt("notes." + id + ".count", 0);
        String base = "notes." + id + "." + index + ".";
        data.set(base + "author", sender.getName());
        data.set(base + "authorId", sender instanceof Player player ? player.getUniqueId() : "console");
        data.set(base + "time", System.currentTimeMillis());
        data.set(base + "text", String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).replaceAll("[\\r\\n]", " "));
        data.set("notes." + id + ".count", index + 1);
        data.save();
        Text.msg(sender, "&aStaff note added to &f" + safeName(target) + "&a.");
        return true;
    }

    private int openReportCount(UUID target) {
        int count = 0;
        for (String reportId : reportIds("OPEN")) {
            if (data.getString("reports." + reportId + ".target", "").equals(target.toString())) {
                count++;
            }
        }
        return count;
    }

    private OfflinePlayer knownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    private String safeName(OfflinePlayer player) {
        return player == null || player.getName() == null ? "Unknown" : player.getName();
    }

    private boolean viewReport(CommandSender sender, String id) {
        String base = "reports." + id + ".";
        if (!data.contains(base + "status")) {
            Text.msg(sender, "&cUnknown report #" + id + ".");
            return true;
        }
        Text.msg(sender, "&6Report #" + id + " &8- &f" + data.getString(base + "status", "OPEN"));
        Text.msg(sender, "&7Target: &f" + data.getString(base + "targetName", "?") + " &7Reporter: &f" + data.getString(base + "reporterName", "?"));
        Text.msg(sender, "&7Reason: &f" + data.getString(base + "reason", ""));
        Text.msg(sender, "&7Location: &f" + data.getString(base + "location", "?") + " &7Nearby: &f" + data.getString(base + "nearby", "none"));
        Text.msg(sender, "&7Last death: &f" + data.getString(base + "lastDeath", "none") + " &7Snapshot: &f" + data.getString(base + "snapshot", "none"));
        return true;
    }

    private boolean closeReport(CommandSender sender, String id) {
        String base = "reports." + id + ".";
        if (!data.contains(base + "status")) {
            Text.msg(sender, "&cUnknown report #" + id + ".");
            return true;
        }
        data.set(base + "status", "CLOSED");
        data.set(base + "closedBy", sender.getName());
        data.set(base + "closedAt", System.currentTimeMillis());
        data.save();
        Text.msg(sender, "&aReport #" + id + " closed.");
        return true;
    }

    private void openRookieMenu(Player player) {
        RookieMenu holder = new RookieMenu();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Rookie Contracts"));
        holder.inventory = inventory;
        inventory.setItem(10, contractIcon(player, "iron", Material.IRON_ORE, "Mine 24 iron ore", 24));
        inventory.setItem(12, contractIcon(player, "mobs", Material.IRON_SWORD, "Kill 20 hostile mobs", 20));
        inventory.setItem(14, contractIcon(player, "travel", Material.BOW, "Travel 1,500 blocks after RTP", 1500));
        inventory.setItem(16, contractIcon(player, "bounty_damage", Material.TARGET, "Damage a bounty target", 1));
        inventory.setItem(22, contractIcon(player, "survive", Material.COOKED_BEEF, "Survive 30 minutes outside spawn", 30));
        inventory.setItem(24, contractIcon(player, "skirmish", Material.IRON_SWORD, "Participate in Skirmish", 1));
        player.openInventory(inventory);
    }

    private ItemStack contractIcon(Player player, String id, Material material, String name, int required) {
        double progress = progress(player, id);
        boolean complete = progress >= required;
        boolean claimed = data.contains("rookie." + player.getUniqueId() + ".claimed." + id);
        return named(new ItemStack(material), "&6" + name, List.of(
            "&7Progress: &f" + Math.min(required, (int) progress) + "&7/&f" + required,
            claimed ? "&aClaimed" : complete ? "&eClick to claim" : "&cIn progress",
            "&8One-time, non-tradeable rookie reward"
        ));
    }

    private void claimRookieContract(Player player, String id) {
        int required = switch (id) { case "iron" -> 24; case "mobs" -> 20; case "travel" -> 1500; case "survive" -> 30; default -> 1; };
        String claimedKey = "rookie." + player.getUniqueId() + ".claimed." + id;
        if (data.contains(claimedKey) || progress(player, id) < required) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.25F, 0.7F);
            return;
        }
        List<ItemStack> rewards = switch (id) {
            case "iron" -> List.of(new ItemStack(Material.IRON_HELMET), new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_BOOTS));
            case "mobs" -> List.of(enchanted(Material.IRON_SWORD, Enchantment.SHARPNESS, 2));
            case "travel" -> List.of(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 64));
            case "survive" -> List.of(new ItemStack(Material.COOKED_BEEF, 24));
            default -> List.of();
        };
        List<ItemStack> restricted = rewards.stream().map(item -> restricted(item, "Rookie Contract")).toList();
        give(player, restricted);
        EconomyService economy = MitchSMP.economy();
        double money = id.equals("travel") || id.equals("skirmish") ? 500.0D : id.equals("bounty_damage") ? 1000.0D : id.equals("survive") ? 250.0D : 0.0D;
        if (economy != null && money > 0.0D) {
            economy.deposit(player.getUniqueId(), money, "rookie contract " + id);
        }
        if (id.equals("bounty_damage")) {
            addCombatXp(player.getUniqueId(), 150, "rookie bounty damage contract");
        } else if (id.equals("skirmish")) {
            addCombatXp(player.getUniqueId(), 75, "rookie Skirmish participation");
        }
        data.set(claimedKey, System.currentTimeMillis());
        data.save();
        Text.msg(player, "&aRookie contract claimed. &7Reward items cannot be sold or listed.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55F, 1.15F);
    }

    private void rewardAssists(Player victim, Player killer) {
        Map<UUID, DamageContribution> contributions = damage.getOrDefault(victim.getUniqueId(), Map.of());
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, DamageContribution> entry : contributions.entrySet()) {
            if (entry.getKey().equals(killer.getUniqueId()) || now - entry.getValue().at() > ASSIST_WINDOW) {
                continue;
            }
            Player assistant = Bukkit.getPlayer(entry.getKey());
            if (assistant == null) {
                continue;
            }
            String pair = pairKey(assistant.getUniqueId(), victim.getUniqueId());
            if (now - data.getLong("assistPair." + pair, 0L) < ASSIST_PAIR_COOLDOWN) {
                continue;
            }
            data.set("assistPair." + pair, now);
            data.set("stats." + assistant.getUniqueId() + ".assists", data.getInt("stats." + assistant.getUniqueId() + ".assists", 0) + 1);
            EconomyService economy = MitchSMP.economy();
            if (economy != null) {
                economy.deposit(assistant.getUniqueId(), 125.0D, "PvP assist on " + victim.getName());
            }
            addCombatXp(assistant.getUniqueId(), 40, "PvP assist");
            Text.msg(assistant, "&aAssist on &f" + victim.getName() + "&a: &f$125 &7+ combat XP. No heart was awarded.");
        }
    }

    private void rewardFirstBlood(Player killer, Player victim) {
        String key = "firstBlood." + killer.getUniqueId();
        String victimKey = "firstBloodVictim." + victim.getUniqueId();
        if (data.contains(key) || data.contains(victimKey) || !eligibleFirstBloodVictim(victim)) {
            return;
        }
        data.set(key, System.currentTimeMillis());
        data.set(victimKey, killer.getUniqueId());
        EconomyService economy = MitchSMP.economy();
        if (economy != null) {
            economy.deposit(killer.getUniqueId(), 750.0D, "First Blood milestone");
        }
        addCombatXp(killer.getUniqueId(), 200, "First Blood");
        give(killer, List.of(restricted(new ItemStack(Material.COOKED_BEEF, 16), "First Blood Supply"), restricted(new ItemStack(Material.ARROW, 16), "First Blood Supply")));
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&4FIRST BLOOD &8- &f" + killer.getName() + " &7claimed their first true Lifesteal kill."));
        killer.sendTitle(Text.color("&4FIRST BLOOD"), Text.color("&6$750 &8+ &fCombat XP"), 10, 50, 15);
    }

    private void survivalMinute() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isRookie(player.getUniqueId()) || restrictedWorld(player) || player.getWorld() == null) {
                continue;
            }
            Location spawn = player.getWorld().getSpawnLocation();
            if (sameWorld(spawn, player.getLocation()) && spawn.distanceSquared(player.getLocation()) >= 128.0D * 128.0D) {
                addProgress(player, "survive", 1.0D, 30.0D);
            }
        }
    }

    private void beginSession(Player player) {
        joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        lastLocations.put(player.getUniqueId(), player.getLocation());
        if (!data.contains("firstSeen." + player.getUniqueId())) {
            data.set("firstSeen." + player.getUniqueId(), System.currentTimeMillis());
            data.save();
        }
    }

    private void endSession(Player player) {
        Long joined = joinedAt.remove(player.getUniqueId());
        if (joined != null) {
            String key = "playedMillis." + player.getUniqueId();
            data.set(key, data.getLong(key, 0L) + Math.max(0L, System.currentTimeMillis() - joined));
            data.save();
        }
        lastLocations.remove(player.getUniqueId());
    }

    @Override
    public boolean isRookie(UUID playerId) {
        long played = data.getLong("playedMillis." + playerId, 0L);
        Long joined = joinedAt.get(playerId);
        if (joined != null) {
            played += Math.max(0L, System.currentTimeMillis() - joined);
        }
        return played < THREE_HOURS || hearts(playerId) <= 10 || kills(playerId) < 3;
    }

    @Override
    public void markTradeRestricted(ItemStack item, String reason) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(restrictedKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(restrictionReasonKey, PersistentDataType.STRING, reason == null ? "Gameplay reward" : reason);
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.removeIf(line -> org.bukkit.ChatColor.stripColor(line == null ? "" : line).startsWith("Trade restricted:"));
        lore.add(Text.color("&8Trade restricted: " + (reason == null ? "Gameplay reward" : reason)));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @Override
    public boolean isTradeRestricted(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta() != null
            && item.getItemMeta().getPersistentDataContainer().has(restrictedKey, PersistentDataType.BYTE);
    }

    @Override
    public String restrictionReason(ItemStack item) {
        if (!isTradeRestricted(item)) {
            return "";
        }
        String reason = item.getItemMeta().getPersistentDataContainer().get(restrictionReasonKey, PersistentDataType.STRING);
        return reason == null || reason.isBlank() ? "Gameplay reward" : reason;
    }

    @Override
    public void recordSkirmishParticipation(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            addProgress(player, "skirmish", 1.0D, 1.0D);
        }
    }

    private ItemStack restricted(ItemStack item, String reason) {
        markTradeRestricted(item, reason);
        return item;
    }

    private ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void give(Player player, List<ItemStack> items) {
        player.getInventory().addItem(items.toArray(ItemStack[]::new)).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void addProgress(Player player, String id, double amount, double required) {
        if (player == null || !isRookie(player.getUniqueId()) || data.contains("rookie." + player.getUniqueId() + ".claimed." + id)) {
            return;
        }
        String key = "rookie." + player.getUniqueId() + ".progress." + id;
        double before = data.getDouble(key, 0.0D);
        double after = Math.min(required, before + amount);
        data.set(key, after);
        if (before < required && after >= required) {
            Text.msg(player, "&6Rookie contract complete: &f/rookie &7to claim your reward.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.6F);
            data.save();
            dirty = false;
            return;
        }
        dirty = true;
    }

    private boolean eligibleFirstBloodVictim(Player victim) {
        UUID id = victim.getUniqueId();
        long played = data.getLong("playedMillis." + id, 0L);
        Long joined = joinedAt.get(id);
        if (joined != null) {
            played += Math.max(0L, System.currentTimeMillis() - joined);
        }
        return played >= 30L * 60L * 1000L || kills(id) > 0 || hearts(id) != 10;
    }

    private void flushProgress() {
        if (dirty) {
            data.save();
            dirty = false;
        }
    }

    private double progress(Player player, String id) {
        return data.getDouble("rookie." + player.getUniqueId() + ".progress." + id, 0.0D);
    }

    private int hearts(UUID id) {
        HeartService hearts = MitchSMP.hearts();
        return hearts == null ? 10 : hearts.getHearts(id);
    }

    private int kills(UUID id) {
        return progression.getInt("legacy.kills." + id, 0);
    }

    private void addCombatXp(UUID id, int amount, String reason) {
        SkillService skills = MitchSMP.skills();
        if (skills != null) {
            skills.addXp(id, "combat", amount, reason);
        }
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        return null;
    }

    private double damageAmount(EntityDamageByEntityEvent event) {
        for (String method : List.of("getFinalDamage", "getDamage")) {
            try {
                Object result = event.getClass().getMethod(method).invoke(event);
                if (result instanceof Number number) {
                    return Math.max(0.1D, number.doubleValue());
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return 1.0D;
    }

    private boolean isHostile(Entity entity) {
        String type = entity == null || entity.getType() == null ? "" : entity.getType().name();
        return HOSTILE_HINTS.stream().anyMatch(type::contains);
    }

    private boolean restrictedWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return true;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return MitchSMP.permissions().isAdminRestricted(player) || world.startsWith("bedwars_") || world.startsWith("tntrun_") || world.startsWith("spleef_") || world.startsWith("skirmish_") || world.startsWith("bloodbound_hub") || world.startsWith("bloodbound_skyblock") || world.startsWith("mitchtest_");
    }

    private boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld());
    }

    private double horizontalDistance(Location first, Location second) {
        double dx = second.getX() - first.getX();
        double dz = second.getZ() - first.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private String pairKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) <= 0 ? a + "." + b : b + "." + a;
    }

    private String nearby(Player target) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(target) && sameWorld(player.getLocation(), target.getLocation()) && player.getLocation().distanceSquared(target.getLocation()) <= 64.0D * 64.0D) {
                names.add(player.getName());
            }
        }
        return names.isEmpty() ? "none" : String.join(",", names);
    }

    private String shortLocation(Location location) {
        return location == null || location.getWorld() == null ? "unknown" : location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private List<String> reportIds(String status) {
        List<Long> ids = new ArrayList<>();
        for (String key : data.keys()) {
            if (!key.startsWith("reports.") || !key.endsWith(".status")) {
                continue;
            }
            String raw = key.substring("reports.".length(), key.length() - ".status".length());
            try {
                long id = Long.parseLong(raw);
                if (status.equals("ALL") || data.getString(key, "OPEN").equalsIgnoreCase(status)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        ids.sort(Comparator.reverseOrder());
        return ids.stream().map(String::valueOf).toList();
    }

    private boolean isReportStaff(CommandSender sender) {
        return !(sender instanceof Player) || MitchSMP.permissions().has(sender, "mitchsmp.reports.staff");
    }

    private void alertReportStaff(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isReportStaff(player)) {
                Text.msg(player, message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(Text.color(message));
    }

    private String formatDuration(long millis) {
        long minutes = Math.max(1L, millis / 60_000L);
        return minutes >= 60L ? (minutes / 60L) + "h " + (minutes % 60L) + "m" : minutes + "m";
    }

    private String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String clip(String value, int length) {
        if (value == null) {
            return "";
        }
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 3)) + "...";
    }

    private record DamageContribution(long at, double damage) {
    }

    private static final class RookieMenu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ReportsMenu implements InventoryHolder {
        private final String filter;
        private final int page;
        private final Map<Integer, String> ids = new HashMap<>();
        private Inventory inventory;

        ReportsMenu(String filter, int page) {
            this.filter = filter;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
