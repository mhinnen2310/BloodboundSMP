package nl.mitchsmp.combattag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.CombatTagService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatTagPlugin extends JavaPlugin implements Listener, CombatTagService, TabCompleter {
    private static final long TAG_MILLIS = 20_000L;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();

    @Override
    public void onEnable() {
        MitchSMP.registerService(CombatTagService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("combat") != null) {
            getCommand("combat").setExecutor(this);
            getCommand("combat").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::cleanup, 20L, 20L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (MitchSMP.permissions().isAdminRestricted(attacker) || MitchSMP.permissions().isAdminRestricted(victim)) {
            return;
        }
        if (attacker.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        tag(attacker.getUniqueId(), victim.getUniqueId());
        tag(victim.getUniqueId(), attacker.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!isTagged(player.getUniqueId())) {
            return;
        }
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&c" + player.getName() + " logde uit tijdens combat en stierf."));
        try {
            player.setHealth(0.0D);
        } catch (IllegalArgumentException ignored) {
        }
        taggedUntil.remove(player.getUniqueId());
    }

    @Override
    public boolean isTagged(UUID playerId) {
        return remainingMillis(playerId) > 0L;
    }

    @Override
    public long remainingMillis(UUID playerId) {
        long until = taggedUntil.getOrDefault(playerId, 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    @Override
    public void tag(UUID playerId, UUID opponentId) {
        boolean wasTagged = isTagged(playerId);
        taggedUntil.put(playerId, System.currentTimeMillis() + TAG_MILLIS);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && !wasTagged) {
            player.sendMessage(Text.PREFIX + Text.color("&cJe bent nu in combat. Geen /home, /tpa of logout."));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        long remaining = remainingMillis(player.getUniqueId());
        if (remaining <= 0L) {
            Text.msg(sender, "&aJe bent niet in combat.");
        } else {
            Text.msg(sender, "&cNog " + ((remaining + 999L) / 1000L) + " seconden combat.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        taggedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}

