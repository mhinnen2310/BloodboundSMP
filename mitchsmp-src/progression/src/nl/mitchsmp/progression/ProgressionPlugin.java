package nl.mitchsmp.progression;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.EconomyWatchService;
import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.SkillService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProgressionPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String HOF_WORLD = "mitchsmp_hall_of_fame";
    private static final List<String> RELICS = List.of("crimson_crown", "ancient_compass", "kings_ring", "forgotten_coin", "blood_relic");
    private static final List<Material> ORDER_MATERIALS = List.of(
        Material.COBBLESTONE, Material.OAK_LOG, Material.WHEAT, Material.CARROT,
        Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT, Material.REDSTONE,
        Material.LAPIS_LAZULI, Material.COAL, Material.EMERALD, Material.DIAMOND,
        Material.GUNPOWDER, Material.BONE, Material.STRING, Material.SLIME_BALL,
        Material.ENDER_PEARL, Material.BLAZE_ROD, Material.QUARTZ, Material.TOTEM_OF_UNDYING
    );
    private static final List<ContractDef> CONTRACTS = buildContracts();
    private static final int[] CONTRACT_SLOTS = {10, 12, 14, 16, 28, 30};

    private final java.util.Random random = new java.util.Random();
    private final Map<UUID, ContractRotation> contractRotationCache = new java.util.HashMap<>();
    private PropertiesFile data;
    private boolean progressionDirty;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("progression.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String command : List.of("collection", "contracts", "orders", "login", "explorer", "legacy", "relic", "progression")) {
            if (getCommand(command) != null) {
                getCommand(command).setExecutor(this);
                getCommand(command).setTabCompleter(this);
            }
        }
        ensureDaily();
        Bukkit.getScheduler().runTaskLater(this, this::rebuildHallOfFame, 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::refreshProgressionMenus, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::flushProgression, 100L, 100L);
    }

    @Override
    public void onDisable() {
        data.save();
    }

    private static List<ContractDef> buildContracts() {
        List<ContractDef> contracts = new ArrayList<>();
        addContractTiers(contracts, ContractType.PLAYER_KILL, "player_kills", "Eliminate %d players", Material.NETHERITE_SWORD, new int[] {2, 3, 5, 8, 12}, 320.0D);
        addContractTiers(contracts, ContractType.BOUNTY_KILL, "bounty_kills", "Claim %d high-value bounties", Material.TOTEM_OF_UNDYING, new int[] {1, 2, 3, 4, 5}, 900.0D);
        addContractTiers(contracts, ContractType.TRAVEL, "travel", "Travel %d blocks", Material.COMPASS, new int[] {1500, 3500, 7000, 12000, 20000}, 0.13D);
        addContractTiers(contracts, ContractType.NETHER_TRAVEL, "nether_travel", "Travel %d blocks in the Nether", Material.NETHERRACK, new int[] {750, 1750, 3500, 6000, 10000}, 0.24D);
        addContractTiers(contracts, ContractType.END_TRAVEL, "end_travel", "Travel %d blocks in the End", Material.END_STONE, new int[] {500, 1250, 2500, 4500, 7500}, 0.32D);
        addContractTiers(contracts, ContractType.DANGEROUS_LOOT, "dangerous_loot", "Loot %d dangerous structures", Material.CHEST, new int[] {1, 2, 3, 5, 8}, 650.0D);
        addContractTiers(contracts, ContractType.BOSS_KILL, "boss_kills", "Defeat %d Bloodbound bosses", Material.NETHER_STAR, new int[] {1, 2, 3, 5, 8}, 1250.0D);
        addContractTiers(contracts, ContractType.MOB_KILL, "mob_kills", "Defeat %d hostile mobs", Material.IRON_SWORD, new int[] {25, 60, 125, 250, 500}, 12.0D);
        addContractTiers(contracts, ContractType.NETHER_MOB_KILL, "nether_mobs", "Defeat %d mobs in the Nether", Material.BLAZE_ROD, new int[] {15, 35, 75, 150, 300}, 24.0D);
        addContractTiers(contracts, ContractType.END_MOB_KILL, "end_mobs", "Defeat %d mobs in the End", Material.ENDER_PEARL, new int[] {10, 25, 50, 100, 200}, 32.0D);
        addContractTiers(contracts, ContractType.DIAMOND_ORE, "diamond_ore", "Mine %d diamond ore", Material.DIAMOND_ORE, new int[] {8, 16, 32, 64, 128}, 55.0D);
        addContractTiers(contracts, ContractType.EMERALD_ORE, "emerald_ore", "Mine %d emerald ore", Material.EMERALD_ORE, new int[] {4, 8, 16, 32, 64}, 78.0D);
        addContractTiers(contracts, ContractType.ANCIENT_DEBRIS, "ancient_debris", "Mine %d ancient debris", Material.ANCIENT_DEBRIS, new int[] {2, 4, 8, 16, 32}, 180.0D);
        addContractTiers(contracts, ContractType.GOLD_ORE, "gold_ore", "Mine %d gold ore", Material.GOLD_ORE, new int[] {24, 48, 96, 192, 384}, 10.0D);
        addContractTiers(contracts, ContractType.REDSTONE_ORE, "redstone_ore", "Mine %d redstone ore", Material.REDSTONE_ORE, new int[] {32, 64, 128, 256, 512}, 6.0D);
        addContractTiers(contracts, ContractType.IRON_ORE, "iron_ore", "Mine %d iron ore", Material.IRON_ORE, new int[] {32, 80, 160, 320, 640}, 5.0D);
        addContractTiers(contracts, ContractType.LOG_BREAK, "timber", "Chop %d logs", Material.DIAMOND_AXE, new int[] {64, 160, 320, 640, 1280}, 3.5D);
        addContractTiers(contracts, ContractType.CROP_HARVEST, "harvest", "Harvest %d mature crops", Material.WHEAT, new int[] {64, 160, 320, 640, 1280}, 3.8D);
        addContractTiers(contracts, ContractType.BLOCK_MINE, "excavation", "Mine %d natural blocks", Material.DIAMOND_PICKAXE, new int[] {500, 1250, 2500, 5000, 10000}, 0.55D);
        addContractTiers(contracts, ContractType.BLOCK_PLACE, "builder", "Place %d building blocks", Material.BRICKS, new int[] {250, 600, 1200, 2400, 4800}, 0.75D);
        return List.copyOf(contracts);
    }

    private static void addContractTiers(List<ContractDef> contracts, ContractType type, String idPrefix, String labelPattern, Material icon, int[] targets, double rewardPerUnit) {
        for (int tier = 0; tier < targets.length; tier++) {
            int target = targets[tier];
            double tierBonus = 1.0D + tier * 0.18D;
            contracts.add(new ContractDef(idPrefix + "_" + target, String.format(Locale.US, labelPattern, target), target, Math.max(250.0D, target * rewardPerUnit * tierBonus), icon, type));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ensureDaily();
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "collection" -> collection(sender, args);
            case "contracts" -> contracts(sender, args);
            case "orders" -> orders(sender, args);
            case "login" -> login(sender);
            case "explorer" -> explorer(sender, args);
            case "legacy" -> legacy(sender, args);
            case "relic" -> relicCommand(sender, args);
            case "progression" -> progression(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("collection") && args.length == 1) {
            return Tab.complete(args[0], "relics");
        }
        if (name.equals("contracts") && args.length == 1) {
            return Tab.complete(args[0], "claim", "config");
        }
        if (name.equals("contracts") && args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            if (sender instanceof Player player) {
                return Tab.complete(args[1], activeContracts(player).stream().map(ContractDef::id).toList());
            }
            return Tab.complete(args[1], CONTRACTS.stream().map(ContractDef::id).toList());
        }
        if (name.equals("orders")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "deliver");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("deliver")) {
                return Tab.complete(args[1], ORDER_MATERIALS.stream().map(material -> material.name().toLowerCase(Locale.ROOT)).toList());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("deliver")) {
                return Tab.amounts(args[2]);
            }
        }
        if (name.equals("explorer") && args.length == 1) {
            return Tab.complete(args[0], "top");
        }
        if (name.equals("legacy") && args.length == 1) {
            return Tab.complete(args[0], "snapshot", "hof", "delete", "purgehof", "rebuild");
        }
        if (name.equals("legacy") && args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return Tab.complete(args[1], legacySeasonNumbers());
        }
        if (name.equals("progression")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "config", "reset");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                return Tab.complete(args[1], "overall");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Tab.complete(args[1], "economyBaseline", "economyScaleMax", "contractDurationMinutes", "orderDurationMinutes", "contractMoneyMultiplier", "orderMoneyMultiplier", "relicChance", "eliteRelicChance");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
                return Tab.complete(args[2], "0.5", "1", "2", "5", "10", "25", "120", "5000");
            }
        }
        if (name.equals("relic")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "give");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return Tab.onlinePlayers(args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return Tab.complete(args[2], RELICS);
            }
        }
        return List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensureDaily();
        Text.msg(event.getPlayer(), "&7Gebruik &f/login &7voor je daily streak en &f/contracts &7voor high-risk opdrachten.");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (isBedWarsWorld(victim) || MitchSMP.permissions().isAdminRestricted(victim)) {
            return;
        }
        add("legacy.deaths." + victim.getUniqueId(), 1);
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId()) || isBedWarsWorld(killer) || MitchSMP.permissions().isAdminRestricted(killer)) {
            return;
        }
        add("legacy.kills." + killer.getUniqueId(), 1);
        progressContracts(killer, ContractType.PLAYER_KILL, 1.0D);
        HeartService hearts = MitchSMP.hearts();
        if (hearts != null && hearts.getHearts(victim.getUniqueId()) > 10) {
            progressContracts(killer, ContractType.BOUNTY_KILL, 1.0D);
            add("legacy.bountyClaims." + killer.getUniqueId(), 1);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null || isBedWarsWorld(player) || MitchSMP.permissions().isAdminRestricted(player)) {
            return;
        }
        if (!from.getWorld().getName().equals(to.getWorld().getName())) {
            return;
        }
        double distance = Math.sqrt(Math.max(0.0D, from.distanceSquared(to)));
        if (distance <= 0.0D || distance > 40.0D) {
            return;
        }
        addDoubleBuffered("explorer.distance." + player.getUniqueId(), distance);
        progressContracts(player, ContractType.TRAVEL, distance, false);
        if (to.getWorld().getEnvironment() == World.Environment.NETHER) {
            progressContracts(player, ContractType.NETHER_TRAVEL, distance, false);
        } else if (to.getWorld().getEnvironment() == World.Environment.THE_END) {
            progressContracts(player, ContractType.END_TRAVEL, distance, false);
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.isPlugin() || event.getLootTable() == null || event.getLootTable().getKey() == null) {
            return;
        }
        String table = event.getLootTable().getKey().toString().toLowerCase(Locale.ROOT);
        double chance = (table.contains("ancient_city") || table.contains("end_city") || table.contains("woodland_mansion"))
            ? setting("eliteRelicChance", 0.018D)
            : setting("relicChance", 0.006D);
        if (table.contains("chests/") && random.nextDouble() < chance) {
            event.getLoot().add(relicItem(RELICS.get(random.nextInt(RELICS.size()))));
        }
        if (table.contains("ancient_city") || table.contains("end_city") || table.contains("woodland_mansion") || table.contains("bastion")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld() != null && !isBedWarsWorld(player) && !MitchSMP.permissions().isAdminRestricted(player)) {
                    progressContracts(player, ContractType.DANGEROUS_LOOT, 1.0D);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        for (String relic : RELICS) {
            if (meta.getPersistentDataContainer().has(relicKey(relic), PersistentDataType.BYTE)) {
                if (unlockRelic(event.getPlayer(), relic)) {
                    consumeRegisteredItem(event, item);
                }
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material broken = event.getBlock().getType();
        if (!isBedWarsWorld(player) && !MitchSMP.permissions().isAdminRestricted(player)) {
            progressContracts(player, ContractType.BLOCK_MINE, 1.0D);
            ContractType oreType = oreContractType(broken);
            if (oreType != null) {
                progressContracts(player, oreType, 1.0D);
            }
            if (isLog(broken)) {
                progressContracts(player, ContractType.LOG_BREAK, 1.0D);
            }
            if (isMatureCrop(event.getBlock())) {
                progressContracts(player, ContractType.CROP_HARVEST, 1.0D);
            }
        }
        if (broken != Material.SPAWNER || isHallOfFame(event.getBlock().getLocation())) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            return;
        }
        try {
            event.getClass().getMethod("setDropItems", boolean.class).invoke(event, false);
        } catch (ReflectiveOperationException ignored) {
        }
        player.getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.SPAWNER));
        Text.msg(player, "&aSpawner gemined met Silk Touch.");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        String name = Text.stripColorCodes(event.getEntity().getCustomName());
        if (killer == null || isBedWarsWorld(killer) || MitchSMP.permissions().isAdminRestricted(killer)) {
            return;
        }
        progressContracts(killer, ContractType.MOB_KILL, 1.0D);
        if (killer.getWorld().getEnvironment() == World.Environment.NETHER) {
            progressContracts(killer, ContractType.NETHER_MOB_KILL, 1.0D);
        } else if (killer.getWorld().getEnvironment() == World.Environment.THE_END) {
            progressContracts(killer, ContractType.END_MOB_KILL, 1.0D);
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("mitchsmp boss") || lowerName.contains("bloodbound boss")) {
            progressContracts(killer, ContractType.BOSS_KILL, 1.0D);
        }
    }

    @EventHandler
    public void onContractBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!isBedWarsWorld(player) && !MitchSMP.permissions().isAdminRestricted(player)) {
            progressContracts(player, ContractType.BLOCK_PLACE, 1.0D);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ProgressionMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (menu.type().equals("contracts")) {
            String id = menu.contract(slot);
            if (id != null) {
                claimContract(player, id);
            }
            openContracts(player);
            return;
        }
        if (menu.type().equals("orders")) {
            Material material = menu.material(slot);
            if (material != null) {
                deliverOrder(player, material, Math.max(1, Math.min(countItems(player, material), orderRemaining(material))));
                openOrders(player);
            }
            return;
        }
        if (menu.type().equals("legacy") && slot == 15) {
            teleportHallOfFame(player);
        }
    }

    @EventHandler
    public void onHallBreak(BlockBreakEvent event) {
        if (isHallOfFame(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cHall of Fame is protected.");
        }
    }

    @EventHandler
    public void onHallPlace(BlockPlaceEvent event) {
        if (isHallOfFame(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.msg(event.getPlayer(), "&cHall of Fame is protected.");
        }
    }

    @EventHandler
    public void onHallMobSpawn(CreatureSpawnEvent event) {
        if (isHallOfFame(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean collection(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length == 0) {
            openCollection(player);
            return true;
        }
        int found = 0;
        for (String relic : RELICS) {
            if (hasRelic(player.getUniqueId(), relic)) {
                found++;
            }
        }
        Text.msg(player, "&dCollection Log: &f" + found + "/" + RELICS.size() + " &7(" + (found * 100 / RELICS.size()) + "%)");
        for (String relic : RELICS) {
            Text.msg(player, (hasRelic(player.getUniqueId(), relic) ? "&a[FOUND] " : "&c[MISSING] ") + displayRelic(relic));
        }
        return true;
    }

    private boolean contracts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
            return progression(sender, prepend("config", java.util.Arrays.copyOfRange(args, 1, args.length)));
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("claim")) {
            if (args.length < 2) {
                Text.msg(player, "&cGebruik: /contracts claim <id>");
                return true;
            }
            return claimContract(player, args[1].toLowerCase(Locale.ROOT));
        }
        openContracts(player);
        if (args.length == 0) {
            return true;
        }
        Text.msg(player, "&6High Risk Contracts in deze rotatie:");
        for (ContractDef contract : activeContracts(player)) {
            contractLine(player, contract);
        }
        Text.msg(player, "&7Claim met &f/contracts claim <id>&7.");
        return true;
    }

    private boolean orders(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("deliver")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cAlleen players.");
                return true;
            }
            if (args.length < 3) {
                Text.msg(player, "&cGebruik: /orders deliver <material> <amount>");
                return true;
            }
            Material material = material(args[1]);
            int amount = parseInt(args[2], 0);
            if (material == null || amount <= 0) {
                Text.msg(player, "&cOngeldige material/amount.");
                return true;
            }
            return deliverOrder(player, material, amount);
        }
        if (sender instanceof Player player && args.length == 0) {
            openOrders(player);
            return true;
        }
        Text.msg(sender, "&aResource Orders &7(new rotation in &f" + orderTimeLeft() + "&7):");
        for (int i = 0; i < 3; i++) {
            Material material = Material.valueOf(data.getString("orders." + i + ".material", "IRON_INGOT"));
            int remaining = data.getInt("orders." + i + ".remaining", 0);
            double price = orderPrice(data.getDouble("orders." + i + ".price", 1.0D));
            Text.msg(sender, "&7" + material.name().toLowerCase(Locale.ROOT) + ": &f" + remaining + " left &7- &a$" + format(price) + " each");
        }
        return true;
    }

    private boolean login(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        String today = LocalDate.now().toString();
        String last = data.getString("login.last." + player.getUniqueId(), "");
        if (today.equals(last)) {
            Text.msg(player, "&cJe hebt je daily reward vandaag al geclaimd.");
            return true;
        }
        int streak = LocalDate.now().minusDays(1).toString().equals(last) ? data.getInt("login.streak." + player.getUniqueId(), 0) + 1 : 1;
        data.set("login.last." + player.getUniqueId(), today);
        data.set("login.streak." + player.getUniqueId(), streak);
        add("fragments." + player.getUniqueId(), 5 + streak);
        if (streak % 7 == 0) {
            add("treasureKeys." + player.getUniqueId(), 1);
        }
        data.save();
        Text.msg(player, "&aDaily streak: &f" + streak + " &7(+fragments" + (streak % 7 == 0 ? ", +treasure key" : "") + ")");
        return true;
    }

    private boolean explorer(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            Text.msg(sender, "&bExplorer top:");
            int index = 1;
            for (Map.Entry<UUID, Double> entry : topDouble("explorer.distance.", 10)) {
                Text.msg(sender, "&7#" + index++ + " &f" + Bukkit.getOfflinePlayer(entry.getKey()).getName() + " &7- &b" + Math.round(entry.getValue()) + " blocks");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cGebruik: /explorer top");
            return true;
        }
        Text.msg(player, "&bExplorer distance: &f" + Math.round(data.getDouble("explorer.distance." + player.getUniqueId(), 0.0D)) + " blocks");
        return true;
    }

    private boolean legacy(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("hof")) {
            if (sender instanceof Player player) {
                teleportHallOfFame(player);
            } else {
                Text.msg(sender, "&cAlleen players.");
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("purgehof")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.progression.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            clearHallSnapshots();
            purgeHallOfFameWorld();
            ensureHallOfFame();
            data.save();
            Text.msg(sender, "&aHall of Fame volledig opgeschoond.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("rebuild")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.progression.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            rebuildHallOfFame();
            Text.msg(sender, "&aHall of Fame opnieuw opgebouwd.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("delete")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.progression.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            if (args.length < 2) {
                Text.msg(sender, "&cGebruik: /legacy delete <season>");
                return true;
            }
            int season = parseInt(args[1], -1);
            if (season < 0 || !deleteHallSeason(season)) {
                Text.msg(sender, "&cNo HOF snapshot found for season " + args[1] + ".");
                return true;
            }
            rebuildHallOfFame();
            Text.msg(sender, "&aSeason &f" + season + " &auit de Hall of Fame verwijderd.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("snapshot")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.progression.admin")) {
                Text.msg(sender, "&cGeen permissie.");
                return true;
            }
            int season = args.length >= 2 ? parseInt(args[1], data.getInt("hof.count", 0) + 1) : data.getInt("hof.count", 0) + 1;
            int index = data.getInt("hof.count", 0);
            List<HallChampion> champions = hallChampions();
            data.set("hof.count", index + 1);
            data.set("hof." + index + ".season", season);
            data.set("hof." + index + ".champions", champions.size());
            for (int i = 0; i < champions.size(); i++) {
                HallChampion champion = champions.get(i);
                data.set("hof." + index + ".champion." + i + ".uuid", champion.id());
                data.set("hof." + index + ".champion." + i + ".name", champion.name());
                data.set("hof." + index + ".champion." + i + ".roles", String.join(" | ", champion.roles()));
            }
            data.set("hof." + index + ".date", LocalDate.now().toString());
            data.save();
            rebuildHallOfFame();
            Text.msg(sender, "&aHall of Fame snapshot for season &f" + season + " &aopgeslagen. Eenmaal in HOF, altijd in HOF.");
            return true;
        }
        if (sender instanceof Player player) {
            openLegacy(player);
            return true;
        }
        Text.msg(sender, "&6Legacy Records:");
        Text.msg(sender, "&7Top kills: &f" + topName("legacy.kills."));
        Text.msg(sender, "&7Top deaths: &f" + topName("legacy.deaths."));
        Text.msg(sender, "&7Top bounty claims: &f" + topName("legacy.bountyClaims."));
        Text.msg(sender, "&7Top explorer: &f" + topNameDouble("explorer.distance."));
        HeartService hearts = MitchSMP.hearts();
        if (hearts != null && !hearts.topHearts(1).isEmpty()) {
            Map.Entry<UUID, Integer> top = hearts.topHearts(1).get(0);
            Text.msg(sender, "&7Highest hearts: &f" + Bukkit.getOfflinePlayer(top.getKey()).getName() + " - " + top.getValue());
        }
        return true;
    }

    private boolean relicCommand(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.progression.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            Text.msg(sender, "&cGebruik: /relic give <player> <id>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        String relic = args[2].toLowerCase(Locale.ROOT);
        if (target == null || !RELICS.contains(relic)) {
            Text.msg(sender, "&cSpeler of relic ongeldig.");
            return true;
        }
        target.getInventory().addItem(relicItem(relic));
        Text.msg(sender, "&aRelic granted.");
        return true;
    }

    private boolean progression(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.progression.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("config"))) {
            Text.msg(sender, "&6Progression tuning:");
            for (String key : List.of("economyBaseline", "economyScaleMax", "contractDurationMinutes", "orderDurationMinutes", "contractMoneyMultiplier", "orderMoneyMultiplier", "relicChance", "eliteRelicChance")) {
                Text.msg(sender, "&7" + key + ": &f" + setting(key, defaultSetting(key)));
            }
            Text.msg(sender, "&7Economy scale nu: &f" + format(economyScale()) + "x");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("overall")) {
            int removed = 0;
            for (String key : new ArrayList<>(data.keys())) {
                if (key.startsWith("legacy.") || key.startsWith("explorer.distance.")) {
                    data.set(key, null);
                    removed++;
                }
            }
            data.save();
            Text.msg(sender, "&aProgression overall stats gereset. Verwijderd: &f" + removed + " &akeys.");
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("config")) {
            Text.msg(sender, "&cGebruik: /progression config <key> <value> of /progression reset overall");
            return true;
        }
        try {
            double value = Math.max(0.0D, Double.parseDouble(args[2]));
            data.set("setting." + args[1], value);
            data.save();
            Text.msg(sender, "&aProgression setting &f" + args[1] + " &agezet naar &f" + value + "&a.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue moet een nummer zijn.");
        }
        return true;
    }

    private void openCollection(Player player) {
        ProgressionMenu holder = new ProgressionMenu("collection");
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Collection Log"));
        holder.inventory(inventory);
        int[] slots = {10, 11, 12, 13, 14};
        for (int i = 0; i < RELICS.size(); i++) {
            String relic = RELICS.get(i);
            boolean found = hasRelic(player.getUniqueId(), relic);
            inventory.setItem(slots[i], button(relicMaterial(relic), displayRelic(relic), List.of(
                found ? "&aUnlocked" : "&cMissing",
                "&7Relic collection item.",
                "&7Completion: &f" + collectionPercent(player) + "%"
            )));
        }
        player.openInventory(inventory);
    }

    private void openContracts(Player player) {
        ProgressionMenu holder = new ProgressionMenu("contracts");
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8High Risk Contracts"));
        holder.inventory(inventory);
        fillContractsInventory(player, inventory, holder);
        player.openInventory(inventory);
    }

    private void fillContractsInventory(Player player, Inventory inventory, ProgressionMenu holder) {
        holder.clearContracts();
        for (int slot : CONTRACT_SLOTS) {
            inventory.setItem(slot, null);
        }
        List<ContractDef> active = activeContracts(player);
        for (int i = 0; i < active.size() && i < CONTRACT_SLOTS.length; i++) {
            ContractDef contract = active.get(i);
            holder.contract(CONTRACT_SLOTS[i], contract.id());
            inventory.setItem(CONTRACT_SLOTS[i], contractIcon(player, contract));
        }
        inventory.setItem(49, button(Material.PAPER, "&eContract Slots", List.of(
            "&7Jouw actieve slots: &f" + active.size(),
            "&7Default 3, VIP 4, MVP 5, Legend 6.",
            "&7Reroll over: &f" + contractTimeLeft(player)
        )));
    }

    private void refreshProgressionMenus() {
        ensureDaily();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = openTopInventory(player);
            if (inventory == null || !(inventory.getHolder() instanceof ProgressionMenu holder)) {
                continue;
            }
            if (holder.type().equals("contracts")) {
                fillContractsInventory(player, inventory, holder);
            } else if (holder.type().equals("orders")) {
                fillOrdersInventory(player, inventory, holder);
            }
        }
    }

    private Inventory openTopInventory(Player player) {
        try {
            Object view = player.getClass().getMethod("getOpenInventory").invoke(player);
            Object top = view.getClass().getMethod("getTopInventory").invoke(view);
            return top instanceof Inventory inventory ? inventory : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private ItemStack contractIcon(Player player, ContractDef contract) {
        double progress = data.getDouble(contractKey(player, contract.id()), 0.0D);
        boolean done = data.contains(contractDoneKey(player, contract.id()));
        return button(contract.icon(), done ? "&a" + contract.label() : "&e" + contract.label(), List.of(
            "&7ID: &f" + contract.id(),
            "&7Progress: &f" + Math.min(contract.target(), (int) progress) + "/" + contract.target(),
            "&7Reward: &a$" + format(contractMoney(contract.reward())),
            "&7Reroll over: &f" + contractTimeLeft(player),
            done ? "&aAl geclaimd." : "&eKlik om te claimen."
        ));
    }

    private void openOrders(Player player) {
        ProgressionMenu holder = new ProgressionMenu("orders");
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Resource Orders"));
        holder.inventory(inventory);
        fillOrdersInventory(player, inventory, holder);
        player.openInventory(inventory);
    }

    private void fillOrdersInventory(Player player, Inventory inventory, ProgressionMenu holder) {
        int[] slots = {11, 13, 15};
        for (int i = 0; i < 3; i++) {
            Material material = Material.valueOf(data.getString("orders." + i + ".material", "IRON_INGOT"));
            holder.material(slots[i], material);
            int remaining = data.getInt("orders." + i + ".remaining", 0);
            double price = orderPrice(data.getDouble("orders." + i + ".price", 1.0D));
            inventory.setItem(slots[i], button(material, "&a" + pretty(material), List.of(
                "&7Nog nodig: &f" + remaining,
                "&7Jij hebt: &f" + countItems(player, material),
                "&7Price/stuk: &a$" + format(price),
                "&7New rotation in: &f" + orderTimeLeft(),
                "&eKlik om zoveel mogelijk te leveren."
            )));
        }
        inventory.setItem(22, button(Material.CLOCK, "&6Order Rotation", List.of(
            "&7New orders in: &f" + orderTimeLeft(),
            "&7Rotation length: &f" + Math.round(setting("orderDurationMinutes", defaultSetting("orderDurationMinutes"))) + " minutes",
            "&7Demand reacts to the active server economy."
        )));
    }

    private void openLegacy(Player player) {
        ProgressionMenu holder = new ProgressionMenu("legacy");
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Legacy Records"));
        holder.inventory(inventory);
        inventory.setItem(10, button(Material.NETHERITE_SWORD, "&6Top kills", List.of("&f" + topName("legacy.kills."))));
        inventory.setItem(11, button(Material.TOTEM_OF_UNDYING, "&6Top bounty claims", List.of("&f" + topName("legacy.bountyClaims."))));
        inventory.setItem(12, button(Material.COMPASS, "&6Top explorer", List.of("&f" + topNameDouble("explorer.distance."))));
        inventory.setItem(13, button(Material.EMERALD, "&6Richest", List.of("&f" + topRichest())));
        inventory.setItem(15, button(Material.GOLD_BLOCK, "&eHall of Fame", List.of("&7Klik om te bezoeken.", "&7Eenmaal in HOF, altijd in HOF.")));
        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private int collectionPercent(Player player) {
        int found = 0;
        for (String relic : RELICS) {
            if (hasRelic(player.getUniqueId(), relic)) {
                found++;
            }
        }
        return found * 100 / RELICS.size();
    }

    private double contractMoney(double base) {
        return base * setting("contractMoneyMultiplier", 1.0D) * economyScale();
    }

    private double orderPrice(double base) {
        return base * setting("orderMoneyMultiplier", 1.0D) * economyScale();
    }

    private double economyScale() {
        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            return 1.0D;
        }
        List<Map.Entry<UUID, Double>> top = economy.topBalances(250);
        if (top.isEmpty()) {
            return 1.0D;
        }
        double average = top.stream().mapToDouble(Map.Entry::getValue).sum() / top.size();
        double richest = top.get(0).getValue();
        double pressure = Math.max(average, richest * 0.25D);
        double baseline = Math.max(1.0D, setting("economyBaseline", 5_000.0D));
        double scale = Math.sqrt(Math.max(1.0D, pressure / baseline));
        double cap = setting("economyScaleMax", 0.0D);
        return cap <= 0.0D ? Math.max(1.0D, scale) : Math.max(1.0D, Math.min(cap, scale));
    }

    private double setting(String key, double fallback) {
        return data.getDouble("setting." + key, fallback);
    }

    private double defaultSetting(String key) {
        return switch (key) {
            case "economyBaseline" -> 5_000.0D;
            case "economyScaleMax" -> 0.0D;
            case "contractDurationMinutes" -> 120.0D;
            case "orderDurationMinutes" -> 360.0D;
            case "contractMoneyMultiplier" -> 1.0D;
            case "orderMoneyMultiplier" -> 1.0D;
            case "relicChance" -> 0.006D;
            case "eliteRelicChance" -> 0.018D;
            default -> 0.0D;
        };
    }

    private int countItems(Player player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    private int orderRemaining(Material material) {
        for (int i = 0; i < 3; i++) {
            if (material.name().equals(data.getString("orders." + i + ".material", ""))) {
                return data.getInt("orders." + i + ".remaining", 0);
            }
        }
        return 0;
    }

    private String[] prepend(String first, String[] rest) {
        String[] result = new String[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void ensureDaily() {
        long cycle = orderCycle();
        if (data.getLong("orders.cycle", Long.MIN_VALUE) == cycle && data.contains("orders.0.material")) {
            return;
        }
        data.set("orders.cycle", cycle);
        data.set("orders.generatedAt", System.currentTimeMillis());
        java.util.Random rotationRandom = new java.util.Random(cycle ^ 0xB100DB0DL);
        List<Material> candidates = new ArrayList<>(ORDER_MATERIALS);
        Collections.shuffle(candidates, rotationRandom);
        EconomyWatchService watch = MitchSMP.economyWatch();
        Map<Material, Double> scores = new LinkedHashMap<>();
        for (Material material : candidates) {
            double stock = watch == null ? 0.0D : Math.max(0, watch.onlineStock(material));
            double value = watch == null ? fallbackOrderPrice(material) : Math.max(0.05D, watch.quickSellPrice(material));
            double scarcity = watch == null ? 1.0D : watch.scarcityMultiplier(material);
            double marketPressure = Math.log1p(stock * Math.max(0.10D, value)) * 0.70D;
            double surplusPressure = Math.max(0.0D, 2.5D - Math.min(2.5D, scarcity)) * 1.5D;
            scores.put(material, rotationRandom.nextDouble() * 6.0D + marketPressure + surplusPressure);
        }
        candidates.sort(Comparator.comparingDouble((Material material) -> scores.getOrDefault(material, 0.0D)).reversed());
        for (int i = 0; i < 3; i++) {
            Material material = candidates.get(i);
            int stock = watch == null ? 0 : Math.max(0, watch.onlineStock(material));
            int baseAmount = baseOrderAmount(material);
            int stockAmount = stock <= 0 ? baseAmount : Math.max(baseAmount / 2, Math.min(baseAmount * 3, stock / 3));
            int amount = Math.max(1, (int) Math.round(stockAmount * (0.85D + rotationRandom.nextDouble() * 0.30D)));
            double marketPrice = watch == null ? fallbackOrderPrice(material) : Math.max(0.05D, watch.quickSellPrice(material));
            double price = marketPrice * (1.08D + rotationRandom.nextDouble() * 0.14D);
            data.set("orders." + i + ".material", material.name());
            data.set("orders." + i + ".remaining", amount);
            data.set("orders." + i + ".price", price);
        }
        data.save();
    }

    private long orderCycle() {
        return System.currentTimeMillis() / orderDurationMillis();
    }

    private long orderDurationMillis() {
        long minutes = Math.max(5L, Math.round(setting("orderDurationMinutes", defaultSetting("orderDurationMinutes"))));
        return minutes * 60_000L;
    }

    private String orderTimeLeft() {
        long duration = orderDurationMillis();
        long left = ((orderCycle() + 1L) * duration) - System.currentTimeMillis();
        return formatDuration(Math.max(0L, left));
    }

    private int baseOrderAmount(Material material) {
        return switch (material) {
            case TOTEM_OF_UNDYING -> 4;
            case DIAMOND, EMERALD, BLAZE_ROD -> 48;
            case ENDER_PEARL, SLIME_BALL -> 96;
            case IRON_INGOT, COPPER_INGOT, GOLD_INGOT, GUNPOWDER -> 192;
            case COBBLESTONE, OAK_LOG, WHEAT, CARROT, REDSTONE, LAPIS_LAZULI, COAL, BONE, STRING, QUARTZ -> 384;
            default -> 128;
        };
    }

    private double fallbackOrderPrice(Material material) {
        return switch (material) {
            case DIAMOND -> 35.0D;
            case EMERALD -> 18.0D;
            case TOTEM_OF_UNDYING -> 250.0D;
            case BLAZE_ROD, ENDER_PEARL, SLIME_BALL -> 12.0D;
            case REDSTONE, GUNPOWDER, LAPIS_LAZULI -> 5.0D;
            case GOLD_INGOT -> 10.0D;
            case IRON_INGOT, COPPER_INGOT -> 6.0D;
            default -> 1.5D;
        };
    }

    private boolean deliverOrder(Player player, Material material, int amount) {
        int slot = -1;
        for (int i = 0; i < 3; i++) {
            if (material.name().equals(data.getString("orders." + i + ".material", ""))) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            Text.msg(player, "&cDit item staat vandaag niet op de order list.");
            return true;
        }
        int remaining = data.getInt("orders." + slot + ".remaining", 0);
        int take = Math.min(amount, remaining);
        if (take <= 0) {
            Text.msg(player, "&cDeze order is al voltooid.");
            return true;
        }
        if (!removeItems(player, material, take)) {
            Text.msg(player, "&cJe hebt niet genoeg items.");
            return true;
        }
        data.set("orders." + slot + ".remaining", remaining - take);
        data.save();
        EconomyService economy = MitchSMP.economy();
        double reward = take * orderPrice(data.getDouble("orders." + slot + ".price", 1.0D));
        reward *= 1.0D + Math.min(10, skillPerk(player, "order_runner")) * 0.015D;
        if (economy != null) {
            economy.deposit(player.getUniqueId(), reward, "resource order");
        }
        Text.msg(player, "&aOrder geleverd: &f" + take + " " + material.name().toLowerCase(Locale.ROOT) + " &7+$" + format(reward));
        return true;
    }

    private boolean removeItems(Player player, Material material, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int available = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                available += item.getAmount();
            }
        }
        if (available < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemStack item : contents) {
            if (item == null || item.getType() != material || remaining <= 0) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    private boolean claimContract(Player player, String id) {
        ContractDef contract = contract(id);
        if (contract == null || activeContracts(player).stream().noneMatch(active -> active.id().equals(contract.id()))) {
            Text.msg(player, "&cUnknown contract.");
            return true;
        }
        if (data.contains(contractDoneKey(player, id))) {
            Text.msg(player, "&cDit contract is in deze rotatie al geclaimd. Reroll over: &f" + contractTimeLeft(player));
            return true;
        }
        double progress = data.getDouble(contractKey(player, id), 0.0D);
        if (progress < contract.target()) {
            Text.msg(player, "&cNog niet klaar. Progress: &f" + Math.round(progress) + "/" + contract.target());
            return true;
        }
        data.set(contractDoneKey(player, id), true);
        data.save();
        EconomyService economy = MitchSMP.economy();
        double reward = contractMoney(contract.reward()) * (1.0D + Math.min(10, skillPerk(player, "contract_broker")) * 0.02D);
        if (economy != null) {
            economy.deposit(player.getUniqueId(), reward, "contract " + id);
        }
        Text.msg(player, "&aContract claimed: &f$" + format(reward));
        return true;
    }

    private void contractLine(Player player, ContractDef contract) {
        double progress = data.getDouble(contractKey(player, contract.id()), 0.0D);
        boolean done = data.contains(contractDoneKey(player, contract.id()));
        Text.msg(player, (done ? "&a[DONE] " : "&e[OPEN] ") + "&f" + contract.id() + " &7- " + contract.label() + " &8[" + Math.min(contract.target(), (int) progress) + "/" + contract.target() + "] &a$" + format(contractMoney(contract.reward())) + " &7| reroll: &f" + contractTimeLeft(player));
    }

    private int skillPerk(Player player, String key) {
        if (player == null || key == null) {
            return 0;
        }
        try {
            SkillService skills = MitchSMP.skills();
            return skills == null ? 0 : skills.getPerkLevel(player.getUniqueId(), key);
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private void progressContracts(Player player, ContractType type, double amount) {
        progressContracts(player, type, amount, true);
    }

    private void progressContracts(Player player, ContractType type, double amount, boolean saveImmediately) {
        if (player == null || type == null || amount <= 0.0D) {
            return;
        }
        boolean changed = false;
        for (ContractDef contract : activeContracts(player)) {
            if (contract.type() != type || data.contains(contractDoneKey(player, contract.id()))) {
                continue;
            }
            String key = contractKey(player, contract.id());
            double current = data.getDouble(key, 0.0D);
            data.set(key, Math.min(contract.target(), current + amount));
            changed = true;
        }
        if (changed) {
            if (saveImmediately) {
                data.save();
            } else {
                progressionDirty = true;
            }
        }
    }

    private ContractType oreContractType(Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name();
        if (name.contains("DIAMOND_ORE")) {
            return ContractType.DIAMOND_ORE;
        }
        if (name.contains("EMERALD_ORE")) {
            return ContractType.EMERALD_ORE;
        }
        if (material == Material.ANCIENT_DEBRIS) {
            return ContractType.ANCIENT_DEBRIS;
        }
        if (name.contains("GOLD_ORE")) {
            return ContractType.GOLD_ORE;
        }
        if (name.contains("REDSTONE_ORE")) {
            return ContractType.REDSTONE_ORE;
        }
        if (name.contains("IRON_ORE")) {
            return ContractType.IRON_ORE;
        }
        return null;
    }

    private boolean isLog(Material material) {
        return material != null && (material.name().contains("_LOG") || material.name().endsWith("_WOOD"));
    }

    private boolean isMatureCrop(org.bukkit.block.Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        if (!List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART").contains(name)) {
            return false;
        }
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            int age = ((Number) data.getClass().getMethod("getAge").invoke(data)).intValue();
            int maximum = ((Number) data.getClass().getMethod("getMaximumAge").invoke(data)).intValue();
            return age >= maximum;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }

    private List<ContractDef> activeContracts(Player player) {
        long cycle = contractCycle(player);
        int limit = contractLimit(player);
        ContractRotation cached = contractRotationCache.get(player.getUniqueId());
        if (cached != null && cached.cycle() == cycle && cached.limit() == limit) {
            return cached.contracts();
        }
        List<ContractDef> pool = new ArrayList<>(CONTRACTS);
        long seed = (cycle + ":" + player.getUniqueId()).hashCode();
        Collections.shuffle(pool, new java.util.Random(seed));
        List<ContractDef> rotation = List.copyOf(pool.subList(0, Math.min(limit, pool.size())));
        contractRotationCache.put(player.getUniqueId(), new ContractRotation(cycle, limit, rotation));
        return rotation;
    }

    private long contractCycle(Player player) {
        return System.currentTimeMillis() / contractDurationMillis();
    }

    private long contractDurationMillis() {
        long minutes = Math.max(5L, Math.round(setting("contractDurationMinutes", defaultSetting("contractDurationMinutes"))));
        return minutes * 60_000L;
    }

    private String contractTimeLeft(Player player) {
        long duration = contractDurationMillis();
        long left = ((contractCycle(player) + 1L) * duration) - System.currentTimeMillis();
        return formatDuration(Math.max(0L, left));
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
    }

    private int contractLimit(Player player) {
        MitchRank rank = MitchSMP.ranks().getRank(player.getUniqueId());
        if (rank.inherits(MitchRank.LEGEND) || rank.staff()) {
            return 6;
        }
        if (rank.inherits(MitchRank.MVP)) {
            return 5;
        }
        if (rank.inherits(MitchRank.VIP)) {
            return 4;
        }
        return 3;
    }

    private ContractDef contract(String id) {
        for (ContractDef contract : CONTRACTS) {
            if (contract.id().equalsIgnoreCase(id)) {
                return contract;
            }
        }
        return null;
    }

    private String contractKey(Player player, String id) {
        return "contracts." + contractCycle(player) + "." + player.getUniqueId() + "." + id;
    }

    private String contractDoneKey(Player player, String id) {
        return contractKey(player, id) + ".done";
    }

    private ItemStack relicItem(String id) {
        ItemStack item = new ItemStack(relicMaterial(id));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(displayRelic(id)));
            meta.setLore(List.of(Text.color("&7Relic Collection item."), Text.color("&8Right-click om te registreren.")));
            meta.getPersistentDataContainer().set(relicKey(id), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private NamespacedKey relicKey(String id) {
        return new NamespacedKey(this, "relic_" + id);
    }

    private Material relicMaterial(String id) {
        return switch (id) {
            case "crimson_crown" -> Material.NETHER_STAR;
            case "ancient_compass" -> Material.COMPASS;
            case "kings_ring" -> Material.GOLD_INGOT;
            case "forgotten_coin" -> Material.SUNFLOWER;
            default -> Material.ECHO_SHARD;
        };
    }

    private String displayRelic(String id) {
        return switch (id) {
            case "crimson_crown" -> "&cCrimson Crown";
            case "ancient_compass" -> "&bAncient Compass";
            case "kings_ring" -> "&6King's Ring";
            case "forgotten_coin" -> "&eForgotten Coin";
            case "blood_relic" -> "&4Blood Relic";
            default -> id;
        };
    }

    private boolean unlockRelic(Player player, String relic) {
        String key = "collection." + player.getUniqueId() + "." + relic;
        if (data.contains(key)) {
            Text.msg(player, "&7Deze relic staat al in je collection log.");
            return false;
        }
        data.set(key, true);
        data.save();
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&d" + player.getName() + " vond relic " + displayRelic(relic) + "&d."));
        return true;
    }

    private void consumeRegisteredItem(PlayerInteractEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        try {
            Object hand = event.getClass().getMethod("getHand").invoke(event);
            if (hand != null && hand.toString().equalsIgnoreCase("OFF_HAND")) {
                event.getPlayer().getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                return;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }

    private boolean hasRelic(UUID id, String relic) {
        return data.contains("collection." + id + "." + relic);
    }

    private int add(String key, int amount) {
        int value = data.getInt(key, 0) + amount;
        data.set(key, value);
        data.save();
        return value;
    }

    private double addDouble(String key, double amount) {
        double value = data.getDouble(key, 0.0D) + amount;
        data.set(key, value);
        data.save();
        return value;
    }

    private void addDoubleBuffered(String key, double amount) {
        data.set(key, data.getDouble(key, 0.0D) + amount);
        progressionDirty = true;
    }

    private void flushProgression() {
        if (!progressionDirty) {
            return;
        }
        progressionDirty = false;
        data.save();
    }

    private List<Map.Entry<UUID, Double>> topDouble(String prefix, int limit) {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>();
        for (String key : data.keys()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            try {
                entries.add(Map.entry(UUID.fromString(key.substring(prefix.length())), data.getDouble(key, 0.0D)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        entries.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        return entries.stream().limit(limit).toList();
    }

    private List<Map.Entry<UUID, Integer>> topInt(String prefix, int limit) {
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>();
        for (String key : data.keys()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            try {
                entries.add(Map.entry(UUID.fromString(key.substring(prefix.length())), data.getInt(key, 0)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        entries.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()));
        return entries.stream().limit(limit).toList();
    }

    private String topName(String prefix) {
        List<Map.Entry<UUID, Integer>> top = topInt(prefix, 1);
        if (top.isEmpty()) {
            return "nog niemand";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(top.get(0).getKey());
        return player.getName() + " - " + top.get(0).getValue();
    }

    private String topNameDouble(String prefix) {
        List<Map.Entry<UUID, Double>> top = topDouble(prefix, 1);
        if (top.isEmpty()) {
            return "nog niemand";
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(top.get(0).getKey());
        return player.getName() + " - " + Math.round(top.get(0).getValue());
    }

    private String topRichest() {
        EconomyService economy = MitchSMP.economy();
        if (economy == null || economy.topBalances(1).isEmpty()) {
            return "nog niemand";
        }
        Map.Entry<UUID, Double> top = economy.topBalances(1).get(0);
        return Bukkit.getOfflinePlayer(top.getKey()).getName() + " - $" + format(top.getValue());
    }

    private List<HallChampion> hallChampions() {
        Map<UUID, List<String>> roles = new LinkedHashMap<>();
        addTopIntChampion(roles, "legacy.kills.", "Top kills", " kills");
        addTopIntChampion(roles, "legacy.bountyClaims.", "Top bounty claims", " claims");
        addTopDoubleChampion(roles, "explorer.distance.", "Top explorer", " blocks");
        EconomyService economy = MitchSMP.economy();
        if (economy != null && !economy.topBalances(1).isEmpty()) {
            Map.Entry<UUID, Double> top = economy.topBalances(1).get(0);
            addChampion(roles, top.getKey(), "Richest $" + format(top.getValue()));
        }
        HeartService hearts = MitchSMP.hearts();
        if (hearts != null && !hearts.topHearts(1).isEmpty()) {
            Map.Entry<UUID, Integer> top = hearts.topHearts(1).get(0);
            addChampion(roles, top.getKey(), "Highest hearts " + top.getValue());
        }
        List<HallChampion> champions = new ArrayList<>();
        for (Map.Entry<UUID, List<String>> entry : roles.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            champions.add(new HallChampion(entry.getKey(), player.getName() == null ? entry.getKey().toString().substring(0, 8) : player.getName(), entry.getValue()));
        }
        return champions;
    }

    private void addTopIntChampion(Map<UUID, List<String>> roles, String prefix, String title, String suffix) {
        List<Map.Entry<UUID, Integer>> top = topInt(prefix, 1);
        if (!top.isEmpty()) {
            Map.Entry<UUID, Integer> entry = top.get(0);
            addChampion(roles, entry.getKey(), title + " " + entry.getValue() + suffix);
        }
    }

    private void addTopDoubleChampion(Map<UUID, List<String>> roles, String prefix, String title, String suffix) {
        List<Map.Entry<UUID, Double>> top = topDouble(prefix, 1);
        if (!top.isEmpty()) {
            Map.Entry<UUID, Double> entry = top.get(0);
            addChampion(roles, entry.getKey(), title + " " + Math.round(entry.getValue()) + suffix);
        }
    }

    private void addChampion(Map<UUID, List<String>> roles, UUID id, String role) {
        roles.computeIfAbsent(id, ignored -> new ArrayList<>()).add(role);
    }

    private List<HallSnapshot> hallSnapshots() {
        List<HallSnapshot> snapshots = new ArrayList<>();
        int count = data.getInt("hof.count", 0);
        for (int index = 0; index < count; index++) {
            if (!data.contains("hof." + index + ".season")) {
                continue;
            }
            int season = data.getInt("hof." + index + ".season", index + 1);
            int championCount = data.getInt("hof." + index + ".champions", 0);
            List<HallChampion> champions = new ArrayList<>();
            for (int i = 0; i < championCount; i++) {
                String rawId = data.getString("hof." + index + ".champion." + i + ".uuid", "");
                String name = data.getString("hof." + index + ".champion." + i + ".name", "Unknown");
                String rawRoles = data.getString("hof." + index + ".champion." + i + ".roles", "");
                try {
                    champions.add(new HallChampion(UUID.fromString(rawId), name, rawRoles.isBlank() ? List.of("Honored") : java.util.Arrays.asList(rawRoles.split(" \\| "))));
                } catch (IllegalArgumentException ignored) {
                }
            }
            snapshots.add(new HallSnapshot(season, data.getString("hof." + index + ".date", "?"), champions));
        }
        snapshots.sort(Comparator.comparingInt(HallSnapshot::season));
        return snapshots;
    }

    private List<String> legacySeasonNumbers() {
        return hallSnapshots().stream().map(snapshot -> String.valueOf(snapshot.season())).distinct().toList();
    }

    private boolean deleteHallSeason(int season) {
        List<HallSnapshot> remaining = hallSnapshots().stream()
            .filter(snapshot -> snapshot.season() != season)
            .toList();
        if (remaining.size() == hallSnapshots().size()) {
            return false;
        }
        clearHallSnapshots();
        writeHallSnapshots(remaining);
        data.save();
        return true;
    }

    private void writeHallSnapshots(List<HallSnapshot> snapshots) {
        data.set("hof.count", snapshots.size());
        for (int index = 0; index < snapshots.size(); index++) {
            HallSnapshot snapshot = snapshots.get(index);
            data.set("hof." + index + ".season", snapshot.season());
            data.set("hof." + index + ".date", snapshot.date());
            data.set("hof." + index + ".champions", snapshot.champions().size());
            for (int i = 0; i < snapshot.champions().size(); i++) {
                HallChampion champion = snapshot.champions().get(i);
                data.set("hof." + index + ".champion." + i + ".uuid", champion.id());
                data.set("hof." + index + ".champion." + i + ".name", champion.name());
                data.set("hof." + index + ".champion." + i + ".roles", String.join(" | ", champion.roles()));
            }
        }
    }

    private void clearHallSnapshots() {
        for (String key : new ArrayList<>(data.keys())) {
            if (key.startsWith("hof.")) {
                data.set(key, null);
            }
        }
    }

    private void ensureHallOfFame() {
        World world = hallWorld();
        if (world == null) {
            return;
        }
        int hallEndZ = Math.max(120, -60 + Math.max(1, hallSnapshots().size()) * 24 + 24);
        for (int x = -70; x <= 70; x++) {
            for (int y = 99; y <= 116; y++) {
                for (int z = -110; z <= hallEndZ + 20; z++) {
                    new Location(world, x, y, z).getBlock().setType(Material.AIR);
                }
            }
        }
        for (int x = -58; x <= 58; x++) {
            for (int z = -96; z <= hallEndZ; z++) {
                boolean border = Math.abs(x) == 58 || z == -96 || z == hallEndZ;
                boolean aisle = Math.abs(x) <= 3;
                boolean lamp = (x == -52 || x == 52 || Math.abs(x) == 5) && (z + 96) % 12 == 0;
                Material material = border ? Material.OBSIDIAN : aisle ? Material.GOLD_BLOCK : lamp ? Material.SEA_LANTERN : Material.QUARTZ_BLOCK;
                new Location(world, x, 100, z).getBlock().setType(material);
                if (border && (x + z) % 4 == 0) {
                    new Location(world, x, 101, z).getBlock().setType(Material.GOLD_BLOCK);
                }
            }
        }
        for (int z = -88; z <= hallEndZ - 4; z += 18) {
            for (int x : List.of(-54, 54)) {
                new Location(world, x, 101, z).getBlock().setType(Material.GOLD_BLOCK);
                new Location(world, x, 102, z).getBlock().setType(Material.SEA_LANTERN);
                new Location(world, x, 103, z).getBlock().setType(Material.GOLD_BLOCK);
            }
        }
        spawnLabel(world, 0.5D, 104.0D, -87.5D, "&6&lMITCHSMP HALL OF FAME");
        spawnLabel(world, 0.5D, 102.8D, -83.5D, "&fSeasons blijven hier bewaard. Verwijderen kan met &e/legacy delete <season>&f.");
    }

    private void teleportHallOfFame(Player player) {
        World world = hallWorld();
        if (world == null) {
            Text.msg(player, "&cHall of Fame world kon niet geladen worden.");
            return;
        }
        player.teleport(new Location(world, 0.5D, 102.0D, -90.5D, 0.0F, 0.0F));
        Text.msg(player, "&6Welkom in de Hall of Fame.");
    }

    private void rebuildHallOfFame() {
        purgeHallOfFameWorld();
        ensureHallOfFame();
        int index = 0;
        for (HallSnapshot snapshot : hallSnapshots()) {
            buildHallOfFameEntry(index++, snapshot);
        }
    }

    private void buildHallOfFameEntry(int index, HallSnapshot snapshot) {
        World world = hallWorld();
        if (world == null) {
            return;
        }
        int baseZ = -60 + index * 24;
        for (int x = -45; x <= 45; x++) {
            for (int z = baseZ - 7; z <= baseZ + 7; z++) {
                boolean edge = Math.abs(x) == 45 || z == baseZ - 7 || z == baseZ + 7;
                Material material = edge ? Material.GOLD_BLOCK : Material.QUARTZ_BLOCK;
                new Location(world, x, 101, z).getBlock().setType(material);
            }
        }
        spawnLabel(world, 0.5D, 104.0D, baseZ - 5.5D, "&6Season #" + snapshot.season() + " &7- &f" + snapshot.date());
        List<HallChampion> champions = snapshot.champions();
        if (champions.isEmpty()) {
            spawnLabel(world, 0.5D, 102.8D, baseZ + 0.5D, "&7Geen winnaars opgeslagen.");
            return;
        }
        int startX = -Math.min(30, (champions.size() - 1) * 8 / 2);
        for (int i = 0; i < champions.size(); i++) {
            HallChampion champion = champions.get(i);
            int x = startX + i * 12;
            new Location(world, x, 102, baseZ + 3).getBlock().setType(Material.GOLD_BLOCK);
            ArmorStand stand = (ArmorStand) world.spawnEntity(new Location(world, x + 0.5D, 103.0D, baseZ + 3.5D), EntityType.ARMOR_STAND);
            stand.setCustomName(Text.color("&6S" + snapshot.season() + " &f" + champion.name() + " &7- &e" + String.join(", ", champion.roles())));
            stand.setCustomNameVisible(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            if (stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                stand.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                stand.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                stand.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                stand.getEquipment().setItemInMainHand(new ItemStack(i == 0 ? Material.NETHERITE_SWORD : Material.GOLD_INGOT));
            }
        }
    }

    private void purgeHallOfFameWorld() {
        World world = hallWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private void spawnLabel(World world, double x, double y, double z, String text) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(new Location(world, x, y, z), EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setCustomName(Text.color(text));
        stand.setCustomNameVisible(true);
    }

    private World hallWorld() {
        World world = Bukkit.getWorld(HOF_WORLD);
        if (world != null) {
            return world;
        }
        try {
            Class<?> creatorClass = Class.forName("org.bukkit.WorldCreator");
            Constructor<?> constructor = creatorClass.getConstructor(String.class);
            Object creator = constructor.newInstance(HOF_WORLD);
            Class<?> generatorClass = Class.forName("org.bukkit.generator.ChunkGenerator");
            creatorClass.getMethod("generator", generatorClass).invoke(creator, new HallChunkGenerator());
            creatorClass.getMethod("generateStructures", boolean.class).invoke(creator, false);
            Bukkit.class.getMethod("createWorld", creatorClass).invoke(null, creator);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Could not create Hall of Fame world: " + exception.getMessage());
        }
        return Bukkit.getWorld(HOF_WORLD);
    }

    private boolean isHallOfFame(Location location) {
        return location != null && location.getWorld() != null && HOF_WORLD.equals(location.getWorld().getName());
    }

    private Material material(String input) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean isBedWarsWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return world.startsWith("bedwars_") || world.startsWith("bw_") || world.contains("bedwars") || world.startsWith("mitchtest_");
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static final class ProgressionMenu implements InventoryHolder {
        private final String type;
        private final java.util.Map<Integer, Material> materials = new java.util.HashMap<>();
        private final java.util.Map<Integer, String> contracts = new java.util.HashMap<>();
        private Inventory inventory;

        ProgressionMenu(String type) {
            this.type = type;
        }

        String type() {
            return type;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        void material(int slot, Material material) {
            materials.put(slot, material);
        }

        Material material(int slot) {
            return materials.get(slot);
        }

        void contract(int slot, String id) {
            contracts.put(slot, id);
        }

        String contract(int slot) {
            return contracts.get(slot);
        }

        void clearContracts() {
            contracts.clear();
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum ContractType {
        PLAYER_KILL,
        BOUNTY_KILL,
        TRAVEL,
        NETHER_TRAVEL,
        END_TRAVEL,
        DANGEROUS_LOOT,
        BOSS_KILL,
        MOB_KILL,
        NETHER_MOB_KILL,
        END_MOB_KILL,
        DIAMOND_ORE,
        EMERALD_ORE,
        ANCIENT_DEBRIS,
        GOLD_ORE,
        REDSTONE_ORE,
        IRON_ORE,
        LOG_BREAK,
        CROP_HARVEST,
        BLOCK_MINE,
        BLOCK_PLACE
    }

    private record ContractDef(String id, String label, int target, double reward, Material icon, ContractType type) {
    }

    private record ContractRotation(long cycle, int limit, List<ContractDef> contracts) {
    }

    private record HallChampion(UUID id, String name, List<String> roles) {
    }

    private record HallSnapshot(int season, String date, List<HallChampion> champions) {
    }

    private static final class HallChunkGenerator extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, 0.5D, 102.0D, 0.5D);
        }
    }
}



