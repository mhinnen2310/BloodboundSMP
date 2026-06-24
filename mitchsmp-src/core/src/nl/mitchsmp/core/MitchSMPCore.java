package nl.mitchsmp.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.PermissionService;
import nl.mitchsmp.core.api.RankService;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

public final class MitchSMPCore extends JavaPlugin implements Listener, TabCompleter {
    private final Map<Class<?>, Object> services = new HashMap<>();
    private CoreRankService rankService;
    private CorePermissionService permissionService;

    @Override
    public void onEnable() {
        MitchSMP.setCore(this);

        Path dataRoot = getDataFolder().toPath();
        rankService = new CoreRankService(new PropertiesFile(dataRoot.resolve("players.properties")));
        permissionService = new CorePermissionService(rankService, new PropertiesFile(dataRoot.resolve("permissions.properties")));

        registerService(RankService.class, rankService);
        registerService(PermissionService.class, permissionService);

        Bukkit.getPluginManager().registerEvents(this, this);
        command("mitchcore");

        for (Player player : Bukkit.getOnlinePlayers()) {
            permissionService.sync(player);
        }

        getLogger().info("MitchSMP-Core enabled with " + services.size() + " services.");
    }

    @Override
    public void onDisable() {
        if (permissionService != null) {
            permissionService.removeAllAttachments();
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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        permissionService.removeAttachment(event.getPlayer());
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        String[] frames = {"<", "<<", "<<<", "<<"};
        String frame = frames[(int) ((System.currentTimeMillis() / 700L) % frames.length)];
        event.setMotd(Text.rawColor("&4&lBloodboundSMP &8" + frame + " &6Steal Hearts. Build Legacy.\n&8No Claims. No Mercy. &7| &cLifesteal &8| &6Hub &8| &bSkyblock &8| &fMinigames"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("mitchcore")) {
            return false;
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
                "mitchsmp.spleef.play"
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
                "mitchsmp.chat.mute"
            );
            addDefault(MitchRank.MODERATOR,
                "mitchsmp.tpa.bypass",
                "mitchsmp.homes.bypass",
                "mitchsmp.rtp.bypass",
                "mitchsmp.combat.admin",
                "mitchsmp.anticheat.alerts",
                "mitchsmp.anticheat.admin",
                "mitchsmp.performance.alerts"
            );
            addDefault(MitchRank.ADMIN,
                "mitchsmp.core.reload",
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
                "mitchsmp.progression.admin",
                "mitchsmp.skills.admin",
                "mitchsmp.endboss.admin",
                "mitchsmp.hub.admin",
                "mitchsmp.skyblock.admin",
                "mitchsmp.performance.admin",
                "mitchsmp.season.admin"
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

        private boolean isPassiveStaffPermission(String requested) {
            return requested.equals("mitchsmp.staffchat");
        }
    }
}
