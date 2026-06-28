package nl.mitchsmp.spawners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
        Bukkit.getScheduler().runTaskTimer(this, this::spawnTick, 40L, 40L);
        getLogger().info("Bloodbound proximity spawners loaded. Types: " + String.join(", ", spawnerTypes()));
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
        data.set(key + ".owner", event.getPlayer().getUniqueId().toString());
        data.set(key + ".ownerName", event.getPlayer().getName());
        data.set(key + ".last", System.currentTimeMillis());
        data.saveSoon(this, 20L);
        Text.msg(event.getPlayer(), "&aPlaced &f" + display(type) + " &7Level &f" + level + "&a. Stay nearby to activate it.");
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
        block.getLocation().getWorld().dropItemNaturally(block.getLocation(), spawnerItem(type, level(key)));
        clearSpawner(key);
        Text.msg(event.getPlayer(), "&eSpawner picked up.");
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
        openUpgrades(event.getPlayer(), key, type);
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
        if (event.getRawSlot() == 18) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 22) {
            upgrade(player, menu.key(), menu.type());
            openUpgrades(player, menu.key(), menu.type());
        }
    }

    private void openUpgrades(Player player, String key, String type) {
        int level = level(key);
        boolean maxed = level >= maxLevel(type);
        Inventory inventory = Bukkit.createInventory(new SpawnerMenu(key, type), 27, Text.color("&4Spawner &8- &f" + display(type)));
        inventory.setItem(10, icon(Material.SPAWNER, "&d" + display(type) + " Spawner", List.of(
            "&7Level: &f" + level + "&7/&f" + maxLevel(type),
            "&7Entity: &f" + entityType(type).name().toLowerCase(Locale.ROOT),
            "&7Activation radius: &f" + activationRadius(type) + " blocks",
            "&7Owner nearby required: &aYes"
        )));
        inventory.setItem(13, icon(Material.EXPERIENCE_BOTTLE, "&aSpawn Settings", List.of(
            "&7Delay: &f" + spawnDelaySeconds(type, level) + "s",
            "&7Count: &f" + spawnCount(type, level),
            "&7Max nearby: &f" + maxNearby(type, level),
            "&8Configurable in spawners.properties."
        )));
        inventory.setItem(18, icon(Material.ARROW, "&aClose", List.of("&7Close this menu.")));
        inventory.setItem(22, icon(maxed ? Material.EMERALD_BLOCK : Material.GOLD_INGOT, maxed ? "&aMax Level" : "&6Upgrade to Level " + (level + 1), List.of(
            "&7Current: &f" + level + "&7/&f" + maxLevel(type),
            "&7Cost: &a$" + String.format(Locale.US, "%.2f", upgradeCost(type, level + 1)),
            "&7Next delay: &f" + spawnDelaySeconds(type, Math.min(maxLevel(type), level + 1)) + "s",
            "&eClick to buy."
        )));
        player.openInventory(inventory);
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

    private void spawnTick() {
        long now = System.currentTimeMillis();
        for (String storedKey : new ArrayList<>(data.keys())) {
            if (!storedKey.startsWith("spawner.") || !storedKey.endsWith(".type")) {
                continue;
            }
            String key = storedKey.substring(0, storedKey.length() - ".type".length());
            String type = data.getString(storedKey, "");
            if (type.isBlank()) {
                continue;
            }
            Location location = locationFromKey(key);
            if (location == null || location.getWorld() == null || location.getBlock().getType() != Material.SPAWNER) {
                continue;
            }
            int level = level(key);
            long delay = Math.max(1L, spawnDelaySeconds(type, level)) * 1000L;
            long last = data.getLong(key + ".last", now);
            if (now - last < delay) {
                continue;
            }
            Player owner = ownerOnline(key);
            if (owner == null || !owner.getWorld().equals(location.getWorld()) || owner.getLocation().distanceSquared(location) > activationRadius(type) * activationRadius(type)) {
                continue;
            }
            if (nearbyCount(location, entityType(type), maxNearbyRadius(type)) >= maxNearby(type, level)) {
                continue;
            }
            Location spawnLocation = new Location(location.getWorld(), location.getBlockX() + 0.5D, location.getBlockY() + 1.0D, location.getBlockZ() + 0.5D);
            for (int i = 0; i < spawnCount(type, level); i++) {
                location.getWorld().spawnEntity(spawnLocation, entityType(type));
            }
            data.set(key + ".last", now);
            data.saveSoon(this, 60L);
        }
    }

    private Player ownerOnline(String key) {
        String raw = data.getString(key + ".owner", "");
        try {
            return raw.isBlank() ? null : Bukkit.getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int nearbyCount(Location location, EntityType type, double radius) {
        int total = 0;
        double radiusSquared = radius * radius;
        for (Entity entity : location.getWorld().getEntities()) {
            if (entity.getType() == type && entity.getLocation().distanceSquared(location) <= radiusSquared) {
                total++;
            }
        }
        return total;
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
                Text.color("&7Mob: &f" + display(type)),
                Text.color("&7Level: &f" + level),
                Text.color("&7Output/hour: &f" + outputPerHour(type, level))
            ));
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.STRING, String.valueOf(level));
            item.setItemMeta(meta);
        }
        return item;
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

    private Location locationFromKey(String key) {
        String[] parts = key.split("\\.");
        if (parts.length != 5) {
            return null;
        }
        World world = Bukkit.getWorld(parts[1]);
        if (world == null) {
            return null;
        }
        return new Location(world, parseInt(parts[2], 0, -30_000_000, 30_000_000), parseInt(parts[3], 64, -2048, 2048), parseInt(parts[4], 0, -30_000_000, 30_000_000));
    }

    private List<String> spawnerTypes() {
        Set<String> types = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (EntityType type : EntityType.values()) {
            if (isMobSpawnerType(type.name())) {
                types.add(normalize(type.name()));
            }
        }
        for (String key : data.keys()) {
            if (key.startsWith("type.") && key.endsWith(".entity")) {
                types.add(key.substring("type.".length(), key.length() - ".entity".length()));
            }
        }
        return new ArrayList<>(types);
    }

    private EntityType entityType(String type) {
        try {
            return EntityType.valueOf(data.getString("type." + type + ".entity", type).toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return EntityType.ZOMBIE;
        }
    }

    private int maxLevel(String type) {
        return Math.max(1, data.getInt("type." + type + ".maxLevel", data.getInt("settings.defaultMaxLevel", 5)));
    }

    private int activationRadius(String type) {
        return Math.max(1, data.getInt("type." + type + ".activationRadius", data.getInt("settings.defaultActivationRadius", 24)));
    }

    private int spawnDelaySeconds(String type, int level) {
        int base = Math.max(1, data.getInt("type." + type + ".baseSpawnDelaySeconds", data.getInt("settings.defaultBaseSpawnDelaySeconds", 60)));
        int reduction = Math.max(0, data.getInt("type." + type + ".delayReductionPerLevel", data.getInt("settings.defaultDelayReductionPerLevel", 6)));
        return Math.max(5, base - (Math.max(1, level) - 1) * reduction);
    }

    private int spawnCount(String type, int level) {
        return Math.max(1, data.getInt("type." + type + ".baseSpawnCount", data.getInt("settings.defaultBaseSpawnCount", 1))
            + (Math.max(1, level) - 1) * data.getInt("type." + type + ".spawnCountPerLevel", data.getInt("settings.defaultSpawnCountPerLevel", 0)));
    }

    private int maxNearby(String type, int level) {
        return Math.max(1, data.getInt("type." + type + ".maxNearby", data.getInt("settings.defaultMaxNearby", 6))
            + (Math.max(1, level) - 1) * data.getInt("type." + type + ".maxNearbyPerLevel", data.getInt("settings.defaultMaxNearbyPerLevel", 1)));
    }

    private int maxNearbyRadius(String type) {
        return Math.max(4, data.getInt("type." + type + ".maxNearbyRadius", data.getInt("settings.defaultMaxNearbyRadius", 8)));
    }

    private double upgradeCost(String type, int level) {
        return Math.max(0.0D, data.getDouble("type." + type + ".upgrade." + level,
            data.getDouble("settings.defaultUpgrade." + level, data.getDouble("settings.defaultUpgradeBase", 250.0D) * level * level)));
    }

    private String display(String type) {
        String configured = data.getString("type." + type + ".display", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return displayFallback(type);
    }

    private void ensureDefaults() {
        setDefault("settings.defaultActivationRadius", 24);
        setDefault("settings.defaultMaxLevel", 5);
        setDefault("settings.defaultBaseSpawnDelaySeconds", 60);
        setDefault("settings.defaultDelayReductionPerLevel", 6);
        setDefault("settings.defaultBaseSpawnCount", 1);
        setDefault("settings.defaultSpawnCountPerLevel", 0);
        setDefault("settings.defaultMaxNearby", 6);
        setDefault("settings.defaultMaxNearbyPerLevel", 1);
        setDefault("settings.defaultMaxNearbyRadius", 8);
        setDefault("settings.defaultUpgradeBase", 250.0D);
        for (int level = 2; level <= 5; level++) {
            setDefault("settings.defaultUpgrade." + level, 250.0D * level * level);
        }
        for (String type : List.of("zombie", "skeleton", "spider", "blaze", "creeper", "enderman", "witch", "slime", "magma_cube")) {
            String prefix = "type." + type + ".";
            setDefault(prefix + "display", displayFallback(type));
            setDefault(prefix + "entity", type.toUpperCase(Locale.ROOT));
            setDefault(prefix + "maxLevel", 5);
            setDefault(prefix + "activationRadius", 24);
            setDefault(prefix + "baseSpawnDelaySeconds", 60);
            setDefault(prefix + "delayReductionPerLevel", 6);
            setDefault(prefix + "baseSpawnCount", 1);
            setDefault(prefix + "spawnCountPerLevel", 0);
            setDefault(prefix + "maxNearby", 6);
            setDefault(prefix + "maxNearbyPerLevel", 1);
            setDefault(prefix + "maxNearbyRadius", 8);
            for (int level = 2; level <= 5; level++) {
                setDefault(prefix + "upgrade." + level, 250.0D * level * level);
            }
        }
        data.save();
    }

    private String outputPerHour(String type, int level) {
        double output = spawnCount(type, level) * 3600.0D / Math.max(1, spawnDelaySeconds(type, level));
        return Math.abs(output - Math.rint(output)) < 0.01D
            ? String.format(Locale.US, "%.0f mobs", output)
            : String.format(Locale.US, "%.1f mobs", output);
    }

    private boolean isMobSpawnerType(String name) {
        Set<String> blocked = new HashSet<>(List.of(
            "PLAYER", "ITEM", "EXPERIENCE_ORB", "AREA_EFFECT_CLOUD", "ARROW", "SPECTRAL_ARROW", "TRIDENT",
            "FIREWORK_ROCKET", "DROPPED_ITEM", "FALLING_BLOCK", "BLOCK_DISPLAY", "ITEM_DISPLAY", "TEXT_DISPLAY",
            "INTERACTION", "PAINTING", "ITEM_FRAME", "GLOW_ITEM_FRAME", "ARMOR_STAND", "BOAT", "CHEST_BOAT",
            "MINECART", "CHEST_MINECART", "COMMAND_BLOCK_MINECART", "FURNACE_MINECART", "HOPPER_MINECART",
            "SPAWNER_MINECART", "TNT_MINECART", "TNT", "LIGHTNING_BOLT", "UNKNOWN", "FISHING_BOBBER",
            "EGG", "SNOWBALL", "SMALL_FIREBALL", "FIREBALL", "DRAGON_FIREBALL", "WITHER_SKULL", "WIND_CHARGE",
            "BREEZE_WIND_CHARGE", "OMINOUS_ITEM_SPAWNER", "LEASH_KNOT", "END_CRYSTAL", "MARKER"
        ));
        return !blocked.contains(name);
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

    private int parseInt(String input, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(input)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record SpawnerMenu(String key, String type) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
