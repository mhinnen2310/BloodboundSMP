package nl.mitchsmp.corruptedhearts;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import nl.mitchsmp.core.api.HeartService;
import nl.mitchsmp.core.api.CorruptedHeartService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CorruptedHeartsPlugin extends JavaPlugin implements Listener, TabCompleter, CorruptedHeartService {
    private static final List<String> CHANCE_KEYS = List.of(
        "simple_dungeon", "abandoned_mineshaft", "ancient_city", "stronghold", "jungle_temple",
        "desert_pyramid", "buried_treasure", "shipwreck_treasure", "woodland_mansion",
        "bastion_treasure", "ruined_portal", "end_city_treasure", "ancient_loot_structure"
    );
    private final Random random = new Random();
    private NamespacedKey key;
    private PropertiesFile config;

    @Override
    public void onEnable() {
        key = new NamespacedKey(this, "corrupted_heart");
        config = new PropertiesFile(getDataFolder().toPath().resolve("tuning.properties"));
        MitchSMP.registerService(CorruptedHeartService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("corruptedheart") != null) {
            getCommand("corruptedheart").setExecutor(this);
            getCommand("corruptedheart").setTabCompleter(this);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isCorruptedHeart(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (isBedWarsWorld(player)) {
            Text.msg(player, "&cCorrupted Hearts are disabled in BedWars.");
            return;
        }
        HeartService hearts = MitchSMP.hearts();
        if (hearts == null) {
            Text.msg(player, "&cLifesteal is niet geladen.");
            return;
        }
        int current = hearts.getHearts(player.getUniqueId());
        if (current >= 10) {
            Text.msg(player, "&cCorrupted Hearts werken alleen onder 10 hearts.");
            return;
        }
        hearts.setHearts(player.getUniqueId(), current + 1);
        item.setAmount(item.getAmount() - 1);
        Text.msg(player, "&5Your Corrupted Heart restored 1 heart. You now have &f" + (current + 1) + "&5 hearts.");
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        EntityType type = event.getEntityType();
        double chance = switch (type) {
            case ENDER_DRAGON, WITHER, WARDEN -> 0.25D;
            case EVOKER, ELDER_GUARDIAN -> 0.04D;
            default -> 0.002D;
        };
        if (random.nextDouble() <= chance) {
            event.getDrops().add(item(1));
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.isPlugin() || event.getLootTable() == null || event.getLootTable().getKey() == null) {
            return;
        }
        String lootTable = event.getLootTable().getKey().toString().toLowerCase(Locale.ROOT);
        double chance = structureChance(lootTable);
        if (chance <= 0.0D || random.nextDouble() > chance) {
            return;
        }
        event.getLoot().add(item(1));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.lifesteal.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("chance")) {
            return chanceCommand(sender, args);
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            Text.msg(sender, "&cGebruik: /corruptedheart give <player> [amount] of /corruptedheart chance [key] [percent]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(sender, "&cSpeler niet online.");
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
            }
        }
        target.getInventory().addItem(item(amount));
        Text.msg(sender, "&aCorrupted Heart granted.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Tab.complete(args[0], "give", "chance");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Tab.onlinePlayers(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("chance")) {
            return Tab.complete(args[1], CHANCE_KEYS);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Tab.amounts(args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("chance")) {
            return Tab.complete(args[2], "0.25", "0.5", "1", "2", "3", "5");
        }
        return List.of();
    }

    private ItemStack item(int amount) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&5Corrupted Heart"));
            meta.setLore(List.of(
                Text.color("&7Only works below 10 hearts."),
                Text.color("&8Right-click to restore 1 heart."),
                Text.color("&8Rarely found in ancient chests.")
            ));
            setVisualModel(meta, 910002, "corrupted_heart");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public ItemStack createHeart(int amount) {
        return item(Math.max(1, amount));
    }

    @Override
    public double getLootChancePercent(String lootKey) {
        String normalized = lootKey == null ? "" : lootKey.toLowerCase(Locale.ROOT);
        return CHANCE_KEYS.contains(normalized) ? chance(normalized, defaultPercent(normalized)) * 100.0D : 0.0D;
    }

    private double structureChance(String lootTable) {
        if (lootTable.contains("chests/simple_dungeon")) {
            return chance("simple_dungeon", 2.5D);
        }
        if (lootTable.contains("chests/abandoned_mineshaft")) {
            return chance("abandoned_mineshaft", 1.4D);
        }
        if (lootTable.contains("chests/ancient_city")) {
            return chance("ancient_city", 2.0D);
        }
        if (lootTable.contains("chests/stronghold")) {
            return chance("stronghold", 1.2D);
        }
        if (lootTable.contains("chests/jungle_temple")) {
            return chance("jungle_temple", 1.5D);
        }
        if (lootTable.contains("chests/desert_pyramid")) {
            return chance("desert_pyramid", 1.5D);
        }
        if (lootTable.contains("chests/buried_treasure")) {
            return chance("buried_treasure", 1.2D);
        }
        if (lootTable.contains("chests/shipwreck_treasure")) {
            return chance("shipwreck_treasure", 1.0D);
        }
        if (lootTable.contains("chests/woodland_mansion")) {
            return chance("woodland_mansion", 1.8D);
        }
        if (lootTable.contains("chests/bastion_treasure")) {
            return chance("bastion_treasure", 1.5D);
        }
        if (lootTable.contains("chests/ruined_portal")) {
            return chance("ruined_portal", 0.8D);
        }
        if (lootTable.contains("chests/end_city_treasure")) {
            return chance("end_city_treasure", 2.0D);
        }
        return 0.0D;
    }

    private boolean chanceCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            Text.msg(sender, "&5Corrupted Heart chest chances:");
            for (String key : CHANCE_KEYS) {
                Text.msg(sender, "&7" + key + ": &f" + percent(chance(key, defaultPercent(key))) + "%");
            }
            return true;
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /corruptedheart chance <key> <percent>");
            return true;
        }
        String chanceKey = args[1].toLowerCase(Locale.ROOT);
        if (!CHANCE_KEYS.contains(chanceKey)) {
            Text.msg(sender, "&cUnknown key. Use tab completion.");
            return true;
        }
        try {
            double percent = Math.max(0.0D, Math.min(25.0D, Double.parseDouble(args[2])));
            config.set("chance." + chanceKey, percent);
            config.save();
            Text.msg(sender, "&aChance &f" + chanceKey + " &agezet naar &f" + percent(percent / 100.0D) + "%&a.");
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cPercent moet een nummer zijn.");
        }
        return true;
    }

    private double chance(String key, double fallbackPercent) {
        return Math.max(0.0D, config.getDouble("chance." + key, fallbackPercent)) / 100.0D;
    }

    private double defaultPercent(String key) {
        return switch (key) {
            case "simple_dungeon" -> 2.5D;
            case "abandoned_mineshaft" -> 1.4D;
            case "ancient_city", "end_city_treasure" -> 2.0D;
            case "ancient_loot_structure" -> 12.0D;
            case "woodland_mansion" -> 1.8D;
            case "jungle_temple", "desert_pyramid", "bastion_treasure" -> 1.5D;
            case "stronghold", "buried_treasure" -> 1.2D;
            case "shipwreck_treasure" -> 1.0D;
            case "ruined_portal" -> 0.8D;
            default -> 0.0D;
        };
    }

    private String percent(double chance) {
        return String.format(Locale.US, "%.2f", chance * 100.0D);
    }

    private boolean isBedWarsWorld(Player player) {
        return player != null
            && player.getWorld() != null
            && player.getWorld().getName().toLowerCase(Locale.ROOT).startsWith("bedwars_");
    }

    @Override
    public boolean isCorruptedHeart(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return false;
        }
        setVisualModel(meta, 910002, "corrupted_heart");
        meta.setDisplayName(Text.color("&5Corrupted Heart"));
        meta.setLore(List.of(
            Text.color("&7Only works below 10 hearts."),
            Text.color("&8Right-click to restore 1 heart."),
            Text.color("&8Rarely found in ancient chests.")
        ));
        item.setItemMeta(meta);
        return true;
    }

    private void setVisualModel(ItemMeta meta, int value, String model) {
        try {
            meta.getClass().getMethod("setCustomModelData", Integer.class).invoke(meta, Integer.valueOf(value));
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            meta.getClass().getMethod("setItemModel", NamespacedKey.class).invoke(meta, new NamespacedKey("bloodbound", model));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }
}

