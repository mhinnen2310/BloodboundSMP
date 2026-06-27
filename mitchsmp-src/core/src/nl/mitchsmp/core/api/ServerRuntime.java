package nl.mitchsmp.core.api;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

public final class ServerRuntime {
    private final Plugin plugin;

    public ServerRuntime(Plugin plugin) {
        this.plugin = plugin;
    }

    public void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void syncLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    public void async(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public double safeMaxHealth(LivingEntity entity, double requested) {
        double capped = Math.max(1.0D, Math.min(1024.0D, requested));
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(capped);
        }
        return capped;
    }

    public void safeSetHealth(LivingEntity entity, double requested) {
        double max = entity.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : entity.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        entity.setHealth(Math.max(0.1D, Math.min(max, requested)));
    }
}
