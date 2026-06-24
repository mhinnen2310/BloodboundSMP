package nl.mitchsmp.permissions;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class PermissionsPlugin extends JavaPlugin implements TabCompleter {
    private static final List<CommandPermission> COMMAND_PERMISSIONS = List.of(
        new CommandPermission("/spawn", "mitchsmp.essentials.spawn"),
        new CommandPermission("/back (adminmode only)", "mitchsmp.essentials.back"),
        new CommandPermission("/tpa, /tpaccept, /tpdeny", "mitchsmp.tpa.use"),
        new CommandPermission("/home, /homes, /sethome, /delhome", "mitchsmp.homes.use"),
        new CommandPermission("/balance, /moneytop", "mitchsmp.economy.use"),
        new CommandPermission("/pay", "mitchsmp.economy.pay"),
        new CommandPermission("/ah, /ah sell/search/sort/cancel", "mitchsmp.auctionhouse.use"),
        new CommandPermission("/bounty, /bounties", "mitchsmp.bounties.view"),
        new CommandPermission("/hud", "mitchsmp.hud.use"),
        new CommandPermission("/rtp", "mitchsmp.rtp.use"),
        new CommandPermission("/bw join", "mitchsmp.bedwars.play"),
        new CommandPermission("/tntrun join", "mitchsmp.tntrun.play"),
        new CommandPermission("/spleef join", "mitchsmp.spleef.play"),
        new CommandPermission("/collection, /contracts, /orders, /login, /explorer, /legacy", "mitchsmp.progression.use"),
        new CommandPermission("/skills, /abilities, /mechanics", "mitchsmp.skills.use"),
        new CommandPermission("/endboss ritual/start/join/confirm/status", "mitchsmp.endboss.use"),
        new CommandPermission("/adminmode, /staffmode", "mitchsmp.staffmode"),
        new CommandPermission("/rank, /setrank", "mitchsmp.rank.set"),
        new CommandPermission("/perm, /rankperms", "mitchsmp.permissions.manage"),
        new CommandPermission("/eco, /econwatch", "mitchsmp.economy.admin"),
        new CommandPermission("/sethearts, /corruptedheart give", "mitchsmp.lifesteal.admin"),
        new CommandPermission("/bounty config", "mitchsmp.bounties.admin"),
        new CommandPermission("/event start, /event config", "mitchsmp.events.admin"),
        new CommandPermission("/perf status", "mitchsmp.performance.alerts"),
        new CommandPermission("/perf config", "mitchsmp.performance.admin"),
        new CommandPermission("/admin, /invsee, /enderchest, /tp, /tphere, /heal, /feed, /fly, /gm, /speed, /setspawn, /freeze, /lockdown, /release, /jail, /unjail, /noclip, /fakeores, /godtools, /shop edit/set/remove/reset/tab, /lagclear", "mitchsmp.essentials.admin"),
        new CommandPermission("/bossshards give, /opshop admin rewards", "mitchsmp.artifacts.admin"),
        new CommandPermission("/relic give, /progression config/reset, /legacy snapshot/delete/purgehof/rebuild", "mitchsmp.progression.admin"),
        new CommandPermission("/skills admin, /abilities config", "mitchsmp.skills.admin"),
        new CommandPermission("/endboss force/end/config", "mitchsmp.endboss.admin"),
        new CommandPermission("/season start/end/reset/delete/purge", "mitchsmp.season.admin"),
        new CommandPermission("/bw paid/premade/start/end/admin", "mitchsmp.bedwars.admin"),
        new CommandPermission("/tntrun premade/paid/start/end", "mitchsmp.tntrun.admin"),
        new CommandPermission("/spleef premade/paid/start/end", "mitchsmp.spleef.admin"),
        new CommandPermission("/ac alerts/sus/check/clear", "mitchsmp.anticheat.admin")
    );

    @Override
    public void onEnable() {
        command("rank");
        command("setrank");
        command("groups");
        command("perm");
        command("rankperms");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("groups")) {
            Text.msg(sender, "&7Ranks: &f" + Arrays.stream(MitchRank.values()).map(MitchRank::displayName).collect(Collectors.joining("&7, &f")));
            return true;
        }

        if (name.equals("rank")) {
            if (args.length == 0) {
                Text.msg(sender, "&cGebruik: /rank <player> [rank]");
                return true;
            }
            UUID targetId = find(args[0]);
            if (targetId == null) {
                Text.msg(sender, "&cUnknowne speler.");
                return true;
            }
            if (args.length == 1) {
                Text.msg(sender, "&f" + args[0] + " &7heeft rank &f" + MitchSMP.ranks().getRank(targetId).displayName() + "&7.");
                return true;
            }
            return setRank(sender, args[0], targetId, args[1]);
        }

        if (name.equals("setrank")) {
            if (args.length < 2) {
                Text.msg(sender, "&cGebruik: /setrank <player> <rank>");
                return true;
            }
            UUID targetId = find(args[0]);
            if (targetId == null) {
                Text.msg(sender, "&cUnknowne speler.");
                return true;
            }
            return setRank(sender, args[0], targetId, args[1]);
        }

        if (name.equals("perm")) {
            return permissionCommand(sender, args);
        }

        if (name.equals("rankperms")) {
            return rankPerms(sender, args);
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("groups")) {
            return List.of();
        }
        if (name.equals("rank")) {
            if (args.length == 1) {
                return Tab.onlinePlayers(args[0]);
            }
            return args.length == 2 ? Tab.complete(args[1], rankNames()) : List.of();
        }
        if (name.equals("setrank")) {
            if (args.length == 1) {
                return Tab.onlinePlayers(args[0]);
            }
            return args.length == 2 ? Tab.complete(args[1], rankNames()) : List.of();
        }
        if (name.equals("perm")) {
            if (args.length == 1) {
                return Tab.complete(args[0], rankNames());
            }
            if (args.length == 2) {
                return Tab.complete(args[1], "add", "remove", "list");
            }
            if (args.length == 3 && !args[1].equalsIgnoreCase("list")) {
                return Tab.complete(args[2],
                    "mitchsmp.core.reload",
                    "mitchsmp.staffmode",
                    "mitchsmp.rank.set",
                    "mitchsmp.economy.admin",
                    "mitchsmp.lifesteal.admin",
                    "mitchsmp.auctionhouse.admin",
                    "mitchsmp.bounties.admin",
                    "mitchsmp.essentials.admin",
                    "mitchsmp.artifacts.admin",
                    "mitchsmp.bedwars.admin",
                    "mitchsmp.events.admin",
                    "mitchsmp.progression.admin",
                    "mitchsmp.season.admin",
                    "mitchsmp.chat.mute",
                    "mitchsmp.chat.color",
                    "mitchsmp.anticheat.alerts"
                );
            }
        }
        if (name.equals("rankperms")) {
            if (args.length == 1) {
                List<String> result = new java.util.ArrayList<>(rankNames());
                result.add("commands");
                return Tab.complete(args[0], result);
            }
            if (args.length == 2) {
                return Tab.complete(args[1], "1", "2", "3", "4", "5");
            }
        }
        return List.of();
    }

    private boolean setRank(CommandSender sender, String targetName, UUID targetId, String rankName) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.rank.set")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        MitchRank rank = MitchRank.parse(rankName);
        MitchSMP.ranks().setRank(targetId, rank);
        Text.msg(sender, "&aRank van &f" + targetName + " &agezet naar &f" + rank.displayName() + "&a.");
        return true;
    }

    private boolean permissionCommand(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.permissions.manage")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cGebruik: /perm <rank> <add|remove|list> [permission]");
            return true;
        }
        MitchRank rank = MitchRank.parse(args[0]);
        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            Text.msg(sender, "&f" + rank.displayName() + " &7permissions: &f" + MitchSMP.permissions().permissionsFor(rank));
            return true;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGeef een permission op.");
            return true;
        }
        if (action.equals("add")) {
            MitchSMP.permissions().addPermission(rank, args[2]);
            Text.msg(sender, "&aPermission toegevoegd.");
            return true;
        }
        if (action.equals("remove")) {
            MitchSMP.permissions().removePermission(rank, args[2]);
            Text.msg(sender, "&aPermission verwijderd.");
            return true;
        }
        Text.msg(sender, "&cUnknowne actie.");
        return true;
    }

    private boolean rankPerms(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.permissions.manage")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length >= 1) {
            MitchRank rank = MitchRank.parse(args[0]);
            if (!args[0].equalsIgnoreCase("commands") && rank.name().equalsIgnoreCase(args[0])) {
                List<String> permissions = MitchSMP.permissions().permissionsFor(rank).stream().sorted().toList();
                int page = args.length >= 2 ? page(args[1]) : 1;
                sendPage(sender, "&6Permissions for " + rank.displayName(), permissions, page);
                return true;
            }
        }
        int page = args.length >= 1 && !args[0].equalsIgnoreCase("commands") ? page(args[0]) : args.length >= 2 ? page(args[1]) : 1;
        List<String> lines = COMMAND_PERMISSIONS.stream()
            .map(entry -> "&f" + entry.command() + " &7-> &e" + entry.permission() + " &7Ranks: &a" + ranksFor(entry.permission()))
            .toList();
        sendPage(sender, "&6Command permissions", lines, page);
        return true;
    }

    private String ranksFor(String permission) {
        return Arrays.stream(MitchRank.values())
            .filter(rank -> rankHas(rank, permission))
            .map(MitchRank::displayName)
            .collect(Collectors.joining(", "));
    }

    private boolean rankHas(MitchRank rank, String permission) {
        String requested = permission.toLowerCase();
        for (String owned : MitchSMP.permissions().permissionsFor(rank)) {
            String normalized = owned.toLowerCase();
            if (normalized.equals("*") || normalized.equals(requested)) {
                return true;
            }
            if (normalized.endsWith(".*") && requested.startsWith(normalized.substring(0, normalized.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private void sendPage(CommandSender sender, String title, List<String> lines, int page) {
        int pageSize = 7;
        int maxPage = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        int current = Math.max(1, Math.min(maxPage, page));
        Text.msg(sender, title + " &7(" + current + "/" + maxPage + ")");
        for (int i = (current - 1) * pageSize; i < Math.min(lines.size(), current * pageSize); i++) {
            Text.msg(sender, lines.get(i));
        }
    }

    private int page(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private UUID find(String name) {
        UUID known = MitchSMP.permissions().findKnownPlayer(name);
        if (known != null) {
            return known;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline == null ? null : offline.getUniqueId();
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private List<String> rankNames() {
        return Arrays.stream(MitchRank.values()).map(rank -> rank.name().toLowerCase()).toList();
    }

    private record CommandPermission(String command, String permission) {
    }
}


