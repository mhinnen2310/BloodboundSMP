package nl.mitchsmp.endboss;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import nl.mitchsmp.core.api.BossShardService;
import nl.mitchsmp.core.api.CorruptedHeartService;
import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class EndBossPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String WORLD_PREFIX = "mitchsmp_hellboss_";
    private static final String HOF_WORLD = "mitchsmp_hall_of_fame";
    private static final long CONFIRM_TIMEOUT_MS = 60_000L;
    private static final long RITUAL_CHEST_TIMEOUT_MS = 15L * 60L * 1000L;
    private static final int ARENA_Y = 80;
    private static final int ARENA_RADIUS = 31;
    private static final int MAX_WAVES = 4;
    private static final int BOSS_SHARD_MODEL = 910001;
    private static final int CORRUPTED_HEART_MODEL = 910002;
    private final Random random = new Random();
    private final Map<UUID, PendingAction> pending = new HashMap<>();
    private final Map<String, RitualChest> ritualChests = new HashMap<>();
    private final Set<String> ritualFireBlocks = new LinkedHashSet<>();
    private final Set<UUID> lockedPlayers = new LinkedHashSet<>();
    private final Set<UUID> respawnToMain = new LinkedHashSet<>();
    private final Set<UUID> respawnToSpectator = new LinkedHashSet<>();
    private PropertiesFile config;
    private NamespacedKey wardKey;
    private NamespacedKey relicKey;
    private Session session;

    @Override
    public void onEnable() {
        config = new PropertiesFile(getDataFolder().toPath().resolve("tuning.properties"));
        wardKey = new NamespacedKey(this, "infernal_ward");
        relicKey = new NamespacedKey(this, "hell_relic");
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("endboss") != null) {
            getCommand("endboss").setExecutor(this);
            getCommand("endboss").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tickRitualChests, 200L, 200L);
    }

    @Override
    public void onDisable() {
        cleanupSession(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("endboss")) {
            return false;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "ritual", "help" -> ritual(sender);
            case "start", "open" -> start(sender);
            case "join" -> join(sender);
            case "confirm" -> confirm(sender);
            case "status" -> status(sender);
            case "drops" -> drops(sender);
            case "force", "forcestart" -> force(sender);
            case "end", "cancel" -> end(sender);
            case "config" -> config(sender, args);
            default -> {
                Text.msg(sender, "&cUsage: /endboss ritual, start, join, confirm, status");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (MitchSMP.permissions().has(sender, "mitchsmp.endboss.admin")) {
                return Tab.complete(args[0], "ritual", "start", "join", "confirm", "status", "drops", "force", "end", "config");
            }
            return Tab.complete(args[0], "ritual", "start", "join", "confirm", "status");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[1], "min_players", "boss_health", "required_hearts", "required_boss_shards", "required_corrupted_hearts", "required_enchanted_apples");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[2], "1", "2", "3", "4", "8", "16", "64", "96", "5000");
        }
        return List.of();
    }

    private boolean ritual(CommandSender sender) {
        Text.msg(sender, "&4Infernal Sovereign ritual:");
        Text.msg(sender, "&7- Trap a hostile mob on one block.");
        Text.msg(sender, "&7- Place Nether Brick Walls north, south, east and west of it.");
        Text.msg(sender, "&7- Place one Redstone Torch on each wall to spawn the Ritual Chest.");
        Text.msg(sender, "&7- Add: &f" + requiredBossShards() + " Boss Shards&7, &f" + requiredCorruptedHearts() + " Corrupted Hearts&7, &f1 Dragon Egg&7, &f1 Nether Star&7 and &f" + requiredEnchantedApples() + " Enchanted Golden Apples&7.");
        Text.msg(sender, "&cBreak the ready Ritual Chest to enter. There is no return: victory or death.");
        return true;
    }

    private boolean start(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.endboss.admin")) {
            Text.msg(player, "&cThe Infernal Sovereign can only be opened through the physical ritual. Use &f/endboss ritual&c.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.endboss.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        if (session != null) {
            Text.msg(player, session.state == State.ASSEMBLING ? "&cA ritual party already exists. Use /endboss join." : "&cA boss fight is already running.");
            return true;
        }
        if (!canOpenRitual(player, true)) {
            return true;
        }
        pending.put(player.getUniqueId(), new PendingAction(PendingType.START));
        Text.msg(player, "&4This opens the Infernal Sovereign ritual and consumes your ritual items.");
        Text.msg(player, "&cAfter it starts, nobody can escape the hell world with commands.");
        Text.msg(player, "&eType &f/endboss confirm &eto open the ritual party.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 0.6F);
        return true;
    }

    private boolean join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.endboss.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        if (session == null || session.state != State.ASSEMBLING) {
            Text.msg(player, "&cThere is no open ritual party.");
            return true;
        }
        if (session.participants.contains(player.getUniqueId())) {
            Text.msg(player, "&aYou are already in the ritual party.");
            return true;
        }
        pending.put(player.getUniqueId(), new PendingAction(PendingType.JOIN));
        Text.msg(player, "&4You are about to join a one-way endgame bossfight.");
        Text.msg(player, "&cAfter it starts, /home, /spawn, /back, /tpa and similar commands are blocked.");
        Text.msg(player, "&eType &f/endboss confirm &eto join.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 0.7F);
        return true;
    }

    private boolean confirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        PendingAction action = pending.remove(player.getUniqueId());
        if (action == null || System.currentTimeMillis() - action.createdAt > CONFIRM_TIMEOUT_MS) {
            Text.msg(player, "&cNo active confirmation. Use /endboss start or /endboss join again.");
            return true;
        }
        if (action.type == PendingType.START) {
            if (session != null) {
                Text.msg(player, "&cEr is al een ritual party of bossfight.");
                return true;
            }
            if (!canOpenRitual(player, true)) {
                return true;
            }
            consumeRitualItems(player);
            session = new Session(player.getUniqueId());
            session.participants.add(player.getUniqueId());
            Bukkit.broadcastMessage(Text.color("&8[&4EndBoss&8] &c" + player.getName() + " opened the Infernal Sovereign ritual. Use &f/endboss join &cto join."));
            Text.msg(player, "&aRitual party geopend. Spelers: &f1/" + minPlayers() + "&a.");
            tryStartAutomatically();
            return true;
        }
        if (session == null || session.state != State.ASSEMBLING) {
            Text.msg(player, "&cDe ritual party bestaat niet meer.");
            return true;
        }
        session.participants.add(player.getUniqueId());
        broadcastParty("&c" + player.getName() + " joined de ritual party. Spelers: &f" + onlinePartySize() + "/" + minPlayers());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
        tryStartAutomatically();
        return true;
    }

    private boolean status(CommandSender sender) {
        if (session == null) {
            Text.msg(sender, "&7No active endboss ritual. Use &f/endboss ritual&7.");
            return true;
        }
        Text.msg(sender, "&4EndBoss status: &f" + session.state.name().toLowerCase(Locale.ROOT));
        Text.msg(sender, "&7Spelers: &f" + onlinePartySize() + "/" + minPlayers() + "&7, world: &f" + (session.worldName == null ? "nog niet gestart" : session.worldName));
        return true;
    }

    private boolean force(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.endboss.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (session == null || session.state != State.ASSEMBLING) {
            Text.msg(sender, "&cGeen ritual party om te starten.");
            return true;
        }
        startFight();
        Text.msg(sender, "&aEndboss force-start uitgevoerd.");
        return true;
    }

    private boolean drops(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.endboss.admin")) {
            Text.msg(sender, "&cNo permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        Inventory inventory = Bukkit.createInventory(new DropsEditor(), 54, Text.color("&4Endboss Drops Editor"));
        loadDropEditor(inventory);
        inventory.setItem(49, editorInfo());
        player.openInventory(inventory);
        Text.msg(player, "&7Place boss drops in slots 1-45. Close the menu to save.");
        return true;
    }

    private boolean end(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.endboss.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        cleanupSession(true);
        Text.msg(sender, "&aEndboss sessie opgeruimd.");
        return true;
    }

    private boolean config(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.endboss.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /endboss config <key> <value>");
            Text.msg(sender, "&7Keys: min_players, boss_health, required_hearts, required_boss_shards, required_corrupted_hearts, required_enchanted_apples");
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("min_players", "boss_health", "required_hearts", "required_boss_shards", "required_corrupted_hearts", "required_enchanted_apples").contains(key)) {
            Text.msg(sender, "&cUnknowne key.");
            return true;
        }
        double value = parseDouble(args[2], -1.0D);
        if (value < 0.0D) {
            Text.msg(sender, "&cGebruik een positief getal.");
            return true;
        }
        config.set(key, value);
        config.save();
        Text.msg(sender, "&aEndboss config aangepast: &f" + key + " = " + value);
        return true;
    }

    private void tryStartAutomatically() {
        if (session != null && session.state == State.ASSEMBLING && onlinePartySize() >= minPlayers()) {
            startFight();
        }
    }

    private void startFight() {
        if (session == null || session.state != State.ASSEMBLING) {
            return;
        }
        session.state = State.RUNNING;
        session.worldName = WORLD_PREFIX + System.currentTimeMillis();
        World world = createBossWorld(session.worldName);
        if (world == null) {
            Bukkit.broadcastMessage(Text.color("&8[&4EndBoss&8] &cBoss world could not be created. Ritual cancelled."));
            cleanupSession(false);
            return;
        }
        disableNaturalSpawns(world);
        buildArena(world);
        List<Player> players = onlineParticipants();
        if (players.isEmpty()) {
            cleanupSession(false);
            return;
        }
        session.startedAtMillis = System.currentTimeMillis();
        for (int index = 0; index < players.size(); index++) {
            Player player = players.get(index);
            lockedPlayers.add(player.getUniqueId());
            Location spawn = playerSpawn(world, index, players.size());
            player.teleport(spawn);
            player.setFallDistance(0.0F);
            player.sendTitle(Text.color("&4Infernal Sovereign"), Text.color("&cNo way back"), 10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2F, 0.65F);
        }
        LivingEntity boss = spawnBoss(world);
        session.bossId = boss == null ? null : boss.getUniqueId();
        session.task = Bukkit.getScheduler().runTaskTimer(this, this::tickFight, 40L, 40L);
        Bukkit.broadcastMessage(Text.color("&8[&4EndBoss&8] &cThe Infernal Sovereign has awakened in a temporary hell world."));
    }

    private LivingEntity spawnBoss(World world) {
        Entity entity = world.spawnEntity(new Location(world, 0.5D, 84.0D, 0.5D), EntityType.WARDEN);
        if (!(entity instanceof LivingEntity boss)) {
            return null;
        }
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(bossHealth());
        }
        boss.setHealth(bossHealth());
        boss.setCustomName(Text.color("&4Infernal Sovereign"));
        boss.setCustomNameVisible(true);
        setEntityFlag(boss, "setGlowing", true);
        setEntityFlag(boss, "setRemoveWhenFarAway", false);
        boss.setFireTicks(0);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 60 * 60, 0, false, true, true));
        return boss;
    }

    private void tickFight() {
        if (session == null || session.state != State.RUNNING) {
            return;
        }
        World world = Bukkit.getWorld(session.worldName);
        if (world == null) {
            cleanupSession(false);
            return;
        }
        purgeLooseEntities(world);
        enforceArenaBounds(world);
        session.ticks += 40;
        for (Player player : onlineParticipants()) {
            if (sameWorld(player.getLocation(), new Location(world, 0, 80, 0))) {
                world.spawnParticle(Particle.ASH, player.getLocation(), 12, 0.5D, 0.8D, 0.5D, 0.02D);
            }
        }
        LivingEntity boss = activeBoss(world);
        if (boss != null) {
            boss.setFireTicks(0);
            world.spawnParticle(particle("CRIMSON_SPORE", Particle.FLAME), boss.getLocation().add(0.0D, 1.0D, 0.0D), 35, 1.1D, 1.4D, 1.1D, 0.02D);
        }
        maybeSpawnWave(world);
        if (session.ticks % 200 == 0) {
            chargedAttack(world);
        }
        if (onlineParticipantsInWorld(world).isEmpty()) {
            Bukkit.broadcastMessage(Text.color("&8[&4EndBoss&8] &cThe party wiped. The hell world is closing."));
            cleanupSession(true);
        }
    }

    private void maybeSpawnWave(World world) {
        if (session.wave >= MAX_WAVES) {
            return;
        }
        int nextWaveAt = 160 + (session.wave * 240);
        if (session.ticks < nextWaveAt) {
            return;
        }
        session.wave++;
        spawnWave(world, session.wave);
    }

    private void spawnWave(World world, int wave) {
        List<EntityType> types = switch (wave) {
            case 1 -> mobTypes("WITHER_SKELETON", "WITHER_SKELETON", "BLAZE");
            case 2 -> mobTypes("PIGLIN_BRUTE", "WITHER_SKELETON", "BLAZE", "BLAZE");
            case 3 -> mobTypes("PIGLIN_BRUTE", "PIGLIN_BRUTE", "WITHER_SKELETON", "WITHER_SKELETON");
            default -> mobTypes("PIGLIN_BRUTE", "PIGLIN_BRUTE", "BLAZE", "WITHER_SKELETON", "WITHER_SKELETON");
        };
        int index = 0;
        for (EntityType type : types) {
            double angle = (Math.PI * 2.0D / types.size()) * index++;
            Location location = new Location(world, Math.cos(angle) * 17.0D + 0.5D, ARENA_Y + 2.0D, Math.sin(angle) * 17.0D + 0.5D);
            Entity entity = world.spawnEntity(location, type);
            if (entity instanceof LivingEntity living) {
                setEntityFlag(living, "setRemoveWhenFarAway", false);
                living.setFireTicks(0);
                living.setCustomName(Text.color("&4Bloodbound Wave " + wave));
                living.setCustomNameVisible(false);
            }
        }
        for (Player player : onlineParticipantsInWorld(world)) {
            player.sendTitle(Text.color("&4Wave " + wave + "/" + MAX_WAVES), Text.color("&7The arena answers in blood."), 5, 40, 10);
            player.playSound(player.getLocation(), sound("ENTITY_WITHER_SPAWN", Sound.ENTITY_ENDER_DRAGON_GROWL), 0.7F, 1.4F);
        }
    }

    private LivingEntity activeBoss(World world) {
        if (session == null || session.bossId == null) {
            return null;
        }
        for (Entity entity : world.getEntities()) {
            if (session.bossId.equals(entity.getUniqueId()) && entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private List<EntityType> mobTypes(String... names) {
        List<EntityType> types = new ArrayList<>();
        for (String name : names) {
            try {
                types.add(EntityType.valueOf(name));
            } catch (IllegalArgumentException exception) {
                types.add(EntityType.ZOMBIE);
            }
        }
        return types;
    }

    private Particle particle(String name, Particle fallback) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private Sound sound(String name, Sound fallback) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private void setEntityFlag(Entity entity, String method, boolean value) {
        try {
            entity.getClass().getMethod(method, boolean.class).invoke(entity, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void disableNaturalSpawns(World world) {
        try {
            world.getClass().getMethod("setSpawnFlags", boolean.class, boolean.class).invoke(world, false, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void enforceArenaBounds(World world) {
        Location center = new Location(world, 0.5D, ARENA_Y + 2.0D, 0.5D);
        for (Player player : onlineParticipants()) {
            if (player.getWorld() == null || !world.getName().equals(player.getWorld().getName())) {
                continue;
            }
            boolean spectator = session != null && session.deadPlayers.contains(player.getUniqueId());
            Location safe = spectator ? spectatorLocation(world) : new Location(world, center.getX(), center.getY(), center.getZ(), center.getYaw(), center.getPitch());
            Location location = player.getLocation();
            if (location.getY() < ARENA_Y - 6 || horizontalDistanceSquared(location) > Math.pow(ARENA_RADIUS + 5.0D, 2.0D)) {
                player.teleport(safe);
                player.setFallDistance(0.0F);
            }
        }
        LivingEntity boss = activeBoss(world);
        if (boss != null && (boss.getLocation().getY() < ARENA_Y - 3 || horizontalDistanceSquared(boss.getLocation()) > Math.pow(ARENA_RADIUS - 2.0D, 2.0D))) {
            boss.teleport(center);
        }
    }

    private double horizontalDistanceSquared(Location location) {
        return location.getX() * location.getX() + location.getZ() * location.getZ();
    }

    private Location spectatorLocation(World world) {
        return new Location(world, 0.5D, ARENA_Y + 18.0D, 0.5D, 0.0F, 65.0F);
    }

    private void chargedAttack(World world) {
        List<Player> targets = onlineParticipantsInWorld(world);
        if (targets.isEmpty()) {
            return;
        }
        for (Player player : targets) {
            player.sendTitle(Text.color("&4Charged Attack"), Text.color(hasInfernalWard(player) ? "&6Infernal Ward dempt de klap" : "&cZoek dekking of gebruik Ward gear"), 5, 35, 8);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.75F);
            world.spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 90, 2.0D, 1.0D, 2.0D, 0.05D, Float.valueOf(1.0F));
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (session == null || session.state != State.RUNNING) {
                return;
            }
            World activeWorld = Bukkit.getWorld(session.worldName);
            if (activeWorld == null) {
                return;
            }
            for (Player player : onlineParticipantsInWorld(activeWorld)) {
                resolveChargedHit(player);
            }
        }, 35L);
    }

    private void resolveChargedHit(Player player) {
        boolean warded = hasInfernalWard(player);
        double damage = warded ? 8.0D : 18.0D;
        double floor = warded ? 8.0D : 2.0D;
        player.setHealth(Math.max(floor, player.getHealth() - damage));
        player.setFireTicks(warded ? 60 : 140);
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, warded ? 50 : 100, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, warded ? 40 : 80, warded ? 0 : 1, false, true, true));
        player.playSound(player.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.1F, warded ? 1.1F : 0.75F);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 5, 0.7D, 0.5D, 0.7D, 0.05D);
    }

    @EventHandler
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (session == null || session.state != State.RUNNING || session.bossId == null) {
            return;
        }
        if (event.getEntity() instanceof Player player && event.getDamager() != null && session.bossId.equals(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
            boolean warded = hasInfernalWard(player);
            double damage = warded ? 5.0D : 12.0D;
            double floor = warded ? 6.0D : 2.0D;
            player.setHealth(Math.max(floor, player.getHealth() - damage));
            player.setFireTicks(warded ? 20 : 80);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, warded ? 30 : 70, 0, false, true, true));
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (session == null || session.state != State.RUNNING || session.bossId == null) {
            return;
        }
        if (!session.bossId.equals(event.getEntity().getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        rewardParticipants(event.getEntity().getLocation());
        sendVictoryToHallOfFame();
        Bukkit.broadcastMessage(Text.color("&8[&4EndBoss&8] &6The Infernal Sovereign has been defeated. The hell world is resetting."));
        cleanupSession(false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!lockedPlayers.contains(player.getUniqueId())) {
            return;
        }
        if (session != null) {
            session.deadPlayers.add(player.getUniqueId());
            if (!onlineParticipantsInWorld(player.getWorld()).isEmpty()) {
                respawnToSpectator.add(player.getUniqueId());
                Text.msg(player, "&cYou fell in the bossfight. Spectating while your party survives.");
                return;
            }
        }
        lockedPlayers.remove(player.getUniqueId());
        respawnToMain.add(player.getUniqueId());
        Text.msg(player, "&cYour party has fallen.");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (respawnToSpectator.remove(event.getPlayer().getUniqueId()) && session != null && session.state == State.RUNNING) {
            World world = Bukkit.getWorld(session.worldName);
            if (world != null) {
                event.setRespawnLocation(spectatorLocation(world));
                Bukkit.getScheduler().runTask(this, () -> {
                    event.getPlayer().setGameMode(GameMode.SPECTATOR);
                    event.getPlayer().teleport(spectatorLocation(world));
                });
                return;
            }
        }
        if (respawnToMain.remove(event.getPlayer().getUniqueId()) || lockedPlayers.contains(event.getPlayer().getUniqueId()) || (session != null && session.deadPlayers.contains(event.getPlayer().getUniqueId()))) {
            event.setRespawnLocation(mainSpawn());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (lockedPlayers.remove(event.getPlayer().getUniqueId()) && session != null) {
            session.deadPlayers.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isLockedInBoss(player)) {
            return;
        }
        String command = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.equals("endboss") || command.equals("hellboss") || command.equals("ritualboss") || command.equals("msg") || command.equals("tell") || command.equals("reply") || command.equals("r") || command.equals("staffchat")) {
            return;
        }
        event.setCancelled(true);
        Text.msg(player, "&cJe zit vast in de bossfight. Win, sterf, of wacht tot de party wiped.");
    }

    @EventHandler
    public void onRitualTorchPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        if (placed == null || !isRitualTorch(placed.getType())) {
            return;
        }
        tryCreatePhysicalRitual(event.getPlayer(), placed);
    }

    @EventHandler
    public void onRitualChestClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DropsEditor) {
            saveDropEditor(event.getInventory());
            if (event.getPlayer() instanceof Player player) {
                Text.msg(player, "&aEndboss drops saved.");
            }
            return;
        }
        RitualChest ritual = ritualForInventory(event.getInventory());
        if (ritual == null || ritual.readyNotified) {
            return;
        }
        Inventory inventory = chestInventory(ritual.key);
        if (inventory != null && ritualRequirementsPresent(inventory)) {
            ritual.readyNotified = true;
            if (event.getPlayer() instanceof Player player) {
                Text.msg(player, "&4Bloodbound &8> &cThe ritual is ready. Break the chest to confirm.");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 0.65F);
            }
        }
    }

    @EventHandler
    public void onRitualChestBreak(BlockBreakEvent event) {
        String key = blockKey(event.getBlock());
        RitualChest ritual = ritualChests.get(key);
        if (ritual == null) {
            return;
        }
        Player player = event.getPlayer();
        if (session != null) {
            event.setCancelled(true);
            Text.msg(player, "&cThe Infernal Sovereign arena is already occupied.");
            return;
        }
        if (!hasRequiredHearts(player)) {
            event.setCancelled(true);
            Text.msg(player, "&cYou need " + requiredHearts() + " hearts to break the Ritual Chest.");
            return;
        }
        Inventory inventory = chestInventory(key);
        if (inventory == null || !ritualRequirementsPresent(inventory)) {
            event.setCancelled(true);
            Text.msg(player, "&cThe Ritual Chest is incomplete: &f" + String.join("&7, &f", missingRequirements(inventory)) + "&c.");
            Text.msg(player, "&7Use &f/endboss ritual &7for the full ritual list.");
            return;
        }
        ritualChests.remove(key);
        consumeRitualInventory(inventory);
        inventory.clear();
        trySetDropItems(event, false);
        event.getBlock().setType(Material.AIR, false);
        ritualBurst(event.getBlock().getLocation(), player);
        session = new Session(player.getUniqueId());
        session.participants.add(player.getUniqueId());
        startFight();
    }

    @EventHandler
    public void onRitualHopperMove(InventoryMoveItemEvent event) {
        if (ritualForInventory(event.getSource()) != null || ritualForInventory(event.getDestination()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRitualBlockExplosion(BlockExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> ritualChests.containsKey(blockKey(block)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRitualEntityExplosion(EntityExplodeEvent event) {
        if (event.blockList().stream().anyMatch(block -> ritualChests.containsKey(blockKey(block)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRitualPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> ritualChests.containsKey(blockKey(block)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRitualPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> ritualChests.containsKey(blockKey(block)))) {
            event.setCancelled(true);
        }
    }

    private void tryCreatePhysicalRitual(Player player, Block placedTorch) {
        if (player == null || placedTorch == null || placedTorch.getLocation() == null || placedTorch.getLocation().getWorld() == null) {
            return;
        }
        Location placed = placedTorch.getLocation();
        World world = placed.getWorld();
        for (int x = placed.getBlockX() - 3; x <= placed.getBlockX() + 3; x++) {
            for (int y = placed.getBlockY() - 1; y <= placed.getBlockY() + 1; y++) {
                for (int z = placed.getBlockZ() - 3; z <= placed.getBlockZ() + 3; z++) {
                    RitualFrame frame = detectFrameAt(world, x, y, z);
                    if (frame == null) {
                        continue;
                    }
                    LivingEntity mob = findRitualMob(frame.center);
                    if (mob == null) {
                        continue;
                    }
                    if (session != null) {
                        Text.msg(player, "&cThe Infernal Sovereign arena is already occupied.");
                        return;
                    }
                    createRitualChest(player, mob, frame);
                    return;
                }
            }
        }
    }

    private LivingEntity findRitualMob(Location center) {
        for (Entity entity : nearbyEntities(center, 1.75D)) {
            if (!(entity instanceof LivingEntity mob) || entity instanceof Player || entity.getType() == EntityType.ARMOR_STAND) {
                continue;
            }
            Location location = mob.getLocation();
            if (location != null && location.getBlockX() == center.getBlockX() && location.getBlockZ() == center.getBlockZ() && Math.abs(location.getBlockY() - center.getBlockY()) <= 2) {
                return mob;
            }
        }
        return null;
    }

    private RitualFrame detectFrameAt(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        int[][] walls = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        List<Block> torches = new ArrayList<>();
        Set<String> seenTorches = new LinkedHashSet<>();
        for (int[] wall : walls) {
            Block wallBlock = world.getBlockAt(x + wall[0], y, z + wall[1]);
            if (wallBlock.getType() != Material.NETHER_BRICK_WALL) {
                return null;
            }
            Block torch = findTorchTouching(wallBlock);
            if (torch == null || !seenTorches.add(blockKey(torch))) {
                return null;
            }
            torches.add(torch);
        }
        return new RitualFrame(new Location(world, x, y, z), torches);
    }

    private RitualFrame detectFrame(LivingEntity mob) {
        Location location = mob.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return detectFrameAt(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private Block findTorchTouching(Block wallBlock) {
        Location location = wallBlock.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int[][] offsets = new int[][] {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }
        };
        for (int[] offset : offsets) {
            Block block = world.getBlockAt(x + offset[0], y + offset[1], z + offset[2]);
            if (isRitualTorch(block.getType())) {
                return block;
            }
        }
        return null;
    }

    private void createRitualChest(Player player, LivingEntity mob, RitualFrame frame) {
        for (Block torch : frame.torches) {
            torch.setType(Material.AIR, false);
        }
        ritualBurst(frame.center, player);
        killRitualMobWithLightning(mob, frame.center);
        Bukkit.getScheduler().runTaskLater(this, () -> placeRitualChest(player, frame.center), 2L);
    }

    private void placeRitualChest(Player player, Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Block chestBlock = null;
        Block centerBlock = world.getBlockAt(center.getBlockX(), center.getBlockY(), center.getBlockZ());
        if (centerBlock.getType() == Material.AIR || centerBlock.getType() == Material.FIRE) {
            chestBlock = centerBlock;
        }
        for (int dy = 0; dy <= 2; dy++) {
            if (chestBlock != null) {
                break;
            }
            Block candidate = world.getBlockAt(center.getBlockX(), center.getBlockY() + dy, center.getBlockZ());
            if (candidate.getType() == Material.AIR || candidate.getType() == Material.FIRE) {
                chestBlock = candidate;
                break;
            }
        }
        if (chestBlock == null) {
            chestBlock = centerBlock;
            chestBlock.setType(Material.AIR, false);
        }
        chestBlock.setType(Material.CHEST, false);
        RitualChest ritual = new RitualChest(player.getUniqueId(), blockKey(chestBlock), System.currentTimeMillis() + RITUAL_CHEST_TIMEOUT_MS);
        ritualChests.put(ritual.key, ritual);
        Text.msg(player, "&4Bloodbound &8> &cA Ritual Chest has formed. Add the required items within 15 minutes.");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.7F);
    }

    private void tickRitualChests() {
        long now = System.currentTimeMillis();
        List<String> expired = ritualChests.values().stream()
            .filter(ritual -> ritual.expiresAt <= now)
            .map(ritual -> ritual.key)
            .toList();
        for (String key : expired) {
            Inventory inventory = chestInventory(key);
            if (inventory != null) {
                inventory.clear();
            }
            Block block = blockFromKey(key);
            if (block != null) {
                block.setType(Material.AIR, false);
            }
            ritualChests.remove(key);
        }
    }

    private boolean ritualRequirementsPresent(Inventory inventory) {
        return missingRequirements(inventory).isEmpty();
    }

    private List<String> missingRequirements(Inventory inventory) {
        List<String> missing = new ArrayList<>();
        int bossShards = countInventory(inventory, this::isBossShard);
        int corruptedHearts = countInventory(inventory, this::isCorruptedHeart);
        int dragonEggs = countInventory(inventory, item -> item != null && item.getType() == Material.DRAGON_EGG);
        int plainNetherStars = countInventory(inventory, this::isPlainNetherStar);
        int enchantedApples = countInventory(inventory, item -> item != null && item.getType() == Material.ENCHANTED_GOLDEN_APPLE);
        if (bossShards < requiredBossShards()) {
            missing.add((requiredBossShards() - bossShards) + " Boss Shards");
        }
        if (corruptedHearts < requiredCorruptedHearts()) {
            missing.add((requiredCorruptedHearts() - corruptedHearts) + " Corrupted Hearts");
        }
        if (dragonEggs < 1) {
            missing.add("1 Dragon Egg");
        }
        if (plainNetherStars < 1) {
            missing.add("1 plain Nether Star");
        }
        if (enchantedApples < requiredEnchantedApples()) {
            missing.add((requiredEnchantedApples() - enchantedApples) + " Enchanted Golden Apples");
        }
        return missing;
    }

    private void consumeRitualInventory(Inventory inventory) {
        removeInventory(inventory, this::isBossShard, requiredBossShards());
        removeInventory(inventory, this::isCorruptedHeart, requiredCorruptedHearts());
        removeInventory(inventory, item -> item != null && item.getType() == Material.DRAGON_EGG, 1);
        removeInventory(inventory, this::isPlainNetherStar, 1);
        removeInventory(inventory, item -> item != null && item.getType() == Material.ENCHANTED_GOLDEN_APPLE, requiredEnchantedApples());
    }

    private int countInventory(Inventory inventory, Predicate<ItemStack> predicate) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (predicate.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removeInventory(Inventory inventory, Predicate<ItemStack> predicate, int amount) {
        ItemStack[] contents = inventory.getContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!predicate.test(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        inventory.setContents(contents);
    }

    private boolean hasRequiredHearts(Player player) {
        HeartService hearts = MitchSMP.hearts();
        return hearts != null && hearts.getHearts(player.getUniqueId()) >= requiredHearts();
    }

    private List<Entity> nearbyEntities(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) {
            return List.of();
        }
        try {
            Object result = world.getClass().getMethod("getNearbyEntities", Location.class, double.class, double.class, double.class).invoke(world, location, radius, radius, radius);
            if (result instanceof Iterable<?> iterable) {
                List<Entity> entities = new ArrayList<>();
                for (Object object : iterable) {
                    if (object instanceof Entity entity) {
                        entities.add(entity);
                    }
                }
                return entities;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return world.getEntities().stream()
            .filter(entity -> entity.getLocation() != null && sameWorld(entity.getLocation(), location) && entity.getLocation().distanceSquared(location) <= radius * radius)
            .toList();
    }

    private RitualChest ritualForInventory(Inventory inventory) {
        String key = ritualChestKey(inventory);
        return key == null ? null : ritualChests.get(key);
    }

    private String ritualChestKey(Inventory inventory) {
        if (inventory == null || inventory.getHolder() == null) {
            return null;
        }
        Object holder = inventory.getHolder();
        if (holder instanceof Chest chest) {
            try {
                Object block = chest.getClass().getMethod("getBlock").invoke(chest);
                if (block instanceof Block chestBlock) {
                    return blockKey(chestBlock);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private Inventory chestInventory(String key) {
        Block block = blockFromKey(key);
        if (block == null || block.getState() == null) {
            return null;
        }
        if (block.getState() instanceof Chest chest) {
            return chest.getInventory();
        }
        return null;
    }

    private String blockKey(Block block) {
        return block == null ? "" : blockKey(block.getLocation());
    }

    private String blockKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private Block blockFromKey(String key) {
        String[] parts = key == null ? new String[0] : key.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void ritualBurst(Location location, Player source) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.FLAME, location, 80, 1.3D, 1.0D, 1.3D, 0.04D);
        world.spawnParticle(Particle.DRAGON_BREATH, location, 45, 1.0D, 0.8D, 1.0D, 0.03D, Float.valueOf(1.0F));
        temporaryFireRing(source == null ? location : source.getLocation());
    }

    private void temporaryFireRing(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        List<String> added = new ArrayList<>();
        int y = center.getBlockY();
        for (int x = center.getBlockX() - 2; x <= center.getBlockX() + 2; x++) {
            for (int z = center.getBlockZ() - 2; z <= center.getBlockZ() + 2; z++) {
                if (Math.abs(x - center.getBlockX()) + Math.abs(z - center.getBlockZ()) > 3) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != Material.AIR) {
                    continue;
                }
                block.setType(Material.FIRE, false);
                String key = blockKey(block);
                ritualFireBlocks.add(key);
                added.add(key);
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (String key : added) {
                Block block = blockFromKey(key);
                if (block != null && block.getType() == Material.FIRE) {
                    block.setType(Material.AIR, false);
                }
                ritualFireBlocks.remove(key);
            }
        }, 20L * 12L);
    }

    private void killRitualMobWithLightning(LivingEntity mob, Location fallback) {
        Location location = mob == null || mob.getLocation() == null ? fallback : mob.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        boolean struck = false;
        try {
            location.getWorld().getClass().getMethod("strikeLightning", Location.class).invoke(location.getWorld(), location);
            struck = true;
        } catch (ReflectiveOperationException ignored) {
        }
        if (!struck) {
            try {
                location.getWorld().getClass().getMethod("strikeLightningEffect", Location.class).invoke(location.getWorld(), location);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (mob != null) {
            try {
                mob.setHealth(0.0D);
            } catch (RuntimeException ignored) {
                mob.remove();
            }
            Bukkit.getScheduler().runTaskLater(this, mob::remove, 1L);
        }
    }

    private void trySetDropItems(BlockBreakEvent event, boolean dropItems) {
        try {
            event.getClass().getMethod("setDropItems", boolean.class).invoke(event, dropItems);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private boolean canOpenRitual(Player player, boolean verbose) {
        HeartService hearts = MitchSMP.hearts();
        if (hearts == null || hearts.getHearts(player.getUniqueId()) < requiredHearts()) {
            if (verbose) {
                Text.msg(player, "&cJe hebt " + requiredHearts() + " hearts nodig om dit ritual te openen.");
            }
            return false;
        }
        List<String> missing = missingRequirements(player);
        if (!missing.isEmpty()) {
            if (verbose) {
                Text.msg(player, "&cJe mist: &f" + String.join("&7, &f", missing));
            }
            return false;
        }
        return true;
    }

    private List<String> missingRequirements(Player player) {
        List<String> missing = new ArrayList<>();
        if (countInventory(player.getInventory(), this::isBossShard) < requiredBossShards()) {
            missing.add(requiredBossShards() + " Boss Shards");
        }
        if (countInventory(player.getInventory(), this::isCorruptedHeart) < requiredCorruptedHearts()) {
            missing.add(requiredCorruptedHearts() + " Corrupted Hearts");
        }
        if (countMaterial(player, Material.DRAGON_EGG) < 1) {
            missing.add("1 Dragon Egg");
        }
        if (countPlainNetherStars(player) < 1) {
            missing.add("1 gewone Nether Star");
        }
        if (countMaterial(player, Material.ENCHANTED_GOLDEN_APPLE) < requiredEnchantedApples()) {
            missing.add(requiredEnchantedApples() + " Enchanted Golden Apples");
        }
        return missing;
    }

    private void consumeRitualItems(Player player) {
        removeInventory(player.getInventory(), this::isBossShard, requiredBossShards());
        removeInventory(player.getInventory(), this::isCorruptedHeart, requiredCorruptedHearts());
        removeMaterial(player, Material.DRAGON_EGG, 1);
        removePlainNetherStars(player, 1);
        removeMaterial(player, Material.ENCHANTED_GOLDEN_APPLE, requiredEnchantedApples());
    }

    private void rewardParticipants(Location bossLocation) {
        List<ItemStack> customDrops = configuredDrops();
        for (Player player : onlineParticipants()) {
            player.sendTitle(Text.color("&6Infernal Victory"), Text.color("&fEndgame loot unlocked"), 10, 80, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0F, 1.2F);
            giveShardReward(player, 18 + random.nextInt(13));
            if (!customDrops.isEmpty()) {
                for (ItemStack drop : customDrops) {
                    giveOrDrop(player, drop.clone());
                }
                continue;
            }
            giveOrDrop(player, wardGear());
            if (random.nextDouble() < 0.55D) {
                giveOrDrop(player, hellforgedWeapon());
            }
            if (random.nextDouble() < 0.45D) {
                giveOrDrop(player, infernalTool());
            }
            if (random.nextDouble() < 0.35D) {
                giveOrDrop(player, relic());
            }
        }
        if (bossLocation != null && bossLocation.getWorld() != null) {
            bossLocation.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, bossLocation, 200, 4.0D, 2.0D, 4.0D, 0.2D);
        }
    }

    private void sendVictoryToHallOfFame() {
        if (session == null) {
            return;
        }
        World world = hallWorld();
        Location fallback = mainSpawn();
        List<Player> players = onlineParticipants();
        long durationMillis = Math.max(0L, System.currentTimeMillis() - session.startedAtMillis);
        Location groupSpawn = world == null ? fallback : buildBossVictoryHallEntry(world, players, durationMillis);
        for (Player player : players) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(groupSpawn);
            player.setFallDistance(0.0F);
        }
    }

    private Location buildBossVictoryHallEntry(World world, List<Player> players, long durationMillis) {
        int index = config.getInt("hof.boss_victories", 0);
        config.set("hof.boss_victories", index + 1);
        config.save();
        int baseZ = 220 + index * 24;
        for (int x = -32; x <= 32; x++) {
            for (int z = baseZ - 8; z <= baseZ + 8; z++) {
                boolean edge = Math.abs(x) == 32 || z == baseZ - 8 || z == baseZ + 8;
                set(world, x, 101, z, edge ? Material.GOLD_BLOCK : Material.QUARTZ_BLOCK);
            }
        }
        spawnHallLabel(world, 0.5D, 105.0D, baseZ - 5.5D, "&4&lEndboss Victory #" + (index + 1));
        spawnHallLabel(world, 0.5D, 103.7D, baseZ - 2.5D, "&6Time: &f" + formatDuration(durationMillis) + " &8| &6Party: &f" + players.size());
        int startX = -Math.min(24, Math.max(0, players.size() - 1) * 6 / 2);
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int x = startX + i * 8;
            set(world, x, 102, baseZ + 4, Material.GOLD_BLOCK);
            ArmorStand stand = (ArmorStand) world.spawnEntity(new Location(world, x + 0.5D, 103.0D, baseZ + 4.5D), EntityType.ARMOR_STAND);
            stand.setCustomName(Text.color("&4Boss Slayer &f" + player.getName()));
            stand.setCustomNameVisible(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            if (stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                stand.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                stand.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                stand.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                stand.getEquipment().setItemInMainHand(new ItemStack(i == 0 ? Material.NETHERITE_SWORD : Material.NETHER_STAR));
            }
        }
        return new Location(world, 0.5D, 102.0D, baseZ - 5.5D, 0.0F, 0.0F);
    }

    private void spawnHallLabel(World world, double x, double y, double z, String text) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(new Location(world, x, y, z), EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setCustomName(Text.color(text));
        stand.setCustomNameVisible(true);
    }

    private World hallWorld() {
        World world = Bukkit.getWorld(HOF_WORLD);
        if (world != null) {
            return world;
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(HOF_WORLD);
            Class<?> generatorClass = Class.forName("org.bukkit.generator.ChunkGenerator");
            creatorClass.getMethod("generator", generatorClass).invoke(creator, new HellChunkGenerator());
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, false);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create Hall of Fame world: " + exception.getMessage());
        }
        return Bukkit.getWorld(HOF_WORLD);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, remainder);
    }

    private void giveShardReward(Player player, int amount) {
        BossShardService shards = MitchSMP.bossShards();
        if (shards == null) {
            getLogger().warning("Boss Shard service is unavailable; reward for " + player.getName() + " was not issued.");
            return;
        }
        shards.giveShards(player, amount);
    }

    private ItemStack wardGear() {
        Material[] pieces = { Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, Material.SHIELD };
        Material material = pieces[random.nextInt(pieces.length)];
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&4Infernal Ward " + readable(material)));
            meta.setLore(List.of(
                Text.color("&6Infernal Ward I"),
                Text.color("&7Boss charged attacks kunnen je niet one-shotten."),
                Text.color("&8Werkt alleen tegen endgame boss damage.")
            ));
            meta.setCustomModelData(910010);
            meta.addEnchant(Enchantment.PROTECTION, 6, true);
            meta.addEnchant(Enchantment.UNBREAKING, 8, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(wardKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack hellforgedWeapon() {
        Material material = random.nextBoolean() ? Material.NETHERITE_SWORD : Material.NETHERITE_AXE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(material == Material.NETHERITE_SWORD ? "&4Hellforged Blade" : "&4Hellforged Cleaver"));
            meta.setLore(List.of(
                Text.color("&7Endgame boss drop."),
                Text.color("&7Sterk, maar niet buiten de OP-shop economie verkrijgbaar.")
            ));
            meta.setCustomModelData(910011);
            meta.addEnchant(Enchantment.SHARPNESS, 7, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 8, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack infernalTool() {
        ItemStack item = new ItemStack(random.nextBoolean() ? Material.NETHERITE_PICKAXE : Material.NETHERITE_SHOVEL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6Infernal Excavator"));
            meta.setLore(List.of(
                Text.color("&7Endgame utility tool."),
                Text.color("&8Combineer met /abilities for tool-bound grinds.")
            ));
            meta.setCustomModelData(910012);
            meta.addEnchant(Enchantment.EFFICIENCY, 8, true);
            meta.addEnchant(Enchantment.FORTUNE, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 8, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack relic() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&4Infernal Ritual Core"));
            meta.setLore(List.of(
                Text.color("&7Extreem zeldzame endgame relic."),
                Text.color("&8Bewijs dat je de hell fight hebt overleefd.")
            ));
            meta.setCustomModelData(910013);
            meta.getPersistentDataContainer().set(relicKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasInfernalWard(Player player) {
        PlayerInventory inventory = player.getInventory();
        return hasWard(inventory.getHelmet())
            || hasWard(inventory.getChestplate())
            || hasWard(inventory.getLeggings())
            || hasWard(inventory.getBoots())
            || hasWard(inventory.getItemInOffHand());
    }

    private boolean hasWard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(wardKey, PersistentDataType.BYTE)) {
            return true;
        }
        return meta != null && meta.getLore() != null && meta.getLore().stream().anyMatch(line -> Text.stripColorCodes(line).toLowerCase(Locale.ROOT).contains("infernal ward"));
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void loadDropEditor(Inventory inventory) {
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = decodeItem(config.getString("drop." + slot, ""));
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(slot, item);
            }
        }
    }

    private void saveDropEditor(Inventory inventory) {
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            config.set("drop." + slot, item == null || item.getType() == Material.AIR ? null : encodeItem(item));
        }
        config.save();
    }

    private List<ItemStack> configuredDrops() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = decodeItem(config.getString("drop." + slot, ""));
            if (item != null && item.getType() != Material.AIR) {
                drops.add(item);
            }
        }
        return drops;
    }

    private ItemStack editorInfo() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6How this editor works"));
            meta.setLore(List.of(
                Text.color("&7Slots 1-45 are boss drops."),
                Text.color("&7Close this GUI to save."),
                Text.color("&8Leave all slots empty for fallback drops.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String encodeItem(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack decodeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Could not load configured endboss drop: " + exception.getMessage());
            return null;
        }
    }

    private int countNamed(Player player, Material material, String strippedName) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isNamed(item, material, strippedName)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private int countPlainNetherStars(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isPlainNetherStar(item)) {
                continue;
            }
            total += item.getAmount();
        }
        return total;
    }

    private void removeNamed(Player player, Material material, String strippedName, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!isNamed(item, material, strippedName)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    private void removeMaterial(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (item == null || item.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    private void removePlainNetherStars(Player player, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!isPlainNetherStar(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    private boolean isNamed(ItemStack item, Material material, String strippedName) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && Text.stripColorCodes(meta.getDisplayName()).equalsIgnoreCase(strippedName);
    }

    private boolean isBossShard(ItemStack item) {
        BossShardService shards = MitchSMP.bossShards();
        if (shards != null && shards.isShard(item)) {
            return true;
        }
        return item != null
            && item.getType() == Material.NETHER_STAR
            && (hasCustomModelData(item, BOSS_SHARD_MODEL) || hasItemModel(item, "bloodbound", "boss_shard"));
    }

    private boolean isCorruptedHeart(ItemStack item) {
        CorruptedHeartService hearts = MitchSMP.corruptedHearts();
        if (hearts != null && hearts.isCorruptedHeart(item)) {
            return true;
        }
        return item != null
            && item.getType() == Material.ECHO_SHARD
            && (hasCustomModelData(item, CORRUPTED_HEART_MODEL) || hasItemModel(item, "bloodbound", "corrupted_heart"));
    }

    private boolean isPlainNetherStar(ItemStack item) {
        return item != null && item.getType() == Material.NETHER_STAR && !isBossShard(item);
    }

    private boolean hasCustomModelData(ItemStack item, int modelData) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        try {
            Object has = meta.getClass().getMethod("hasCustomModelData").invoke(meta);
            if (has instanceof Boolean bool && !bool) {
                return false;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object value = meta.getClass().getMethod("getCustomModelData").invoke(meta);
            return value instanceof Integer integer && integer == modelData;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private boolean hasItemModel(ItemStack item, String namespace, String key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        try {
            Object model = meta.getClass().getMethod("getItemModel").invoke(meta);
            return model instanceof NamespacedKey namespacedKey
                && namespacedKey.getNamespace().equalsIgnoreCase(namespace)
                && namespacedKey.getKey().equalsIgnoreCase(key);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private boolean isRitualTorch(Material material) {
        return material == Material.REDSTONE_TORCH || material == Material.REDSTONE_WALL_TORCH;
    }

    private void buildArena(World world) {
        int y = ARENA_Y;
        int radius = ARENA_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) {
                    continue;
                }
                Material floor = dist > radius - 2 ? Material.OBSIDIAN : ((x + z) % 9 == 0 ? Material.BLACKSTONE : Material.NETHERRACK);
                set(world, x, y, z, floor);
                if (dist > radius - 1) {
                    set(world, x, y + 1, z, Material.CRYING_OBSIDIAN);
                }
                if (dist > radius - 5 && dist < radius - 3 && (x + z) % 5 == 0) {
                    set(world, x, y + 1, z, Material.MAGMA_BLOCK);
                }
            }
        }
        for (int x = -(radius + 3); x <= radius + 3; x++) {
            for (int z = -(radius + 3); z <= radius + 3; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < radius + 1 || dist > radius + 3) {
                    continue;
                }
                for (int yy = 1; yy <= 8; yy++) {
                    set(world, x, y + yy, z, Material.BARRIER);
                }
            }
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                if (Math.abs(x) == 10 || Math.abs(z) == 10) {
                    set(world, x, y + 1, z, Material.BLACKSTONE);
                }
            }
        }
        for (int[] point : List.of(new int[] { -22, -22 }, new int[] { 22, -22 }, new int[] { -22, 22 }, new int[] { 22, 22 })) {
            pillar(world, point[0], y + 1, point[1]);
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                set(world, x, y + 1, z, Math.abs(x) == 4 || Math.abs(z) == 4 ? Material.OBSIDIAN : Material.SOUL_SAND);
            }
        }
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius + 2 && dist < radius + 8) {
                    set(world, x, y - 1, z, Material.LAVA);
                }
            }
        }
    }

    private void pillar(World world, int x, int y, int z) {
        for (int yy = 0; yy < 10; yy++) {
            set(world, x, y + yy, z, yy % 3 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
        }
        set(world, x, y + 10, z, Material.MAGMA_BLOCK);
    }

    private void set(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material);
    }

    private Location playerSpawn(World world, int index, int total) {
        double angle = (Math.PI * 2.0D / Math.max(1, total)) * index;
        double x = Math.cos(angle) * 21.0D;
        double z = Math.sin(angle) * 21.0D;
        return new Location(world, x + 0.5D, 82.0D, z + 0.5D, (float) Math.toDegrees(-angle), 0.0F);
    }

    private World createBossWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(name);
            try {
                Class<?> environmentClass = Class.forName("org.bukkit.World$Environment");
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Object nether = Enum.valueOf((Class<Enum>) environmentClass.asSubclass(Enum.class), "NETHER");
                creatorClass.getMethod("environment", environmentClass).invoke(creator, nether);
            } catch (ReflectiveOperationException ignored) {
            }
            Class<?> generatorClass = Class.forName("org.bukkit.generator.ChunkGenerator");
            creatorClass.getMethod("generator", generatorClass).invoke(creator, new HellChunkGenerator());
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, false);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create endboss world " + name + ": " + exception.getMessage());
        }
        return Bukkit.getWorld(name);
    }

    private void cleanupSession(boolean teleportOut) {
        Session old = session;
        session = null;
        if (old == null) {
            lockedPlayers.clear();
            return;
        }
        if (old.task != null) {
            old.task.cancel();
        }
        if (teleportOut) {
            for (UUID id : old.participants) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.teleport(mainSpawn());
                    player.setFallDistance(0.0F);
                }
            }
        }
        lockedPlayers.removeAll(old.participants);
        respawnToSpectator.removeAll(old.participants);
        respawnToMain.removeAll(old.participants);
        if (old.worldName != null) {
            World world = Bukkit.getWorld(old.worldName);
            if (world != null) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Player player) {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.teleport(mainSpawn());
                        player.setFallDistance(0.0F);
                    } else {
                        entity.remove();
                    }
                }
                try {
                    Bukkit.class.getMethod("unloadWorld", World.class, boolean.class).invoke(null, world, false);
                } catch (ReflectiveOperationException exception) {
                    getLogger().warning("Could not unload endboss world " + old.worldName + ": " + exception.getMessage());
                }
            }
            Bukkit.getScheduler().runTaskLater(this, () -> deleteWorldFolder(old.worldName), 20L);
        }
    }

    private void deleteWorldFolder(String worldName) {
        Path root = serverRoot();
        Path worldPath = root == null ? Path.of(worldName) : root.resolve(worldName);
        if (!Files.exists(worldPath) || !worldPath.getFileName().toString().startsWith(WORLD_PREFIX)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(worldPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException exception) {
                    getLogger().warning("Could not delete " + path + ": " + exception.getMessage());
                }
            });
        } catch (java.io.IOException exception) {
            getLogger().warning("Could not clean endboss world folder " + worldName + ": " + exception.getMessage());
        }
    }

    private Path serverRoot() {
        Path data = getDataFolder().toPath();
        Path plugins = data.getParent();
        return plugins == null ? null : plugins.getParent();
    }

    private void purgeLooseEntities(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            if (session != null && session.bossId != null && session.bossId.equals(entity.getUniqueId())) {
                continue;
            }
            if (entity.getType() == EntityType.ARMOR_STAND) {
                entity.remove();
            }
        }
    }

    private boolean isLockedInBoss(Player player) {
        if (player == null) {
            return false;
        }
        if (lockedPlayers.contains(player.getUniqueId())) {
            return true;
        }
        return session != null && session.worldName != null && player.getWorld() != null && session.worldName.equals(player.getWorld().getName());
    }

    private List<Player> onlineParticipants() {
        List<Player> players = new ArrayList<>();
        if (session == null) {
            return players;
        }
        for (UUID id : session.participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private List<Player> onlineParticipantsInWorld(World world) {
        List<Player> players = new ArrayList<>();
        for (Player player : onlineParticipants()) {
            if (player.getWorld() != null && world.getName().equals(player.getWorld().getName()) && !session.deadPlayers.contains(player.getUniqueId())) {
                players.add(player);
            }
        }
        return players;
    }

    private int onlinePartySize() {
        return onlineParticipants().size();
    }

    private void broadcastParty(String message) {
        if (session == null) {
            return;
        }
        for (Player player : onlineParticipants()) {
            Text.msg(player, message);
        }
    }

    private Location mainSpawn() {
        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world == null ? new Location(null, 0, 100, 0) : world.getSpawnLocation();
    }

    private boolean sameWorld(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && b.getWorld() != null && a.getWorld().getName().equals(b.getWorld().getName());
    }

    private int minPlayers() {
        return Math.max(1, config.getInt("min_players", 3));
    }

    private double bossHealth() {
        return Math.max(500.0D, config.getDouble("boss_health", 5000.0D));
    }

    private int requiredHearts() {
        return Math.max(1, config.getInt("required_hearts", 20));
    }

    private int requiredBossShards() {
        return Math.max(1, config.getInt("required_boss_shards", 64));
    }

    private int requiredCorruptedHearts() {
        return Math.max(1, config.getInt("required_corrupted_hearts", 8));
    }

    private int requiredEnchantedApples() {
        return Math.max(1, config.getInt("required_enchanted_apples", 2));
    }

    private double parseDouble(String input, double fallback) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String readable(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private enum State {
        ASSEMBLING,
        RUNNING
    }

    private enum PendingType {
        START,
        JOIN
    }

    private static final class RitualFrame {
        private final Location center;
        private final List<Block> torches;

        private RitualFrame(Location center, List<Block> torches) {
            this.center = center;
            this.torches = torches;
        }
    }

    private static final class RitualChest {
        private final UUID owner;
        private final String key;
        private final long expiresAt;
        private boolean readyNotified;

        private RitualChest(UUID owner, String key, long expiresAt) {
            this.owner = owner;
            this.key = key;
            this.expiresAt = expiresAt;
        }
    }

    private static final class DropsEditor implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class PendingAction {
        private final PendingType type;
        private final long createdAt;

        private PendingAction(PendingType type) {
            this.type = type;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final class Session {
        private final UUID leader;
        private final Set<UUID> participants = new LinkedHashSet<>();
        private final Set<UUID> deadPlayers = new LinkedHashSet<>();
        private State state = State.ASSEMBLING;
        private String worldName;
        private UUID bossId;
        private BukkitTask task;
        private int ticks;
        private int wave;
        private long startedAtMillis;

        private Session(UUID leader) {
            this.leader = leader;
        }
    }

    private static final class HellChunkGenerator extends ChunkGenerator {
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
            return new Location(world, 0.5D, 82.0D, 24.5D);
        }
    }
}



