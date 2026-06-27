package nl.mitchsmp.essentials;

import java.lang.reflect.Constructor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.EconomyWatchService;
import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.InventorySnapshotService;
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
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class EssentialsPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String JAIL_WORLD = "mitchsmp_jail";
    private static final String TEST_WORLD_PREFIX = "mitchtest_";
    private static final String AUDIT_OWNER_UUID_KEY = "owner.audit.uuid";
    private static final int AUDIT_MAX = 5000;
    private static final int SHOP_SIZE = 54;
    private static final int SHOP_PRODUCT_SLOTS = 45;
    private static final Set<String> PUBLIC_ROOT_COMMANDS = Set.of(
        "spawn", "starterkit", "kit", "menu", "m", "help", "?", "bloodhelp", "shop", "commands", "cmds", "mcommands"
    );
    private static final Map<String, String> ROOT_COMMAND_PERMISSIONS = Map.ofEntries(
        Map.entry("spawn", "mitchsmp.essentials.spawn"),
        Map.entry("setspawn", "mitchsmp.essentials.admin"),
        Map.entry("trash", "mitchsmp.essentials.trash"),
        Map.entry("bin", "mitchsmp.essentials.trash"),
        Map.entry("starterkit", "mitchsmp.essentials.spawn"),
        Map.entry("kit", "mitchsmp.essentials.spawn"),
        Map.entry("menu", "mitchsmp.essentials.spawn"),
        Map.entry("m", "mitchsmp.essentials.spawn"),
        Map.entry("help", "mitchsmp.essentials.spawn"),
        Map.entry("?", "mitchsmp.essentials.spawn"),
        Map.entry("bloodhelp", "mitchsmp.essentials.spawn"),
        Map.entry("shop", "mitchsmp.essentials.spawn"),
        Map.entry("commands", "mitchsmp.essentials.spawn"),
        Map.entry("cmds", "mitchsmp.essentials.spawn"),
        Map.entry("mcommands", "mitchsmp.essentials.spawn"),
        Map.entry("adminmode", "mitchsmp.staffmode"),
        Map.entry("staffmode", "mitchsmp.staffmode"),
        Map.entry("admin", "mitchsmp.essentials.admin"),
        Map.entry("adminui", "mitchsmp.essentials.admin"),
        Map.entry("heal", "mitchsmp.essentials.admin"),
        Map.entry("feed", "mitchsmp.essentials.admin"),
        Map.entry("fly", "mitchsmp.essentials.admin"),
        Map.entry("gamemode", "mitchsmp.essentials.admin"),
        Map.entry("gm", "mitchsmp.essentials.admin"),
        Map.entry("day", "mitchsmp.essentials.admin"),
        Map.entry("night", "mitchsmp.essentials.admin"),
        Map.entry("sun", "mitchsmp.essentials.admin"),
        Map.entry("rain", "mitchsmp.essentials.admin"),
        Map.entry("speed", "mitchsmp.essentials.admin"),
        Map.entry("invsee", "mitchsmp.essentials.admin"),
        Map.entry("enderchest", "mitchsmp.essentials.admin"),
        Map.entry("ec", "mitchsmp.essentials.admin"),
        Map.entry("tp", "mitchsmp.essentials.admin"),
        Map.entry("tphere", "mitchsmp.essentials.admin"),
        Map.entry("clearinventory", "mitchsmp.essentials.admin"),
        Map.entry("ci", "mitchsmp.essentials.admin"),
        Map.entry("back", "mitchsmp.essentials.admin"),
        Map.entry("noclip", "mitchsmp.essentials.admin"),
        Map.entry("fakeores", "mitchsmp.essentials.admin"),
        Map.entry("godtools", "mitchsmp.essentials.admin"),
        Map.entry("freeze", "mitchsmp.essentials.admin"),
        Map.entry("lockdown", "mitchsmp.essentials.admin"),
        Map.entry("release", "mitchsmp.essentials.admin"),
        Map.entry("jail", "mitchsmp.essentials.admin"),
        Map.entry("unjail", "mitchsmp.essentials.admin"),
        Map.entry("vanish", "mitchsmp.essentials.admin"),
        Map.entry("v", "mitchsmp.essentials.admin"),
        Map.entry("model", "mitchsmp.essentials.admin"),
        Map.entry("disguise", "mitchsmp.essentials.admin"),
        Map.entry("morph", "mitchsmp.essentials.admin"),
        Map.entry("modelchanger", "mitchsmp.essentials.admin"),
        Map.entry("lagclear", "mitchsmp.essentials.admin"),
        Map.entry("clearlag", "mitchsmp.essentials.admin"),
        Map.entry("spawnmob", "mitchsmp.essentials.admin"),
        Map.entry("mobspawn", "mitchsmp.essentials.admin"),
        Map.entry("testmob", "mitchsmp.essentials.admin"),
        Map.entry("killall", "mitchsmp.essentials.admin"),
        Map.entry("kilall", "mitchsmp.essentials.admin"),
        Map.entry("clearmobs", "mitchsmp.essentials.admin"),
        Map.entry("testworld", "mitchsmp.essentials.admin"),
        Map.entry("tworld", "mitchsmp.essentials.admin"),
        Map.entry("sandbox", "mitchsmp.essentials.admin"),
        Map.entry("smpworld", "mitchsmp.owner.smpworld"),
        Map.entry("ownerconfirm", "mitchsmp.owner.confirm"),
        Map.entry("serverconfig", "mitchsmp.essentials.admin"),
        Map.entry("confighelp", "mitchsmp.essentials.admin"),
        Map.entry("goals", "mitchsmp.gameplay.use"),
        Map.entry("rookie", "mitchsmp.gameplay.use"),
        Map.entry("rookiecontracts", "mitchsmp.gameplay.use"),
        Map.entry("recoverykit", "mitchsmp.gameplay.use"),
        Map.entry("recovery", "mitchsmp.gameplay.use"),
        Map.entry("report", "mitchsmp.gameplay.use"),
        Map.entry("reports", "mitchsmp.reports.staff"),
        Map.entry("staffprofile", "mitchsmp.reports.staff"),
        Map.entry("playerprofile", "mitchsmp.reports.staff"),
        Map.entry("staffnote", "mitchsmp.reports.staff"),
        Map.entry("notes", "mitchsmp.reports.staff"),
        Map.entry("balance", "mitchsmp.economy.use"),
        Map.entry("bal", "mitchsmp.economy.use"),
        Map.entry("money", "mitchsmp.economy.use"),
        Map.entry("moneytop", "mitchsmp.economy.use"),
        Map.entry("pay", "mitchsmp.economy.pay"),
        Map.entry("quicksell", "mitchsmp.economy.use"),
        Map.entry("sell", "mitchsmp.economy.use"),
        Map.entry("qs", "mitchsmp.economy.use"),
        Map.entry("sellquick", "mitchsmp.economy.use"),
        Map.entry("eco", "mitchsmp.economy.admin"),
        Map.entry("auctionhouse", "mitchsmp.auctionhouse.use"),
        Map.entry("ah", "mitchsmp.auctionhouse.use"),
        Map.entry("ahadmin", "mitchsmp.auctionhouse.admin"),
        Map.entry("bedwars", "mitchsmp.bedwars.play"),
        Map.entry("bw", "mitchsmp.bedwars.play"),
        Map.entry("tntrun", "mitchsmp.tntrun.play"),
        Map.entry("tr", "mitchsmp.tntrun.play"),
        Map.entry("spleef", "mitchsmp.spleef.play"),
        Map.entry("sf", "mitchsmp.spleef.play"),
        Map.entry("skirmish", "mitchsmp.skirmish.play"),
        Map.entry("skirm", "mitchsmp.skirmish.play"),
        Map.entry("skills", "mitchsmp.skills.use"),
        Map.entry("skilltree", "mitchsmp.skills.use"),
        Map.entry("sk", "mitchsmp.skills.use"),
        Map.entry("abilities", "mitchsmp.skills.use"),
        Map.entry("ability", "mitchsmp.skills.use"),
        Map.entry("enchants", "mitchsmp.skills.use"),
        Map.entry("mechanics", "mitchsmp.skills.use"),
        Map.entry("guide", "mitchsmp.skills.use"),
        Map.entry("mguide", "mitchsmp.skills.use"),
        Map.entry("safezone", "mitchsmp.safezones.admin"),
        Map.entry("sz", "mitchsmp.safezones.admin"),
        Map.entry("homes", "mitchsmp.homes.use"),
        Map.entry("home", "mitchsmp.homes.use"),
        Map.entry("sethome", "mitchsmp.homes.use"),
        Map.entry("delhome", "mitchsmp.homes.use"),
        Map.entry("deletehome", "mitchsmp.homes.use"),
        Map.entry("tpa", "mitchsmp.tpa.use"),
        Map.entry("tpaccept", "mitchsmp.tpa.use"),
        Map.entry("tpdeny", "mitchsmp.tpa.use"),
        Map.entry("combat", "mitchsmp.combat.view"),
        Map.entry("hearts", "mitchsmp.lifesteal.view"),
        Map.entry("rtp", "mitchsmp.rtp.use"),
        Map.entry("wild", "mitchsmp.rtp.use"),
        Map.entry("hud", "mitchsmp.hud.use"),
        Map.entry("collection", "mitchsmp.progression.use"),
        Map.entry("clog", "mitchsmp.progression.use"),
        Map.entry("contracts", "mitchsmp.progression.use"),
        Map.entry("orders", "mitchsmp.progression.use"),
        Map.entry("resourceorders", "mitchsmp.progression.use"),
        Map.entry("login", "mitchsmp.progression.use"),
        Map.entry("daily", "mitchsmp.progression.use"),
        Map.entry("explorer", "mitchsmp.progression.use"),
        Map.entry("legacy", "mitchsmp.progression.use"),
        Map.entry("relic", "mitchsmp.progression.use"),
        Map.entry("progression", "mitchsmp.progression.use"),
        Map.entry("endboss", "mitchsmp.endboss.use"),
        Map.entry("hellboss", "mitchsmp.endboss.use"),
        Map.entry("ritualboss", "mitchsmp.endboss.use"),
        Map.entry("bounty", "mitchsmp.bounties.view"),
        Map.entry("bounties", "mitchsmp.bounties.view"),
        Map.entry("boss", "mitchsmp.bosses.use"),
        Map.entry("bosses", "mitchsmp.bosses.use"),
        Map.entry("msg", "mitchsmp.chat.msg"),
        Map.entry("tell", "mitchsmp.chat.msg"),
        Map.entry("w", "mitchsmp.chat.msg"),
        Map.entry("reply", "mitchsmp.chat.msg"),
        Map.entry("r", "mitchsmp.chat.msg"),
        Map.entry("staffchat", "mitchsmp.staffchat"),
        Map.entry("sc", "mitchsmp.staffchat"),
        Map.entry("mute", "mitchsmp.chat.mute"),
        Map.entry("unmute", "mitchsmp.chat.mute"),
        Map.entry("anticheat", "mitchsmp.anticheat.admin"),
        Map.entry("ac", "mitchsmp.anticheat.admin"),
        Map.entry("perf", "mitchsmp.performance.admin"),
        Map.entry("performance", "mitchsmp.performance.admin"),
        Map.entry("lagwatch", "mitchsmp.performance.admin"),
        Map.entry("econwatch", "mitchsmp.economy.admin"),
        Map.entry("marketwatch", "mitchsmp.economy.admin"),
        Map.entry("market", "mitchsmp.economy.admin"),
        Map.entry("rank", "mitchsmp.permissions.manage"),
        Map.entry("setrank", "mitchsmp.rank.set"),
        Map.entry("groups", "mitchsmp.permissions.manage"),
        Map.entry("perm", "mitchsmp.permissions.manage"),
        Map.entry("rankperms", "mitchsmp.permissions.manage"),
        Map.entry("cmdperms", "mitchsmp.permissions.manage"),
        Map.entry("sethearts", "mitchsmp.lifesteal.admin"),
        Map.entry("setheart", "mitchsmp.lifesteal.admin"),
        Map.entry("corruptedheart", "mitchsmp.lifesteal.admin"),
        Map.entry("cheart", "mitchsmp.lifesteal.admin"),
        Map.entry("mitchcore", "mitchsmp.core.reload"),
        Map.entry("motd", "mitchsmp.core.motd"),
        Map.entry("features", "mitchsmp.features.admin"),
        Map.entry("errors", "mitchsmp.errors.view"),
        Map.entry("maintenance", "mitchsmp.maintenance.admin"),
        Map.entry("qa", "mitchsmp.qa.run"),
        Map.entry("updates", "mitchsmp.updates.view"),
        Map.entry("update", "mitchsmp.updates.view"),
        Map.entry("updater", "mitchsmp.updates.view"),
        Map.entry("custommob", "mitchsmp.custommobs.admin"),
        Map.entry("custommobs", "mitchsmp.custommobs.admin"),
        Map.entry("mobmodel", "mitchsmp.custommobs.admin"),
        Map.entry("rollback", "mitchsmp.recovery.admin"),
        Map.entry("rb", "mitchsmp.recovery.admin"),
        Map.entry("snapshots", "mitchsmp.recovery.admin"),
        Map.entry("event", "mitchsmp.events.admin"),
        Map.entry("season", "mitchsmp.season.view"),
        Map.entry("cosmetics", "mitchsmp.cosmetics.basic"),
        Map.entry("cos", "mitchsmp.cosmetics.basic"),
        Map.entry("emote", "mitchsmp.cosmetics.basic"),
        Map.entry("opshop", "mitchsmp.artifacts.use"),
        Map.entry("bossshop", "mitchsmp.artifacts.use"),
        Map.entry("shardshop", "mitchsmp.artifacts.use"),
        Map.entry("bossshards", "mitchsmp.artifacts.admin"),
        Map.entry("shards", "mitchsmp.artifacts.admin"),
        Map.entry("hub", "mitchsmp.hub.use"),
        Map.entry("serverhub", "mitchsmp.hub.use"),
        Map.entry("navigator", "mitchsmp.hub.use"),
        Map.entry("protect", "mitchsmp.hub.admin"),
        Map.entry("skyblock", "mitchsmp.skyblock.use"),
        Map.entry("island", "mitchsmp.skyblock.use"),
        Map.entry("sb", "mitchsmp.skyblock.use")
    );
    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> noclipModes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> noclipAllowFlights = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> noclipFlyingStates = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> possessedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> fakeOreLocations = new ConcurrentHashMap<>();
    private final Map<String, Long> fakeOreServerBlocks = new ConcurrentHashMap<>();
    private final Map<String, Material> fakeOreOriginalBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, FakeOreSelection> fakeOreSelections = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> lockedModes = new ConcurrentHashMap<>();
    private final Map<UUID, Location> frozenLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> jailedCells = new ConcurrentHashMap<>();
    private final Map<UUID, AuditView> auditViews = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> vanished = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> modelDisguises = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> staffPvpRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> staffPvpRequestExpiries = new ConcurrentHashMap<>();
    private final Map<String, Long> staffPvpSessions = new ConcurrentHashMap<>();
    private final Map<UUID, SmpWorldConfirmation> smpWorldConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, BbSelection> bbSelections = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<List<BbBlockChange>>> bbUndo = new ConcurrentHashMap<>();
    private final Map<UUID, List<BbClipboardBlock>> bbClipboards = new ConcurrentHashMap<>();
    private final Set<UUID> bbEditAllowed = ConcurrentHashMap.newKeySet();
    private final java.util.Random random = new java.util.Random();
    private PropertiesFile data;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("essentials.properties"));
        ensureShopDefaults();
        jailWorld();
        loadJails();
        loadBbEditPermissions();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String command : List.of("spawn", "setspawn", "heal", "feed", "fly", "gamemode", "day", "night", "sun", "rain", "speed", "trash", "admin", "invsee", "enderchest", "tp", "tphere", "clearinventory", "back", "commands", "help", "menu", "noclip", "fakeores", "godtools", "freeze", "lockdown", "release", "jail", "unjail", "adminmode", "staffmode", "vanish", "model", "starterkit", "shop", "lagclear", "spawnmob", "killall", "testworld", "smpworld", "ownerconfirm", "serverconfig")) {
            if (getCommand(command) != null) {
                getCommand(command).setExecutor(this);
                getCommand(command).setTabCompleter(this);
            }
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tickJails, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickModelDisguises, 20L, 20L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (MitchSMP.permissions().isAdminMode(player)) {
                saveAdminInventory(player);
            }
        }
        data.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("spawn")) {
            return spawn(sender);
        }
        if (name.equals("setspawn")) {
            return setSpawn(sender);
        }
        if (name.equals("heal")) {
            return heal(sender, args);
        }
        if (name.equals("feed")) {
            return feed(sender, args);
        }
        if (name.equals("fly")) {
            return fly(sender, args);
        }
        if (name.equals("gamemode")) {
            return gamemode(sender, args);
        }
        if (name.equals("day") || name.equals("night")) {
            return time(sender, name.equals("day") ? 1000L : 13000L, name);
        }
        if (name.equals("sun") || name.equals("rain")) {
            return weather(sender, name.equals("rain"));
        }
        if (name.equals("speed")) {
            return speed(sender, args);
        }
        if (name.equals("trash")) {
            return trash(sender);
        }
        if (name.equals("menu")) {
            return menu(sender);
        }
        if (name.equals("admin")) {
            return openAdmin(sender);
        }
        if (name.equals("invsee")) {
            return invsee(sender, args);
        }
        if (name.equals("enderchest")) {
            return enderchest(sender, args);
        }
        if (name.equals("tp")) {
            return teleportTo(sender, args);
        }
        if (name.equals("tphere")) {
            return teleportHere(sender, args);
        }
        if (name.equals("clearinventory")) {
            return clearInventory(sender, args);
        }
        if (name.equals("back")) {
            return back(sender);
        }
        if (name.equals("commands") || name.equals("help")) {
            return commands(sender, args);
        }
        if (name.equals("noclip")) {
            return noclip(sender, args);
        }
        if (name.equals("fakeores")) {
            return fakeOresCommand(sender, args);
        }
        if (name.equals("godtools")) {
            return godTools(sender, args);
        }
        if (name.equals("freeze")) {
            return freeze(sender, args);
        }
        if (name.equals("lockdown")) {
            return lockdown(sender, args);
        }
        if (name.equals("release")) {
            return release(sender, args);
        }
        if (name.equals("jail")) {
            return jail(sender, args);
        }
        if (name.equals("unjail")) {
            return unjail(sender, args);
        }
        if (name.equals("adminmode") || name.equals("staffmode")) {
            return adminMode(sender, args);
        }
        if (name.equals("vanish")) {
            return vanish(sender, args);
        }
        if (name.equals("model")) {
            return model(sender, args);
        }
        if (name.equals("starterkit")) {
            return starterKit(sender);
        }
        if (name.equals("shop")) {
            return shop(sender, args);
        }
        if (name.equals("lagclear")) {
            return lagClear(sender);
        }
        if (name.equals("spawnmob")) {
            return spawnMob(sender, args);
        }
        if (name.equals("killall")) {
            return killAll(sender, args);
        }
        if (name.equals("testworld")) {
            return testWorld(sender, args);
        }
        if (name.equals("smpworld")) {
            return smpWorld(sender, args);
        }
        if (name.equals("ownerconfirm")) {
            return ownerConfirm(sender, args);
        }
        if (name.equals("serverconfig")) {
            return serverConfig(sender);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!canSeeRootCommand(sender, name)) {
            return List.of();
        }
        if (name.matches("heal|feed|fly|invsee|tphere|clearinventory|noclip|godtools|freeze|lockdown|release|unjail|vanish") && args.length == 1) {
            return Tab.onlinePlayers(args[0]);
        }
        if (name.equals("jail") && args.length == 1) {
            List<String> result = new java.util.ArrayList<>(Tab.onlinePlayers(args[0]));
            result.addAll(Tab.complete(args[0], "visit"));
            return result;
        }
        if (name.equals("adminmode") && args.length == 1) {
            List<String> options = new ArrayList<>(List.of("on", "off", "pvpaccept", "pvpdeny", "pvpstop"));
            if (sender instanceof Player player && MitchSMP.permissions().isAdminMode(player)) {
                options.add("pvp");
            }
            if (sender instanceof Player player && MitchSMP.ranks().getRank(player.getUniqueId()) == MitchRank.OWNER) {
                options.add("override");
            }
            return Tab.complete(args[0], options);
        }
        if (name.equals("adminmode") && args.length == 2 && args[0].equalsIgnoreCase("pvp")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (name.equals("testworld")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "create", "join", "leave", "reset", "list");
            }
            if (args.length == 2 && args[0].matches("(?i)join|reset")) {
                return Tab.complete(args[1], testWorldNames().toArray(String[]::new));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
                return Tab.complete(args[2], "confirm");
            }
        }
        if (name.equals("smpworld")) {
            if (!(sender instanceof Player player) || MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
                return List.of();
            }
            if (args.length == 1) {
                return Tab.complete(args[0], "load", "active");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("load")) {
                return Tab.complete(args[2], "confirm");
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("load")) {
                return Tab.complete(args[3], "confirm");
            }
        }
        if (name.equals("ownerconfirm")) {
            if (!(sender instanceof Player player) || MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
                return List.of();
            }
            return args.length == 1 ? Tab.complete(args[0], "confirm") : List.of();
        }
        if (name.equals("jail") && args.length == 2) {
            if (args[0].equalsIgnoreCase("visit")) {
                return Tab.onlinePlayers(args[1]);
            }
            return Tab.complete(args[1], "5", "15", "30", "60", "0");
        }
        if (name.equals("fakeores")) {
            if (args.length == 1) {
                List<String> result = new java.util.ArrayList<>(Tab.onlinePlayers(args[0]));
                result.addAll(Tab.complete(args[0], "all", "nearby", "cancel", "delete"));
                return result;
            }
            if (args.length == 2) {
                return Tab.complete(args[1], "24", "32", "48", "64", "96");
            }
            if (args.length == 3) {
                return Tab.complete(args[2], "600", "1800", "3600");
            }
        }
        if (name.equals("model")) {
            if (args.length == 1) {
                List<String> result = new ArrayList<>(Tab.complete(args[0], "off", "player"));
                result.addAll(Tab.complete(args[0], java.util.Arrays.stream(EntityType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toArray(String[]::new)));
                return result;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
                return Tab.onlinePlayers(args[1]);
            }
        }
        if (name.equals("spawnmob")) {
            if (args.length == 1) {
                List<String> result = new ArrayList<>(Tab.complete(args[0], "endboss", "miniboss"));
                result.addAll(Tab.complete(args[0], java.util.Arrays.stream(EntityType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toArray(String[]::new)));
                return result;
            }
            if (args.length == 2) {
                return Tab.complete(args[1], "1", "2", "5", "10");
            }
        }
        if (name.equals("tp")) {
            if (args.length == 1) {
                List<String> result = new java.util.ArrayList<>(Tab.onlinePlayers(args[0]));
                result.addAll(Tab.complete(args[0], "~", "0"));
                return result;
            }
            if (args.length >= 2 && args.length <= 4) {
                return Tab.complete(args[args.length - 1], "~", "0", "64", "100");
            }
        }
        if (name.equals("enderchest") && args.length == 1) {
            return Tab.onlinePlayers(args[0]);
        }
        if (name.equals("gamemode")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "survival", "creative", "adventure", "spectator");
            }
            return args.length == 2 ? Tab.onlinePlayers(args[1]) : List.of();
        }
        if (name.equals("speed")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "walk", "fly");
            }
            if (args.length == 2) {
                return Tab.complete(args[1], "1", "2", "3", "5", "10");
            }
            return args.length == 3 ? Tab.onlinePlayers(args[2]) : List.of();
        }
        if ((name.equals("commands") || name.equals("help")) && args.length == 1) {
            return Tab.complete(args[0], "1", "2", "3", "4", "5");
        }
        if (name.equals("menu")) {
            return List.of();
        }
        if (name.equals("shop")) {
            if (args.length == 1) {
                List<String> result = new java.util.ArrayList<>(Tab.complete(args[0], "edit", "set", "remove", "list", "reset", "tab"));
                result.addAll(Tab.complete(args[0], shopTabs().toArray(String[]::new)));
                return result;
            }
            if (args.length == 2 && args[0].matches("(?i)edit|list|remove")) {
                return Tab.complete(args[1], shopTabs().toArray(String[]::new));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                List<String> result = new java.util.ArrayList<>(Tab.complete(args[1], shopTabs().toArray(String[]::new)));
                result.addAll(Tab.complete(args[1], "10", "11", "12", "13", "14", "15", "16"));
                return result;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("tab")) {
                return Tab.complete(args[1], "add", "remove", "rename", "icon", "list");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("tab") && args[1].matches("(?i)remove|rename|icon")) {
                return Tab.complete(args[2], shopTabs().toArray(String[]::new));
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("tab") && args[1].equalsIgnoreCase("icon")) {
                return Tab.complete(args[3], java.util.Arrays.stream(Material.values()).map(Material::name).toList());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set") && isInteger(args[1])) {
                return Tab.complete(args[2], java.util.Arrays.stream(Material.values()).map(Material::name).toList());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                return Tab.complete(args[2], "0", "1", "2", "10", "11", "12", "13", "14", "15", "16");
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
                if (isInteger(args[1])) {
                    return Tab.amounts(args[3]);
                }
                return Tab.complete(args[3], java.util.Arrays.stream(Material.values()).map(Material::name).toList());
            }
            if (args.length == 5 && args[0].equalsIgnoreCase("set")) {
                return Tab.amounts(args[4]);
            }
            if (args.length == 6 && args[0].equalsIgnoreCase("set")) {
                return Tab.complete(args[5], "1", "8", "16", "32", "64");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
                return Tab.amounts(args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
                return Tab.complete(args[3], "1", "8", "16", "32", "64");
            }
        }
        return List.of();
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        Collection<String> commands = commandCollection(event);
        if (player == null || commands == null) {
            return;
        }
        commands.removeIf(command -> !canSeeRootCommand(player, command));
    }

    @SuppressWarnings("unchecked")
    private Collection<String> commandCollection(PlayerCommandSendEvent event) {
        try {
            Object raw = event.getClass().getMethod("getCommands").invoke(event);
            return raw instanceof Collection<?> collection ? (Collection<String>) collection : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private boolean canSeeRootCommand(CommandSender sender, String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return true;
        }
        String original = rawCommand.toLowerCase(Locale.ROOT);
        String command = original;
        int namespace = command.indexOf(':');
        if (namespace >= 0 && namespace + 1 < command.length()) {
            command = command.substring(namespace + 1);
        }
        if (PUBLIC_ROOT_COMMANDS.contains(command)) {
            return true;
        }
        String permission = ROOT_COMMAND_PERMISSIONS.get(command);
        if (permission == null && namespace >= 0 && (original.startsWith("bukkit:") || original.startsWith("minecraft:"))) {
            return false;
        }
        return permission != null && MitchSMP.permissions().has(sender, permission);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (MitchSMP.permissions().isAdminMode(event.getPlayer())) {
            applyAdminMode(event.getPlayer(), false);
            Text.msg(event.getPlayer(), "&cYou are still in admin mode. Use &f/adminmode off &cto play fairly.");
        }
        restoreDanglingTestWorldSession(event.getPlayer());
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (Boolean.TRUE.equals(vanished.get(staff.getUniqueId())) && !canSeeVanish(event.getPlayer())) {
                event.getPlayer().hidePlayer(this, staff);
            }
            if (modelDisguises.containsKey(staff.getUniqueId()) && !event.getPlayer().equals(staff)) {
                event.getPlayer().hidePlayer(this, staff);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopNoclip(event.getPlayer(), false);
        clearModelDisguise(event.getPlayer(), false);
        clearStaffPvp(event.getPlayer(), true);
        if (MitchSMP.permissions().isAdminMode(event.getPlayer())) {
            saveAdminInventory(event.getPlayer());
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String lower = message.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("//") || lower.startsWith("//")) {
            event.setCancelled(true);
            bbEditCommand(player, message.substring(2).trim());
            return;
        }
        if (lower.equals("/staffaudit") || lower.startsWith("/staffaudit ") || lower.equals("/stafflogs") || lower.startsWith("/stafflogs ")) {
            event.setCancelled(true);
            if (!isAuditOwner(player)) {
                Text.msg(player, "&cYou do not have permission.");
                return;
            }
            String filter = "";
            String[] parts = message.split("\\s+");
            if (parts.length >= 2) {
                filter = parts[1].toLowerCase(Locale.ROOT);
            }
            openAudit(player, 0, filter);
            return;
        }
        if (jailedCells.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            Text.msg(player, "&cYou cannot use commands while jailed.");
            audit(player, "jail-command-blocked", message);
            return;
        }
        if (lower.equals("/help") || lower.startsWith("/help ")
            || lower.equals("/?") || lower.startsWith("/? ")
            || lower.equals("/bukkit:?") || lower.startsWith("/bukkit:? ")
            || lower.equals("/minecraft:help") || lower.startsWith("/minecraft:help ")
            || lower.equals("/bukkit:help") || lower.startsWith("/bukkit:help ")) {
            event.setCancelled(true);
            String[] parts = message.trim().split("\\s+");
            String[] args = parts.length <= 1 ? new String[0] : java.util.Arrays.copyOfRange(parts, 1, parts.length);
            commands(player, args);
            return;
        }
        audit(player, "command", message);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (MitchSMP.permissions().isAdminMode(event.getPlayer())) {
            return;
        }
        backLocations.put(event.getPlayer().getUniqueId(), event.getFrom());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        backLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation());
        if (MitchSMP.permissions().isAdminMode(event.getEntity())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            event.getDrops().clear();
            saveAdminInventory(event.getEntity());
            audit(event.getEntity(), "adminmode-death-kept", shortLocation(event.getEntity().getLocation()));
        }
    }

    @EventHandler
    public void onStaffModePvp(EntityDamageByEntityEvent event) {
        Player damager = attackingPlayer(event.getDamager());
        if (!(event.getEntity() instanceof Player victim) || damager == null) {
            return;
        }
        boolean adminInvolved = MitchSMP.permissions().isAdminMode(damager) || MitchSMP.permissions().isAdminMode(victim);
        if (!adminInvolved) {
            return;
        }
        if (isStaffPvpSession(damager, victim)) {
            return;
        }
        event.setCancelled(true);
        if (MitchSMP.permissions().isAdminMode(damager)) {
            Text.msg(damager, "&cStaffmode PvP is disabled. Use &f/staffmode pvp " + victim.getName() + " &cand wait for acceptance.");
        }
    }

    @EventHandler
    public void onJailMobSpawn(CreatureSpawnEvent event) {
        if (event.getLocation() != null && event.getLocation().getWorld() != null && JAIL_WORLD.equals(event.getLocation().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Entity possessed = possessedEntities.get(event.getPlayer().getUniqueId());
        if (possessed != null && noclipModes.containsKey(event.getPlayer().getUniqueId()) && event.getTo() != null) {
            try {
                possessed.teleport(event.getTo());
                possessed.setFireTicks(0);
            } catch (RuntimeException exception) {
                possessedEntities.remove(event.getPlayer().getUniqueId());
                Text.msg(event.getPlayer(), "&cPossession stopped because the entity is no longer valid.");
            }
        }
        Entity disguise = modelDisguises.get(event.getPlayer().getUniqueId());
        if (disguise != null && event.getTo() != null) {
            try {
                disguise.teleport(event.getTo());
                disguise.setFireTicks(0);
            } catch (RuntimeException exception) {
                modelDisguises.remove(event.getPlayer().getUniqueId());
                revealPlayer(event.getPlayer());
                Text.msg(event.getPlayer(), "&cModel disguise stopped because the entity is no longer valid.");
            }
        }
        Location frozen = frozenLocations.get(event.getPlayer().getUniqueId());
        Location jail = jailedCells.get(event.getPlayer().getUniqueId());
        if (jail != null && event.getTo() != null && sameWorld(jail, event.getTo()) && event.getTo().distanceSquared(jail) > 9.0D) {
            event.setCancelled(true);
            event.getPlayer().teleport(jail);
            Text.msg(event.getPlayer(), "&cYou are confined to this jail cell.");
            return;
        }
        if (frozen == null || event.getTo() == null) {
            return;
        }
        if (event.getTo().distanceSquared(frozen) > 0.01D) {
            event.setCancelled(true);
            event.getPlayer().teleport(frozen);
        }
    }

    @EventHandler
    public void onFakeOreBreak(BlockBreakEvent event) {
        Player actor = event.getPlayer();
        if (MitchSMP.permissions().isAdminMode(actor)) {
            audit(actor, "block-break", event.getBlock().getType().name() + " at " + shortLocation(event.getBlock().getLocation()));
            recordAdminTrace(actor, "break", event.getBlock().getLocation(), event.getBlock().getType());
        }
        if (isJailArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cThis jail area is protected.");
            return;
        }
        Player player = event.getPlayer();
        String key = blockKey(event.getBlock().getLocation());
        Long serverExpiresAt = fakeOreServerBlocks.get(key);
        if (serverExpiresAt == null || serverExpiresAt < System.currentTimeMillis()) {
            return;
        }
        Map<String, Long> locations = fakeOreLocations.get(player.getUniqueId());
        boolean target = false;
        if (locations != null) {
            Long expiresAt = locations.remove(key);
            target = expiresAt != null && expiresAt >= System.currentTimeMillis();
        }
        event.setCancelled(true);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.6F);
        alertStaffText((target ? "&c[XRay alert] " : "&e[XRay bait] ") + "&f" + player.getName() + " &7probeerde een fake ore te minen op &f" + shortLocation(event.getBlock().getLocation()) + "&7.");
    }

    @EventHandler
    public void onJailPlace(BlockPlaceEvent event) {
        Player actor = event.getPlayer();
        if (MitchSMP.permissions().isAdminMode(actor)) {
            audit(actor, "block-place", event.getBlock().getType().name() + " at " + shortLocation(event.getBlock().getLocation()));
            recordAdminTrace(actor, "place", event.getBlock().getLocation(), event.getBlock().getType());
        }
        if (shouldBlockAdminWorldDamage(actor, event.getBlock().getType())) {
            event.setCancelled(true);
            Text.msg(actor, "&cAdmin mode cannot place destructive blocks outside the test world.");
            return;
        }
        if (isJailArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cThis jail area is protected.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop() == null ? null : event.getItemDrop().getItemStack();
        audit(player, "drop", itemName(item));
        if (MitchSMP.permissions().isAdminMode(player)) {
            if (isTestWorld(player.getWorld())) {
                return;
            }
            event.setCancelled(true);
            if (event.getItemDrop() != null) {
                event.getItemDrop().remove();
            }
            Text.msg(player, "&cAdmin mode items cannot be dropped outside the test world. Drop was removed.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (handleBbEditWand(event)) {
            return;
        }
        FakeOreSelection selection = fakeOreSelections.get(event.getPlayer().getUniqueId());
        if (selection != null) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                event.setCancelled(true);
                placeSelectedFakeOre(event.getPlayer(), event.getClickedBlock(), selection);
                return;
            }
            if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                Text.msg(event.getPlayer(), "&7Click a block to place a 2x2x2 fake ore vein there. Use &f/fakeores cancel &7to stop.");
            }
        }
        if (MitchSMP.permissions().isAdminMode(event.getPlayer())) {
            audit(event.getPlayer(), "admin-use", event.getAction() + " " + itemName(event.getItem()));
            if (shouldBlockAdminWorldDamage(event.getPlayer(), event.getItem() == null ? null : event.getItem().getType())) {
                event.setCancelled(true);
                Text.msg(event.getPlayer(), "&cAdmin mode cannot ignite or place destructive items outside the test world.");
                return;
            }
        }
    }

    @EventHandler
    public void onNoclipPossess(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (fakeOreSelections.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!noclipModes.containsKey(player.getUniqueId()) || !MitchSMP.permissions().isAdminMode(player)) {
            return;
        }
        Entity target = event.getRightClicked();
        if (target == null || target instanceof Player) {
            return;
        }
        event.setCancelled(true);
        clearSpectatorTarget(player);
        player.teleport(target.getLocation());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0.0F);
        possessedEntities.put(player.getUniqueId(), target);
        Text.msg(player, "&aEntity-possession: &f" + target.getType().name().toLowerCase(Locale.ROOT) + "&7. Flight is off; walk normally to control the entity. Use &f/noclip &7to stop.");
        audit(player, "noclip-possess", target.getType().name() + " " + shortLocation(target.getLocation()));
    }

    @EventHandler
    public void onAdminClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof AuditMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && isAuditOwner(player)) {
                handleAuditClick(player, menu, event.getRawSlot());
            }
            return;
        }
        if (top.getHolder() instanceof AuditCategoryMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && isAuditOwner(player)) {
                handleAuditCategoryClick(player, event.getRawSlot());
            }
            return;
        }
        if (top.getHolder() instanceof MainMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleMainMenuClick(player, menu, event.getRawSlot());
            }
            return;
        }
        if (top.getHolder() instanceof ShopMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                handleShopClick(player, top, event);
            }
            return;
        }
        if (top.getHolder() instanceof FakeOreMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && hasAdmin(player)) {
                handleFakeOreMenuClick(player, menu, event.getRawSlot());
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player && shouldBlockAdminContainerEdit(player, top, event)) {
            event.setCancelled(true);
            Text.msg(player, "&cAdmin mode cannot modify containers outside the test world.");
            audit(player, "admin-container-blocked", event.getView().getTitle() + " slot " + event.getRawSlot());
            return;
        }
        if (event.getWhoClicked() instanceof Player player && MitchSMP.permissions().isAdminMode(player)) {
            audit(player, "inventory-click", event.getView().getTitle() + " slot " + event.getRawSlot() + " item " + itemName(event.getCurrentItem()));
        }
        if (!(top.getHolder() instanceof AdminMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!hasAdmin(player)) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> healPlayer(player);
            case 11 -> feedPlayer(player);
            case 12 -> player.getWorld().setTime(1000L);
            case 13 -> player.getWorld().setTime(13000L);
            case 14 -> player.getWorld().setStorm(false);
            case 15 -> player.getWorld().setStorm(true);
            case 16 -> player.setGameMode(GameMode.CREATIVE);
            case 20 -> player.openInventory(player.getEnderChest());
            case 21 -> player.openInventory(Bukkit.createInventory(null, 54, Text.color("&8Trash")));
            case 22 -> player.setGameMode(GameMode.SURVIVAL);
            case 23 -> {
                boolean enabled = !player.getAllowFlight();
                player.setAllowFlight(enabled);
                if (!enabled) {
                    player.setFlying(false);
                }
            }
            case 24 -> toggleNoclip(player);
            case 25 -> openFakeOreMenu(player, "nearby", 64, 3600);
            case 26 -> giveGodTools(player);
            default -> {
                return;
            }
        }
        Text.msg(player, "&aAdmin actie uitgevoerd.");
    }

    @EventHandler
    public void onAdminDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !MitchSMP.permissions().isAdminMode(player) || isTestWorld(player.getWorld())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!isPlacedContainer(top)) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
            event.setCancelled(true);
            Text.msg(player, "&cAdmin mode cannot drag items into SMP containers.");
            audit(player, "admin-container-drag-blocked", event.getView().getTitle());
        }
    }

    private boolean spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.essentials.spawn")) {
            Text.msg(player, "&cGeen permissie.");
            return true;
        }
        Location location = decode(data.getString("spawn", ""));
        if (location == null) {
            location = player.getWorld().getSpawnLocation();
        }
        player.teleport(location);
        Text.msg(player, "&aGeteleporteerd naar spawn.");
        return true;
    }

    private boolean back(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&c/back is only available in admin mode.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.essentials.back")) {
            Text.msg(player, "&cGeen permissie.");
            return true;
        }
        Location location = backLocations.get(player.getUniqueId());
        if (location == null) {
            Text.msg(player, "&cNo previous location found.");
            return true;
        }
        player.teleport(location);
        Text.msg(player, "&aTerug geteleporteerd.");
        return true;
    }

    private boolean commands(CommandSender sender, String[] args) {
        List<String> commands = commandCatalog(sender);
        int page = args.length >= 1 ? parsePage(args[0]) : 1;
        int pageSize = 8;
        int maxPage = Math.max(1, (commands.size() + pageSize - 1) / pageSize);
        page = Math.max(1, Math.min(maxPage, page));
        Text.msg(sender, "&4Bloodbound commands &7(" + page + "/" + maxPage + ")");
        for (int index = (page - 1) * pageSize; index < Math.min(commands.size(), page * pageSize); index++) {
            Text.msg(sender, "&f" + commands.get(index));
        }
        return true;
    }

    private List<String> commandCatalog(CommandSender sender) {
        List<String> all = List.of(
            "/menu - Open the main Bloodbound menu",
            "/commands [page] - Show commands you can use",
            "/help [page] - Same as /commands",
            "/spawn - Return to spawn",
            "/starterkit - Claim the starter kit",
            "/recoverykit - Claim a cooldown recovery kit when eligible",
            "/goals - Show personal next objectives",
            "/rookie - Open Rookie Contracts",
            "/shop - Open the configurable shop",
            "/sell - Open quick sell",
            "/tpa <player> - Request teleport",
            "/tpaccept - Accept teleport",
            "/tpdeny - Deny teleport",
            "/sethome [name] - Set a home",
            "/home [name] - Teleport home",
            "/homes - List homes",
            "/delhome <name> - Delete a home",
            "/balance [player] - View balance",
            "/pay <player> <amount> - Pay a player",
            "/moneytop - Economy leaderboard",
            "/ah - Open Auction House",
            "/ah sell <price> - Sell held item",
            "/bounty [player] - View bounty",
            "/bounties - Bounty menu/list",
            "/hearts [player] - View heart count",
            "/cosmetics - Open cosmetics",
            "/emote <name> - Use an emote",
            "/hud - Configure HUD",
            "/hud mode <seasonal|overall> - Switch HUD stat mode",
            "/rtp [radius] - Random teleport",
            "/opshop - Open Boss Shard shop",
            "/season - Season status",
            "/event status - Current event status",
            "/bw join [arena] - Join BedWars",
            "/bw leave - Leave BedWars",
            "/tntrun join [arena] - Join TNT Run",
            "/tntrun leave - Leave TNT Run",
            "/spleef join [arena] - Join Spleef",
            "/spleef leave - Leave Spleef",
            "/skirmish [join|leave|start|stats] - Low-stakes PvP practice",
            "/skyblock create|home|leave|reset|info - Skyblock island commands",
            "/msg <player> <message> - Private message",
            "/reply <message> - Reply",
            "/collection - Collection log",
            "/contracts - Contract board",
            "/orders - Resource orders",
            "/login - Daily reward",
            "/explorer [top] - Explorer ranking",
            "/legacy - Legacy and Hall of Fame",
            "/skills - Skilltree",
            "/abilities - Tool abilities",
            "/mechanics - Mechanics guide",
            "/endboss ritual - Endboss ritual guide",
            "/report <player> <reason> - Report a player with context",
            "/staffchat <message> - Staff chat",
            "/reports [open|closed|all] - Review player reports",
            "/staffprofile <player> - Review a player profile",
            "/staffnote <add|view> <player> [note] - Manage staff notes",
            "/admin - Open admin UI",
            "/back - Adminmode-only return",
            "/invsee <player> - Inspect inventory",
            "/enderchest [player] - Inspect ender chest",
            "/tp <player|x y z> - Admin teleport",
            "/tphere <player> - Teleport player to you",
            "/heal [player] - Heal",
            "/feed [player] - Feed",
            "/fly [player] - Toggle flight",
            "/gm <mode> [player] - Change gamemode",
            "/day - Set day",
            "/night - Set night",
            "/sun - Clear weather",
            "/rain - Rain",
            "/speed <walk|fly> <1-10> [player] - Set speed",
            "/rankperms [commands|rank] - View rank permissions",
            "/eco stats - Economy stats",
            "/eco adminbal [player] [amount] - Admin balance",
            "/eco clear <player> confirm - Clear player balance",
            "/eco resetall confirm - Reset all balances",
            "/econwatch [stats|price|top|active] - Economy watcher",
            "/perf [status|config] - Performance monitor",
            "/adminmode [on|off|override] - Staff mode",
            "/staffmode pvp <player> - Request staffmode PvP",
            "/staffmode pvpaccept|pvpdeny|pvpstop - Staffmode PvP response",
            "/testworld create|join|leave|reset|list - Isolated test worlds",
            "//permission <player> yes|no - Owner grants BloodboundEdit access",
            "//wand, //set, //replace, //walls, //copy, //paste, //undo - BloodboundEdit building",
            "/v [player] - Vanish",
            "/noclip [player] - No-clip/admin possession",
            "/fakeores [player|all|nearby|cancel] [radius] [seconds] - Fake ore bait",
            "/freeze <player> - Freeze player",
            "/lockdown <player> - Lock player",
            "/release <player> - Release player",
            "/jail <player> [minutes] - Jail player",
            "/jail visit <player> - Visit jail",
            "/unjail <player> - Release jail",
            "/godtools [player] - Staff intervention tools",
            "/lagclear - Cleanup dropped items"
        );
        List<String> visible = new ArrayList<>();
        for (String line : all) {
            if (line.startsWith("//")) {
                if (sender instanceof Player player && hasBbEditAccess(player)) {
                    visible.add(line);
                }
                continue;
            }
            String root = rootCommand(line);
            if (canSeeRootCommand(sender, root)) {
                visible.add(line);
            }
        }
        return visible;
    }

    private String rootCommand(String commandLine) {
        if (commandLine == null || !commandLine.startsWith("/")) {
            return "";
        }
        String value = commandLine.substring(1);
        int space = value.indexOf(' ');
        return (space >= 0 ? value.substring(0, space) : value).toLowerCase(Locale.ROOT);
    }

    private boolean invsee(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cGebruik: /invsee <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        player.openInventory(target.getInventory());
        return true;
    }

    private boolean enderchest(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : player;
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        player.openInventory(target.getEnderChest());
        return true;
    }

    private boolean teleportTo(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cGebruik: /tp <player> of /tp <x> <y> <z> of /tp <player> <x> <y> <z>");
            return true;
        }
        if (args.length == 3) {
            Location location = coordinates(player.getLocation(), args, 0);
            if (location == null) {
                Text.msg(sender, "&cCoordinaten ongeldig.");
                return true;
            }
            player.teleport(location);
            Text.msg(player, "&aGeteleporteerd naar &f" + block(location) + "&a.");
            return true;
        }
        if (args.length == 4) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Text.msg(sender, "&cSpeler niet online.");
                return true;
            }
            Location location = coordinates(target.getLocation(), args, 1);
            if (location == null) {
                Text.msg(sender, "&cCoordinaten ongeldig.");
                return true;
            }
            target.teleport(location);
            Text.msg(sender, "&a" + target.getName() + " geteleporteerd naar &f" + block(location) + "&a.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        player.teleport(target.getLocation());
        Text.msg(player, "&aGeteleporteerd naar &f" + target.getName() + "&a.");
        return true;
    }

    private Location coordinates(Location base, String[] args, int start) {
        Double x = coordinate(base.getX(), args[start]);
        Double y = coordinate(base.getY(), args[start + 1]);
        Double z = coordinate(base.getZ(), args[start + 2]);
        if (x == null || y == null || z == null) {
            return null;
        }
        return new Location(base.getWorld(), x, y, z, base.getYaw(), base.getPitch());
    }

    private Double coordinate(double base, String input) {
        if (input.equals("~")) {
            return base;
        }
        if (input.startsWith("~")) {
            try {
                return base + Double.parseDouble(input.substring(1));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String block(Location location) {
        return location.getBlockX() + " " + (int) Math.floor(location.getY()) + " " + location.getBlockZ();
    }

    private String blockKey(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private String shortLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private boolean sameWorld(Location a, Location b) {
        return a != null
            && b != null
            && a.getWorld() != null
            && b.getWorld() != null
            && a.getWorld().getName().equals(b.getWorld().getName());
    }

    private void alertStaff(String message) {
        boolean sent = false;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (canReceiveStaffAlerts(staff) || isAuditOwner(staff)) {
                Text.msg(staff, message);
                staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9F, 1.4F);
                sent = true;
            }
        }
        if (!sent) {
            getLogger().warning(Text.stripColorCodes(message));
        }
    }

    private void alertStaffText(String message) {
        boolean sent = false;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (canReceiveStaffAlerts(staff) || isAuditOwner(staff)) {
                Text.msg(staff, message);
                sent = true;
            }
        }
        if (!sent) {
            getLogger().warning(Text.stripColorCodes(message));
        }
    }

    private boolean isStaffMember(Player player) {
        return player != null && MitchSMP.ranks().getRank(player.getUniqueId()).staff();
    }

    private boolean canReceiveStaffAlerts(Player player) {
        return player != null && MitchSMP.ranks().getRank(player.getUniqueId()).weight() >= MitchRank.MODERATOR.weight();
    }

    private boolean canSeeVanish(Player player) {
        return player != null && (isStaffMember(player) || isAuditOwner(player));
    }

    private String senderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "Console";
    }

    private boolean teleportHere(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cGebruik: /tphere <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        target.teleport(player.getLocation());
        Text.msg(player, "&a" + target.getName() + " is naar jou geteleporteerd.");
        return true;
    }

    private boolean clearInventory(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        target.getInventory().clear();
        Text.msg(sender, "&aInventory geleegd van &f" + target.getName() + "&a.");
        return true;
    }

    private boolean noclip(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        toggleNoclip(target);
        Text.msg(sender, "&aNo-clip getoggled for &f" + target.getName() + "&a.");
        return true;
    }

    private void toggleNoclip(Player player) {
        UUID id = player.getUniqueId();
        if (noclipModes.containsKey(id)) {
            stopNoclip(player, true);
            return;
        }
        noclipModes.put(id, player.getGameMode());
        noclipAllowFlights.put(id, player.getAllowFlight());
        noclipFlyingStates.put(id, player.isFlying());
        player.setGameMode(GameMode.SPECTATOR);
        Text.msg(player, "&aNo-clip aan. Klik op een mob/entity om drive-possession te starten.");
    }

    private void stopNoclip(Player player, boolean message) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        GameMode previous = noclipModes.remove(id);
        if (previous == null) {
            return;
        }
        possessedEntities.remove(id);
        clearSpectatorTarget(player);
        player.setGameMode(previous);
        boolean allowFlight = Boolean.TRUE.equals(noclipAllowFlights.remove(id));
        boolean wasFlying = Boolean.TRUE.equals(noclipFlyingStates.remove(id));
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && wasFlying);
        player.setFallDistance(0.0F);
        if (message) {
            Text.msg(player, "&aNo-clip uit.");
        }
    }

    private void clearSpectatorTarget(Player player) {
        if (player == null || player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        try {
            player.setSpectatorTarget(null);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean fakeOresCommand(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
            fakeOreSelections.remove(player.getUniqueId());
            Text.msg(player, "&aFake-ore selection stopped.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            int restored = deleteFakeOres();
            Text.msg(player, "&aRemoved &f" + restored + " &afake ore bait blocks.");
            alertStaffText("&e[XRay bait] &f" + player.getName() + " &7deleted all active fake ore bait.");
            return true;
        }
        String selector = args.length >= 1 ? args[0] : player.getName();
        int radius = args.length >= 2 ? parseInt(args[1], 48, 8, 128) : 48;
        int seconds = args.length >= 3 ? parseInt(args[2], 3600, 60, 7200) : 3600;
        openFakeOreMenu(player, selector, radius, seconds);
        return true;
    }

    private void openFakeOreMenu(Player player, String selector, int radius, int seconds) {
        Inventory inventory = Bukkit.createInventory(new FakeOreMenu(selector, radius, seconds), 27, Text.color("&8Fake Ore Bait"));
        inventory.setItem(10, item(Material.DIAMOND_ORE, "&bDiamond Ore", "&7Overworld diamond bait.", "&7Target: &f" + selector));
        inventory.setItem(11, item(Material.DEEPSLATE_DIAMOND_ORE, "&bDeepslate Diamond", "&7Deep diamond bait.", "&7Target: &f" + selector));
        inventory.setItem(12, item(Material.EMERALD_ORE, "&aEmerald Ore", "&7Emerald bait.", "&7Target: &f" + selector));
        inventory.setItem(13, item(Material.GOLD_ORE, "&6Gold Ore", "&7Gold bait.", "&7Target: &f" + selector));
        inventory.setItem(14, item(Material.IRON_ORE, "&fIron Ore", "&7Iron bait.", "&7Target: &f" + selector));
        inventory.setItem(15, item(Material.DEEPSLATE_IRON_ORE, "&7Deepslate Iron", "&7Deep iron bait.", "&7Target: &f" + selector));
        inventory.setItem(16, item(Material.ANCIENT_DEBRIS, "&5Ancient Debris", "&7Alleen logisch in Nether.", "&7Target: &f" + selector));
        inventory.setItem(22, item(Material.BARRIER, "&cCancel", "&7Sluit zonder selectie."));
        player.openInventory(inventory);
    }

    private void handleFakeOreMenuClick(Player player, FakeOreMenu menu, int slot) {
        Material ore = switch (slot) {
            case 10 -> Material.DIAMOND_ORE;
            case 11 -> Material.DEEPSLATE_DIAMOND_ORE;
            case 12 -> Material.EMERALD_ORE;
            case 13 -> Material.GOLD_ORE;
            case 14 -> Material.IRON_ORE;
            case 15 -> Material.DEEPSLATE_IRON_ORE;
            case 16 -> Material.ANCIENT_DEBRIS;
            default -> null;
        };
        if (ore == null) {
            player.closeInventory();
            return;
        }
        if (ore == Material.ANCIENT_DEBRIS && !isNetherWorld(player.getWorld())) {
            Text.msg(player, "&cAncient Debris bait can only be selected in the Nether.");
            return;
        }
        fakeOreSelections.put(player.getUniqueId(), new FakeOreSelection(menu.selector(), menu.radius(), menu.seconds(), ore));
        player.closeInventory();
        if (!noclipModes.containsKey(player.getUniqueId())) {
            toggleNoclip(player);
        }
        Text.msg(player, "&aFake ore select mode: &f" + pretty(ore) + "&7. Rechtsklik blokken om 2x2x2 bait veins te plaatsen. &f/fakeores cancel &7stopt.");
        audit(player, "fakeores-select", ore.name() + " selector=" + menu.selector());
    }

    private void fakeOres(Player player, int radius, int seconds) {
        Location base = player.getLocation();
        int targetCount = Math.max(260, Math.min(2200, radius * 22));
        int placed = 0;
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        Map<String, Long> locations = fakeOreLocations.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        int minY = Math.max(-58, base.getBlockY() - Math.max(48, radius * 2));
        int maxY = Math.max(minY + 1, base.getBlockY() + 14);
        for (int index = 0; index < targetCount * 3 && placed < targetCount; index++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int y = minY + random.nextInt(maxY - minY + 1);
            int dz = random.nextInt(radius * 2 + 1) - radius;
            Material fake = fakeOreMaterial(player.getWorld(), y);
            int vein = 3 + random.nextInt(6);
            for (int i = 0; i < vein && placed < targetCount; i++) {
                Location location = new Location(player.getWorld(), base.getBlockX() + dx + random.nextInt(5) - 2, y + random.nextInt(3) - 1, base.getBlockZ() + dz + random.nextInt(5) - 2);
                if (sendFakeOre(player, locations, location, fake, expiresAt, seconds)) {
                    placed++;
                }
            }
        }
        int clusterY = Math.max(-58, base.getBlockY() - 12);
        for (int dx = -12; dx <= 12 && placed < targetCount; dx++) {
            for (int dz = -12; dz <= 12 && placed < targetCount; dz++) {
                for (int dy = -5; dy <= 5 && placed < targetCount; dy++) {
                    if (random.nextDouble() > 0.24D) {
                        continue;
                    }
                    Location location = new Location(player.getWorld(), base.getBlockX() + dx, clusterY + dy, base.getBlockZ() + dz);
                    Material fake = fakeOreMaterial(player.getWorld(), clusterY + dy);
                    if (sendFakeOre(player, locations, location, fake, expiresAt, seconds)) {
                        placed++;
                    }
                }
            }
        }
        alertStaff("&e[XRay bait] &f" + placed + " &7fake ores server-side geplaatst rond &f" + player.getName() + "&7.");
    }

    private boolean sendFakeOre(Player player, Map<String, Long> locations, Location location, Material fake, long expiresAt, int seconds) {
        Material real = location.getBlock().getType();
        if (!isFakeOreHost(real)) {
            return false;
        }
        String key = blockKey(location);
        if (locations.containsKey(key)) {
            return false;
        }
        locations.put(key, expiresAt);
        placeServerFakeBlock(location, fake, expiresAt);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Map<String, Long> active = fakeOreLocations.get(player.getUniqueId());
            if (active != null) {
                active.remove(key);
            }
            restoreServerFakeBlock(location, key, expiresAt);
        }, seconds * 20L);
        return true;
    }

    private void placeSelectedFakeOre(Player staff, Block clicked, FakeOreSelection selection) {
        if (clicked == null || clicked.getLocation() == null || clicked.getLocation().getWorld() == null) {
            return;
        }
        List<Player> targets = fakeOreTargets(staff, selection);
        if (targets.isEmpty()) {
            Text.msg(staff, "&cNo valid targets found for &f" + selection.selector() + "&c.");
            return;
        }
        int total = 0;
        long expiresAt = System.currentTimeMillis() + selection.seconds() * 1000L;
        for (Player target : targets) {
            total += sendSelectedFakeVein(target, clicked.getLocation(), selection.ore(), expiresAt, selection.seconds());
        }
        Text.msg(staff, "&aFake vein geplaatst: &f" + pretty(selection.ore()) + " &7voor &f" + targets.size() + " &7target(s), &f" + total + " &7ore blocks.");
        alertStaffText("&e[XRay bait] &f" + staff.getName() + " &7plaatste manual bait &f" + pretty(selection.ore()) + " &7op &f" + shortLocation(clicked.getLocation()) + "&7.");
    }

    private int sendSelectedFakeVein(Player target, Location origin, Material ore, long expiresAt, int seconds) {
        Map<String, Long> locations = fakeOreLocations.computeIfAbsent(target.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        List<Location> restore = new ArrayList<>();
        int ores = 0;
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 2; dz++) {
                    boolean oreBlock = dx >= 0 && dx <= 1 && dy >= 0 && dy <= 1 && dz >= 0 && dz <= 1;
                    Location location = new Location(world, ox + dx, oy + dy, oz + dz);
                    Material real = location.getBlock().getType();
                    if (oreBlock) {
                        if (!isFakeOreHost(real)) {
                            continue;
                        }
                        String key = blockKey(location);
                        locations.put(key, expiresAt);
                        placeServerFakeBlock(location, ore, expiresAt);
                        restore.add(location);
                        ores++;
                    } else if (real != Material.AIR && real != Material.BEDROCK) {
                        rememberOriginalBlock(location);
                        location.getBlock().setType(Material.AIR);
                        restore.add(location);
                    }
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Map<String, Long> active = fakeOreLocations.get(target.getUniqueId());
            for (Location location : restore) {
                if (active != null) {
                    active.remove(blockKey(location));
                }
                restoreServerFakeBlock(location, blockKey(location), expiresAt);
            }
        }, seconds * 20L);
        return ores;
    }

    private void placeServerFakeBlock(Location location, Material fake, long expiresAt) {
        rememberOriginalBlock(location);
        String key = blockKey(location);
        fakeOreServerBlocks.put(key, expiresAt);
        location.getBlock().setType(fake);
    }

    private void rememberOriginalBlock(Location location) {
        fakeOreOriginalBlocks.putIfAbsent(blockKey(location), location.getBlock().getType());
    }

    private void restoreServerFakeBlock(Location location, String key, long expiresAt) {
        Long activeExpiresAt = fakeOreServerBlocks.get(key);
        if (activeExpiresAt != null && activeExpiresAt > expiresAt) {
            return;
        }
        fakeOreServerBlocks.remove(key);
        Material original = fakeOreOriginalBlocks.remove(key);
        if (original == null) {
            return;
        }
        Material current = location.getBlock().getType();
        if (current == Material.AIR || isFakeOreMaterial(current)) {
            location.getBlock().setType(original);
        }
    }

    private int deleteFakeOres() {
        int restored = 0;
        for (String key : new ArrayList<>(fakeOreServerBlocks.keySet())) {
            Location location = locationFromBlockKey(key);
            Material original = fakeOreOriginalBlocks.remove(key);
            fakeOreServerBlocks.remove(key);
            if (location == null || original == null) {
                continue;
            }
            Material current = location.getBlock().getType();
            if (current == Material.AIR || isFakeOreMaterial(current)) {
                location.getBlock().setType(original);
                restored++;
            }
        }
        fakeOreLocations.clear();
        fakeOreSelections.clear();
        return restored;
    }

    private Location locationFromBlockKey(String key) {
        String[] parts = key == null ? new String[0] : key.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isFakeOreMaterial(Material material) {
        return material == Material.DIAMOND_ORE
            || material == Material.DEEPSLATE_DIAMOND_ORE
            || material == Material.EMERALD_ORE
            || material == Material.DEEPSLATE_EMERALD_ORE
            || material == Material.GOLD_ORE
            || material == Material.DEEPSLATE_GOLD_ORE
            || material == Material.IRON_ORE
            || material == Material.DEEPSLATE_IRON_ORE
            || material == Material.ANCIENT_DEBRIS;
    }

    private boolean isNetherWorld(World world) {
        return world != null && world.getName().toLowerCase(Locale.ROOT).contains("nether");
    }

    private List<Player> fakeOreTargets(Player staff, FakeOreSelection selection) {
        String selector = selection.selector();
        List<Player> targets = new ArrayList<>();
        if (selector.equalsIgnoreCase("all")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!MitchSMP.permissions().isAdminRestricted(player) && player.getWorld().equals(staff.getWorld())) {
                    targets.add(player);
                }
            }
            return targets;
        }
        if (selector.equalsIgnoreCase("nearby")) {
            int radius = selection.radius();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!MitchSMP.permissions().isAdminRestricted(player)
                    && player.getWorld().equals(staff.getWorld())
                    && player.getLocation().distanceSquared(staff.getLocation()) <= radius * radius) {
                    targets.add(player);
                }
            }
            return targets;
        }
        Player target = Bukkit.getPlayerExact(selector);
        if (target != null && target.getWorld().equals(staff.getWorld())) {
            targets.add(target);
        }
        return targets;
    }

    private boolean isFakeOreHost(Material material) {
        String name = material.name();
        return material != Material.AIR
            && material != Material.CHEST
            && material != Material.ENDER_CHEST
            && !name.contains("BED")
            && !name.contains("ORE")
            && material != Material.ANCIENT_DEBRIS;
    }

    private Material fakeOreMaterial(World world, int y) {
        String worldName = world == null ? "" : world.getName().toLowerCase(Locale.ROOT);
        if (worldName.contains("nether")) {
            return Material.ANCIENT_DEBRIS;
        }
        boolean deep = y < 0;
        return switch (random.nextInt(7)) {
            case 0, 1, 2 -> deep ? Material.DEEPSLATE_DIAMOND_ORE : Material.DIAMOND_ORE;
            case 3 -> deep ? Material.DEEPSLATE_EMERALD_ORE : Material.EMERALD_ORE;
            case 4 -> deep ? Material.DEEPSLATE_GOLD_ORE : Material.GOLD_ORE;
            default -> deep ? Material.DEEPSLATE_IRON_ORE : Material.IRON_ORE;
        };
    }

    private boolean godTools(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        giveGodTools(target);
        Text.msg(sender, "&aAdmin intervention tools granted to &f" + target.getName() + "&a.");
        return true;
    }

    private void giveGodTools(Player player) {
        player.getInventory().addItem(
            highTool(Material.NETHERITE_SWORD, "&cAdmin Lockdown Blade", Enchantment.SHARPNESS, 10, Enchantment.FIRE_ASPECT, 3, "&7Gebruik met &f/lockdown <player>&7 om een cheater uit te schakelen."),
            highTool(Material.COMPASS, "&bAdmin Freeze Compass", Enchantment.UNBREAKING, 10, Enchantment.MENDING, 1, "&7Gebruik met &f/freeze <player>&7 om beweging te stoppen."),
            highTool(Material.NETHERITE_PICKAXE, "&bAdmin Evidence Extractor", Enchantment.EFFICIENCY, 10, Enchantment.SILK_TOUCH, 1, "&7Inspect suspicious tunnels without replacing tools."),
            highTool(Material.NETHERITE_AXE, "&6Admin Verdict Axe", Enchantment.EFFICIENCY, 9, Enchantment.SHARPNESS, 8, "&7Voor staff-only noodsituaties. Niet als survival reward.")
        );
        Text.msg(player, "&aAdmin intervention tools received. Commands: &f/freeze&7, &f/lockdown&7, &f/release&7, &f/fakeores&a.");
    }

    private ItemStack highTool(Material material, String name, Enchantment first, int firstLevel, Enchantment second, int secondLevel, String extraLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(List.of(Text.color("&7High-end staff/admin tool."), Text.color(extraLore), Text.color("&8Niet bedoeld als normale economy reward.")));
            meta.setUnbreakable(true);
            meta.addEnchant(first, firstLevel, true);
            meta.addEnchant(second, secondLevel, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean freeze(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        UUID id = target.getUniqueId();
        if (frozenLocations.remove(id) != null) {
            Text.msg(sender, "&aFreeze uit for &f" + target.getName() + "&a.");
            Text.msg(target, "&aJe bent niet meer frozen.");
            return true;
        }
        frozenLocations.put(id, target.getLocation());
        target.setAllowFlight(false);
        target.setFlying(false);
        Text.msg(sender, "&cFreeze aan for &f" + target.getName() + "&c.");
        Text.msg(target, "&cJe bent gefreezed door staff. Log niet uit.");
        alertStaff("&e[Staff] &f" + senderName(sender) + " &7freezede &f" + target.getName() + "&7.");
        return true;
    }

    private boolean lockdown(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        UUID id = target.getUniqueId();
        lockedModes.putIfAbsent(id, target.getGameMode());
        frozenLocations.put(id, target.getLocation());
        target.setAllowFlight(false);
        target.setFlying(false);
        target.setGameMode(GameMode.ADVENTURE);
        target.setNoDamageTicks(200);
        Text.msg(sender, "&cLockdown enabled for &f" + target.getName() + "&c.");
        Text.msg(target, "&cStaff lockdown active. You cannot move or exploit.");
        alertStaff("&c[Lockdown] &f" + senderName(sender) + " &7heeft &f" + target.getName() + " &7volledig vastgezet.");
        return true;
    }

    private boolean release(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        UUID id = target.getUniqueId();
        frozenLocations.remove(id);
        GameMode old = lockedModes.remove(id);
        if (old != null) {
            target.setGameMode(old);
        }
        Text.msg(sender, "&aRelease uitgevoerd for &f" + target.getName() + "&a.");
        Text.msg(target, "&aStaff lockdown/freeze is opgeheven.");
        alertStaff("&a[Release] &f" + senderName(sender) + " &7heeft &f" + target.getName() + " &7vrijgegeven.");
        return true;
    }

    private boolean jail(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cUsage: /jail <player> [minutes] or /jail visit <player>");
            return true;
        }
        if (args[0].equalsIgnoreCase("visit")) {
            return visitJail(sender, args);
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        int minutes = args.length >= 2 ? parseInt(args[1], 30, 0, 10080) : 30;
        UUID id = target.getUniqueId();
        captureSnapshot(target, "jail-enter");
        int cell = data.getInt("jail." + id + ".cellIndex", -1);
        if (cell < 0) {
            cell = data.getInt("jail.nextCell", 0);
            data.set("jail.nextCell", cell + 1);
        }
        Location location = jailCell(cell);
        buildJailCell(location);
        data.set("jail." + id + ".cellIndex", cell);
        data.set("jail." + id + ".cell", encode(location));
        data.set("jail." + id + ".return", encode(target.getLocation()));
        data.set("jail." + id + ".mode", target.getGameMode().name());
        data.set("jail." + id + ".until", minutes <= 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L);
        data.save();
        jailedCells.put(id, location);
        frozenLocations.remove(id);
        lockedModes.putIfAbsent(id, target.getGameMode());
        target.setAllowFlight(false);
        target.setFlying(false);
        target.setGameMode(GameMode.ADVENTURE);
        target.teleport(location);
        target.setNoDamageTicks(200);
        Text.msg(sender, "&c" + target.getName() + " is now jailed" + (minutes <= 0 ? " permanently" : " for " + minutes + " minutes") + ".");
        Text.msg(target, "&cYou are in staff jail. Wait for staff or for your timer to expire.");
        alertStaff("&c[Jail] &f" + senderName(sender) + " &7placed &f" + target.getName() + " &7in jail.");
        return true;
    }

    private boolean visitJail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cUsage: /jail visit <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        Location cell = jailedCells.get(target.getUniqueId());
        if (cell == null) {
            cell = decode(data.getString("jail." + target.getUniqueId() + ".cell", ""));
        }
        if (cell == null) {
            Text.msg(sender, "&cThis player is not jailed.");
            return true;
        }
        Location visit = new Location(cell.getWorld(), cell.getX() + 4.5D, cell.getY(), cell.getZ() + 0.5D, -90.0F, 0.0F);
        buildJailCell(cell);
        staff.teleport(visit);
        staff.setFallDistance(0.0F);
        staff.setNoDamageTicks(80);
        Text.msg(staff, "&aJe kijkt nu from buiten de cel van &f" + target.getName() + "&a.");
        audit(staff, "jail-visit", target.getName());
        return true;
    }

    private boolean unjail(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cGebruik: /unjail <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        releaseJail(target, true);
        Text.msg(sender, "&a" + target.getName() + " was released from jail.");
        alertStaff("&a[Jail] &f" + senderName(sender) + " &7released &f" + target.getName() + " &7from jail.");
        return true;
    }

    private boolean adminMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length >= 1 && args[0].matches("(?i)pvpaccept|accept|pvpdeny|deny|pvpstop|stop")) {
            return staffPvpResponse(player, args[0]);
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.staffmode")) {
            Text.msg(player, "&cGeen permissie.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("pvp")) {
            return staffPvpRequest(player, args);
        }
        boolean active = MitchSMP.permissions().isAdminMode(player);
        boolean enable = !active;
        if (args.length >= 1) {
            String mode = args[0].toLowerCase(Locale.ROOT);
            if (mode.equals("override")) {
                return adminModeOverride(player, args);
            }
            if (mode.equals("on") || mode.equals("aan")) {
                enable = true;
            } else if (mode.equals("off") || mode.equals("uit")) {
                enable = false;
            }
        }
        if (enable) {
            enableAdminMode(player);
        } else {
            disableAdminMode(player);
        }
        return true;
    }

    private boolean adminModeOverride(Player player, String[] args) {
        if (MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
            Text.msg(player, "&cOnly the Owner rank can use adminmode override.");
            return true;
        }
        boolean enable = !MitchSMP.permissions().isAdminModeOverride(player);
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("on") || mode.equals("aan")) {
                enable = true;
            } else if (mode.equals("off") || mode.equals("uit")) {
                enable = false;
            }
        }
        if (enable) {
            if (!MitchSMP.permissions().isAdminMode(player)) {
                enableAdminMode(player);
            }
            MitchSMP.permissions().setAdminModeOverride(player, true);
        } else if (MitchSMP.permissions().isAdminMode(player)) {
            disableAdminMode(player);
        } else {
            MitchSMP.permissions().setAdminModeOverride(player, false);
        }
        Text.msg(player, enable
            ? "&6Owner override enabled. Admin restrictions and rollback are bypassed for testing."
            : "&eOwner override disabled. You returned to normal mode.");
        audit(player, enable ? "adminmode-override-on" : "adminmode-override-off", shortLocation(player.getLocation()));
        alertStaff("&6[AdminMode] &f" + player.getName() + " &7set owner override " + (enable ? "on" : "off") + ".");
        return true;
    }

    private boolean staffPvpRequest(Player staff, String[] args) {
        if (!MitchSMP.permissions().isAdminMode(staff)) {
            Text.msg(staff, "&cJe moet in staffmode zijn for een staff PvP test.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(staff, "&cGebruik: /staffmode pvp <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(staff, "&cSpeler niet online.");
            return true;
        }
        if (target.getUniqueId().equals(staff.getUniqueId())) {
            Text.msg(staff, "&cYou cannot send a PvP request to yourself.");
            return true;
        }
        if (MitchSMP.permissions().isAdminMode(target)) {
            Text.msg(staff, "&cThe target is also in staff mode. Disable one staff session or choose a normal player.");
            return true;
        }
        staffPvpRequests.put(target.getUniqueId(), staff.getUniqueId());
        staffPvpRequestExpiries.put(target.getUniqueId(), System.currentTimeMillis() + 60_000L);
        Text.msg(staff, "&aStaff PvP request gestuurd naar &f" + target.getName() + "&a. Geldig for 60 seconden.");
        Text.msg(target, "&c[Staff PvP] &f" + staff.getName() + " &7wil tijdelijk PvP met jou testen.");
        Text.msg(target, "&7Accepteer met &f/staffmode pvpaccept &7of weiger met &f/staffmode pvpdeny&7.");
        audit(staff, "staff-pvp-request", target.getName());
        alertStaff("&e[Staff PvP] &f" + staff.getName() + " &7vroeg PvP consent aan &f" + target.getName() + "&7.");
        return true;
    }

    private boolean staffPvpResponse(Player player, String action) {
        if (action.equalsIgnoreCase("pvpstop") || action.equalsIgnoreCase("stop")) {
            boolean stopped = clearStaffPvp(player, true);
            Text.msg(player, stopped ? "&aStaff PvP session stopped." : "&7You have no active staff PvP session.");
            return true;
        }
        UUID staffId = staffPvpRequests.remove(player.getUniqueId());
        Long expiresAt = staffPvpRequestExpiries.remove(player.getUniqueId());
        if (staffId == null || expiresAt == null || expiresAt < System.currentTimeMillis()) {
            Text.msg(player, "&cJe hebt geen actieve staff PvP aanvraag.");
            return true;
        }
        Player staff = Bukkit.getPlayer(staffId);
        if (staff == null || !MitchSMP.permissions().isAdminMode(staff)) {
            Text.msg(player, "&cDe staff PvP aanvraag is verlopen.");
            return true;
        }
        if (action.equalsIgnoreCase("pvpdeny") || action.equalsIgnoreCase("deny")) {
            Text.msg(player, "&7Staff PvP aanvraag geweigerd.");
            Text.msg(staff, "&c" + player.getName() + " weigerde je staff PvP aanvraag.");
            audit(staff, "staff-pvp-denied", player.getName());
            return true;
        }
        String key = pvpKey(staff.getUniqueId(), player.getUniqueId());
        staffPvpSessions.put(key, System.currentTimeMillis() + 10 * 60_000L);
        Text.msg(player, "&aStaff PvP met &f" + staff.getName() + " &ais allowed for 10 minutes. Stop met &f/staffmode pvpstop&a.");
        Text.msg(staff, "&a" + player.getName() + " accepteerde. PvP tussen jullie is allowed for 10 minutes.");
        audit(staff, "staff-pvp-accepted", player.getName());
        alertStaff("&c[Staff PvP] &f" + player.getName() + " &7accepteerde PvP met &f" + staff.getName() + "&7.");
        return true;
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isStaffPvpSession(Player first, Player second) {
        String key = pvpKey(first.getUniqueId(), second.getUniqueId());
        Long expiresAt = staffPvpSessions.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            staffPvpSessions.remove(key);
            Text.msg(first, "&7Staff PvP sessie is verlopen.");
            Text.msg(second, "&7Staff PvP sessie is verlopen.");
            return false;
        }
        return true;
    }

    private boolean clearStaffPvp(Player player, boolean notifyOther) {
        boolean changed = staffPvpRequests.remove(player.getUniqueId()) != null;
        changed = staffPvpRequestExpiries.remove(player.getUniqueId()) != null || changed;
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(staffPvpRequests.entrySet())) {
            if (entry.getValue().equals(player.getUniqueId())) {
                staffPvpRequests.remove(entry.getKey());
                staffPvpRequestExpiries.remove(entry.getKey());
                changed = true;
            }
        }
        for (String key : new ArrayList<>(staffPvpSessions.keySet())) {
            if (!key.contains(player.getUniqueId().toString())) {
                continue;
            }
            staffPvpSessions.remove(key);
            changed = true;
            if (notifyOther) {
                Player other = otherPvpParticipant(key, player.getUniqueId());
                if (other != null) {
                    Text.msg(other, "&7The staff PvP session with &f" + player.getName() + " &7has ended.");
                }
            }
        }
        return changed;
    }

    private Player otherPvpParticipant(String key, UUID self) {
        String[] parts = key.split(":");
        if (parts.length != 2) {
            return null;
        }
        UUID first = UUID.fromString(parts[0]);
        UUID second = UUID.fromString(parts[1]);
        return Bukkit.getPlayer(first.equals(self) ? second : first);
    }

    private String pvpKey(UUID first, UUID second) {
        String a = first.toString();
        String b = second.toString();
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private void enableAdminMode(Player player) {
        if (MitchSMP.permissions().isAdminMode(player)) {
            applyAdminMode(player, false);
            Text.msg(player, "&cJe bent al in admin mode.");
            return;
        }
        UUID id = player.getUniqueId();
        captureSnapshot(player, "adminmode-enter");
        data.set("adminmode." + id + ".playInventory", encodeInventory(player.getInventory().getContents()));
        data.set("adminmode." + id + ".playMode", player.getGameMode().name());
        data.set("adminmode." + id + ".playAllowFlight", player.getAllowFlight());
        data.set("adminmode." + id + ".playFlying", player.isFlying());
        data.set("adminmode." + id + ".playLocation", encode(player.getLocation()));
        data.save();
        MitchSMP.permissions().setAdminMode(player, true);
        applyAdminMode(player, true);
        audit(player, "adminmode-on", shortLocation(player.getLocation()));
        alertStaff("&c[AdminMode] &f" + player.getName() + " &7ging in admin mode.");
    }

    private void disableAdminMode(Player player) {
        if (!MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&7Je bent niet in admin mode.");
            return;
        }
        UUID id = player.getUniqueId();
        clearModelDisguise(player, false);
        captureSnapshot(player, "adminmode-exit-staff-inventory");
        saveAdminInventory(player);
        boolean override = MitchSMP.permissions().isAdminModeOverride(player);
        if (override) {
            clearAdminTrace(player);
        } else {
            rollbackAdminTrace(player);
        }
        ItemStack[] playInventory = decodeInventory(data.getString("adminmode." + id + ".playInventory", ""), player.getInventory().getSize());
        player.getInventory().clear();
        player.getInventory().setContents(playInventory);
        GameMode mode = GameMode.SURVIVAL;
        try {
            mode = GameMode.valueOf(data.getString("adminmode." + id + ".playMode", "SURVIVAL"));
        } catch (IllegalArgumentException ignored) {
        }
        player.setGameMode(mode);
        player.setAllowFlight(data.getString("adminmode." + id + ".playAllowFlight", "false").equalsIgnoreCase("true"));
        player.setFlying(data.getString("adminmode." + id + ".playFlying", "false").equalsIgnoreCase("true"));
        Location playLocation = decode(data.getString("adminmode." + id + ".playLocation", ""));
        if (playLocation != null) {
            player.teleport(playLocation);
            player.setFallDistance(0.0F);
            player.setNoDamageTicks(120);
        }
        data.set("adminmode." + id + ".playInventory", null);
        data.set("adminmode." + id + ".playMode", null);
        data.set("adminmode." + id + ".playAllowFlight", null);
        data.set("adminmode." + id + ".playFlying", null);
        data.set("adminmode." + id + ".playLocation", null);
        data.save();
        MitchSMP.permissions().setAdminMode(player, false);
        clearStaffPvp(player, true);
        audit(player, "adminmode-off", playLocation == null ? "unknown return" : shortLocation(playLocation));
        Text.msg(player, override
            ? "&6Owner override disabled. Your normal SMP inventory has been restored; rollback was skipped."
            : "&aAdmin mode disabled. Your normal SMP inventory has been restored.");
        alertStaff("&a[AdminMode] &f" + player.getName() + " &7returned to play mode.");
        captureSnapshot(player, "adminmode-exit-survival-restored");
    }

    private void captureSnapshot(Player player, String trigger) {
        InventorySnapshotService service = MitchSMP.snapshots();
        if (service != null) {
            service.capture(player, trigger);
        }
    }

    private void applyAdminMode(Player player, boolean fresh) {
        UUID id = player.getUniqueId();
        player.getInventory().clear();
        ItemStack[] inventory = decodeInventory(data.getString("adminmode." + id + ".staffInventory", ""), player.getInventory().getSize());
        if (hasAnyItem(inventory)) {
            player.getInventory().setContents(inventory);
        } else {
            giveGodTools(player);
        }
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setNoDamageTicks(200);
        Text.msg(player, fresh
            ? "&cAdmin mode aan. Inventory, permissions en mechanics zijn gescheiden van je SMP account."
            : "&cAdmin mode active. Your staff inventory is loaded.");
    }

    private void saveAdminInventory(Player player) {
        data.set("adminmode." + player.getUniqueId() + ".staffInventory", encodeInventory(player.getInventory().getContents()));
        data.save();
    }

    private boolean hasAnyItem(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean vanish(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        boolean enable = !Boolean.TRUE.equals(vanished.get(target.getUniqueId()));
        vanished.put(target.getUniqueId(), enable);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            if (enable && !canSeeVanish(viewer)) {
                viewer.hidePlayer(this, target);
            } else {
                viewer.showPlayer(this, target);
            }
        }
        Text.msg(target, enable ? "&aVanish aan." : "&cVanish uit.");
        alertStaff((enable ? "&7[Vanish] &f" : "&7[Vanish] &f") + target.getName() + (enable ? " &7is vanished." : " &7is zichtbaar."));
        audit(target, enable ? "vanish-on" : "vanish-off", senderName(sender));
        return true;
    }

    private boolean model(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&cUse admin mode before changing your staff model.");
            return true;
        }
        if (args.length == 0) {
            Text.msg(player, "&cUsage: /model <mob|off> or /model player <name>");
            return true;
        }
        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("clear")) {
            clearModelDisguise(player, true);
            return true;
        }
        if (args[0].equalsIgnoreCase("player")) {
            Text.msg(player, "&cPlayer-skin disguises need packet/client model support. Mob disguises are available with &f/model <mob>&c.");
            return true;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Text.msg(player, "&cUnknown model. Use tab completion for mob types.");
            return true;
        }
        if (type == EntityType.ARMOR_STAND) {
            Text.msg(player, "&cArmor stands are not useful as staff models.");
            return true;
        }
        clearModelDisguise(player, false);
        Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);
        makeDisguiseEntitySafe(entity, player);
        modelDisguises.put(player.getUniqueId(), entity);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.hidePlayer(this, player);
            }
        }
        Text.msg(player, "&aStaff model changed to &f" + type.name().toLowerCase(Locale.ROOT) + "&a. Use &f/model off&a to clear.");
        audit(player, "model-disguise", type.name());
        return true;
    }

    private void clearModelDisguise(Player player, boolean message) {
        Entity entity = modelDisguises.remove(player.getUniqueId());
        if (entity != null) {
            entity.remove();
        }
        revealPlayer(player);
        if (message) {
            Text.msg(player, "&aStaff model cleared.");
        }
    }

    private void tickModelDisguises() {
        for (Map.Entry<UUID, Entity> entry : new ArrayList<>(modelDisguises.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Entity entity = entry.getValue();
            if (player == null || entity == null) {
                if (entity != null) {
                    entity.remove();
                }
                modelDisguises.remove(entry.getKey());
                continue;
            }
            try {
                entity.teleport(player.getLocation());
                entity.setFireTicks(0);
            } catch (RuntimeException exception) {
                modelDisguises.remove(entry.getKey());
                revealPlayer(player);
            }
        }
    }

    private void revealPlayer(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            if (Boolean.TRUE.equals(vanished.get(player.getUniqueId())) && !canSeeVanish(viewer)) {
                viewer.hidePlayer(this, player);
            } else {
                viewer.showPlayer(this, player);
            }
        }
    }

    private void makeDisguiseEntitySafe(Entity entity, Player owner) {
        try {
            entity.getClass().getMethod("setInvulnerable", boolean.class).invoke(entity, true);
            entity.getClass().getMethod("setSilent", boolean.class).invoke(entity, true);
            entity.getClass().getMethod("setGravity", boolean.class).invoke(entity, false);
            entity.getClass().getMethod("setPersistent", boolean.class).invoke(entity, false);
        } catch (ReflectiveOperationException ignored) {
        }
        if (entity instanceof LivingEntity living) {
            living.setCustomName(null);
            living.setCustomNameVisible(false);
            try {
                living.getClass().getMethod("setAI", boolean.class).invoke(living, false);
                living.getClass().getMethod("setCollidable", boolean.class).invoke(living, false);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private boolean starterKit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        String key = "starter." + player.getUniqueId();
        if (data.contains(key)) {
            Text.msg(player, "&cJe hebt je starter kit al geclaimd.");
            return true;
        }
        List<ItemStack> kit = new ArrayList<>(List.of(
            new ItemStack(Material.STONE_PICKAXE),
            new ItemStack(Material.STONE_AXE),
            new ItemStack(Material.STONE_SHOVEL),
            new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.SHIELD),
            new ItemStack(Material.BOW),
            new ItemStack(Material.ARROW, 8),
            new ItemStack(Material.COOKED_BEEF, 16),
            new ItemStack(Material.TORCH, 16),
            new ItemStack(Material.OAK_PLANKS, 32)
        ));
        if (MitchSMP.gameplay() == null) {
            Text.msg(player, "&cStarter kit safety service is unavailable. Try again after the server is checked.");
            return true;
        }
        kit.forEach(item -> MitchSMP.gameplay().markTradeRestricted(item, "Starter Kit"));
        player.getInventory().addItem(kit.toArray(ItemStack[]::new)).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        data.set(key, System.currentTimeMillis());
        data.save();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        Text.msg(player, "&aStarter kit received.");
        audit(player, "starterkit", "claimed");
        return true;
    }

    private boolean shop(CommandSender sender, String[] args) {
        if (args.length > 0 && !shopTabs().contains(sanitizeShopTab(args[0]))) {
            return shopAdmin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        openShop(player, args.length > 0 ? sanitizeShopTab(args[0]) : firstShopTab(), false, 0.0D, 0);
        return true;
    }

    private void openShop(Player player, String tab, boolean editor, double editPrice, int editAmount) {
        String activeTab = shopTabs().contains(sanitizeShopTab(tab)) ? sanitizeShopTab(tab) : firstShopTab();
        ShopMenu holder = new ShopMenu(activeTab, editor, editPrice, editAmount);
        Inventory inventory = Bukkit.createInventory(holder, SHOP_SIZE, Text.color((editor ? "&8Shop Edit: " : "&8Shop: ") + shopTabName(activeTab)));
        holder.inventory(inventory);
        for (int slot = 0; slot < SHOP_PRODUCT_SLOTS; slot++) {
            ShopOffer offer = shopOffer(activeTab, slot);
            if (offer != null) {
                shopItem(holder, inventory, slot, offer);
            } else if (editor) {
                inventory.setItem(slot, item(Material.RED_STAINED_GLASS_PANE, "&cLeeg slot", "&7Klik met item in je hand om hier te plaatsen.", "&7Shift-klik of lege hand verwijdert dit slot."));
            }
        }
        renderShopTabs(holder, inventory);
        if (editor) {
            inventory.setItem(49, item(Material.ANVIL, "&eEdit mode", "&7Price: &f$" + formatMoney(editPrice), editAmount > 0 ? "&7Amount: &f" + editAmount : "&7Amount: &fstack in je hand", "&7Gebruik: &f/shop edit <tab> <price> [amount]"));
        }
        player.openInventory(inventory);
    }

    private boolean shopAdmin(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("edit")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cAlleen players.");
                return true;
            }
            String tab = args.length >= 2 ? sanitizeShopTab(args[1]) : firstShopTab();
            if (!shopTabs().contains(tab)) {
                Text.msg(sender, "&cUnknowne shop tab. Maak hem met &f/shop tab add " + tab + " <name>&c.");
                return true;
            }
            double price = args.length >= 3 ? parseDouble(args[2], -1.0D) : 0.0D;
            if (args.length >= 3 && price <= 0.0D) {
                Text.msg(sender, "&cPrice moet hoger zijn dan 0.");
                return true;
            }
            int amount = args.length >= 4 ? parseInt(args[3], 1, 1, 64) : 0;
            openShop(player, tab, true, price, amount);
            Text.msg(player, "&aShop edit geopend. Klik een slot met het item in je hand.");
            return true;
        }
        if (action.equals("tab")) {
            return shopTabAdmin(sender, args);
        }
        if (action.equals("list")) {
            if (args.length == 1) {
                Text.msg(sender, "&aShop tabs:");
                for (String tab : shopTabs()) {
                    Text.msg(sender, "&7- &f" + tab + " &8(" + shopTabName(tab) + "&8, icon " + shopTabIcon(tab).name() + ")");
                }
                Text.msg(sender, "&7Bekijk items: &f/shop list <tab>&7.");
                return true;
            }
            String tab = sanitizeShopTab(args[1]);
            if (!shopTabs().contains(tab)) {
                Text.msg(sender, "&cUnknowne shop tab.");
                return true;
            }
            Text.msg(sender, "&aShop offers in &f" + shopTabName(tab) + "&a:");
            for (int slot = 0; slot < SHOP_PRODUCT_SLOTS; slot++) {
                ShopOffer offer = shopOffer(tab, slot);
                if (offer != null) {
                    Text.msg(sender, "&7Slot &f" + slot + "&7: &f" + offer.amount() + "x " + itemName(offer.item()) + " &7- &a$" + formatMoney(offer.price()));
                }
            }
            Text.msg(sender, "&7GUI edit: &f/shop edit " + tab + " <price> [amount]&7.");
            return true;
        }
        if (action.equals("reset")) {
            clearShopOffers();
            writeDefaultShopOffers();
            data.save();
            Text.msg(sender, "&aShop teruggezet naar defaults.");
            return true;
        }
        if (action.equals("remove")) {
            if (args.length < 2) {
                Text.msg(sender, "&cGebruik: /shop remove [tab] <slot>");
                return true;
            }
            String tab = args.length >= 3 ? sanitizeShopTab(args[1]) : firstShopTab();
            int slot = parseInt(args.length >= 3 ? args[2] : args[1], -1, 0, SHOP_PRODUCT_SLOTS - 1);
            if (!shopTabs().contains(tab) || slot < 0) {
                Text.msg(sender, "&cOngeldige tab of slot.");
                return true;
            }
            clearShopSlot(tab, slot);
            data.save();
            Text.msg(sender, "&aShop slot &f" + tab + ":" + slot + " &averwijderd.");
            return true;
        }
        if (action.equals("set")) {
            if (args.length < 4) {
                Text.msg(sender, "&cGebruik: /shop set [tab] <slot> <material> <price> [amount]");
                return true;
            }
            boolean oldStyle = isInteger(args[1]);
            if (!oldStyle && args.length < 5) {
                Text.msg(sender, "&cGebruik: /shop set [tab] <slot> <material> <price> [amount]");
                return true;
            }
            String tab = oldStyle ? firstShopTab() : sanitizeShopTab(args[1]);
            int slot = parseInt(args[oldStyle ? 1 : 2], -1, 0, SHOP_PRODUCT_SLOTS - 1);
            Material material = material(args[oldStyle ? 2 : 3]);
            if (!shopTabs().contains(tab) || slot < 0 || material == null || material == Material.AIR) {
                Text.msg(sender, "&cOngeldige tab, slot of material.");
                return true;
            }
            double price = parseDouble(args[oldStyle ? 3 : 4], -1.0D);
            if (price <= 0.0D) {
                Text.msg(sender, "&cPrice moet hoger zijn dan 0.");
                return true;
            }
            int amountArg = oldStyle ? 4 : 5;
            int amount = args.length > amountArg ? parseInt(args[amountArg], 1, 1, 64) : 1;
            setShopMaterial(tab, slot, material, price, amount);
            data.save();
            Text.msg(sender, "&aShop slot &f" + tab + ":" + slot + " &agezet naar &f" + amount + "x " + pretty(material) + " &avoor &f$" + formatMoney(price) + "&a.");
            return true;
        }
        Text.msg(sender, "&cGebruik: /shop, /shop edit <tab> <price> [amount], /shop tab add <id> <name>, /shop set [tab] <slot> <material> <price> [amount]");
        return true;
    }

    private void shopItem(ShopMenu holder, Inventory inventory, int slot, ShopOffer offer) {
        holder.offer(slot, offer);
        ItemStack display = offer.item().clone();
        display.setAmount(offer.amount());
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            String baseName = meta.hasDisplayName() ? meta.getDisplayName() : Text.color("&a" + offer.amount() + "x " + pretty(display.getType()));
            meta.setDisplayName(baseName);
            meta.setLore(List.of(
                Text.color("&7Price: &f$" + formatMoney(offer.price())),
                Text.color(holder.editor() ? "&eEdit: klik met nieuw hand-item om te vervangen." : "&aKlik om te kopen.")
            ));
            display.setItemMeta(meta);
        }
        inventory.setItem(slot, display);
    }

    private void renderShopTabs(ShopMenu holder, Inventory inventory) {
        List<String> tabs = shopTabs();
        int visible = Math.min(9, tabs.size());
        for (int i = 0; i < visible; i++) {
            int slot = 45 + i;
            String tab = tabs.get(i);
            boolean selected = tab.equals(holder.tab());
            holder.tabButton(slot, tab);
            inventory.setItem(slot, item(shopTabIcon(tab), (selected ? "&a" : "&7") + shopTabName(tab), selected ? "&7Huidige tab." : "&7Klik om deze tab te openen."));
        }
    }

    private void handleShopClick(Player player, Inventory top, InventoryClickEvent event) {
        if (!(top.getHolder() instanceof ShopMenu menu)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        String clickedTab = menu.tabButton(slot);
        if (clickedTab != null) {
            openShop(player, clickedTab, menu.editor(), menu.editPrice(), menu.editAmount());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.5F);
            return;
        }
        if (menu.editor() && slot < SHOP_PRODUCT_SLOTS) {
            handleShopEditClick(player, menu, slot, event.isShiftClick());
            return;
        }
        ShopOffer offer = menu.offer(slot);
        if (offer == null) {
            return;
        }
        EconomyService economy = MitchSMP.economy();
        if (economy == null || !economy.withdraw(player.getUniqueId(), offer.price(), "basic shop")) {
            Text.msg(player, "&cJe hebt niet genoeg geld.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
            return;
        }
        ItemStack bought = offer.item().clone();
        bought.setAmount(offer.amount());
        player.getInventory().addItem(bought);
        EconomyWatchService watch = MitchSMP.economyWatch();
        if (watch != null) {
            watch.recordItemSignal(bought.getType(), Math.max(1, bought.getAmount()), "shopbuy");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
        Text.msg(player, "&aGekocht: &f" + offer.amount() + "x " + itemName(offer.item()) + " &avoor &f$" + formatMoney(offer.price()) + "&a.");
        audit(player, "shop-buy", offer.amount() + "x " + offer.item().getType().name() + " $" + formatMoney(offer.price()));
    }

    private void handleShopEditClick(Player player, ShopMenu menu, int slot, boolean remove) {
        if (!hasAdmin(player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (remove || hand == null || hand.getType() == Material.AIR) {
            clearShopSlot(menu.tab(), slot);
            data.save();
            Text.msg(player, "&aShop slot &f" + menu.tab() + ":" + slot + " &averwijderd.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.8F);
            openShop(player, menu.tab(), true, menu.editPrice(), menu.editAmount());
            return;
        }
        ShopOffer previous = shopOffer(menu.tab(), slot);
        double price = menu.editPrice() > 0.0D ? menu.editPrice() : (previous == null ? -1.0D : previous.price());
        if (price <= 0.0D) {
            Text.msg(player, "&cOpen edit mode with a price: &f/shop edit " + menu.tab() + " <price> [amount]&c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
            return;
        }
        int amount = menu.editAmount() > 0 ? menu.editAmount() : Math.max(1, Math.min(64, hand.getAmount()));
        setShopItem(menu.tab(), slot, hand, price, amount);
        data.save();
        Text.msg(player, "&aShop slot &f" + menu.tab() + ":" + slot + " &agezet naar &f" + amount + "x " + itemName(hand) + " &avoor &f$" + formatMoney(price) + "&a.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
        openShop(player, menu.tab(), true, menu.editPrice(), menu.editAmount());
    }

    private void ensureShopDefaults() {
        if (data.contains("shop.tabs")) {
            return;
        }
        boolean migrated = migrateLegacyShop();
        if (!migrated) {
            writeDefaultShopOffers();
        }
        clearLegacyShopOffers();
        data.set("shop.initialized", true);
        data.save();
    }

    private void writeDefaultShopOffers() {
        data.set("shop.tabs", "tools,blocks,food");
        setShopTab("tools", "Tools", Material.IRON_PICKAXE);
        setShopTab("blocks", "Blocks", Material.WHITE_WOOL);
        setShopTab("food", "Food", Material.COOKED_BEEF);
        setShopMaterial("tools", 10, Material.STONE_PICKAXE, 25.0D, 1);
        setShopMaterial("tools", 11, Material.STONE_AXE, 25.0D, 1);
        setShopMaterial("tools", 12, Material.STONE_SHOVEL, 15.0D, 1);
        setShopMaterial("tools", 14, Material.IRON_PICKAXE, 120.0D, 1);
        setShopMaterial("tools", 15, Material.IRON_AXE, 120.0D, 1);
        setShopMaterial("tools", 16, Material.IRON_SWORD, 150.0D, 1);
        setShopMaterial("blocks", 10, Material.WHITE_WOOL, 40.0D, 16);
        setShopMaterial("blocks", 11, Material.OAK_PLANKS, 32.0D, 32);
        setShopMaterial("blocks", 12, Material.STONE, 24.0D, 32);
        setShopMaterial("food", 13, Material.COOKED_BEEF, 30.0D, 16);
    }

    private boolean migrateLegacyShop() {
        boolean migrated = false;
        data.set("shop.tabs", "tools");
        setShopTab("tools", "Tools", Material.IRON_PICKAXE);
        for (int slot = 0; slot < 27; slot++) {
            String materialName = data.getString("shop.slot." + slot + ".material", "");
            if (materialName.isBlank()) {
                continue;
            }
            Material material = material(materialName);
            if (material == null || material == Material.AIR) {
                continue;
            }
            setShopMaterial("tools", slot, material, data.getDouble("shop.slot." + slot + ".price", 1.0D), data.getInt("shop.slot." + slot + ".amount", 1));
            migrated = true;
        }
        if (migrated) {
            data.set("shop.tabs", "tools,blocks,food");
            setShopTab("blocks", "Blocks", Material.WHITE_WOOL);
            setShopTab("food", "Food", Material.COOKED_BEEF);
            setShopMaterial("blocks", 10, Material.WHITE_WOOL, 40.0D, 16);
            setShopMaterial("blocks", 11, Material.OAK_PLANKS, 32.0D, 32);
            setShopMaterial("blocks", 12, Material.STONE, 24.0D, 32);
            setShopMaterial("food", 13, Material.COOKED_BEEF, 30.0D, 16);
        }
        return migrated;
    }

    private boolean shopTabAdmin(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            Text.msg(sender, "&aShop tabs:");
            for (String tab : shopTabs()) {
                Text.msg(sender, "&7- &f" + tab + " &8(" + shopTabName(tab) + "&8, icon " + shopTabIcon(tab).name() + ")");
            }
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            if (args.length < 3) {
                Text.msg(sender, "&cGebruik: /shop tab add <id> [naam]");
                return true;
            }
            String tab = sanitizeShopTab(args[2]);
            if (tab.isBlank()) {
                Text.msg(sender, "&cOngeldige tab id.");
                return true;
            }
            List<String> tabs = new ArrayList<>(shopTabs());
            if (!tabs.contains(tab)) {
                tabs.add(tab);
            }
            data.set("shop.tabs", String.join(",", tabs));
            setShopTab(tab, args.length >= 4 ? joinArgs(args, 3) : prettyTab(tab), Material.CHEST);
            data.save();
            Text.msg(sender, "&aShop tab &f" + tab + " &aaangemaakt.");
            return true;
        }
        if (sub.equals("remove")) {
            if (args.length < 3) {
                Text.msg(sender, "&cGebruik: /shop tab remove <id>");
                return true;
            }
            String tab = sanitizeShopTab(args[2]);
            List<String> tabs = new ArrayList<>(shopTabs());
            if (tabs.size() <= 1 || !tabs.remove(tab)) {
                Text.msg(sender, "&cJe kan deze tab niet verwijderen.");
                return true;
            }
            clearShopTab(tab);
            data.set("shop.tabs", String.join(",", tabs));
            data.save();
            Text.msg(sender, "&aShop tab &f" + tab + " &averwijderd.");
            return true;
        }
        if (sub.equals("rename")) {
            if (args.length < 4) {
                Text.msg(sender, "&cGebruik: /shop tab rename <id> <name>");
                return true;
            }
            String tab = sanitizeShopTab(args[2]);
            if (!shopTabs().contains(tab)) {
                Text.msg(sender, "&cUnknowne shop tab.");
                return true;
            }
            data.set("shop.tab." + tab + ".name", joinArgs(args, 3));
            data.save();
            Text.msg(sender, "&aShop tab hernoemd.");
            return true;
        }
        if (sub.equals("icon")) {
            if (args.length < 4) {
                Text.msg(sender, "&cGebruik: /shop tab icon <id> <material>");
                return true;
            }
            String tab = sanitizeShopTab(args[2]);
            Material icon = material(args[3]);
            if (!shopTabs().contains(tab) || icon == null || icon == Material.AIR) {
                Text.msg(sender, "&cOngeldige tab of icon.");
                return true;
            }
            data.set("shop.tab." + tab + ".icon", icon.name());
            data.save();
            Text.msg(sender, "&aShop tab icon bijgewerkt.");
            return true;
        }
        Text.msg(sender, "&cGebruik: /shop tab add/remove/rename/icon/list");
        return true;
    }

    private void setShopTab(String tab, String name, Material icon) {
        data.set("shop.tab." + tab + ".name", name);
        data.set("shop.tab." + tab + ".icon", icon.name());
    }

    private void setShopMaterial(String tab, int slot, Material material, double price, int amount) {
        String prefix = shopSlotPrefix(tab, slot);
        data.set(prefix + ".item", null);
        data.set(prefix + ".material", material.name());
        data.set(prefix + ".price", price);
        data.set(prefix + ".amount", Math.max(1, Math.min(64, amount)));
    }

    private void setShopItem(String tab, int slot, ItemStack item, double price, int amount) {
        ItemStack stored = item.clone();
        stored.setAmount(Math.max(1, Math.min(64, amount)));
        String prefix = shopSlotPrefix(tab, slot);
        data.set(prefix + ".item", Base64.getEncoder().encodeToString(stored.serializeAsBytes()));
        data.set(prefix + ".material", stored.getType().name());
        data.set(prefix + ".price", price);
        data.set(prefix + ".amount", stored.getAmount());
    }

    private ShopOffer shopOffer(String tab, int slot) {
        String prefix = shopSlotPrefix(tab, slot);
        String encoded = data.getString(prefix + ".item", "");
        ItemStack item = null;
        if (!encoded.isBlank()) {
            try {
                item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
            } catch (IllegalArgumentException exception) {
                item = null;
            }
        }
        if (item == null) {
            String materialName = data.getString(prefix + ".material", "");
            if (materialName.isBlank()) {
                return null;
            }
            Material material = material(materialName);
            if (material == null || material == Material.AIR) {
                return null;
            }
            item = new ItemStack(material);
        }
        double price = data.getDouble(prefix + ".price", 1.0D);
        int amount = data.getInt(prefix + ".amount", Math.max(1, item.getAmount()));
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return new ShopOffer(item, Math.max(0.01D, price), Math.max(1, Math.min(64, amount)));
    }

    private List<String> shopTabs() {
        String raw = data == null ? "" : data.getString("shop.tabs", "tools");
        List<String> tabs = new ArrayList<>();
        for (String part : raw.split(",")) {
            String tab = sanitizeShopTab(part);
            if (!tab.isBlank() && !tabs.contains(tab)) {
                tabs.add(tab);
            }
        }
        if (tabs.isEmpty()) {
            tabs.add("tools");
        }
        return tabs;
    }

    private String firstShopTab() {
        return shopTabs().get(0);
    }

    private String shopTabName(String tab) {
        return data.getString("shop.tab." + tab + ".name", prettyTab(tab));
    }

    private Material shopTabIcon(String tab) {
        Material icon = material(data.getString("shop.tab." + tab + ".icon", "CHEST"));
        return icon == null || icon == Material.AIR ? Material.CHEST : icon;
    }

    private String sanitizeShopTab(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private String prettyTab(String input) {
        String cleaned = sanitizeShopTab(input).replace('_', ' ').replace('-', ' ');
        return cleaned.isBlank() ? "Shop" : Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private String shopSlotPrefix(String tab, int slot) {
        return "shop.tab." + sanitizeShopTab(tab) + ".slot." + slot;
    }

    private void clearShopOffers() {
        for (String key : new ArrayList<>(data.keys())) {
            if (key.equals("shop.tabs") || key.equals("shop.initialized") || key.startsWith("shop.tab.") || key.startsWith("shop.slot.")) {
                data.set(key, null);
            }
        }
    }

    private void clearLegacyShopOffers() {
        for (String key : new ArrayList<>(data.keys())) {
            if (key.startsWith("shop.slot.")) {
                data.set(key, null);
            }
        }
    }

    private void clearShopTab(String tab) {
        String prefix = "shop.tab." + sanitizeShopTab(tab) + ".";
        for (String key : new ArrayList<>(data.keys())) {
            if (key.startsWith(prefix)) {
                data.set(key, null);
            }
        }
    }

    private void clearShopSlot(String tab, int slot) {
        String prefix = shopSlotPrefix(tab, slot);
        data.set(prefix + ".item", null);
        data.set(prefix + ".material", null);
        data.set(prefix + ".price", null);
        data.set(prefix + ".amount", null);
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private double parseDouble(String input, double fallback) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean lagClear(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    entity.remove();
                    removed++;
                }
            }
        }
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&aCleanup: removed &f" + removed + " &adropped items."));
        audit(sender instanceof Player player ? player : null, "lagclear", removed + " dropped items");
        return true;
    }

    private boolean spawnMob(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!isTestWorld(player.getWorld())) {
            Text.msg(player, "&cMob spawning tools are sandbox-only. Use &f/testworld join <name>&c first.");
            return true;
        }
        if (args.length == 0) {
            Text.msg(player, "&cUsage: /spawnmob <mob|miniboss|endboss> [amount]");
            return true;
        }
        String typeName = args[0].toLowerCase(Locale.ROOT);
        int amount = args.length > 1 ? parseInt(args[1], 1, 1, 25) : 1;
        if (typeName.equals("endboss")) {
            amount = 1;
        }
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            Location base = player.getLocation();
            double yaw = Math.toRadians(base.getYaw());
            Location location = new Location(base.getWorld(), base.getX() - Math.sin(yaw) * 4.0D, base.getY() + 1.0D, base.getZ() + Math.cos(yaw) * 4.0D, base.getYaw(), base.getPitch());
            Entity entity;
            if (typeName.equals("endboss")) {
                entity = spawnTestEndBoss(location);
            } else if (typeName.equals("miniboss") || typeName.equals("boss")) {
                entity = spawnTestMiniBoss(location);
            } else {
                EntityType type;
                try {
                    type = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    Text.msg(player, "&cUnknown mob type. Use tab completion.");
                    return true;
                }
                if (type == EntityType.ARMOR_STAND) {
                    Text.msg(player, "&cThat entity type is blocked for this test command.");
                    return true;
                }
                try {
                    entity = player.getWorld().spawnEntity(location, type);
                } catch (RuntimeException exception) {
                    Text.msg(player, "&cThat entity type cannot be spawned here.");
                    return true;
                }
            }
            if (entity instanceof LivingEntity living) {
                entityFlag(living, "setRemoveWhenFarAway", false);
                living.setFireTicks(0);
            }
            spawned++;
        }
        Text.msg(player, "&aSpawned &f" + spawned + " &atest mob(s) in sandbox world &f" + player.getWorld().getName() + "&a.");
        audit(player, "test-spawnmob", typeName + " x" + spawned);
        return true;
    }

    private Entity spawnTestEndBoss(Location location) {
        Entity entity = location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        if (entity instanceof LivingEntity boss) {
            boss.setCustomName(Text.color("&4Bloodbound Archfiend &7(Test)"));
            boss.setCustomNameVisible(true);
            double health = MitchSMP.runtime().safeMaxHealth(boss, 5000.0D);
            MitchSMP.runtime().safeSetHealth(boss, health);
            boss.setCustomNameVisible(false);
            hideTestController(boss);
            entityFlag(boss, "setRemoveWhenFarAway", false);
            attachArchfiendVisual(boss);
        }
        return entity;
    }

    private void hideTestController(LivingEntity boss) {
        entityFlag(boss, "setInvisible", true);
        entityFlag(boss, "setSilent", true);
        entityFlag(boss, "setAI", false);
        entityFlag(boss, "setGlowing", false);
        try {
            Object invisibility = Class.forName("org.bukkit.potion.PotionEffectType").getField("INVISIBILITY").get(null);
            if (invisibility instanceof org.bukkit.potion.PotionEffectType type) {
                boss.addPotionEffect(new org.bukkit.potion.PotionEffect(type, 20 * 60 * 60, 0, false, false, false));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            if (boss.getEquipment() != null) {
                boss.getEquipment().setHelmet(null);
                boss.getEquipment().setChestplate(null);
                boss.getEquipment().setLeggings(null);
                boss.getEquipment().setBoots(null);
                boss.getEquipment().setItemInMainHand(null);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private Entity spawnTestMiniBoss(Location location) {
        Entity entity = location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        if (entity instanceof LivingEntity boss) {
            boss.setCustomName(Text.color("&4Bloodbound Night Stalker &7(Test)"));
            boss.setCustomNameVisible(true);
            if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
                boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(250.0D);
            }
            boss.setHealth(250.0D);
            if (boss.getEquipment() != null) {
                boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            }
        }
        return entity;
    }

    private void attachArchfiendVisual(LivingEntity boss) {
        try {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MitchSMP-CustomMobs");
            if (plugin != null && plugin.isEnabled()) {
                plugin.getClass().getMethod("attachArchfiend", Entity.class).invoke(plugin, boss);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private boolean killAll(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!isTestWorld(player.getWorld())) {
            Text.msg(player, "&cKillall is sandbox-only. Use &f/testworld join <name>&c first.");
            return true;
        }
        int removed = 0;
        for (Entity entity : new ArrayList<>(player.getWorld().getEntities())) {
            if (entity instanceof Player) {
                continue;
            }
            if (entity instanceof LivingEntity) {
                entity.remove();
                removed++;
            }
        }
        Text.msg(player, "&aRemoved &f" + removed + " &aentities from sandbox world &f" + player.getWorld().getName() + "&a.");
        audit(player, "test-killall", "mobs removed=" + removed);
        return true;
    }

    private void entityFlag(Entity entity, String method, boolean value) {
        try {
            entity.getClass().getMethod(method, boolean.class).invoke(entity, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private boolean smpWorld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
            Text.msg(player, "&cThis command is restricted to the Owner rank.");
            return true;
        }
        if (!MitchSMP.permissions().isAdminMode(player)) {
            Text.msg(player, "&cEnter admin mode before loading an SMP world.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("active")) {
            Text.msg(player, "&7Active SMP wilderness: &f" + data.getString("smpworld.active", "world"));
            Text.msg(player, "&7Load a new one with &f/smpworld load <name>&7.");
            return true;
        }
        if (!args[0].equalsIgnoreCase("load") || args.length < 2) {
            Text.msg(player, "&cUsage: /smpworld load <name> [confirm confirm]");
            return true;
        }
        String safeName = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (safeName.isBlank() || safeName.length() > 32) {
            Text.msg(player, "&cWorld name must contain 1-32 letters, digits, dashes, or underscores.");
            return true;
        }
        long now = System.currentTimeMillis();
        SmpWorldConfirmation pending = smpWorldConfirmations.get(player.getUniqueId());
        int confirmationWords = 0;
        for (int index = 2; index < args.length; index++) {
            if (args[index].equalsIgnoreCase("confirm")) {
                confirmationWords++;
            }
        }
        if (confirmationWords == 0) {
            smpWorldConfirmations.put(player.getUniqueId(), new SmpWorldConfirmation(safeName, 1, now + 60_000L));
            Text.msg(player, "&6Safety check 1/2. This creates a new generated SMP world and changes Wilderness RTP.");
            Text.msg(player, "&eConfirm with: &f/smpworld load " + safeName + " confirm");
            return true;
        }
        if (pending == null || pending.expiresAt() < now || !pending.name().equals(safeName)) {
            smpWorldConfirmations.remove(player.getUniqueId());
            Text.msg(player, "&cThe confirmation expired or targets another world. Start again without confirm.");
            return true;
        }
        if (confirmationWords == 1 && pending.stage() == 1) {
            smpWorldConfirmations.put(player.getUniqueId(), new SmpWorldConfirmation(safeName, 2, now + 60_000L));
            Text.msg(player, "&cSafety check 2/2. Final confirmation:");
            Text.msg(player, "&f/smpworld load " + safeName + " confirm confirm");
            return true;
        }
        if (confirmationWords < 2 || pending.stage() != 2) {
            Text.msg(player, "&cComplete both confirmation steps in order.");
            return true;
        }
        smpWorldConfirmations.remove(player.getUniqueId());
        String worldName = "bloodbound_smp_" + safeName;
        World world = loadGeneratedSmpWorld(worldName);
        if (world == null) {
            Text.msg(player, "&cThe SMP world could not be loaded. Check the console for details.");
            return true;
        }
        data.set("smpworld.active", worldName);
        data.save();
        Location spawn = world.getSpawnLocation();
        Block highest = world.getHighestBlockAt(spawn.getBlockX(), spawn.getBlockZ());
        Location destination = highest == null ? spawn : highest.getLocation().add(0.5D, 1.0D, 0.5D);
        player.teleport(destination);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(100);
        Text.msg(player, "&aLoaded and activated SMP wilderness &f" + worldName + "&a.");
        Text.msg(player, "&7Existing worlds and player data were not removed.");
        audit(player, "smpworld-load", worldName);
        return true;
    }

    private World loadGeneratedSmpWorld(String worldName) {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            return existing;
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(worldName);
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, true);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create SMP world " + worldName + ": " + exception.getMessage());
        }
        return Bukkit.getWorld(worldName);
    }

    private boolean testWorld(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length == 0) {
            Text.msg(sender, "&e/testworld create <name> &7- create sandbox world");
            Text.msg(sender, "&e/testworld join <name> &7- ga naar sandbox met losse inventory");
            Text.msg(sender, "&e/testworld reset <name> confirm &7- reset sandbox volledig");
            Text.msg(sender, "&e/testworld leave &7- return to your SMP state");
            Text.msg(sender, "&e/testworld list &7- toon sandboxes");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            List<String> worlds = testWorldNames();
            Text.msg(sender, worlds.isEmpty() ? "&7Geen testwerelden." : "&7Testwerelden: &f" + String.join("&7, &f", worlds));
            return true;
        }
        if (sub.equals("leave")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cAlleen players.");
                return true;
            }
            if (!restoreTestWorldPlayer(player, true)) {
                Text.msg(player, "&7Je hebt geen actieve testwereld sessie.");
            }
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /testworld " + sub + " <name>");
            return true;
        }
        String name = safeTestWorld(args[1]);
        if (name.isBlank()) {
            Text.msg(sender, "&cOngeldige testwereld naam.");
            return true;
        }
        if (sub.equals("create")) {
            World world = loadTestWorld(name);
            if (world == null) {
                Text.msg(sender, "&cKon testwereld niet maken.");
                return true;
            }
            data.set("testworld." + name + ".world", TEST_WORLD_PREFIX + name);
            data.save();
            Text.msg(sender, "&aTest world &f" + name + " &ais ready. Use &f/testworld join " + name + "&a.");
            audit(sender instanceof Player player ? player : null, "testworld-create", name);
            return true;
        }
        if (sub.equals("join")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cAlleen players.");
                return true;
            }
            World world = loadTestWorld(name);
            if (world == null) {
                Text.msg(player, "&cKon testwereld niet laden.");
                return true;
            }
            data.set("testworld." + name + ".world", TEST_WORLD_PREFIX + name);
            saveTestWorldPlayer(player);
            Location spawn = testWorldSpawn(world);
            player.getInventory().clear();
            player.setGameMode(GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(false);
            player.teleport(spawn);
            player.setFallDistance(0.0F);
            player.setNoDamageTicks(100);
            Text.msg(player, "&aJe zit in testwereld &f" + name + "&a. Alles hier kun je resetten met &f/testworld reset " + name + " confirm&a.");
            audit(player, "testworld-join", name);
            return true;
        }
        if (sub.equals("reset")) {
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                Text.msg(sender, "&cBevestig reset met: &f/testworld reset " + name + " confirm");
                return true;
            }
            resetTestWorld(sender, name);
            return true;
        }
        Text.msg(sender, "&cUnknown testworld subcommand.");
        return true;
    }

    private World loadTestWorld(String name) {
        String worldName = TEST_WORLD_PREFIX + safeTestWorld(name);
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            buildTestWorldSpawn(existing);
            return existing;
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(worldName);
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, true);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create test world " + worldName + ": " + exception.getMessage());
        }
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            buildTestWorldSpawn(world);
        }
        return world;
    }

    private void resetTestWorld(CommandSender sender, String name) {
        String worldName = TEST_WORLD_PREFIX + safeTestWorld(name);
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (player.getWorld().getName().equals(worldName)) {
                    restoreTestWorldPlayer(player, true);
                }
            }
            try {
                Bukkit.class.getMethod("unloadWorld", World.class, boolean.class).invoke(null, world, false);
            } catch (ReflectiveOperationException exception) {
                getLogger().warning("Could not unload test world " + worldName + ": " + exception.getMessage());
            }
        }
        deleteWorldFolder(worldName);
        data.set("testworld." + name + ".world", worldName);
        data.save();
        World fresh = loadTestWorld(name);
        Text.msg(sender, fresh == null ? "&cTest world reset geprobeerd, maar kon niet opnieuw laden." : "&aTest world &f" + name + " &ais volledig gereset.");
        audit(sender instanceof Player player ? player : null, "testworld-reset", name);
    }

    private void saveTestWorldPlayer(Player player) {
        UUID id = player.getUniqueId();
        String base = "testworld.player." + id + ".";
        if (data.contains(base + "inventory")) {
            return;
        }
        data.set(base + "inventory", encodeInventory(player.getInventory().getContents()));
        data.set(base + "mode", player.getGameMode().name());
        data.set(base + "allowFlight", player.getAllowFlight());
        data.set(base + "flying", player.isFlying());
        data.set(base + "location", encode(player.getLocation()));
        data.save();
    }

    private boolean restoreTestWorldPlayer(Player player, boolean teleportBack) {
        UUID id = player.getUniqueId();
        String base = "testworld.player." + id + ".";
        if (!data.contains(base + "inventory")) {
            return false;
        }
        ItemStack[] inventory = decodeInventory(data.getString(base + "inventory", ""), player.getInventory().getSize());
        player.getInventory().clear();
        player.getInventory().setContents(inventory);
        GameMode mode = GameMode.SURVIVAL;
        try {
            mode = GameMode.valueOf(data.getString(base + "mode", "SURVIVAL"));
        } catch (IllegalArgumentException ignored) {
        }
        player.setGameMode(mode);
        player.setAllowFlight(data.getString(base + "allowFlight", "false").equalsIgnoreCase("true"));
        player.setFlying(data.getString(base + "flying", "false").equalsIgnoreCase("true"));
        Location back = decode(data.getString(base + "location", ""));
        data.set(base + "inventory", null);
        data.set(base + "mode", null);
        data.set(base + "allowFlight", null);
        data.set(base + "flying", null);
        data.set(base + "location", null);
        data.save();
        if (teleportBack && back != null) {
            player.teleport(back);
        }
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(100);
        Text.msg(player, "&aTest world left. Your SMP inventory and gamemode have been restored.");
        return true;
    }

    private void restoreDanglingTestWorldSession(Player player) {
        if (isTestWorld(player.getWorld()) || data.contains("testworld.player." + player.getUniqueId() + ".inventory")) {
            restoreTestWorldPlayer(player, true);
        }
    }

    private Location testWorldSpawn(World world) {
        return new Location(world, 0.5D, 100.0D, 0.5D, 0.0F, 0.0F);
    }

    private void buildTestWorldSpawn(World world) {
        Location spawn = testWorldSpawn(world);
        int y = spawn.getBlockY() - 1;
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                Material material = Math.abs(x) == 5 || Math.abs(z) == 5 ? Material.SEA_LANTERN : Material.QUARTZ_BLOCK;
                world.getBlockAt(x, y, z).setType(material, false);
            }
        }
    }

    private boolean isTestWorld(World world) {
        return world != null && world.getName().startsWith(TEST_WORLD_PREFIX);
    }

    private List<String> testWorldNames() {
        List<String> names = new ArrayList<>();
        for (String key : data.keys()) {
            if (key.startsWith("testworld.") && key.endsWith(".world")) {
                String name = key.substring("testworld.".length(), key.length() - ".world".length());
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private String safeTestWorld(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("^_+|_+$", "");
    }

    private void deleteWorldFolder(String worldName) {
        Path root = Path.of(worldName).toAbsolutePath().normalize();
        if (!root.getFileName().toString().startsWith(TEST_WORLD_PREFIX) || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            getLogger().warning("Could not delete test world " + worldName + ": " + exception.getMessage());
        }
    }

    private boolean setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        data.set("spawn", encode(player.getLocation()));
        data.save();
        Text.msg(player, "&aSpawn ingesteld.");
        return true;
    }

    private boolean ownerConfirm(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cOnly an in-game Owner can perform this permanent confirmation.");
            return true;
        }
        String existing = data.getString(AUDIT_OWNER_UUID_KEY, "").trim();
        if (!existing.isBlank()) {
            if (isAuditOwner(player)) {
                Text.msg(player, "&6The staff audit is already permanently bound to your UUID.");
            } else {
                Text.msg(player, "&cThe staff audit already has a permanent Owner binding.");
            }
            return true;
        }
        if (MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
            Text.msg(player, "&cOnly the exact Owner rank can bind the staff audit.");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("confirm")) {
            Text.msg(player, "&4Permanent action: &cuse &f/ownerconfirm confirm &cto bind the audit to your UUID forever.");
            return true;
        }
        data.set(AUDIT_OWNER_UUID_KEY, player.getUniqueId());
        data.save();
        audit(player, "owner-audit-bound", player.getUniqueId().toString());
        getLogger().warning("Staff audit permanently bound to Owner UUID " + player.getUniqueId() + ".");
        Text.msg(player, "&6Owner confirmed. &f/staffaudit &6is now permanently bound to your UUID.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
        return true;
    }

    private boolean serverConfig(CommandSender sender) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Text.msg(sender, "&4Bloodbound live configuration commands:");
        Text.msg(sender, "&d/abilities config &7- ability requirements, global toggles and PvP cooldowns.");
        Text.msg(sender, "&6/event config &7- world events, underground Ancient Structures, guards and loot.");
        Text.msg(sender, "&5/corruptedheart chance &7- all Corrupted Heart chest chances.");
        Text.msg(sender, "&4/boss config <key> <value> &7- personal boss rates, range, health, shards and rare gear.");
        Text.msg(sender, "&c/sethearts config <key> <value> &7- Lifesteal anti-farm limits.");
        Text.msg(sender, "&6/bounty config <key> <value> &7- bounty scaling and anti-farm limits.");
        Text.msg(sender, "&a/progression config &7- contracts, orders, rewards, rerolls and economy scaling.");
        Text.msg(sender, "&5/endboss config <key> <value> &7- ritual costs, health and party requirements.");
        Text.msg(sender, "&b/perf config <key> <value> &7- performance warning thresholds.");
        Text.msg(sender, "&7Use Tab after each command to inspect valid keys and values.");
        return true;
    }

    private boolean isAuditOwner(Player player) {
        if (player == null) {
            return false;
        }
        String configured = data.getString(AUDIT_OWNER_UUID_KEY, "").trim();
        if (configured.isBlank()) {
            return false;
        }
        try {
            return UUID.fromString(configured).equals(player.getUniqueId());
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid immutable Owner UUID in essentials.properties; staff audit access is locked.");
            return false;
        }
    }

    private void audit(Player actor, String action, String detail) {
        if (actor == null) {
            return;
        }
        int next = data.getInt("audit.next", 0);
        int index = next % AUDIT_MAX;
        String base = "audit." + index + ".";
        data.set(base + "time", Instant.now().toString());
        data.set(base + "uuid", actor.getUniqueId());
        data.set(base + "name", actor.getName());
        data.set(base + "rank", MitchSMP.ranks().getRank(actor.getUniqueId()).displayName());
        data.set(base + "adminmode", MitchSMP.permissions().isAdminMode(actor));
        data.set(base + "action", action);
        data.set(base + "detail", detail == null ? "" : detail);
        data.set("audit.next", next + 1);
        String statKey = "auditstat." + actor.getUniqueId() + "." + action;
        data.set(statKey, data.getInt(statKey, 0) + 1);
        data.saveSoon(this, 100L);
    }

    private void openAudit(Player player, int page, String filter) {
        if (!isAuditOwner(player)) {
            return;
        }
        List<Integer> entries = auditEntries(filter);
        int maxPage = Math.max(0, (entries.size() - 1) / 45);
        int clamped = Math.max(0, Math.min(page, maxPage));
        AuditMenu holder = new AuditMenu(clamped, filter == null ? "" : filter);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8Owner Staff Audit"));
        holder.inventory(inventory);
        int offset = clamped * 45;
        for (int slot = 0; slot < 45 && offset + slot < entries.size(); slot++) {
            inventory.setItem(slot, auditIcon(entries.get(offset + slot)));
        }
        inventory.setItem(45, item(Material.ARROW, "&aPrevious", "&7Previous page."));
        inventory.setItem(46, item(Material.COMPASS, "&eCategories", "&7Choose an audit category."));
        inventory.setItem(47, item(Material.COMPASS, "&bCommands", "&7Filter command logs."));
        inventory.setItem(48, item(Material.CHEST, "&dInventory", "&7Filter inventory logs."));
        inventory.setItem(49, item(Material.STONE, "&6Blocks", "&7Filter block logs."));
        inventory.setItem(50, item(Material.NETHERITE_PICKAXE, "&cAdmin mode", "&7Filter adminmode/staff tools."));
        inventory.setItem(51, item(Material.PLAYER_HEAD, "&eStaff ranks", "&7Show the staff/rank overview."));
        inventory.setItem(53, item(Material.ARROW, "&aVolgende", "&7Pagina verder."));
        player.openInventory(inventory);
    }

    private List<Integer> auditEntries(String filter) {
        String normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        int next = data.getInt("audit.next", 0);
        List<Integer> entries = new ArrayList<>();
        for (int i = Math.max(0, next - AUDIT_MAX); i < next; i++) {
            int index = i % AUDIT_MAX;
            String action = data.getString("audit." + index + ".action", "");
            String detail = data.getString("audit." + index + ".detail", "");
            String name = data.getString("audit." + index + ".name", "");
            if (action.isBlank()) {
                continue;
            }
            String haystack = (action + " " + detail + " " + name).toLowerCase(Locale.ROOT);
            boolean matches = normalized.isBlank()
                || normalized.startsWith("category:") && matchesAuditCategory(action, detail, normalized.substring("category:".length()))
                || !normalized.startsWith("category:") && haystack.contains(normalized);
            if (matches) {
                entries.add(index);
            }
        }
        entries.sort(Comparator.reverseOrder());
        return entries;
    }

    private ItemStack auditIcon(int index) {
        String base = "audit." + index + ".";
        String action = data.getString(base + "action", "unknown");
        String name = data.getString(base + "name", "unknown");
        String rank = data.getString(base + "rank", "?");
        String time = data.getString(base + "time", "?");
        String detail = data.getString(base + "detail", "");
        Material material = action.contains("command") ? Material.COMPASS
            : action.contains("block") ? Material.STONE
            : action.contains("inventory") || action.contains("drop") ? Material.CHEST
            : action.contains("admin") || action.contains("vanish") ? Material.NETHERITE_PICKAXE
            : Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&e" + name + " &7- &f" + action));
            meta.setLore(List.of(
                Text.color("&7Rank: &f" + rank),
                Text.color("&7Time: &f" + time),
                Text.color("&7Detail: &f" + clip(detail, 52))
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleAuditClick(Player player, AuditMenu menu, int slot) {
        if (slot == 45) {
            openAudit(player, menu.page() - 1, menu.filter());
        } else if (slot == 46) {
            openAuditCategories(player);
        } else if (slot == 47) {
            openAudit(player, 0, "command");
        } else if (slot == 48) {
            openAudit(player, 0, "inventory");
        } else if (slot == 49) {
            openAudit(player, 0, "block");
        } else if (slot == 50) {
            openAudit(player, 0, "admin");
        } else if (slot == 51) {
            sendStaffOverview(player);
        } else if (slot == 53) {
            openAudit(player, menu.page() + 1, menu.filter());
        }
    }

    private void openAuditCategories(Player player) {
        AuditCategoryMenu holder = new AuditCategoryMenu();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Staff Audit Categories"));
        holder.inventory = inventory;
        String[][] categories = {
            {"economy", "&aEconomy"}, {"adminmode", "&cAdmin Mode"}, {"punishments", "&4Punishments"},
            {"rollback", "&6Rollback"}, {"inventory", "&dInventory"}, {"teleport", "&bTeleport"},
            {"gamemode", "&eGamemode"}, {"permissions", "&5Permissions"}, {"reports", "&fReports"}
        };
        Material[] icons = { Material.EMERALD, Material.NETHERITE_PICKAXE, Material.IRON_BARS, Material.CLOCK,
            Material.CHEST, Material.COMPASS, Material.GRASS_BLOCK, Material.NAME_TAG, Material.PAPER };
        for (int index = 0; index < categories.length; index++) {
            inventory.setItem(9 + index, item(icons[index], categories[index][1], "&7Filter " + categories[index][0] + " audit actions."));
        }
        inventory.setItem(22, item(Material.BARRIER, "&fAll actions", "&7Return to the complete audit stream."));
        player.openInventory(inventory);
    }

    private void handleAuditCategoryClick(Player player, int slot) {
        String[] categories = {"economy", "adminmode", "punishments", "rollback", "inventory", "teleport", "gamemode", "permissions", "reports"};
        if (slot >= 9 && slot < 18) {
            openAudit(player, 0, "category:" + categories[slot - 9]);
        } else if (slot == 22) {
            openAudit(player, 0, "");
        }
    }

    private boolean matchesAuditCategory(String action, String detail, String category) {
        String value = (action + " " + detail).toLowerCase(Locale.ROOT);
        return switch (category) {
            case "economy" -> containsAny(value, "economy", "eco", "balance", "pay", "shop", "sell", "auction", "money");
            case "adminmode" -> containsAny(value, "admin", "vanish", "noclip", "godtool", "model-disguise");
            case "punishments" -> containsAny(value, "jail", "freeze", "lockdown", "release", "punish", "ban", "mute");
            case "rollback" -> containsAny(value, "rollback", "restore", "snapshot", "cleanup");
            case "inventory" -> containsAny(value, "inventory", "container", "drop", "item", "chest");
            case "teleport" -> containsAny(value, "teleport", " tpa", " tp ", "back", "world", "jail-visit");
            case "gamemode" -> containsAny(value, "gamemode", "creative", "survival", "spectator", "flight", "fly", "speed");
            case "permissions" -> containsAny(value, "permission", "rank", "owner-audit");
            case "reports" -> containsAny(value, "report");
            default -> true;
        };
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void sendStaffOverview(Player player) {
        Text.msg(player, "&6Staff and rank overview:");
        for (Player online : Bukkit.getOnlinePlayers()) {
            MitchRank rank = MitchSMP.ranks().getRank(online.getUniqueId());
            if (rank.staff() || isAuditOwner(online)) {
                Text.msg(player, "&f" + online.getName() + " &7rank=&e" + rank.displayName() + " &7adminmode=&c" + MitchSMP.permissions().isAdminMode(online));
            }
        }
    }

    private void recordAdminTrace(Player player, String action, Location location, Material material) {
        if (!MitchSMP.permissions().isAdminMode(player) || location == null || location.getWorld() == null) {
            return;
        }
        int count = data.getInt("admintrace." + player.getUniqueId() + ".count", 0);
        data.set("admintrace." + player.getUniqueId() + "." + count, action + ";" + encode(location) + ";" + material.name());
        data.set("admintrace." + player.getUniqueId() + ".count", count + 1);
        data.saveSoon(this, 100L);
    }

    private void rollbackAdminTrace(Player player) {
        int count = data.getInt("admintrace." + player.getUniqueId() + ".count", 0);
        int changed = 0;
        for (int i = count - 1; i >= 0; i--) {
            String key = "admintrace." + player.getUniqueId() + "." + i;
            String[] parts = data.getString(key, "").split(";", 8);
            if (parts.length < 8) {
                data.set(key, null);
                continue;
            }
            Location location = decode(String.join(";", java.util.Arrays.copyOfRange(parts, 1, 7)));
            Material material = material(parts[7]);
            if (location != null && material != null) {
                location.getBlock().setType(parts[0].equals("place") ? Material.AIR : material);
                changed++;
            }
            data.set(key, null);
        }
        data.set("admintrace." + player.getUniqueId() + ".count", null);
        data.save();
        if (changed > 0) {
            Text.msg(player, "&eAdmin cleanup: &f" + changed + " &eblock sporen teruggedraaid.");
            audit(player, "admin-cleanup", changed + " traces rolled back");
        }
    }

    private void clearAdminTrace(Player player) {
        int count = data.getInt("admintrace." + player.getUniqueId() + ".count", 0);
        for (int i = count - 1; i >= 0; i--) {
            data.set("admintrace." + player.getUniqueId() + "." + i, null);
        }
        data.set("admintrace." + player.getUniqueId() + ".count", null);
        data.save();
        audit(player, "admin-cleanup-skipped", "owner override");
    }

    private void loadJails() {
        boolean changed = false;
        for (String key : data.keys()) {
            if (!key.startsWith("jail.") || !key.endsWith(".cell")) {
                continue;
            }
            String raw = key.substring("jail.".length(), key.length() - ".cell".length());
            try {
                UUID id = UUID.fromString(raw);
                Location cell = decode(data.getString(key, ""));
                int index = data.getInt("jail." + id + ".cellIndex", -1);
                if ((cell == null || cell.getWorld() == null || !JAIL_WORLD.equals(cell.getWorld().getName())) && index >= 0) {
                    cell = jailCell(index);
                    data.set(key, encode(cell));
                    changed = true;
                }
                if (cell != null) {
                    buildJailCell(cell);
                    jailedCells.put(id, cell);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (changed) {
            data.save();
        }
    }

    private void tickJails() {
        long now = System.currentTimeMillis();
        for (UUID id : new java.util.ArrayList<>(jailedCells.keySet())) {
            long until = data.getLong("jail." + id + ".until", 0L);
            if (until <= 0L || until > now) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                releaseJail(player, true);
            } else {
                clearJailData(id);
            }
        }
    }

    private void releaseJail(Player player, boolean teleportBack) {
        UUID id = player.getUniqueId();
        captureSnapshot(player, "jail-exit-before");
        Location back = decode(data.getString("jail." + id + ".return", ""));
        GameMode mode = GameMode.SURVIVAL;
        try {
            mode = GameMode.valueOf(data.getString("jail." + id + ".mode", "SURVIVAL"));
        } catch (IllegalArgumentException ignored) {
        }
        clearJailData(id);
        frozenLocations.remove(id);
        lockedModes.remove(id);
        player.setGameMode(mode);
        if (teleportBack && back != null) {
            player.teleport(back);
        }
        captureSnapshot(player, "jail-exit-restored");
        Text.msg(player, "&aYou were released from jail.");
    }

    private void clearJailData(UUID id) {
        jailedCells.remove(id);
        data.set("jail." + id + ".cell", null);
        data.set("jail." + id + ".return", null);
        data.set("jail." + id + ".mode", null);
        data.set("jail." + id + ".until", null);
        data.set("jail." + id + ".cellIndex", null);
        data.save();
    }

    private Location jailCell(int index) {
        World world = jailWorld();
        int x = (index % 8) * 8;
        int z = (index / 8) * 8;
        int y = 80;
        return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D);
    }

    private void buildJailCell(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY() - 1;
        int cz = center.getBlockZ();
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int y = cy; y <= cy + 4; y++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean floor = y == cy;
                    boolean roof = y == cy + 4;
                    boolean wall = x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 2;
                    Material material = floor ? Material.OBSIDIAN : (roof || wall ? Material.GLASS : Material.AIR);
                    new Location(world, x, y, z).getBlock().setType(material);
                }
            }
        }
        buildJailVisitPlatform(center);
    }

    private void buildJailVisitPlatform(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY() - 1;
        int cz = center.getBlockZ();
        for (int x = cx + 3; x <= cx + 6; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                new Location(world, x, cy, z).getBlock().setType(Material.OBSIDIAN);
                boolean edge = x == cx + 6 || z == cz - 2 || z == cz + 2;
                new Location(world, x, cy + 1, z).getBlock().setType(edge ? Material.GLASS : Material.AIR);
                new Location(world, x, cy + 2, z).getBlock().setType(edge ? Material.GLASS : Material.AIR);
            }
        }
    }

    private boolean isJailArea(Location location) {
        int count = data.getInt("jail.nextCell", 0);
        for (int index = 0; index < count; index++) {
            Location cell = jailCell(index);
            int dx = location.getBlockX() - cell.getBlockX();
            int dz = Math.abs(location.getBlockZ() - cell.getBlockZ());
            if (sameWorld(cell, location) && dx >= -4 && dx <= 7 && Math.abs(location.getBlockY() - cell.getBlockY()) <= 5 && dz <= 4) {
                return true;
            }
        }
        return false;
    }

    private boolean heal(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        healPlayer(target);
        Text.msg(sender, "&aHealed &f" + target.getName() + "&a.");
        return true;
    }

    private boolean feed(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        feedPlayer(target);
        Text.msg(sender, "&aFed &f" + target.getName() + "&a.");
        return true;
    }

    private boolean fly(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        Player target = target(sender, args, 0);
        if (target == null) {
            return true;
        }
        boolean enabled = !target.getAllowFlight();
        target.setAllowFlight(enabled);
        if (!enabled) {
            target.setFlying(false);
        }
        Text.msg(sender, "&aFly " + (enabled ? "aan" : "uit") + " for &f" + target.getName() + "&a.");
        return true;
    }

    private boolean gamemode(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cGebruik: /gm <survival|creative|adventure|spectator> [player]");
            return true;
        }
        Player target = target(sender, args, 1);
        if (target == null) {
            return true;
        }
        GameMode mode = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "c", "creative", "1" -> GameMode.CREATIVE;
            case "a", "adventure", "2" -> GameMode.ADVENTURE;
            case "sp", "spectator", "3" -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
        target.setGameMode(mode);
        Text.msg(sender, "&aGamemode van &f" + target.getName() + " &agezet naar &f" + mode.name().toLowerCase(Locale.ROOT) + "&a.");
        return true;
    }

    private boolean time(CommandSender sender, long time, String label) {
        if (!hasAdmin(sender)) {
            return true;
        }
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        world.setTime(time);
        Text.msg(sender, "&aTime set to &f" + label + "&a.");
        return true;
    }

    private boolean weather(CommandSender sender, boolean rain) {
        if (!hasAdmin(sender)) {
            return true;
        }
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        world.setStorm(rain);
        Text.msg(sender, rain ? "&aRain started." : "&aWeather set to clear.");
        return true;
    }

    private boolean speed(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /speed <walk|fly> <1-10> [player]");
            return true;
        }
        Player target = target(sender, args, 2);
        if (target == null) {
            return true;
        }
        float value;
        try {
            value = Math.max(1, Math.min(10, Integer.parseInt(args[1]))) / 10.0F;
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cSpeed moet 1-10 zijn.");
            return true;
        }
        if (args[0].equalsIgnoreCase("fly")) {
            target.setFlySpeed(value);
        } else {
            target.setWalkSpeed(value);
        }
        Text.msg(sender, "&aSpeed bijgewerkt.");
        return true;
    }

    private boolean trash(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.essentials.trash")) {
            Text.msg(player, "&cGeen permissie.");
            return true;
        }
        player.openInventory(Bukkit.createInventory(null, 54, Text.color("&8Trash")));
        return true;
    }

    private boolean menu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        openMainMenu(player);
        return true;
    }

    private void openMainMenu(Player player) {
        MainMenu holder = new MainMenu();
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&4Bloodbound Menu"));
        holder.inventory(inventory);
        setMenuHeader(inventory, 0, "&4Survival");
        addMenuButton(player, holder, inventory, 1, Material.COMPASS, "&cSpawn", "spawn", "&7Return to the main spawn.");
        addMenuButton(player, holder, inventory, 2, Material.GRASS_BLOCK, "&2Wilderness", "rtp", "&7Teleport into the SMP world.");
        addMenuButton(player, holder, inventory, 3, Material.RED_BED, "&eHomes", "homes", "&7Manage your saved homes.");
        addMenuButton(player, holder, inventory, 4, Material.ENDER_CHEST, "&dTeleport Requests", "tpa", "&7Request player teleports.");
        addMenuButton(player, holder, inventory, 5, Material.WOODEN_SWORD, "&aStarter Kit", "starterkit", "&7Claim your early-game kit.");
        addMenuButton(player, holder, inventory, 6, Material.NETHER_STAR, "&6Hub", "hub", "&7Open the server hub navigator.");
        addMenuButton(player, holder, inventory, 7, Material.COMPASS, "&6Personal Goals", "goals", "&7See your next recommended objectives.");
        addMenuButton(player, holder, inventory, 8, Material.SHIELD, "&cRecovery Kit", "recoverykit", "&7Rookie and low-heart recovery supplies.");

        setMenuHeader(inventory, 9, "&6Economy");
        addMenuButton(player, holder, inventory, 10, Material.EMERALD, "&aShop", "shop", "&7Buy and sell basic supplies.");
        addMenuButton(player, holder, inventory, 11, Material.HOPPER, "&aQuick Sell", "sell", "&7Sell items through the sell UI.");
        addMenuButton(player, holder, inventory, 12, Material.CHEST, "&eAuction House", "ah", "&7Browse and list player auctions.");
        addMenuButton(player, holder, inventory, 13, Material.GOLD_INGOT, "&eBalance", "balance", "&7View your active economy balance.");
        addMenuButton(player, holder, inventory, 14, Material.DIAMOND_SWORD, "&4Bounties", "bounties", "&7Hunt high-value heart targets.");
        addMenuButton(player, holder, inventory, 15, Material.IRON_SWORD, "&6Rookie Contracts", "rookie", "&7Complete your first combat-ready objectives.");

        setMenuHeader(inventory, 18, "&5Progression");
        addMenuButton(player, holder, inventory, 19, Material.EXPERIENCE_BOTTLE, "&bSkills", "skills", "&7Open your skilltree.");
        addMenuButton(player, holder, inventory, 20, Material.NETHERITE_PICKAXE, "&dAbilities", "abilities", "&7View tool-bound abilities.");
        addMenuButton(player, holder, inventory, 21, Material.NETHER_STAR, "&6Collection Log", "collection", "&7Track relics and rare finds.");
        addMenuButton(player, holder, inventory, 22, Material.PAPER, "&cContracts", "contracts", "&7Take risky high-reward tasks.");
        addMenuButton(player, holder, inventory, 23, Material.IRON_INGOT, "&aResource Orders", "orders", "&7Deliver requested resources.");
        addMenuButton(player, holder, inventory, 24, Material.GOLDEN_APPLE, "&eDaily Rewards", "login", "&7Claim login streak rewards.");
        addMenuButton(player, holder, inventory, 25, Material.COMPASS, "&bHUD", "hud", "&7Customize your on-screen HUD.");
        addMenuButton(player, holder, inventory, 26, Material.WRITTEN_BOOK, "&fMechanics Guide", "mechanics", "&7Read the Bloodbound systems guide.");

        setMenuHeader(inventory, 27, "&9Minigames");
        addMenuButton(player, holder, inventory, 28, Material.GRASS_BLOCK, "&aSkyblock", "skyblock", "&7Create or visit your island.");
        addMenuButton(player, holder, inventory, 29, Material.RED_BED, "&cBedWars", "bw join", "&7Join Bloodbound BedWars.");
        addMenuButton(player, holder, inventory, 30, Material.TNT, "&4TNT Run", "tntrun join", "&7Join TNT Run.");
        addMenuButton(player, holder, inventory, 31, Material.SNOWBALL, "&fSpleef", "spleef join", "&7Join snowball Spleef.");
        addMenuButton(player, holder, inventory, 32, Material.IRON_SWORD, "&cSkirmish", "skirmish join", "&7Practice PvP without risking hearts or gear.");

        setMenuHeader(inventory, 36, "&8Endgame");
        addMenuButton(player, holder, inventory, 37, Material.NETHER_STAR, "&4Endboss Ritual", "endboss ritual", "&7Learn the physical boss ritual.");
        addMenuButton(player, holder, inventory, 38, Material.NETHER_STAR, "&dBoss Shard Shop", "opshop", "&7Spend Boss Shards on endgame items.");
        addMenuButton(player, holder, inventory, 39, Material.EXPERIENCE_BOTTLE, "&6Legacy", "legacy", "&7Season and Hall of Fame progress.");
        addMenuButton(player, holder, inventory, 40, Material.COMPASS, "&eCommand List", "commands", "&7Show commands you can use.");
        player.openInventory(inventory);
    }

    private void setMenuHeader(Inventory inventory, int slot, String name) {
        inventory.setItem(slot, item(Material.RED_STAINED_GLASS_PANE, name, "&8BloodboundSMP"));
    }

    private void addMenuButton(Player player, MainMenu holder, Inventory inventory, int slot, Material material, String name, String command, String... lore) {
        String root = command.split("\\s+", 2)[0];
        if (!canSeeRootCommand(player, root)) {
            return;
        }
        inventory.setItem(slot, item(material, name, lore));
        holder.command(slot, command);
    }

    private void handleMainMenuClick(Player player, MainMenu menu, int slot) {
        String command = menu.command(slot);
        if (command == null || command.isBlank()) {
            return;
        }
        player.closeInventory();
        Bukkit.dispatchCommand(player, command);
    }

    private boolean openAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!hasAdmin(sender)) {
            return true;
        }
        AdminMenu holder = new AdminMenu();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Admin UI"));
        holder.inventory(inventory);
        inventory.setItem(10, item(Material.GOLDEN_APPLE, "&aHeal", "&7Heal yourself."));
        inventory.setItem(11, item(Material.COOKED_BEEF, "&aFeed", "&7Refill hunger."));
        inventory.setItem(12, item(Material.EXPERIENCE_BOTTLE, "&eDay", "&7Set daytime."));
        inventory.setItem(13, item(Material.ECHO_SHARD, "&9Night", "&7Set night."));
        inventory.setItem(14, item(Material.SUNFLOWER, "&eSun", "&7Set clear weather."));
        inventory.setItem(15, item(Material.WATER_BUCKET, "&bRain", "&7Set rain."));
        inventory.setItem(16, item(Material.DIAMOND, "&bCreative", "&7Set creative mode."));
        inventory.setItem(20, item(Material.ENDER_CHEST, "&5Enderchest", "&7Open your enderchest."));
        inventory.setItem(21, item(Material.BARRIER, "&cTrash", "&7Open trash."));
        inventory.setItem(22, item(Material.DIRT, "&aSurvival", "&7Gamemode survival."));
        inventory.setItem(23, item(Material.ELYTRA, "&fFly toggle", "&7Toggle fly."));
        inventory.setItem(24, item(Material.COMPASS, "&dNo-clip", "&7Toggle spectator no-clip."));
        inventory.setItem(25, item(Material.DIAMOND_ORE, "&bFake ores", "&7Choose ore type and place bait veins."));
        inventory.setItem(26, item(Material.NETHERITE_PICKAXE, "&6God tools", "&7Receive high-end staff tools."));
        player.openInventory(inventory);
        return true;
    }

    private Player target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = Bukkit.getPlayerExact(args[index]);
            if (target == null) {
                Text.msg(sender, "&cSpeler niet online.");
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        Text.msg(sender, "&cGebruik een spelernaam.");
        return null;
    }

    private void loadBbEditPermissions() {
        bbEditAllowed.clear();
        for (String key : data.keys()) {
            if (!key.startsWith("bbedit.allowed.") || !Boolean.parseBoolean(data.getString(key, "false"))) {
                continue;
            }
            try {
                bbEditAllowed.add(UUID.fromString(key.substring("bbedit.allowed.".length())));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void bbEditCommand(Player player, String input) {
        String[] args = input.isBlank() ? new String[0] : input.split("\\s+");
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("permission")) {
            bbEditPermission(player, args);
            return;
        }
        if (!hasBbEditAccess(player)) {
            Text.msg(player, "&cBBEdit access is locked. An Owner must use &f//permission " + player.getName() + " yes&c.");
            return;
        }
        switch (sub) {
            case "wand" -> {
                player.getInventory().addItem(item(Material.BLAZE_ROD, "&4BloodboundEdit Wand", "&7Left-click: pos1", "&7Right-click: pos2"));
                Text.msg(player, "&aBBEdit wand granted.");
            }
            case "pos1" -> {
                selection(player).pos1 = player.getLocation().getBlock().getLocation();
                Text.msg(player, "&aBBEdit pos1 set to &f" + shortLocation(selection(player).pos1));
            }
            case "pos2" -> {
                selection(player).pos2 = player.getLocation().getBlock().getLocation();
                Text.msg(player, "&aBBEdit pos2 set to &f" + shortLocation(selection(player).pos2));
            }
            case "set" -> {
                Material material = args.length > 1 ? parseMaterial(args[1]) : null;
                if (material == null) {
                    Text.msg(player, "&cUsage: //set <material>");
                    return;
                }
                applySelection(player, "set " + material.name(), (block, edge) -> material);
            }
            case "replace" -> {
                Material from = args.length > 1 ? parseMaterial(args[1]) : null;
                Material to = args.length > 2 ? parseMaterial(args[2]) : null;
                if (from == null || to == null) {
                    Text.msg(player, "&cUsage: //replace <from> <to>");
                    return;
                }
                applySelection(player, "replace " + from.name() + " " + to.name(), (block, edge) -> block.getType() == from ? to : null);
            }
            case "walls" -> {
                Material material = args.length > 1 ? parseMaterial(args[1]) : null;
                if (material == null) {
                    Text.msg(player, "&cUsage: //walls <material>");
                    return;
                }
                applySelection(player, "walls " + material.name(), (block, edge) -> edge.wall() ? material : null);
            }
            case "outline" -> {
                Material material = args.length > 1 ? parseMaterial(args[1]) : null;
                if (material == null) {
                    Text.msg(player, "&cUsage: //outline <material>");
                    return;
                }
                applySelection(player, "outline " + material.name(), (block, edge) -> edge.outline() ? material : null);
            }
            case "cut" -> {
                bbCopy(player, true);
                applySelection(player, "cut", (block, edge) -> Material.AIR);
            }
            case "copy" -> bbCopy(player, false);
            case "paste" -> bbPaste(player);
            case "sphere" -> bbSphere(player, args);
            case "undo" -> bbUndo(player);
            case "limit" -> {
                if (args.length > 2 && args[1].equalsIgnoreCase("max")) {
                    if (MitchSMP.ranks().getRank(player.getUniqueId()) != MitchRank.OWNER) {
                        Text.msg(player, "&cOnly the Owner rank can change the BBEdit maximum limit.");
                        return;
                    }
                    int maxLimit = parseInt(args[2], bbMaxLimit(), 100, 5_000_000);
                    data.set("bbedit.limit.max", maxLimit);
                    if (bbLimit() > maxLimit) {
                        data.set("bbedit.limit", maxLimit);
                    }
                    data.saveSoon(this, 20L);
                    Text.msg(player, "&aBBEdit maximum block limit: &f" + maxLimit);
                    return;
                }
                int limit = args.length > 1 ? parseInt(args[1], bbLimit(), 100, bbMaxLimit()) : bbLimit();
                data.set("bbedit.limit", limit);
                data.saveSoon(this, 20L);
                Text.msg(player, "&aBBEdit block limit: &f" + limit + " &7(max &f" + bbMaxLimit() + "&7)");
            }
            default -> {
                Text.msg(player, "&4BloodboundEdit &7commands:");
                Text.msg(player, "&f//permission <player> yes|no &7Owner only");
                Text.msg(player, "&f//wand //pos1 //pos2 //set //replace //walls //outline");
                Text.msg(player, "&f//copy //paste //cut //sphere //undo //limit [blocks]");
                Text.msg(player, "&f//limit max <blocks> &7Owner only");
            }
        }
    }

    private void bbEditPermission(Player owner, String[] args) {
        if (MitchSMP.ranks().getRank(owner.getUniqueId()) != MitchRank.OWNER) {
            Text.msg(owner, "&cOnly the Owner rank can grant BBEdit access.");
            return;
        }
        if (args.length < 3) {
            Text.msg(owner, "&cUsage: //permission <player> yes|no");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(owner, "&cPlayer must be online for BBEdit permission changes.");
            return;
        }
        boolean allow = args[2].equalsIgnoreCase("yes") || args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("on");
        if (allow) {
            bbEditAllowed.add(target.getUniqueId());
        } else {
            bbEditAllowed.remove(target.getUniqueId());
        }
        data.set("bbedit.allowed." + target.getUniqueId(), allow);
        data.saveSoon(this, 20L);
        Text.msg(owner, "&aBBEdit access for &f" + target.getName() + " &ais now &f" + (allow ? "enabled" : "disabled") + "&a.");
        Text.msg(target, allow ? "&aOwner granted you BBEdit access." : "&cOwner revoked your BBEdit access.");
    }

    private boolean handleBbEditWand(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!hasBbEditAccess(player) || !isBbEditWand(event.getItem()) || event.getClickedBlock() == null) {
            return false;
        }
        event.setCancelled(true);
        BbSelection selection = selection(player);
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.pos1 = event.getClickedBlock().getLocation();
            Text.msg(player, "&aBBEdit pos1 set to &f" + shortLocation(selection.pos1));
            return true;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.pos2 = event.getClickedBlock().getLocation();
            Text.msg(player, "&aBBEdit pos2 set to &f" + shortLocation(selection.pos2));
            return true;
        }
        return true;
    }

    private boolean isBbEditWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && Text.stripColorCodes(meta.getDisplayName()).toLowerCase(Locale.ROOT).contains("bloodboundedit");
    }

    private boolean hasBbEditAccess(Player player) {
        return MitchSMP.ranks().getRank(player.getUniqueId()) == MitchRank.OWNER || bbEditAllowed.contains(player.getUniqueId());
    }

    private BbSelection selection(Player player) {
        return bbSelections.computeIfAbsent(player.getUniqueId(), ignored -> new BbSelection());
    }

    private int bbLimit() {
        return Math.max(100, Math.min(bbMaxLimit(), data.getInt("bbedit.limit", 50000)));
    }

    private int bbMaxLimit() {
        return Math.max(100, data.getInt("bbedit.limit.max", 250000));
    }

    private Material parseMaterial(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('-', '_');
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void applySelection(Player player, String label, BbMaterialResolver resolver) {
        BbSelection selection = selection(player);
        Region region = region(player, selection);
        if (region == null) {
            return;
        }
        if (region.volume() > bbLimit()) {
            Text.msg(player, "&cSelection too large: &f" + region.volume() + " &cblocks. Limit: &f" + bbLimit() + "&c.");
            return;
        }
        List<BbBlockChange> changes = new ArrayList<>();
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Block block = region.world.getBlockAt(x, y, z);
                    Edge edge = new Edge(x == region.minX, x == region.maxX, y == region.minY, y == region.maxY, z == region.minZ, z == region.maxZ);
                    Material to = resolver.resolve(block, edge);
                    if (to != null && block.getType() != to) {
                        changes.add(new BbBlockChange(block.getLocation(), block.getType(), to));
                    }
                }
            }
        }
        queueBbChanges(player, changes, label);
    }

    private Region region(Player player, BbSelection selection) {
        if (selection.pos1 == null || selection.pos2 == null) {
            Text.msg(player, "&cSet both positions first with &f//wand&c, &f//pos1 &cor &f//pos2&c.");
            return null;
        }
        if (selection.pos1.getWorld() == null || selection.pos2.getWorld() == null || !selection.pos1.getWorld().equals(selection.pos2.getWorld())) {
            Text.msg(player, "&cBoth BBEdit positions must be in the same world.");
            return null;
        }
        World world = selection.pos1.getWorld();
        int minY = Math.max(world.getMinHeight(), Math.min(selection.pos1.getBlockY(), selection.pos2.getBlockY()));
        int maxY = Math.min(319, Math.max(selection.pos1.getBlockY(), selection.pos2.getBlockY()));
        return new Region(world,
            Math.min(selection.pos1.getBlockX(), selection.pos2.getBlockX()),
            minY,
            Math.min(selection.pos1.getBlockZ(), selection.pos2.getBlockZ()),
            Math.max(selection.pos1.getBlockX(), selection.pos2.getBlockX()),
            maxY,
            Math.max(selection.pos1.getBlockZ(), selection.pos2.getBlockZ()));
    }

    private void queueBbChanges(Player player, List<BbBlockChange> changes, String label) {
        if (changes.isEmpty()) {
            Text.msg(player, "&7BBEdit: nothing changed.");
            return;
        }
        Deque<List<BbBlockChange>> stack = bbUndo.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        stack.push(changes);
        while (stack.size() > 10) {
            stack.removeLast();
        }
        int batchSize = data.getInt("bbedit.batch", 2500);
        final int[] index = {0};
        final org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int end = Math.min(changes.size(), index[0] + Math.max(100, batchSize));
            for (int i = index[0]; i < end; i++) {
                BbBlockChange change = changes.get(i);
                if (change.location.getWorld() != null) {
                    change.location.getBlock().setType(change.to, false);
                }
            }
            index[0] = end;
            if (index[0] >= changes.size()) {
                task[0].cancel();
                Text.msg(player, "&aBBEdit " + label + " complete: &f" + changes.size() + " &ablocks.");
                audit(player, "bbedit-" + label, changes.size() + " blocks");
            }
        }, 1L, 1L);
    }

    private void bbCopy(Player player, boolean quiet) {
        BbSelection selection = selection(player);
        Region region = region(player, selection);
        if (region == null) {
            return;
        }
        if (region.volume() > bbLimit()) {
            Text.msg(player, "&cSelection too large to copy: &f" + region.volume() + "&c.");
            return;
        }
        List<BbClipboardBlock> clipboard = new ArrayList<>();
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Material type = region.world.getBlockAt(x, y, z).getType();
                    clipboard.add(new BbClipboardBlock(x - region.minX, y - region.minY, z - region.minZ, type));
                }
            }
        }
        bbClipboards.put(player.getUniqueId(), clipboard);
        if (!quiet) {
            Text.msg(player, "&aCopied &f" + clipboard.size() + " &ablocks.");
        }
    }

    private void bbPaste(Player player) {
        List<BbClipboardBlock> clipboard = bbClipboards.get(player.getUniqueId());
        if (clipboard == null || clipboard.isEmpty()) {
            Text.msg(player, "&cClipboard is empty.");
            return;
        }
        if (clipboard.size() > bbLimit()) {
            Text.msg(player, "&cClipboard too large for current limit.");
            return;
        }
        Location origin = player.getLocation().getBlock().getLocation();
        List<BbBlockChange> changes = new ArrayList<>();
        for (BbClipboardBlock entry : clipboard) {
            Location location = new Location(origin.getWorld(), origin.getBlockX() + entry.dx, origin.getBlockY() + entry.dy, origin.getBlockZ() + entry.dz);
            Block block = location.getBlock();
            if (block.getType() != entry.material) {
                changes.add(new BbBlockChange(location, block.getType(), entry.material));
            }
        }
        queueBbChanges(player, changes, "paste");
    }

    private void bbSphere(Player player, String[] args) {
        Material material = args.length > 1 ? parseMaterial(args[1]) : null;
        int radius = args.length > 2 ? parseInt(args[2], 5, 1, 30) : 5;
        boolean hollow = args.length > 3 && args[3].equalsIgnoreCase("hollow");
        if (material == null) {
            Text.msg(player, "&cUsage: //sphere <material> [radius] [hollow]");
            return;
        }
        Location center = player.getLocation().getBlock().getLocation();
        int diameter = radius * 2 + 1;
        long volume = (long) diameter * diameter * diameter;
        if (volume > bbLimit()) {
            Text.msg(player, "&cSphere bounding box too large: &f" + volume + "&c.");
            return;
        }
        List<BbBlockChange> changes = new ArrayList<>();
        double max = radius * radius + 0.5D;
        double inner = Math.max(0.0D, (radius - 1) * (radius - 1) - 0.5D);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = x * x + y * y + z * z;
                    if (distance > max || (hollow && distance < inner)) {
                        continue;
                    }
                    Location location = new Location(center.getWorld(), center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    Block block = location.getBlock();
                    if (block.getType() != material) {
                        changes.add(new BbBlockChange(location, block.getType(), material));
                    }
                }
            }
        }
        queueBbChanges(player, changes, "sphere");
    }

    private void bbUndo(Player player) {
        Deque<List<BbBlockChange>> stack = bbUndo.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            Text.msg(player, "&cNothing to undo.");
            return;
        }
        List<BbBlockChange> previous = stack.pop();
        List<BbBlockChange> reverse = new ArrayList<>();
        for (BbBlockChange change : previous) {
            reverse.add(new BbBlockChange(change.location, change.to, change.from));
        }
        queueBbChanges(player, reverse, "undo");
    }

    private boolean hasAdmin(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.essentials.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return false;
        }
        return true;
    }

    private boolean shouldBlockAdminWorldDamage(Player player, Material material) {
        if (player == null || material == null || !MitchSMP.permissions().isAdminMode(player) || isTestWorld(player.getWorld())) {
            return false;
        }
        String name = material.name();
        return name.equals("TNT")
            || name.equals("TNT_MINECART")
            || name.equals("END_CRYSTAL")
            || name.equals("RESPAWN_ANCHOR")
            || name.equals("FIRE_CHARGE")
            || name.equals("FLINT_AND_STEEL")
            || name.equals("LAVA_BUCKET")
            || name.equals("LAVA")
            || name.equals("FIRE")
            || name.equals("SOUL_FIRE")
            || name.contains("EXPLOSIVE");
    }

    private boolean shouldBlockAdminContainerEdit(Player player, Inventory top, InventoryClickEvent event) {
        if (player == null || top == null || event == null || !MitchSMP.permissions().isAdminMode(player) || isTestWorld(player.getWorld())) {
            return false;
        }
        if (!isPlacedContainer(top)) {
            return false;
        }
        return event.getRawSlot() < top.getSize() || event.isShiftClick();
    }

    private boolean isPlacedContainer(Inventory inventory) {
        try {
            Object location = inventory.getClass().getMethod("getLocation").invoke(inventory);
            return location instanceof Location;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void healPlayer(Player player) {
        double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        player.setHealth(max);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private void feedPlayer(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Text.color(line));
            }
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String itemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "AIR";
        }
        ItemMeta meta = item.getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? Text.stripColorCodes(meta.getDisplayName()) : pretty(item.getType());
        return item.getAmount() + "x " + name;
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String clip(String input, int length) {
        if (input == null) {
            return "";
        }
        return input.length() <= length ? input : input.substring(0, Math.max(0, length - 3)) + "...";
    }

    private String safeAudit(String input) {
        String value = input == null ? "none" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
        if (value.length() > 40) {
            value = value.substring(0, 40);
        }
        return value.isBlank() ? "none" : value;
    }

    private Material material(String input) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
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
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException exception) {
            return null;
        }
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
            String part = parts[i];
            if (part == null || part.isBlank() || part.equals("-")) {
                continue;
            }
            try {
                contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(part));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Could not load saved inventory slot " + i + ": " + exception.getMessage());
            }
        }
        return contents;
    }

    private int parsePage(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private int parseInt(String input, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(input)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private World jailWorld() {
        World world = Bukkit.getWorld(JAIL_WORLD);
        if (world != null) {
            return world;
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(JAIL_WORLD);
            Class<?> generatorClass = Class.forName("org.bukkit.generator.ChunkGenerator");
            creatorClass.getMethod("generator", generatorClass).invoke(creator, new JailChunkGenerator());
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, false);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create jail world: " + exception.getMessage());
        }
        return Bukkit.getWorld(JAIL_WORLD);
    }

    private static final class JailChunkGenerator extends ChunkGenerator {
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
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, 0.5D, 82.0D, 0.5D);
        }
    }

    private static final class AdminMenu implements InventoryHolder {
        private Inventory inventory;

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MainMenu implements InventoryHolder {
        private final Map<Integer, String> commands = new ConcurrentHashMap<>();
        private Inventory inventory;

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        void command(int slot, String command) {
            commands.put(slot, command);
        }

        String command(int slot) {
            return commands.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record FakeOreSelection(String selector, int radius, int seconds, Material ore) {
    }

    private record SmpWorldConfirmation(String name, int stage, long expiresAt) {
    }

    private record FakeOreMenu(String selector, int radius, int seconds) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class AuditMenu implements InventoryHolder {
        private final int page;
        private final String filter;
        private Inventory inventory;

        AuditMenu(int page, String filter) {
            this.page = page;
            this.filter = filter == null ? "" : filter;
        }

        int page() {
            return page;
        }

        String filter() {
            return filter;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class AuditCategoryMenu implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ShopMenu implements InventoryHolder {
        private final Map<Integer, ShopOffer> offers = new ConcurrentHashMap<>();
        private final Map<Integer, String> tabButtons = new ConcurrentHashMap<>();
        private final String tab;
        private final boolean editor;
        private final double editPrice;
        private final int editAmount;
        private Inventory inventory;

        ShopMenu(String tab, boolean editor, double editPrice, int editAmount) {
            this.tab = tab;
            this.editor = editor;
            this.editPrice = editPrice;
            this.editAmount = editAmount;
        }

        void offer(int slot, ShopOffer offer) {
            offers.put(slot, offer);
        }

        ShopOffer offer(int slot) {
            return offers.get(slot);
        }

        void tabButton(int slot, String tab) {
            tabButtons.put(slot, tab);
        }

        String tabButton(int slot) {
            return tabButtons.get(slot);
        }

        String tab() {
            return tab;
        }

        boolean editor() {
            return editor;
        }

        double editPrice() {
            return editPrice;
        }

        int editAmount() {
            return editAmount;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record ShopOffer(ItemStack item, double price, int amount) {
    }

    private record AuditView(int page, String filter) {
    }

    private static final class BbSelection {
        private Location pos1;
        private Location pos2;
    }

    private record Region(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long volume() {
            return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        }
    }

    private record Edge(boolean minX, boolean maxX, boolean minY, boolean maxY, boolean minZ, boolean maxZ) {
        boolean wall() {
            return minX || maxX || minZ || maxZ;
        }

        boolean outline() {
            int count = 0;
            if (minX || maxX) {
                count++;
            }
            if (minY || maxY) {
                count++;
            }
            if (minZ || maxZ) {
                count++;
            }
            return count >= 2;
        }
    }

    private record BbBlockChange(Location location, Material from, Material to) {
    }

    private record BbClipboardBlock(int dx, int dy, int dz, Material material) {
    }

    @FunctionalInterface
    private interface BbMaterialResolver {
        Material resolve(Block block, Edge edge);
    }
}



