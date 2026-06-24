package nl.mitchsmp.hub;

import java.lang.reflect.Constructor;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.MitchRank;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class HubPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String HUB_WORLD = "bloodbound_hub";
    private static final String NAVIGATOR_NAME = "&4Bloodbound Navigator";
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("hub.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("hub") != null) {
            getCommand("hub").setExecutor(this);
            getCommand("hub").setTabCompleter(this);
        }
        if (getCommand("protect") != null) {
            getCommand("protect").setExecutor(this);
            getCommand("protect").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World world = hubWorld();
            if (world != null && data.getInt("layoutVersion", 0) < 2) {
                buildHub(world);
            }
        }, 40L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("protect")) {
            return protect(player, args);
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.hub.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("build")) {
            if (!MitchSMP.permissions().has(player, "mitchsmp.hub.admin")) {
                Text.msg(player, "&cYou do not have permission.");
                return true;
            }
            buildHub(hubWorld());
            Text.msg(player, "&aHub rebuilt.");
            return true;
        }
        if (MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&cLeave admin mode before entering the player Hub. Use staff teleports for moderation.");
            return true;
        }
        World world = hubWorld();
        if (world == null) {
            Text.msg(player, "&cHub world is not available.");
            return true;
        }
        player.teleport(spawn(world));
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(80);
        player.setGameMode(GameMode.ADVENTURE);
        enterHub(player);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.1F);
        Text.msg(player, "&4Bloodbound &8> &7Right-click the compass to open the navigator.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (command.getName().equalsIgnoreCase("protect")) {
                return Tab.complete(args[0], "build");
            }
            if (MitchSMP.permissions().has(sender, "mitchsmp.hub.admin")) {
                return Tab.complete(args[0], "menu", "build");
            }
            return Tab.complete(args[0], "menu");
        }
        if (command.getName().equalsIgnoreCase("protect")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("build")) {
                return Tab.onlinePlayers(args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("build")) {
                return Tab.complete(args[2], "true", "false");
            }
        }
        return List.of();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isHub(event.getBlock().getLocation()) && !canBuildInHub(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isHub(event.getBlock().getLocation()) && !canBuildInHub(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (isHub(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isHub(event.getPlayer().getLocation())) {
            giveNavigator(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isHub(event.getPlayer().getLocation()) || !isNavigator(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            openNavigator(event.getPlayer());
        }
    }

    @EventHandler
    public void onHubCommand(PlayerCommandPreprocessEvent event) {
        if (!isHub(event.getPlayer().getLocation())) {
            return;
        }
        String command = event.getMessage().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.matches("rtp|wild|spawn|bw|bedwars|tntrun|tr|spleef|sf|skyblock|island|sb")) {
            restoreHubSession(event.getPlayer());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HubMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> run(player, "rtp");
            case 12 -> run(player, "bedwars join");
            case 14 -> run(player, "tntrun join");
            case 16 -> run(player, "spleef join");
            case 22 -> run(player, "skyblock home");
            default -> {
            }
        }
    }

    private void openNavigator(Player player) {
        HubMenu holder = new HubMenu();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&4Bloodbound Navigator"));
        holder.inventory(inventory);
        inventory.setItem(10, icon(Material.COMPASS, "&6Wilderness", "&7Enter the PvP lifesteal world.", "&8Runs /rtp."));
        inventory.setItem(12, icon(Material.RED_BED, "&cBedWars", "&7Fast PvP arena mode.", "&8Runs /bedwars join."));
        inventory.setItem(14, icon(Material.TNT, "&4TNT Run", "&7Last runner wins.", "&8Runs /tntrun join."));
        inventory.setItem(16, icon(Material.SNOWBALL, "&bSpleef", "&7Shoot the floor away.", "&8Runs /spleef join."));
        inventory.setItem(22, icon(Material.GRASS_BLOCK, "&aSkyblock", "&7Your isolated void island.", "&8Runs /skyblock home."));
        player.openInventory(inventory);
    }

    private void enterHub(Player player) {
        String base = "session." + player.getUniqueId() + ".";
        if (!data.contains(base + "inventory")) {
            data.set(base + "inventory", encodeInventory(player.getInventory().getContents()));
            data.set(base + "gamemode", player.getGameMode().name());
            data.save();
        }
        player.getInventory().clear();
        giveNavigator(player);
    }

    private void restoreHubSession(Player player) {
        String base = "session." + player.getUniqueId() + ".";
        String encoded = data.getString(base + "inventory", "");
        if (!encoded.isBlank()) {
            player.getInventory().setContents(decodeInventory(encoded, player.getInventory().getSize()));
        }
        try {
            player.setGameMode(GameMode.valueOf(data.getString(base + "gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        data.set(base + "inventory", null);
        data.set(base + "gamemode", null);
        data.save();
    }

    private void giveNavigator(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(4, icon(Material.COMPASS, NAVIGATOR_NAME, "&7Right-click to choose a Bloodbound destination."));
    }

    private boolean isNavigator(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && Text.stripColorCodes(meta.getDisplayName()).equalsIgnoreCase("Bloodbound Navigator");
    }

    private boolean protect(Player player, String[] args) {
        if (MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
            Text.msg(player, "&cOnly the Owner rank can change hub build protection.");
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("build")) {
            Text.msg(player, "&cUsage: /protect build <player> <true|false>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(player, "&cPlayer is not online.");
            return true;
        }
        boolean allowed = Boolean.parseBoolean(args[2]);
        data.set("build." + target.getUniqueId(), allowed ? "true" : null);
        data.save();
        Text.msg(player, "&aHub build access for &f" + target.getName() + " &ais now &f" + allowed + "&a.");
        Text.msg(target, allowed ? "&aYou can now build in the Bloodbound hub." : "&cYour Bloodbound hub build access was removed.");
        return true;
    }

    private boolean canBuildInHub(Player player) {
        return player != null
            && (MitchSMP.ranks().getRank(player.getUniqueId()) == MitchRank.OWNER
            || "true".equalsIgnoreCase(data.getString("build." + player.getUniqueId(), "false")));
    }

    private String encodeInventory(ItemStack[] contents) {
        List<String> parts = new java.util.ArrayList<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                parts.add("-");
            } else {
                parts.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
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
                getLogger().warning("Could not load saved hub inventory slot " + i + ": " + exception.getMessage());
            }
        }
        return contents;
    }

    private void run(Player player, String command) {
        player.closeInventory();
        restoreHubSession(player);
        Bukkit.dispatchCommand(player, command);
    }

    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(java.util.Arrays.stream(lore).map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void buildHub(World world) {
        if (world == null) {
            return;
        }
        clear(world, -64, 88, -64, 64, 136, 64);
        for (int x = -42; x <= 42; x++) {
            for (int z = -42; z <= 42; z++) {
                int dist = x * x + z * z;
                if (dist <= 42 * 42) {
                    Material floor = dist > 39 * 39 ? Material.BLACKSTONE : ((x + z) % 9 == 0 ? Material.QUARTZ_BLOCK : Material.END_STONE);
                    materialAt(world, x, 100, z, floor);
                }
            }
        }
        for (int y = 101; y <= 116; y++) {
            ring(world, 0, y, 0, 43, y % 4 == 0 ? Material.CRYING_OBSIDIAN : Material.BLACKSTONE);
        }
        for (int x = -42; x <= 42; x++) {
            for (int z = -42; z <= 42; z++) {
                int dist = x * x + z * z;
                if (dist <= 42 * 42 && dist >= 8 * 8) {
                    materialAt(world, x, 117, z, dist > 36 * 36 ? Material.BLACKSTONE : Material.GLASS);
                }
            }
        }
        ring(world, 0, 101, 0, 30, Material.RED_WOOL);
        ring(world, 0, 102, 0, 31, Material.GOLD_BLOCK);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8.0D;
            int x = (int) Math.round(Math.cos(angle) * 34);
            int z = (int) Math.round(Math.sin(angle) * 34);
            pillar(world, x, 101, z, i % 2 == 0 ? Material.GOLD_BLOCK : Material.CRYING_OBSIDIAN);
        }
        portalPad(world, 0, 101, -18, Material.EMERALD_BLOCK);
        portalPad(world, -18, 101, 0, Material.RED_WOOL);
        portalPad(world, 18, 101, 0, Material.SNOW_BLOCK);
        portalPad(world, 0, 101, 18, Material.GRASS_BLOCK);
        building(world, -34, 101, -10, -24, 108, 10, Material.QUARTZ_BLOCK, Material.RED_WOOL);
        building(world, 24, 101, -10, 34, 108, 10, Material.BLACKSTONE, Material.GOLD_BLOCK);
        building(world, -10, 101, 24, 10, 108, 34, Material.END_STONE, Material.CRYING_OBSIDIAN);
        for (int[] plant : List.of(new int[] { -30, -30 }, new int[] { 30, -30 }, new int[] { -30, 30 }, new int[] { 30, 30 }, new int[] { -12, -30 }, new int[] { 12, 30 })) {
            plant(world, plant[0], 101, plant[1]);
        }
        drawText(world, "BLOODBOUND", -47, 122, -45, Material.RED_WOOL, Material.BLACKSTONE);
        drawText(world, "SMP", -10, 113, -45, Material.GOLD_BLOCK, Material.BLACKSTONE);
        data.set("layoutVersion", 2);
        data.save();
    }

    private void building(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Material wall, Material trim) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean edge = x == minX || x == maxX || z == minZ || z == maxZ || y == minY || y == maxY;
                    if (!edge) {
                        continue;
                    }
                    boolean trimBlock = y == minY || y == maxY || x == minX || x == maxX;
                    materialAt(world, x, y, z, trimBlock ? trim : wall);
                }
            }
        }
        for (int y = minY + 1; y <= minY + 3; y++) {
            materialAt(world, (minX + maxX) / 2, y, minZ, Material.AIR);
        }
    }

    private void plant(World world, int x, int y, int z) {
        for (int dy = 0; dy < 6; dy++) {
            materialAt(world, x, y + dy, z, Material.OAK_LOG);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 4; dy <= 8; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 6) <= 5) {
                        materialAt(world, x + dx, y + dy, z + dz, Material.GREEN_WOOL);
                    }
                }
            }
        }
    }

    private void drawText(World world, String text, int startX, int startY, int z, Material foreground, Material shadow) {
        int cursor = startX;
        for (char character : text.toCharArray()) {
            String[] glyph = glyph(character);
            for (int row = 0; row < glyph.length; row++) {
                for (int col = 0; col < glyph[row].length(); col++) {
                    if (glyph[row].charAt(col) != '1') {
                        continue;
                    }
                    int x = cursor + col;
                    int y = startY - row;
                    materialAt(world, x + 1, y - 1, z + 1, shadow);
                    materialAt(world, x, y, z, foreground);
                    materialAt(world, x, y, z + 1, foreground);
                }
            }
            cursor += glyph[0].length() + 1;
        }
    }

    private String[] glyph(char character) {
        return switch (Character.toUpperCase(character)) {
            case 'B' -> new String[] { "11110", "10001", "10001", "11110", "10001", "10001", "11110" };
            case 'D' -> new String[] { "11110", "10001", "10001", "10001", "10001", "10001", "11110" };
            case 'L' -> new String[] { "10000", "10000", "10000", "10000", "10000", "10000", "11111" };
            case 'M' -> new String[] { "10001", "11011", "10101", "10101", "10001", "10001", "10001" };
            case 'N' -> new String[] { "10001", "11001", "10101", "10011", "10001", "10001", "10001" };
            case 'O' -> new String[] { "01110", "10001", "10001", "10001", "10001", "10001", "01110" };
            case 'P' -> new String[] { "11110", "10001", "10001", "11110", "10000", "10000", "10000" };
            case 'S' -> new String[] { "01111", "10000", "10000", "01110", "00001", "00001", "11110" };
            case 'U' -> new String[] { "10001", "10001", "10001", "10001", "10001", "10001", "01110" };
            default -> new String[] { "000", "000", "000", "000", "000", "000", "000" };
        };
    }

    private void portalPad(World world, int cx, int y, int cz, Material icon) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                materialAt(world, x, y, z, Material.BLACKSTONE);
            }
        }
        materialAt(world, cx, y + 1, cz, icon);
    }

    private void pillar(World world, int x, int y, int z, Material material) {
        for (int dy = 0; dy < 8; dy++) {
            materialAt(world, x, y + dy, z, material);
        }
        materialAt(world, x, y + 8, z, Material.SEA_LANTERN);
    }

    private void ring(World world, int cx, int y, int cz, int radius, Material material) {
        int outer = radius * radius;
        int inner = (radius - 1) * (radius - 1);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                int dx = x - cx;
                int dz = z - cz;
                int dist = dx * dx + dz * dz;
                if (dist <= outer && dist >= inner) {
                    materialAt(world, x, y, z, material);
                }
            }
        }
    }

    private void clear(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    materialAt(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    private void materialAt(World world, int x, int y, int z, Material material) {
        new Location(world, x, y, z).getBlock().setType(material);
    }

    private boolean isHub(Location location) {
        return location != null && location.getWorld() != null && HUB_WORLD.equalsIgnoreCase(location.getWorld().getName());
    }

    private Location spawn(World world) {
        return new Location(world, 0.5D, 102.0D, 0.5D, 180.0F, 0.0F);
    }

    private World hubWorld() {
        return loadWorld(HUB_WORLD);
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
            getLogger().warning("Could not create hub world " + name + ": " + exception.getMessage());
        }
        return Bukkit.getWorld(name);
    }

    private static final class HubMenu implements InventoryHolder {
        private Inventory inventory;

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
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
