package org.bukkit;

public enum ChatColor {
    BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,
    DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE,
    MAGIC, BOLD, STRIKETHROUGH, UNDERLINE, ITALIC, RESET;

    public static String translateAlternateColorCodes(char alternateColorChar, String textToTranslate) {
        return textToTranslate;
    }

    public static String stripColor(String input) {
        return input;
    }
}
