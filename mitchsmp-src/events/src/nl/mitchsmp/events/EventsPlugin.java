package nl.mitchsmp.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.CorruptedHeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EventsPlugin extends JavaPlugin implements Listener, TabCompleter {
    private final Random random = new Random();
    private final Map<UUID, Integer> kothPoints = new HashMap<>();
    private String activeEvent = "none";
    private Location kothCenter;
    private int kothSecondsLeft;
    private long lastAutoEvent;
    private String currentEventToken = "";
    private final Map<String, EventCache> eventCaches = new HashMap<>();
    private final java.util.Set<String> claimedEventTokens = new java.util.HashSet<>();
    private PropertiesFile config;

    @Override
    public void onEnable() {
        config = new PropertiesFile(getDataFolder().toPath().resolve("events.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(this);
            getCommand("event").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tickKoth, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::autoEvent, 20L * 60L, 20L * 60L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            Text.msg(sender, "&7Actief event: &f" + activeEvent);
            Text.msg(sender, "&7Auto world events: &f" + (setting("autoWorldEvents", defaultSetting("autoWorldEvents")) > 0.0D ? "aan" : "uit"));
            Text.msg(sender, "&7Lootstructure kans/chunk: &f" + setting("lootStructureChancePercent", defaultSetting("lootStructureChancePercent")) + "%");
            return true;
        }
        if (args[0].equalsIgnoreCase("config")) {
            return config(sender, args);
        }
        if (!args[0].equalsIgnoreCase("start")) {
            Text.msg(sender, "&cGebruik: /event start <structure|koth|airdrop|treasure|meteor|vault|bandit|merchant|rift|feast|ambush|boss|end>");
            return true;
        }
        if (!MitchSMP.permissions().has(sender, "mitchsmp.events.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cKies structure, koth, airdrop, treasure, meteor, vault, bandit, merchant, rift, feast, ambush, boss of end.");
            return true;
        }
        start(args[1].toLowerCase());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "status", "start", "config");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Tab.complete(args[1], "structure", "koth", "airdrop", "treasure", "meteor", "vault", "bandit", "merchant", "rift", "feast", "ambush", "boss", "end");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[1], "autoWorldEvents", "autoIntervalMinutes", "eventMinDistance", "eventMaxDistance", "lootStructureChancePercent", "lootStructureMinDistanceFromSpawn", "lootStructureMinBedrockOffset", "lootStructureMaxBedrockOffset", "lootStructureGuardCount", "lootStructureGuardHealth", "lootStructureSpawnerCount", "lootStructureLootRolls", "proGearChance", "enchantedAppleChance", "kothMoney", "eventMoneyMultiplier", "economyBaseline", "economyScaleMax");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[2], "0.05", "0.1", "0.5", "1", "2", "5", "10", "30", "500", "1000");
        }
        return List.of();
    }

    private void autoEvent() {
        if (setting("autoWorldEvents", defaultSetting("autoWorldEvents")) <= 0.0D) {
            return;
        }
        long interval = (long) (setting("autoIntervalMinutes", 30.0D) * 60_000L);
        if (System.currentTimeMillis() - lastAutoEvent < interval) {
            return;
        }
        lastAutoEvent = System.currentTimeMillis();
        String[] events = {"koth", "airdrop", "treasure", "meteor", "vault", "bandit", "merchant", "rift", "feast", "ambush"};
        start(events[random.nextInt(events.length)]);
    }

    private void start(String type) {
        switch (type) {
            case "structure", "lootstructure", "loot" -> startLootStructure();
            case "koth" -> startKoth();
            case "airdrop" -> startAirdrop("Airdrop");
            case "treasure" -> startAirdrop("Treasure Hunt");
            case "meteor" -> startMeteor();
            case "vault" -> startVault();
            case "bandit" -> startBandit();
            case "merchant", "blackmarket" -> startMerchant();
            case "rift" -> startRift();
            case "feast" -> startFeast();
            case "ambush" -> startAmbush();
            case "boss" -> startBoss();
            case "end" -> {
                activeEvent = "End Event";
                Bukkit.broadcastMessage(Text.PREFIX + Text.color("&5End Event gestart. Versla de End dreiging for rewards."));
            }
            default -> Bukkit.broadcastMessage(Text.PREFIX + Text.color("&cUnknown event: " + type));
        }
    }

    private void startLootStructure() {
        Location location = randomSurfaceLocation();
        if (spawnLootStructure(location, true)) {
            activeEvent = "Generated Loot Structure";
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&5Ancient Loot Structure ontdekt op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + ". Verwacht zware tegenstand."));
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !eligibleStructureWorld(event.getWorld())) {
            return;
        }
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        String key = generatedChunkKey(event.getWorld(), chunkX, chunkZ);
        if (config.contains(key)) {
            return;
        }
        java.util.Random chunkRandom = new java.util.Random((((long) chunkX) * 341873128712L) ^ (((long) chunkZ) * 132897987541L) ^ event.getWorld().getName().hashCode());
        if (chunkRandom.nextDouble() * 100.0D >= setting("lootStructureChancePercent", defaultSetting("lootStructureChancePercent"))) {
            return;
        }
        config.set(key, true);
        config.save();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            int x = chunkX * 16 + 8;
            int z = chunkZ * 16 + 8;
            Location location = event.getWorld().getHighestBlockAt(x, z).getLocation().add(0.0D, 1.0D, 0.0D);
            spawnLootStructure(location, false);
        }, 1L);
    }

    private void startKoth() {
        World world = Bukkit.getWorlds().get(0);
        kothCenter = world.getSpawnLocation();
        kothSecondsLeft = 300;
        kothPoints.clear();
        activeEvent = "KOTH";
        beginEvent("KOTH");
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6KOTH gestart bij spawn. Sta binnen 12 blocks om punten te krijgen."));
    }

    private void tickKoth() {
        if (!activeEvent.equals("KOTH") || kothCenter == null) {
            return;
        }
        kothSecondsLeft--;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(kothCenter.getWorld()) && player.getLocation().distanceSquared(kothCenter) <= 144) {
                kothPoints.merge(player.getUniqueId(), 1, Integer::sum);
            }
        }
        if (kothSecondsLeft <= 0) {
            endKoth();
        }
    }

    private void endKoth() {
        activeEvent = "none";
        kothPoints.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).ifPresentOrElse(winner -> {
            Player player = Bukkit.getPlayer(winner.getKey());
            if (player != null) {
                EconomyService economy = MitchSMP.economy();
                if (economy != null) {
                    economy.deposit(player.getUniqueId(), eventMoney(setting("kothMoney", 500.0D)), "koth win");
                }
                Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + player.getName() + " won KOTH."));
            }
        }, () -> Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6KOTH eindigde zonder winnaar.")));
    }

    private void startAirdrop(String name) {
        Location location = randomSurfaceLocation();
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, name.equals("Treasure Hunt") ? 9 : 7, false);
        activeEvent = name;
        beginEvent(name);
        trackEventChest(location, name);
        decorateSite(location, Material.GOLD_BLOCK, 2);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + name + " geland op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + "."));
    }

    private void startMeteor() {
        Location location = randomSurfaceLocation();
        location.getWorld().createExplosion(location, 2.5F, false, false);
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, 10, true);
        activeEvent = "Meteor Impact";
        beginEvent(activeEvent);
        trackEventChest(location, activeEvent);
        decorateSite(location, Material.OBSIDIAN, 3);
        spawnEventEffects(location, Particle.EXPLOSION, 3);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&5Meteor Impact op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + "."));
    }

    private void startVault() {
        Location location = randomSurfaceLocation();
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, 12, true);
        activeEvent = "Ancient Vault";
        beginEvent(activeEvent);
        trackEventChest(location, activeEvent);
        decorateSite(location, Material.SEA_LANTERN, 3);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&dAncient Vault geopend op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + "."));
    }

    private void startBandit() {
        World world = Bukkit.getWorlds().get(0);
        Location location = randomSurfaceLocation();
        spawnBandits(world, location, 6, 45.0D, "&cBandit Guard");
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, 9, false);
        activeEvent = "Bandit Camp";
        beginEvent(activeEvent);
        trackEventChest(location, activeEvent);
        decorateSite(location, Material.RED_WOOL, 3);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&cBandit Camp gespot op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + "."));
    }

    private void startMerchant() {
        Location location = randomSurfaceLocation();
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, 8, true);
        activeEvent = "Black Market Merchant";
        beginEvent(activeEvent);
        trackEventChest(location, activeEvent);
        decorateSite(location, Material.EMERALD_BLOCK, 2);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&8Black Market Merchant stash op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + "."));
    }

    private void startRift() {
        Location location = randomSurfaceLocation();
        activeEvent = "Rift Surge";
        beginEvent(activeEvent);
        decorateSite(location, Material.SEA_LANTERN, 3);
        spawnEventEffects(location, Particle.TOTEM_OF_UNDYING, 5);
        placeEventChest(at(location, 2, 0, 0), activeEvent, 8, true);
        placeEventChest(at(location, -2, 0, 0), activeEvent, 6, false);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&dRift Surge verscheen op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + ". Meerdere caches zijn actief."));
    }

    private void startFeast() {
        Location location = randomSurfaceLocation();
        activeEvent = "Feast";
        beginEvent(activeEvent);
        decorateSite(location, Material.GOLD_BLOCK, 4);
        for (Location chest : List.of(at(location, 3, 0, 0), at(location, -3, 0, 0), at(location, 0, 0, 3), at(location, 0, 0, -3))) {
            placeEventChest(chest, activeEvent, 5 + random.nextInt(5), random.nextBoolean());
        }
        spawnEventEffects(location, Particle.HEART, 4);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6Feast gestart op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + ". Race naar de caches."));
    }

    private void startAmbush() {
        World world = Bukkit.getWorlds().get(0);
        Location location = randomSurfaceLocation();
        activeEvent = "Raider Ambush";
        beginEvent(activeEvent);
        spawnBandits(world, location, 10, 60.0D, "&4Raider Elite");
        decorateSite(location, Material.OBSIDIAN, 3);
        placeEventChest(location, activeEvent, 11, true);
        spawnEventEffects(location, Particle.FLAME, 4);
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&4Raider Ambush op X:" + location.getBlockX() + " Z:" + location.getBlockZ() + ". Vecht for de cache."));
    }

    private void startBoss() {
        activeEvent = "Personal Boss Delegation";
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "boss spawn")) {
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&cThe Bosses plugin could not start a boss."));
        }
    }

    private boolean spawnLootStructure(Location location, boolean manual) {
        if (location == null || location.getWorld() == null || !eligibleStructureWorld(location.getWorld())) {
            return false;
        }
        Location center = undergroundStructureLocation(location);
        if (!manual && tooCloseToSpawn(center)) {
            return false;
        }
        buildAncientVault(center);
        String token = "loot-structure-" + center.getWorld().getName() + "-" + center.getBlockX() + "-" + center.getBlockZ() + "-" + System.currentTimeMillis();
        placeStructureChest(at(center, 0, 1, 0), "Ancient Loot Structure", token, true);
        placeStructureChest(at(center, 4, 1, 4), "Ancient Loot Structure", token, true);
        placeStructureChest(at(center, -4, 1, -4), "Ancient Loot Structure", token, true);
        spawnStructureSpawners(center);
        spawnStructureGuards(center);
        spawnEventEffects(at(center, 0, 2, 0), Particle.SMOKE, manual ? 2 : 0);
        return true;
    }

    private Location undergroundStructureLocation(Location location) {
        World world = location.getWorld();
        int minimum = Math.max(3, Math.min(10, (int) setting("lootStructureMinBedrockOffset", defaultSetting("lootStructureMinBedrockOffset"))));
        int maximum = Math.max(minimum, Math.min(10, (int) setting("lootStructureMaxBedrockOffset", defaultSetting("lootStructureMaxBedrockOffset"))));
        int offset = minimum + random.nextInt(maximum - minimum + 1);
        return new Location(world, location.getBlockX(), world.getMinHeight() + offset, location.getBlockZ());
    }

    private void buildAncientVault(Location center) {
        World world = center.getWorld();
        int y = center.getBlockY();
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int dy = 0; dy <= 7; dy++) {
                    at(center, x, dy, z).getBlock().setType(Material.AIR);
                }
                boolean outer = Math.abs(x) == 7 || Math.abs(z) == 7;
                boolean inner = Math.abs(x) <= 2 && Math.abs(z) <= 2;
                Material floor = outer ? Material.OBSIDIAN : inner ? Material.CRYING_OBSIDIAN : ((x + z) % 3 == 0 ? Material.BASALT : Material.BLACKSTONE);
                at(center, x, 0, z).getBlock().setType(floor);
                if (outer) {
                    for (int dy = 1; dy <= 4; dy++) {
                        boolean doorway = (Math.abs(x) <= 1 && Math.abs(z) == 7 && dy <= 3) || (Math.abs(z) <= 1 && Math.abs(x) == 7 && dy <= 3);
                        at(center, x, dy, z).getBlock().setType(doorway ? Material.AIR : ((dy == 4 || (x + z + dy) % 5 == 0) ? Material.OBSIDIAN : Material.BLACKSTONE));
                    }
                }
            }
        }
        for (int x : List.of(-5, 5)) {
            for (int z : List.of(-5, 5)) {
                for (int dy = 1; dy <= 5; dy++) {
                    at(center, x, dy, z).getBlock().setType(dy == 5 ? Material.SEA_LANTERN : Material.CRYING_OBSIDIAN);
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3 || (x == 0 && z == 0)) {
                    at(center, x, 5, z).getBlock().setType(Material.OBSIDIAN);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            at(center, x, 1, 7).getBlock().setType(Material.MAGMA_BLOCK);
            at(center, x, 1, -7).getBlock().setType(Material.MAGMA_BLOCK);
        }
        for (int z = -2; z <= 2; z++) {
            at(center, 7, 1, z).getBlock().setType(Material.MAGMA_BLOCK);
            at(center, -7, 1, z).getBlock().setType(Material.MAGMA_BLOCK);
        }
        world.spawnParticle(Particle.FLAME, at(center, 0, 2, 0), 120, 5.0D, 2.5D, 5.0D, 0.02D);
    }

    private void placeStructureChest(Location location, String label, String token, boolean elite) {
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, Math.max(4, (int) setting("lootStructureLootRolls", defaultSetting("lootStructureLootRolls"))), elite);
        CorruptedHeartService corruptedHearts = MitchSMP.corruptedHearts();
        if (corruptedHearts != null && percentRoll(corruptedHearts.getLootChancePercent("ancient_loot_structure"))) {
            chest.getInventory().addItem(corruptedHearts.createHeart(1));
        }
        EventCache cache = new EventCache(token, label);
        eventCaches.put(blockKey(location), cache);
        config.set(structureChestKey(location), cache.token() + "|" + cache.label());
        config.save();
    }

    private void spawnStructureSpawners(Location center) {
        int spawners = Math.max(1, (int) setting("lootStructureSpawnerCount", defaultSetting("lootStructureSpawnerCount")));
        List<Location> slots = List.of(at(center, 5, 1, 5), at(center, -5, 1, 5), at(center, 5, 1, -5), at(center, -5, 1, -5), at(center, 0, 1, 5), at(center, 0, 1, -5));
        for (int i = 0; i < Math.min(spawners, slots.size()); i++) {
            Location slot = slots.get(i);
            slot.getBlock().setType(Material.SPAWNER);
            tuneSpawner(slot);
        }
    }

    private void tuneSpawner(Location location) {
        try {
            Object state = location.getBlock().getState();
            state.getClass().getMethod("setSpawnedType", EntityType.class).invoke(state, EntityType.ZOMBIE);
            state.getClass().getMethod("setMinSpawnDelay", int.class).invoke(state, 60);
            state.getClass().getMethod("setMaxSpawnDelay", int.class).invoke(state, 140);
            state.getClass().getMethod("setSpawnCount", int.class).invoke(state, 4);
            state.getClass().getMethod("setRequiredPlayerRange", int.class).invoke(state, 18);
            state.getClass().getMethod("setMaxNearbyEntities", int.class).invoke(state, 18);
            state.getClass().getMethod("update").invoke(state);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not tune lootstructure spawner: " + exception.getMessage());
        }
    }

    private void spawnStructureGuards(Location center) {
        int amount = Math.max(6, (int) setting("lootStructureGuardCount", defaultSetting("lootStructureGuardCount")));
        double health = Math.max(40.0D, setting("lootStructureGuardHealth", defaultSetting("lootStructureGuardHealth")));
        for (int i = 0; i < amount; i++) {
            Location spawn = at(center, random.nextInt(11) - 5, 1, random.nextInt(11) - 5);
            Zombie guard = (Zombie) center.getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
            guard.setCustomName(Text.color(i % 5 == 0 ? "&4Vault Warden" : "&cVault Guard"));
            guard.setCustomNameVisible(true);
            if (guard.getAttribute(Attribute.MAX_HEALTH) != null) {
                guard.getAttribute(Attribute.MAX_HEALTH).setBaseValue(i % 5 == 0 ? health * 1.6D : health);
            }
            guard.setHealth(i % 5 == 0 ? health * 1.6D : health);
            guard.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 120, 0, true, true, true));
            guard.setFireTicks(0);
            if (guard.getEquipment() != null) {
                guard.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                guard.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                guard.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                guard.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                guard.getEquipment().setItemInMainHand(new ItemStack(i % 5 == 0 ? Material.NETHERITE_AXE : Material.NETHERITE_SWORD));
                zeroDropChances(guard.getEquipment());
            }
            try {
                guard.getClass().getMethod("setRemoveWhenFarAway", boolean.class).invoke(guard, false);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private void zeroDropChances(Object equipment) {
        for (String method : List.of("setHelmetDropChance", "setChestplateDropChance", "setLeggingsDropChance", "setBootsDropChance", "setItemInMainHandDropChance")) {
            try {
                equipment.getClass().getMethod(method, float.class).invoke(equipment, 0.0F);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @EventHandler
    public void onEventChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        EventCache cache = takeTrackedChest(block.getLocation());
        if (cache == null) {
            return;
        }
        event.setCancelled(true);
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        java.util.List<ItemStack> loot = new java.util.ArrayList<>();
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                loot.add(item);
            }
        }
        chest.getInventory().clear();
        block.setType(Material.AIR);
        Player player = event.getPlayer();
        player.getInventory().addItem(loot.toArray(new ItemStack[0])).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        boolean first = claimedEventTokens.add(cache.token());
        celebrateClaim(player, cache, first);
    }

    @EventHandler
    public void onEventChestBreak(BlockBreakEvent event) {
        if (hasTrackedChest(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cOpen de cache om hem te claimen.");
        }
    }

    private void fillEventLoot(Chest chest, int rolls, boolean elite) {
        chest.getInventory().clear();
        for (int i = 0; i < rolls; i++) {
            chest.getInventory().addItem(eventLoot(elite));
        }
    }

    private void placeEventChest(Location location, String label, int rolls, boolean elite) {
        location.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) location.getBlock().getState();
        fillEventLoot(chest, rolls, elite);
        trackEventChest(location, label);
    }

    private void beginEvent(String label) {
        currentEventToken = label + "-" + System.currentTimeMillis();
        activeEvent = label;
    }

    private void trackEventChest(Location location, String label) {
        eventCaches.put(blockKey(location), new EventCache(currentEventToken, label));
    }

    private EventCache takeTrackedChest(Location location) {
        EventCache cache = eventCaches.remove(blockKey(location));
        String key = structureChestKey(location);
        if (cache == null && config.contains(key)) {
            String raw = config.getString(key, "");
            String[] parts = raw.split("\\|", 2);
            cache = new EventCache(parts.length > 0 ? parts[0] : "loot-structure", parts.length > 1 ? parts[1] : "Ancient Loot Structure");
        }
        if (config.contains(key)) {
            config.set(key, null);
            config.save();
        }
        return cache;
    }

    private boolean hasTrackedChest(Location location) {
        return eventCaches.containsKey(blockKey(location)) || config.contains(structureChestKey(location));
    }

    private void celebrateClaim(Player player, EventCache cache, boolean first) {
        boolean structure = cache.label().toLowerCase().contains("structure");
        player.sendTitle(Text.color(first ? (structure ? "&6Vault geplunderd" : "&6Event gewonnen") : "&aCache geclaimed"), Text.color("&f" + cache.label()), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F, 1.2F);
        for (int i = 0; i < (first ? 4 : 2); i++) {
            player.getWorld().spawnEntity(player.getLocation().add(0.0D, 2.0D, 0.0D), EntityType.FIREWORK_ROCKET);
        }
        if (first) {
            Bukkit.broadcastMessage(Text.PREFIX + Text.color("&6" + player.getName() + (structure ? " plunderde " : " won ") + cache.label() + (structure ? "." : " door als eerste de cache te openen.")));
        } else {
            Text.msg(player, "&aEvent cache geclaimed.");
        }
    }

    private void decorateSite(Location center, Material accent, int radius) {
        World world = center.getWorld();
        int y = center.getBlockY() - 1;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius || Math.abs(z) == radius || random.nextInt(7) == 0) {
                    new Location(world, center.getBlockX() + x, y, center.getBlockZ() + z).getBlock().setType(accent);
                }
            }
        }
        spawnEventEffects(center, Particle.NOTE, 2);
    }

    private void spawnEventEffects(Location location, Particle particle, int fireworks) {
        World world = location.getWorld();
        world.spawnParticle(particle, location, 80, 3.0D, 2.0D, 3.0D, 0.1D);
        for (int i = 0; i < fireworks; i++) {
            world.spawnEntity(at(location, random.nextInt(5) - 2, 2, random.nextInt(5) - 2), EntityType.FIREWORK_ROCKET);
        }
    }

    private void spawnBandits(World world, Location center, int amount, double health, String name) {
        for (int i = 0; i < amount; i++) {
            Zombie bandit = (Zombie) world.spawnEntity(at(center, random.nextInt(9) - 4, 0, random.nextInt(9) - 4), EntityType.ZOMBIE);
            bandit.setCustomName(Text.color(name));
            bandit.setCustomNameVisible(true);
            if (bandit.getAttribute(Attribute.MAX_HEALTH) != null) {
                bandit.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            }
            bandit.setHealth(health);
        }
    }

    private Location at(Location base, int dx, int dy, int dz) {
        return new Location(base.getWorld(), base.getBlockX() + dx, base.getBlockY() + dy, base.getBlockZ() + dz);
    }

    private String blockKey(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private ItemStack eventLoot(boolean elite) {
        if (percentRoll(setting("proGearChance", defaultSetting("proGearChance")))) {
            return proGear(elite);
        }
        if (percentRoll(setting("enchantedAppleChance", elite ? 8.0D : 2.0D))) {
            return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        }
        int roll = random.nextInt(100);
        if (roll < 18) {
            return new ItemStack(Material.TOTEM_OF_UNDYING);
        }
        if (roll < 35) {
            return new ItemStack(Material.GOLDEN_APPLE, elite ? 3 + random.nextInt(5) : 1 + random.nextInt(3));
        }
        if (roll < 55) {
            return new ItemStack(Material.DIAMOND, elite ? 8 + random.nextInt(13) : 3 + random.nextInt(8));
        }
        if (roll < 73) {
            return new ItemStack(Material.EMERALD, elite ? 10 + random.nextInt(19) : 4 + random.nextInt(10));
        }
        if (roll < 88) {
            return new ItemStack(Material.EXPERIENCE_BOTTLE, elite ? 24 + random.nextInt(33) : 8 + random.nextInt(17));
        }
        return new ItemStack(Material.GOLD_INGOT, elite ? 16 + random.nextInt(33) : 6 + random.nextInt(13));
    }

    private ItemStack proGear(boolean elite) {
        Material material = switch (random.nextInt(6)) {
            case 0 -> Material.NETHERITE_SWORD;
            case 1 -> Material.NETHERITE_AXE;
            case 2 -> Material.NETHERITE_HELMET;
            case 3 -> Material.NETHERITE_CHESTPLATE;
            case 4 -> Material.NETHERITE_LEGGINGS;
            default -> Material.NETHERITE_BOOTS;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(elite ? "&dEvent Pro Gear" : "&bEvent Gear"));
            meta.setLore(List.of(Text.color("&7Zeldzame event loot."), Text.color("&8Niet koopbaar met hearts.")));
            meta.setUnbreakable(true);
            if (material.name().contains("SWORD") || material.name().contains("AXE")) {
                meta.addEnchant(Enchantment.SHARPNESS, elite ? 7 : 5, true);
                meta.addEnchant(Enchantment.FIRE_ASPECT, elite ? 2 : 1, true);
            } else {
                meta.addEnchant(Enchantment.PROTECTION, elite ? 6 : 4, true);
                meta.addEnchant(Enchantment.UNBREAKING, elite ? 8 : 5, true);
            }
            meta.addEnchant(Enchantment.MENDING, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean config(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.events.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length == 1) {
            Text.msg(sender, "&6Event tuning:");
            for (String key : List.of("autoWorldEvents", "autoIntervalMinutes", "eventMinDistance", "eventMaxDistance", "lootStructureChancePercent", "lootStructureMinDistanceFromSpawn", "lootStructureMinBedrockOffset", "lootStructureMaxBedrockOffset", "lootStructureGuardCount", "lootStructureGuardHealth", "lootStructureSpawnerCount", "lootStructureLootRolls", "proGearChance", "enchantedAppleChance", "kothMoney", "eventMoneyMultiplier", "economyBaseline", "economyScaleMax")) {
                double current = setting(key, defaultSetting(key));
                String display = key.equals("economyScaleMax") && current <= 0.0D ? "onbeperkt (0)" : String.valueOf(current);
                Text.msg(sender, "&7" + key + ": &f" + display);
            }
            return true;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /event config <key> <value>");
            return true;
        }
        String key = args[1];
        try {
            double value = Math.max(0.0D, Double.parseDouble(args[2]));
            config.set("setting." + key, value);
            config.save();
            Text.msg(sender, "&aEvent setting &f" + key + " &agezet naar &f" + value + "&a.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue moet een nummer zijn.");
        }
        return true;
    }

    private double setting(String key, double fallback) {
        return config.getDouble("setting." + key, fallback);
    }

    private boolean percentRoll(double percent) {
        return percent > 0.0D && random.nextDouble() * 100.0D < percent;
    }

    private double eventMoney(double base) {
        return base * setting("eventMoneyMultiplier", 1.0D) * economyScale();
    }

    private double economyScale() {
        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            return 1.0D;
        }
        List<Map.Entry<UUID, Double>> top = economy.topBalances(250);
        if (top.isEmpty()) {
            return 1.0D;
        }
        double average = top.stream().mapToDouble(Map.Entry::getValue).sum() / top.size();
        double richest = top.get(0).getValue();
        double pressure = Math.max(average, richest * 0.25D);
        double baseline = Math.max(1.0D, setting("economyBaseline", 5_000.0D));
        double scale = Math.max(1.0D, Math.sqrt(Math.max(1.0D, pressure / baseline)));
        double cap = setting("economyScaleMax", 0.0D);
        return cap <= 0.0D ? scale : Math.min(Math.max(1.0D, cap), scale);
    }

    private double defaultSetting(String key) {
        return switch (key) {
            case "autoWorldEvents" -> 0.0D;
            case "autoIntervalMinutes" -> 30.0D;
            case "eventMinDistance" -> 300.0D;
            case "eventMaxDistance" -> 1600.0D;
            case "lootStructureChancePercent" -> 0.12D;
            case "lootStructureMinDistanceFromSpawn" -> 600.0D;
            case "lootStructureMinBedrockOffset" -> 6.0D;
            case "lootStructureMaxBedrockOffset" -> 10.0D;
            case "lootStructureGuardCount" -> 16.0D;
            case "lootStructureGuardHealth" -> 90.0D;
            case "lootStructureSpawnerCount" -> 4.0D;
            case "lootStructureLootRolls" -> 9.0D;
            case "proGearChance" -> 0.05D;
            case "enchantedAppleChance" -> 3.0D;
            case "kothMoney" -> 500.0D;
            case "eventMoneyMultiplier" -> 1.0D;
            case "economyBaseline" -> 5_000.0D;
            case "economyScaleMax" -> 0.0D;
            default -> 0.0D;
        };
    }

    private boolean eligibleStructureWorld(World world) {
        if (world == null) {
            return false;
        }
        if (!Bukkit.getWorlds().isEmpty() && !world.equals(Bukkit.getWorlds().get(0))) {
            return false;
        }
        String name = world.getName().toLowerCase();
        return !name.startsWith("bedwars_")
            && !name.startsWith("bw_")
            && !name.startsWith("tntrun_")
            && !name.contains("hall_of_fame")
            && !name.contains("jail")
            && !name.contains("boss")
            && !name.contains("hell");
    }

    private boolean tooCloseToSpawn(Location location) {
        Location spawn = location.getWorld().getSpawnLocation();
        double min = setting("lootStructureMinDistanceFromSpawn", defaultSetting("lootStructureMinDistanceFromSpawn"));
        return spawn.getWorld().equals(location.getWorld()) && spawn.distanceSquared(location) < min * min;
    }

    private String generatedChunkKey(World world, int chunkX, int chunkZ) {
        return "lootStructure.generated." + safeKey(world.getName()) + "." + chunkX + "." + chunkZ;
    }

    private String structureChestKey(Location location) {
        return "lootStructure.chest." + safeKey(blockKey(location));
    }

    private String safeKey(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private Location randomSurfaceLocation() {
        World world = Bukkit.getWorlds().get(0);
        List<Player> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                candidates.add(player);
            }
        }
        Location anchor = candidates.isEmpty() ? world.getSpawnLocation() : candidates.get(random.nextInt(candidates.size())).getLocation();
        int min = Math.max(32, (int) setting("eventMinDistance", 300.0D));
        int max = Math.max(min + 1, (int) setting("eventMaxDistance", 1600.0D));
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int distance = min + random.nextInt(max - min + 1);
        int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return world.getHighestBlockAt(x, z).getLocation().add(0, 1, 0);
    }

    private Location randomSurfaceNear(Player player, int min, int max) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int distance = min + random.nextInt(Math.max(1, max - min));
        int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        return player.getWorld().getHighestBlockAt(x, z).getLocation().add(0, 1, 0);
    }

    private record EventCache(String token, String label) {
    }
}


