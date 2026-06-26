package nl.mitchsmp.custommobs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private static final Pattern ANIMATION_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private final Map<UUID, VisualLink> visuals = new HashMap<>();
    private final Map<UUID, List<Location>> solidFootprints = new HashMap<>();
    private final Map<UUID, AnimationPulse> animationPulses = new HashMap<>();
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
            Text.msg(sender, "&7Visual culling mode: &fmarker=" + configBool("visual.marker", false));
            return true;
        }
        if (sub.equals("reload")) {
            config.load();
            saveBundledModel();
            archfiendReport = inspectModel(getDataFolder().toPath().resolve(ARCHFIEND_SOURCE));
            Text.msg(sender, "&aCustom mob config and model metadata reloaded.");
            return true;
        }
        if (sub.equals("models")) {
            Text.msg(sender, "&4Bloodbound model IDs:");
            Text.msg(sender, "&7- &farchfiend &8(default endboss visual)");
            Text.msg(sender, "&7Use resourcepack item models as &fbloodbound:<id>&7.");
            Text.msg(sender, "&7Upload .bbmodel sources to &fplugins/MitchSMP-CustomMobs/models/&7 and rebuild the resourcepack model JSON.");
            return true;
        }
        if (sub.equals("animations")) {
            List<String> names = archfiendReport.animations();
            Text.msg(sender, "&4Archfiend animation metadata:");
            if (names.isEmpty()) {
                Text.msg(sender, "&7No Blockbench animations embedded yet. The plugin is ready to expose them once the model includes animation tracks.");
            } else {
                Text.msg(sender, "&7" + String.join(", ", names));
            }
            return true;
        }
        if (sub.equals("uploadinfo")) {
            Text.msg(sender, "&4Custom model pipeline");
            Text.msg(sender, "&71. Add the .bbmodel source under &fplugins/MitchSMP-CustomMobs/models/<id>.bbmodel&7.");
            Text.msg(sender, "&72. Add the matching item model and textures to the Bloodbound resourcepack as &fbloodbound:<id>&7.");
            Text.msg(sender, "&73. Use &f/custommob place <id> ghost|solid &7to spawn it.");
            Text.msg(sender, "&7Solid mode creates a small tracked barrier footprint; ghost mode is visual only.");
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
        if (sub.equals("clone")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cPlayers only.");
                return true;
            }
            String targetName = args.length > 1 ? args[1] : player.getName();
            spawnPlayerClone(player.getLocation(), targetName);
            Text.msg(player, "&aSpawned Bloodbound clone for &f" + targetName + "&a.");
            return true;
        }
        if (sub.equals("test") || sub.equals("place")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cPlayers only.");
                return true;
            }
            String model = sub.equals("place") && args.length > 1 ? safeModelId(args[1]) : ARCHFIEND_MODEL;
            boolean solid = sub.equals("place") && args.length > 2 && args[2].equalsIgnoreCase("solid");
            ArmorStand stand = createVisualStand(player.getLocation(), null, model);
            if (solid) {
                createSolidFootprint(stand.getUniqueId(), stand.getLocation());
            }
            visuals.put(stand.getUniqueId(), new VisualLink(stand.getUniqueId(), stand.getUniqueId(), true));
            Text.msg(player, "&aSpawned standalone &f" + model + " &avisual in &f" + (solid ? "solid" : "ghost") + " &amode. Use &f/custommob clear&a to remove.");
            return true;
        }
        Text.msg(sender, "&cUsage: /custommob <status|reload|models|animations|uploadinfo|attach|clone|test|place|clear>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.custommobs.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Tab.complete(args[0], "status", "reload", "models", "animations", "uploadinfo", "attach", "clone", "test", "place", "clear");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clone")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return Tab.complete(args[1], ARCHFIEND_MODEL);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("place")) {
            return Tab.complete(args[2], "ghost", "solid");
        }
        return List.of();
    }

    public void attachArchfiend(Entity boss) {
        if (!(boss instanceof LivingEntity living) || boss.getWorld() == null) {
            return;
        }
        removeVisual(visuals.remove(boss.getUniqueId()));
        ArmorStand stand = createVisualStand(boss.getLocation(), boss, ARCHFIEND_MODEL);
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
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.contains("infernal sovereign") || normalized.contains("bloodbound archfiend")) {
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
                tickAnimation(stand);
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
            AnimationPulse pulse = animationPulses.get(stand.getUniqueId());
            if (pulse != null) {
                target.add(0.0D, animationBob(pulse), 0.0D);
            }
            stand.teleport(target);
            stand.setFireTicks(0);
            if (configBool("archfiend.crimson_particles", true)) {
                boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0D, 1.8D, 0.0D), 2, 0.5D, 0.7D, 0.5D, 0.01D);
            }
            tickAnimation(stand);
        }
    }

    public void playArchfiendAnimation(Entity boss, String animation) {
        if (boss == null) {
            return;
        }
        VisualLink link = visuals.get(boss.getUniqueId());
        if (link == null) {
            return;
        }
        animationPulses.put(link.visualId(), new AnimationPulse(animation == null ? "pulse" : animation, System.currentTimeMillis() + 1400L));
    }

    private void tickAnimation(ArmorStand stand) {
        AnimationPulse pulse = animationPulses.get(stand.getUniqueId());
        if (pulse == null) {
            return;
        }
        pulse.ticks++;
        if (pulse.untilMillis < System.currentTimeMillis()) {
            animationPulses.remove(stand.getUniqueId());
            return;
        }
        Location center = stand.getLocation().add(0.0D, 1.2D, 0.0D);
        String animation = pulse.name.toLowerCase(Locale.ROOT);
        if (animation.contains("nova")) {
            stand.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 8, 0.7D, 0.5D, 0.7D, 0.02D);
        } else if (animation.contains("chain")) {
            stand.getWorld().spawnParticle(Particle.SMOKE, center, 8, 0.5D, 0.55D, 0.5D, 0.01D);
        } else if (animation.contains("rend")) {
            stand.getWorld().spawnParticle(Particle.HEART, center, 2, 0.35D, 0.45D, 0.35D, 0.01D);
        } else {
            stand.getWorld().spawnParticle(Particle.CRIT, center, 5, 0.45D, 0.45D, 0.45D, 0.03D);
        }
    }

    private double animationBob(AnimationPulse pulse) {
        return Math.sin(pulse.ticks * 0.55D) * 0.16D;
    }

    private ArmorStand createVisualStand(Location location, Entity owner, String modelId) {
        World world = location.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomNameVisible(false);
        setHelmet(stand, archfiendItem(modelId));
        reflectFlag(stand, "setMarker", configBool("visual.marker", false));
        reflectFlag(stand, "setInvulnerable", true);
        reflectFlag(stand, "setPersistent", false);
        reflectFlag(stand, "setCollidable", false);
        if (owner != null) {
            addScoreboardTag(stand, "bloodbound_archfiend_visual");
            addScoreboardTag(stand, "owner_" + owner.getUniqueId());
        }
        return stand;
    }

    private ItemStack archfiendItem(String modelId) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&4Bloodbound Model: &f" + modelId));
            meta.setItemModel(new NamespacedKey("bloodbound", modelId));
            meta.setCustomModelData(910100);
            suppressGlint(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Entity spawnPlayerClone(Location location, String playerName) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCustomName(Text.color("&6Hall of Fame &f" + playerName));
        stand.setCustomNameVisible(true);
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(playerHead(playerName));
            stand.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            stand.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            stand.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        }
        addScoreboardTag(stand, "bloodbound_player_clone");
        return stand;
    }

    private ItemStack playerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            try {
                OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(playerName);
                if (offline != null) {
                    meta.getClass().getMethod("setOwningPlayer", OfflinePlayer.class).invoke(meta, offline);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            meta.setDisplayName(Text.color("&6" + playerName));
            head.setItemMeta(meta);
        }
        return head;
    }

    private void suppressGlint(ItemMeta meta) {
        try {
            meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(meta, Boolean.FALSE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
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
        animationPulses.remove(link.visualId());
        removeSolidFootprint(link.visualId());
    }

    private void defaults() {
        boolean changed = false;
        changed |= setDefault("archfiend.offset_y", -0.25D);
        changed |= setDefault("archfiend.tick_ticks", 5);
        changed |= setDefault("archfiend.crimson_particles", true);
        changed |= setDefault("visual.marker", false);
        changed |= setDefault("solid.footprint_radius", 1);
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
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(ARCHFIEND_SOURCE)) {
                if (input == null) {
                    throw new IOException("resource not found");
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
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
            return new ModelReport(name, elements, textures, resolution, animationNames(text));
        } catch (IOException exception) {
            return new ModelReport("missing", 0, 0, "unknown", List.of());
        }
    }

    private List<String> animationNames(String text) {
        int index = text.indexOf("\"animations\"");
        if (index < 0) {
            return List.of();
        }
        String tail = text.substring(index);
        Matcher matcher = ANIMATION_NAME_PATTERN.matcher(tail);
        List<String> names = new ArrayList<>();
        while (matcher.find() && names.size() < 20) {
            String value = matcher.group(1);
            if (!value.isBlank() && !names.contains(value)) {
                names.add(value);
            }
        }
        return names;
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

    private static final class AnimationPulse {
        private final String name;
        private final long untilMillis;
        private int ticks;

        private AnimationPulse(String name, long untilMillis) {
            this.name = name;
            this.untilMillis = untilMillis;
        }
    }

    private String safeModelId(String input) {
        String value = input == null ? ARCHFIEND_MODEL : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "");
        return value.isBlank() ? ARCHFIEND_MODEL : value;
    }

    private void createSolidFootprint(UUID visualId, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        int radius = Math.max(0, config.getInt("solid.footprint_radius", 1));
        List<Location> placed = new ArrayList<>();
        int y = location.getBlockY() - 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block block = location.getWorld().getBlockAt(location.getBlockX() + dx, y, location.getBlockZ() + dz);
                if (isAir(block.getType())) {
                    block.setType(Material.BARRIER, false);
                    placed.add(block.getLocation());
                }
            }
        }
        if (!placed.isEmpty()) {
            solidFootprints.put(visualId, placed);
        }
    }

    private void removeSolidFootprint(UUID visualId) {
        List<Location> locations = solidFootprints.remove(visualId);
        if (locations == null) {
            return;
        }
        for (Location location : locations) {
            if (location.getWorld() != null && location.getBlock().getType() == Material.BARRIER) {
                location.getBlock().setType(Material.AIR, false);
            }
        }
    }

    private boolean isAir(Material material) {
        return material != null && material.name().endsWith("AIR");
    }

    private record ModelReport(String name, int elements, int textures, String resolution, List<String> animations) {
        static ModelReport empty() {
            return new ModelReport("missing", 0, 0, "unknown", List.of());
        }

        String summary() {
            return name + " | elements=" + elements + " | textures=" + textures + " | " + resolution + " | animations=" + animations.size();
        }
    }
}
