package nl.mitchsmp.safezones;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SafezonesPlugin extends JavaPlugin implements Listener, TabCompleter {
    private final Map<String, Safezone> zones = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, String> lastZone = new HashMap<>();
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("safezones.properties"));
        loadZones();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("safezone") != null) {
            getCommand("safezone").setExecutor(this);
            getCommand("safezone").setTabCompleter(this);
        }
        if (getCommand("commandsign") != null) {
            getCommand("commandsign").setExecutor(this);
            getCommand("commandsign").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tickZoneEffects, 100L, 100L);
        getLogger().info("Loaded " + zones.size() + " Bloodbound safezones.");
    }

    @Override
    public void onDisable() {
        saveZones();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        if (command.getName().equalsIgnoreCase("commandsign")) {
            return commandSign(sender, args);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            list(sender);
            return true;
        }
        if (sub.equals("info")) {
            info(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        if (!isAdmin(sender)) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (!(sender instanceof Player player) && !sub.equals("reload")) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        switch (sub) {
            case "wand" -> wand((Player) sender);
            case "pos1" -> setPosition((Player) sender, true, ((Player) sender).getLocation().getBlock().getLocation());
            case "pos2" -> setPosition((Player) sender, false, ((Player) sender).getLocation().getBlock().getLocation());
            case "create" -> create((Player) sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "flag" -> flag(sender, args);
            case "allow" -> allow(sender, args);
            case "tp" -> teleport((Player) sender, args);
            case "reload" -> {
                loadZones();
                Text.msg(sender, "&aSafezones reloaded.");
            }
            default -> help(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = isAdmin(sender);
        if (args.length == 1) {
            if (command.getName().equalsIgnoreCase("commandsign")) {
                return isAdmin(sender) ? Tab.complete(args[0], "set", "text", "remove", "info") : List.of();
            }
            List<String> base = new ArrayList<>(List.of("list", "info"));
            if (admin) {
                base.addAll(List.of("wand", "pos1", "pos2", "create", "delete", "flag", "allow", "tp", "reload"));
            }
            return Tab.complete(args[0], base);
        }
        if (command.getName().equalsIgnoreCase("commandsign")) {
            if (!admin) {
                return List.of();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("text")) {
                return Tab.complete(args[1], "1", "2", "3", "4");
            }
            return List.of();
        }
        if (args.length == 2 && List.of("info", "delete", "remove", "flag", "tp").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Tab.complete(args[1], zones.keySet());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("allow")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("allow")) {
            return Tab.complete(args[2], zones.keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("flag")) {
            return Tab.complete(args[2], "pvp", "mobspawn", "hunger", "build", "explosions", "fall_damage", "hostile_damage", "healing", "fixed_day");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("flag")) {
            return Tab.complete(args[3], "true", "false");
        }
        return List.of();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (handleCommandSignClick(event)) {
            return;
        }
        if (!isAdmin(event.getPlayer()) || !isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        setPosition(event.getPlayer(), event.getAction() == Action.LEFT_CLICK_BLOCK, event.getClickedBlock().getLocation());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (player == null || to == null) {
            return;
        }
        Safezone zone = zoneAt(to);
        String oldId = lastZone.get(player.getUniqueId());
        String newId = zone == null ? null : zone.id();
        if (oldId == null ? newId == null : oldId.equals(newId)) {
            return;
        }
        if (newId == null) {
            lastZone.remove(player.getUniqueId());
            Text.msg(player, "&8[&4Bloodbound&8] &7You left the safezone.");
        } else {
            lastZone.put(player.getUniqueId(), newId);
            Text.msg(player, "&8[&4Bloodbound&8] &6Safezone entered: &f" + zone.id());
        }
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        Safezone zone = zoneAt(victim.getLocation());
        if (zone == null) {
            zone = zoneAt(attacker.getLocation());
        }
        if (zone != null && !zone.flag("pvp")) {
            event.setCancelled(true);
            Text.msg(attacker, "&cPvP is disabled in this safezone.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Safezone zone = zoneAt(player.getLocation());
        if (zone == null) {
            return;
        }
        String cause = event.getCause() == null ? "" : event.getCause().name().toLowerCase(Locale.ROOT);
        if (cause.contains("fall") && !zone.flag("fall_damage")) {
            event.setCancelled(true);
        }
        if ((cause.contains("entity") || cause.contains("mob")) && !zone.flag("hostile_damage")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        Safezone zone = zoneAt(event.getLocation());
        if (zone != null && !zone.flag("mobspawn")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            Safezone zone = zoneAt(player.getLocation());
            if (zone != null && !zone.flag("hunger")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Safezone zone = zoneAt(event.getBlock().getLocation());
        if (zone != null && !zone.flag("build") && !canBuild(event.getPlayer(), zone)) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cThis safezone is protected.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Safezone zone = zoneAt(event.getBlock().getLocation());
        if (zone != null && !zone.flag("build") && !canBuild(event.getPlayer(), zone)) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cThis safezone is protected.");
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> blocksExplosions(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> blocksExplosions(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> zoneAt(block.getLocation()) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> zoneAt(block.getLocation()) != null)) {
            event.setCancelled(true);
        }
    }

    private void help(CommandSender sender) {
        Text.msg(sender, "&4Bloodbound Safezones");
        Text.msg(sender, "&7/safezone list &8- &fshow zones");
        Text.msg(sender, "&7/safezone info [id] &8- &finspect current/selected zone");
        if (isAdmin(sender)) {
            Text.msg(sender, "&7/safezone wand, pos1, pos2, create <id>, delete <id>");
            Text.msg(sender, "&7/safezone flag <id> <flag> <true|false>");
            Text.msg(sender, "&7/safezone allow <player> <id> [true|false]");
            Text.msg(sender, "&7/commandsign set <command>, text <line> <text>, remove, info");
        }
    }

    private void list(CommandSender sender) {
        if (zones.isEmpty()) {
            Text.msg(sender, "&7No safezones configured.");
            return;
        }
        Text.msg(sender, "&6Safezones: &f" + String.join("&7, &f", zones.keySet()));
    }

    private void info(CommandSender sender, String id) {
        Safezone zone = id == null && sender instanceof Player player ? zoneAt(player.getLocation()) : zones.get(normalize(id));
        if (zone == null) {
            Text.msg(sender, "&cSafezone not found.");
            return;
        }
        Text.msg(sender, "&6Safezone &f" + zone.id() + " &7in &f" + zone.world());
        Text.msg(sender, "&7Bounds: &f" + zone.minX() + "," + zone.minY() + "," + zone.minZ() + " &7to &f" + zone.maxX() + "," + zone.maxY() + "," + zone.maxZ());
        Text.msg(sender, "&7Flags: &fpvp=" + zone.flag("pvp") + " mobspawn=" + zone.flag("mobspawn") + " hunger=" + zone.flag("hunger") + " build=" + zone.flag("build") + " healing=" + zone.flag("healing") + " fixed_day=" + zone.flag("fixed_day"));
        Text.msg(sender, "&7Allowed builders: &f" + (zone.allowed().isEmpty() ? "none" : String.join(", ", zone.allowed())));
    }

    private void wand(Player player) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6Safezone Wand"));
            meta.setLore(List.of(Text.color("&7Left-click: pos1"), Text.color("&7Right-click: pos2")));
            item.setItemMeta(meta);
        }
        player.getInventory().addItem(item);
        Text.msg(player, "&aSafezone wand granted.");
    }

    private void setPosition(Player player, boolean first, Location location) {
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (first) {
            selection.pos1 = blockLocation(location);
        } else {
            selection.pos2 = blockLocation(location);
        }
        Text.msg(player, "&aSafezone pos" + (first ? "1" : "2") + " set to &f" + shortLocation(location));
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            Text.msg(player, "&cUsage: /safezone create <id>");
            return;
        }
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.pos1 == null || selection.pos2 == null || selection.pos1.getWorld() == null || !selection.pos1.getWorld().equals(selection.pos2.getWorld())) {
            Text.msg(player, "&cSet pos1 and pos2 in the same world first.");
            return;
        }
        String id = normalize(args[1]);
        Location a = selection.pos1;
        Location b = selection.pos2;
        Map<String, Boolean> flags = defaultFlags();
        Safezone zone = new Safezone(id, a.getWorld().getName(),
            Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
            Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()),
            flags, new HashSet<>());
        zones.put(id, zone);
        saveZones();
        Text.msg(player, "&aSafezone created: &f" + id);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.msg(sender, "&cUsage: /safezone delete <id>");
            return;
        }
        Safezone removed = zones.remove(normalize(args[1]));
        if (removed == null) {
            Text.msg(sender, "&cSafezone not found.");
            return;
        }
        saveZones();
        Text.msg(sender, "&aSafezone deleted: &f" + removed.id());
    }

    private void flag(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Text.msg(sender, "&cUsage: /safezone flag <id> <flag> <true|false>");
            return;
        }
        Safezone zone = zones.get(normalize(args[1]));
        if (zone == null) {
            Text.msg(sender, "&cSafezone not found.");
            return;
        }
        String flag = args[2].toLowerCase(Locale.ROOT);
        if (!defaultFlags().containsKey(flag)) {
            Text.msg(sender, "&cUnknown flag.");
            return;
        }
        zone.flags().put(flag, Boolean.parseBoolean(args[3]));
        saveZones();
        Text.msg(sender, "&aSafezone flag updated: &f" + zone.id() + " " + flag + "=" + zone.flag(flag));
    }

    private void allow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.msg(sender, "&cUsage: /safezone allow <player> <safezone> [true|false]");
            return;
        }
        String playerName = normalize(args[1]);
        Safezone zone = zones.get(normalize(args[2]));
        if (playerName.isBlank() || zone == null) {
            Text.msg(sender, "&cPlayer or safezone not found.");
            return;
        }
        boolean allow = args.length < 4 || Boolean.parseBoolean(args[3]);
        if (allow) {
            zone.allowed().add(playerName);
        } else {
            zone.allowed().remove(playerName);
        }
        saveZones();
        Text.msg(sender, "&aSafezone build access for &f" + playerName + " &ain &f" + zone.id() + " &ais now &f" + allow);
    }

    private void teleport(Player player, String[] args) {
        if (args.length < 2) {
            Text.msg(player, "&cUsage: /safezone tp <id>");
            return;
        }
        Safezone zone = zones.get(normalize(args[1]));
        World world = zone == null ? null : Bukkit.getWorld(zone.world());
        if (zone == null || world == null) {
            Text.msg(player, "&cSafezone/world not found.");
            return;
        }
        player.teleport(new Location(world, (zone.minX() + zone.maxX()) / 2.0D + 0.5D, zone.maxY() + 1.0D, (zone.minZ() + zone.maxZ()) / 2.0D + 0.5D));
    }

    private void loadZones() {
        zones.clear();
        for (String key : data.keys()) {
            if (!key.startsWith("zone.") || !key.endsWith(".world")) {
                continue;
            }
            String id = key.substring("zone.".length(), key.length() - ".world".length());
            Map<String, Boolean> flags = defaultFlags();
            for (String flag : flags.keySet()) {
                flags.put(flag, Boolean.parseBoolean(data.getString("zone." + id + ".flag." + flag, String.valueOf(flags.get(flag)))));
            }
            Set<String> allowed = new HashSet<>();
            String rawAllowed = data.getString("zone." + id + ".allowed", "");
            for (String part : rawAllowed.split(",")) {
                String normalized = normalize(part);
                if (!normalized.isBlank()) {
                    allowed.add(normalized);
                }
            }
            zones.put(id, new Safezone(id, data.getString("zone." + id + ".world", "world"),
                data.getInt("zone." + id + ".minX", 0), data.getInt("zone." + id + ".minY", -64), data.getInt("zone." + id + ".minZ", 0),
                data.getInt("zone." + id + ".maxX", 0), data.getInt("zone." + id + ".maxY", 320), data.getInt("zone." + id + ".maxZ", 0),
                flags, allowed));
        }
    }

    private void saveZones() {
        for (String key : new ArrayList<>(data.keys())) {
            if (key.startsWith("zone.")) {
                data.set(key, null);
            }
        }
        for (Safezone zone : zones.values().stream().sorted(Comparator.comparing(Safezone::id)).toList()) {
            String prefix = "zone." + zone.id() + ".";
            data.set(prefix + "world", zone.world());
            data.set(prefix + "minX", zone.minX());
            data.set(prefix + "minY", zone.minY());
            data.set(prefix + "minZ", zone.minZ());
            data.set(prefix + "maxX", zone.maxX());
            data.set(prefix + "maxY", zone.maxY());
            data.set(prefix + "maxZ", zone.maxZ());
            for (Map.Entry<String, Boolean> entry : zone.flags().entrySet()) {
                data.set(prefix + "flag." + entry.getKey(), entry.getValue());
            }
            data.set(prefix + "allowed", String.join(",", zone.allowed()));
        }
        data.save();
    }

    private boolean commandSign(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        Block block = targetedBlock(player, 6);
        if (block == null || !isSignBlock(block)) {
            Text.msg(player, "&cLook at a sign within 6 blocks.");
            return true;
        }
        String key = commandSignKey(block);
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            Text.msg(player, "&6Command sign: &f" + key);
            Text.msg(player, "&7Command: &f" + data.getString("commandsign." + key + ".command", "none"));
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            for (String stored : new ArrayList<>(data.keys())) {
                if (stored.startsWith("commandsign." + key + ".")) {
                    data.set(stored, null);
                }
            }
            data.save();
            Text.msg(player, "&aCommand sign removed.");
            return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                Text.msg(player, "&cUsage: /commandsign set <command without slash>");
                return true;
            }
            String command = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).replaceFirst("^/", "");
            data.set("commandsign." + key + ".command", command);
            data.save();
            Text.msg(player, "&aCommand sign command set to &f/" + command);
            return true;
        }
        if (args[0].equalsIgnoreCase("text")) {
            if (args.length < 3) {
                Text.msg(player, "&cUsage: /commandsign text <1-4> <text>");
                return true;
            }
            int line = Math.max(1, Math.min(4, parseInt(args[1], 1))) - 1;
            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            setSignLine(block, line, Text.color(text));
            data.set("commandsign." + key + ".line." + (line + 1), text);
            data.save();
            Text.msg(player, "&aCommand sign text updated.");
            return true;
        }
        Text.msg(player, "&cUsage: /commandsign <set|text|remove|info>");
        return true;
    }

    private boolean handleCommandSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || !isSignBlock(event.getClickedBlock())) {
            return false;
        }
        String command = data.getString("commandsign." + commandSignKey(event.getClickedBlock()) + ".command", "");
        if (command.isBlank()) {
            return false;
        }
        event.setCancelled(true);
        Bukkit.dispatchCommand(event.getPlayer(), command.replaceFirst("^/", ""));
        return true;
    }

    private Block targetedBlock(Player player, int range) {
        Location eye = player.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw());
        double pitch = Math.toRadians(eye.getPitch());
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal * 0.35D;
        double dy = -Math.sin(pitch) * 0.35D;
        double dz = Math.cos(yaw) * horizontal * 0.35D;
        double x = eye.getX();
        double y = eye.getY();
        double z = eye.getZ();
        for (int i = 0; i < range * 3; i++) {
            x += dx;
            y += dy;
            z += dz;
            Block block = new Location(eye.getWorld(), x, y, z).getBlock();
            if (block.getType() != Material.AIR) {
                return block;
            }
        }
        return null;
    }

    private boolean isSignBlock(Block block) {
        if (block == null) {
            return false;
        }
        String type = block.getType().name();
        return type.endsWith("_SIGN") || type.endsWith("_WALL_SIGN") || type.equals("SIGN") || type.equals("WALL_SIGN");
    }

    private void setSignLine(Block block, int line, String text) {
        try {
            Object state = block.getState();
            state.getClass().getMethod("setLine", int.class, String.class).invoke(state, line, text);
            state.getClass().getMethod("update", boolean.class, boolean.class).invoke(state, true, false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Text.msg(Bukkit.getConsoleSender(), "&cCould not update command sign text: " + exception.getMessage());
        }
    }

    private String commandSignKey(Block block) {
        Location location = block.getLocation();
        return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
    }

    private void tickZoneEffects() {
        for (Safezone zone : zones.values()) {
            World world = Bukkit.getWorld(zone.world());
            if (world != null && zone.flag("fixed_day")) {
                world.setTime(1000L);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Safezone zone = zoneAt(player.getLocation());
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            if (zone != null && zone.flag("healing") && player.getHealth() < maxHealth) {
                player.setHealth(Math.min(maxHealth, player.getHealth() + 1.0D));
            }
        }
    }

    private Safezone zoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (Safezone zone : zones.values()) {
            if (zone.contains(world, x, y, z)) {
                return zone;
            }
        }
        return null;
    }

    private boolean blocksExplosions(Location location) {
        Safezone zone = zoneAt(location);
        return zone != null && !zone.flag("explosions");
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && Chat.strip(meta.getDisplayName()).equalsIgnoreCase("Safezone Wand");
    }

    private boolean isAdmin(CommandSender sender) {
        return MitchSMP.permissions().has(sender, "mitchsmp.safezones.admin");
    }

    private boolean canBuild(Player player, Safezone zone) {
        return isAdmin(player) || zone.allowed().contains(normalize(player.getName()));
    }

    private Map<String, Boolean> defaultFlags() {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("pvp", false);
        flags.put("mobspawn", false);
        flags.put("hunger", false);
        flags.put("build", false);
        flags.put("explosions", false);
        flags.put("fall_damage", true);
        flags.put("hostile_damage", true);
        flags.put("healing", false);
        flags.put("fixed_day", false);
        return flags;
    }

    private int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Location blockLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private String shortLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static final class Chat {
        static String strip(String input) {
            return input == null ? "" : input.replaceAll("(?i)&[0-9a-fk-or]", "").replaceAll("(?i)\u00a7[0-9a-fk-or]", "");
        }
    }

    private static final class Selection {
        private Location pos1;
        private Location pos2;
    }

    private record Safezone(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Map<String, Boolean> flags, Set<String> allowed) {
        boolean contains(String worldName, int x, int y, int z) {
            return world.equals(worldName) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        boolean flag(String key) {
            return flags.getOrDefault(key, false);
        }
    }
}
