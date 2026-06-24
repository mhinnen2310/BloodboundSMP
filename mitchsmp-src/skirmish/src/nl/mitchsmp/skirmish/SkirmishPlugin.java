package nl.mitchsmp.skirmish;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.InventorySnapshotService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.SkillService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkirmishPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String WORLD = "skirmish_bloodcourt";
    private static final int MAX_PLAYERS = 8;
    private final Set<UUID> queued = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private PropertiesFile data;
    private boolean running;
    private boolean finishing;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("skirmish.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("skirmish") != null) {
            getCommand("skirmish").setExecutor(this);
            getCommand("skirmish").setTabCompleter(this);
        }
        ensureArena();
        Bukkit.getScheduler().runTaskTimer(this, this::autoStart, 100L, 100L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (queued.contains(player.getUniqueId())) {
                leave(player, true);
            }
        }
        data.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "join" -> join(sender);
            case "leave" -> leaveCommand(sender);
            case "start" -> startCommand(sender);
            case "stats" -> stats(sender);
            default -> help(sender);
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Tab.complete(args[0], "join", "leave", "start", "stats") : List.of();
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!queued.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        String command = event.getMessage().toLowerCase(Locale.ROOT);
        if (!command.equals("/skirmish leave") && !command.equals("/skirm leave") && !command.equals("/skirmish start") && !command.equals("/skirm start")) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cCommands are disabled in Skirmish. Use &f/skirmish leave&c.");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!queued.contains(player.getUniqueId())) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        alive.remove(player.getUniqueId());
        data.set("stats." + player.getUniqueId() + ".deaths", data.getInt("stats." + player.getUniqueId() + ".deaths", 0) + 1);
        Player killer = player.getKiller();
        if (killer != null && queued.contains(killer.getUniqueId())) {
            data.set("stats." + killer.getUniqueId() + ".kills", data.getInt("stats." + killer.getUniqueId() + ".kills", 0) + 1);
        }
        data.save();
        Bukkit.getScheduler().runTask(this, this::checkWinner);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (queued.contains(event.getPlayer().getUniqueId())) {
            event.setRespawnLocation(spectator());
            Bukkit.getScheduler().runTask(this, () -> {
                event.getPlayer().setGameMode(GameMode.SPECTATOR);
                event.getPlayer().setSpectatorTarget(null);
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String saved = data.getString("player." + event.getPlayer().getUniqueId() + ".inventory", "");
        if (!saved.isBlank()) {
            restore(event.getPlayer(), true);
            Text.msg(event.getPlayer(), "&7Your interrupted Skirmish session was safely restored.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (queued.contains(event.getPlayer().getUniqueId())) {
            leave(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && queued.contains(player.getUniqueId()) && !running) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isSkirmishWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isSkirmishWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean isSkirmishWorld(Player player) {
        return player != null && player.getWorld() != null && player.getWorld().getName().equalsIgnoreCase(WORLD);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && queued.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getLocation() != null && event.getLocation().getWorld() != null && WORLD.equals(event.getLocation().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    private boolean join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&cLeave admin mode before joining Skirmish.");
            return true;
        }
        if (queued.contains(player.getUniqueId())) {
            Text.msg(player, "&7You are already in the Skirmish lobby.");
            return true;
        }
        if (queued.size() >= MAX_PLAYERS || running) {
            Text.msg(player, "&cThis Skirmish is already full or active.");
            return true;
        }
        capture(player, "skirmish-join");
        returns.put(player.getUniqueId(), player.getLocation());
        data.set("player." + player.getUniqueId() + ".inventory", encodeInventory(player.getInventory().getContents()));
        data.set("player." + player.getUniqueId() + ".return", encode(player.getLocation()));
        data.set("player." + player.getUniqueId() + ".gamemode", player.getGameMode().name());
        data.save();
        queued.add(player.getUniqueId());
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.teleport(lobby());
        player.setNoDamageTicks(100);
        Text.msg(player, "&aJoined Skirmish &8(&f" + queued.size() + "&7/&f" + MAX_PLAYERS + "&8)&a. No hearts or SMP gear are at risk.");
        return true;
    }

    private boolean leaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!queued.contains(player.getUniqueId())) {
            Text.msg(player, "&7You are not in Skirmish.");
            return true;
        }
        leave(player, true);
        return true;
    }

    private boolean startCommand(CommandSender sender) {
        if (!(sender instanceof Player player) || !queued.contains(player.getUniqueId())) {
            Text.msg(sender, "&cJoin Skirmish before starting it.");
            return true;
        }
        int minimum = MitchSMP.ranks().getRank(player.getUniqueId()).staff() ? 1 : 2;
        if (queued.size() < minimum) {
            Text.msg(player, "&cAt least two players are required. Staff may solo-start for testing.");
            return true;
        }
        start();
        return true;
    }

    private boolean stats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        Text.msg(player, "&4Skirmish stats: &f" + data.getInt("stats." + player.getUniqueId() + ".wins", 0) + " wins &8| &f" + data.getInt("stats." + player.getUniqueId() + ".kills", 0) + " kills &8| &f" + data.getInt("stats." + player.getUniqueId() + ".deaths", 0) + " deaths");
        return true;
    }

    private boolean help(CommandSender sender) {
        Text.msg(sender, "&7/skirmish join, /skirmish start, /skirmish leave, /skirmish stats");
        return true;
    }

    private void autoStart() {
        if (!running && queued.size() >= MAX_PLAYERS) {
            start();
        }
    }

    private void start() {
        if (running || queued.isEmpty()) {
            return;
        }
        running = true;
        finishing = false;
        alive.clear();
        alive.addAll(queued);
        List<Player> players = players();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (MitchSMP.gameplay() != null) {
                MitchSMP.gameplay().recordSkirmishParticipation(player.getUniqueId());
            }
            player.getInventory().clear();
            giveKit(player);
            player.setGameMode(GameMode.SURVIVAL);
            double angle = Math.PI * 2.0D * i / Math.max(1, players.size());
            player.teleport(new Location(world(), Math.cos(angle) * 15.0D + 0.5D, 81.0D, Math.sin(angle) * 15.0D + 0.5D));
            player.setHealth(20.0D);
            player.setFoodLevel(20);
            player.setNoDamageTicks(80);
            player.sendTitle(Text.color("&4SKIRMISH"), Text.color("&7Fight without risking hearts"), 10, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.4F);
        }
    }

    private void checkWinner() {
        if (!running || finishing) {
            return;
        }
        List<Player> survivors = alive.stream().map(Bukkit::getPlayer).filter(player -> player != null && queued.contains(player.getUniqueId())).toList();
        if (survivors.size() > 1) {
            return;
        }
        Player winner = survivors.isEmpty() ? null : survivors.get(0);
        finishing = true;
        running = false;
        Bukkit.getScheduler().runTaskLater(this, () -> finishMatch(winner), 40L);
    }

    private void finishMatch(Player winner) {
        if (winner != null && queued.size() >= 2) {
            data.set("stats." + winner.getUniqueId() + ".wins", data.getInt("stats." + winner.getUniqueId() + ".wins", 0) + 1);
            EconomyService economy = MitchSMP.economy();
            if (economy != null) {
                economy.deposit(winner.getUniqueId(), 250.0D, "Skirmish win");
            }
            SkillService skills = MitchSMP.skills();
            if (skills != null) {
                skills.addXp(winner.getUniqueId(), "combat", 75, "Skirmish win");
            }
            broadcast("&6" + winner.getName() + " won the Skirmish. &7Reward: &f$250 + Combat XP&7.");
        }
        data.save();
        for (Player player : new ArrayList<>(players())) {
            leave(player, true);
        }
        alive.clear();
        finishing = false;
    }

    private void leave(Player player, boolean teleport) {
        queued.remove(player.getUniqueId());
        alive.remove(player.getUniqueId());
        restore(player, teleport);
        if (running) {
            Bukkit.getScheduler().runTask(this, this::checkWinner);
        }
    }

    private void restore(Player player, boolean teleport) {
        player.getInventory().clear();
        player.getInventory().setContents(decodeInventory(data.getString("player." + player.getUniqueId() + ".inventory", ""), player.getInventory().getSize()));
        Location back = returns.remove(player.getUniqueId());
        if (back == null) {
            back = decode(data.getString("player." + player.getUniqueId() + ".return", ""));
        }
        GameMode mode = GameMode.SURVIVAL;
        try {
            mode = GameMode.valueOf(data.getString("player." + player.getUniqueId() + ".gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException ignored) {
        }
        player.setGameMode(mode);
        if (teleport && back != null) {
            player.teleport(back);
        }
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(100);
        data.set("player." + player.getUniqueId() + ".inventory", null);
        data.set("player." + player.getUniqueId() + ".return", null);
        data.set("player." + player.getUniqueId() + ".gamemode", null);
        data.save();
        capture(player, "skirmish-leave");
    }

    private void giveKit(Player player) {
        ItemStack sword = enchanted(Material.IRON_SWORD, Enchantment.SHARPNESS, 1);
        player.getInventory().addItem(sword, new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 24), new ItemStack(Material.COOKED_BEEF, 8));
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
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

    private List<Player> players() {
        return queued.stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
    }

    private void broadcast(String message) {
        players().forEach(player -> Text.msg(player, message));
    }

    private void ensureArena() {
        World world = loadWorld();
        if (world == null || data.getInt("layout", 0) >= 1) {
            return;
        }
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                int distance = x * x + z * z;
                if (distance <= 22 * 22) {
                    world.getBlockAt(x, 80, z).setType(distance >= 20 * 20 ? Material.RED_WOOL : Material.BLACKSTONE);
                }
            }
        }
        for (int y = 81; y <= 86; y++) {
            for (int x = -23; x <= 23; x++) {
                for (int z = -23; z <= 23; z++) {
                    int d = x * x + z * z;
                    if (d >= 21 * 21 && d <= 23 * 23) {
                        world.getBlockAt(x, y, z).setType(y == 86 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                    }
                }
            }
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = 30; z <= 42; z++) {
                world.getBlockAt(x, 90, z).setType(Material.BLACKSTONE);
                world.getBlockAt(x, 96, z).setType(Material.OBSIDIAN);
                if (x == -7 || x == 7 || z == 30 || z == 42) {
                    for (int y = 91; y <= 95; y++) {
                        world.getBlockAt(x, y, z).setType(Material.OBSIDIAN);
                    }
                }
            }
        }
        data.set("layout", 1);
        data.save();
    }

    private World loadWorld() {
        if (Bukkit.getWorld(WORLD) != null) {
            return Bukkit.getWorld(WORLD);
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(WORLD);
            creatorClass.getMethod("generator", Class.forName("org.bukkit.generator.ChunkGenerator")).invoke(creator, new VoidGenerator());
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, false);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create Skirmish world: " + exception.getMessage());
        }
        return Bukkit.getWorld(WORLD);
    }

    private World world() {
        return loadWorld();
    }

    private Location lobby() {
        return new Location(world(), 0.5D, 91.0D, 36.5D, 180.0F, 0.0F);
    }

    private Location spectator() {
        return new Location(world(), 0.5D, 88.0D, 0.5D);
    }

    private void capture(Player player, String trigger) {
        InventorySnapshotService snapshots = MitchSMP.snapshots();
        if (snapshots != null) {
            snapshots.capture(player, trigger);
        }
    }

    private String encode(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location decode(String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split(";");
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
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
            parts.add(item == null || item.getType() == Material.AIR ? "-" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        return String.join(";", parts);
    }

    private ItemStack[] decodeInventory(String encoded, int size) {
        ItemStack[] result = new ItemStack[size];
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        String[] parts = encoded.split(";", -1);
        for (int index = 0; index < Math.min(size, parts.length); index++) {
            if (parts[index].equals("-") || parts[index].isBlank()) {
                continue;
            }
            try {
                result[index] = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[index]));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Could not restore Skirmish inventory slot " + index);
            }
        }
        return result;
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
}
