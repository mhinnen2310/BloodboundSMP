package nl.mitchsmp.spawners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnersPlugin extends JavaPlugin implements Listener, TabCompleter {
    private NamespacedKey typeKey;
    private NamespacedKey levelKey;
    private PropertiesFile data;

    @Override
    public void onEnable() {
        typeKey = new NamespacedKey(this, "spawner_type");
        levelKey = new NamespacedKey(this, "spawner_level");
        data = new PropertiesFile(getDataFolder().toPath().resolve("spawners.properties"));
        ensureDefaults();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("spawner") != null) {
            getCommand("spawner").setExecutor(this);
            getCommand("spawner").setTabCompleter(this);
        }
        getLogger().info("Bloodbound inventory spawners loaded. Types: " + String.join(", ", spawnerTypes()));
    }

    @Override
    public void onDisable() {
        data.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("types")) {
            Text.msg(sender, "&7Spawner types: &f" + String.join("&7, &f", spawnerTypes()));
            Text.msg(sender, "&7Admin: &f/spawner give <player> <type> [level]");
            return true;
        }
        if (!MitchSMP.permissions().has(sender, "mitchsmp.spawners.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            data.load();
            ensureDefaults();
            Text.msg(sender, "&aSpawner config reloaded.");
            return true;
        }
        if (!args[0].equalsIgnoreCase("give") || args.length < 3) {
            Text.msg(sender, "&cUsage: /spawner give <player> <type> [level]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(sender, "&cPlayer is not online.");
            return true;
        }
        String type = normalize(args[2]);
        if (!spawnerTypes().contains(type)) {
            Text.msg(sender, "&cUnknown spawner type.");
            return true;
        }
        int level = args.length >= 4 ? parseInt(args[3], 1, 1, maxLevel(type)) : 1;
        target.getInventory().addItem(spawnerItem(type, level)).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        Text.msg(sender, "&aSpawner granted: &f" + type + " &7level &f" + level);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("types"));
            if (MitchSMP.permissions().has(sender, "mitchsmp.spawners.admin")) {
                options.addAll(List.of("give", "reload"));
            }
            return Tab.complete(args[0], options);
        }
        if (!MitchSMP.permissions().has(sender, "mitchsmp.spawners.admin")) {
            return List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Tab.complete(args[2], spawnerTypes());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return Tab.complete(args[3], "1", "2", "3", "4", "5");
        }
        return List.of();
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        String type = itemType(hand);
        if (type == null || event.getBlock().getType() != Material.SPAWNER) {
            return;
        }
        String key = key(event.getBlock().getLocation());
        int level = itemLevel(hand);
        data.set(key + ".type", type);
        data.set(key + ".level", level);
        data.set(key + ".stored", 0);
        data.set(key + ".last", System.currentTimeMillis());
        data.saveSoon(this, 20L);
        Text.msg(event.getPlayer(), "&aPlaced &f" + display(type) + " &7Level &f" + level + "&a.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        String key = key(block.getLocation());
        String type = data.getString(key + ".type", "");
        if (type.isBlank()) {
            return;
        }
        try {
            event.getClass().getMethod("setDropItems", boolean.class).invoke(event, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        updateProduction(key, type);
        int level = data.getInt(key + ".level", 1);
        int stored = data.getInt(key + ".stored", 0);
        block.getLocation().getWorld().dropItemNaturally(block.getLocation(), spawnerItem(type, level));
        if (stored > 0) {
            block.getLocation().getWorld().dropItemNaturally(block.getLocation(), outputItem(type, stored));
        }
        clearSpawner(key);
        Text.msg(event.getPlayer(), "&eSpawner picked up. Stored output dropped.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }
        String key = key(event.getClickedBlock().getLocation());
        String type = data.getString(key + ".type", "");
        if (type.isBlank()) {
            return;
        }
        event.setCancelled(true);
        updateProduction(key, type);
        openMain(event.getPlayer(), key, type);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder() instanceof SpawnerMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == 11 && menu.page().equals("main")) {
            collect(player, menu.key(), menu.type());
            openMain(player, menu.key(), menu.type());
            return;
        }
        if (event.getRawSlot() == 15 && menu.page().equals("main")) {
            openUpgrades(player, menu.key(), menu.type());
            return;
        }
        if (event.getRawSlot() == 22 && menu.page().equals("upgrades")) {
            upgrade(player, menu.key(), menu.type());
            openUpgrades(player, menu.key(), menu.type());
            return;
        }
        if (event.getRawSlot() == 18 && menu.page().equals("upgrades")) {
            openMain(player, menu.key(), menu.type());
        }
    }

    private void openMain(Player player, String key, String type) {
        Inventory inventory = Bukkit.createInventory(new SpawnerMenu("main", key, type), 27, Text.color("&4Spawner &8- &f" + display(type)));
        inventory.setItem(11, icon(outputMaterial(type), "&aSpawner Inventory", List.of(
            "&7Stored: &f" + data.getInt(key + ".stored", 0) + "&7/&f" + capacity(type, level(key)),
            "&7Output: &f" + outputMaterial(type).name().toLowerCase(Locale.ROOT),
            "&eClick to collect."
        )));
        inventory.setItem(15, icon(Material.ANVIL, "&6Upgrades", List.of(
            "&7Level: &f" + level(key) + "&7/&f" + maxLevel(type),
            "&7Production interval: &f" + intervalSeconds(type, level(key)) + "s",
            "&eClick to upgrade."
        )));
        player.openInventory(inventory);
    }

    private void openUpgrades(Player player, String key, String type) {
        int level = level(key);
        boolean maxed = level >= maxLevel(type);
        Inventory inventory = Bukkit.createInventory(new SpawnerMenu("upgrades", key, type), 27, Text.color("&4Spawner Upgrades"));
        inventory.setItem(18, icon(Material.ARROW, "&aBack", List.of("&7Return to spawner.")));
        inventory.setItem(22, icon(maxed ? Material.EMERALD_BLOCK : Material.GOLD_INGOT, maxed ? "&aMax Level" : "&6Upgrade to Level " + (level + 1), List.of(
            "&7Current: &f" + level + "&7/&f" + maxLevel(type),
            "&7Cost: &a$" + String.format(Locale.US, "%.2f", upgradeCost(type, level + 1)),
            "&7Next capacity: &f" + capacity(type, Math.min(maxLevel(type), level + 1)),
            "&eClick to buy."
        )));
        player.openInventory(inventory);
    }

    private void collect(Player player, String key, String type) {
        updateProduction(key, type);
        int stored = data.getInt(key + ".stored", 0);
        if (stored <= 0) {
            Text.msg(player, "&7Spawner inventory is empty.");
            return;
        }
        ItemStack item = outputItem(type, stored);
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        data.set(key + ".stored", 0);
        data.saveSoon(this, 20L);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.2F);
    }

    private void upgrade(Player player, String key, String type) {
        int level = level(key);
        if (level >= maxLevel(type)) {
            Text.msg(player, "&aThis spawner is already maxed.");
            return;
        }
        double cost = upgradeCost(type, level + 1);
        EconomyService economy = MitchSMP.economy();
        if (economy == null || !economy.withdraw(player.getUniqueId(), cost, "spawner upgrade " + type)) {
            Text.msg(player, "&cYou need &a$" + String.format(Locale.US, "%.2f", cost) + "&c.");
            return;
        }
        data.set(key + ".level", level + 1);
        data.saveSoon(this, 20L);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.25F);
        Text.msg(player, "&aSpawner upgraded to level &f" + (level + 1) + "&a.");
    }

    private void updateProduction(String key, String type) {
        long now = System.currentTimeMillis();
        long last = data.getLong(key + ".last", now);
        int level = level(key);
        long interval = Math.max(1L, intervalSeconds(type, level)) * 1000L;
        long cycles = Math.max(0L, (now - last) / interval);
        if (cycles <= 0L) {
            return;
        }
        int stored = data.getInt(key + ".stored", 0);
        int amount = (int) Math.min(100_000L, cycles * amountPerCycle(type, level));
        data.set(key + ".stored", Math.min(capacity(type, level), stored + amount));
        data.set(key + ".last", last + cycles * interval);
        data.saveSoon(this, 40L);
    }

    private void clearSpawner(String key) {
        for (String stored : new ArrayList<>(data.keys())) {
            if (stored.startsWith(key + ".")) {
                data.set(stored, null);
            }
        }
        data.saveSoon(this, 20L);
    }

    private ItemStack spawnerItem(String type, int level) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&d" + display(type) + " Spawner"));
            meta.setLore(List.of(
                Text.color("&7Inventory-based AFK spawner."),
                Text.color("&7Level: &f" + level),
                Text.color("&7Right-click after placing.")
            ));
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.STRING, String.valueOf(level));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack outputItem(String type, int amount) {
        return new ItemStack(outputMaterial(type), Math.max(1, amount));
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String itemType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        String type = meta == null ? null : meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return type == null || type.isBlank() ? null : normalize(type);
    }

    private int itemLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1;
        }
        ItemMeta meta = item.getItemMeta();
        String raw = meta == null ? "1" : meta.getPersistentDataContainer().get(levelKey, PersistentDataType.STRING);
        return parseInt(raw, 1, 1, 100);
    }

    private int level(String key) {
        return Math.max(1, data.getInt(key + ".level", 1));
    }

    private String key(Location location) {
        return "spawner." + location.getWorld().getName() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    private List<String> spawnerTypes() {
        List<String> types = new ArrayList<>();
        for (String key : data.keys()) {
            if (key.startsWith("type.") && key.endsWith(".material")) {
                types.add(key.substring("type.".length(), key.length() - ".material".length()));
            }
        }
        types.sort(String::compareToIgnoreCase);
        return types;
    }

    private Material outputMaterial(String type) {
        return material(data.getString("type." + type + ".material", "ROTTEN_FLESH"), Material.ROTTEN_FLESH);
    }

    private int maxLevel(String type) {
        return Math.max(1, data.getInt("type." + type + ".maxLevel", 5));
    }

    private int intervalSeconds(String type, int level) {
        int base = Math.max(1, data.getInt("type." + type + ".baseIntervalSeconds", 60));
        int reduction = Math.max(0, data.getInt("type." + type + ".intervalReductionPerLevel", 6));
        return Math.max(5, base - (Math.max(1, level) - 1) * reduction);
    }

    private int amountPerCycle(String type, int level) {
        return Math.max(1, data.getInt("type." + type + ".baseAmount", 1) + (Math.max(1, level) - 1) * data.getInt("type." + type + ".amountPerLevel", 1));
    }

    private int capacity(String type, int level) {
        return Math.max(64, data.getInt("type." + type + ".baseCapacity", 256) + (Math.max(1, level) - 1) * data.getInt("type." + type + ".capacityPerLevel", 128));
    }

    private double upgradeCost(String type, int level) {
        return Math.max(0.0D, data.getDouble("type." + type + ".upgrade." + level, 250.0D * level * level));
    }

    private String display(String type) {
        String configured = data.getString("type." + type + ".display", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1).replace('_', ' ');
    }

    private void ensureDefaults() {
        Map<String, Material> defaults = new LinkedHashMap<>();
        defaults.put("zombie", Material.ROTTEN_FLESH);
        defaults.put("skeleton", Material.BONE);
        defaults.put("spider", Material.STRING);
        defaults.put("blaze", Material.BLAZE_ROD);
        defaults.put("iron", Material.IRON_INGOT);
        defaults.put("gold", Material.GOLD_INGOT);
        for (Map.Entry<String, Material> entry : defaults.entrySet()) {
            String prefix = "type." + entry.getKey() + ".";
            setDefault(prefix + "display", displayFallback(entry.getKey()));
            setDefault(prefix + "material", entry.getValue().name());
            setDefault(prefix + "maxLevel", 5);
            setDefault(prefix + "baseIntervalSeconds", 60);
            setDefault(prefix + "intervalReductionPerLevel", 6);
            setDefault(prefix + "baseAmount", 1);
            setDefault(prefix + "amountPerLevel", 1);
            setDefault(prefix + "baseCapacity", 256);
            setDefault(prefix + "capacityPerLevel", 128);
            for (int level = 2; level <= 5; level++) {
                setDefault(prefix + "upgrade." + level, 250.0D * level * level);
            }
        }
        data.save();
    }

    private void setDefault(String key, Object value) {
        if (!data.contains(key)) {
            data.set(key, value);
        }
    }

    private String displayFallback(String type) {
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1).replace('_', ' ');
    }

    private String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    private Material material(String input, Material fallback) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private int parseInt(String input, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(input)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record SpawnerMenu(String page, String key, String type) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
