package nl.mitchsmp.tpa;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.CombatTagService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TpaPlugin extends JavaPlugin implements TabCompleter {
    private static final long EXPIRE_MILLIS = 60_000L;
    private final Map<UUID, Request> requestsByTarget = new HashMap<>();

    @Override
    public void onEnable() {
        command("tpa");
        command("tpaccept");
        command("tpdeny");
        Bukkit.getScheduler().runTaskTimer(this, this::cleanup, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        String name = command.getName().toLowerCase();
        if (name.equals("tpa")) {
            if (args.length < 1) {
                Text.msg(player, "&cGebruik: /tpa <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || target.equals(player)) {
                Text.msg(player, "&cOngeldige speler.");
                return true;
            }
            if (blocked(player) || blocked(target)) {
                Text.msg(player, "&cTeleport requests kunnen niet tijdens combat.");
                return true;
            }
            requestsByTarget.put(target.getUniqueId(), new Request(player.getUniqueId(), System.currentTimeMillis() + EXPIRE_MILLIS));
            Text.msg(player, "&aTPA verzoek gestuurd naar &f" + target.getName() + "&a.");
            Text.msg(target, "&f" + player.getName() + " &7wil naar jou teleporteren. &a/tpaccept &7of &c/tpdeny");
            return true;
        }

        if (name.equals("tpaccept") || name.equals("tpdeny")) {
            Request request = requestFor(player, args);
            if (request == null) {
                Text.msg(player, "&cGeen open TPA verzoek.");
                return true;
            }
            Player requester = Bukkit.getPlayer(request.requester());
            requestsByTarget.remove(player.getUniqueId());
            if (requester == null) {
                Text.msg(player, "&cDie speler is niet meer online.");
                return true;
            }
            if (name.equals("tpdeny")) {
                Text.msg(player, "&cTPA geweigerd.");
                Text.msg(requester, "&cJe TPA verzoek is geweigerd.");
                return true;
            }
            if (blocked(player) || blocked(requester)) {
                Text.msg(player, "&cTeleporteren kan niet tijdens combat.");
                Text.msg(requester, "&cTeleporteren kan niet tijdens combat.");
                return true;
            }
            requester.teleport(player.getLocation());
            Text.msg(player, "&aTPA geaccepteerd.");
            Text.msg(requester, "&aJe bent geteleporteerd.");
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.onlinePlayers(args[0]);
        }
        return List.of();
    }

    private Request requestFor(Player player, String[] args) {
        Request request = requestsByTarget.get(player.getUniqueId());
        if (request == null || request.expired()) {
            return null;
        }
        if (args.length == 0) {
            return request;
        }
        Player named = Bukkit.getPlayerExact(args[0]);
        if (named != null && named.getUniqueId().equals(request.requester())) {
            return request;
        }
        return null;
    }

    private boolean blocked(Player player) {
        CombatTagService combat = MitchSMP.combatTags();
        return combat != null && combat.isTagged(player.getUniqueId()) && !MitchSMP.permissions().has(player, "mitchsmp.tpa.bypass");
    }

    private void cleanup() {
        Iterator<Map.Entry<UUID, Request>> iterator = requestsByTarget.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expired()) {
                iterator.remove();
            }
        }
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private record Request(UUID requester, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}

