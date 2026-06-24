package nl.mitchsmp.core.api;

import java.util.Locale;

public enum MitchRank {
    DEFAULT("Default", "&8[Default] ", 0, 3, false, null),
    VIP("VIP", "&c[VIP] ", 10, 5, false, DEFAULT),
    MVP("MVP", "&4[MVP] ", 20, 8, false, VIP),
    LEGEND("Legend", "&6[Legend] ", 30, 12, false, MVP),
    HELPER("Helper", "&e[Helper] ", 40, 3, true, DEFAULT),
    MODERATOR("Moderator", "&7[Mod] ", 50, 3, true, HELPER),
    ADMIN("Admin", "&4[Admin] ", 60, 6, true, MODERATOR),
    OWNER("Owner", "&6[Owner] ", 100, 6, true, ADMIN);

    private final String displayName;
    private final String prefix;
    private final int weight;
    private final int homeLimit;
    private final boolean staff;
    private final MitchRank parent;

    MitchRank(String displayName, String prefix, int weight, int homeLimit, boolean staff, MitchRank parent) {
        this.displayName = displayName;
        this.prefix = prefix;
        this.weight = weight;
        this.homeLimit = homeLimit;
        this.staff = staff;
        this.parent = parent;
    }

    public String displayName() {
        return displayName;
    }

    public String prefix() {
        return prefix;
    }

    public int weight() {
        return weight;
    }

    public int homeLimit() {
        return homeLimit;
    }

    public boolean staff() {
        return staff;
    }

    public MitchRank parent() {
        return parent;
    }

    public boolean inherits(MitchRank other) {
        for (MitchRank rank = this; rank != null; rank = rank.parent) {
            if (rank == other) {
                return true;
            }
        }
        return false;
    }

    public static MitchRank parse(String input) {
        if (input == null || input.isBlank()) {
            return DEFAULT;
        }
        String normalized = input.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        for (MitchRank rank : values()) {
            if (rank.name().equals(normalized) || rank.displayName.toUpperCase(Locale.ROOT).equals(normalized)) {
                return rank;
            }
        }
        return DEFAULT;
    }
}
