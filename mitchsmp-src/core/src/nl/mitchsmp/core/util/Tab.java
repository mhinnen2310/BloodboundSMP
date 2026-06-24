package nl.mitchsmp.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Tab {
    private Tab() {
    }

    public static List<String> complete(String input, String... options) {
        return complete(input, Arrays.asList(options));
    }

    public static List<String> complete(String input, Collection<String> options) {
        String lower = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    public static List<String> onlinePlayers(String input) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        names.sort(String::compareToIgnoreCase);
        return complete(input, names);
    }

    public static List<String> amounts(String input) {
        return complete(input, "1", "10", "50", "100", "500", "1000", "5000");
    }
}
