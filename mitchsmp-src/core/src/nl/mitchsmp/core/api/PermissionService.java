package nl.mitchsmp.core.api;

import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface PermissionService {
    boolean has(CommandSender sender, String permission);

    boolean isAdminMode(Player player);

    boolean isAdminModeOverride(Player player);

    boolean isAdminRestricted(Player player);

    void setAdminMode(Player player, boolean active);

    void setAdminModeOverride(Player player, boolean active);

    Set<String> permissionsFor(MitchRank rank);

    void addPermission(MitchRank rank, String permission);

    void removePermission(MitchRank rank, String permission);

    void sync(Player player);

    void syncAll();

    UUID findKnownPlayer(String name);
}
