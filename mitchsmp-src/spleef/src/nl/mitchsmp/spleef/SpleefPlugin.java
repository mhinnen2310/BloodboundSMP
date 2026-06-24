package nl.mitchsmp.spleef;

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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpleefPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String DEFAULT_ARENA = "default";
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;
    private static final int LAYOUT_VERSION = 3;
    private static final String CLAPPER_NAME = "Snowball Clapper";

    private final Map<UUID, String> activeArena = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, Integer> savedFoodLevels = new HashMap<>();
    private final Map<UUID, Float> savedSaturations = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Set<UUID> inGame = new HashSet<>();
    private final Set<String> starting = new HashSet<>();
    private final Set<String> running = new HashSet<>();
    private final Map<String, Double> matchPots = new HashMap<>();
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("arenas.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("spleef") != null) {
            getCommand("spleef").setExecutor(this);
            getCommand("spleef").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ensureDefaultArena();
            upgradeArenaLayouts();
        }, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::autoStart, 60L, 60L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "arenas" -> Text.msg(sender, arenaNames().isEmpty() ? "&7Geen Spleef arenas." : "&7Spleef arenas: &f" + String.join("&7, &f", arenaNames()));
            case "premade" -> premade(sender, args);
            case "paid", "price" -> paid(sender, args);
            case "start" -> startCommand(sender, args);
            case "end" -> end(sender, args.length >= 2 ? safe(args[1]) : firstArena());
            default -> help(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "join", "start", "leave", "arenas", "premade", "paid", "price", "end");
        }
        if (args.length == 2 && args[0].matches("(?i)join|start|end|paid|price")) {
            return Tab.complete(args[1], arenaNames());
        }
        if (args.length == 3 && args[0].matches("(?i)paid|price")) {
            return Tab.complete(args[2], "0", "50", "100", "250", "500", "1000");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("premade")) {
            return Tab.complete(args[2], "frost", "bloodice", "relic");
        }
        return List.of();
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!activeArena.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        String lower = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (lower.equals("/spleef leave") || lower.equals("/sf leave") || lower.equals("/spleef start") || lower.startsWith("/spleef start ") || lower.equals("/sf start") || lower.startsWith("/sf start ")) {
            return;
        }
        event.setCancelled(true);
        Text.msg(event.getPlayer(), "&cCommands are disabled during Spleef. Use &f/spleef leave&c.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restoreSavedSurvivalInventory(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (activeArena.containsKey(event.getPlayer().getUniqueId())) {
            finishLeave(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        Location to = event.getTo();
        if (arena == null || to == null) {
            return;
        }
        if (!inGame.contains(player.getUniqueId())) {
            keepInLobby(player, arena, to);
            return;
        }
        if (to.getY() < 80.0D || outsidePlayableArea(to)) {
            eliminate(player, arena);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null) {
            return;
        }
        event.setCancelled(true);
        if (!running.contains(arena) || !inGame.contains(player.getUniqueId())) {
            Text.msg(player, "&cWacht tot de ronde start.");
        } else {
            Text.msg(player, "&cGebruik snowballs om de vloer weg te schieten.");
        }
    }

    @EventHandler
    public void onThrowSnowball(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null || !running.contains(arena) || !inGame.contains(player.getUniqueId())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isClapper(item)) {
            return;
        }
        event.setCancelled(true);
        throwSnowball(player);
    }

    @EventHandler
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null || !running.contains(arena) || !inGame.contains(player.getUniqueId())) {
            return;
        }
        Location impact = null;
        Block hitBlock = event.getHitBlock();
        if (hitBlock != null) {
            impact = hitBlock.getLocation();
        } else if (event.getHitEntity() != null) {
            impact = event.getHitEntity().getLocation();
            impact = new Location(impact.getWorld(), impact.getX(), impact.getY() - 1.0D, impact.getZ());
        }
        if (impact != null) {
            breakSnowPatch(impact);
            player.playSound(player.getLocation(), Sound.BLOCK_SNOW_BREAK, 0.55F, 1.5F);
        }
        event.getEntity().remove();
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (activeArena.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getLocation() != null && event.getLocation().getWorld() != null && event.getLocation().getWorld().getName().startsWith("spleef_")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && activeArena.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setFallDistance(0.0F);
            player.setNoDamageTicks(20);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && activeArena.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            fillFood(player);
        }
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.spleef.play")) {
            Text.msg(player, "&cGeen permissie.");
            return;
        }
        if (MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&cLeave admin mode before joining Spleef. Staff inventory and test data cannot enter minigames.");
            return;
        }
        if (activeArena.containsKey(player.getUniqueId())) {
            Text.msg(player, "&cJe zit al in Spleef.");
            return;
        }
        ensureDefaultArena();
        String arena = args.length >= 2 ? safe(args[1]) : firstArena();
        Location lobby = lobby(arena);
        if (arena == null || lobby == null) {
            Text.msg(player, "&cArena not found.");
            return;
        }
        if (players(arena).size() >= MAX_PLAYERS) {
            Text.msg(player, "&cDeze Spleef lobby is vol: &f" + MAX_PLAYERS + "/" + MAX_PLAYERS + "&c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.6F);
            return;
        }
        double price = entryFee(arena);
        if (price > 0.0D) {
            EconomyService economy = MitchSMP.economy();
            if (economy == null || economy.getBalance(player.getUniqueId()) < price) {
                Text.msg(player, "&cJe hebt &f$" + money(price) + " &cnodig om deze Spleef lobby te joinen.");
                return;
            }
        }
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents());
        savedFoodLevels.put(player.getUniqueId(), player.getFoodLevel());
        savedSaturations.put(player.getUniqueId(), player.getSaturation());
        returnLocations.put(player.getUniqueId(), player.getLocation());
        data.set("player." + player.getUniqueId() + ".savedInventory", encodeInventory(player.getInventory().getContents()));
        data.set("player." + player.getUniqueId() + ".food", player.getFoodLevel());
        data.set("player." + player.getUniqueId() + ".saturation", player.getSaturation());
        data.set("player." + player.getUniqueId() + ".return", encode(player.getLocation()));
        data.save();
        activeArena.put(player.getUniqueId(), arena);
        inGame.remove(player.getUniqueId());
        prepareLobby(player, arena);
        Text.msg(player, "&aYou joined Spleef. Auto-starts at &f" + MAX_PLAYERS + " &aplayers. Use &f/spleef start &afrom &f" + MIN_PLAYERS + "&a players.");
        if (price > 0.0D) {
            Text.msg(player, "&6Betaalde match: &f$" + money(price) + " &7entry fee. Pot gaat naar de winnaar.");
        }
        tryStart(arena);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return;
        }
        if (!activeArena.containsKey(player.getUniqueId())) {
            Text.msg(player, "&cJe zit niet in Spleef.");
            return;
        }
        finishLeave(player, true);
    }

    private void premade(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        String arena = args.length >= 2 ? safe(args[1]) : DEFAULT_ARENA;
        ArenaTheme theme = args.length >= 3 ? ArenaTheme.parse(args[2]) : ArenaTheme.FROST;
        if (theme == null) {
            Text.msg(sender, "&cUnknown theme. Use frost, bloodice or relic.");
            return;
        }
        World world = loadWorld("spleef_" + arena);
        if (world == null) {
            Text.msg(sender, "&cCould not create the Spleef world.");
            return;
        }
        data.set("arena." + arena + ".theme", theme.key);
        buildArena(arena, world);
        Text.msg(sender, "&aSpleef arena &f" + arena + " &ais ready with the &f" + theme.key + " &atheme.");
    }

    private void paid(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /spleef paid <arena> <price>");
            return;
        }
        String arena = safe(args[1]);
        if (lobby(arena) == null) {
            Text.msg(sender, "&cArena not found.");
            return;
        }
        double price;
        try {
            price = Math.max(0.0D, Double.parseDouble(args[2]));
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cOngeldig bedrag.");
            return;
        }
        data.set("arena." + arena + ".price", String.format(Locale.US, "%.2f", price));
        data.save();
        Text.msg(sender, price <= 0.0D
            ? "&aSpleef arena &f" + arena + " &ais weer gratis."
            : "&aSpleef arena &f" + arena + " &akost nu &f$" + money(price) + " &aper speler.");
    }

    private void startCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !MitchSMP.permissions().has(sender, "mitchsmp.spleef.admin")) {
            String arena = args.length >= 2 ? safe(args[1]) : activeArena.get(player.getUniqueId());
            if (arena == null || !arena.equals(activeArena.get(player.getUniqueId())) || inGame.contains(player.getUniqueId())) {
                Text.msg(player, "&cJe moet in de Spleef lobby zitten om deze match te starten.");
                return;
            }
            start(player, arena, false);
            return;
        }
        if (!admin(sender)) {
            return;
        }
        String arena = args.length >= 2 ? safe(args[1]) : sender instanceof Player player ? activeArena.getOrDefault(player.getUniqueId(), firstArena()) : firstArena();
        start(sender, arena, true);
    }

    private void start(CommandSender sender, String arena, boolean forced) {
        if (arena == null) {
            Text.msg(sender, "&cGeen arena.");
            return;
        }
        if (forced && !admin(sender)) {
            return;
        }
        if (running.contains(arena) || starting.contains(arena)) {
            Text.msg(sender, "&cArena draait al.");
            return;
        }
        if (!forced && players(arena).size() < MIN_PLAYERS) {
            Text.msg(sender, "&cNot enough players in this arena. Required: &f" + MIN_PLAYERS + "&c.");
            return;
        }
        starting.add(arena);
        rebuild(arena);
        broadcast(arena, "&bSpleef begins in &f5 &bseconds.");
        scheduleCountdown(arena, "&bFROSTFALL", "&7Shatter the ground beneath your rivals.");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!preparePaidPot(arena, forced ? 1 : MIN_PLAYERS)) {
                starting.remove(arena);
                return;
            }
            starting.remove(arena);
            running.add(arena);
            for (Player player : players(arena)) {
                inGame.add(player.getUniqueId());
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(spawn(arena));
                player.getInventory().clear();
                player.getInventory().addItem(clapper());
                fillFood(player);
                player.setFallDistance(0.0F);
                player.sendTitle(Text.color("&bFIRE!"), Text.color("&fBreak their footing. Be the last standing."), 5, 35, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.25F);
            }
        }, 100L);
    }

    private void end(CommandSender sender, String arena) {
        if (!admin(sender) || arena == null) {
            return;
        }
        for (Player player : new ArrayList<>(players(arena))) {
            finishLeave(player, true);
        }
        running.remove(arena);
        starting.remove(arena);
        refundPot(arena);
        rebuild(arena);
        Text.msg(sender, "&aSpleef arena gestopt.");
    }

    private void eliminate(Player player, String arena) {
        inGame.remove(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(lobby(arena));
        Text.msg(player, "&cYou have been eliminated.");
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.9F, 0.8F);
        checkWin(arena);
    }

    private void checkWin(String arena) {
        List<Player> alive = players(arena).stream().filter(player -> inGame.contains(player.getUniqueId())).toList();
        if (!running.contains(arena) || alive.size() > 1) {
            return;
        }
        running.remove(arena);
        if (alive.size() == 1) {
            Player winner = alive.get(0);
            broadcast(arena, "&6" + winner.getName() + " wint Spleef!");
            winner.sendTitle(Text.color("&6Victory"), Text.color("&fSpleef gewonnen"), 10, 60, 15);
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            winner.getWorld().spawnEntity(winner.getLocation().add(0, 2, 0), EntityType.FIREWORK_ROCKET);
            payWinner(arena, winner);
        } else {
            broadcast(arena, "&7Spleef eindigde zonder winnaar.");
            refundPot(arena);
        }
        matchPots.remove(arena);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : new ArrayList<>(players(arena))) {
                returnToLobby(player, arena);
            }
            rebuild(arena);
            tryStart(arena);
        }, 80L);
    }

    private void returnToLobby(Player player, String arena) {
        inGame.remove(player.getUniqueId());
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        fillFood(player);
        Location lobby = lobby(arena);
        if (lobby != null) {
            player.teleport(lobby);
        }
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(80);
        Text.msg(player, "&7You are back in the Spleef lobby. Use &f/spleef leave &7to return to the SMP.");
    }

    private void finishLeave(Player player, boolean teleportBack) {
        String arena = activeArena.remove(player.getUniqueId());
        inGame.remove(player.getUniqueId());
        player.getInventory().clear();
        ItemStack[] saved = savedInventories.remove(player.getUniqueId());
        if (saved == null) {
            saved = decodeInventory(data.getString("player." + player.getUniqueId() + ".savedInventory", ""), player.getInventory().getSize());
        }
        player.getInventory().setContents(saved);
        restoreFood(player);
        Location back = returnLocations.remove(player.getUniqueId());
        if (back == null) {
            back = decode(data.getString("player." + player.getUniqueId() + ".return", ""));
        }
        data.set("player." + player.getUniqueId() + ".savedInventory", null);
        data.set("player." + player.getUniqueId() + ".food", null);
        data.set("player." + player.getUniqueId() + ".saturation", null);
        data.set("player." + player.getUniqueId() + ".return", null);
        data.save();
        if (teleportBack && back != null) {
            player.teleport(back);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(80);
        if (arena != null) {
            checkWin(arena);
        }
    }

    private void restoreSavedSurvivalInventory(Player player) {
        String savedRaw = data.getString("player." + player.getUniqueId() + ".savedInventory", "");
        if (savedRaw == null || savedRaw.isBlank()) {
            return;
        }
        activeArena.remove(player.getUniqueId());
        inGame.remove(player.getUniqueId());
        player.getInventory().clear();
        player.getInventory().setContents(decodeInventory(savedRaw, player.getInventory().getSize()));
        restoreFood(player);
        Location back = decode(data.getString("player." + player.getUniqueId() + ".return", ""));
        data.set("player." + player.getUniqueId() + ".savedInventory", null);
        data.set("player." + player.getUniqueId() + ".food", null);
        data.set("player." + player.getUniqueId() + ".saturation", null);
        data.set("player." + player.getUniqueId() + ".return", null);
        data.save();
        if (back != null) {
            player.teleport(back);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(100);
        Text.msg(player, "&7Je Spleef sessie is veilig hersteld naar de SMP.");
    }

    private void throwSnowball(Player player) {
        try {
            Class<?> snowballClass = Class.forName("org.bukkit.entity.Snowball");
            Object projectile = player.getClass().getMethod("launchProjectile", Class.class).invoke(player, snowballClass);
            boostProjectile(projectile, 1.40D);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.8F);
        } catch (ReflectiveOperationException exception) {
            Text.msg(player, "&cKon snowball niet gooien op deze serverversie.");
        }
    }

    private void boostProjectile(Object projectile, double multiplier) {
        if (projectile == null) {
            return;
        }
        try {
            Object velocity = projectile.getClass().getMethod("getVelocity").invoke(projectile);
            Object boosted = velocity.getClass().getMethod("multiply", double.class).invoke(velocity, multiplier);
            projectile.getClass().getMethod("setVelocity", velocity.getClass()).invoke(projectile, boosted);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private ItemStack clapper() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&b" + CLAPPER_NAME));
            meta.setLore(List.of(
                Text.color("&7Shoots unlimited snowballs."),
                Text.color("&7Projectile speed: &f140%&7."),
                Text.color("&8Spleef-only tool.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isClapper(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && Text.stripColorCodes(meta.getDisplayName()).equalsIgnoreCase(CLAPPER_NAME);
    }

    private void breakSnowPatch(Location impact) {
        if (impact.getWorld() == null) {
            return;
        }
        int centerX = impact.getBlockX();
        int centerY = impact.getBlockY();
        int centerZ = impact.getBlockZ();
        int floorY = Integer.MIN_VALUE;
        for (int y = centerY + 1; y >= centerY - 1; y--) {
            if (new Location(impact.getWorld(), centerX, y, centerZ).getBlock().getType() == Material.SNOW_BLOCK) {
                floorY = y;
                break;
            }
        }
        if (floorY == Integer.MIN_VALUE) {
            return;
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                Location location = new Location(impact.getWorld(), x, floorY, z);
                if (location.getBlock().getType() == Material.SNOW_BLOCK) {
                    location.getBlock().setType(Material.AIR);
                }
            }
        }
    }

    private void prepareLobby(Player player, String arena) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        fillFood(player);
        player.teleport(lobby(arena));
        player.setFallDistance(0.0F);
    }

    private void keepInLobby(Player player, String arena, Location to) {
        Location lobby = lobby(arena);
        if (lobby == null || to.getWorld() == null || !to.getWorld().getName().equals(lobby.getWorld().getName())) {
            player.teleport(lobby);
            return;
        }
        if (to.distanceSquared(lobby) > 200.0D || Math.abs(to.getY() - lobby.getY()) > 8.0D) {
            player.teleport(lobby);
        }
    }

    private boolean outsidePlayableArea(Location location) {
        if (location.getY() < 80.0D) {
            return false;
        }
        double distance = Math.sqrt(location.getX() * location.getX() + location.getZ() * location.getZ());
        if (location.getY() >= 102.5D) {
            return distance > 25.5D;
        }
        if (location.getY() >= 96.5D) {
            return distance > 23.5D;
        }
        if (location.getY() >= 90.5D) {
            return distance > 21.5D;
        }
        return distance > 19.5D;
    }

    private void fillFood(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private void restoreFood(Player player) {
        Integer savedFood = savedFoodLevels.remove(player.getUniqueId());
        Float saturation = savedSaturations.remove(player.getUniqueId());
        int food = savedFood == null ? Math.max(0, Math.min(20, data.getInt("player." + player.getUniqueId() + ".food", 20))) : savedFood;
        if (saturation == null) {
            saturation = (float) Math.max(0.0D, Math.min(20.0D, data.getDouble("player." + player.getUniqueId() + ".saturation", food)));
        }
        player.setFoodLevel(food);
        player.setSaturation(saturation);
    }

    private void autoStart() {
        for (String arena : arenaNames()) {
            tryStart(arena);
        }
    }

    private void tryStart(String arena) {
        if (players(arena).size() >= MAX_PLAYERS) {
            start(Bukkit.getConsoleSender(), arena, false);
        }
    }

    private void ensureDefaultArena() {
        ensureThemedArena(DEFAULT_ARENA, ArenaTheme.FROST);
        ensureThemedArena("bloodice", ArenaTheme.BLOODICE);
        ensureThemedArena("relicfrost", ArenaTheme.RELIC);
    }

    private void ensureThemedArena(String arena, ArenaTheme theme) {
        if (data.contains("arena." + arena + ".world")) {
            return;
        }
        World world = loadWorld("spleef_" + arena);
        if (world != null) {
            data.set("arena." + arena + ".theme", theme.key);
            buildArena(arena, world);
        }
    }

    private void upgradeArenaLayouts() {
        for (String arena : arenaNames()) {
            if (data.getInt("arena." + arena + ".layoutVersion", 0) >= LAYOUT_VERSION) {
                continue;
            }
            World world = world(arena);
            if (world != null) {
                buildArena(arena, world);
            }
        }
    }

    private void buildArena(String arena, World world) {
        ArenaTheme theme = theme(arena);
        clear(world, -48, 78, -84, 48, 132, 84);
        disc(world, 0, 104, 0, 24, Material.SNOW_BLOCK);
        ring(world, 0, 104, 0, 25, theme.rim);
        disc(world, 0, 98, 0, 22, Material.SNOW_BLOCK);
        ring(world, 0, 98, 0, 23, theme.accent);
        disc(world, 0, 92, 0, 20, Material.SNOW_BLOCK);
        ring(world, 0, 92, 0, 21, theme.rim);
        disc(world, 0, 86, 0, 18, Material.SNOW_BLOCK);
        ring(world, 0, 86, 0, 19, theme.accent);
        ring(world, 0, 80, 0, 28, theme.base);
        buildClosedLobby(world, theme);
        data.set("arena." + arena + ".world", world.getName());
        data.set("arena." + arena + ".lobby", encode(new Location(world, 0.5D, 121.0D, 58.5D, 180.0F, 0.0F)));
        data.set("arena." + arena + ".spawn", encode(new Location(world, 0.5D, 107.0D, 0.5D)));
        data.set("arena." + arena + ".theme", theme.key);
        data.set("arena." + arena + ".layoutVersion", LAYOUT_VERSION);
        data.save();
    }

    private void buildClosedLobby(World world, ArenaTheme theme) {
        int cx = 0;
        int floor = 120;
        int cz = 58;
        int half = 9;
        int height = 7;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                boolean edge = x == cx - half || x == cx + half || z == cz - half || z == cz + half;
                new Location(world, x, floor, z).getBlock().setType(edge ? theme.rim : theme.floor);
                new Location(world, x, floor + height, z).getBlock().setType(edge ? theme.accent : Material.GLASS);
                for (int y = floor + 1; y < floor + height; y++) {
                    new Location(world, x, y, z).getBlock().setType(edge ? Material.GLASS : Material.AIR);
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                new Location(world, x, floor, z).getBlock().setType((Math.abs(x) == 3 || Math.abs(z - cz) == 3) ? theme.accent : Material.SNOW_BLOCK);
            }
        }
    }

    private ArenaTheme theme(String arena) {
        ArenaTheme selected = ArenaTheme.parse(data.getString("arena." + arena + ".theme", "frost"));
        return selected == null ? ArenaTheme.FROST : selected;
    }

    private void rebuild(String arena) {
        World world = world(arena);
        if (world != null) {
            buildArena(arena, world);
        }
    }

    private void clear(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    new Location(world, x, y, z).getBlock().setType(Material.AIR);
                }
            }
        }
    }

    private void disc(World world, int cx, int y, int cz, int radius, Material material) {
        int squared = radius * radius;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz <= squared) {
                    new Location(world, x, y, z).getBlock().setType(material);
                }
            }
        }
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
                    new Location(world, x, y, z).getBlock().setType(material);
                }
            }
        }
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
            getLogger().warning("Could not create Spleef world " + name + ": " + exception.getMessage());
        }
        return Bukkit.getWorld(name);
    }

    private World world(String arena) {
        String worldName = data.getString("arena." + arena + ".world", "");
        return worldName.isBlank() ? null : loadWorld(worldName);
    }

    private Location lobby(String arena) {
        return decode(data.getString("arena." + arena + ".lobby", ""));
    }

    private Location spawn(String arena) {
        return decode(data.getString("arena." + arena + ".spawn", ""));
    }

    private double entryFee(String arena) {
        return Math.max(0.0D, data.getDouble("arena." + arena + ".price", 0.0D));
    }

    private boolean preparePaidPot(String arena, int neededPlayers) {
        List<Player> players = players(arena);
        if (players.size() < neededPlayers) {
            broadcast(arena, "&cStart cancelled: not enough players.");
            return false;
        }
        double price = entryFee(arena);
        if (price <= 0.0D) {
            matchPots.put(arena, 0.0D);
            return true;
        }
        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            broadcast(arena, "&cStart cancelled: economy is not loaded.");
            return false;
        }
        for (Player player : new ArrayList<>(players)) {
            if (economy.getBalance(player.getUniqueId()) < price) {
                Text.msg(player, "&cJe hebt niet genoeg geld for deze betaalde Spleef match.");
                finishLeave(player, true);
            }
        }
        players = players(arena);
        if (players.size() < neededPlayers) {
            broadcast(arena, "&cStart cancelled: not enough paid players.");
            return false;
        }
        double pot = 0.0D;
        for (Player player : players) {
            if (economy.withdraw(player.getUniqueId(), price, "spleef entry")) {
                pot += price;
                Text.msg(player, "&6Spleef entry betaald: &f$" + money(price) + "&6.");
            }
        }
        matchPots.put(arena, pot);
        broadcast(arena, "&6Spleef pot: &f$" + money(pot));
        return true;
    }

    private void payWinner(String arena, Player winner) {
        double pot = matchPots.getOrDefault(arena, 0.0D);
        if (pot <= 0.0D || winner == null) {
            return;
        }
        EconomyService economy = MitchSMP.economy();
        if (economy != null) {
            economy.deposit(winner.getUniqueId(), pot, "spleef pot");
            Text.msg(winner, "&6Je won de Spleef pot: &f$" + money(pot) + "&6.");
        }
    }

    private void refundPot(String arena) {
        double pot = matchPots.getOrDefault(arena, 0.0D);
        if (pot <= 0.0D) {
            return;
        }
        EconomyService economy = MitchSMP.economy();
        List<Player> players = players(arena);
        if (economy == null || players.isEmpty()) {
            matchPots.remove(arena);
            return;
        }
        double share = pot / players.size();
        for (Player player : players) {
            economy.deposit(player.getUniqueId(), share, "spleef pot refund");
            Text.msg(player, "&6Spleef pot terugbetaald: &f$" + money(share) + "&6.");
        }
        matchPots.remove(arena);
    }

    private List<Player> players(String arena) {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                players.add(player);
            }
        }
        return players;
    }

    private List<String> arenaNames() {
        List<String> result = new ArrayList<>();
        for (String key : data.keys()) {
            if (key.startsWith("arena.") && key.endsWith(".world")) {
                result.add(key.substring("arena.".length(), key.length() - ".world".length()));
            }
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    private String firstArena() {
        return arenaNames().isEmpty() ? null : arenaNames().get(0);
    }

    private void broadcast(String arena, String message) {
        for (Player player : players(arena)) {
            Text.msg(player, message);
        }
    }

    private void scheduleCountdown(String arena, String title, String subtitle) {
        for (int seconds = 5; seconds >= 1; seconds--) {
            int shown = seconds;
            long delay = (5L - seconds) * 20L;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player player : players(arena)) {
                    player.sendTitle(Text.color(title + " &8| &f" + shown), Text.color(subtitle), 2, 16, 2);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9F, 0.8F + ((5 - shown) * 0.16F));
                }
            }, delay);
        }
    }

    private boolean admin(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.spleef.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return false;
        }
        return true;
    }

    private void help(CommandSender sender) {
        Text.msg(sender, "&7/spleef join [arena], /spleef start, /spleef leave, /spleef arenas");
        Text.msg(sender, "&7Auto-starts at &f" + MAX_PLAYERS + " &7players. Manual start from &f" + MIN_PLAYERS + "&7 players.");
        if (MitchSMP.permissions().has(sender, "mitchsmp.spleef.admin")) {
            Text.msg(sender, "&7Admin: /spleef premade <arena>, /spleef paid <arena> <price>, /spleef start <arena>, /spleef end <arena>");
        }
    }

    private String safe(String input) {
        String safe = input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return safe.isBlank() ? DEFAULT_ARENA : safe;
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private String encode(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location decode(String encoded) {
        String[] parts = encoded.split(";");
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
                getLogger().warning("Could not load Spleef saved inventory slot " + i + ": " + exception.getMessage());
            }
        }
        return contents;
    }

    private enum ArenaTheme {
        FROST("frost", Material.BLUE_WOOL, Material.SEA_LANTERN, Material.QUARTZ_BLOCK, Material.QUARTZ_BLOCK),
        BLOODICE("bloodice", Material.BLACKSTONE, Material.RED_WOOL, Material.CRYING_OBSIDIAN, Material.BLACKSTONE),
        RELIC("relic", Material.OBSIDIAN, Material.GOLD_BLOCK, Material.SEA_LANTERN, Material.QUARTZ_BLOCK);

        private final String key;
        private final Material base;
        private final Material rim;
        private final Material accent;
        private final Material floor;

        ArenaTheme(String key, Material base, Material rim, Material accent, Material floor) {
            this.key = key;
            this.base = base;
            this.rim = rim;
            this.accent = accent;
            this.floor = floor;
        }

        private static ArenaTheme parse(String input) {
            for (ArenaTheme theme : values()) {
                if (theme.key.equalsIgnoreCase(input)) {
                    return theme;
                }
            }
            return null;
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



