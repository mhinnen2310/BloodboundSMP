package nl.mitchsmp.core.api;

import nl.mitchsmp.core.MitchSMPCore;

public final class MitchSMP {
    private static MitchSMPCore core;

    private MitchSMP() {
    }

    public static void setCore(MitchSMPCore plugin) {
        core = plugin;
    }

    public static MitchSMPCore core() {
        if (core == null || !core.isEnabled()) {
            throw new IllegalStateException("MitchSMP-Core is not enabled");
        }
        return core;
    }

    public static RankService ranks() {
        return core().ranks();
    }

    public static PermissionService permissions() {
        return core().permissions();
    }

    public static FeatureFlagService features() {
        return service(FeatureFlagService.class);
    }

    public static InventorySnapshotService snapshots() {
        return service(InventorySnapshotService.class);
    }

    public static <T> void registerService(Class<T> type, T service) {
        core().registerService(type, service);
    }

    public static <T> T service(Class<T> type) {
        return core().service(type);
    }

    public static HeartService hearts() {
        return service(HeartService.class);
    }

    public static CombatTagService combatTags() {
        return service(CombatTagService.class);
    }

    public static EconomyService economy() {
        return service(EconomyService.class);
    }

    public static BountyService bounties() {
        return service(BountyService.class);
    }

    public static EconomyWatchService economyWatch() {
        return service(EconomyWatchService.class);
    }

    public static SkillService skills() {
        return service(SkillService.class);
    }

    public static CosmeticService cosmetics() {
        return service(CosmeticService.class);
    }

    public static BossShardService bossShards() {
        return service(BossShardService.class);
    }

    public static CorruptedHeartService corruptedHearts() {
        return service(CorruptedHeartService.class);
    }
}
