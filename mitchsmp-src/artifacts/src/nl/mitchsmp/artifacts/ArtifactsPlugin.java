package nl.mitchsmp.artifacts;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.BossShardService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ArtifactsPlugin extends JavaPlugin implements Listener, TabCompleter, BossShardService {
    private final Random random = new Random();
    private final Map<UUID, Long> pendingPhoenix = new HashMap<>();
    private NamespacedKey shardKey;
    private NamespacedKey phoenixKey;
    private NamespacedKey shardAtmKey;
    private NamespacedKey shardVaultKey;
    private PropertiesFile shopData;
    private PropertiesFile shardData;

    @Override
    public void onEnable() {
        shardKey = new NamespacedKey(this, "boss_shard");
        phoenixKey = new NamespacedKey(this, "phoenix_totem");
        shardAtmKey = new NamespacedKey(this, "shard_atm");
        shardVaultKey = new NamespacedKey(this, "shard_vault");
        shopData = new PropertiesFile(getDataFolder().toPath().resolve("opshop.properties"));
        shardData = new PropertiesFile(getDataFolder().toPath().resolve("shards.properties"));
        ensureShopDefaults();
        ensureShardDefaults();
        MitchSMP.registerService(BossShardService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String command : List.of("opshop", "opshopadmin", "bossshards", "shardatm", "shardvault")) {
            if (getCommand(command) != null) {
                getCommand(command).setExecutor(this);
                getCommand(command).setTabCompleter(this);
            }
        }
        Bukkit.getScheduler().runTaskTimer(this, this::vaultAlertTick, 100L, 200L);
    }

    @Override
    public void onDisable() {
        shopData.save();
        shardData.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("opshop")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cPlayers only.");
                return true;
            }
            if (!MitchSMP.permissions().has(player, "mitchsmp.artifacts.use")) {
                Text.msg(player, "&cYou do not have permission.");
                return true;
            }
            openShop(player, args.length >= 1 ? args[0] : firstTab());
            return true;
        }
        if (command.getName().equalsIgnoreCase("opshopadmin")) {
            return opShopAdmin(sender, args);
        }
        if (command.getName().equalsIgnoreCase("shardatm") || command.getName().equalsIgnoreCase("shardvault")) {
            return shardBlockCommand(sender, command.getName(), args);
        }
        if (!MitchSMP.permissions().has(sender, "mitchsmp.artifacts.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            Text.msg(sender, "&cUsage: /bossshards give <player> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.msg(sender, "&cPlayer is not online.");
            return true;
        }
        int amount = args.length >= 3 ? parseInt(args[2], 1) : 1;
        giveShards(target, Math.max(1, amount));
        Text.msg(sender, "&aBoss Shards granted.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("opshop")) {
            return args.length == 1 ? Tab.complete(args[0], shopTabs()) : List.of();
        }
        if (command.getName().equalsIgnoreCase("opshopadmin")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.artifacts.admin")) {
                return List.of();
            }
            if (args.length == 1) {
                return Tab.complete(args[0], "tab", "set", "remove", "reload", "list");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("tab")) {
                return Tab.complete(args[1], "add", "remove", "list");
            }
            if (args.length == 2 && args[0].matches("(?i)set|remove")) {
                return Tab.complete(args[1], shopTabs());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("tab") && args[1].equalsIgnoreCase("remove")) {
                return Tab.complete(args[2], shopTabs());
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
                return Tab.complete(args[3], "1", "2", "4", "8", "16", "24");
            }
        }
        if (command.getName().equalsIgnoreCase("bossshards")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "give");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return Tab.onlinePlayers(args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return Tab.amounts(args[2]);
            }
        }
        if (command.getName().equalsIgnoreCase("shardatm") || command.getName().equalsIgnoreCase("shardvault")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.artifacts.admin")) {
                return List.of();
            }
            if (args.length == 1) {
                return Tab.onlinePlayers(args[0]);
            }
            if (args.length == 2) {
                return Tab.amounts(args[1]);
            }
        }
        return List.of();
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        int amount = dropAmount(event);
        if (amount <= 0) {
            return;
        }
        event.getDrops().add(shard(amount));
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() != null) {
            Text.msg(entity.getKiller(), "&dA boss dropped &f" + amount + " &dBoss Shard(s).");
        }
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShopMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShopMenu menu = (ShopMenu) top.getHolder();
        if (event.getRawSlot() >= 0 && event.getRawSlot() < 9) {
            String tab = tabAtSlot(event.getRawSlot());
            if (tab != null) {
                openShop(player, tab);
            }
            return;
        }
        buy(player, menu.tab(), event.getRawSlot());
    }

    @EventHandler
    public void onShardMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShopMenu) {
            return;
        }
        if (top.getHolder() instanceof ShardMenu menu) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            switch (event.getRawSlot()) {
                case 11 -> depositAll(player, menu);
                case 13 -> withdraw(player, menu, 1);
                case 15 -> withdraw(player, menu, Math.min(64, shardBalance(player, menu)));
                case 22 -> withdraw(player, menu, shardBalance(player, menu));
                default -> {
                }
            }
            openShardStorage(player, menu.kind(), menu.key());
            return;
        }
        if (clickHasShard(event) && (event.isShiftClick() || event.getRawSlot() < top.getSize())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                Text.msg(player, "&cBoss Shards cannot be stored in normal containers. Use a Shard ATM or Shard Vault.");
            }
        }
    }

    @EventHandler
    public void onShardDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ShardMenu || top.getHolder() instanceof ShopMenu) {
            return;
        }
        if (!draggedShard(event)) {
            return;
        }
        for (Integer slot : event.getRawSlots()) {
            if (slot != null && slot < top.getSize()) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    Text.msg(player, "&cBoss Shards cannot be dragged into normal containers.");
                }
                return;
            }
        }
    }

    private boolean draggedShard(InventoryDragEvent event) {
        try {
            Object item = event.getClass().getMethod("getOldCursor").invoke(event);
            return item instanceof ItemStack stack && isShard(stack);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean clickHasShard(InventoryClickEvent event) {
        if (isShard(event.getCurrentItem())) {
            return true;
        }
        try {
            Object cursor = event.getClass().getMethod("getCursor").invoke(event);
            return cursor instanceof ItemStack stack && isShard(stack);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @EventHandler
    public void onShardHopperMove(InventoryMoveItemEvent event) {
        if (isShard(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onShardBlockPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (isShardAtm(hand)) {
            if (event.getBlock().getType() != Material.ENDER_CHEST) {
                event.setCancelled(true);
                Text.msg(event.getPlayer(), "&cShard ATMs must be placed as ender chests.");
                return;
            }
            String key = blockKey(event.getBlock().getLocation());
            shardData.set("block." + key + ".type", "atm");
            shardData.set("block." + key + ".placedBy", event.getPlayer().getUniqueId().toString());
            shardData.saveSoon(this, 20L);
            Text.msg(event.getPlayer(), "&aShard ATM placed. Players can bank shards here.");
            return;
        }
        if (isShardVault(hand)) {
            if (event.getBlock().getType() != Material.CHEST) {
                event.setCancelled(true);
                Text.msg(event.getPlayer(), "&cShard Vaults must be placed as chests.");
                return;
            }
            String key = blockKey(event.getBlock().getLocation());
            shardData.set("block." + key + ".type", "vault");
            shardData.set("block." + key + ".owner", event.getPlayer().getUniqueId().toString());
            shardData.set("block." + key + ".ownerName", event.getPlayer().getName());
            shardData.set("vault." + key + ".balance", 0);
            shardData.saveSoon(this, 20L);
            Text.msg(event.getPlayer(), "&aShard Vault placed. Warning: nearby players may detect it.");
        }
    }

    @EventHandler
    public void onShardBlockBreak(BlockBreakEvent event) {
        String key = blockKey(event.getBlock().getLocation());
        String type = shardData.getString("block." + key + ".type", "");
        if (type.isBlank()) {
            return;
        }
        try {
            event.getClass().getMethod("setDropItems", boolean.class).invoke(event, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (type.equals("atm")) {
            event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), shardAtmItem(1));
        } else {
            int balance = shardData.getInt("vault." + key + ".balance", 0);
            event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), shardVaultItem(1));
            if (balance > 0) {
                event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), shard(balance));
            }
            clearVault(key);
        }
        clearShardBlock(key);
        Text.msg(event.getPlayer(), "&eShard storage removed.");
    }

    @EventHandler
    public void onShardBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        String key = blockKey(block.getLocation());
        String type = shardData.getString("block." + key + ".type", "");
        if (type.isBlank()) {
            return;
        }
        event.setCancelled(true);
        openShardStorage(event.getPlayer(), type, key);
    }

    @EventHandler
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) {
            return;
        }
        if (event.getFinalDamage() < player.getHealth() || !hasHeldPhoenixTotem(player)) {
            return;
        }
        pendingPhoenix.put(player.getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTaskLater(this, () -> pendingPhoenix.remove(player.getUniqueId()), 40L);
    }

    @EventHandler
    public void onPhoenixResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) {
            return;
        }
        Long marked = pendingPhoenix.remove(player.getUniqueId());
        if (marked == null || System.currentTimeMillis() - marked > 4000L) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> activatePhoenix(player));
    }

    private int dropAmount(EntityDeathEvent event) {
        String name = Text.stripColorCodes(event.getEntity().getCustomName());
        if (name.toLowerCase(Locale.ROOT).contains("mitchsmp boss")) {
            return 2 + random.nextInt(3);
        }
        EntityType type = event.getEntityType();
        double chance = switch (type) {
            case ENDER_DRAGON, WITHER, WARDEN -> 0.18D;
            case ELDER_GUARDIAN -> 0.04D;
            case EVOKER -> 0.01D;
            default -> 0.0D;
        };
        return random.nextDouble() <= chance ? 1 : 0;
    }

    private void openShop(Player player, String requestedTab) {
        String tab = shopTabs().contains(normalize(requestedTab)) ? normalize(requestedTab) : firstTab();
        ShopMenu holder = new ShopMenu(tab);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8Boss Shard Shop &7- &f" + tabDisplay(tab)));
        holder.inventory(inventory);
        inventory.setItem(4, shopInfo(player));
        for (String shopTab : shopTabs()) {
            int slot = tabSlot(shopTab);
            if (slot >= 0 && slot < 9) {
                inventory.setItem(slot, icon(tabIcon(shopTab), (shopTab.equals(tab) ? "&a" : "&7") + tabDisplay(shopTab), List.of("&7Click to open this tab.")));
            }
        }
        for (String key : shopData.keys()) {
            String prefix = "tabs." + tab + ".slots.";
            if (!key.startsWith(prefix) || !key.endsWith(".item")) {
                continue;
            }
            int slot = parseInt(key.substring(prefix.length(), key.length() - ".item".length()), -1);
            if (slot < 9 || slot >= 54) {
                continue;
            }
            ItemStack item = decodeItem(shopData.getString(key, ""));
            if (item != null) {
                inventory.setItem(slot, shopIcon(tab, slot, item));
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack tierInfo(String name, String lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(List.of(Text.color(lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack shopInfo(Player player) {
        ItemStack item = shard(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&dBoss Shards: &f" + countShards(player)));
            meta.setLore(List.of(Text.color("&7Drop zeldzaam van bosses."), Text.color("&7Gebruik ze hier for OP items.")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack shopIcon(String tab, int slot, ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(Text.color("&8"));
            lore.add(Text.color("&7Cost: &d" + shopCost(tab, slot) + " Boss Shards"));
            lore.add(Text.color("&eClick to buy."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ShopItem shopItem(int slot) {
        for (ShopItem item : ShopItem.values()) {
            if (item.slot() == slot) {
                return item;
            }
        }
        return null;
    }

    private void buy(Player player, String tab, int slot) {
        ItemStack reward = decodeItem(shopData.getString("tabs." + tab + ".slots." + slot + ".item", ""));
        if (reward == null) {
            return;
        }
        int cost = shopCost(tab, slot);
        if (countShards(player) < cost) {
            Text.msg(player, "&cYou need &f" + cost + " &cBoss Shards.");
            return;
        }
        removeShards(player, cost);
        player.getInventory().addItem(decorateReward(reward.clone())).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Text.msg(player, "&aPurchased &f" + displayName(reward) + "&a.");
    }

    private boolean shardBlockCommand(CommandSender sender, String command, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.artifacts.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Text.msg(sender, "&cPlayer is not online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Text.msg(sender, "&cUsage: /" + command + " <player> [amount]");
            return true;
        }
        int amount = args.length >= 2 ? Math.max(1, parseInt(args[1], 1)) : 1;
        ItemStack item = command.equalsIgnoreCase("shardatm") ? shardAtmItem(amount) : shardVaultItem(amount);
        target.getInventory().addItem(item).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        Text.msg(sender, "&aGranted &f" + amount + " &a" + (command.equalsIgnoreCase("shardatm") ? "Shard ATM" : "Shard Vault") + "&a.");
        return true;
    }

    private void openShardStorage(Player player, String kind, String key) {
        ShardMenu holder = new ShardMenu(kind, key);
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color(kind.equals("atm") ? "&4Shard ATM" : "&4Shard Vault"));
        holder.inventory(inventory);
        int balance = shardBalance(player, holder);
        inventory.setItem(4, icon(kind.equals("atm") ? Material.ENDER_CHEST : Material.CHEST, kind.equals("atm") ? "&dShard ATM" : "&dShard Vault", List.of(
            "&7Stored shards: &f" + balance,
            kind.equals("atm") ? "&7Personal protected shard bank." : "&7Physical vault storage.",
            kind.equals("vault") ? "&cNearby enemies can detect this vault." : "&7Use ATMs at risky PvP locations."
        )));
        inventory.setItem(11, icon(Material.NETHER_STAR, "&aDeposit all", List.of("&7Move all carried Boss Shards into storage.")));
        inventory.setItem(13, icon(Material.PAPER, "&eWithdraw 1", List.of("&7Withdraw one Boss Shard.")));
        inventory.setItem(15, icon(Material.EMERALD, "&eWithdraw stack", List.of("&7Withdraw up to 64 Boss Shards.")));
        inventory.setItem(22, icon(Material.REDSTONE, "&cWithdraw all", List.of("&7Withdraw every stored Boss Shard.")));
        player.openInventory(inventory);
    }

    private int shardBalance(Player player, ShardMenu menu) {
        if (menu.kind().equals("atm")) {
            return Math.max(0, shardData.getInt("bank." + player.getUniqueId(), 0));
        }
        return Math.max(0, shardData.getInt("vault." + menu.key() + ".balance", 0));
    }

    private void setShardBalance(Player player, ShardMenu menu, int amount) {
        if (menu.kind().equals("atm")) {
            shardData.set("bank." + player.getUniqueId(), Math.max(0, amount));
        } else {
            shardData.set("vault." + menu.key() + ".balance", Math.max(0, amount));
        }
        shardData.saveSoon(this, 20L);
    }

    private void depositAll(Player player, ShardMenu menu) {
        int carried = countShards(player);
        if (carried <= 0) {
            Text.msg(player, "&7You are not carrying Boss Shards.");
            return;
        }
        removeShards(player, carried);
        setShardBalance(player, menu, shardBalance(player, menu) + carried);
        Text.msg(player, "&aDeposited &f" + carried + " &aBoss Shards.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.4F);
    }

    private void withdraw(Player player, ShardMenu menu, int amount) {
        int balance = shardBalance(player, menu);
        int take = Math.max(0, Math.min(balance, amount));
        if (take <= 0) {
            Text.msg(player, "&7No Boss Shards stored here.");
            return;
        }
        setShardBalance(player, menu, balance - take);
        giveShards(player, take);
        Text.msg(player, "&aWithdrew &f" + take + " &aBoss Shards.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.2F);
    }

    private ItemStack shardAtmItem(int amount) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&dShard ATM"));
            meta.setLore(List.of(Text.color("&7Physical Boss Shard bank terminal."), Text.color("&7Place at PvP locations for risky banking.")));
            meta.getPersistentDataContainer().set(shardAtmKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack shardVaultItem(int amount) {
        ItemStack item = new ItemStack(Material.CHEST, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&dShard Vault"));
            meta.setLore(List.of(Text.color("&7Placeable Boss Shard vault."), Text.color("&cEnemies nearby may detect it.")));
            meta.getPersistentDataContainer().set(shardVaultKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isShardAtm(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(shardAtmKey, PersistentDataType.BYTE);
    }

    private boolean isShardVault(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(shardVaultKey, PersistentDataType.BYTE);
    }

    private String blockKey(Location location) {
        return location.getWorld().getName() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    private Location locationFromBlockKey(String key) {
        String[] parts = key.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        return new Location(world, parseInt(parts[1], 0), parseInt(parts[2], 64), parseInt(parts[3], 0));
    }

    private void clearShardBlock(String key) {
        for (String stored : new ArrayList<>(shardData.keys())) {
            if (stored.startsWith("block." + key + ".")) {
                shardData.set(stored, null);
            }
        }
        shardData.saveSoon(this, 20L);
    }

    private void clearVault(String key) {
        for (String stored : new ArrayList<>(shardData.keys())) {
            if (stored.startsWith("vault." + key + ".")) {
                shardData.set(stored, null);
            }
        }
        shardData.saveSoon(this, 20L);
    }

    private void vaultAlertTick() {
        double radius = Math.max(1.0D, shardData.getDouble("settings.vault_alert_radius", 50.0D));
        double radiusSquared = radius * radius;
        for (String stored : new ArrayList<>(shardData.keys())) {
            if (!stored.startsWith("block.") || !stored.endsWith(".type") || !shardData.getString(stored, "").equals("vault")) {
                continue;
            }
            String key = stored.substring("block.".length(), stored.length() - ".type".length());
            Location location = locationFromBlockKey(key);
            if (location == null || location.getWorld() == null) {
                continue;
            }
            String owner = shardData.getString("block." + key + ".owner", "");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > radiusSquared) {
                    continue;
                }
                if (player.getUniqueId().toString().equals(owner)) {
                    continue;
                }
                String alertKey = "vault." + key + ".alerted." + player.getUniqueId();
                if (Boolean.parseBoolean(shardData.getString(alertKey, "false"))) {
                    continue;
                }
                shardData.set(alertKey, true);
                Text.msg(player, "&5You sense a hidden Shard Vault nearby...");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 0.65F);
            }
        }
        shardData.saveSoon(this, 40L);
    }

    private void ensureShardDefaults() {
        if (!shardData.contains("settings.vault_alert_radius")) {
            shardData.set("settings.vault_alert_radius", 50.0D);
            shardData.save();
        }
    }

    private ItemStack decorateReward(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) {
            return item;
        }
        String plain = displayName(item).toLowerCase(Locale.ROOT);
        if (!plain.contains("phoenix")) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                Text.color("&6God-tier Phoenix artifact."),
                Text.color("&7Werkt als normale totem bij lethal damage."),
                Text.color("&7Daarna: vuur, blindness, slowness en geluid rond jou.")
            ));
            meta.getPersistentDataContainer().set(phoenixKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean hasHeldPhoenixTotem(Player player) {
        return isPhoenixTotem(player.getInventory().getItemInMainHand()) || isPhoenixTotem(player.getInventory().getItemInOffHand());
    }

    private boolean isPhoenixTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(phoenixKey, PersistentDataType.BYTE);
    }

    private void activatePhoenix(Player player) {
        org.bukkit.Location origin = player.getLocation();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0D : player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        player.setHealth(maxHealth);
        player.setFireTicks(0);
        player.getWorld().spawnParticle(Particle.EXPLOSION, origin, 10, 2.0D, 1.0D, 2.0D, 0.1D);
        player.getWorld().spawnParticle(Particle.FLAME, origin, 160, 4.0D, 1.3D, 4.0D, 0.06D);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, origin, 120, 2.0D, 1.8D, 2.0D, 0.25D);
        player.playSound(origin, Sound.ITEM_TOTEM_USE, 1.5F, 0.7F);
        player.playSound(origin, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.2F, 0.8F);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || !sameWorld(origin, target.getLocation()) || origin.distanceSquared(target.getLocation()) > 100.0D) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
            target.setFireTicks(100);
            target.playSound(target.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.9F, 0.75F);
            Text.msg(target, "&6De Phoenix Totem verblindt je door de explosie!");
        }
        Text.msg(player, "&6Phoenix Totem geactiveerd: je bent herboren in vuur.");
    }

    private boolean sameWorld(org.bukkit.Location a, org.bukkit.Location b) {
        return a.getWorld() != null && b.getWorld() != null && a.getWorld().getName().equals(b.getWorld().getName());
    }

    private ItemStack shard(int amount) {
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&dBoss Shard"));
            meta.setLore(List.of(Text.color("&7Extremely rare boss currency."), Text.color("&7Spend it in &f/opshop&7.")));
            setVisualModel(meta, 910001, "boss_shard");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public ItemStack createShard(int amount) {
        return shard(Math.max(1, amount));
    }

    @Override
    public void giveShards(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        player.getInventory().addItem(createShard(amount)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    @Override
    public boolean isShard(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || (!meta.getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE) && !looksLikeLegacyShard(meta))) {
            return false;
        }
        meta.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        setVisualModel(meta, 910001, "boss_shard");
        meta.setDisplayName(Text.color("&dBoss Shard"));
        meta.setLore(List.of(Text.color("&7Extremely rare boss currency."), Text.color("&7Spend it in &f/opshop&7.")));
        item.setItemMeta(meta);
        return true;
    }

    private boolean looksLikeLegacyShard(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String display = Text.stripColorCodes(meta.getDisplayName());
        if (!"Boss Shard".equalsIgnoreCase(display)) {
            return false;
        }
        if (meta.getLore() == null || meta.getLore().isEmpty()) {
            return true;
        }
        return meta.getLore().stream()
            .map(Text::stripColorCodes)
            .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("boss currency")
                || line.toLowerCase(Locale.ROOT).contains("/opshop"));
    }

    @EventHandler
    public void onShardOwnerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (ItemStack item : event.getPlayer().getInventory().getContents()) {
                isShard(item);
            }
            event.getPlayer().updateInventory();
        }, 20L);
    }

    private void setVisualModel(ItemMeta meta, int value, String model) {
        meta.setCustomModelData(value);
        meta.setItemModel(new NamespacedKey("bloodbound", model));
    }

    private int countShards(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isShard(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removeShards(Player player, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!isShard(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[index] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    private int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean opShopAdmin(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.artifacts.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Text.msg(sender, "&7Tabs: &f" + String.join("&7, &f", shopTabs()));
            Text.msg(sender, "&7Use &f/opshopadmin set <tab> <slot> <cost> [display] &7with an item in hand.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            shopData.load();
            ensureShopDefaults();
            Text.msg(sender, "&aOP shop config reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("tab")) {
            return opShopAdminTab(sender, args);
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                Text.msg(sender, "&cUsage: /opshopadmin remove <tab> <slot>");
                return true;
            }
            String tab = normalize(args[1]);
            int slot = parseInt(args[2], -1);
            shopData.set("tabs." + tab + ".slots." + slot + ".item", null);
            shopData.set("tabs." + tab + ".slots." + slot + ".cost", null);
            shopData.save();
            Text.msg(sender, "&aRemoved shop slot &f" + slot + " &afrom tab &f" + tab + "&a.");
            return true;
        }
        if (!args[0].equalsIgnoreCase("set") || args.length < 4) {
            Text.msg(sender, "&cUsage: /opshopadmin set <tab> <slot> <cost> [display]");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only for setting items.");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            Text.msg(player, "&cHold the shop reward item in your main hand.");
            return true;
        }
        String tab = normalize(args[1]);
        if (!shopTabs().contains(tab)) {
            Text.msg(player, "&cUnknown tab. Add it first with &f/opshopadmin tab add " + tab + " <display>&c.");
            return true;
        }
        int slot = parseInt(args[2], -1);
        if (slot < 9 || slot >= 54) {
            Text.msg(player, "&cSlot must be 9-53. The top row is reserved for tabs.");
            return true;
        }
        int cost = Math.max(0, parseInt(args[3], 0));
        ItemStack stored = hand.clone();
        if (args.length >= 5) {
            ItemMeta meta = stored.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color(String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length))));
                stored.setItemMeta(meta);
            }
        }
        shopData.set("tabs." + tab + ".slots." + slot + ".item", encodeItem(stored));
        shopData.set("tabs." + tab + ".slots." + slot + ".cost", cost);
        shopData.save();
        Text.msg(player, "&aSaved &f" + displayName(stored) + " &ain tab &f" + tab + " &aslot &f" + slot + " &afor &d" + cost + " shards&a.");
        return true;
    }

    private boolean opShopAdminTab(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            Text.msg(sender, "&7Tabs: &f" + String.join("&7, &f", shopTabs()));
            return true;
        }
        if (args[1].equalsIgnoreCase("add")) {
            if (args.length < 3) {
                Text.msg(sender, "&cUsage: /opshopadmin tab add <id> [display]");
                return true;
            }
            String tab = normalize(args[2]);
            if (tab.isBlank()) {
                Text.msg(sender, "&cInvalid tab id.");
                return true;
            }
            String display = args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : tabDisplay(tab);
            shopData.set("tabs." + tab + ".display", display);
            shopData.set("tabs." + tab + ".slot", nextTabSlot());
            shopData.set("tabs." + tab + ".icon", Material.NETHER_STAR.name());
            shopData.save();
            Text.msg(sender, "&aAdded OP shop tab &f" + tab + "&a.");
            return true;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                Text.msg(sender, "&cUsage: /opshopadmin tab remove <id>");
                return true;
            }
            String tab = normalize(args[2]);
            for (String key : new ArrayList<>(shopData.keys())) {
                if (key.startsWith("tabs." + tab + ".")) {
                    shopData.set(key, null);
                }
            }
            shopData.save();
            Text.msg(sender, "&aRemoved OP shop tab &f" + tab + "&a.");
            return true;
        }
        Text.msg(sender, "&cUsage: /opshopadmin tab <add|remove|list>");
        return true;
    }

    private void ensureShopDefaults() {
        if (!shopData.keys().stream().noneMatch(key -> key.startsWith("tabs."))) {
            return;
        }
        addDefaultTab("pro", "&aPro", Material.DIAMOND_SWORD, 0);
        addDefaultTab("elite", "&bElite", Material.NETHERITE_SWORD, 1);
        addDefaultTab("god", "&6God", Material.NETHERITE_CHESTPLATE, 2);
        addDefaultTab("special", "&dSpecials", Material.TOTEM_OF_UNDYING, 3);
        for (ShopItem item : ShopItem.values()) {
            String tab = item.tier().toLowerCase(Locale.ROOT);
            setDefaultShopItem(tab, item.slot(), item.cost(), decorateReward(item.single()));
        }
        shopData.save();
    }

    private void addDefaultTab(String tab, String display, Material icon, int slot) {
        shopData.set("tabs." + tab + ".display", display);
        shopData.set("tabs." + tab + ".icon", icon.name());
        shopData.set("tabs." + tab + ".slot", slot);
    }

    private void setDefaultShopItem(String tab, int slot, int cost, ItemStack item) {
        shopData.set("tabs." + tab + ".slots." + slot + ".item", encodeItem(item));
        shopData.set("tabs." + tab + ".slots." + slot + ".cost", cost);
    }

    private List<String> shopTabs() {
        List<String> tabs = new ArrayList<>();
        for (String key : shopData.keys()) {
            if (key.startsWith("tabs.") && key.endsWith(".display")) {
                String tab = key.substring("tabs.".length(), key.length() - ".display".length());
                if (!tabs.contains(tab)) {
                    tabs.add(tab);
                }
            }
        }
        tabs.sort(java.util.Comparator.comparingInt(this::tabSlot).thenComparing(String::compareToIgnoreCase));
        return tabs;
    }

    private String firstTab() {
        List<String> tabs = shopTabs();
        return tabs.isEmpty() ? "pro" : tabs.get(0);
    }

    private int tabSlot(String tab) {
        return shopData.getInt("tabs." + tab + ".slot", 0);
    }

    private int nextTabSlot() {
        Set<Integer> used = new java.util.HashSet<>();
        for (String tab : shopTabs()) {
            used.add(tabSlot(tab));
        }
        for (int slot = 0; slot < 9; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return 8;
    }

    private String tabAtSlot(int slot) {
        for (String tab : shopTabs()) {
            if (tabSlot(tab) == slot) {
                return tab;
            }
        }
        return null;
    }

    private String tabDisplay(String tab) {
        return Text.color(shopData.getString("tabs." + tab + ".display", tab));
    }

    private Material tabIcon(String tab) {
        return material(shopData.getString("tabs." + tab + ".icon", "NETHER_STAR"), Material.NETHER_STAR);
    }

    private int shopCost(String tab, int slot) {
        return Math.max(0, shopData.getInt("tabs." + tab + ".slots." + slot + ".cost", 1));
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String displayName(ItemStack item) {
        if (item == null) {
            return "Item";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Text.stripColorCodes(meta.getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String encodeItem(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private ItemStack decodeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Material material(String input, Material fallback) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    private enum ShopItem {
        PRO_SWORD("&aPro Sword", 4, Material.DIAMOND_SWORD, GearType.SWORD, "Pro", 10, 4),
        PRO_AXE("&aPro Axe", 4, Material.DIAMOND_AXE, GearType.AXE, "Pro", 11, 4),
        PRO_BOW("&aPro Bow", 3, Material.BOW, GearType.BOW, "Pro", 12, 4),
        PRO_HELMET("&aPro Helmet", 3, Material.DIAMOND_HELMET, GearType.HELMET, "Pro", 13, 4),
        PRO_CHESTPLATE("&aPro Chestplate", 5, Material.DIAMOND_CHESTPLATE, GearType.CHESTPLATE, "Pro", 14, 4),
        PRO_LEGGINGS("&aPro Leggings", 5, Material.DIAMOND_LEGGINGS, GearType.LEGGINGS, "Pro", 15, 4),
        PRO_BOOTS("&aPro Boots", 3, Material.DIAMOND_BOOTS, GearType.BOOTS, "Pro", 16, 4),
        PRO_SHIELD("&aPro Shield", 4, Material.SHIELD, GearType.SHIELD, "Pro", 17, 4),

        ELITE_SWORD("&bElite Sword", 8, Material.NETHERITE_SWORD, GearType.SWORD, "Elite", 19, 6),
        ELITE_AXE("&bElite Axe", 8, Material.NETHERITE_AXE, GearType.AXE, "Elite", 20, 6),
        ELITE_BOW("&bElite Bow", 7, Material.BOW, GearType.BOW, "Elite", 21, 6),
        ELITE_HELMET("&bElite Helmet", 7, Material.NETHERITE_HELMET, GearType.HELMET, "Elite", 22, 6),
        ELITE_CHESTPLATE("&bElite Chestplate", 10, Material.NETHERITE_CHESTPLATE, GearType.CHESTPLATE, "Elite", 23, 6),
        ELITE_LEGGINGS("&bElite Leggings", 9, Material.NETHERITE_LEGGINGS, GearType.LEGGINGS, "Elite", 24, 6),
        ELITE_BOOTS("&bElite Boots", 7, Material.NETHERITE_BOOTS, GearType.BOOTS, "Elite", 25, 6),
        ELITE_SHIELD("&bElite Shield", 8, Material.SHIELD, GearType.SHIELD, "Elite", 26, 6),

        GOD_SWORD("&6God Sword", 18, Material.NETHERITE_SWORD, GearType.SWORD, "God", 28, 9),
        GOD_AXE("&6God Axe", 18, Material.NETHERITE_AXE, GearType.AXE, "God", 29, 9),
        GOD_BOW("&6God Bow", 16, Material.BOW, GearType.BOW, "God", 30, 9),
        GOD_HELMET("&6God Helmet", 15, Material.NETHERITE_HELMET, GearType.HELMET, "God", 31, 9),
        GOD_CHESTPLATE("&6God Chestplate", 24, Material.NETHERITE_CHESTPLATE, GearType.CHESTPLATE, "God", 32, 9),
        GOD_LEGGINGS("&6God Leggings", 22, Material.NETHERITE_LEGGINGS, GearType.LEGGINGS, "God", 33, 9),
        GOD_BOOTS("&6God Boots", 15, Material.NETHERITE_BOOTS, GearType.BOOTS, "God", 34, 9),
        GOD_SHIELD("&6God Shield", 16, Material.SHIELD, GearType.SHIELD, "God", 35, 9),

        PHOENIX_TOTEM("&6Phoenix Totem", 20, Material.TOTEM_OF_UNDYING, GearType.PHOENIX, "Special", 40, 10),
        GOD_WINGS("&fGod Wings", 14, Material.ELYTRA, GearType.ELYTRA, "Special", 41, 8);

        private final String displayName;
        private final int cost;
        private final Material material;
        private final GearType type;
        private final String tier;
        private final int slot;
        private final int power;

        ShopItem(String displayName, int cost, Material material, GearType type, String tier, int slot, int power) {
            this.displayName = displayName;
            this.cost = cost;
            this.material = material;
            this.type = type;
            this.tier = tier;
            this.slot = slot;
            this.power = power;
        }

        String displayName() {
            return Text.stripColorCodes(displayName);
        }

        int cost() {
            return cost;
        }

        String tier() {
            return tier;
        }

        int slot() {
            return slot;
        }

        ItemStack icon() {
            return single();
        }

        ItemStack[] items() {
            return new ItemStack[] {single()};
        }

        ItemStack single() {
            if (type == GearType.HELMET || type == GearType.CHESTPLATE || type == GearType.LEGGINGS || type == GearType.BOOTS) {
                return armor(material, displayName, tier, power);
            }
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color(displayName));
                meta.setLore(List.of(Text.color("&7" + tier + " Boss Shop item."), Text.color("&7Enchants boven normale progression.")));
                if (type != GearType.PHOENIX) {
                    meta.setUnbreakable(true);
                }
                switch (type) {
                    case SWORD -> {
                        meta.addEnchant(Enchantment.SHARPNESS, power + 2, true);
                        meta.addEnchant(Enchantment.FIRE_ASPECT, Math.max(2, power / 3), true);
                        meta.addEnchant(Enchantment.LOOTING, Math.max(3, power / 2), true);
                        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                        meta.addEnchant(Enchantment.MENDING, 1, true);
                    }
                    case AXE -> {
                        meta.addEnchant(Enchantment.SHARPNESS, power + 1, true);
                        meta.addEnchant(Enchantment.KNOCKBACK, Math.max(2, power / 3), true);
                        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                        meta.addEnchant(Enchantment.MENDING, 1, true);
                    }
                    case BOW -> {
                        meta.addEnchant(Enchantment.POWER, power + 2, true);
                        meta.addEnchant(Enchantment.PUNCH, Math.max(2, power / 3), true);
                        meta.addEnchant(Enchantment.FLAME, 2, true);
                        meta.addEnchant(Enchantment.INFINITY, 1, true);
                        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                    }
                    case SHIELD -> {
                        meta.addEnchant(Enchantment.PROTECTION, Math.max(4, power), true);
                        meta.addEnchant(Enchantment.THORNS, Math.max(2, power / 2), true);
                        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                        meta.addEnchant(Enchantment.MENDING, 1, true);
                    }
                    case ELYTRA -> {
                        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                        meta.addEnchant(Enchantment.MENDING, 1, true);
                    }
                    default -> {
                    }
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        private static ItemStack armor(Material material, String name, String tier, int protection) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color(name));
                meta.setLore(List.of(Text.color("&7" + tier + " Boss Shop armor."), Text.color("&7Boven vanilla enchant levels.")));
                meta.setUnbreakable(true);
                meta.addEnchant(Enchantment.PROTECTION, protection, true);
                meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                meta.addEnchant(Enchantment.MENDING, 1, true);
                meta.addEnchant(Enchantment.THORNS, 4, true);
                if (material == Material.NETHERITE_BOOTS) {
                    meta.addEnchant(Enchantment.FEATHER_FALLING, 6, true);
                    meta.addEnchant(Enchantment.DEPTH_STRIDER, 4, true);
                }
                if (material == Material.NETHERITE_HELMET) {
                    meta.addEnchant(Enchantment.RESPIRATION, 5, true);
                    meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, true);
                }
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    private enum GearType {
        SWORD,
        AXE,
        BOW,
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        SHIELD,
        ELYTRA,
        PHOENIX
    }

    private static final class ShopMenu implements InventoryHolder {
        private Inventory inventory;
        private final String tab;

        private ShopMenu(String tab) {
            this.tab = tab;
        }

        String tab() {
            return tab;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ShardMenu implements InventoryHolder {
        private Inventory inventory;
        private final String kind;
        private final String key;

        private ShardMenu(String kind, String key) {
            this.kind = kind;
            this.key = key;
        }

        String kind() {
            return kind;
        }

        String key() {
            return key;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}



