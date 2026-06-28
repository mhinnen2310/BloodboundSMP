package nl.mitchsmp.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import nl.mitchsmp.core.api.FeatureFlagService;
import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.PermissionService;
import nl.mitchsmp.core.api.RankService;
import nl.mitchsmp.core.api.ServerRuntime;
import nl.mitchsmp.core.storage.BoundedRecordStore;
import nl.mitchsmp.core.storage.KeyValueStore;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

public final class MitchSMPCore extends JavaPlugin implements Listener, TabCompleter {
    private static final List<String> EXPECTED_PLUGINS = List.of(
        "MitchSMP-Lifesteal", "MitchSMP-CorruptedHearts", "MitchSMP-Permissions", "MitchSMP-CombatTag",
        "MitchSMP-Recovery", "MitchSMP-TPA", "MitchSMP-Homes", "MitchSMP-Economy", "MitchSMP-EconomyWatch", "MitchSMP-Bounties",
        "MitchSMP-AuctionHouse", "MitchSMP-HUD", "MitchSMP-RTP", "MitchSMP-Essentials", "MitchSMP-Hub",
        "MitchSMP-Skyblock", "MitchSMP-Performance", "MitchSMP-Artifacts", "MitchSMP-Bosses", "MitchSMP-BedWars",
        "MitchSMP-TNTRun", "MitchSMP-Spleef", "MitchSMP-Cosmetics", "MitchSMP-Events", "MitchSMP-Progression",
        "MitchSMP-Gameplay", "MitchSMP-Skirmish", "MitchSMP-Skills", "MitchSMP-EndBoss", "MitchSMP-Chat", "MitchSMP-AntiCheat",
        "MitchSMP-Seasons", "MitchSMP-UpdateOrchestrator", "MitchSMP-CustomMobs", "MitchSMP-Safezones", "MitchSMP-Spawners"
    );
    private final Map<Class<?>, Object> services = new HashMap<>();
    private CoreRankService rankService;
    private CorePermissionService permissionService;
    private CoreFeatureFlagService featureFlagService;
    private CommandErrorTracker commandErrorTracker;
    private ServerRuntime runtime;
    private KeyValueStore systemState;
    private PropertiesFile motdConfig;
    private BoundedRecordStore qaLog;
    private final Map<UUID, QaSession> qaSessions = new HashMap<>();
    private final Set<UUID> autoQaRunning = new HashSet<>();
    private long lastErrorAlertAt;

    @Override
    public void onEnable() {
        MitchSMP.setCore(this);
        runtime = new ServerRuntime(this);

        Path dataRoot = getDataFolder().toPath();
        rankService = new CoreRankService(new PropertiesFile(dataRoot.resolve("players.properties")));
        permissionService = new CorePermissionService(rankService, new PropertiesFile(dataRoot.resolve("permissions.properties")));
        featureFlagService = new CoreFeatureFlagService(new PropertiesFile(dataRoot.resolve("features.properties")));
        motdConfig = new PropertiesFile(dataRoot.resolve("motd.properties"));
        ensureMotdDefaults();
        systemState = new KeyValueStore(dataRoot.resolve("system-state.db"));
        qaLog = new BoundedRecordStore(dataRoot.resolve("qa-runs.db"), 1000);
        commandErrorTracker = new CommandErrorTracker(new BoundedRecordStore(dataRoot.resolve("command-errors.db"), 250));

        registerService(RankService.class, rankService);
        registerService(PermissionService.class, permissionService);
        registerService(FeatureFlagService.class, featureFlagService);

        Bukkit.getPluginManager().registerEvents(this, this);
        command("mitchcore");
        command("features");
        command("errors");
        command("maintenance");
        command("qa");
        command("motd");

        Logger.getLogger("").addHandler(commandErrorTracker);

        for (Player player : Bukkit.getOnlinePlayers()) {
            permissionService.sync(player);
        }

        getLogger().info("Bloodbound Core " + getDescription().getVersion() + " enabled on Java " + Runtime.version().feature() + " with " + services.size() + " services.");
        getLogger().info("Storage backend: " + PropertiesFile.backendName() + (PropertiesFile.sqliteEnabled() ? " (Paper/Xerial JDBC detected)" : " (SQLite JDBC not visible; using .tmp/.bak protected files)"));
        getLogger().info("Feature flags: " + featureFlagService.summary());
        if (maintenanceEnabled()) {
            getLogger().warning("Maintenance mode is active. Only staff/QA players can join.");
        }
        Bukkit.getScheduler().runTaskLater(this, this::runStartupDiagnostics, 40L);
    }

    @Override
    public void onDisable() {
        if (permissionService != null) {
            permissionService.removeAllAttachments();
        }
        if (commandErrorTracker != null) {
            Logger.getLogger("").removeHandler(commandErrorTracker);
        }
        services.clear();
        MitchSMP.setCore(null);
    }

    public RankService ranks() {
        return rankService;
    }

    public PermissionService permissions() {
        return permissionService;
    }

    public FeatureFlagService features() {
        return featureFlagService;
    }

    public ServerRuntime runtime() {
        return runtime;
    }

