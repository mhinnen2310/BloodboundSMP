package nl.mitchsmp.artifacts;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.BossShardService;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    @Override
    public void onEnable() {
        shardKey = new NamespacedKey(this, "boss_shard");
        phoenixKey = new NamespacedKey(this, "phoenix_totem");
        MitchSMP.registerService(BossShardService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String command : List.of("opshop", "bossshards")) {
            if (getCommand(command) != null) {
                getCommand(command).setExecutor(this);
                getCommand(command).setTabCompleter(this);
            }
        }
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
            openShop(player);
            return true;
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
        ShopItem item = shopItem(event.getRawSlot());
        if (item == null) {
            return;
        }
        buy(player, item);
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

    private void openShop(Player player) {
        ShopMenu holder = new ShopMenu();
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8Boss Shard Shop"));
        holder.inventory(inventory);
        inventory.setItem(4, shopInfo(player));
        inventory.setItem(9, tierInfo("&aPro Tier", "&73-6 shards, sterke starter OP gear."));
        inventory.setItem(18, tierInfo("&bElite Tier", "&78-13 shards, high-end PVP gear."));
        inventory.setItem(27, tierInfo("&6God Tier", "&716-26 shards, boven vanilla max."));
        inventory.setItem(36, tierInfo("&dSpecials", "&7Phoenix en mobility items."));
        for (ShopItem item : ShopItem.values()) {
            inventory.setItem(item.slot(), shopIcon(item));
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

    private ItemStack shopIcon(ShopItem shopItem) {
        ItemStack item = shopItem.icon();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new java.util.ArrayList<>();
            lore.add(Text.color("&7Tier: &f" + shopItem.tier()));
            lore.add(Text.color("&7Cost: &d" + shopItem.cost() + " Boss Shards"));
            if (shopItem == ShopItem.PHOENIX_TOTEM) {
                lore.add(Text.color("&6Trigger: lethal damage, like a normal totem."));
            }
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

    private void buy(Player player, ShopItem shopItem) {
        if (countShards(player) < shopItem.cost()) {
            Text.msg(player, "&cJe hebt &f" + shopItem.cost() + " &cBoss Shards nodig.");
            return;
        }
        removeShards(player, shopItem.cost());
        player.getInventory().addItem(rewardItems(shopItem)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Text.msg(player, "&aGekocht: &f" + shopItem.displayName() + "&a.");
    }

    private ItemStack[] rewardItems(ShopItem shopItem) {
        ItemStack[] items = shopItem.items();
        for (int index = 0; index < items.length; index++) {
            items[index] = decorateReward(items[index], shopItem);
        }
        return items;
    }

    private ItemStack decorateReward(ItemStack item, ShopItem shopItem) {
        if (shopItem != ShopItem.PHOENIX_TOTEM) {
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

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}



