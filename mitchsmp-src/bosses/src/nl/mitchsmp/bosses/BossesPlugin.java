package nl.mitchsmp.bosses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import nl.mitchsmp.core.api.BossShardService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

public final class BossesPlugin extends JavaPlugin implements Listener, TabCompleter {
    private final Random random = new Random();
    private final Map<UUID, Zombie> activeBosses = new HashMap<>();
    private final Map<UUID, UUID> bossOwners = new HashMap<>();
    private PropertiesFile data;
    private NamespacedKey bossKey;
    private NamespacedKey ownerKey;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("bosses.properties"));
        bossKey = new NamespacedKey(this, "bloodbound_boss");
        ownerKey = new NamespacedKey(this, "boss_owner");
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("boss") != null) {
            getCommand("boss").setExecutor(this);
            getCommand("boss").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tickSpawnChecks, 20L * 60L, 20L * 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickBosses, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickBossAttacks, 100L, 100L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            Text.msg(sender, "&4Bloodbound Bosses &8| &7active: &f" + activeBosses.size());
            Text.msg(sender, "&7Personal roll: &f" + format(setting("chancePercent", 25.0D)) + "% every " + format(setting("intervalMinutes", 60.0D)) + " minutes");
            return true;
        }
        if (!MitchSMP.permissions().has(sender, "mitchsmp.bosses.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args[0].equalsIgnoreCase("spawn")) {
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : randomEligiblePlayer();
            if (target == null || !eligible(target)) {
                Text.msg(sender, "&cNo eligible online target found.");
                return true;
            }
            Zombie boss = spawnFor(target);
            Text.msg(sender, boss == null ? "&cBoss spawn failed." : "&aSpawned a Bloodbound boss near &f" + target.getName() + "&a.");
            return true;
        }
        if (args[0].equalsIgnoreCase("config")) {
            return configure(sender, args);
        }
        Text.msg(sender, "&cUsage: /boss <status|spawn [player]|config <key> <value>>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("status"));
            if (MitchSMP.permissions().has(sender, "mitchsmp.bosses.admin")) {
                options.add("spawn");
                options.add("config");
            }
            return Tab.complete(args[0], options);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[1], "chancePercent", "intervalMinutes", "minDistance", "maxDistance", "health", "shardMin", "shardMax", "rareGearChancePercent", "spawnAttempts", "builtBlockBuffer", "requireNaturalGround");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[2], "0.05", "1", "3", "10", "25", "60", "260");
        }
        return List.of();
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        activeBosses.remove(event.getEntity().getUniqueId());
        UUID ownerId = bossOwners.remove(event.getEntity().getUniqueId());
        int minimum = Math.max(1, (int) Math.round(setting("shardMin", 1.0D)));
        int maximum = Math.max(minimum, (int) Math.round(setting("shardMax", 3.0D)));
        int amount = minimum + random.nextInt(maximum - minimum + 1);
        BossShardService shards = MitchSMP.bossShards();
        if (shards != null) {
            event.getDrops().add(shards.createShard(amount));
        }
        if (random.nextDouble() < setting("rareGearChancePercent", 0.05D) / 100.0D) {
            event.getDrops().add(rareGear());
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killer.sendTitle(Text.color("&4BOSS DEFEATED"), Text.color("&d" + amount + " Boss Shard" + (amount == 1 ? "" : "s") + " dropped"), 5, 45, 10);
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F);
            Text.msg(killer, "&dThe boss dropped &f" + amount + " &dBoss Shard(s).");
        }
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        if (owner != null && (killer == null || !owner.getUniqueId().equals(killer.getUniqueId()))) {
            Text.msg(owner, "&7Your nearby Bloodbound boss was defeated.");
        }
    }

    private void tickSpawnChecks() {
        long now = System.currentTimeMillis();
        long interval = Math.max(60_000L, Math.round(setting("intervalMinutes", 60.0D) * 60_000.0D));
        double chance = Math.max(0.0D, Math.min(1.0D, setting("chancePercent", 25.0D) / 100.0D));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!eligible(player) || hasActiveBoss(player.getUniqueId())) {
                continue;
            }
            String key = "lastRoll." + player.getUniqueId();
            long last = data.getLong(key, 0L);
            if (last > 0L && now - last < interval) {
                continue;
            }
            data.set(key, now);
            if (random.nextDouble() <= chance) {
                spawnFor(player);
            }
        }
        data.save();
    }

    private Zombie spawnFor(Player player) {
        Location location = randomSurfaceNear(player);
        if (location == null) {
            return null;
        }
        Zombie boss = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        boss.setCustomName(Text.color("&4Bloodbound Night Stalker"));
        boss.setCustomNameVisible(true);
        double health = Math.max(40.0D, setting("health", 260.0D));
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        }
        boss.setHealth(health);
        boss.setFireTicks(0);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        boss.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        if (boss.getEquipment() != null) {
            boss.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            boss.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        }
        activeBosses.put(boss.getUniqueId(), boss);
        bossOwners.put(boss.getUniqueId(), player.getUniqueId());
        player.sendTitle(Text.color("&4A PRESENCE AWAKENS"), Text.color("&7A Night Stalker is nearby"), 10, 55, 15);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.7F);
        Text.msg(player, "&4A Night Stalker emerged nearby at &fX:" + location.getBlockX() + " Z:" + location.getBlockZ() + "&4.");
        return boss;
    }

    private void tickBosses() {
        for (Map.Entry<UUID, Zombie> entry : new ArrayList<>(activeBosses.entrySet())) {
            Zombie boss = entry.getValue();
            try {
                if (boss.getHealth() <= 0.0D) {
                    activeBosses.remove(entry.getKey());
                    bossOwners.remove(entry.getKey());
                    continue;
                }
                boss.setFireTicks(0);
                boss.getWorld().spawnParticle(Particle.SMOKE, boss.getLocation(), 7, 0.4D, 0.8D, 0.4D, 0.01D);
            } catch (RuntimeException exception) {
                activeBosses.remove(entry.getKey());
                bossOwners.remove(entry.getKey());
            }
        }
    }

    private void tickBossAttacks() {
        for (Zombie boss : new ArrayList<>(activeBosses.values())) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!sameWorld(boss.getLocation(), target.getLocation()) || boss.getLocation().distanceSquared(target.getLocation()) > 144.0D || MitchSMP.permissions().isAdminRestricted(target)) {
                    continue;
                }
                if (random.nextDouble() > 0.35D) {
                    continue;
                }
                Location impact = target.getLocation();
                impact.getWorld().createExplosion(impact, 1.8F, false, false);
                target.setHealth(Math.max(1.0D, target.getHealth() - 4.0D));
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, false, true, true));
                target.playSound(impact, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8F, 0.8F);
            }
        }
    }

    private boolean isBoss(org.bukkit.entity.LivingEntity entity) {
        return entity != null && entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    private boolean hasActiveBoss(UUID playerId) {
        return bossOwners.containsValue(playerId);
    }

    private Player randomEligiblePlayer() {
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream().filter(this::eligible).toList();
        return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
    }

    private boolean eligible(Player player) {
        if (player == null || player.getWorld() == null || MitchSMP.permissions().isAdminRestricted(player)) {
            return false;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return !world.startsWith("bedwars_")
            && !world.startsWith("tntrun_")
            && !world.startsWith("spleef_")
            && !world.contains("skyblock")
            && !world.contains("hub")
            && !world.contains("hall_of_fame")
            && !world.contains("jail")
            && !world.contains("boss");
    }

    private Location randomSurfaceNear(Player player) {
        int minimum = Math.max(6, (int) Math.round(setting("minDistance", 12.0D)));
        int maximum = Math.max(minimum + 1, (int) Math.round(setting("maxDistance", 32.0D)));
        int attempts = Math.max(8, (int) Math.round(setting("spawnAttempts", 36.0D)));
        int builtBuffer = Math.max(0, (int) Math.round(setting("builtBlockBuffer", 14.0D)));
        boolean requireNatural = setting("requireNaturalGround", 1.0D) > 0.0D;
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = minimum + random.nextInt(maximum - minimum + 1);
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Block highest = player.getWorld().getHighestBlockAt(x, z);
            if (highest == null || unsafe(highest.getType())) {
                continue;
            }
            Location spawn = highest.getLocation().add(0.5D, 1.0D, 0.5D);
            if (!safeSpawnAir(spawn) || (requireNatural && !naturalSurface(highest.getType())) || builtAreaNearby(player, spawn, builtBuffer)) {
                continue;
            }
            return spawn;
        }
        return null;
    }

    private boolean safeSpawnAir(Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            return false;
        }
        String feet = spawn.getWorld().getBlockAt(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()).getType().name();
        String head = spawn.getWorld().getBlockAt(spawn.getBlockX(), spawn.getBlockY() + 1, spawn.getBlockZ()).getType().name();
        return feet.equals("AIR") && head.equals("AIR");
    }

    private boolean naturalSurface(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.equals("GRASS_BLOCK")
            || name.equals("DIRT")
            || name.equals("COARSE_DIRT")
            || name.equals("PODZOL")
            || name.equals("MYCELIUM")
            || name.equals("SAND")
            || name.equals("RED_SAND")
            || name.equals("GRAVEL")
            || name.equals("STONE")
            || name.equals("DEEPSLATE")
            || name.equals("SNOW_BLOCK")
            || name.equals("SNOW")
            || name.endsWith("_NYLIUM");
    }

    private boolean builtAreaNearby(Player player, Location center, int radius) {
        if (radius <= 0 || center == null || center.getWorld() == null) {
            return false;
        }
        int step = radius <= 10 ? 1 : 2;
        int minY = Math.max(center.getWorld().getMinHeight(), center.getBlockY() - 5);
        int maxY = center.getBlockY() + 7;
        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x += step) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z += step) {
                for (int y = minY; y <= maxY; y += step) {
                    Material material = center.getWorld().getBlockAt(x, y, z).getType();
                    if (builtLike(material)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean builtLike(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.contains("CHEST")
            || name.contains("FURNACE")
            || name.contains("CRAFTING")
            || name.contains("ANVIL")
            || name.contains("BED")
            || name.contains("DOOR")
            || name.contains("TRAPDOOR")
            || name.contains("GLASS")
            || name.contains("TORCH")
            || name.contains("LANTERN")
            || name.contains("SIGN")
            || name.contains("RAIL")
            || name.endsWith("_PLANKS")
            || name.endsWith("_SLAB")
            || name.endsWith("_STAIRS")
            || name.endsWith("_FENCE")
            || name.endsWith("_WALL")
            || name.equals("COBBLESTONE")
            || name.equals("STONE_BRICKS")
            || name.equals("BRICKS")
            || name.equals("IRON_BLOCK")
            || name.equals("GOLD_BLOCK")
            || name.equals("DIAMOND_BLOCK")
            || name.equals("EMERALD_BLOCK");
    }

    private boolean unsafe(Material material) {
        String name = material == null ? "" : material.name();
        return material == Material.AIR || name.contains("WATER") || name.contains("LAVA") || name.contains("FIRE") || name.contains("MAGMA");
    }

    private ItemStack rareGear() {
        Material[] materials = {Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        ItemStack item = new ItemStack(materials[random.nextInt(materials.length)]);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&dNight Stalker Relic"));
            meta.setLore(List.of(Text.color("&7Extremely rare Bloodbound boss loot.")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean configure(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.msg(sender, "&cUsage: /boss config <key> <value>");
            return true;
        }
        String key = args[1];
        if (!List.of("chancePercent", "intervalMinutes", "minDistance", "maxDistance", "health", "shardMin", "shardMax", "rareGearChancePercent", "spawnAttempts", "builtBlockBuffer", "requireNaturalGround").contains(key)) {
            Text.msg(sender, "&cUnknown boss setting.");
            return true;
        }
        try {
            double value = Math.max(0.0D, Double.parseDouble(args[2]));
            data.set("setting." + key, value);
            data.save();
            Text.msg(sender, "&aBoss setting updated: &f" + key + " = " + format(value));
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue must be a number.");
        }
        return true;
    }

    private double setting(String key, double fallback) {
        return data.getDouble("setting." + key, fallback);
    }

    private boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null && first.getWorld().getName().equals(second.getWorld().getName());
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
