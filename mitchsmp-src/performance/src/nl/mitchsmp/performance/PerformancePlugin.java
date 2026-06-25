package nl.mitchsmp.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Array;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PerformancePlugin extends JavaPlugin implements TabCompleter, Listener {
    private PropertiesFile config;
    private long lastSampleNanos;
    private long lastAlertMillis;
    private int warmupSamples;
    private double lastElapsedMillis = 1000.0D;
    private double lastTps = 20.0D;
    private double lastMemoryPercent;
    private long lastMemoryUsedBytes;
    private long lastMemoryMaxBytes = 1L;
    private int lastEntityCount;
    private int lastDroppedItemCount;
    private int lastMobCount;
    private int consecutiveLagSamples;
    private final Map<String, Integer> chunkEntityCounts = new HashMap<>();
    private long profileCalls;
    private long profileTotalNanos;
    private long profileMaxNanos;

    @Override
    public void onEnable() {
        config = new PropertiesFile(getDataFolder().toPath().resolve("performance.properties"));
        normalizeLaunchDefaults();
        Bukkit.getPluginManager().registerEvents(this, this);
        command("perf");
        Bukkit.getScheduler().runTaskTimer(this, this::sample, 40L, 20L);
        getLogger().info("MitchSMP-Performance enabled.");
    }

    private void normalizeLaunchDefaults() {
        boolean changed = false;
        if (setting("lag_ms", 1500.0D) < 1500.0D) {
            config.set("lag_ms", 1500.0D);
            changed = true;
        }
        if (setting("alert_cooldown_seconds", 180.0D) < 180.0D) {
            config.set("alert_cooldown_seconds", 180.0D);
            changed = true;
        }
        if (changed) {
            config.save();
            getLogger().info("Performance launch defaults normalized: lag_ms>=1500, alert_cooldown_seconds>=180.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canView(sender)) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("detail") || args[0].equalsIgnoreCase("summary")) {
            showStatus(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("profiler")) {
            showProfiler(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("config")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.performance.admin")) {
                Text.msg(sender, "&cYou do not have permission.");
                return true;
            }
            config(sender, args);
            return true;
        }
        Text.msg(sender, "&cUsage: /perf status, /perf profiler or /perf config <key> <value>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "status", "detail", "summary", "profiler", "config");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[1], "lag_ms", "memory_percent", "entity_count", "entity_per_chunk", "enforce_entity_per_chunk", "alert_cooldown_seconds");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[2], "250", "500", "750", "85", "90", "5000", "10000", "60", "120");
        }
        return List.of();
    }

    private void sample() {
        long profileStart = System.nanoTime();
        long now = System.nanoTime();
        if (lastSampleNanos == 0L) {
            lastSampleNanos = now;
            return;
        }
        lastElapsedMillis = (now - lastSampleNanos) / 1_000_000.0D;
        lastSampleNanos = now;
        lastTps = clamp(0.0D, 20.0D, 20_000.0D / Math.max(1.0D, lastElapsedMillis));

        Runtime runtime = Runtime.getRuntime();
        long max = Math.max(1L, runtime.maxMemory());
        long used = runtime.totalMemory() - runtime.freeMemory();
        lastMemoryUsedBytes = used;
        lastMemoryMaxBytes = max;
        lastMemoryPercent = used * 100.0D / max;

        int entities = 0;
        int droppedItems = 0;
        int mobs = 0;
        Map<String, Integer> currentChunkCounts = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                entities++;
                currentChunkCounts.merge(chunkKey(entity), 1, Integer::sum);
                if (isDroppedItem(entity)) {
                    droppedItems++;
                } else if (!(entity instanceof Player)) {
                    mobs++;
                }
            }
        }
        lastEntityCount = entities;
        lastDroppedItemCount = droppedItems;
        lastMobCount = mobs;
        chunkEntityCounts.clear();
        chunkEntityCounts.putAll(currentChunkCounts);

        long duration = System.nanoTime() - profileStart;
        profileCalls++;
        profileTotalNanos += duration;
        profileMaxNanos = Math.max(profileMaxNanos, duration);

        if (warmupSamples++ < 5) {
            return;
        }

        boolean rawLag = lastElapsedMillis - 1000.0D >= setting("lag_ms", 1500.0D) || lastTps < 16.5D;
        consecutiveLagSamples = rawLag ? consecutiveLagSamples + 1 : 0;
        boolean lag = consecutiveLagSamples >= 3;
        boolean memory = lastMemoryPercent >= setting("memory_percent", 88.0D);
        boolean entity = lastEntityCount >= (int) setting("entity_count", 7000.0D);
        if (lag || memory || entity) {
            alert(lag, memory, entity);
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player || isFallingBlock(entity) || isNamedEntity(entity) || setting("enforce_entity_per_chunk", 1.0D) < 0.5D) {
            return;
        }
        int limit = Math.max(20, (int) setting("entity_per_chunk", 120.0D));
        String key = chunkKey(entity);
        int count = chunkEntityCounts.getOrDefault(key, 0);
        if (count >= limit) {
            event.setCancelled(true);
            return;
        }
        chunkEntityCounts.put(key, count + 1);
    }

    private boolean isFallingBlock(Entity entity) {
        return entity != null && "FALLING_BLOCK".equals(entity.getType().name());
    }

    private void alert(boolean lag, boolean memory, boolean entity) {
        long now = System.currentTimeMillis();
        long cooldown = (long) setting("alert_cooldown_seconds", 180.0D) * 1000L;
        if (now - lastAlertMillis < cooldown) {
            return;
        }
        lastAlertMillis = now;
        String reason = (lag ? "tick-delay " : "") + (memory ? "memory " : "") + (entity ? "entities" : "");
        String message = Text.color("&c[Performance] &7" + reason.trim()
            + " &8| &fTPS " + format(lastTps)
            + " &8| &fTick " + format(lastElapsedMillis) + "ms"
            + " &8| &fMem " + format(lastMemoryPercent) + "%"
            + " &8| &fEntities " + lastEntityCount);
        boolean sent = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canReceiveAlerts(player)) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.65F);
                sent = true;
            }
        }
        if (!sent) {
            getLogger().warning(Text.stripColorCodes(message));
        }
    }

    private void showStatus(CommandSender sender) {
        double lagLimit = setting("lag_ms", 1500.0D);
        double memoryLimit = setting("memory_percent", 88.0D);
        int entityLimit = (int) setting("entity_count", 7000.0D);
        boolean tickBad = lastElapsedMillis - 1000.0D >= lagLimit || lastTps < 16.5D;
        boolean tickWarn = !tickBad && (lastElapsedMillis - 1000.0D >= Math.max(250.0D, lagLimit * 0.35D) || lastTps < 18.5D);
        boolean memoryBad = lastMemoryPercent >= memoryLimit;
        boolean memoryWarn = !memoryBad && lastMemoryPercent >= Math.max(60.0D, memoryLimit - 10.0D);
        boolean entityBad = lastEntityCount >= entityLimit;
        boolean entityWarn = !entityBad && lastEntityCount >= Math.max(500.0D, entityLimit * 0.75D);
        List<WorldReport> worlds = worldReports();
        int loadedChunks = worlds.stream().mapToInt(report -> Math.max(0, report.loadedChunks)).sum();
        boolean chunkWarn = loadedChunks >= 900;
        boolean itemWarn = lastDroppedItemCount >= 500;

        Text.msg(sender, "&aPerformance overview:");
        Text.msg(sender, "&7Summary: " + summary(tickBad, tickWarn, memoryBad, memoryWarn, entityBad, entityWarn, chunkWarn, itemWarn));
        Text.msg(sender, verdict(tickBad, tickWarn) + " &7TPS/tick: &f" + format(lastTps) + " TPS &8| &f" + format(lastElapsedMillis) + "ms &7per seconde-sample");
        Text.msg(sender, verdict(memoryBad, memoryWarn) + " &7Memory: &f" + mb(lastMemoryUsedBytes) + "/" + mb(lastMemoryMaxBytes) + "MB &8(&f" + format(lastMemoryPercent) + "%&8)");
        Text.msg(sender, verdict(entityBad, entityWarn) + " &7Entities: &f" + lastEntityCount + " &8| &7mobs/non-player &f" + lastMobCount + " &8| &7drops &f" + lastDroppedItemCount);
        Text.msg(sender, verdict(false, chunkWarn) + " &7Loaded chunks: &f" + (loadedChunks <= 0 ? "onbekend" : String.valueOf(loadedChunks)));
        Text.msg(sender, "&7Limits: lag &f+" + format(lagLimit) + "ms for 3 samples&7, mem &f" + format(memoryLimit) + "%&7, entities &f" + entityLimit + "&7, alerts every &f" + (int) setting("alert_cooldown_seconds", 180.0D) + "s&7.");
        int hottestChunk = chunkEntityCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Text.msg(sender, "&7Chunk cap: &f" + (int) setting("entity_per_chunk", 120.0D) + " &8| &7enforced: &f" + (setting("enforce_entity_per_chunk", 1.0D) >= 0.5D) + " &8| &7hottest chunk: &f" + hottestChunk);
        Text.msg(sender, "&7Worlds:");
        for (WorldReport report : worlds) {
            Text.msg(sender, "&8- &f" + report.name + " &7P:&f" + report.players + " &7Chunks:&f" + (report.loadedChunks < 0 ? "?" : report.loadedChunks) + " &7Ent:&f" + report.entities + " &7Drops:&f" + report.droppedItems + " &7Mobs:&f" + report.mobs);
        }
        Text.msg(sender, "&7Advice: " + advice(tickBad, memoryBad, entityBad, itemWarn, chunkWarn));
    }

    private void config(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.msg(sender, "&7Keys: &flag_ms, memory_percent, entity_count, entity_per_chunk, enforce_entity_per_chunk, alert_cooldown_seconds");
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("lag_ms", "memory_percent", "entity_count", "entity_per_chunk", "enforce_entity_per_chunk", "alert_cooldown_seconds").contains(key)) {
            Text.msg(sender, "&cUnknown key.");
            return;
        }
        try {
            double value = Math.max(1.0D, Double.parseDouble(args[2]));
            config.set("setting." + key, value);
            config.save();
            Text.msg(sender, "&aPerformance setting &f" + key + " &aset to &f" + format(value) + "&a.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue must be a number.");
        }
    }

    private void showProfiler(CommandSender sender) {
        double averageMs = profileCalls == 0L ? 0.0D : (profileTotalNanos / 1_000_000.0D) / profileCalls;
        double maxMs = profileMaxNanos / 1_000_000.0D;
        Text.msg(sender, "&aBloodbound performance profiler:");
        Text.msg(sender, "&7Plugin/task: &fMitchSMP-Performance / entity-memory sample");
        Text.msg(sender, "&7Calls: &f" + profileCalls + " &8| &7avg: &f" + format(averageMs) + "ms &8| &7max: &f" + format(maxMs) + "ms");
        Text.msg(sender, "&7Estimated tick impact: " + (maxMs >= 50.0D ? "&cHIGH" : averageMs >= 10.0D ? "&eMEDIUM" : "&aLOW"));
        List<PluginLoad> loads = crossPluginLoads();
        Text.msg(sender, "&7Cross-plugin registrations (top 12):");
        for (PluginLoad load : loads.stream().limit(12).toList()) {
            Text.msg(sender, "&8- &f" + load.name + " &7listeners:&f" + load.listeners + " &7pending tasks:&f" + load.tasks);
        }
        Text.msg(sender, "&8Registration counts locate broad plugins; Paper timings are still required for per-listener execution time.");
    }

    private List<PluginLoad> crossPluginLoads() {
        Map<String, Integer> tasks = new HashMap<>();
        Map<String, Integer> listeners = new HashMap<>();
        try {
            Object pending = Bukkit.getScheduler().getClass().getMethod("getPendingTasks").invoke(Bukkit.getScheduler());
            if (pending instanceof Iterable<?> iterable) {
                for (Object task : iterable) {
                    Object owner = task.getClass().getMethod("getOwner").invoke(task);
                    String name = String.valueOf(owner.getClass().getMethod("getName").invoke(owner));
                    tasks.merge(name, 1, Integer::sum);
                }
            }
            Object pluginArray = Bukkit.getPluginManager().getClass().getMethod("getPlugins").invoke(Bukkit.getPluginManager());
            Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
            Class<?> handlerList = Class.forName("org.bukkit.event.HandlerList");
            for (int index = 0; index < Array.getLength(pluginArray); index++) {
                Object plugin = Array.get(pluginArray, index);
                String name = String.valueOf(plugin.getClass().getMethod("getName").invoke(plugin));
                Object registered = handlerList.getMethod("getRegisteredListeners", pluginClass).invoke(null, plugin);
                listeners.put(name, Array.getLength(registered));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Cross-plugin registration profile unavailable: " + exception.getMessage());
        }
        Set<String> names = new java.util.HashSet<>(tasks.keySet());
        names.addAll(listeners.keySet());
        return names.stream()
            .map(name -> new PluginLoad(name, listeners.getOrDefault(name, 0), tasks.getOrDefault(name, 0)))
            .sorted(java.util.Comparator.comparingInt((PluginLoad load) -> load.listeners + load.tasks).reversed())
            .toList();
    }

    private String chunkKey(Entity entity) {
        if (entity == null || entity.getWorld() == null || entity.getLocation() == null) {
            return "unknown";
        }
        return entity.getWorld().getName() + ":" + (entity.getLocation().getBlockX() >> 4) + ":" + (entity.getLocation().getBlockZ() >> 4);
    }

    private boolean isNamedEntity(Entity entity) {
        try {
            Object value = entity.getClass().getMethod("getCustomName").invoke(entity);
            return value != null && !String.valueOf(value).isBlank();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private boolean canView(CommandSender sender) {
        return MitchSMP.permissions().has(sender, "mitchsmp.performance.alerts")
            || MitchSMP.permissions().has(sender, "mitchsmp.performance.admin");
    }

    private record PluginLoad(String name, int listeners, int tasks) {
    }

    private boolean canReceiveAlerts(Player player) {
        return player != null && MitchSMP.permissions().has(player, "mitchsmp.performance.alerts");
    }

    private List<WorldReport> worldReports() {
        List<WorldReport> reports = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            int entities = 0;
            int droppedItems = 0;
            int mobs = 0;
            for (Entity entity : world.getEntities()) {
                entities++;
                if (isDroppedItem(entity)) {
                    droppedItems++;
                } else if (!(entity instanceof Player)) {
                    mobs++;
                }
            }
            reports.add(new WorldReport(world.getName(), onlinePlayers(world), loadedChunks(world), entities, droppedItems, mobs));
        }
        reports.sort((left, right) -> Integer.compare(right.entities, left.entities));
        return reports;
    }

    private int onlinePlayers(World world) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != null && world != null && player.getWorld().getName().equals(world.getName())) {
                count++;
            }
        }
        return count;
    }

    private int loadedChunks(World world) {
        try {
            Object result = world.getClass().getMethod("getLoadedChunks").invoke(world);
            if (result instanceof Object[] chunks) {
                return chunks.length;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return -1;
    }

    private boolean isDroppedItem(Entity entity) {
        if (entity instanceof Item) {
            return true;
        }
        return entity != null && entity.getType() != null && entity.getType().name().toLowerCase(Locale.ROOT).contains("item");
    }

    private String summary(boolean tickBad, boolean tickWarn, boolean memoryBad, boolean memoryWarn, boolean entityBad, boolean entityWarn, boolean chunkWarn, boolean itemWarn) {
        List<String> bad = new ArrayList<>();
        if (tickBad) {
            bad.add("tick lag");
        } else if (tickWarn) {
            bad.add("lichte TPS dip");
        }
        if (memoryBad) {
            bad.add("memory hoog");
        } else if (memoryWarn) {
            bad.add("memory stijgt");
        }
        if (entityBad) {
            bad.add("te veel entities");
        } else if (entityWarn) {
            bad.add("entitydruk stijgt");
        }
        if (chunkWarn) {
            bad.add("veel loaded chunks");
        }
        if (itemWarn) {
            bad.add("veel drops");
        }
        if (bad.isEmpty()) {
            return Text.color("&aHealthy. No immediate performance problems found.");
        }
        return Text.color("&eAandacht nodig: &f" + String.join("&7, &f", bad) + "&7.");
    }

    private String advice(boolean tickBad, boolean memoryBad, boolean entityBad, boolean itemWarn, boolean chunkWarn) {
        List<String> tips = new ArrayList<>();
        if (itemWarn) {
            tips.add("/lagclear is waarschijnlijk nuttig");
        }
        if (entityBad) {
            tips.add("check farms, mob caps en event mobs");
        }
        if (chunkWarn) {
            tips.add("check view-distance, players far apart and chunkloaders");
        }
        if (memoryBad) {
            tips.add("plan restart of verhoog RAM als dit blijft stijgen");
        }
        if (tickBad && tips.isEmpty()) {
            tips.add("bekijk laatste console errors en actieve events/minigames");
        }
        if (tips.isEmpty()) {
            return Text.color("&aGeen actie nodig.");
        }
        return Text.color("&f" + String.join("&7; &f", tips) + "&7.");
    }

    private String verdict(boolean bad, boolean warn) {
        if (bad) {
            return Text.color("&cSLECHT");
        }
        if (warn) {
            return Text.color("&eWARN");
        }
        return Text.color("&aOK");
    }

    private long mb(long bytes) {
        return Math.max(0L, bytes / 1024L / 1024L);
    }

    private double setting(String key, double fallback) {
        return config.getDouble("setting." + key, fallback);
    }

    private double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static final class WorldReport {
        private final String name;
        private final int players;
        private final int loadedChunks;
        private final int entities;
        private final int droppedItems;
        private final int mobs;

        private WorldReport(String name, int players, int loadedChunks, int entities, int droppedItems, int mobs) {
            this.name = name;
            this.players = players;
            this.loadedChunks = loadedChunks;
            this.entities = entities;
            this.droppedItems = droppedItems;
            this.mobs = mobs;
        }
    }
}



