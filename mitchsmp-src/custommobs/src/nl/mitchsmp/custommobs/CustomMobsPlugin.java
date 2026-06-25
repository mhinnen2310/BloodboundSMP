package nl.mitchsmp.custommobs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomMobsPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String ARCHFIEND_MODEL = "archfiend";
    private static final String ARCHFIEND_SOURCE = "models/bloodbound_archfiend.bbmodel";
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("\"elements\"\\s*:\\s*\\[", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXTURE_PATTERN = Pattern.compile("\"textures\"\\s*:\\s*\\[", Pattern.CASE_INSENSITIVE);
    private final Map<UUID, VisualLink> visuals = new HashMap<>();
    private PropertiesFile config;
    private ModelReport archfiendReport = ModelReport.empty();

    @Override
    public void onEnable() {
        config = new PropertiesFile(getDataFolder().toPath().resolve("custommobs.properties"));
        defaults();
        saveBundledModel();
        archfiendReport = inspectModel(getDataFolder().toPath().resolve(ARCHFIEND_SOURCE));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("custommob") != null) {
            getCommand("custommob").setExecutor(this);
            getCommand("custommob").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tickVisuals, 20L, Math.max(1L, config.getInt("archfiend.tick_ticks", 5)));
        getLogger().info("Bloodbound Archfiend model loaded: " + archfiendReport.summary());
    }

    @Override
    public void onDisable() {
        for (VisualLink link : new ArrayList<>(visuals.values())) {
            removeVisual(link);
        }
        visuals.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.custommobs.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            Text.msg(sender, "&4Bloodbound CustomMobs &8| &7active visuals: &f" + visuals.size());
            Text.msg(sender, "&7Archfiend: &f" + archfiendReport.summary());
            Text.msg(sender, "&7Resourcepack item model: &fbloodbound:" + ARCHFIEND_MODEL);
            return true;
        }
        if (sub.equals("reload")) {
            config.load();
            archfiendReport = inspectModel(getDataFolder().toPath().resolve(ARCHFIEND_SOURCE));
            Text.msg(sender, "&aCustom mob config and model metadata reloaded.");
            return true;
        }
        if (sub.equals("clear")) {
            for (VisualLink link : new ArrayList<>(visuals.values())) {
                removeVisual(link);
            }
            visuals.clear();
            Text.msg(sender, "&aRemoved all custom mob visuals.");
            return true;
        }
        if (sub.equals("attach")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cPlayers only.");
                return true;
            }
            LivingEntity target = nearestLiving(player, 16.0D);
            if (target == null) {
                Text.msg(player, "&cNo nearby living entity found.");
                return true;
            }
            attachArchfiend(target);
            Text.msg(player, "&aAttached Archfiend visual to nearby entity.");
            return true;
        }
        if (sub.equals("test")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cPlayers only.");
                return true;
            }
            ArmorStand stand = createVisualStand(player.getLocation(), null);
            visuals.put(stand.getUniqueId(), new VisualLink(stand.getUniqueId(), stand.getUniqueId(), true));
            Text.msg(player, "&aSpawned standalone Archfiend visual. Use &f/custommob clear&a to remove.");
            return true;
        }
        Text.msg(sender, "&cUsage: /custommob <status|reload|attach|test|clear>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.custommobs.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Tab.complete(args[0], "status", "reload", "attach", "test", "clear");
        }
        return List.of();
    }

    public void attachArchfiend(Entity boss) {
        if (!(boss instanceof LivingEntity living) || boss.getWorld() == null) {
            return;
        }
        removeVisual(visuals.remove(boss.getUniqueId()));
        ArmorStand stand = createVisualStand(boss.getLocation(), boss);
        visuals.put(boss.getUniqueId(), new VisualLink(boss.getUniqueId(), stand.getUniqueId(), false));
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) || entity.getWorld() == null || !entity.getWorld().getName().startsWith("mitchsmp_hellboss_")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isEntityAlive(entity)) {
                return;
            }
            String name = customName(entity);
            if (name.toLowerCase(Locale.ROOT).contains("infernal sovereign")) {
                attachArchfiend(entity);
            }
        }, 5L);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        VisualLink link = visuals.remove(event.getEntity().getUniqueId());
        removeVisual(link);
    }

    private void tickVisuals() {
        for (VisualLink link : new ArrayList<>(visuals.values())) {
            Entity visual = findEntity(link.visualId());
            if (!(visual instanceof ArmorStand stand) || !isEntityAlive(visual)) {
                visuals.remove(link.bossId());
                continue;
            }
            if (link.standalone()) {
                stand.getWorld().spawnParticle(Particle.FLAME, stand.getLocation().add(0.0D, 1.3D, 0.0D), 1, 0.15D, 0.2D, 0.15D, 0.0D);
                continue;
            }
            Entity boss = findEntity(link.bossId());
            if (!isEntityAlive(boss)) {
                removeVisual(link);
                visuals.remove(link.bossId());
                continue;
            }
            Location location = boss.getLocation();
            Location target = new Location(location.getWorld(), location.getX(), location.getY() + config.getDouble("archfiend.offset_y", -0.25D), location.getZ(), location.getYaw(), location.getPitch());
            stand.teleport(target);
            stand.setFireTicks(0);
            if (configBool("archfiend.crimson_particles", true)) {
                boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0D, 1.8D, 0.0D), 2, 0.5D, 0.7D, 0.5D, 0.01D);
            }
        }
    }

    private ArmorStand createVisualStand(Location location, Entity owner) {
        World world = location.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomNameVisible(false);
        setHelmet(stand, archfiendItem());
        reflectFlag(stand, "setMarker", true);
        reflectFlag(stand, "setInvulnerable", true);
        reflectFlag(stand, "setPersistent", false);
        if (owner != null) {
            addScoreboardTag(stand, "bloodbound_archfiend_visual");
            addScoreboardTag(stand, "owner_" + owner.getUniqueId());
        }
        return stand;
    }

    private ItemStack archfiendItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&4Bloodbound Archfiend Model"));
            meta.setItemModel(new NamespacedKey("bloodbound", ARCHFIEND_MODEL));
            meta.setCustomModelData(910100);
            item.setItemMeta(meta);
        }
        return item;
    }

    private LivingEntity nearestLiving(Player player, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        if (player.getWorld() == null) {
            return null;
        }
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof LivingEntity living && !living.equals(player)) {
                double distance = living.getLocation().distanceSquared(player.getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = living;
                }
            }
        }
        return best;
    }

    private Entity findEntity(UUID id) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (id.equals(entity.getUniqueId())) {
                    return entity;
                }
            }
        }
        return null;
    }

    private void removeVisual(VisualLink link) {
        if (link == null) {
            return;
        }
        Entity visual = findEntity(link.visualId());
        if (visual != null) {
            visual.remove();
        }
    }

    private void defaults() {
        boolean changed = false;
        changed |= setDefault("archfiend.offset_y", -0.25D);
        changed |= setDefault("archfiend.tick_ticks", 5);
        changed |= setDefault("archfiend.crimson_particles", true);
        if (changed) {
            config.save();
        }
    }

    private boolean setDefault(String key, Object value) {
        if (config.contains(key)) {
            return false;
        }
        config.set(key, value);
        return true;
    }

    private boolean configBool(String key, boolean fallback) {
        return Boolean.parseBoolean(config.getString(key, String.valueOf(fallback)));
    }

    private void saveBundledModel() {
        Path target = getDataFolder().toPath().resolve(ARCHFIEND_SOURCE);
        if (Files.exists(target)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(ARCHFIEND_SOURCE)) {
                if (input == null) {
                    throw new IOException("resource not found");
                }
                Files.copy(input, target);
            }
        } catch (RuntimeException | IOException exception) {
            getLogger().warning("Could not copy bundled Archfiend model: " + exception.getMessage());
        }
    }

    private ModelReport inspectModel(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            int elements = countObjectsAfterArray(text, ELEMENT_PATTERN);
            int textures = countObjectsAfterArray(text, TEXTURE_PATTERN);
            String name = first(text, "\"name\"\\s*:\\s*\"([^\"]+)\"", "unknown");
            String resolution = first(text, "\"resolution\"\\s*:\\s*\\{\\s*\"width\"\\s*:\\s*(\\d+)\\s*,\\s*\"height\"\\s*:\\s*(\\d+)", "?x?");
            return new ModelReport(name, elements, textures, resolution);
        } catch (IOException exception) {
            return new ModelReport("missing", 0, 0, "unknown");
        }
    }

    private int countObjectsAfterArray(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return 0;
        }
        int depth = 0;
        int objects = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = matcher.end(); i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    objects++;
                }
                depth++;
            } else if (ch == '}') {
                depth = Math.max(0, depth - 1);
            } else if (ch == ']' && depth == 0) {
                break;
            }
        }
        return objects;
    }

    private String first(String text, String pattern, String fallback) {
        Matcher matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(text);
        if (!matcher.find()) {
            return fallback;
        }
        if (matcher.groupCount() >= 2) {
            return matcher.group(1) + "x" + matcher.group(2);
        }
        return matcher.group(1);
    }

    private void reflectFlag(Entity entity, String method, boolean value) {
        try {
            entity.getClass().getMethod(method, boolean.class).invoke(entity, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private boolean isEntityAlive(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            Object valid = entity.getClass().getMethod("isValid").invoke(entity);
            if (valid instanceof Boolean bool && !bool) {
                return false;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Object dead = entity.getClass().getMethod("isDead").invoke(entity);
            return !(dead instanceof Boolean bool && bool);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    private String customName(Entity entity) {
        try {
            Object value = entity.getClass().getMethod("getCustomName").invoke(entity);
            return value == null ? "" : Text.stripColorCodes(String.valueOf(value));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return "";
        }
    }

    private void setHelmet(ArmorStand stand, ItemStack item) {
        try {
            Object equipment = stand.getClass().getMethod("getEquipment").invoke(stand);
            if (equipment != null) {
                equipment.getClass().getMethod("setHelmet", ItemStack.class).invoke(equipment, item);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            stand.getClass().getMethod("setHelmet", ItemStack.class).invoke(stand, item);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void addScoreboardTag(Entity entity, String tag) {
        try {
            entity.getClass().getMethod("addScoreboardTag", String.class).invoke(entity, tag);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private record VisualLink(UUID bossId, UUID visualId, boolean standalone) {
    }

    private record ModelReport(String name, int elements, int textures, String resolution) {
        static ModelReport empty() {
            return new ModelReport("missing", 0, 0, "unknown");
        }

        String summary() {
            return name + " | elements=" + elements + " | textures=" + textures + " | " + resolution;
        }
    }
}
