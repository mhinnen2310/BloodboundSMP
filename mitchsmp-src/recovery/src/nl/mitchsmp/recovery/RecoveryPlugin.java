package nl.mitchsmp.recovery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import nl.mitchsmp.core.api.InventorySnapshotService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class RecoveryPlugin extends JavaPlugin implements Listener, TabCompleter, InventorySnapshotService {
    private static final long HISTORY_RETENTION_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int MAX_REGION_VOLUME = 250_000;
    private static final int MAX_SNAPSHOTS_PER_PLAYER = 60;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, PendingRollback> pendingRollbacks = new HashMap<>();
    private final Deque<BlockChange> blockHistory = new ArrayDeque<>();
    private final AtomicLong snapshotSequence = new AtomicLong();
    private ExecutorService fileWriter;
    private PropertiesFile snapshots;
    private Path blockLog;
    private Path auditLog;
    private NamespacedKey wandKey;
    private volatile boolean applyingRollback;

    @Override
    public void onEnable() {
        Path root = getDataFolder().toPath();
        snapshots = new PropertiesFile(root.resolve("inventory-snapshots.properties"));
        blockLog = root.resolve("block-history.log");
        auditLog = root.resolve("recovery-audit.log");
        fileWriter = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Bloodbound-Recovery-Writer");
            thread.setDaemon(true);
            return thread;
        });
        wandKey = new NamespacedKey(this, "rollback_wand");
        loadBlockHistory();
        MitchSMP.registerService(InventorySnapshotService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("rollback") != null) {
            getCommand("rollback").setExecutor(this);
            getCommand("rollback").setTabCompleter(this);
        }
        getLogger().info("Recovery ready: " + blockHistory.size() + " recent block changes, max region " + MAX_REGION_VOLUME + " blocks.");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            capture(player, "server-shutdown");
        }
        if (fileWriter != null) {
            fileWriter.shutdown();
            try {
                fileWriter.awaitTermination(5L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) {
            record(event.getBlock(), event.getPlayer().getUniqueId().toString(), "break");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }
        BlockState replaced = event.getBlockReplacedState();
        record(event.getBlock().getLocation(), replaced.getType(), replaced.getBlockData().getAsString(), event.getPlayer().getUniqueId().toString(), "place");
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!event.isCancelled()) {
            event.blockList().forEach(block -> record(block, "EXPLOSION", "entity-explosion"));
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!event.isCancelled()) {
            event.blockList().forEach(block -> record(block, "EXPLOSION", "block-explosion"));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        capture(event.getEntity(), "death");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        capture(event.getPlayer(), "world-change:" + event.getFrom().getName() + "->" + event.getPlayer().getWorld().getName());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        capture(event.getPlayer(), "gamemode-change:" + event.getPlayer().getGameMode() + "->" + event.getNewGameMode());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        capture(event.getPlayer(), "quit");
    }

    @EventHandler
    public void onWand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!isWand(item) || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!MitchSMP.permissions().has(player, "mitchsmp.recovery.admin")) {
            return;
        }
        event.setCancelled(true);
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        Location location = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = location;
            Text.msg(player, "&aRollback position 1: &f" + shortLocation(location));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.second = location;
            Text.msg(player, "&aRollback position 2: &f" + shortLocation(location));
        }
        pendingRollbacks.remove(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.recovery.admin")) {
            Text.msg(sender, "&cYou do not have permission to use recovery tools.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cRegion selections and inventory restores require an in-game staff player.");
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("wand")) {
            giveWand(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("inventory")) {
            return inventoryCommand(player, args);
        }
        return rollbackCommand(player, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.recovery.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Tab.complete(args[0], "wand", "20m", "2h", "4h", "inventory");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("inventory")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("inventory")) {
            return Tab.complete(args[2], "list", "restore", "take");
        }
        return List.of();
    }

    @Override
    public synchronized String capture(Player player, String trigger) {
        if (player == null) {
            return "";
        }
        long timestamp = System.currentTimeMillis();
        String id = timestamp + "-" + snapshotSequence.incrementAndGet();
        String base = "snapshot." + player.getUniqueId() + "." + id + ".";
        Location location = player.getLocation();
        snapshots.set(base + "timestamp", timestamp);
        snapshots.set(base + "trigger", clean(trigger));
        snapshots.set(base + "world", location.getWorld() == null ? "unknown" : location.getWorld().getName());
        snapshots.set(base + "location", shortLocation(location));
        snapshots.set(base + "gamemode", player.getGameMode().name());
        snapshots.set(base + "adminmode", MitchSMP.permissions().isAdminMode(player));
        snapshots.set(base + "inventory", encodeItems(player.getInventory().getContents()));
        snapshots.set(base + "enderchest", encodeItems(player.getEnderChest().getContents()));
        trimSnapshots(player.getUniqueId());
        snapshots.save();
        return id;
    }

    @Override
    public synchronized List<SnapshotInfo> list(UUID playerId, int limit) {
        String prefix = "snapshot." + playerId + ".";
        return snapshots.keys().stream()
            .filter(key -> key.startsWith(prefix) && key.endsWith(".trigger"))
            .map(key -> key.substring(prefix.length(), key.length() - ".trigger".length()))
            .map(id -> info(playerId, id))
            .filter(info -> info != null)
            .sorted(Comparator.comparingLong(SnapshotInfo::timestamp).reversed())
            .limit(Math.max(1, limit))
            .toList();
    }

    @Override
    public synchronized boolean restore(Player player, String snapshotId) {
        String base = "snapshot." + player.getUniqueId() + "." + snapshotId + ".";
        if (!snapshots.contains(base + "inventory")) {
            return false;
        }
        ItemStack[] inventory = decodeItems(snapshots.getString(base + "inventory", ""));
        ItemStack[] ender = decodeItems(snapshots.getString(base + "enderchest", ""));
        if (inventory == null || ender == null) {
            return false;
        }
        capture(player, "before-restore:" + snapshotId);
        player.getInventory().clear();
        player.getInventory().setContents(resize(inventory, player.getInventory().getSize()));
        player.getEnderChest().clear();
        player.getEnderChest().setContents(resize(ender, player.getEnderChest().getSize()));
        return true;
    }

    private boolean rollbackCommand(Player player, String[] args) {
        boolean confirm = args[args.length - 1].equalsIgnoreCase("confirm");
        int tokenCount = confirm ? args.length - 1 : args.length;
        long duration = parseDuration(args, tokenCount);
        if (duration <= 0L) {
            Text.msg(player, "&cInvalid duration. Examples: &f/rb 20m&c, &f/rb 2h&c, &f/rb 4h 20m&c.");
            return true;
        }
        Region region = region(player);
        if (region == null) {
            return true;
        }
        long cutoff = System.currentTimeMillis() - duration;
        List<BlockChange> matches = matchingChanges(region, cutoff);
        if (!confirm) {
            pendingRollbacks.put(player.getUniqueId(), new PendingRollback(region, duration, cutoff, matches.size(), System.currentTimeMillis() + 60_000L));
            Text.msg(player, "&eRollback preview: &f" + matches.size() + " &echanges inside &f" + region.volume() + " &eblocks.");
            Text.msg(player, "&7No blocks changed. Repeat with &fconfirm &7within 60 seconds: &f/rb " + durationText(duration) + " confirm");
            return true;
        }
        PendingRollback pending = pendingRollbacks.remove(player.getUniqueId());
        if (pending == null || pending.expiresAt < System.currentTimeMillis() || !pending.region.equals(region) || pending.duration != duration) {
            Text.msg(player, "&cPreview missing, expired or changed. Run the rollback command once without confirm first.");
            return true;
        }
        cutoff = pending.cutoff;
        matches = matchingChanges(region, cutoff);
        applyingRollback = true;
        int restored = 0;
        try {
            for (BlockChange change : matches) {
                World world = Bukkit.getWorld(change.world);
                if (world == null) {
                    continue;
                }
                Block block = world.getBlockAt(change.x, change.y, change.z);
                try {
                    block.setBlockData(Bukkit.createBlockData(change.blockData), false);
                } catch (IllegalArgumentException exception) {
                    block.setType(Material.valueOf(change.material), false);
                }
                restored++;
            }
        } finally {
            applyingRollback = false;
        }
        audit(player.getName() + " restored " + restored + " changes in " + region + " since " + Instant.ofEpochMilli(cutoff));
        Text.msg(player, "&aRollback complete: &f" + restored + " &ablock changes restored inside the selected region only.");
        return true;
    }

    private boolean inventoryCommand(Player staff, String[] args) {
        if (args.length < 3) {
            Text.msg(staff, "&cUsage: /rb inventory <player> <list|take|restore <id>>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(staff, "&cPlayer must be online for inventory recovery.");
            return true;
        }
        if (args[2].equalsIgnoreCase("list")) {
            List<SnapshotInfo> available = list(target.getUniqueId(), 12);
            Text.msg(staff, "&6Snapshots for &f" + target.getName() + "&6:");
            available.forEach(info -> Text.msg(staff, "&7" + info.id() + " &f" + info.trigger() + " &8| &7" + info.world() + " " + info.location() + " &8| &7" + Instant.ofEpochMilli(info.timestamp())));
            if (available.isEmpty()) {
                Text.msg(staff, "&7No snapshots found.");
            }
            return true;
        }
        if (args[2].equalsIgnoreCase("take")) {
            String id = capture(target, "manual-by:" + staff.getName());
            audit(staff.getName() + " captured inventory " + id + " for " + target.getName());
            Text.msg(staff, "&aSnapshot captured: &f" + id);
            return true;
        }
        if (args[2].equalsIgnoreCase("restore") && args.length >= 4) {
            if (!restore(target, args[3])) {
                Text.msg(staff, "&cSnapshot not found or corrupt. No inventory changes were made.");
                return true;
            }
            audit(staff.getName() + " restored inventory " + args[3] + " for " + target.getName());
            Text.msg(staff, "&aRestored snapshot &f" + args[3] + " &afor &f" + target.getName() + "&a.");
            Text.msg(target, "&eYour inventory was restored by staff from snapshot &f" + args[3] + "&e.");
            return true;
        }
        Text.msg(staff, "&cUsage: /rb inventory <player> <list|take|restore <id>>");
        return true;
    }

    private void record(Block block, String actor, String cause) {
        record(block.getLocation(), block.getType(), block.getBlockData().getAsString(), actor, cause);
    }

    private void record(Location location, Material material, String blockData, String actor, String cause) {
        if (applyingRollback || location == null || location.getWorld() == null) {
            return;
        }
        BlockChange change = new BlockChange(System.currentTimeMillis(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), material.name(), blockData, clean(actor), clean(cause));
        synchronized (blockHistory) {
            blockHistory.addLast(change);
            pruneHistory();
        }
        append(blockLog, change.encode());
    }

    private void loadBlockHistory() {
        if (!Files.exists(blockLog)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - HISTORY_RETENTION_MILLIS;
        try {
            for (String line : Files.readAllLines(blockLog, StandardCharsets.UTF_8)) {
                BlockChange change = BlockChange.decode(line);
                if (change != null && change.timestamp >= cutoff) {
                    blockHistory.addLast(change);
                }
            }
        } catch (IOException exception) {
            getLogger().severe("Could not load block history: " + exception.getMessage());
        }
    }

    private List<BlockChange> matchingChanges(Region region, long cutoff) {
        synchronized (blockHistory) {
            return blockHistory.stream()
                .filter(change -> change.timestamp >= cutoff && region.contains(change))
                .sorted(Comparator.comparingLong(BlockChange::timestamp).reversed())
                .toList();
        }
    }

    private void pruneHistory() {
        long cutoff = System.currentTimeMillis() - HISTORY_RETENTION_MILLIS;
        while (!blockHistory.isEmpty() && blockHistory.peekFirst().timestamp < cutoff) {
            blockHistory.removeFirst();
        }
    }

    private Region region(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null || selection.second == null) {
            Text.msg(player, "&cSelect both corners first with &f/rb wand&c.");
            return null;
        }
        if (selection.first.getWorld() == null || selection.second.getWorld() == null || !selection.first.getWorld().getName().equals(selection.second.getWorld().getName())) {
            Text.msg(player, "&cBoth rollback positions must be in the same world.");
            return null;
        }
        Region region = Region.of(selection.first, selection.second);
        if (region.volume() > MAX_REGION_VOLUME) {
            Text.msg(player, "&cSelection is too large: &f" + region.volume() + "&c. Maximum is &f" + MAX_REGION_VOLUME + "&c.");
            return null;
        }
        return region;
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(Text.color("&6&lRollback Wand"));
        meta.setLore(List.of(Text.color("&7Left click: position 1"), Text.color("&7Right click: position 2"), Text.color("&cRollback never affects blocks outside the cuboid.")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        Text.msg(player, "&aRollback Wand received. Select two corners.");
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private SnapshotInfo info(UUID playerId, String id) {
        String base = "snapshot." + playerId + "." + id + ".";
        if (!snapshots.contains(base + "trigger")) {
            return null;
        }
        return new SnapshotInfo(id, snapshots.getLong(base + "timestamp", 0L), snapshots.getString(base + "trigger", "unknown"), snapshots.getString(base + "world", "unknown"), snapshots.getString(base + "location", "unknown"), snapshots.getString(base + "gamemode", "unknown"), Boolean.parseBoolean(snapshots.getString(base + "adminmode", "false")));
    }

    private void trimSnapshots(UUID playerId) {
        List<SnapshotInfo> all = list(playerId, Integer.MAX_VALUE);
        for (int index = MAX_SNAPSHOTS_PER_PLAYER; index < all.size(); index++) {
            String prefix = "snapshot." + playerId + "." + all.get(index).id() + ".";
            snapshots.keys().stream().filter(key -> key.startsWith(prefix)).toList().forEach(key -> snapshots.set(key, null));
        }
    }

    private void append(Path path, String line) {
        fileWriter.execute(() -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                getLogger().severe("Recovery log write failed: " + exception.getMessage());
            }
        });
    }

    private void audit(String message) {
        append(auditLog, System.currentTimeMillis() + "\t" + clean(message));
        getLogger().warning("RECOVERY AUDIT: " + message);
    }

    private static String encodeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(items.length);
                for (ItemStack item : items) {
                    if (item == null || item.getType() == Material.AIR) {
                        output.writeInt(-1);
                    } else {
                        byte[] serialized = item.serializeAsBytes();
                        output.writeInt(serialized.length);
                        output.write(serialized);
                    }
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode inventory", exception);
        }
    }

    private static ItemStack[] decodeItems(String encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            int length = input.readInt();
            if (length < 0 || length > 200) {
                return null;
            }
            ItemStack[] items = new ItemStack[length];
            for (int index = 0; index < length; index++) {
                int size = input.readInt();
                if (size >= 0) {
                    if (size > 2_000_000) {
                        return null;
                    }
                    items[index] = ItemStack.deserializeBytes(input.readNBytes(size));
                }
            }
            return items;
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static ItemStack[] resize(ItemStack[] input, int size) {
        ItemStack[] result = new ItemStack[size];
        System.arraycopy(input, 0, result, 0, Math.min(input.length, size));
        return result;
    }

    private static long parseDuration(String[] args, int count) {
        long result = 0L;
        for (int index = 0; index < count; index++) {
            String token = args[index].toLowerCase(Locale.ROOT);
            if (!token.matches("\\d+[mhd]")) {
                return -1L;
            }
            long value = Long.parseLong(token.substring(0, token.length() - 1));
            result += switch (token.charAt(token.length() - 1)) {
                case 'm' -> value * 60_000L;
                case 'h' -> value * 3_600_000L;
                case 'd' -> value * 86_400_000L;
                default -> 0L;
            };
        }
        return Math.min(result, HISTORY_RETENTION_MILLIS);
    }

    private static String durationText(long duration) {
        long hours = duration / 3_600_000L;
        long minutes = (duration % 3_600_000L) / 60_000L;
        return (hours > 0 ? hours + "h" : "") + (minutes > 0 ? (hours > 0 ? " " : "") + minutes + "m" : "");
    }

    private static String shortLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private static String clean(String input) {
        return input == null ? "unknown" : input.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    private void usage(Player player) {
        Text.msg(player, "&6Recovery commands:");
        Text.msg(player, "&f/rb wand &7- select a bounded cuboid.");
        Text.msg(player, "&f/rb 20m &7- preview changes; append &fconfirm &7to apply.");
        Text.msg(player, "&f/rb inventory <player> list|take|restore <id>");
    }

    private static final class Selection {
        private Location first;
        private Location second;
    }

    private record PendingRollback(Region region, long duration, long cutoff, int count, long expiresAt) {
    }

    private record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Region of(Location first, Location second) {
            return new Region(first.getWorld().getName(), Math.min(first.getBlockX(), second.getBlockX()), Math.min(first.getBlockY(), second.getBlockY()), Math.min(first.getBlockZ(), second.getBlockZ()), Math.max(first.getBlockX(), second.getBlockX()), Math.max(first.getBlockY(), second.getBlockY()), Math.max(first.getBlockZ(), second.getBlockZ()));
        }

        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        boolean contains(BlockChange change) {
            return world.equals(change.world) && change.x >= minX && change.x <= maxX && change.y >= minY && change.y <= maxY && change.z >= minZ && change.z <= maxZ;
        }
    }

    private record BlockChange(long timestamp, String world, int x, int y, int z, String material, String blockData, String actor, String cause) {
        String encode() {
            return timestamp + "\t" + b64(world) + "\t" + x + "\t" + y + "\t" + z + "\t" + material + "\t" + b64(blockData) + "\t" + b64(actor) + "\t" + b64(cause);
        }

        static BlockChange decode(String line) {
            try {
                String[] parts = line.split("\\t", -1);
                if (parts.length != 9) {
                    return null;
                }
                return new BlockChange(Long.parseLong(parts[0]), unb64(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), parts[5], unb64(parts[6]), unb64(parts[7]), unb64(parts[8]));
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private static String b64(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String unb64(String value) {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }
}
