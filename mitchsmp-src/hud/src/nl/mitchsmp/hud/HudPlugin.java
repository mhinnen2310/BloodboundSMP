package nl.mitchsmp.hud;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.BountyService;
import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.SkillService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HudPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String DEFAULT_COMPONENTS = "balance,kills,deaths,rank";

    private PropertiesFile data;
    private PropertiesFile seasonStats;
    private PropertiesFile progressionStats;
    private Method componentText;
    private boolean warnedScoreboard;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("hud.properties"));
        java.nio.file.Path plugins = getDataFolder().toPath().getParent();
        seasonStats = new PropertiesFile(plugins.resolve("MitchSMP-Seasons").resolve("season.properties"));
        progressionStats = new PropertiesFile(plugins.resolve("MitchSMP-Progression").resolve("progression.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("hud") != null) {
            getCommand("hud").setExecutor(this);
            getCommand("hud").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.hud.use")) {
            Text.msg(player, "&cGeen permissie.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Text.msg(player, "&aHUD staat " + (enabled(player) ? "&aan" : "&cuit") + "&a.");
            Text.msg(player, "&7Stats mode: &f" + statsMode(player).key());
            Text.msg(player, "&7Actief: &f" + components(player).stream().map(ComponentPart::key).collect(Collectors.joining("&7, &f")));
            Text.msg(player, "&7Onderdelen: &f" + Arrays.stream(ComponentPart.values()).map(ComponentPart::key).collect(Collectors.joining("&7, &f")));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("on")) {
            data.set("enabled." + player.getUniqueId(), true);
            data.save();
            Text.msg(player, "&aHUD aangezet.");
            return true;
        }
        if (action.equals("off")) {
            data.set("enabled." + player.getUniqueId(), false);
            data.save();
            clearSidebar(player);
            Text.msg(player, "&cHUD uitgezet.");
            return true;
        }
        if (action.equals("reset")) {
            data.set("enabled." + player.getUniqueId(), true);
            data.set("components." + player.getUniqueId(), DEFAULT_COMPONENTS);
            data.save();
            Text.msg(player, "&aHUD gereset.");
            return true;
        }
        if (action.equals("mode")) {
            if (args.length < 2) {
                Text.msg(player, "&cGebruik: /hud mode <seasonal|overall>");
                return true;
            }
            StatsMode mode = StatsMode.parse(args[1]);
            if (mode == null) {
                Text.msg(player, "&cUnknown mode. Use seasonal or overall.");
                return true;
            }
            data.set("mode." + player.getUniqueId(), mode.key());
            data.save();
            Text.msg(player, "&aHUD stats mode: &f" + mode.key());
            return true;
        }
        if (action.equals("toggle") || action.equals("add") || action.equals("remove")) {
            if (args.length < 2) {
                Text.msg(player, "&cGebruik: /hud " + action + " <onderdeel>");
                return true;
            }
            ComponentPart part = ComponentPart.parse(args[1]);
            if (part == null) {
                Text.msg(player, "&cUnknown component. Use /hud list.");
                return true;
            }
            Set<ComponentPart> parts = components(player);
            boolean active = parts.contains(part);
            if (action.equals("add") || (action.equals("toggle") && !active)) {
                parts.add(part);
                Text.msg(player, "&aHUD onderdeel toegevoegd: &f" + part.key());
            } else {
                parts.remove(part);
                Text.msg(player, "&eHUD onderdeel verwijderd: &f" + part.key());
            }
            saveComponents(player, parts);
            return true;
        }

        Text.msg(player, "&cGebruik: /hud <on|off|mode|toggle|add|remove|list|reset>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "on", "off", "mode", "toggle", "add", "remove", "list", "reset");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return Tab.complete(args[1], "seasonal", "overall");
        }
        if (args.length == 2 && args[0].matches("(?i)toggle|add|remove")) {
            return Tab.complete(args[1], Arrays.stream(ComponentPart.values()).map(ComponentPart::key).toList());
        }
        return List.of();
    }

    private void tick() {
        seasonStats.load();
        progressionStats.load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!enabled(player)) {
                clearSidebar(player);
                continue;
            }
            showSidebar(player, buildLines(player));
        }
    }

    private List<String> buildLines(Player player) {
        List<String> parts = new ArrayList<>();
        for (ComponentPart part : components(player)) {
            String rendered = render(player, part);
            if (!rendered.isBlank()) {
                parts.add(rendered);
            }
        }
        SkillService skills = MitchSMP.skills();
        if (skills != null) {
            for (String ability : skills.getAbilityHudLines(player.getUniqueId())) {
                if (ability != null && !ability.isBlank()) {
                    parts.add(ability);
                }
            }
        }
        BountyService bounties = MitchSMP.bounties();
        if (bounties != null) {
            double bounty = bounties.getBounty(player.getUniqueId());
            if (bounty > 0.0D) {
                parts.add("&cBOUNTY:&6$" + String.format(Locale.US, "%.2f", bounty));
            }
        }
        return parts;
    }

    private String render(Player player, ComponentPart part) {
        return switch (part) {
            case BALANCE -> balance(player);
            case KILLS -> "&aK" + modeSuffix(player) + ":&f" + stat(player, "kills");
            case DEATHS -> "&cD" + modeSuffix(player) + ":&f" + stat(player, "deaths");
            case KDR -> "&6KDR:&f" + kdr(player);
            case BIOME -> "&3" + biome(player);
            case XYZ -> xyz(player);
            case HEARTS -> hearts(player);
            case RANK -> "&7Rank:&f" + MitchSMP.ranks().getRank(player.getUniqueId()).displayName();
            case WORLD -> "&7World:&f" + player.getWorld().getName();
            case ONLINE -> "&bOnline:&f" + Bukkit.getOnlinePlayers().size();
            case PING -> "&7Ping:&f" + ping(player);
        };
    }

    private String balance(Player player) {
        EconomyService economy = MitchSMP.economy();
        return economy == null ? "" : "&a$" + String.format(Locale.US, "%.2f", economy.getBalance(player.getUniqueId()));
    }

    private String hearts(Player player) {
        HeartService hearts = MitchSMP.hearts();
        return hearts == null ? "" : "&cHearts:&f" + hearts.getHearts(player.getUniqueId());
    }

    private String kdr(Player player) {
        int kills = stat(player, "kills");
        int deaths = stat(player, "deaths");
        if (deaths == 0) {
            return String.valueOf(kills);
        }
        return String.format(Locale.US, "%.2f", (double) kills / deaths);
    }

    private String biome(Player player) {
        try {
            Object block = player.getLocation().getBlock();
            Object biome = block.getClass().getMethod("getBiome").invoke(block);
            return pretty(String.valueOf(biome));
        } catch (ReflectiveOperationException exception) {
            return "biome?";
        }
    }

    private String xyz(Player player) {
        Location location = player.getLocation();
        return "&eXYZ:&f" + location.getBlockX() + " " + (int) Math.floor(location.getY()) + " " + location.getBlockZ();
    }

    private String ping(Player player) {
        try {
            Object value = player.getClass().getMethod("getPing").invoke(player);
            return value + "ms";
        } catch (ReflectiveOperationException exception) {
            return "?";
        }
    }

    private int stat(Player player, String key) {
        UUID id = player.getUniqueId();
        if (statsMode(player) == StatsMode.OVERALL) {
            return progressionStats.getInt("legacy." + key + "." + id, 0);
        }
        return seasonStats.getInt("stats." + id + "." + key, 0);
    }

    private String modeSuffix(Player player) {
        return statsMode(player) == StatsMode.OVERALL ? "(O)" : "(S)";
    }

    private StatsMode statsMode(Player player) {
        StatsMode mode = StatsMode.parse(data.getString("mode." + player.getUniqueId(), "seasonal"));
        return mode == null ? StatsMode.SEASONAL : mode;
    }

    private boolean enabled(Player player) {
        return Boolean.parseBoolean(data.getString("enabled." + player.getUniqueId(), "true"));
    }

    private Set<ComponentPart> components(Player player) {
        String raw = data.getString("components." + player.getUniqueId(), DEFAULT_COMPONENTS);
        Set<ComponentPart> result = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            ComponentPart parsed = ComponentPart.parse(part);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private void saveComponents(Player player, Set<ComponentPart> parts) {
        data.set("components." + player.getUniqueId(), parts.stream().map(ComponentPart::key).collect(Collectors.joining(",")));
        data.save();
    }

    private String pretty(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        int colon = normalized.lastIndexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }
        return normalized.replace("_", " ");
    }

    private void showSidebar(Player player, List<String> lines) {
        try {
            Object manager = Bukkit.class.getMethod("getScoreboardManager").invoke(null);
            Class<?> managerClass = Class.forName("org.bukkit.scoreboard.ScoreboardManager");
            Object scoreboard = managerClass.getMethod("getNewScoreboard").invoke(manager);
            Object objective = registerObjective(scoreboard);
            setDisplaySlot(objective);

            int scoreValue = Math.max(1, lines.size());
            Set<String> used = new LinkedHashSet<>();
            Class<?> objectiveClass = Class.forName("org.bukkit.scoreboard.Objective");
            Class<?> scoreClass = Class.forName("org.bukkit.scoreboard.Score");
            for (String line : lines) {
                String entry = unique(Text.color(line), used);
                Object score = objectiveClass.getMethod("getScore", String.class).invoke(objective, entry);
                scoreClass.getMethod("setScore", int.class).invoke(score, scoreValue--);
            }
            setScoreboard(player, scoreboard);
        } catch (ReflectiveOperationException exception) {
            if (!warnedScoreboard) {
                warnedScoreboard = true;
                getLogger().warning("Could not render sidebar HUD: " + exception.getMessage());
            }
        }
    }

    private Object registerObjective(Object scoreboard) throws ReflectiveOperationException {
        Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
        Class<?> scoreboardClass = Class.forName("org.bukkit.scoreboard.Scoreboard");
        if (componentText == null) {
            componentText = componentClass.getMethod("text", String.class);
        }
        try {
            Class<?> criteriaClass = Class.forName("org.bukkit.scoreboard.Criteria");
            Object dummy = criteriaClass.getField("DUMMY").get(null);
            Object title = componentText.invoke(null, Text.color("&4Bloodbound"));
            return scoreboardClass.getMethod("registerNewObjective", String.class, criteriaClass, componentClass).invoke(scoreboard, "mitchhud", dummy, title);
        } catch (ReflectiveOperationException exception) {
            return scoreboardClass.getMethod("registerNewObjective", String.class, String.class, String.class).invoke(scoreboard, "mitchhud", "dummy", Text.color("&4Bloodbound"));
        }
    }

    private void setDisplaySlot(Object objective) throws ReflectiveOperationException {
        Class<?> slotClass = Class.forName("org.bukkit.scoreboard.DisplaySlot");
        Class<?> objectiveClass = Class.forName("org.bukkit.scoreboard.Objective");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object sidebar = Enum.valueOf((Class<? extends Enum>) slotClass.asSubclass(Enum.class), "SIDEBAR");
        objectiveClass.getMethod("setDisplaySlot", slotClass).invoke(objective, sidebar);
    }

    private void setScoreboard(Player player, Object scoreboard) throws ReflectiveOperationException {
        Class<?> scoreboardClass = Class.forName("org.bukkit.scoreboard.Scoreboard");
        Player.class.getMethod("setScoreboard", scoreboardClass).invoke(player, scoreboard);
    }

    private void clearSidebar(Player player) {
        try {
            Object manager = Bukkit.class.getMethod("getScoreboardManager").invoke(null);
            Class<?> managerClass = Class.forName("org.bukkit.scoreboard.ScoreboardManager");
            Object scoreboard = managerClass.getMethod("getMainScoreboard").invoke(manager);
            setScoreboard(player, scoreboard);
        } catch (ReflectiveOperationException exception) {
        }
    }

    private String unique(String line, Set<String> used) {
        String clipped = line.length() > 40 ? line.substring(0, 40) : line;
        String value = clipped;
        int suffix = 0;
        while (!used.add(value)) {
            value = clipped + Text.color("&" + Integer.toHexString(suffix++ % 16));
        }
        return value;
    }

    private enum StatsMode {
        SEASONAL("seasonal"),
        OVERALL("overall");

        private final String key;

        StatsMode(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        static StatsMode parse(String input) {
            if (input == null) {
                return null;
            }
            String normalized = input.toLowerCase(Locale.ROOT);
            for (StatsMode mode : values()) {
                if (mode.key.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private enum ComponentPart {
        BALANCE("balance"),
        KILLS("kills"),
        DEATHS("deaths"),
        KDR("kdr"),
        BIOME("biome"),
        XYZ("xyz"),
        HEARTS("hearts"),
        RANK("rank"),
        WORLD("world"),
        ONLINE("online"),
        PING("ping");

        private final String key;

        ComponentPart(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        static ComponentPart parse(String input) {
            if (input == null) {
                return null;
            }
            String normalized = input.toLowerCase(Locale.ROOT).replace("-", "_");
            for (ComponentPart part : values()) {
                if (part.key.equals(normalized) || part.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return part;
                }
            }
            return null;
        }
    }
}


