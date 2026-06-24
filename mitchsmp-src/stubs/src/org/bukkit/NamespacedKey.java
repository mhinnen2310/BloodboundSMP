package org.bukkit;

import org.bukkit.plugin.Plugin;

public final class NamespacedKey {
    public NamespacedKey(String namespace, String key) {
    }

    public NamespacedKey(Plugin plugin, String key) {
    }

    public String getNamespace() {
        return "";
    }

    public String getKey() {
        return "";
    }
}