    public <T> void registerService(Class<T> type, T service) {
        services.put(type, service);
        getLogger().info("Registered service " + type.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    public <T> T service(Class<T> type) {
        return (T) services.get(type);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        permissionService.remember(event.getPlayer());
        permissionService.sync(event.getPlayer());
        if (maintenanceEnabled() && !canBypassMaintenance(event.getPlayer())) {
            Bukkit.getScheduler().runTask(this, () -> kickForMaintenance(event.getPlayer()));
            return;
        }
        if (maintenanceEnabled() && canBypassMaintenance(event.getPlayer())) {
            Text.msg(event.getPlayer(), "&6Maintenance mode is active. Use &f/qa start smoke &6to begin guided testing.");
        }
        if (permissionService.has(event.getPlayer(), "mitchsmp.errors.view")) {
            Text.msg(event.getPlayer(), PropertiesFile.sqliteEnabled()
                ? "&8[&4Storage&8] &7SQLite backend active for Bloodbound data."
                : "&8[&4Storage&8] &eSQLite JDBC not visible; using crash-safe properties fallback.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        permissionService.removeAttachment(event.getPlayer());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 2) {
            return;
        }
        String root = message.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (maintenanceEnabled()
            && !canBypassMaintenance(event.getPlayer())
            && !Set.of("help", "discord", "rules", "qa", "maintenance").contains(root)) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cBloodbound is in maintenance mode. Please try again later.");
            return;
        }
        String feature = featureFlagService.featureForCommand(root);
        if (feature == null || featureFlagService.isEnabled(feature)) {
            return;
        }
        Player player = event.getPlayer();
        if (permissionService.has(player, "mitchsmp.features.override") && permissionService.isAdminMode(player)) {
            return;
        }
        event.setCancelled(true);
        Text.msg(player, "&cThis feature is temporarily disabled: &f" + feature + "&c. Your data was not changed.");
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        event.setMotd(Text.rawColor(renderMotd()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("features")) {
            return featureCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("errors")) {
            return errorsCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("maintenance")) {
            return maintenanceCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("qa")) {
            return qaCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("motd")) {
            return motdCommand(sender, args);
        }
        if (!command.getName().equalsIgnoreCase("mitchcore")) {
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!permissionService.has(sender, "mitchsmp.core.reload")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            rankService.reload();
            permissionService.reload();
            permissionService.syncAll();
            Text.msg(sender, "&aCore data opnieuw geladen.");
            return true;
        }

        Text.msg(sender, "&aMitchSMP-Core &7v" + getDescription().getVersion());
        Text.msg(sender, "&7Services: &f" + serviceNames());
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("features")) {
            if (!permissionService.has(sender, "mitchsmp.features.admin")) {
                return List.of();
            }
            if (args.length == 1) {
                return nl.mitchsmp.core.util.Tab.complete(args[0], "list", "enable", "disable");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
                return nl.mitchsmp.core.util.Tab.complete(args[1], featureFlagService.all().keySet());
            }
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("errors")) {
            if (!permissionService.has(sender, "mitchsmp.errors.view")) {
                return List.of();
            }
            return args.length == 1 ? nl.mitchsmp.core.util.Tab.complete(args[0], "recent", "clear") : List.of();
        }
        if (command.getName().equalsIgnoreCase("maintenance")) {
            if (!permissionService.has(sender, "mitchsmp.maintenance.admin")) {
                return List.of();
            }
            return args.length == 1 ? nl.mitchsmp.core.util.Tab.complete(args[0], "on", "off", "status") : List.of();
        }
        if (command.getName().equalsIgnoreCase("qa")) {
            if (!permissionService.has(sender, "mitchsmp.qa.run")) {
                return List.of();
            }
            if (args.length == 1) {
                return nl.mitchsmp.core.util.Tab.complete(args[0], "start", "auto", "next", "pass", "warn", "fail", "status", "stop", "recent");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
                return nl.mitchsmp.core.util.Tab.complete(args[1], "smoke");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("auto")) {
                return nl.mitchsmp.core.util.Tab.complete(args[1], "smoke");
            }
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("motd")) {
            if (!permissionService.has(sender, "mitchsmp.core.motd")) {
                return List.of();
            }
            if (args.length == 1) {
                return nl.mitchsmp.core.util.Tab.complete(args[0], "show", "set", "frames", "reload");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return nl.mitchsmp.core.util.Tab.complete(args[1], "1", "2");
            }
            return List.of();
        }
        if (args.length == 1) {
            return nl.mitchsmp.core.util.Tab.complete(args[0], "reload");
        }
        return java.util.List.of();
    }

    private void command(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private String serviceNames() {
        java.util.List<String> names = new ArrayList<>();
        for (Class<?> service : services.keySet()) {
            names.add(service.getSimpleName());
        }
        names.sort(String::compareToIgnoreCase);
        return String.join("&7, &f", names);
    }

    private void ensureMotdDefaults() {
        boolean changed = false;
        changed |= motdDefault("enabled", true);
        changed |= motdDefault("frame_interval_ms", 700);
        changed |= motdDefault("frames", "<,<<,<<<,<<");
        changed |= motdDefault("line1", "&4&lBloodboundSMP &8{frame} &6Steal Hearts. Build Legacy.");
        changed |= motdDefault("line2", "&8No Claims. No Mercy. &7| &cLifesteal &8| &6Hub &8| &bSkyblock &8| &fMinigames");
        changed |= motdDefault("help.1", "Use /motd show, /motd set 1 <text>, /motd set 2 <text>, /motd frames <a,b,c>.");
        changed |= motdDefault("help.2", "Placeholders: {frame}. Use & color codes.");
        if (changed) {
            motdConfig.save();
        }
    }

    private boolean motdDefault(String key, Object value) {
        if (motdConfig.contains(key)) {
            return false;
        }
        motdConfig.set(key, value);
        return true;
    }

    private String renderMotd() {
        if (motdConfig == null || !Boolean.parseBoolean(motdConfig.getString("enabled", "true"))) {
            return "&4&lBloodboundSMP &8| &6Steal Hearts. Build Legacy.\n&8No Claims. No Mercy.";
        }
        String frame = motdFrame();
        String line1 = motdConfig.getString("line1", "&4&lBloodboundSMP &8{frame} &6Steal Hearts. Build Legacy.");
        String line2 = motdConfig.getString("line2", "&8No Claims. No Mercy. &7| &cLifesteal &8| &6Hub &8| &bSkyblock &8| &fMinigames");
        return line1.replace("{frame}", frame) + "\n" + line2.replace("{frame}", frame);
    }

    private String motdFrame() {
        String raw = motdConfig.getString("frames", "<,<<,<<<,<<");
        String[] frames = java.util.Arrays.stream(raw.split(",", -1))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);
        if (frames.length == 0) {
            return "";
        }
        long interval = Math.max(100L, motdConfig.getLong("frame_interval_ms", 700L));
        return frames[(int) ((System.currentTimeMillis() / interval) % frames.length)];
    }

    private boolean motdCommand(CommandSender sender, String[] args) {
        if (!permissionService.has(sender, "mitchsmp.core.motd")) {
            Text.msg(sender, "&cYou do not have permission to edit the MOTD.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("show")) {
            Text.msg(sender, "&6Current Bloodbound MOTD:");
            Text.msg(sender, "&7Line 1: &f" + motdConfig.getString("line1", ""));
            Text.msg(sender, "&7Line 2: &f" + motdConfig.getString("line2", ""));
            Text.msg(sender, "&7Frames: &f" + motdConfig.getString("frames", ""));
            Text.msg(sender, "&7Preview:");
            for (String line : renderMotd().split("\\n", -1)) {
                Text.msg(sender, line);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            motdConfig.load();
            ensureMotdDefaults();
            Text.msg(sender, "&aMOTD config reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("frames")) {
            if (args.length < 2) {
                Text.msg(sender, "&cUsage: /motd frames <frame1,frame2,frame3>");
                return true;
            }
            motdConfig.set("frames", joinArgs(args, 1));
            motdConfig.save();
            Text.msg(sender, "&aMOTD animation frames updated.");
            return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3 || (!args[1].equals("1") && !args[1].equals("2"))) {
                Text.msg(sender, "&cUsage: /motd set <1|2> <text>");
                return true;
            }
            motdConfig.set("line" + args[1], joinArgs(args, 2));
            motdConfig.save();
            Text.msg(sender, "&aMOTD line " + args[1] + " updated.");
            return true;
        }
        Text.msg(sender, "&cUsage: /motd [show|reload|frames <frames>|set <1|2> <text>]");
        return true;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private boolean featureCommand(CommandSender sender, String[] args) {
        if (!permissionService.has(sender, "mitchsmp.features.admin")) {
            Text.msg(sender, "&cYou do not have permission to manage feature flags.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Text.msg(sender, "&6Bloodbound feature flags:");
            featureFlagService.all().forEach((name, enabled) ->
                Text.msg(sender, (enabled ? "&aON  " : "&cOFF ") + "&f" + name));
            Text.msg(sender, "&7Disabled commands fail safely before plugin execution. Adminmode users with override permission can test them.");
            return true;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            String feature = args[1].toLowerCase(Locale.ROOT);
            if (!featureFlagService.all().containsKey(feature)) {
                Text.msg(sender, "&cUnknown feature. Use &f/features list&c.");
                return true;
            }
            boolean enabled = args[0].equalsIgnoreCase("enable");
            featureFlagService.setEnabled(feature, enabled);
            Text.msg(sender, enabled ? "&aEnabled &f" + feature + "&a." : "&cDisabled &f" + feature + "&c.");
            getLogger().warning("Feature flag " + feature + " set to " + enabled + " by " + sender.getName());
            return true;
        }
        Text.msg(sender, "&cUsage: /features [list|enable <feature>|disable <feature>]");
        return true;
    }

    private boolean errorsCommand(CommandSender sender, String[] args) {
        if (!permissionService.has(sender, "mitchsmp.errors.view")) {
            Text.msg(sender, "&cYou do not have permission to view command errors.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            commandErrorTracker.clear();
            Text.msg(sender, "&aCommand error history cleared.");
            return true;
        }
        int limit = 10;
        if (args.length > 1) {
            try {
                limit = Math.max(1, Math.min(50, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        List<String> recent = commandErrorTracker.recent(limit);
        Text.msg(sender, "&6Recent command errors &7(" + recent.size() + "):");
        if (recent.isEmpty()) {
            Text.msg(sender, "&aNo captured command errors.");
        } else {
            recent.forEach(line -> Text.msg(sender, "&7- &f" + line));
        }
        return true;
    }

    private boolean maintenanceCommand(CommandSender sender, String[] args) {
        if (!permissionService.has(sender, "mitchsmp.maintenance.admin")) {
            Text.msg(sender, "&cYou do not have permission to manage maintenance mode.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            Text.msg(sender, maintenanceEnabled()
                ? "&6Maintenance is &aON&6. Normal players cannot join."
                : "&6Maintenance is &cOFF&6. The server is public.");
            return true;
        }
        if (args[0].equalsIgnoreCase("on")) {
            systemState.set("maintenance.enabled", true);
            systemState.set("maintenance.changed_by", sender.getName());
            systemState.set("maintenance.changed_at", System.currentTimeMillis());
            alertStaff("&6[Maintenance] &f" + sender.getName() + " &7enabled maintenance mode.");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!canBypassMaintenance(online)) {
                    kickForMaintenance(online);
                }
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("off")) {
            systemState.set("maintenance.enabled", false);
            systemState.set("maintenance.changed_by", sender.getName());
            systemState.set("maintenance.changed_at", System.currentTimeMillis());
            alertStaff("&6[Maintenance] &f" + sender.getName() + " &7disabled maintenance mode.");
            return true;
        }
        Text.msg(sender, "&cUsage: /maintenance <on|off|status>");
        return true;
    }

    private boolean qaCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cQA sessions are in-game only.");
            return true;
        }
        if (!permissionService.has(player, "mitchsmp.qa.run")) {
            Text.msg(player, "&cYou do not have permission to run guided QA.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            QaSession session = qaSessions.get(player.getUniqueId());
            if (session == null) {
                Text.msg(player, "&6No active QA session. Use &f/qa start smoke&6.");
            } else {
                showQaStep(player, session);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("smoke")) {
                Text.msg(player, "&cUsage: /qa start smoke");
                return true;
            }
            if (!maintenanceEnabled()) {
                Text.msg(player, "&cEnable maintenance first with &f/maintenance on&c. Guided launch QA must run while players are locked out.");
                return true;
            }
            QaSession session = new QaSession(System.currentTimeMillis(), smokeSteps());
            applyAutomaticSmokeChecks(player, session);
            qaSessions.put(player.getUniqueId(), session);
            qaLog.append("START", player.getName() + " started smoke QA");
            Text.msg(player, "&6Guided smoke QA started. Use &f/qa pass&6, &f/qa warn <note>&6, &f/qa fail <note>&6, or &f/qa next&6.");
            showQaStep(player, session);
            return true;
        }
        if (args[0].equalsIgnoreCase("auto")) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("smoke")) {
                Text.msg(player, "&cUsage: /qa auto smoke");
                return true;
            }
            startAutomaticSmokeQa(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("recent")) {
            Text.msg(player, "&6Recent QA records:");
            qaLog.recent(10).forEach(line -> Text.msg(player, "&7- &f" + line));
            return true;
        }
        QaSession session = qaSessions.get(player.getUniqueId());
        if (session == null) {
            Text.msg(player, "&cNo active QA session. Use &f/qa start smoke&c.");
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            qaLog.append("STOP", player.getName() + " stopped QA at step " + (session.index + 1));
            qaSessions.remove(player.getUniqueId());
            Text.msg(player, "&eQA session stopped.");
            return true;
        }
        if (args[0].equalsIgnoreCase("pass") || args[0].equalsIgnoreCase("warn") || args[0].equalsIgnoreCase("fail")) {
            String verdict = args[0].toUpperCase(Locale.ROOT);
            String note = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "";
            qaLog.append(verdict, player.getName() + " | " + session.current() + (note.isBlank() ? "" : " | " + note));
            if (args[0].equalsIgnoreCase("warn")) {
                session.warnings++;
            }
            if (args[0].equalsIgnoreCase("fail")) {
                session.failures++;
            }
            session.index++;
            if (session.index >= session.steps.size()) {
                String finalVerdict = session.failures > 0 ? "NOT_READY" : session.warnings > 0 ? "READY_WITH_WARNINGS" : "READY";
                qaLog.append("VERDICT", player.getName() + " | " + finalVerdict + " | warnings=" + session.warnings + " failures=" + session.failures);
                qaSessions.remove(player.getUniqueId());
                alertStaff("&6[QA] &f" + player.getName() + " &7finished smoke QA: &f" + finalVerdict);
                return true;
            }
            showQaStep(player, session);
            return true;
        }
        if (args[0].equalsIgnoreCase("next")) {
            session.index = Math.min(session.index + 1, session.steps.size() - 1);
            showQaStep(player, session);
            return true;
        }
        Text.msg(player, "&cUsage: /qa <start smoke|auto smoke|pass|warn <note>|fail <note>|next|status|stop|recent>");
        return true;
    }

    private void runStartupDiagnostics() {
        List<String> missing = new ArrayList<>();
        for (String pluginName : EXPECTED_PLUGINS) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin == null || !plugin.isEnabled()) {
                missing.add(pluginName);
            }
        }
        if (missing.isEmpty()) {
            getLogger().info("STARTUP CHECK PASS: all " + (EXPECTED_PLUGINS.size() + 1) + " Bloodbound plugins are enabled.");
        } else {
            getLogger().severe("STARTUP CHECK FAILED: missing/disabled plugins: " + String.join(", ", missing));
        }
    }

    private boolean maintenanceEnabled() {
        return systemState != null && systemState.getBoolean("maintenance.enabled", false);
    }

    private boolean canBypassMaintenance(Player player) {
        if (player == null) {
            return false;
        }
        MitchRank rank = rankService.getRank(player.getUniqueId());
        return player.isOp()
            || rank.staff()
            || permissionService.has(player, "mitchsmp.maintenance.bypass")
            || permissionService.has(player, "mitchsmp.qa.run");
    }

    private void kickForMaintenance(Player player) {
        if (player != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + player.getName() + " BloodboundSMP maintenance is active while staff run launch checks.");
        }
    }

    private void showQaStep(Player player, QaSession session) {
        Text.msg(player, "&6QA step &f" + (session.index + 1) + "&7/&f" + session.steps.size() + "&6:");
        Text.msg(player, "&f" + session.current());
        Text.msg(player, "&7Use &a/qa pass&7, &e/qa warn <note>&7, &c/qa fail <note>&7, or &f/qa next&7.");
    }

    private void startAutomaticSmokeQa(Player player) {
        if (!maintenanceEnabled()) {
            Text.msg(player, "&cEnable maintenance first with &f/maintenance on&c. Automated QA must run while players are locked out.");
            return;
        }
        if (!isSafeQaWorld(player)) {
            Text.msg(player, "&cAutomated QA only runs in a sandbox/test world. Move to &fmitchtest_*&c, &fbloodbound_test*&c, or &fsandbox*&c first.");
            return;
        }
        if (!autoQaRunning.add(player.getUniqueId())) {
            Text.msg(player, "&cAn automated QA run is already active for you.");
            return;
        }

        List<String> commands = automaticSmokeCommands(player);
        qaLog.append("AUTO_START", player.getName() + " started automated smoke QA in " + player.getWorld().getName() + " commands=" + commands.size());
        Text.msg(player, "&6Automated smoke QA started. &7Commands: &f" + commands.size() + "&7. The runner will close GUIs between checks.");
        alertStaff("&6[QA] &f" + player.getName() + " &7started automated smoke QA in &f" + player.getWorld().getName() + "&7.");

        UUID playerId = player.getUniqueId();
        final int[] index = {0};
        final int[] warnings = {0};
        final int[] failures = {0};
        final org.bukkit.scheduler.BukkitTask[] taskRef = new org.bukkit.scheduler.BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getPlayer(playerId) == null) {
                autoQaRunning.remove(playerId);
                qaLog.append("AUTO_ABORT", player.getName() + " went offline during automated smoke QA");
                taskRef[0].cancel();
                return;
            }
            if (!isSafeQaWorld(player)) {
                autoQaRunning.remove(playerId);
                failures[0]++;
                qaLog.append("AUTO_FAIL", player.getName() + " left the sandbox during automated smoke QA");
                Text.msg(player, "&cAutomated QA stopped because you left the sandbox world.");
                taskRef[0].cancel();
                return;
            }
            if (index[0] >= commands.size()) {
                autoQaRunning.remove(playerId);
                String verdict = failures[0] > 0 ? "NOT_READY" : warnings[0] > 0 ? "READY_WITH_WARNINGS" : "READY";
                qaLog.append("AUTO_VERDICT", player.getName() + " | " + verdict + " | warnings=" + warnings[0] + " failures=" + failures[0]);
                Text.msg(player, "&6Automated smoke QA finished: &f" + verdict + "&7. Warnings: &e" + warnings[0] + "&7, failures: &c" + failures[0] + "&7.");
                alertStaff("&6[QA] &f" + player.getName() + " &7finished automated smoke QA: &f" + verdict);
                taskRef[0].cancel();
                return;
            }

            String command = commands.get(index[0]++);
            try {
                boolean accepted = Bukkit.dispatchCommand(player, command);
                if (accepted) {
                    qaLog.append("AUTO_PASS", player.getName() + " | /" + command);
                } else {
                    warnings[0]++;
                    qaLog.append("AUTO_WARN", player.getName() + " | /" + command + " returned false");
                }
            } catch (Throwable throwable) {
                failures[0]++;
                String summary = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
                qaLog.append("AUTO_FAIL", player.getName() + " | /" + command + " | " + summary);
                Text.msg(player, "&c[QA auto] /" + command + " failed: &f" + summary);
            } finally {
                Bukkit.getScheduler().runTaskLater(this, player::closeInventory, 1L);
            }
        }, 1L, 4L);
    }

    private boolean isSafeQaWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return world.startsWith("mitchtest_")
            || world.startsWith("bloodbound_test")
            || world.startsWith("testworld")
            || world.startsWith("sandbox");
    }

    private List<String> automaticSmokeCommands(Player player) {
        LinkedHashSet<String> commands = new LinkedHashSet<>();
        commands.add("mitchcore");
        commands.add("features list");
        commands.add("errors recent 5");
        commands.add("maintenance status");
        commands.add("qa status");
        commands.add("qa recent");
        commands.add("menu");
        commands.add("commands");
        commands.add("help");
        commands.add("balance");
        commands.add("pay QaVirtualTarget 1");
        commands.add("bounty");
        commands.add("bounty QaVirtualTarget 1");
        commands.add("ah");
        commands.add("ah listings");
        commands.add("ah mine");
        commands.add("ah sell abc");
        commands.add("shop");
        commands.add("sell");
        commands.add("contracts");
        commands.add("orders");
        commands.add("collection");
        commands.add("clog");
        commands.add("legacy");
        commands.add("daily");
        commands.add("explorer");
        commands.add("goals");
        commands.add("rookie");
        commands.add("reports open");
        commands.add("staffprofile QaVirtualTarget");
        commands.add("skills");
        commands.add("mechanics");
        commands.add("abilities");
        commands.add("hud");
        commands.add("hud status");
        commands.add("homes");
        commands.add("tpa QaVirtualTarget");
        commands.add("tpdeny");
        commands.add("tpaccept");
        commands.add("perf");
        commands.add("performance");
        commands.add("lagclear");
        commands.add("custommob status");
        commands.add("custommob models");
        commands.add("custommob animations");
        commands.add("custommob uploadinfo");
        commands.add("custommob test");
        commands.add("custommob clear");
        commands.add("spawnmob zombie 1");
        commands.add("spawnmob miniboss 1");
        commands.add("killall");
        commands.add("fakeores cancel");
        commands.add("adminui");
        commands.add("model status");
        commands.add("updates status");
        commands.add("updates list");
        commands.add("updates history");
        commands.add("updates manifest");
        return new ArrayList<>(commands);
    }

    private void applyAutomaticSmokeChecks(Player player, QaSession session) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String pluginName : EXPECTED_PLUGINS) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin == null || !plugin.isEnabled()) {
                failures.add("Missing/disabled plugin: " + pluginName);
            }
        }
        if (MitchSMP.economy() == null) {
            failures.add("Economy service is not registered.");
        }
        if (MitchSMP.ranks() == null || MitchSMP.permissions() == null) {
            failures.add("Rank/permission services are not registered.");
        }
        if (!PropertiesFile.sqliteEnabled()) {
            warnings.add("SQLite backend is not active; properties fallback is in use.");
        }

        failures.forEach(failure -> {
            session.failures++;
            qaLog.append("AUTO_FAIL", player.getName() + " | " + failure);
            Text.msg(player, "&c[QA auto] " + failure);
        });
        warnings.forEach(warning -> {
            session.warnings++;
            qaLog.append("AUTO_WARN", player.getName() + " | " + warning);
            Text.msg(player, "&e[QA auto] " + warning);
        });
        if (failures.isEmpty() && warnings.isEmpty()) {
            qaLog.append("AUTO_PASS", player.getName() + " | launch preflight checks passed");
            Text.msg(player, "&a[QA auto] Launch preflight checks passed.");
        }
    }

    private List<String> smokeSteps() {
        return List.of(
            "AUTO checked: core plugin set, key services, and storage backend. Review auto warnings/failures above.",
            "Confirm feature flags UI: run /features list.",
            "Confirm error tracker: run /errors recent 5.",
            "Confirm economy read path: run /balance.",
            "Confirm invalid pay is rejected cleanly: run /pay NotAPlayer NaN, Infinity, and 1e309.",
            "Confirm Auction House opens: run /ah.",
            "Confirm invalid AH sell gives a friendly error: run /ah sell abc.",
            "Confirm player menu opens: run /menu.",
            "Confirm shop opens and does not expose OP gear: run /shop.",
            "Confirm contracts UI/countdowns: run /contracts.",
            "Confirm orders UI/countdowns: run /orders.",
            "Confirm skills UI and tooltips: run /skills.",
            "Confirm mechanics guide command: run /mechanics.",
            "Confirm adminmode permission behavior: toggle /adminmode on and off.",
            "Confirm staff-only command protection outside adminmode: test /freeze on a test account.",
            "Confirm recovery snapshot capture works on a test player.",
            "Confirm report submit/staff review path works.",
            "Confirm skirmish join/leave restores inventory.",
            "Confirm minigame leave/rejoin recovery does not strand players.",
            "Manual combat tag check: attack a test player and verify home/tpa are blocked."
        );
    }

    private void alertStaff(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permissionService.has(player, "mitchsmp.errors.view") || permissionService.has(player, "mitchsmp.qa.run")) {
                Text.msg(player, message);
            }
        }
    }

    private void alertStaffError(String summary) {
        long now = System.currentTimeMillis();
        if (now - lastErrorAlertAt < 5000L) {
            return;
        }
        lastErrorAlertAt = now;
        Bukkit.getScheduler().runTask(this, () -> alertStaff("&c[ErrorTracker] &f" + summary + " &7Use &f/errors recent&7."));
    }

    private static final class QaSession {
        private final long startedAt;
        private final List<String> steps;
        private int index;
        private int warnings;
        private int failures;

        private QaSession(long startedAt, List<String> steps) {
            this.startedAt = startedAt;
            this.steps = steps;
        }

        private String current() {
            return steps.get(Math.max(0, Math.min(index, steps.size() - 1)));
        }
    }

    private static final class CoreFeatureFlagService implements FeatureFlagService {
        private static final List<String> DEFAULT_FEATURES = List.of(
            "auctionhouse", "quicksell", "pay", "bounties", "abilities", "endboss", "events", "bosses",
            "bedwars", "tntrun", "spleef", "skirmish", "skyblock", "homes", "tpa", "rtp", "shop", "contracts",
            "orders", "collections"
        );
        private static final Map<String, String> COMMAND_FEATURES = Map.ofEntries(
            Map.entry("ah", "auctionhouse"), Map.entry("auctionhouse", "auctionhouse"),
            Map.entry("sell", "quicksell"), Map.entry("quicksell", "quicksell"), Map.entry("qs", "quicksell"), Map.entry("sellquick", "quicksell"),
            Map.entry("pay", "pay"), Map.entry("bounty", "bounties"), Map.entry("bounties", "bounties"),
            Map.entry("abilities", "abilities"), Map.entry("ability", "abilities"), Map.entry("enchants", "abilities"),
            Map.entry("endboss", "endboss"), Map.entry("hellboss", "endboss"), Map.entry("ritualboss", "endboss"),
            Map.entry("event", "events"), Map.entry("boss", "bosses"), Map.entry("bosses", "bosses"),
            Map.entry("bw", "bedwars"), Map.entry("bedwars", "bedwars"), Map.entry("tntrun", "tntrun"), Map.entry("tr", "tntrun"),
            Map.entry("spleef", "spleef"), Map.entry("sf", "spleef"), Map.entry("skyblock", "skyblock"), Map.entry("sb", "skyblock"), Map.entry("island", "skyblock"),
            Map.entry("skirmish", "skirmish"), Map.entry("skirm", "skirmish"),
            Map.entry("home", "homes"), Map.entry("homes", "homes"), Map.entry("sethome", "homes"), Map.entry("delhome", "homes"), Map.entry("deletehome", "homes"),
            Map.entry("tpa", "tpa"), Map.entry("tpaccept", "tpa"), Map.entry("tpdeny", "tpa"), Map.entry("rtp", "rtp"), Map.entry("wild", "rtp"),
            Map.entry("shop", "shop"), Map.entry("contracts", "contracts"), Map.entry("orders", "orders"), Map.entry("resourceorders", "orders"),
            Map.entry("collection", "collections"), Map.entry("clog", "collections")
        );
        private final PropertiesFile file;

        CoreFeatureFlagService(PropertiesFile file) {
            this.file = file;
            boolean changed = false;
            for (String feature : DEFAULT_FEATURES) {
                if (!file.contains("feature." + feature)) {
                    file.set("feature." + feature, true);
                    changed = true;
                }
            }
            if (changed) {
                file.save();
            }
        }

        @Override
        public boolean isEnabled(String feature) {
            return Boolean.parseBoolean(file.getString("feature." + feature.toLowerCase(Locale.ROOT), "false"));
        }

        @Override
        public void setEnabled(String feature, boolean enabled) {
            String normalized = feature.toLowerCase(Locale.ROOT);
            if (!DEFAULT_FEATURES.contains(normalized)) {
                throw new IllegalArgumentException("Unknown feature: " + feature);
            }
            file.set("feature." + normalized, enabled);
            file.save();
        }

        @Override
        public Map<String, Boolean> all() {
            Map<String, Boolean> result = new LinkedHashMap<>();
            DEFAULT_FEATURES.forEach(feature -> result.put(feature, isEnabled(feature)));
            return Map.copyOf(result);
        }

        @Override
        public String featureForCommand(String commandRoot) {
            return COMMAND_FEATURES.get(commandRoot.toLowerCase(Locale.ROOT));
        }

        String summary() {
            List<String> disabled = all().entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).sorted().toList();
            return disabled.isEmpty() ? "all enabled" : "disabled=" + String.join(",", disabled);
        }
    }

    private final class CommandErrorTracker extends Handler {
        private final BoundedRecordStore store;

        CommandErrorTracker(BoundedRecordStore store) {
            this.store = store;
        }

        @Override
        public synchronized void publish(LogRecord record) {
            if (record == null || record.getLevel().intValue() < Level.SEVERE.intValue()) {
                return;
            }
            String message = String.valueOf(record.getMessage());
            Throwable thrown = record.getThrown();
            String throwableName = thrown == null ? "" : thrown.getClass().getName();
            String haystack = (message + " " + throwableName).toLowerCase(Locale.ROOT);
            if (!haystack.contains("command") && !haystack.contains("brigadier")) {
                return;
            }
            String cause = thrown == null ? "" : deepest(thrown).getClass().getSimpleName() + ": " + safe(deepest(thrown).getMessage());
            String value = safe(message) + (cause.isBlank() ? "" : " | " + cause);
            store.append("ERROR", value);
            alertStaffError(value.length() > 120 ? value.substring(0, 120) + "..." : value);
        }

        List<String> recent(int limit) {
            return store.recent(limit);
        }

        void clear() {
            store.clear();
        }

        private static Throwable deepest(Throwable throwable) {
            Throwable result = throwable;
            while (result.getCause() != null && result.getCause() != result) {
                result = result.getCause();
            }
            return result;
        }

        private static String safe(String input) {
            return input == null ? "" : input.replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim();
        }

        @Override public void flush() { }
        @Override public void close() { }
    }

    private static final class CoreRankService implements RankService {
        private final PropertiesFile players;

        CoreRankService(PropertiesFile players) {
            this.players = players;
        }

        void reload() {
            players.load();
        }

        @Override
        public MitchRank getRank(UUID playerId) {
            return MitchRank.parse(players.getString(playerId.toString() + ".rank", "DEFAULT"));
        }

        @Override
        public void setRank(UUID playerId, MitchRank rank) {
            players.set(playerId.toString() + ".rank", rank.name());
            players.save();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && MitchSMP.core().permissions() != null) {
                MitchSMP.core().permissions().sync(player);
            }
        }

        @Override
        public String getPrefix(UUID playerId) {
            return getRank(playerId).prefix();
        }

        @Override
        public int getHomeLimit(UUID playerId) {
            return getRank(playerId).homeLimit();
        }

        @Override
        public Map<UUID, MitchRank> allRanks() {
            Map<UUID, MitchRank> ranks = new LinkedHashMap<>();
            for (String key : players.keys()) {
                if (!key.endsWith(".rank")) {
                    continue;
                }
                String id = key.substring(0, key.length() - ".rank".length());
                try {
                    ranks.put(UUID.fromString(id), MitchRank.parse(players.getString(key, "DEFAULT")));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return ranks;
        }
    }

    private static final class CorePermissionService implements PermissionService {
        private final CoreRankService ranks;
        private final PropertiesFile permissionsFile;
        private final Map<MitchRank, Set<String>> permissions = new EnumMap<>(MitchRank.class);
        private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

        CorePermissionService(CoreRankService ranks, PropertiesFile permissionsFile) {
            this.ranks = ranks;
            this.permissionsFile = permissionsFile;
            loadDefaults();
            loadCustom();
        }

        void reload() {
            permissionsFile.load();
            loadDefaults();
            loadCustom();
        }

        void remember(Player player) {
            permissionsFile.set("known." + player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
            permissionsFile.save();
        }

        @Override
        public UUID findKnownPlayer(String name) {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                return online.getUniqueId();
            }

            String raw = permissionsFile.getString("known." + name.toLowerCase(Locale.ROOT), "");
            if (!raw.isBlank()) {
                try {
                    return UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {
                }
            }

            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
            return offline == null ? null : offline.getUniqueId();
        }

        @Override
        public boolean has(CommandSender sender, String permission) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            String requested = permission.toLowerCase(Locale.ROOT);
            MitchRank rank = ranks.getRank(player.getUniqueId());
            if (isSandboxAdmin(player, rank)) {
                return true;
            }
            if (requested.equals("mitchsmp.staffmode")) {
                return player.isOp() || rank.staff();
            }
            if (rank.staff() && isPassiveStaffPermission(requested)) {
                return true;
            }
            if (player.isOp() && isAdminMode(player)) {
                return true;
            }
            MitchRank effectiveRank = (rank.staff() || player.isOp()) && !isAdminMode(player) ? MitchRank.LEGEND : rank;
            for (String owned : permissionsFor(effectiveRank)) {
                if (matches(owned, requested)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isAdminMode(Player player) {
            return player != null && permissionsFile.getString("staffmode." + player.getUniqueId(), "false").equalsIgnoreCase("true");
        }

        @Override
        public boolean isAdminModeOverride(Player player) {
            return player != null && isAdminMode(player) && permissionsFile.getString("staffmode.override." + player.getUniqueId(), "false").equalsIgnoreCase("true");
        }

        @Override
        public boolean isAdminRestricted(Player player) {
            if (player != null && isSandboxAdmin(player, ranks.getRank(player.getUniqueId()))) {
                return false;
            }
            return isAdminMode(player) && !isAdminModeOverride(player);
        }

        @Override
        public void setAdminMode(Player player, boolean active) {
            permissionsFile.set("staffmode." + player.getUniqueId(), active);
            if (!active) {
                permissionsFile.set("staffmode.override." + player.getUniqueId(), null);
            }
            permissionsFile.save();
            sync(player);
        }

        @Override
        public void setAdminModeOverride(Player player, boolean active) {
            if (player == null) {
                return;
            }
            permissionsFile.set("staffmode.override." + player.getUniqueId(), active && isAdminMode(player));
            permissionsFile.save();
            sync(player);
        }

        @Override
        public Set<String> permissionsFor(MitchRank rank) {
            Set<String> result = new LinkedHashSet<>();
            for (MitchRank current = rank; current != null; current = current.parent()) {
                result.addAll(permissions.getOrDefault(current, Set.of()));
            }
            return result;
        }

        @Override
        public void addPermission(MitchRank rank, String permission) {
            permissions.computeIfAbsent(rank, ignored -> new LinkedHashSet<>()).add(permission);
            permissionsFile.set("rank." + rank.name() + ".add." + safe(permission), permission);
            permissionsFile.save();
            syncAll();
        }

        @Override
        public void removePermission(MitchRank rank, String permission) {
            permissions.computeIfAbsent(rank, ignored -> new LinkedHashSet<>()).remove(permission);
            permissionsFile.set("rank." + rank.name() + ".add." + safe(permission), null);
            permissionsFile.save();
            syncAll();
        }

        @Override
        public void sync(Player player) {
            removeAttachment(player);
            PermissionAttachment attachment = player.addAttachment(MitchSMP.core());
            MitchRank rank = ranks.getRank(player.getUniqueId());
            MitchRank effectiveRank = (rank.staff() || player.isOp()) && !isAdminMode(player) ? MitchRank.LEGEND : rank;
            for (String permission : permissionsFor(effectiveRank)) {
                attachment.setPermission(permission, true);
            }
            attachments.put(player.getUniqueId(), attachment);
            String prefix = ranks.getPrefix(player.getUniqueId());
            player.setPlayerListName(Text.color(prefix) + player.getName());
        }

        @Override
        public void syncAll() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sync(player);
            }
        }

        void removeAttachment(Player player) {
            PermissionAttachment old = attachments.remove(player.getUniqueId());
            if (old != null) {
                try {
                    player.removeAttachment(old);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        void removeAllAttachments() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeAttachment(player);
            }
        }

        private void loadDefaults() {
            permissions.clear();
            for (MitchRank rank : MitchRank.values()) {
                permissions.put(rank, new LinkedHashSet<>());
            }

            addDefault(MitchRank.DEFAULT,
                "mitchsmp.tpa.use",
                "mitchsmp.combat.view",
                "mitchsmp.lifesteal.view",
                "mitchsmp.homes.use",
                "mitchsmp.economy.use",
                "mitchsmp.economy.pay",
                "mitchsmp.auctionhouse.use",
                "mitchsmp.bounties.view",
                "mitchsmp.chat.msg",
                "mitchsmp.season.view",
                "mitchsmp.cosmetics.basic",
                "mitchsmp.hud.use",
                "mitchsmp.rtp.use",
                "mitchsmp.progression.use",
                "mitchsmp.skills.use",
                "mitchsmp.artifacts.use",
                "mitchsmp.bosses.use",
                "mitchsmp.endboss.use",
                "mitchsmp.essentials.spawn",
                "mitchsmp.essentials.trash",
                "mitchsmp.hub.use",
                "mitchsmp.skyblock.use",
                "mitchsmp.bedwars.play",
                "mitchsmp.tntrun.play",
                "mitchsmp.spleef.play",
                "mitchsmp.skirmish.play",
                "mitchsmp.gameplay.use"
            );
            addDefault(MitchRank.VIP,
                "mitchsmp.cosmetics.vip",
                "mitchsmp.chat.color"
            );
            addDefault(MitchRank.MVP, "mitchsmp.cosmetics.mvp");
            addDefault(MitchRank.LEGEND, "mitchsmp.cosmetics.legend");
            addDefault(MitchRank.HELPER,
                "mitchsmp.staffmode",
                "mitchsmp.essentials.back",
                "mitchsmp.staffchat",
                "mitchsmp.chat.mute",
                "mitchsmp.maintenance.bypass",
                "mitchsmp.qa.run",
                "mitchsmp.updates.view",
                "mitchsmp.updates.check"
            );
            addDefault(MitchRank.MODERATOR,
                "mitchsmp.tpa.bypass",
                "mitchsmp.homes.bypass",
                "mitchsmp.rtp.bypass",
                "mitchsmp.combat.admin",
                "mitchsmp.anticheat.alerts",
                "mitchsmp.anticheat.admin",
                "mitchsmp.performance.alerts",
                "mitchsmp.errors.view",
                "mitchsmp.maintenance.bypass",
                "mitchsmp.qa.run",
                "mitchsmp.economy.alerts",
                "mitchsmp.auctionhouse.alerts",
                "mitchsmp.reports.staff",
                "mitchsmp.updates.view",
                "mitchsmp.updates.check"
            );
            addDefault(MitchRank.ADMIN,
                "mitchsmp.core.reload",
                "mitchsmp.core.motd",
                "mitchsmp.rank.set",
                "mitchsmp.permissions.manage",
                "mitchsmp.lifesteal.admin",
                "mitchsmp.economy.admin",
                "mitchsmp.auctionhouse.admin",
                "mitchsmp.bounties.admin",
                "mitchsmp.essentials.admin",
                "mitchsmp.artifacts.admin",
                "mitchsmp.bedwars.admin",
                "mitchsmp.tntrun.admin",
                "mitchsmp.spleef.admin",
                "mitchsmp.events.admin",
                "mitchsmp.bosses.admin",
                "mitchsmp.custommobs.admin",
                "mitchsmp.progression.admin",
                "mitchsmp.skills.admin",
                "mitchsmp.endboss.admin",
                "mitchsmp.hub.admin",
                "mitchsmp.skyblock.admin",
                "mitchsmp.performance.admin",
                "mitchsmp.season.admin",
                "mitchsmp.features.admin",
                "mitchsmp.features.override",
                "mitchsmp.maintenance.admin",
                "mitchsmp.recovery.admin",
                "mitchsmp.updates.stage",
                "mitchsmp.updates.verify",
                "mitchsmp.updates.approve",
                "mitchsmp.updates.rollback",
                "mitchsmp.updates.admin",
                "mitchsmp.updates.debug"
            );
            addDefault(MitchRank.OWNER, "mitchsmp.*");
        }

        private void loadCustom() {
            for (String key : permissionsFile.keys()) {
                if (!key.startsWith("rank.") || !key.contains(".add.")) {
                    continue;
                }
                String[] parts = key.split("\\.");
                if (parts.length < 4) {
                    continue;
                }
                MitchRank rank = MitchRank.parse(parts[1]);
                permissions.computeIfAbsent(rank, ignored -> new HashSet<>())
                    .add(permissionsFile.getString(key, ""));
            }
        }

        private void addDefault(MitchRank rank, String... values) {
            Set<String> set = permissions.computeIfAbsent(rank, ignored -> new LinkedHashSet<>());
            for (String value : values) {
                set.add(value);
            }
        }

        private String safe(String permission) {
            return permission.toLowerCase(Locale.ROOT).replace("*", "star").replace(" ", "_");
        }

        private boolean matches(String owned, String requested) {
            String normalized = owned.toLowerCase(Locale.ROOT);
            if (normalized.equals("*") || normalized.equals(requested)) {
                return true;
            }
            if (normalized.endsWith(".*")) {
                String prefix = normalized.substring(0, normalized.length() - 1);
                return requested.startsWith(prefix);
            }
            return false;
        }

        private boolean isSandboxAdmin(Player player, MitchRank rank) {
            return player != null
                && player.getWorld() != null
                && player.getWorld().getName().toLowerCase(Locale.ROOT).startsWith("mitchtest_")
                && (player.isOp() || rank.inherits(MitchRank.ADMIN));
        }

        private boolean isPassiveStaffPermission(String requested) {
            return requested.equals("mitchsmp.staffchat")
                || requested.equals("mitchsmp.anticheat.alerts")
                || requested.equals("mitchsmp.performance.alerts")
                || requested.equals("mitchsmp.economy.alerts")
                || requested.equals("mitchsmp.auctionhouse.alerts");
        }
    }
}
