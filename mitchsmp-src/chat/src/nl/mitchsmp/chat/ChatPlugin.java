package nl.mitchsmp.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.CosmeticService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatPlugin extends JavaPlugin implements Listener, TabCompleter {
    private final Map<UUID, UUID> replies = new HashMap<>();
    private PropertiesFile mutes;

    @Override
    public void onEnable() {
        mutes = new PropertiesFile(getDataFolder().toPath().resolve("mutes.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        command("msg");
        command("reply");
        command("staffchat");
        command("mute");
        command("unmute");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        long mutedUntil = mutes.getLong(player.getUniqueId().toString(), 0L);
        if (mutedUntil > System.currentTimeMillis()) {
            event.setCancelled(true);
            Text.msg(player, "&cYou are still muted.");
            return;
        }
        if (mutedUntil != 0L) {
            mutes.set(player.getUniqueId().toString(), null);
            mutes.save();
        }
        String message = event.getMessage();
        if (MitchSMP.permissions().has(player, "mitchsmp.chat.color")) {
            message = Text.rawColor(message);
            event.setMessage(message);
        }
        CosmeticService cosmetics = MitchSMP.cosmetics();
        String tag = cosmetics == null ? "" : cosmetics.tag(player.getUniqueId());
        String prefix = Text.color(MitchSMP.ranks().getPrefix(player.getUniqueId())) + tag;
        event.setFormat(prefix + "%1$s" + Text.color("&8: &f") + "%2$s");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("msg")) {
            return msg(sender, args);
        }
        if (name.equals("reply")) {
            return reply(sender, args);
        }
        if (name.equals("staffchat")) {
            return staffchat(sender, args);
        }
        if (name.equals("mute")) {
            return mute(sender, args);
        }
        if (name.equals("unmute")) {
            return unmute(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("msg") && args.length == 1) {
            return Tab.onlinePlayers(args[0]);
        }
        if (name.equals("mute")) {
            if (args.length == 1) {
                return Tab.onlinePlayers(args[0]);
            }
            return args.length == 2 ? Tab.complete(args[1], "5", "10", "30", "60", "1440") : List.of();
        }
        if (name.equals("unmute") && args.length == 1) {
            return Tab.onlinePlayers(args[0]);
        }
        return List.of();
    }

    private boolean msg(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(player, "&cUsage: /msg <player> <message>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(player, "&cSpeler niet online.");
            return true;
        }
        String message = join(args, 1);
        player.sendMessage(Text.color("&8[&dme -> " + target.getName() + "&8] &f") + Text.rawColor(message));
        target.sendMessage(Text.color("&8[&d" + player.getName() + " -> me&8] &f") + Text.rawColor(message));
        replies.put(player.getUniqueId(), target.getUniqueId());
        replies.put(target.getUniqueId(), player.getUniqueId());
        return true;
    }

    private boolean reply(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        UUID targetId = replies.get(player.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            Text.msg(player, "&cNobody to reply to.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(player, "&cUsage: /reply <message>");
            return true;
        }
        return msg(player, new String[] {target.getName(), join(args, 0)});
    }

    private boolean staffchat(CommandSender sender, String[] args) {
        if (!canUseStaffChat(sender)) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cUsage: /staffchat <message>");
            return true;
        }
        String from = sender instanceof Player player ? player.getName() : "Console";
        String message = Text.color("&8[&4Staff&8] &f" + from + "&8: &c") + Text.rawColor(join(args, 0));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isStaff(online)) {
                online.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
        return true;
    }

    private boolean canUseStaffChat(CommandSender sender) {
        return !(sender instanceof Player player) || isStaff(player);
    }

    private boolean isStaff(Player player) {
        return player != null && MitchSMP.ranks().getRank(player.getUniqueId()).staff();
    }

    private boolean mute(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.chat.mute")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cUsage: /mute <player> <minutes> [reason]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        try {
            long minutes = Long.parseLong(args[1]);
            mutes.set(target.getUniqueId().toString(), System.currentTimeMillis() + minutes * 60_000L);
            mutes.save();
            Text.msg(sender, "&aPlayer muted.");
            Text.msg(target, "&cYou have been muted for &f" + minutes + " &cminutes.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cMinuten moet een nummer zijn.");
        }
        return true;
    }

    private boolean unmute(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.chat.mute")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 1) {
            Text.msg(sender, "&cUsage: /unmute <player>");
            return true;
        }
        UUID target = MitchSMP.permissions().findKnownPlayer(args[0]);
        if (target == null) {
            Text.msg(sender, "&cUnknowne speler.");
            return true;
        }
        mutes.set(target.toString(), null);
        mutes.save();
        Text.msg(sender, "&aPlayer unmuted.");
        return true;
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }
}



