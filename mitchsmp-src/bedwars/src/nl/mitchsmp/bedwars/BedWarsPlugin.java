package nl.mitchsmp.bedwars;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.HeartService;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedWarsPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String DEFAULT_ARENA = "auto";
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 4;
    private static final int LAYOUT_VERSION = 2;
    private static final double LOBBY_RADIUS = 7.5D;

    private final Random random = new Random();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, String> activeArena = new HashMap<>();
    private final Map<UUID, String> activeTeam = new HashMap<>();
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Set<UUID> inGame = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> voidCooldown = new HashSet<>();
    private final Set<String> endingArenas = new HashSet<>();
    private final Set<String> startingArenas = new HashSet<>();
    private final Set<String> pendingBedRepairs = new HashSet<>();
    private final Map<String, Double> matchPots = new HashMap<>();
    private final Map<String, Integer> matchPlayerCounts = new HashMap<>();
    private final Map<UUID, Long> safeAfterLeave = new HashMap<>();
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("arenas.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("bedwars") != null) {
            getCommand("bedwars").setExecutor(this);
            getCommand("bedwars").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::updateAllHud, 20L, 20L);
        Bukkit.getScheduler().runTaskLater(this, this::ensureDefaultArena, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::autoStartReadyArenas, 60L, 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::refillRunningArenaChests, 20L * 45L, 20L * 45L);
        Bukkit.getScheduler().runTaskTimer(this, this::repairActiveArenaBeds, 20L * 10L, 20L * 10L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "premade" -> premade(sender, args);
            case "create" -> create(sender, args);
            case "setlobby" -> setLobby(sender, args);
            case "setspawn" -> setSpawn(sender, args);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "arenas" -> arenas(sender);
            case "paid", "price" -> paid(sender, args);
            case "start" -> startCommand(sender, args);
            case "end" -> setRunning(sender, args, false);
            default -> help(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "premade", "create", "setlobby", "setspawn", "join", "leave", "arenas", "paid", "price", "start", "end");
        }
        if (args.length == 2 && args[0].matches("(?i)setlobby|setspawn|join|start|end|paid|price")) {
            return Tab.complete(args[1], arenaNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setspawn")) {
            return Tab.complete(args[2], "red", "blue", "green", "yellow");
        }
        if (args.length == 3 && args[0].matches("(?i)paid|price")) {
            return Tab.amounts(args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("premade")) {
            return Tab.complete(args[2], "crimson", "ember", "relic");
        }
        return List.of();
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!activeArena.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        String lower = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (lower.equals("/bw leave") || lower.startsWith("/bw leave ") || lower.equals("/bedwars leave") || lower.startsWith("/bedwars leave ")
            || lower.equals("/bw start") || lower.startsWith("/bw start ") || lower.equals("/bedwars start") || lower.startsWith("/bedwars start ")) {
            return;
        }
        event.setCancelled(true);
        Text.msg(event.getPlayer(), "&cDit command is niet beschikbaar in deze arena.");
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
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = safeAfterLeave.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        if (until < System.currentTimeMillis()) {
            safeAfterLeave.remove(player.getUniqueId());
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL || event.getCause() == EntityDamageEvent.DamageCause.VOID || !activeArena.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation() != null
            && event.getLocation().getWorld() != null
            && event.getLocation().getWorld().getName().toLowerCase(Locale.ROOT).startsWith("bedwars_")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBedPhysics(BlockPhysicsEvent event) {
        String arena = arenaFromWorld(event.getBlock().getLocation().getWorld());
        if (arena == null || !isUnbrokenBedBlock(arena, event.getBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
        if (!pendingBedRepairs.add(arena)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                repairUnbrokenBeds(arena);
            } finally {
                pendingBedRepairs.remove(arena);
            }
        }, 4L);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null) {
            return;
        }
        if (!inGame.contains(player.getUniqueId()) || !isRunning(arena)) {
            event.setCancelled(true);
            Text.msg(player, "&cWacht tot de ronde start.");
            return;
        }
        Material type = event.getBlock().getType();
        String material = type.name().toLowerCase(Locale.ROOT);
        if (!material.contains("bed")) {
            if (!material.endsWith("_wool")) {
                event.setCancelled(true);
            }
            return;
        }

        String bedTeam = bedTeam(arena, event.getBlock().getLocation());
        if (bedTeam == null) {
            event.setCancelled(true);
            return;
        }
        String playerTeam = activeTeam.get(player.getUniqueId());
        if (bedTeam.equals(playerTeam)) {
            event.setCancelled(true);
            Text.msg(player, "&cJe kunt je eigen bed niet breken.");
            return;
        }
        if (data.getString("arena." + arena + ".bedbroken." + bedTeam, "false").equals("true")) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        clearBed(arena, bedTeam);
        data.set("arena." + arena + ".bedbroken." + bedTeam, true);
        data.save();
        broadcast(arena, "&cBed van team &f" + bedTeam + " &cis gebroken door &f" + player.getName() + "&c.");
        playArenaSound(arena, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.7F);
        updateArenaHud(arena);
        checkWin(arena);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null) {
            return;
        }
        if (!inGame.contains(player.getUniqueId()) || !isRunning(arena)) {
            event.setCancelled(true);
            Text.msg(player, "&cWacht tot de ronde start.");
            return;
        }
        if (isUnbrokenBedBlock(arena, event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(player, "&cJe kunt geen blok in een levend bed plaatsen.");
            return;
        }
        if (!isPlayerPlaceable(event.getBlock().getType())) {
            event.setCancelled(true);
            Text.msg(player, "&cAlleen bouwblokken uit BedWars mogen geplaatst worden.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> repairUnbrokenBeds(arena), 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null || !inGame.contains(player.getUniqueId()) || !isRunning(arena)) {
            return;
        }
        if (event.getClickedBlock().getType() == Material.ENDER_CHEST) {
            event.setCancelled(true);
            openTrader(player);
        }
    }

    @EventHandler
    public void onTraderClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TraderMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TraderOffer offer = menu.offer(event.getRawSlot());
        if (offer != null) {
            buyTraderOffer(player, offer);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        if (!inGame.contains(player.getUniqueId())) {
            return;
        }
        handleArenaDeath(player, arena, false);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null) {
            return;
        }
        if (!inGame.contains(player.getUniqueId())) {
            Location lobby = lobby(arena);
            if (lobby != null) {
                event.setRespawnLocation(lobby);
            }
            Bukkit.getScheduler().runTaskLater(this, () -> prepareLobbyPlayer(player, arena, true), 1L);
            return;
        }
        if (eliminated.contains(player.getUniqueId())) {
            Location lobby = lobby(arena);
            if (lobby != null) {
                event.setRespawnLocation(lobby);
            }
            Bukkit.getScheduler().runTask(this, () -> returnToLobby(player, arena, true));
            return;
        }
        Location spawn = teamSpawn(arena, activeTeam.get(player.getUniqueId()));
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            protectLanding(player);
            heal(player);
            giveKit(player);
            updateHud(player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        }, 1L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        String arena = activeArena.get(player.getUniqueId());
        Location to = event.getTo();
        if (arena == null || to == null) {
            return;
        }
        if (!inGame.contains(player.getUniqueId()) || !isRunning(arena)) {
            keepInLobby(player, arena, to);
            return;
        }
        if (to.getY() > 82.0D || voidCooldown.contains(player.getUniqueId())) {
            return;
        }
        voidCooldown.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> voidCooldown.remove(player.getUniqueId()), 40L);
        handleArenaDeath(player, arena, true);
    }

    private void create(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /bw create <arena>");
            return;
        }
        String arena = safe(args[1]);
        String worldName = "bedwars_" + arena;
        loadWorld(worldName);
        data.set("arena." + arena + ".world", worldName);
        data.set("arena." + arena + ".running", false);
        data.set("arena." + arena + ".price", 0.0D);
        data.save();
        Text.msg(sender, "&aBedWars arena &f" + arena + " &aaangemaakt met world &f" + worldName + "&a.");
    }

    private void premade(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cUsage: /bw premade <arena> [crimson|ember|relic]");
            return;
        }
        String arena = safe(args[1]);
        ArenaTheme theme = args.length >= 3 ? ArenaTheme.parse(args[2]) : ArenaTheme.CRIMSON;
        if (theme == null) {
            Text.msg(sender, "&cUnknown theme. Use crimson, ember or relic.");
            return;
        }
        String worldName = "bedwars_" + arena;
        loadWorld(worldName);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Text.msg(sender, "&cCould not create the BedWars world.");
            return;
        }
        data.set("arena." + arena + ".theme", theme.key);
        setupPremade(arena, world);
        Text.msg(sender, "&aPremade BedWars arena &f" + arena + " &ais ready with the &f" + theme.key + " &atheme. Join with &f/bw join " + arena + "&a.");
    }

    private void setLobby(CommandSender sender, String[] args) {
        if (!admin(sender) || !(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /bw setlobby <arena>");
            return;
        }
        String arena = safe(args[1]);
        data.set("arena." + arena + ".lobby", encode(player.getLocation()));
        data.save();
        Text.msg(sender, "&aLobby gezet for &f" + arena + "&a.");
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!admin(sender) || !(sender instanceof Player player)) {
            return;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /bw setspawn <arena> <red|blue|green|yellow>");
            return;
        }
        String arena = safe(args[1]);
        String team = safe(args[2]);
        if (!team.matches("red|blue|green|yellow")) {
            Text.msg(sender, "&cTeam moet red, blue, green of yellow zijn.");
            return;
        }
        data.set("arena." + arena + ".spawn." + team, encode(player.getLocation()));
        data.save();
        Text.msg(sender, "&aSpawn &f" + team + " &agezet for &f" + arena + "&a.");
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.bedwars.play")) {
            Text.msg(player, "&cGeen permissie.");
            return;
        }
        if (activeArena.containsKey(player.getUniqueId())) {
            Text.msg(player, "&cJe zit al in BedWars.");
            return;
        }
        ensureDefaultArena();
        String arena = args.length >= 2 ? safe(args[1]) : bestJoinArena();
        if (arena == null || !data.contains("arena." + arena + ".world")) {
            Text.msg(player, "&cArena not found.");
            return;
        }
        if (playersInArena(arena) >= MAX_PLAYERS) {
            Text.msg(player, "&cDeze BedWars lobby is vol: &f" + MAX_PLAYERS + "/" + MAX_PLAYERS + "&c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.6F);
            return;
        }
        Location location = lobby(arena);
        if (location == null) {
            Text.msg(player, "&cArena heeft nog geen BedWars lobby.");
            return;
        }
        double price = entryFee(arena);
        if (price > 0.0D) {
            EconomyService economy = MitchSMP.economy();
            if (economy == null) {
                Text.msg(player, "&cDeze betaalde BedWars match kan niet starten omdat economy niet geladen is.");
                return;
            }
            if (economy.getBalance(player.getUniqueId()) < price) {
                Text.msg(player, "&cJe hebt &f$" + money(price) + " &cnodig om deze BedWars lobby te joinen.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.6F);
                return;
            }
        }

        savedInventories.put(player.getUniqueId(), player.getInventory().getContents());
        returnLocations.put(player.getUniqueId(), player.getLocation());
        data.set("player." + player.getUniqueId() + ".savedInventory", encodeInventory(player.getInventory().getContents()));
        data.set("player." + player.getUniqueId() + ".return", encode(player.getLocation()));
        data.save();
        activeArena.put(player.getUniqueId(), arena);
        activeTeam.remove(player.getUniqueId());
        lives.remove(player.getUniqueId());
        inGame.remove(player.getUniqueId());
        eliminated.remove(player.getUniqueId());
        prepareLobbyPlayer(player, arena, true);
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.3F);
        Text.msg(player, "&aYou joined the BedWars lobby for &f" + arena + "&a. Auto-starts at &f" + MAX_PLAYERS + " &aplayers. Use &f/bw start &afrom &f" + MIN_PLAYERS + "&a players.");
        if (price > 0.0D) {
            Text.msg(player, "&6Betaalde match: &f$" + money(price) + " &7entry fee. Pot gaat naar de winnaar.");
        }
        tryAutoStart(arena);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return;
        }
        if (!activeArena.containsKey(player.getUniqueId())) {
            Text.msg(player, "&cJe zit niet in BedWars.");
            return;
        }
        finishLeave(player, true);
    }

    private void finishLeave(Player player, boolean teleportBack) {
        String arena = activeArena.remove(player.getUniqueId());
        if (arena == null) {
            return;
        }
        activeTeam.remove(player.getUniqueId());
        lives.remove(player.getUniqueId());
        inGame.remove(player.getUniqueId());
        eliminated.remove(player.getUniqueId());
        voidCooldown.remove(player.getUniqueId());
        player.setLevel(0);
        player.getInventory().clear();
        ItemStack[] saved = savedInventories.remove(player.getUniqueId());
        if (saved == null) {
            saved = decodeInventory(data.getString("player." + player.getUniqueId() + ".savedInventory", ""), player.getInventory().getSize());
        }
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
        Location back = returnLocations.remove(player.getUniqueId());
        if (back == null) {
            back = decode(data.getString("player." + player.getUniqueId() + ".return", ""));
        }
        clearSavedSurvivalInventory(player.getUniqueId());
        if (teleportBack && back != null) {
            player.teleport(back);
            protectLanding(player);
        }
        player.setGameMode(GameMode.SURVIVAL);
        HeartService hearts = MitchSMP.hearts();
        if (hearts != null) {
            hearts.apply(player);
        }
        Text.msg(player, "&aYou returned to survival.");
        if (playersInArena(arena) == 0) {
            startingArenas.remove(arena);
            matchPots.remove(arena);
            matchPlayerCounts.remove(arena);
        }
        checkWin(arena);
    }

    private void restoreSavedSurvivalInventory(Player player) {
        String raw = data.getString("player." + player.getUniqueId() + ".savedInventory", "");
        if (raw.isBlank()) {
            return;
        }
        player.getInventory().clear();
        player.getInventory().setContents(decodeInventory(raw, player.getInventory().getSize()));
        Location back = decode(data.getString("player." + player.getUniqueId() + ".return", ""));
        clearSavedSurvivalInventory(player.getUniqueId());
        activeArena.remove(player.getUniqueId());
        activeTeam.remove(player.getUniqueId());
        inGame.remove(player.getUniqueId());
        eliminated.remove(player.getUniqueId());
        lives.remove(player.getUniqueId());
        voidCooldown.remove(player.getUniqueId());
        if (back != null) {
            player.teleport(back);
            protectLanding(player);
        }
        player.setGameMode(GameMode.SURVIVAL);
        HeartService hearts = MitchSMP.hearts();
        if (hearts != null) {
            hearts.apply(player);
        }
        Text.msg(player, "&aJe survival inventory is hersteld na een BedWars/server restart.");
    }

    private void clearSavedSurvivalInventory(UUID id) {
        data.set("player." + id + ".savedInventory", null);
        data.set("player." + id + ".return", null);
        data.save();
    }

    private void arenas(CommandSender sender) {
        List<String> arenas = arenaNames();
        Text.msg(sender, arenas.isEmpty() ? "&7Geen BedWars arenas." : "&7Arenas: &f" + String.join("&7, &f", arenas));
    }

    private void paid(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /bw paid <arena> <price>");
            return;
        }
        String arena = safe(args[1]);
        if (!data.contains("arena." + arena + ".world")) {
            Text.msg(sender, "&cArena not found.");
            return;
        }
        double price;
        try {
            price = Math.max(0.0D, Double.parseDouble(args[2]));
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cPrice moet een nummer zijn.");
            return;
        }
        data.set("arena." + arena + ".price", price);
        data.save();
        Text.msg(sender, price <= 0.0D
            ? "&aArena &f" + arena + " &ais weer gratis."
            : "&aArena &f" + arena + " &akost nu &f$" + money(price) + " &aper match.");
    }

    private void setRunning(CommandSender sender, String[] args, boolean running) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /bw " + (running ? "start" : "end") + " <arena>");
            return;
        }
        String arena = safe(args[1]);
        if (running) {
            startArena(sender, arena, true);
            return;
        }
        data.set("arena." + arena + ".running", running);
        data.save();
        startingArenas.remove(arena);
        endingArenas.remove(arena);
        matchPots.remove(arena);
        matchPlayerCounts.remove(arena);
        broadcastTitle(arena, "&cArena gestopt", "&7Je wordt teruggestuurd.", Sound.BLOCK_BELL_USE, 1.0F, 0.75F);
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                returnToLobby(player, arena, false);
            }
        }
        Text.msg(sender, "&cArena gestopt.");
    }

    private void startCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !MitchSMP.permissions().has(sender, "mitchsmp.bedwars.admin")) {
            String arena = args.length >= 2 ? safe(args[1]) : activeArena.get(player.getUniqueId());
            if (arena == null || !arena.equals(activeArena.get(player.getUniqueId())) || inGame.contains(player.getUniqueId())) {
                Text.msg(player, "&cJe moet in de BedWars lobby zitten om deze match te starten.");
                return;
            }
            startArena(player, arena, false);
            return;
        }
        if (!admin(sender)) {
            return;
        }
        String arena = args.length >= 2 ? safe(args[1]) : sender instanceof Player player ? activeArena.getOrDefault(player.getUniqueId(), firstArena()) : firstArena();
        startArena(sender, arena, true);
    }

    private void startArena(CommandSender sender, String arena, boolean forced) {
        if (!data.contains("arena." + arena + ".world")) {
            if (sender != null) {
                Text.msg(sender, "&cArena not found.");
            }
            return;
        }
        if (startingArenas.contains(arena) || endingArenas.contains(arena) || isRunning(arena)) {
            if (sender != null) {
                Text.msg(sender, "&cDeze arena is al bezig of start al.");
            }
            return;
        }
        int needed = forced ? 1 : MIN_PLAYERS;
        if (playersInArena(arena) < needed) {
            if (sender != null) {
                Text.msg(sender, "&cNot enough players in this arena. Required: &f" + needed + "&c.");
            }
            return;
        }
        startingArenas.add(arena);
        data.set("arena." + arena + ".running", false);
        data.save();
        broadcast(arena, "&6BedWars begins in &f5 &6seconds.");
        countdown(arena, 5, 0L);
        countdown(arena, 4, 20L);
        countdown(arena, 3, 40L);
        countdown(arena, 2, 60L);
        countdown(arena, 1, 80L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!preparePaidPot(arena, needed)) {
                startingArenas.remove(arena);
                data.set("arena." + arena + ".running", false);
                data.save();
                tryAutoStartLater(arena);
                return;
            }
            resetArena(arena);
            data.set("arena." + arena + ".running", true);
            data.save();
            startingArenas.remove(arena);
            sendPlayersToGame(arena);
            broadcast(arena, "&aBedWars has begun. Destroy the enemy beds!");
            broadcastTitle(arena, "&4BLOODBOUND!", "&fBreak beds. Claim loot. Survive.", Sound.ENTITY_PLAYER_LEVELUP, 1.2F, 1.0F);
            spawnStartFireworks(arena);
            updateArenaHud(arena);
        }, 100L);
        if (sender != null) {
            Text.msg(sender, "&aStart-countdown gestart for &f" + arena + "&a.");
        }
    }

    private void countdown(String arena, int seconds, long delay) {
        Bukkit.getScheduler().runTaskLater(this, () ->
            broadcastTitle(arena, "&4BEDWARS &8| &6" + seconds, "&7No bed. No mercy.", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.7F - (seconds * 0.2F)), delay);
    }

    private void handleArenaDeath(Player player, String arena, boolean instant) {
        String team = activeTeam.get(player.getUniqueId());
        boolean bedBroken = isBedBroken(arena, team);
        if (bedBroken) {
            int remaining = Math.max(0, lives.getOrDefault(player.getUniqueId(), 1) - 1);
            lives.put(player.getUniqueId(), remaining);
            if (remaining <= 0) {
                eliminated.add(player.getUniqueId());
                broadcast(arena, "&c" + player.getName() + " is ge-elimineerd.");
                playArenaSound(arena, Sound.BLOCK_BELL_USE, 1.0F, 0.8F);
                checkWin(arena);
                if (instant) {
                    returnToLobby(player, arena, true);
                }
                return;
            }
            broadcast(arena, "&e" + player.getName() + " verloor een life. Lives: &f" + remaining);
        } else {
            Text.msg(player, "&aJe bed leeft nog, je respawnt bij je eiland.");
        }
        if (instant) {
            Location spawn = teamSpawn(arena, team);
            if (spawn != null) {
                player.teleport(spawn);
            }
            protectLanding(player);
            heal(player);
            giveKit(player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.1F);
        }
        updateHud(player);
    }

    private void checkWin(String arena) {
        if (endingArenas.contains(arena) || !isRunning(arena)) {
            return;
        }
        Set<String> aliveTeams = new HashSet<>();
        for (Map.Entry<UUID, String> entry : activeTeam.entrySet()) {
            if (arena.equals(activeArena.get(entry.getKey())) && inGame.contains(entry.getKey()) && !eliminated.contains(entry.getKey())) {
                aliveTeams.add(entry.getValue());
            }
        }
        if (aliveTeams.size() != 1) {
            return;
        }
        String winner = aliveTeams.iterator().next();
        if (matchPlayerCounts.getOrDefault(arena, 0) < 2 && !allEnemyBedsBroken(arena, winner)) {
            return;
        }
        endingArenas.add(arena);
        data.set("arena." + arena + ".running", false);
        data.save();
        payWinners(arena, winner);
        broadcast(arena, "&6Team &f" + winner + " &6wint BedWars!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!arena.equals(activeArena.get(player.getUniqueId()))) {
                continue;
            }
            player.sendTitle(Text.color("&6Victory"), Text.color("&fTeam " + winner + " wint"), 10, 70, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F, 1.2F);
            for (int i = 0; i < 4; i++) {
                player.getWorld().spawnEntity(player.getLocation().add(0.0D, 2.0D, 0.0D), EntityType.FIREWORK_ROCKET);
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (arena.equals(activeArena.get(player.getUniqueId()))) {
                    returnToLobby(player, arena, true);
                }
            }
            resetArena(arena);
            endingArenas.remove(arena);
            matchPots.remove(arena);
            matchPlayerCounts.remove(arena);
            tryAutoStartLater(arena);
        }, 100L);
    }

    private boolean allEnemyBedsBroken(String arena, String winner) {
        for (String team : List.of("red", "blue", "green", "yellow")) {
            if (!team.equals(winner) && !isBedBroken(arena, team)) {
                return false;
            }
        }
        return true;
    }

    private int playersInArena(String arena) {
        int count = 0;
        for (String current : activeArena.values()) {
            if (arena.equals(current)) {
                count++;
            }
        }
        return count;
    }

    private int playersInGame(String arena) {
        int count = 0;
        for (UUID playerId : inGame) {
            if (arena.equals(activeArena.get(playerId)) && !eliminated.contains(playerId)) {
                count++;
            }
        }
        return count;
    }

    private void ensureDefaultArena() {
        ensureThemedArena(DEFAULT_ARENA, ArenaTheme.CRIMSON);
        ensureThemedArena("emberkeep", ArenaTheme.EMBER);
        ensureThemedArena("relicspire", ArenaTheme.RELIC);
        upgradeArenaLayouts();
    }

    private void ensureThemedArena(String arena, ArenaTheme theme) {
        if (data.contains("arena." + arena + ".world")) {
            return;
        }
        String worldName = "bedwars_" + arena;
        loadWorld(worldName);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Could not auto-create BedWars arena '" + arena + "'.");
            return;
        }
        data.set("arena." + arena + ".theme", theme.key);
        setupPremade(arena, world);
        getLogger().info("Auto-created " + theme.key + " BedWars arena '" + arena + "'.");
    }

    private void upgradeArenaLayouts() {
        for (String arena : arenaNames()) {
            if (data.getInt("arena." + arena + ".layoutVersion", 0) >= LAYOUT_VERSION || isRunning(arena) || playersInArena(arena) > 0) {
                continue;
            }
            World world = world(arena);
            if (world != null) {
                setupPremade(arena, world);
            }
        }
    }

    private void autoStartReadyArenas() {
        for (String arena : arenaNames()) {
            tryAutoStart(arena);
        }
    }

    private void tryAutoStart(String arena) {
        if (playersInArena(arena) >= MAX_PLAYERS) {
            startArena(null, arena, false);
        }
    }

    private void tryAutoStartLater(String arena) {
        Bukkit.getScheduler().runTaskLater(this, () -> tryAutoStart(arena), 60L);
    }

    private String bestJoinArena() {
        List<String> names = arenaNames();
        if (names.isEmpty()) {
            return null;
        }
        for (String arena : names) {
            if (!isRunning(arena) && !startingArenas.contains(arena) && !endingArenas.contains(arena) && playersInArena(arena) < MAX_PLAYERS) {
                return arena;
            }
        }
        return names.get(0);
    }

    private boolean isRunning(String arena) {
        return data.getString("arena." + arena + ".running", "false").equals("true");
    }

    private Location lobby(String arena) {
        return decode(data.getString("arena." + arena + ".lobby", ""));
    }

    private double entryFee(String arena) {
        return Math.max(0.0D, data.getDouble("arena." + arena + ".price", 0.0D));
    }

    private boolean preparePaidPot(String arena, int neededPlayers) {
        List<Player> players = playersInArenaList(arena);
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
                Text.msg(player, "&cJe hebt niet genoeg geld for deze betaalde BedWars match.");
                finishLeave(player, true);
            }
        }
        players = playersInArenaList(arena);
        if (players.size() < neededPlayers) {
            broadcast(arena, "&cStart cancelled: not enough paid players.");
            return false;
        }
        double pot = 0.0D;
        for (Player player : players) {
            if (economy.withdraw(player.getUniqueId(), price, "bedwars entry")) {
                pot += price;
                Text.msg(player, "&6BedWars entry betaald: &f$" + money(price) + "&6.");
            }
        }
        matchPots.put(arena, pot);
        broadcast(arena, "&6Betaalde match pot: &f$" + money(pot));
        return true;
    }

    private List<Player> playersInArenaList(String arena) {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                players.add(player);
            }
        }
        return players;
    }

    private void sendPlayersToGame(String arena) {
        List<Player> players = playersInArenaList(arena);
        matchPlayerCounts.put(arena, players.size());
        for (Player player : players) {
            String team = chooseTeam(arena);
            activeTeam.put(player.getUniqueId(), team);
            lives.put(player.getUniqueId(), 3);
            eliminated.remove(player.getUniqueId());
            voidCooldown.remove(player.getUniqueId());
            inGame.add(player.getUniqueId());
            setBedWarsHealth(player);
            player.setGameMode(GameMode.SURVIVAL);
            Location spawn = teamSpawn(arena, team);
            if (spawn != null) {
                player.teleport(spawn);
                protectLanding(player);
            }
            giveKit(player);
            heal(player);
            updateHud(player);
            Text.msg(player, "&aJe speelt als team &f" + team + "&a.");
        }
    }

    private void returnToLobby(Player player, String arena, boolean message) {
        inGame.remove(player.getUniqueId());
        activeTeam.remove(player.getUniqueId());
        lives.remove(player.getUniqueId());
        eliminated.remove(player.getUniqueId());
        voidCooldown.remove(player.getUniqueId());
        prepareLobbyPlayer(player, arena, true);
        protectLanding(player);
        if (message) {
            Text.msg(player, "&7You are back in the BedWars lobby. The next round starts automatically.");
        }
    }

    private void prepareLobbyPlayer(Player player, String arena, boolean teleport) {
        setBedWarsHealth(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().clear();
        heal(player);
        player.setLevel(playersInArena(arena));
        Location lobby = lobby(arena);
        if (teleport && lobby != null) {
            player.teleport(lobby);
            protectLanding(player);
        }
        updateHud(player);
    }

    private void protectLanding(Player player) {
        safeAfterLeave.put(player.getUniqueId(), System.currentTimeMillis() + 6000L);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(120);
    }

    private void keepInLobby(Player player, String arena, Location to) {
        Location lobby = lobby(arena);
        if (lobby == null || lobby.getWorld() == null || to.getWorld() == null) {
            return;
        }
        boolean wrongWorld = !lobby.getWorld().getName().equals(to.getWorld().getName());
        double dx = to.getX() - lobby.getX();
        double dz = to.getZ() - lobby.getZ();
        boolean tooFar = (dx * dx + dz * dz) > (LOBBY_RADIUS * LOBBY_RADIUS);
        boolean tooLowOrHigh = Math.abs(to.getY() - lobby.getY()) > 4.0D;
        if (wrongWorld || tooFar || tooLowOrHigh) {
            player.teleport(lobby);
            player.playSound(lobby, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 0.7F);
            Text.msg(player, "&cJe blijft in de BedWars lobby tot de match start.");
        }
    }

    private void payWinners(String arena, String winnerTeam) {
        double pot = matchPots.getOrDefault(arena, 0.0D);
        if (pot <= 0.0D) {
            return;
        }
        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            return;
        }
        List<Player> winners = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (arena.equals(activeArena.get(id)) && inGame.contains(id) && winnerTeam.equals(activeTeam.get(id)) && !eliminated.contains(id)) {
                winners.add(player);
            }
        }
        if (winners.isEmpty()) {
            return;
        }
        double share = pot / winners.size();
        for (Player winner : winners) {
            economy.deposit(winner.getUniqueId(), share, "bedwars pot");
            Text.msg(winner, "&6Je won de BedWars pot: &f$" + money(share) + "&6.");
        }
    }

    private void setBedWarsHealth(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0D);
        }
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private boolean isBedBroken(String arena, String team) {
        return team == null || data.getString("arena." + arena + ".bedbroken." + team, "false").equals("true");
    }

    private Location teamSpawn(String arena, String team) {
        if (team == null) {
            return null;
        }
        return decode(data.getString("arena." + arena + ".spawn." + team, ""));
    }

    private void giveKit(Player player) {
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.WHITE_WOOL, 32), new ItemStack(Material.COOKED_BEEF, 8));
        equipArmor(player, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
    }

    private void heal(Player player) {
        double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        player.setHealth(max);
        player.setFoodLevel(20);
        player.setSaturation(10.0F);
    }

    private void openTrader(Player player) {
        TraderMenu holder = new TraderMenu();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8BedWars Trader"));
        holder.inventory(inventory);
        traderOffer(holder, inventory, 10, new TraderOffer("wool", Material.WHITE_WOOL, 32, Material.IRON_INGOT, 8, "&f32 Wool"));
        traderOffer(holder, inventory, 11, new TraderOffer("endstone", Material.END_STONE, 16, Material.IRON_INGOT, 16, "&f16 End Stone"));
        traderOffer(holder, inventory, 12, new TraderOffer("planks", Material.OAK_PLANKS, 16, Material.IRON_INGOT, 12, "&f16 Planks"));
        traderOffer(holder, inventory, 13, new TraderOffer("ironsword", Material.IRON_SWORD, 1, Material.GOLD_INGOT, 8, "&fIron Sword"));
        traderOffer(holder, inventory, 14, new TraderOffer("bow", Material.BOW, 1, Material.GOLD_INGOT, 10, "&fBow + 16 Arrows"));
        traderOffer(holder, inventory, 15, new TraderOffer("gapple", Material.GOLDEN_APPLE, 1, Material.GOLD_INGOT, 4, "&fGolden Apple"));
        traderOffer(holder, inventory, 16, new TraderOffer("ironarmor", Material.IRON_CHESTPLATE, 1, Material.GOLD_INGOT, 16, "&fIron Armor Set"));
        traderOffer(holder, inventory, 19, new TraderOffer("chainrush", Material.IRON_AXE, 1, Material.IRON_INGOT, 20, "&fAxe Rush Kit"));
        traderOffer(holder, inventory, 20, new TraderOffer("crossbow", Material.CROSSBOW, 1, Material.GOLD_INGOT, 14, "&fCrossbow + 24 Arrows"));
        traderOffer(holder, inventory, 21, new TraderOffer("obsidian", Material.OBSIDIAN, 8, Material.DIAMOND, 2, "&58 Obsidian"));
        traderOffer(holder, inventory, 22, new TraderOffer("diamondsword", Material.DIAMOND_SWORD, 1, Material.DIAMOND, 4, "&bDiamond Sword"));
        traderOffer(holder, inventory, 23, new TraderOffer("diamondarmor", Material.DIAMOND_CHESTPLATE, 1, Material.DIAMOND, 8, "&bDiamond Armor Set"));
        traderOffer(holder, inventory, 24, new TraderOffer("trident", Material.TRIDENT, 1, Material.DIAMOND, 6, "&bTrident"));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.4F);
    }

    private void traderOffer(TraderMenu holder, Inventory inventory, int slot, TraderOffer offer) {
        holder.offer(slot, offer);
        inventory.setItem(slot, item(offer.icon(), "&a" + offer.title(), List.of(
            "&7Kosten: &f" + offer.cost() + "x " + pretty(offer.currency()),
            "&eKlik om te kopen."
        )));
    }

    private void buyTraderOffer(Player player, TraderOffer offer) {
        String arena = activeArena.get(player.getUniqueId());
        if (arena == null || !inGame.contains(player.getUniqueId()) || !isRunning(arena)) {
            Text.msg(player, "&cJe kunt de trader alleen gebruiken tijdens een BedWars ronde.");
            return;
        }
        if (!removeItems(player, offer.currency(), offer.cost())) {
            Text.msg(player, "&cNiet genoeg " + pretty(offer.currency()) + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
            return;
        }
        switch (offer.id()) {
            case "bow" -> {
                give(player, new ItemStack(Material.BOW));
                give(player, new ItemStack(Material.ARROW, 16));
            }
            case "crossbow" -> {
                give(player, new ItemStack(Material.CROSSBOW));
                give(player, new ItemStack(Material.ARROW, 24));
            }
            case "ironarmor" -> equipArmor(player, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
            case "diamondarmor" -> equipArmor(player, Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
            case "chainrush" -> {
                give(player, new ItemStack(Material.IRON_AXE));
                give(player, new ItemStack(Material.WHITE_WOOL, 32));
            }
            default -> give(player, new ItemStack(offer.icon(), offer.amount()));
        }
        Text.msg(player, "&aGekocht: &f" + Text.stripColorCodes(Text.color(offer.title())) + "&a.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
    }

    private void equipArmor(Player player, Material helmet, Material chestplate, Material leggings, Material boots) {
        if (player.getEquipment() == null) {
            return;
        }
        player.getEquipment().setHelmet(new ItemStack(helmet));
        player.getEquipment().setChestplate(new ItemStack(chestplate));
        player.getEquipment().setLeggings(new ItemStack(leggings));
        player.getEquipment().setBoots(new ItemStack(boots));
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private boolean removeItems(Player player, Material material, int amount) {
        if (countItems(player, material) < amount) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (ItemStack item : contents) {
            if (item == null || item.getType() != material || remaining <= 0) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    private int countItems(Player player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void updateAllHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (activeArena.containsKey(player.getUniqueId())) {
                updateHud(player);
            }
        }
    }

    private void updateArenaHud(String arena) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                updateHud(player);
            }
        }
    }

    private void updateHud(Player player) {
        String arena = activeArena.get(player.getUniqueId());
        if (!inGame.contains(player.getUniqueId())) {
            double price = entryFee(arena);
            String state = startingArenas.contains(arena) ? "&eStarting" : isRunning(arena) ? "&cIn game" : "&aWaiting";
            String fee = price > 0.0D ? " &7| &6$" + money(price) : "";
            player.setLevel(playersInArena(arena));
            player.sendActionBar(Text.color("&eBW &7| " + state + " &7| &f" + playersInArena(arena) + "/" + MAX_PLAYERS + fee));
            return;
        }
        String team = activeTeam.get(player.getUniqueId());
        int remaining = lives.getOrDefault(player.getUniqueId(), 3);
        player.setLevel(remaining);
        String bed = isBedBroken(arena, team) ? "&cBed broken" : "&aBed alive";
        player.sendActionBar(Text.color("&eBW &7| &fTeam " + team + " &7| &cLives " + remaining + " &7| " + bed));
    }

    private void playArenaSound(String arena, Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void broadcastTitle(String arena, String title, String subtitle, Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!arena.equals(activeArena.get(player.getUniqueId()))) {
                continue;
            }
            player.sendTitle(Text.color(title), Text.color(subtitle), 5, 25, 8);
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void spawnStartFireworks(String arena) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                player.getWorld().spawnEntity(player.getLocation().add(0.0D, 2.0D, 0.0D), EntityType.FIREWORK_ROCKET);
            }
        }
    }

    private String chooseTeam(String arena) {
        String best = "red";
        long bestCount = Long.MAX_VALUE;
        for (String team : List.of("red", "blue", "green", "yellow")) {
            if (decode(data.getString("arena." + arena + ".spawn." + team, "")) == null) {
                continue;
            }
            String currentTeam = team;
            long count = activeTeam.entrySet().stream()
                .filter(entry -> arena.equals(activeArena.get(entry.getKey())))
                .filter(entry -> currentTeam.equals(entry.getValue()))
                .count();
            if (count < bestCount) {
                best = team;
                bestCount = count;
            }
        }
        return best;
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
        List<String> names = arenaNames();
        return names.isEmpty() ? null : names.get(0);
    }

    private boolean admin(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.bedwars.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return false;
        }
        return true;
    }

    private void help(CommandSender sender) {
        Text.msg(sender, "&7/bw join [arena], /bw start, /bw leave, /bw arenas");
        Text.msg(sender, "&7Auto-starts at &f" + MAX_PLAYERS + " &7players. Manual start from &f" + MIN_PLAYERS + "&7 players.");
        if (MitchSMP.permissions().has(sender, "mitchsmp.bedwars.admin")) {
            Text.msg(sender, "&7Admin: /bw premade <arena>, /bw paid <arena> <price>, /bw start <arena>, /bw end <arena>");
        }
    }

    private void buildPremade(World world, ArenaTheme theme) {
        clearBox(world, -74, 96, -74, 74, 119, 74);
        disc(world, 0, 100, 0, 16, theme.base);
        disc(world, 0, 101, 0, 14, theme.floor);
        disc(world, 0, 102, 0, 10, theme.inner);
        ring(world, 0, 103, 0, 12, theme.light);
        platform(world, 0, 103, 0, 2, theme.relic);
        lobbyCage(world, theme);
        pillar(world, -7, 101, -7, 9, theme.accent);
        pillar(world, 7, 101, -7, 9, theme.accent);
        pillar(world, -7, 101, 7, 9, theme.accent);
        pillar(world, 7, 101, 7, 9, theme.accent);
        pillar(world, -18, 99, 0, 12, theme.base);
        pillar(world, 18, 99, 0, 12, theme.base);
        pillar(world, 0, 99, -18, 12, theme.base);
        pillar(world, 0, 99, 18, 12, theme.base);
        lootChest(world, 0, 104, 0, true);
        lootChest(world, 6, 103, 0, true);
        lootChest(world, -6, 103, 0, true);

        teamIsland(world, "red", -55, 100, 0, Material.RED_WOOL, Material.RED_BED, Material.GOLD_BLOCK, theme);
        teamIsland(world, "blue", 55, 100, 0, Material.BLUE_WOOL, Material.BLUE_BED, Material.LAPIS_BLOCK, theme);
        teamIsland(world, "green", 0, 100, -55, Material.GREEN_WOOL, Material.GREEN_BED, Material.EMERALD_BLOCK, theme);
        teamIsland(world, "yellow", 0, 100, 55, Material.YELLOW_WOOL, Material.YELLOW_BED, Material.GOLD_BLOCK, theme);
        bridge(world, -43, 102, 0, -14, 102, 0, Material.RED_WOOL);
        bridge(world, 43, 102, 0, 14, 102, 0, Material.BLUE_WOOL);
        bridge(world, 0, 102, -43, 0, 102, -14, Material.GREEN_WOOL);
        bridge(world, 0, 102, 43, 0, 102, 14, Material.YELLOW_WOOL);
    }

    private void setupPremade(String arena, World world) {
        ArenaTheme theme = theme(arena);
        buildPremade(world, theme);
        data.set("arena." + arena + ".world", world.getName());
        data.set("arena." + arena + ".running", false);
        if (!data.contains("arena." + arena + ".price")) {
            data.set("arena." + arena + ".price", 0.0D);
        }
        data.set("arena." + arena + ".lobby", encode(new Location(world, 0.5D, 113.0D, 0.5D)));
        data.set("arena." + arena + ".theme", theme.key);
        data.set("arena." + arena + ".layoutVersion", LAYOUT_VERSION);
        setTeam(arena, world, "red", -55, 103, 0, Material.RED_BED);
        setTeam(arena, world, "blue", 55, 103, 0, Material.BLUE_BED);
        setTeam(arena, world, "green", 0, 103, -55, Material.GREEN_BED);
        setTeam(arena, world, "yellow", 0, 103, 55, Material.YELLOW_BED);
        data.save();
    }

    private void resetArena(String arena) {
        World world = world(arena);
        if (world == null) {
            return;
        }
        double price = entryFee(arena);
        setupPremade(arena, world);
        data.set("arena." + arena + ".price", price);
        data.set("arena." + arena + ".running", false);
        data.save();
    }

    private World world(String arena) {
        String worldName = data.getString("arena." + arena + ".world", "");
        if (worldName.isBlank()) {
            return null;
        }
        loadWorld(worldName);
        return Bukkit.getWorld(worldName);
    }

    private void lobbyCage(World world, ArenaTheme theme) {
        platform(world, 0, 112, 0, 8, theme.floor);
        platform(world, 0, 117, 0, 8, Material.GLASS);
        ring(world, 0, 113, 0, 8, theme.light);
        for (int y = 113; y <= 116; y++) {
            for (int x = -8; x <= 8; x++) {
                set(world, x, y, -8, Material.GLASS);
                set(world, x, y, 8, Material.GLASS);
            }
            for (int z = -8; z <= 8; z++) {
                set(world, -8, y, z, Material.GLASS);
                set(world, 8, y, z, Material.GLASS);
            }
        }
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                set(world, x, 113, z, Material.AIR);
                set(world, x, 114, z, Material.AIR);
                set(world, x, 115, z, Material.AIR);
            }
        }
        platform(world, 0, 112, 0, 6, theme.inner);
        platform(world, 0, 116, 0, 6, Material.GLASS);
    }

    private void teamIsland(World world, String team, int centerX, int y, int centerZ, Material wool, Material bed, Material accent, ArenaTheme theme) {
        disc(world, centerX, y, centerZ, 13, theme.base);
        disc(world, centerX, y + 1, centerZ, 11, theme.floor);
        disc(world, centerX, y + 2, centerZ, 8, wool);
        ring(world, centerX, y + 3, centerZ, 9, theme.light);
        placeBed(world, centerX, y + 3, centerZ, bed, bedFacing(team));
        platform(world, centerX, y + 3, centerZ + 4, 2, Material.OAK_PLANKS);
        pillar(world, centerX - 7, y + 2, centerZ - 7, 5, accent);
        pillar(world, centerX + 7, y + 2, centerZ - 7, 5, accent);
        pillar(world, centerX - 7, y + 2, centerZ + 7, 5, accent);
        pillar(world, centerX + 7, y + 2, centerZ + 7, 5, accent);
        set(world, centerX - 4, y + 3, centerZ, Material.ENDER_CHEST);
        lootChest(world, centerX, y + 3, centerZ + 7, false);
        lootChest(world, centerX + 4, y + 3, centerZ, false);
    }

    private ArenaTheme theme(String arena) {
        ArenaTheme selected = ArenaTheme.parse(data.getString("arena." + arena + ".theme", "crimson"));
        return selected == null ? ArenaTheme.CRIMSON : selected;
    }

    private void setTeam(String arena, World world, String team, int x, int y, int z, Material bed) {
        data.set("arena." + arena + ".spawn." + team, encode(teamIslandSpawn(world, team, x, y, z)));
        data.set("arena." + arena + ".bed." + team, encode(new Location(world, x, y, z)));
        data.set("arena." + arena + ".bedMaterial." + team, bed.name());
        data.set("arena." + arena + ".bedbroken." + team, false);
        placeBed(world, x, y, z, bed, bedFacing(team));
    }

    private Location teamIslandSpawn(World world, String team, int x, int y, int z) {
        int offset = 5;
        return switch (team) {
            case "red" -> new Location(world, x - offset + 0.5D, y + 6.0D, z + 0.5D, -90.0F, 8.0F);
            case "blue" -> new Location(world, x + offset + 0.5D, y + 6.0D, z + 0.5D, 90.0F, 8.0F);
            case "green" -> new Location(world, x + 0.5D, y + 6.0D, z - offset + 0.5D, 0.0F, 8.0F);
            case "yellow" -> new Location(world, x + 0.5D, y + 6.0D, z + offset + 0.5D, 180.0F, 8.0F);
            default -> new Location(world, x + 0.5D, y + 6.0D, z + 0.5D);
        };
    }

    private void clearBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    private void platform(World world, int centerX, int y, int centerZ, int radius, Material material) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                set(world, x, y, z, material);
            }
        }
    }

    private void disc(World world, int centerX, int y, int centerZ, int radius, Material material) {
        int squared = radius * radius;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                if (dx * dx + dz * dz <= squared) {
                    set(world, x, y, z, material);
                }
            }
        }
    }

    private void ring(World world, int centerX, int y, int centerZ, int radius, Material material) {
        int outer = radius * radius;
        int inner = (radius - 1) * (radius - 1);
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                int dx = x - centerX;
                int dz = z - centerZ;
                int distance = dx * dx + dz * dz;
                if (distance <= outer && distance >= inner) {
                    set(world, x, y, z, material);
                }
            }
        }
    }

    private void pillar(World world, int x, int y, int z, int height, Material material) {
        for (int offset = 0; offset < height; offset++) {
            set(world, x, y + offset, z, material);
        }
    }

    private void bridge(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int dx = Integer.compare(x2, x1);
        int dz = Integer.compare(z2, z1);
        int x = x1;
        int z = z1;
        while (x != x2 || z != z2) {
            if (dx != 0) {
                for (int offset = -1; offset <= 1; offset++) {
                    set(world, x, y1, z + offset, material);
                }
                set(world, x, y1 + 1, z - 2, Material.SEA_LANTERN);
                set(world, x, y1 + 1, z + 2, Material.SEA_LANTERN);
            } else {
                for (int offset = -1; offset <= 1; offset++) {
                    set(world, x + offset, y1, z, material);
                }
                set(world, x - 2, y1 + 1, z, Material.SEA_LANTERN);
                set(world, x + 2, y1 + 1, z, Material.SEA_LANTERN);
            }
            if (x != x2) {
                x += dx;
            }
            if (z != z2) {
                z += dz;
            }
        }
    }

    private void placeBed(World world, int x, int y, int z, Material bed, String facing) {
        int[] head = bedHeadOffset(facing);
        set(world, x, y, z, Material.AIR);
        set(world, x + head[0], y, z + head[1], Material.AIR);
        setBedBlock(world, x, y, z, bed, facing, "FOOT");
        setBedBlock(world, x + head[0], y, z + head[1], bed, facing, "HEAD");
    }

    private void setBedBlock(World world, int x, int y, int z, Material bed, String facing, String part) {
        org.bukkit.block.Block block = new Location(world, x, y, z).getBlock();
        try {
            Object blockData = Bukkit.createBlockData(bed);
            Class<?> bedClass = Class.forName("org.bukkit.block.data.type.Bed");
            Class<?> partClass = Class.forName("org.bukkit.block.data.type.Bed$Part");
            Class<?> directionalClass = Class.forName("org.bukkit.block.data.Directional");
            Class<?> blockFaceClass = Class.forName("org.bukkit.block.BlockFace");
            Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
            Class<?> blockClass = Class.forName("org.bukkit.block.Block");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object partValue = Enum.valueOf((Class<? extends Enum>) partClass.asSubclass(Enum.class), part);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object faceValue = Enum.valueOf((Class<? extends Enum>) blockFaceClass.asSubclass(Enum.class), facing);
            bedClass.getMethod("setPart", partClass).invoke(blockData, partValue);
            directionalClass.getMethod("setFacing", blockFaceClass).invoke(blockData, faceValue);
            blockClass.getMethod("setBlockData", blockDataClass, boolean.class).invoke(block, blockData, false);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            block.setType(bed);
        }
    }

    private void lootChest(World world, int x, int y, int z, boolean center) {
        set(world, x, y, z, Material.CHEST);
        if (new Location(world, x, y, z).getBlock().getState() instanceof Chest chest) {
            chest.getInventory().clear();
            int rolls = center ? 6 + random.nextInt(5) : 4 + random.nextInt(4);
            for (int roll = 0; roll < rolls; roll++) {
                chest.getInventory().addItem(randomLoot(center));
            }
        }
    }

    private void refillRunningArenaChests() {
        for (String arena : arenaNames()) {
            if (isRunning(arena) && playersInGame(arena) > 0) {
                refillArenaChests(arena);
                broadcast(arena, "&bBedWars chests zijn opnieuw gevuld.");
                playArenaSound(arena, Sound.BLOCK_CHEST_CLOSE, 0.7F, 1.4F);
            }
        }
    }

    private void refillArenaChests(String arena) {
        World world = world(arena);
        if (world == null) {
            return;
        }
        lootChest(world, 0, 104, 0, true);
        lootChest(world, 6, 103, 0, true);
        lootChest(world, -6, 103, 0, true);
        lootChest(world, -55, 103, 7, false);
        lootChest(world, -51, 103, 0, false);
        lootChest(world, 55, 103, 7, false);
        lootChest(world, 59, 103, 0, false);
        lootChest(world, 0, 103, -48, false);
        lootChest(world, 4, 103, -55, false);
        lootChest(world, 0, 103, 62, false);
        lootChest(world, 4, 103, 55, false);
    }

    private ItemStack randomLoot(boolean center) {
        int roll = random.nextInt(100);
        if (center && roll < 4) {
            return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        }
        if (roll < (center ? 18 : 10)) {
            return new ItemStack(Material.GOLDEN_APPLE, center ? 1 + random.nextInt(3) : 1);
        }
        if (roll < (center ? 35 : 24)) {
            return randomWeapon(center);
        }
        if (roll < (center ? 50 : 40)) {
            return new ItemStack(Material.ARROW, center ? 12 + random.nextInt(21) : 8 + random.nextInt(17));
        }
        if (roll < (center ? 68 : 64)) {
            return new ItemStack(Material.GOLD_INGOT, center ? 6 + random.nextInt(15) : 2 + random.nextInt(8));
        }
        if (roll < (center ? 84 : 86)) {
            return new ItemStack(Material.IRON_INGOT, center ? 12 + random.nextInt(25) : 8 + random.nextInt(17));
        }
        if (roll < 94) {
            return new ItemStack(Material.WHITE_WOOL, center ? 32 + random.nextInt(33) : 16 + random.nextInt(33));
        }
        return new ItemStack(Material.DIAMOND, center ? 1 + random.nextInt(5) : 1);
    }

    private ItemStack randomWeapon(boolean center) {
        int roll = random.nextInt(4);
        Material material = switch (roll) {
            case 0 -> center ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
            case 1 -> center ? Material.DIAMOND_AXE : Material.IRON_SWORD;
            case 2 -> Material.BOW;
            default -> Material.WOODEN_SWORD;
        };
        ItemStack item = new ItemStack(material);
        if (center || random.nextDouble() < 0.35D) {
            enchantLoot(item, center);
        }
        return item;
    }

    private void enchantLoot(ItemStack item, boolean center) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int level = center ? 2 + random.nextInt(4) : 1 + random.nextInt(2);
        if (item.getType() == Material.BOW) {
            meta.setDisplayName(Text.color(center ? "&dCenter Bow" : "&aLoot Bow"));
            meta.addEnchant(Enchantment.POWER, level + 1, true);
            if (random.nextBoolean()) {
                meta.addEnchant(Enchantment.PUNCH, 1, true);
            }
        } else {
            meta.setDisplayName(Text.color(center ? "&dCenter Blade" : "&aLoot Blade"));
            meta.addEnchant(Enchantment.SHARPNESS, level, true);
            if (center && random.nextBoolean()) {
                meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            }
        }
        meta.addEnchant(Enchantment.UNBREAKING, 2 + level, true);
        item.setItemMeta(meta);
    }

    private void set(World world, int x, int y, int z, Material material) {
        new Location(world, x, y, z).getBlock().setType(material, false);
    }

    private boolean isPlayerPlaceable(Material material) {
        String name = material.name();
        return name.endsWith("_WOOL")
            || material == Material.OAK_PLANKS
            || material == Material.END_STONE
            || material == Material.OBSIDIAN;
    }

    private String bedFacing(String team) {
        return switch (team) {
            case "blue" -> "WEST";
            case "green" -> "SOUTH";
            case "yellow" -> "NORTH";
            default -> "EAST";
        };
    }

    private int[] bedHeadOffset(String facing) {
        return switch (facing) {
            case "WEST" -> new int[] {-1, 0};
            case "NORTH" -> new int[] {0, -1};
            case "SOUTH" -> new int[] {0, 1};
            default -> new int[] {1, 0};
        };
    }

    private boolean isUnbrokenBedBlock(String arena, Location location) {
        for (String team : List.of("red", "blue", "green", "yellow")) {
            if (!isBedBroken(arena, team) && isBedBlock(arena, team, location)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBedBlock(String arena, String team, Location location) {
        Location bed = decode(data.getString("arena." + arena + ".bed." + team, ""));
        if (bed == null || location == null || bed.getWorld() == null || location.getWorld() == null || !bed.getWorld().getName().equals(location.getWorld().getName())) {
            return false;
        }
        int[] head = bedHeadOffset(bedFacing(team));
        int bx = bed.getBlockX();
        int by = (int) Math.floor(bed.getY());
        int bz = bed.getBlockZ();
        int lx = location.getBlockX();
        int ly = (int) Math.floor(location.getY());
        int lz = location.getBlockZ();
        return ly == by && ((lx == bx && lz == bz) || (lx == bx + head[0] && lz == bz + head[1]));
    }

    private void repairUnbrokenBeds(String arena) {
        World world = world(arena);
        if (world == null) {
            return;
        }
        for (String team : List.of("red", "blue", "green", "yellow")) {
            if (isBedBroken(arena, team)) {
                continue;
            }
            Location bed = decode(data.getString("arena." + arena + ".bed." + team, ""));
            Material material = material(data.getString("arena." + arena + ".bedMaterial." + team, defaultBedMaterial(team).name()));
            if (bed != null && material != null) {
                int[] head = bedHeadOffset(bedFacing(team));
                int x = bed.getBlockX();
                int y = (int) Math.floor(bed.getY());
                int z = bed.getBlockZ();
                Material foot = new Location(world, x, y, z).getBlock().getType();
                Material headType = new Location(world, x + head[0], y, z + head[1]).getBlock().getType();
                if (foot != material || headType != material) {
                    placeBed(world, x, y, z, material, bedFacing(team));
                }
            }
        }
    }

    private void repairActiveArenaBeds() {
        for (String arena : arenaNames()) {
            if (playersInArena(arena) > 0 || isRunning(arena) || startingArenas.contains(arena)) {
                if (!pendingBedRepairs.add(arena)) {
                    continue;
                }
                try {
                    repairUnbrokenBeds(arena);
                } finally {
                    pendingBedRepairs.remove(arena);
                }
            }
        }
    }

    private String arenaFromWorld(World world) {
        if (world == null) {
            return null;
        }
        String worldName = world.getName();
        if (worldName.startsWith("bedwars_")) {
            String arena = safe(worldName.substring("bedwars_".length()));
            return data.contains("arena." + arena + ".world") ? arena : null;
        }
        return null;
    }

    private Material defaultBedMaterial(String team) {
        return switch (team) {
            case "blue" -> Material.BLUE_BED;
            case "green" -> Material.GREEN_BED;
            case "yellow" -> Material.YELLOW_BED;
            default -> Material.RED_BED;
        };
    }

    private Material material(String input) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String bedTeam(String arena, Location location) {
        for (String team : List.of("red", "blue", "green", "yellow")) {
            if (isBedBlock(arena, team, location)) {
                return team;
            }
        }
        return null;
    }

    private void clearBed(String arena, String team) {
        Location bed = decode(data.getString("arena." + arena + ".bed." + team, ""));
        if (bed == null || bed.getWorld() == null) {
            return;
        }
        int[] head = bedHeadOffset(bedFacing(team));
        set(bed.getWorld(), bed.getBlockX(), (int) Math.floor(bed.getY()), bed.getBlockZ(), Material.AIR);
        set(bed.getWorld(), bed.getBlockX() + head[0], (int) Math.floor(bed.getY()), bed.getBlockZ() + head[1], Material.AIR);
    }

    private void broadcast(String arena, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (arena.equals(activeArena.get(player.getUniqueId()))) {
                Text.msg(player, message);
            }
        }
    }

    private void loadWorld(String name) {
        if (Bukkit.getWorld(name) != null) {
            return;
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
            getLogger().warning("Could not create BedWars world " + name + ": " + exception.getMessage());
        }
    }

    private String encode(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ() + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private Location decode(String encoded) {
        String[] parts = encoded.split(";");
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            loadWorld(parts[0]);
            world = Bukkit.getWorld(parts[0]);
        }
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
            String part = parts[i];
            if (part == null || part.isBlank() || part.equals("-")) {
                continue;
            }
            try {
                contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(part));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Could not load BedWars saved inventory slot " + i + ": " + exception.getMessage());
            }
        }
        return contents;
    }

    private String safe(String input) {
        String safe = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return safe.isBlank() ? "default" : safe;
    }

    private static final class TraderMenu implements InventoryHolder {
        private final Map<Integer, TraderOffer> offers = new HashMap<>();
        private Inventory inventory;

        void offer(int slot, TraderOffer offer) {
            offers.put(slot, offer);
        }

        TraderOffer offer(int slot) {
            return offers.get(slot);
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record TraderOffer(String id, Material icon, int amount, Material currency, int cost, String title) {
    }

    private enum ArenaTheme {
        CRIMSON("crimson", Material.OBSIDIAN, Material.BLACKSTONE, Material.RED_WOOL, Material.SEA_LANTERN, Material.GOLD_BLOCK, Material.CRYING_OBSIDIAN),
        EMBER("ember", Material.BASALT, Material.NETHERRACK, Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.GOLD_BLOCK, Material.MAGMA_BLOCK),
        RELIC("relic", Material.OBSIDIAN, Material.QUARTZ_BLOCK, Material.END_STONE, Material.SEA_LANTERN, Material.DIAMOND_BLOCK, Material.GOLD_BLOCK);

        private final String key;
        private final Material base;
        private final Material floor;
        private final Material inner;
        private final Material light;
        private final Material relic;
        private final Material accent;

        ArenaTheme(String key, Material base, Material floor, Material inner, Material light, Material relic, Material accent) {
            this.key = key;
            this.base = base;
            this.floor = floor;
            this.inner = inner;
            this.light = light;
            this.relic = relic;
            this.accent = accent;
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

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5D, 113.0D, 0.5D);
        }
    }
}



