package nl.mitchsmp.skyblock;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkyblockPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String WORLD_NAME = "bloodbound_skyblock";
    private static final int ISLAND_RADIUS = 48;
    private static final int GRID = 180;
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("skyblock.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("skyblock") != null) {
            getCommand("skyblock").setExecutor(this);
            getCommand("skyblock").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskLater(this, this::skyWorld, 40L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (inSkyblock(player)) {
                saveSkyState(player);
            }
        }
        data.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.skyblock.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        String sub = args.length == 0 ? "home" : args[0].toLowerCase(Locale.ROOT);
        if (MitchSMP.permissions().isAdminMode(player) && !sub.equals("leave") && !sub.equals("hub")) {
            Text.msg(player, "&cLeave admin mode before entering Skyblock. Staff inventory and test data cannot enter player islands.");
            return true;
        }
        switch (sub) {
            case "create" -> create(player);
            case "home", "join" -> home(player);
            case "leave", "hub" -> leave(player, true);
            case "reset" -> reset(player, args);
            case "info" -> info(player);
            default -> help(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.skyblock.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return Tab.complete(args[0], "create", "home", "leave", "reset", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Tab.complete(args[1], "confirm");
        }
        return List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (Boolean.parseBoolean(data.getString(base(player) + "inside", "false"))) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                loadSkyInventory(player);
                Location home = islandHome(player.getUniqueId());
                if (home != null) {
                    player.teleport(home);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setFallDistance(0.0F);
                    player.setNoDamageTicks(100);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (inSkyblock(event.getPlayer())) {
            saveSkyState(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.parseBoolean(data.getString(base(player) + "inside", "false"))) {
            return;
        }
        Location home = islandHome(player.getUniqueId());
        if (home != null) {
            event.setRespawnLocation(home);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.teleport(home);
                player.setFallDistance(0.0F);
                player.setNoDamageTicks(100);
            }, 2L);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!inSkyblock(player)) {
            return;
        }
        String lower = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (lower.equals("/hub") || lower.equals("/serverhub") || lower.equals("/navigator")) {
            event.setCancelled(true);
            leave(player, true);
            return;
        }
        if (lower.startsWith("/skyblock") || lower.startsWith("/island") || lower.startsWith("/sb")) {
            return;
        }
        event.setCancelled(true);
        Text.msg(player, "&cCommands are disabled in Skyblock. Use &f/skyblock leave &cor &f/hub&c.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isSkyWorld(event.getBlock().getLocation()) && !canEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cYou can only edit your own Skyblock island.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isSkyWorld(event.getBlock().getLocation()) && !canEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cYou can only edit your own Skyblock island.");
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (isSkyWorld(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private void create(Player player) {
        if (islandHome(player.getUniqueId()) != null) {
            Text.msg(player, "&cYou already have a Skyblock island. Use &f/skyblock home&c.");
            return;
        }
        Location center = nextIslandCenter();
        data.set("island." + player.getUniqueId() + ".center", encode(center));
        data.set("island." + player.getUniqueId() + ".owner", player.getName());
        data.save();
        buildIsland(center);
        Text.msg(player, "&aSkyblock island created.");
        enterSkyblock(player, offset(center, 0.5D, 2.0D, 0.5D));
    }

    private void home(Player player) {
        Location home = islandHome(player.getUniqueId());
        if (home == null) {
            create(player);
            return;
        }
        enterSkyblock(player, home);
    }

    private void leave(Player player, boolean toHub) {
        if (!inSkyblock(player)) {
            Text.msg(player, "&cYou are not in Skyblock.");
            return;
        }
        saveSkyState(player);
        restoreSmpState(player);
        data.set(base(player) + "inside", false);
        data.save();
        Location destination = toHub ? hubSpawn() : decode(data.getString(base(player) + "return", ""));
        if (destination != null) {
            player.teleport(destination);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(100);
        Text.msg(player, toHub ? "&aReturned to the Bloodbound hub." : "&aReturned to the SMP.");
    }

    private void reset(Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            Text.msg(player, "&cConfirm with: &f/skyblock reset confirm");
            return;
        }
        Location center = islandCenter(player.getUniqueId());
        if (center == null) {
            Text.msg(player, "&cYou do not have a Skyblock island.");
            return;
        }
        clearIsland(center);
        buildIsland(center);
        data.set(base(player) + "skyInventory", "");
        data.save();
        if (inSkyblock(player)) {
            player.getInventory().clear();
            player.teleport(offset(center, 0.5D, 2.0D, 0.5D));
        }
        Text.msg(player, "&aSkyblock island reset.");
    }

    private void info(Player player) {
        Location center = islandCenter(player.getUniqueId());
        if (center == null) {
            Text.msg(player, "&7No island yet. Use &f/skyblock create&7.");
            return;
        }
        Text.msg(player, "&6Skyblock island:");
        Text.msg(player, "&7World: &f" + WORLD_NAME);
        Text.msg(player, "&7Center: &f" + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ());
        Text.msg(player, "&7Edit radius: &f" + ISLAND_RADIUS + " blocks");
    }

    private void help(Player player) {
        Text.msg(player, "&7/skyblock create, /skyblock home, /skyblock leave, /skyblock reset confirm, /skyblock info");
    }

    private void enterSkyblock(Player player, Location destination) {
        if (!inSkyblock(player)) {
            data.set(base(player) + "smpInventory", encodeInventory(player.getInventory().getContents()));
            data.set(base(player) + "return", encode(player.getLocation()));
            player.getInventory().clear();
            loadSkyInventory(player);
        }
        data.set(base(player) + "inside", true);
        data.save();
        player.teleport(destination);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(100);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
        Text.msg(player, "&aYou entered your isolated Skyblock island.");
    }

    private void saveSkyState(Player player) {
        data.set(base(player) + "skyInventory", encodeInventory(player.getInventory().getContents()));
        data.save();
    }

    private void loadSkyInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(decodeInventory(data.getString(base(player) + "skyInventory", ""), player.getInventory().getSize()));
    }

    private void restoreSmpState(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(decodeInventory(data.getString(base(player) + "smpInventory", ""), player.getInventory().getSize()));
        data.set(base(player) + "smpInventory", null);
    }

    private boolean canEdit(Player player, Location location) {
        if (player == null || location == null || !isSkyWorld(location)) {
            return false;
        }
        Location center = islandCenter(player.getUniqueId());
        if (center == null || center.getWorld() == null) {
            return false;
        }
        if (!center.getWorld().getName().equals(location.getWorld().getName())) {
            return false;
        }
        return Math.abs(location.getBlockX() - center.getBlockX()) <= ISLAND_RADIUS
            && Math.abs(location.getBlockZ() - center.getBlockZ()) <= ISLAND_RADIUS;
    }

    private Location nextIslandCenter() {
        int index = data.getInt("nextIsland", 0);
        data.set("nextIsland", index + 1);
        int ring = (int) Math.floor(Math.sqrt(index));
        int x = (index % 16) * GRID;
        int z = ring * GRID;
        return new Location(skyWorld(), x, 100, z);
    }

    private void buildIsland(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = cx - 3; x <= cx + 1; x++) {
            for (int z = cz - 2; z <= cz + 1; z++) {
                set(world, x, cy, z, (x + z) % 3 == 0 ? Material.GRASS_BLOCK : Material.DIRT);
            }
        }
        for (int x = cx - 3; x <= cx + 3; x++) {
            set(world, x, cy, cz - 3, Material.DIRT);
        }
        for (int z = cz - 3; z <= cz + 3; z++) {
            set(world, cx - 3, cy, z, Material.DIRT);
        }
        Block chestBlock = new Location(world, cx, cy + 1, cz - 1).getBlock();
        chestBlock.setType(Material.CHEST);
        fillStarterChest(chestBlock);
        growStarterTree(world, cx + 2, cy + 1, cz + 1);
    }

    private void growStarterTree(World world, int x, int y, int z) {
        set(world, x, y, z, Material.OAK_LOG);
        set(world, x, y + 1, z, Material.OAK_LOG);
        set(world, x, y + 2, z, Material.OAK_LOG);
        set(world, x, y + 3, z, Material.OAK_LOG);
        Material leaves = materialNamed("OAK_LEAVES");
        if (leaves == null) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    set(world, x + dx, y + 3, z + dz, leaves);
                    set(world, x + dx, y + 4, z + dz, leaves);
                }
            }
        }
        set(world, x, y + 5, z, leaves);
    }

    private void fillStarterChest(Block chestBlock) {
        if (!(chestBlock.getState() instanceof Chest chest)) {
            return;
        }
        Inventory inventory = chest.getInventory();
        addStarterItem(inventory, 10, "WATER_BUCKET", 1);
        addStarterItem(inventory, 11, "LAVA_BUCKET", 1);
        addStarterItem(inventory, 12, "WHEAT_SEEDS", 8);
        addStarterItem(inventory, 13, "CARROT", 4);
        addStarterItem(inventory, 14, "MELON_SLICE", 2);
        addStarterItem(inventory, 15, "PUMPKIN_SEEDS", 2);
        addStarterItem(inventory, 16, "BONE_MEAL", 8);
        addStarterItem(inventory, 22, "OAK_SAPLING", 1);
        inventory.setItem(21, new ItemStack(Material.DIRT, 16));
    }

    private void addStarterItem(Inventory inventory, int slot, String materialName, int amount) {
        Material material = materialNamed(materialName);
        if (material != null) {
            inventory.setItem(slot, new ItemStack(material, amount));
        }
    }

    private void clearIsland(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = cx - ISLAND_RADIUS; x <= cx + ISLAND_RADIUS; x++) {
            for (int y = cy - 16; y <= cy + 48; y++) {
                for (int z = cz - ISLAND_RADIUS; z <= cz + ISLAND_RADIUS; z++) {
                    set(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    private void set(World world, int x, int y, int z, Material material) {
        if (material != null) {
            new Location(world, x, y, z).getBlock().setType(material);
        }
    }

    private Material materialNamed(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean inSkyblock(Player player) {
        return player != null && isSkyWorld(player.getLocation());
    }

    private boolean isSkyWorld(Location location) {
        return location != null && location.getWorld() != null && WORLD_NAME.equalsIgnoreCase(location.getWorld().getName());
    }

    private Location islandHome(UUID id) {
        Location center = islandCenter(id);
        return center == null ? null : offset(center, 0.5D, 2.0D, 0.5D);
    }

    private Location offset(Location location, double x, double y, double z) {
        return new Location(location.getWorld(), location.getX() + x, location.getY() + y, location.getZ() + z, location.getYaw(), location.getPitch());
    }

    private Location islandCenter(UUID id) {
        return decode(data.getString("island." + id + ".center", ""));
    }

    private Location hubSpawn() {
        World world = loadWorld("bloodbound_hub");
        return world == null ? null : new Location(world, 0.5D, 102.0D, 0.5D, 180.0F, 0.0F);
    }

    private String base(Player player) {
        return "player." + player.getUniqueId() + ".";
    }

    private String encode(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location decode(String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split(";");
        if (parts.length != 6) {
            return null;
        }
        World world = loadWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String encodeInventory(ItemStack[] contents) {
        List<String> parts = new ArrayList<>();
        for (ItemStack item : contents) {
            parts.add(item == null || item.getType() == Material.AIR || item.getAmount() <= 0 ? "-" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        return String.join(";", parts);
    }

    private ItemStack[] decodeInventory(String encoded, int size) {
        ItemStack[] contents = new ItemStack[size];
        if (encoded == null || encoded.isBlank()) {
            return contents;
        }
        String[] parts = encoded.split(";", -1);
        for (int i = 0; i < Math.min(size, parts.length); i++) {
            if (parts[i] == null || parts[i].isBlank() || parts[i].equals("-")) {
                continue;
            }
            try {
                contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[i]));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Could not load Skyblock inventory slot " + i + ": " + exception.getMessage());
            }
        }
        return contents;
    }

    private World skyWorld() {
        World world = loadWorld(WORLD_NAME);
        applySkyblockBorder(world);
        return world;
    }

    private World loadWorld(String name) {
        if (Bukkit.getWorld(name) != null) {
            return Bukkit.getWorld(name);
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(name);
            Class<?> generatorClass = Class.forName("org.bukkit.generator.ChunkGenerator");
            creatorClass.getMethod("generator", generatorClass).invoke(creator, new VoidChunkGenerator());
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, false);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create Skyblock world " + name + ": " + exception.getMessage());
        }
        return Bukkit.getWorld(name);
    }

    private void applySkyblockBorder(World world) {
        if (world == null) {
            return;
        }
        try {
            Object border = world.getClass().getMethod("getWorldBorder").invoke(world);
            border.getClass().getMethod("setCenter", double.class, double.class).invoke(border, 0.0D, 0.0D);
            border.getClass().getMethod("setSize", double.class).invoke(border, 1500.0D);
            try {
                border.getClass().getMethod("setWarningDistance", int.class).invoke(border, 24);
                border.getClass().getMethod("setDamageAmount", double.class).invoke(border, 0.2D);
            } catch (ReflectiveOperationException ignored) {
            }
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not apply Skyblock worldborder: " + exception.getMessage());
        }
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }
    }
}
