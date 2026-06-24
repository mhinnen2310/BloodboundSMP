package nl.mitchsmp.economywatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.EconomyWatchService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.enchantments.Enchantment;

public final class EconomyWatchPlugin extends JavaPlugin implements EconomyWatchService, Listener, TabCompleter {
    private static final double HALF_LIFE_MILLIS = 6.0D * 60.0D * 60.0D * 1000.0D;
    private static final int DEFAULT_ACTIVE_DAYS = 14;
    private PropertiesFile market;

    @Override
    public void onEnable() {
        market = new PropertiesFile(getDataFolder().toPath().resolve("economy-watch.properties"));
        MitchSMP.registerService(EconomyWatchService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("econwatch") != null) {
            getCommand("econwatch").setExecutor(this);
            getCommand("econwatch").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::scanOnlineEconomy, 100L, 20L * 60L);
        Bukkit.getScheduler().runTaskLater(this, this::scanOnlineEconomy, 40L);
    }

    @Override
    public void onDisable() {
        market.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        recordSeen(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this, this::scanOnlineEconomy, 20L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Bukkit.getScheduler().runTaskLater(this, this::scanOnlineEconomy, 1L);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getItemDrop() != null) {
            ItemStack item = event.getItemDrop().getItemStack();
            if (item != null) {
                recordItemSignal(item.getType(), Math.max(1, item.getAmount()), "drop");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && ignoredPlayer(player)) {
            return;
        }
        Material material = event.getBlock() == null ? null : event.getBlock().getType();
        if (material != null && material != Material.AIR) {
            recordItemSignal(material, 1, "break");
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getDrops() == null) {
            return;
        }
        for (ItemStack item : event.getDrops()) {
            if (item != null) {
                recordItemSignal(item.getType(), Math.max(1, item.getAmount()), "mobdrop");
            }
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.getLoot() == null) {
            return;
        }
        for (ItemStack item : event.getLoot()) {
            if (item != null) {
                recordItemSignal(item.getType(), Math.max(1, item.getAmount()), event.isPlugin() ? "pluginloot" : "worldloot");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.economy.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
            scanOnlineEconomy();
            Text.msg(sender, "&aEconomy Watch:");
            Text.msg(sender, "&7Online stock players: &f" + market.getInt("snapshot.players", 0));
            Text.msg(sender, "&7Active money players: &f" + market.getInt("money.players", 0) + " &8(" + activeDays() + "d)");
            Text.msg(sender, "&7Total money: &a$" + format(market.getDouble("money.total", 0.0D)));
            Text.msg(sender, "&7Average balance: &a$" + format(market.getDouble("money.average", 0.0D)));
            Text.msg(sender, "&7Richest balance: &a$" + format(market.getDouble("money.richest", 0.0D)));
            Text.msg(sender, "&7Money pressure multiplier: &f" + format(moneyMultiplier()) + "x");
            Text.msg(sender, "&7Bekijk item: &f/econwatch price <material>&7, duurste: &f/econwatch top&7, actieve window: &f/econwatch active <days>&7.");
            return true;
        }
        if (args[0].equalsIgnoreCase("active")) {
            if (args.length < 2) {
                Text.msg(sender, "&7Actieve economy window: &f" + activeDays() + " dagen&7.");
                Text.msg(sender, "&7Aanpassen: &f/econwatch active <days>");
                return true;
            }
            try {
                int days = Math.max(1, Integer.parseInt(args[1]));
                market.set("setting.activeDays", days);
                market.save();
                scanOnlineEconomy();
                Text.msg(sender, "&aEconomyWatch active window gezet op &f" + days + " &adagen.");
            } catch (NumberFormatException exception) {
                Text.msg(sender, "&cDays moet een nummer zijn.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("price")) {
            if (args.length < 2) {
                Text.msg(sender, "&cGebruik: /econwatch price <material>");
                return true;
            }
            Material material = material(args[1]);
            if (material == null || material == Material.AIR) {
                Text.msg(sender, "&cUnknown material.");
                return true;
            }
            Text.msg(sender, explain(material));
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            Text.msg(sender, "&aHoogste dynamische quicksell prijzen:");
            int index = 1;
            for (Material material : pricedMaterials().stream().limit(12).toList()) {
                Text.msg(sender, "&7#" + index++ + " &f" + pretty(material) + " &7- &a$" + format(quickSellPrice(material)));
            }
            return true;
        }
        Text.msg(sender, "&cGebruik: /econwatch stats, /econwatch price <material>, /econwatch top, /econwatch active <days>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "stats", "price", "top", "active");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("active")) {
            return Tab.complete(args[1], "7", "14", "30", "60");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("price")) {
            return Tab.complete(args[1], java.util.Arrays.stream(Material.values()).map(Material::name).toList());
        }
        return List.of();
    }

    @Override
    public double quickSellPrice(Material material) {
        if (material == null || material == Material.AIR) {
            return 0.0D;
        }
        if (material == Material.COPPER_INGOT) {
            return quickSellPrice(Material.RAW_COPPER) * 1.05D;
        }
        double recipeCap = recipeMarketValue(material) * setting("craftedValueMultiplier", 0.80D);
        double base = Math.max(basePrice(material), recipeStaticBase(material) * 0.80D);
        double price = base * moneyMultiplier() * scarcityMultiplier(material) * supplyPressureMultiplier(material) * lootAvailabilityMultiplier(material);
        double cap = maximumPrice(material);
        if (recipeCap > 0.0D) {
            cap = Math.min(cap, recipeCap);
        }
        double minimum = recipeCap > 0.0D ? Math.min(minimumPrice(material), recipeCap) : minimumPrice(material);
        return Math.max(minimum, Math.min(cap, price));
    }

    @Override
    public double quickSellPrice(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return 0.0D;
        }
        if (MitchSMP.bossShards() != null && MitchSMP.bossShards().isShard(item)) {
            return 0.0D;
        }
        if (MitchSMP.corruptedHearts() != null && MitchSMP.corruptedHearts().isCorruptedHeart(item)) {
            return setting("corruptedHeartValue", 750.0D) * moneyMultiplier();
        }
        double safeFloor = safeGearFloor(item.getType());
        double price = Math.max(quickSellPrice(item.getType()), safeFloor);
        double enchantBonus = enchantmentValue(item);
        double durability = durabilityMultiplier(item);
        double rarityBonus = rarityBonus(item);
        double value = (price + enchantBonus + rarityBonus) * durability;
        double floor = safeFloor;
        if (floor > 0.0D && enchantBonus > 0.0D) {
            floor *= 1.35D;
        }
        double cap = Math.max(maximumPrice(item.getType()) * (isGear(item.getType()) ? 12.0D : 2.0D), value * 1.25D);
        return Math.max(Math.max(minimumPrice(item.getType()), floor), Math.min(cap, value));
    }

    @Override
    public double basePrice(Material material) {
        String name = material.name();
        if (material == Material.NETHERITE_INGOT) {
            return 480.0D;
        }
        if (material == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
            return 50.0D;
        }
        double crafted = recipeStaticBase(material);
        if (crafted > 0.0D) {
            return crafted * 0.90D;
        }
        if (name.contains("NETHERITE")) {
            return name.contains("CHESTPLATE") || name.contains("SWORD") || name.contains("AXE") ? 820.0D : 620.0D;
        }
        if (name.contains("DIAMOND")) {
            if (name.contains("BLOCK")) {
                return 180.0D;
            }
            if (name.contains("SWORD") || name.contains("AXE") || name.contains("CHESTPLATE")) {
                return 210.0D;
            }
            if (name.contains("HELMET") || name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("PICKAXE") || name.contains("SHOVEL")) {
                return 155.0D;
            }
            if (name.contains("ORE")) {
                return 26.0D;
            }
            return 24.0D;
        }
        if (name.contains("EMERALD")) {
            return name.contains("BLOCK") ? 96.0D : name.contains("ORE") ? 18.0D : 13.0D;
        }
        if (name.contains("ANCIENT_DEBRIS")) {
            return 115.0D;
        }
        if (name.contains("ORE")) {
            return name.contains("GOLD") ? 7.0D : name.contains("IRON") ? 4.0D : 2.0D;
        }
        if (name.contains("LOG") || name.endsWith("_WOOD") || name.contains("STEM")) {
            return 0.20D;
        }
        if (name.contains("PLANKS")) {
            return 0.04D;
        }
        if (name.contains("LEAVES") || name.contains("DIRT") || name.contains("SAND") || name.contains("GRAVEL")) {
            return 0.03D;
        }
        if (name.contains("STONE") || name.contains("COBBLE") || name.contains("DEEPSLATE")) {
            return 0.05D;
        }
        if (name.contains("WOOL")) {
            return 0.25D;
        }
        if (name.contains("SPAWN_EGG")) {
            return 180.0D;
        }
        return switch (material) {
            case STICK -> 0.01D;
            case BONE -> 0.24D;
            case BONE_MEAL -> 0.06D;
            case ROTTEN_FLESH -> 0.04D;
            case GLASS -> 0.12D;
            case GLASS_PANE -> 0.035D;
            case RAW_COPPER -> 0.65D;
            case COPPER_INGOT -> 0.70D;
            case NETHERITE_INGOT -> 480.0D;
            case NETHERITE_UPGRADE_SMITHING_TEMPLATE -> 50.0D;
            case IRON_INGOT -> 3.0D;
            case GOLD_INGOT -> 5.0D;
            case GOLD_BLOCK -> 42.0D;
            case REDSTONE -> 1.0D;
            case GUNPOWDER -> 2.0D;
            case EXPERIENCE_BOTTLE -> 0.8D;
            case TOTEM_OF_UNDYING -> 120.0D;
            case ENCHANTED_GOLDEN_APPLE -> 300.0D;
            case GOLDEN_APPLE -> 16.0D;
            case NETHER_STAR -> 500.0D;
            case DRAGON_EGG -> 2_500.0D;
            case ELYTRA -> 1_500.0D;
            case SPAWNER -> 250.0D;
            default -> 0.10D;
        };
    }

    @Override
    public double moneyMultiplier() {
        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            return 1.0D;
        }
        List<Map.Entry<UUID, Double>> top = activeBalanceEntries(economy);
        double average = top.isEmpty() ? 0.0D : top.stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0D);
        if (average <= 2_500.0D) {
            return 1.2D;
        }
        double multiplier = Math.sqrt(5_000.0D / Math.max(5_000.0D, average));
        return clamp(0.30D, 1.20D, multiplier);
    }

    @Override
    public double scarcityMultiplier(Material material) {
        double expected = expectedStock(material) * Math.max(1, market.getInt("snapshot.players", 1));
        double recentSold = market.getDouble(prefix(material) + ".sold.decayed", 0.0D);
        double stock = onlineStock(material) + supplyScore(material) * 0.50D + recentSold * 2.25D;
        return clamp(0.25D, 2.65D, Math.sqrt((expected + 32.0D) / (stock + 32.0D)));
    }

    @Override
    public int onlineStock(Material material) {
        return market.getInt(prefix(material) + ".stock", 0);
    }

    @Override
    public double supplyScore(Material material) {
        decay(prefix(material) + ".supply");
        return market.getDouble(prefix(material) + ".supply.decayed", 0.0D);
    }

    @Override
    public void recordItemSignal(Material material, int amount, String source) {
        if (material == null || material == Material.AIR || amount <= 0) {
            return;
        }
        String prefix = prefix(material);
        market.set(prefix + "." + source + ".total", market.getLong(prefix + "." + source + ".total", 0L) + amount);
        addDecayed(prefix + ".supply", amount * sourceWeight(source));
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        if (normalizedSource.contains("loot") || normalizedSource.equals("mobdrop")) {
            addDecayed(prefix + ".loot", amount * sourceWeight(source));
        }
        market.save();
    }

    @Override
    public void recordSale(Material material, int amount, double total, String source) {
        if (material == null || material == Material.AIR || amount <= 0) {
            return;
        }
        String prefix = prefix(material);
        market.set(prefix + ".sold.total", market.getLong(prefix + ".sold.total", 0L) + amount);
        market.set(prefix + ".sold.money", market.getDouble(prefix + ".sold.money", 0.0D) + Math.max(0.0D, total));
        addDecayed(prefix + ".sold", amount);
        addDecayed(prefix + ".supply", amount * 0.65D);
        market.save();
    }

    @Override
    public String explain(Material material) {
        return Text.color("&a" + pretty(material)
            + " &7price=&a$" + format(quickSellPrice(material))
            + " &8(base " + format(basePrice(material))
            + ", craft cap " + format(recipeMarketValue(material) * setting("craftedValueMultiplier", 0.80D))
            + ", money " + format(moneyMultiplier()) + "x"
            + ", scarcity " + format(scarcityMultiplier(material)) + "x"
            + ", stock " + onlineStock(material)
            + ", loot " + format(lootAvailabilityMultiplier(material)) + "x"
            + ", supply " + format(supplyScore(material)) + ")");
    }

    private void scanOnlineEconomy() {
        Map<Material, Integer> totals = new HashMap<>();
        int players = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ignoredPlayer(player)) {
                continue;
            }
            recordSeen(player);
            players++;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                totals.merge(item.getType(), Math.max(1, item.getAmount()), Integer::sum);
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                totals.merge(item.getType(), Math.max(1, item.getAmount()), Integer::sum);
            }
        }
        market.set("snapshot.players", players);
        market.set("snapshot.time", System.currentTimeMillis());
        for (Material material : Material.values()) {
            if (material != Material.AIR) {
                market.set(prefix(material) + ".stock", totals.getOrDefault(material, 0));
            }
        }
        updateMoneySnapshot();
        market.save();
    }

    private void updateMoneySnapshot() {
        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            return;
        }
        List<Map.Entry<UUID, Double>> top = activeBalanceEntries(economy);
        double total = top.stream().mapToDouble(Map.Entry::getValue).sum();
        market.set("money.players", top.size());
        market.set("money.total", total);
        market.set("money.average", top.isEmpty() ? 0.0D : total / top.size());
        market.set("money.richest", top.isEmpty() ? 0.0D : top.get(0).getValue());
    }

    private List<Map.Entry<UUID, Double>> activeBalanceEntries(EconomyService economy) {
        return economy.topBalances(250).stream()
            .filter(entry -> isActiveEconomyPlayer(entry.getKey()))
            .toList();
    }

    private List<Material> pricedMaterials() {
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material != Material.AIR) {
                materials.add(material);
            }
        }
        materials.sort(Comparator.comparing((Material material) -> quickSellPrice(material)).reversed().thenComparing(Material::name));
        return materials;
    }

    private boolean ignoredPlayer(Player player) {
        return player == null
            || MitchSMP.permissions().isAdminMode(player)
            || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR
            || isBedWarsWorld(player);
    }

    private void recordSeen(Player player) {
        if (player == null) {
            return;
        }
        market.set("player." + player.getUniqueId() + ".seen", System.currentTimeMillis());
    }

    private boolean isActiveEconomyPlayer(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null && !ignoredPlayer(online)) {
            return true;
        }
        long lastSeen = market.getLong("player." + id + ".seen", 0L);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        lastSeen = Math.max(lastSeen, lastPlayed(offline));
        if (lastSeen <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - lastSeen <= activeDays() * 86_400_000L;
    }

    private long lastPlayed(OfflinePlayer player) {
        if (player == null) {
            return 0L;
        }
        try {
            Object value = player.getClass().getMethod("getLastPlayed").invoke(player);
            return value instanceof Number number ? number.longValue() : 0L;
        } catch (ReflectiveOperationException exception) {
            return 0L;
        }
    }

    private int activeDays() {
        return Math.max(1, market.getInt("setting.activeDays", DEFAULT_ACTIVE_DAYS));
    }

    private boolean isBedWarsWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return world.startsWith("bedwars_") || world.startsWith("bw_") || world.contains("bedwars");
    }

    private double expectedStock(Material material) {
        String name = material.name();
        if (name.contains("LOG") || name.contains("PLANKS") || name.endsWith("_WOOD") || name.contains("STONE") || name.contains("DIRT")) {
            return 12_000.0D;
        }
        if (name.contains("WOOL") || name.contains("SAND") || name.contains("GRAVEL")) {
            return 4_000.0D;
        }
        if (name.contains("ORE")) {
            return name.contains("DIAMOND") || name.contains("EMERALD") ? 180.0D : 900.0D;
        }
        return switch (material) {
            case DIAMOND -> 220.0D;
            case EMERALD -> 400.0D;
            case IRON_INGOT -> 2_400.0D;
            case GOLD_INGOT -> 1_200.0D;
            case REDSTONE -> 4_000.0D;
            case GUNPOWDER -> 1_500.0D;
            case TOTEM_OF_UNDYING -> 40.0D;
            case ENCHANTED_GOLDEN_APPLE -> 12.0D;
            case NETHER_STAR, DRAGON_EGG, ELYTRA, SPAWNER -> 4.0D;
            case STICK, BONE_MEAL, ROTTEN_FLESH -> 20_000.0D;
            case BONE, GLASS, GLASS_PANE, RAW_COPPER, COPPER_INGOT -> 5_000.0D;
            case NETHERITE_UPGRADE_SMITHING_TEMPLATE -> 40.0D;
            default -> 500.0D;
        };
    }

    private double lootAvailabilityMultiplier(Material material) {
        decay(prefix(material) + ".loot");
        double loot = market.getDouble(prefix(material) + ".loot.decayed", 0.0D);
        double expected = Math.max(8.0D, expectedStock(material) * Math.max(1, market.getInt("snapshot.players", 1)) * 0.10D);
        return clamp(0.35D, 1.0D, 1.0D / Math.sqrt(1.0D + loot / expected));
    }

    private double supplyPressureMultiplier(Material material) {
        double expected = expectedStock(material) * Math.max(1, market.getInt("snapshot.players", 1));
        double recentSold = market.getDouble(prefix(material) + ".sold.decayed", 0.0D);
        double pressure = supplyScore(material) * 0.65D + recentSold * 4.0D;
        return clamp(0.25D, 1.12D, 1.0D / Math.sqrt(1.0D + pressure / Math.max(16.0D, expected * 0.35D)));
    }

    private double minimumPrice(Material material) {
        String name = material.name();
        if (name.contains("LOG") || name.contains("PLANKS") || name.contains("STONE") || name.contains("DIRT")) {
            return 0.01D;
        }
        double gearFloor = gearFloor(material);
        if (gearFloor > 0.0D) {
            return gearFloor;
        }
        return 0.05D;
    }

    private double maximumPrice(Material material) {
        return Math.max(0.25D, Math.max(basePrice(material), recipeStaticBase(material)) * 4.0D);
    }

    private double recipeValue(Material material) {
        Recipe recipe = recipe(material);
        if (recipe == null) {
            return 0.0D;
        }
        double total = 0.0D;
        for (RecipePart part : recipe.parts()) {
            total += basePriceRaw(part.material()) * part.amount();
        }
        return total / Math.max(1, recipe.output());
    }

    private double recipeMarketValue(Material material) {
        Recipe recipe = recipe(material);
        if (recipe == null) {
            return 0.0D;
        }
        double total = 0.0D;
        for (RecipePart part : recipe.parts()) {
            total += quickSellPrice(part.material()) * part.amount();
        }
        return total / Math.max(1, recipe.output());
    }

    private double recipeStaticBase(Material material) {
        Recipe recipe = recipe(material);
        if (recipe == null) {
            return 0.0D;
        }
        double total = 0.0D;
        for (RecipePart part : recipe.parts()) {
            total += basePriceRaw(part.material()) * part.amount();
        }
        return total / Math.max(1, recipe.output());
    }

    private boolean isGear(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD")
            || name.endsWith("_AXE")
            || name.endsWith("_PICKAXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || name.equals("SHIELD")
            || name.equals("ELYTRA");
    }

    private double gearFloor(Material material) {
        String name = material.name();
        if (name.startsWith("NETHERITE_")) {
            if (name.endsWith("_CHESTPLATE")) {
                return 2_800.0D;
            }
            if (name.endsWith("_LEGGINGS")) {
                return 2_450.0D;
            }
            if (name.endsWith("_HELMET")) {
                return 1_750.0D;
            }
            if (name.endsWith("_BOOTS")) {
                return 1_400.0D;
            }
            if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")) {
                return 1_850.0D;
            }
            return 1_250.0D;
        }
        if (name.startsWith("DIAMOND_")) {
            if (name.endsWith("_CHESTPLATE")) {
                return 420.0D;
            }
            if (name.endsWith("_LEGGINGS")) {
                return 360.0D;
            }
            if (name.endsWith("_HELMET") || name.endsWith("_BOOTS")) {
                return 260.0D;
            }
            if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")) {
                return 240.0D;
            }
        }
        if (name.equals("ELYTRA")) {
            return 1_500.0D;
        }
        return 0.0D;
    }

    private double safeGearFloor(Material material) {
        double configured = gearFloor(material);
        double recipe = recipeMarketValue(material);
        return recipe > 0.0D ? Math.min(configured, recipe * setting("craftedValueMultiplier", 0.80D)) : configured;
    }

    private double basePriceRaw(Material material) {
        if (material == Material.STICK) return 0.01D;
        if (material == Material.BONE) return 0.24D;
        if (material == Material.BONE_MEAL) return 0.06D;
        if (material == Material.ROTTEN_FLESH) return 0.04D;
        if (material == Material.GLASS) return 0.12D;
        if (material == Material.GLASS_PANE) return 0.035D;
        if (material == Material.RAW_COPPER) return 0.65D;
        if (material == Material.COPPER_INGOT) return 0.70D;
        if (material == Material.NETHERITE_INGOT) return 480.0D;
        if (material == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) return 50.0D;
        String name = material.name();
        if (name.contains("NETHERITE")) {
            return 620.0D;
        }
        if (name.contains("DIAMOND")) {
            return 24.0D;
        }
        if (name.contains("EMERALD")) {
            return 13.0D;
        }
        if (name.contains("GOLD")) {
            return material == Material.GOLD_BLOCK ? 42.0D : 5.0D;
        }
        if (name.contains("IRON")) {
            return 3.0D;
        }
        if (name.contains("STONE") || name.contains("COBBLE")) {
            return 0.05D;
        }
        if (name.contains("LOG") || name.endsWith("_WOOD")) {
            return 0.20D;
        }
        if (name.contains("PLANKS")) {
            return 0.04D;
        }
        return 0.10D;
    }

    private Recipe recipe(Material material) {
        if (material == Material.OAK_PLANKS) {
            return new Recipe(4, new RecipePart(Material.OAK_LOG, 1));
        }
        if (material == Material.STICK) {
            return new Recipe(4, new RecipePart(Material.OAK_PLANKS, 2));
        }
        if (material == Material.GLASS_PANE) {
            return new Recipe(16, new RecipePart(Material.GLASS, 6));
        }
        if (material == Material.BONE_MEAL) {
            return new Recipe(3, new RecipePart(Material.BONE, 1));
        }
        String name = material.name();
        if (name.startsWith("NETHERITE_")) {
            Material diamondItem = diamondVariant(material);
            if (diamondItem != null) {
                return new Recipe(1,
                    new RecipePart(diamondItem, 1),
                    new RecipePart(Material.NETHERITE_INGOT, 1),
                    new RecipePart(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1));
            }
        }
        Material core = toolCore(material);
        if (core == null) {
            return null;
        }
        if (name.endsWith("_SWORD")) {
            return new Recipe(1, new RecipePart(core, 2), new RecipePart(Material.OAK_PLANKS, 1));
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) {
            return new Recipe(1, new RecipePart(core, 3), new RecipePart(Material.OAK_PLANKS, 2));
        }
        if (name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return new Recipe(1, new RecipePart(core, name.endsWith("_HOE") ? 2 : 1), new RecipePart(Material.OAK_PLANKS, 2));
        }
        if (name.endsWith("_HELMET")) {
            return new Recipe(1, new RecipePart(core, 5));
        }
        if (name.endsWith("_CHESTPLATE")) {
            return new Recipe(1, new RecipePart(core, 8));
        }
        if (name.endsWith("_LEGGINGS")) {
            return new Recipe(1, new RecipePart(core, 7));
        }
        if (name.endsWith("_BOOTS")) {
            return new Recipe(1, new RecipePart(core, 4));
        }
        return null;
    }

    private Material toolCore(Material material) {
        String name = material.name();
        if (name.startsWith("DIAMOND_")) {
            return Material.DIAMOND;
        }
        if (name.startsWith("GOLDEN_")) {
            return Material.GOLD_INGOT;
        }
        if (name.startsWith("IRON_")) {
            return Material.IRON_INGOT;
        }
        if (name.startsWith("STONE_")) {
            return Material.STONE;
        }
        if (name.startsWith("WOODEN_")) {
            return Material.OAK_PLANKS;
        }
        return null;
    }

    private Material diamondVariant(Material material) {
        return switch (material) {
            case NETHERITE_SWORD -> Material.DIAMOND_SWORD;
            case NETHERITE_AXE -> Material.DIAMOND_AXE;
            case NETHERITE_PICKAXE -> Material.DIAMOND_PICKAXE;
            case NETHERITE_SHOVEL -> Material.DIAMOND_SHOVEL;
            case NETHERITE_HOE -> Material.DIAMOND_HOE;
            case NETHERITE_HELMET -> Material.DIAMOND_HELMET;
            case NETHERITE_CHESTPLATE -> Material.DIAMOND_CHESTPLATE;
            case NETHERITE_LEGGINGS -> Material.DIAMOND_LEGGINGS;
            case NETHERITE_BOOTS -> Material.DIAMOND_BOOTS;
            default -> null;
        };
    }

    private double enchantmentValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0.0D;
        }
        double base = Math.max(Math.max(quickSellPrice(item.getType()), gearFloor(item.getType())) * 0.18D, 5.0D);
        double bonus = 0.0D;
        bonus += enchantBonus(item, Enchantment.EFFICIENCY, base * 0.55D);
        bonus += enchantBonus(item, Enchantment.FORTUNE, base * 1.25D);
        bonus += enchantBonus(item, Enchantment.SILK_TOUCH, base * 1.15D);
        bonus += enchantBonus(item, Enchantment.SHARPNESS, base * 0.65D);
        bonus += enchantBonus(item, Enchantment.PROTECTION, base * 0.70D);
        bonus += enchantBonus(item, Enchantment.UNBREAKING, base * 0.35D);
        bonus += enchantBonus(item, Enchantment.MENDING, base * 1.40D);
        bonus += enchantBonus(item, Enchantment.LOOTING, base * 1.10D);
        bonus += enchantBonus(item, Enchantment.POWER, base * 0.60D);
        bonus += enchantBonus(item, Enchantment.INFINITY, base * 1.00D);
        bonus += enchantBonus(item, Enchantment.FLAME, base * 0.75D);
        return bonus;
    }

    private double enchantBonus(ItemStack item, Enchantment enchantment, double value) {
        int level = enchantLevel(item, enchantment);
        if (level <= 0) {
            return 0.0D;
        }
        double bonus = value * level;
        if (level > 5) {
            bonus += value * 0.45D * (level - 5);
        }
        return bonus;
    }

    private int enchantLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || enchantment == null || !item.containsEnchantment(enchantment)) {
            return 0;
        }
        try {
            Object value = item.getClass().getMethod("getEnchantmentLevel", Enchantment.class).invoke(item, enchantment);
            return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 1;
        }
    }

    private double rarityBonus(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0.0D;
        }
        ItemMeta meta = item.getItemMeta();
        double floor = gearFloor(item.getType());
        double bonus = 0.0D;
        if (isUnbreakable(meta)) {
            bonus += Math.max(250.0D, floor * 0.35D);
        }
        if (meta != null && meta.hasDisplayName()) {
            String name = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
            if (name != null && name.toLowerCase(Locale.ROOT).contains("event pro")) {
                bonus += Math.max(500.0D, floor * 0.45D);
            }
        }
        return bonus;
    }

    private boolean isUnbreakable(ItemMeta meta) {
        if (meta == null) {
            return false;
        }
        try {
            Object value = meta.getClass().getMethod("isUnbreakable").invoke(meta);
            return value instanceof Boolean result && result;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private double durabilityMultiplier(ItemStack item) {
        try {
            Object maxRaw = item.getType().getClass().getMethod("getMaxDurability").invoke(item.getType());
            int max = maxRaw instanceof Number number ? number.intValue() : 0;
            if (max <= 0 || !item.hasItemMeta()) {
                return 1.0D;
            }
            Object meta = item.getItemMeta();
            if (meta == null) {
                return 1.0D;
            }
            Object hasDamageRaw = meta.getClass().getMethod("hasDamage").invoke(meta);
            if (!(hasDamageRaw instanceof Boolean hasDamage) || !hasDamage) {
                return 1.0D;
            }
            Object damageRaw = meta.getClass().getMethod("getDamage").invoke(meta);
            int damage = damageRaw instanceof Number number ? number.intValue() : 0;
            return clamp(0.15D, 1.0D, (max - Math.max(0, damage)) / (double) max);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 1.0D;
        }
    }

    private double sourceWeight(String source) {
        return switch (source.toLowerCase(Locale.ROOT)) {
            case "worldloot" -> 1.2D;
            case "shopbuy" -> 1.0D;
            case "pluginloot" -> 1.5D;
            case "mobdrop" -> 0.8D;
            case "break" -> 0.35D;
            case "drop" -> 0.20D;
            default -> 0.5D;
        };
    }

    private void addDecayed(String key, double amount) {
        decay(key);
        market.set(key + ".decayed", market.getDouble(key + ".decayed", 0.0D) + amount);
        market.set(key + ".last", System.currentTimeMillis());
    }

    private void decay(String key) {
        long now = System.currentTimeMillis();
        long last = market.getLong(key + ".last", now);
        double value = market.getDouble(key + ".decayed", 0.0D);
        if (value <= 0.0D || last >= now) {
            market.set(key + ".last", now);
            return;
        }
        double multiplier = Math.pow(0.5D, (now - last) / HALF_LIFE_MILLIS);
        market.set(key + ".decayed", value * multiplier);
        market.set(key + ".last", now);
    }

    private String prefix(Material material) {
        return "material." + material.name();
    }

    private Material material(String input) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private double clamp(double min, double max, double value) {
        return Math.max(min, Math.min(max, value));
    }

    private double setting(String key, double fallback) {
        return market.getDouble("setting." + key, fallback);
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private record Recipe(int output, RecipePart... parts) {
    }

    private record RecipePart(Material material, int amount) {
    }
}

