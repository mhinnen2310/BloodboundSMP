package nl.mitchsmp.economy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.EconomyWatchService;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.SkillService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyPlugin extends JavaPlugin implements EconomyService, Listener, TabCompleter {
    private static final double START_BALANCE = 100.0D;
    private static final double ADMIN_START_BALANCE = 1_000_000.0D;
    private static final double MAX_BALANCE = 100_000_000_000.0D;
    private static final double MAX_TRANSACTION = 10_000_000_000.0D;
    private static final int QUICKSELL_SELL_SLOTS = 45;
    private static final int QUICKSELL_CANCEL_SLOT = 45;
    private static final int QUICKSELL_PRICES_SLOT = 47;
    private static final int QUICKSELL_INFO_SLOT = 49;
    private static final int QUICKSELL_CONFIRM_SLOT = 53;
    private static final String QUICKSELL_LORE_MARKER = "[QS]";
    private PropertiesFile balances;
    private Path transactions;
    private ExecutorService transactionWriter;

    @Override
    public void onEnable() {
        balances = new PropertiesFile(getDataFolder().toPath().resolve("balances.properties"));
        transactions = getDataFolder().toPath().resolve("transactions.log");
        transactionWriter = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Bloodbound-Economy-Writer");
            thread.setDaemon(true);
            return thread;
        });
        MitchSMP.registerService(EconomyService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        command("balance");
        command("pay");
        command("moneytop");
        command("eco");
        command("quicksell");
    }

    @Override
    public void onDisable() {
        if (transactionWriter != null) {
            transactionWriter.shutdown();
            try {
                transactionWriter.awaitTermination(5L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensure(event.getPlayer().getUniqueId());
    }

    @Override
    public double getBalance(UUID playerId) {
        String key = activeBalanceKey(playerId);
        double start = activeStartBalance(playerId);
        ensure(key, start);
        return Math.max(0.0D, balances.getDouble(key, start));
    }

    @Override
    public void setBalance(UUID playerId, double amount) {
        setBalance(activeBalanceKey(playerId), amount);
    }

    @Override
    public void deposit(UUID playerId, double amount, String reason) {
        if (!validTransaction(amount)) {
            return;
        }
        String key = depositBalanceKey(playerId, reason);
        double start = key.startsWith("admin.") ? ADMIN_START_BALANCE : START_BALANCE;
        ensure(key, start);
        setBalance(key, Math.max(0.0D, balances.getDouble(key, start)) + amount);
        log("DEPOSIT", null, playerId, amount, reason);
    }

    @Override
    public boolean withdraw(UUID playerId, double amount, String reason) {
        String key = activeBalanceKey(playerId);
        double start = activeStartBalance(playerId);
        ensure(key, start);
        double current = Math.max(0.0D, balances.getDouble(key, start));
        if (!validTransaction(amount) || current < amount) {
            return false;
        }
        setBalance(key, current - amount);
        log("WITHDRAW", playerId, null, amount, reason);
        return true;
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount, String reason) {
        if (isAdminWalletActive(from) || isAdminWalletActive(to)) {
            return false;
        }
        if (!validTransaction(amount) || !withdraw(from, amount, reason)) {
            return false;
        }
        deposit(to, amount, reason);
        log("TRANSFER", from, to, amount, reason);
        return true;
    }

    @Override
    public List<Map.Entry<UUID, Double>> topBalances(int limit) {
        return balances.keys().stream()
            .map(this::entry)
            .filter(entry -> entry != null)
            .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
            .limit(limit)
            .toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("balance")) {
            UUID targetId;
            String targetName;
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    Text.msg(sender, "&cGebruik: /balance <player>");
                    return true;
                }
                targetId = player.getUniqueId();
                targetName = player.getName();
            } else {
                targetId = findPlayerId(args[0]);
                if (targetId == null) {
                    Text.msg(sender, "&cPlayer not found.");
                    return true;
                }
                targetName = playerName(targetId);
            }
            Text.msg(sender, "&f" + targetName + " &7heeft &b" + activeBalanceLabel(targetId) + " &a$" + format(getBalance(targetId)) + "&7.");
            return true;
        }

        if (name.equals("pay")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cAlleen players.");
                return true;
            }
            if (args.length < 2) {
                Text.msg(sender, "&cGebruik: /pay <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || target.equals(player)) {
                Text.msg(sender, "&cOngeldige speler.");
                return true;
            }
            if (isAdminWalletActive(player.getUniqueId()) || isAdminWalletActive(target.getUniqueId())) {
                Text.msg(sender, "&c/pay is geblokkeerd als jij of de ontvanger in adminmode zit.");
                return true;
            }
            Double amount = parse(args[1]);
            if (amount == null || amount <= 0.0D) {
                Text.msg(sender, "&cOngeldig bedrag.");
                return true;
            }
            if (!transfer(player.getUniqueId(), target.getUniqueId(), amount, "pay")) {
                Text.msg(sender, "&cJe hebt niet genoeg geld.");
                return true;
            }
            awardEconomyXp(player, Math.max(4, Math.min(50, (int) Math.round(amount / 100.0D))), "pay");
            awardEconomyXp(target, 2, "pay received");
            Text.msg(player, "&aJe betaalde &f$" + format(amount) + " &aaan &f" + target.getName() + "&a.");
            Text.msg(target, "&aJe kreeg &f$" + format(amount) + " &avan &f" + player.getName() + "&a.");
            return true;
        }

        if (name.equals("moneytop")) {
            Text.msg(sender, "&aTop balances:");
            int index = 1;
            for (Map.Entry<UUID, Double> entry : topBalances(10)) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                Text.msg(sender, "&7#" + index++ + " &f" + player.getName() + " &7- &a$" + format(entry.getValue()));
            }
            return true;
        }

        if (name.equals("eco")) {
            return eco(sender, args);
        }
        if (name.equals("quicksell")) {
            return quicksell(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("balance")) {
            return args.length == 1 ? Tab.onlinePlayers(args[0]) : List.of();
        }
        if (name.equals("pay")) {
            if (args.length == 1) {
                return Tab.onlinePlayers(args[0]);
            }
            return args.length == 2 ? Tab.amounts(args[1]) : List.of();
        }
        if (name.equals("eco")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "give", "take", "set", "stats", "adminbal", "clear", "resetall");
            }
            if (args[0].equalsIgnoreCase("stats")) {
                return List.of();
            }
            if (args[0].equalsIgnoreCase("resetall")) {
                return args.length == 2 ? Tab.complete(args[1], "confirm") : List.of();
            }
            if (args[0].equalsIgnoreCase("adminbal")) {
                if (args.length == 2) {
                    return Tab.onlinePlayers(args[1]);
                }
                return args.length == 3 ? Tab.amounts(args[2]) : List.of();
            }
            if (args[0].equalsIgnoreCase("clear")) {
                if (args.length == 2) {
                    return Tab.onlinePlayers(args[1]);
                }
                return args.length == 3 ? Tab.complete(args[2], "confirm") : List.of();
            }
            if (args.length == 2) {
                return Tab.onlinePlayers(args[1]);
            }
            return args.length == 3 ? Tab.amounts(args[2]) : List.of();
        }
        if (name.equals("quicksell")) {
            if (args.length == 1) {
                return Tab.complete(args[0], "all", "hand", "prices");
            }
        }
        return List.of();
    }

    @EventHandler
    public void onQuickSellClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof QuickSellMenu menu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!canUseQuickSell(player)) {
            event.setCancelled(true);
            returnQuickSellItems(player, top);
            menu.completed(true);
            player.closeInventory();
            Text.msg(player, "&cQuickSell is unavailable in admin mode, creative/spectator, BedWars or test worlds.");
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= QUICKSELL_CANCEL_SLOT && slot < top.getSize()) {
            event.setCancelled(true);
            if (slot == QUICKSELL_CANCEL_SLOT) {
                cancelQuickSell(player, menu);
            } else if (slot == QUICKSELL_PRICES_SLOT) {
                quickSellPrices(player);
                updateQuickSellControls(player, menu);
            } else if (slot == QUICKSELL_INFO_SLOT) {
                updateQuickSellControls(player, menu);
            } else if (slot == QUICKSELL_CONFIRM_SLOT) {
                executeQuickSellBasket(player, menu);
            }
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateQuickSellControls(player, menu);
            player.updateInventory();
        }, 1L);
    }

    @EventHandler
    public void onQuickSellClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof QuickSellMenu menu)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            if (!menu.completed()) {
                returnQuickSellItems(player, inventory);
            }
            restoreValueLore(player.getInventory());
            Bukkit.getScheduler().runTaskLater(this, player::updateInventory, 1L);
        }
    }

    @EventHandler
    public void onQuickSellDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof QuickSellMenu menu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!canUseQuickSell(player)) {
            event.setCancelled(true);
            returnQuickSellItems(player, top);
            menu.completed(true);
            player.closeInventory();
            Text.msg(player, "&cQuickSell is unavailable in admin mode, creative/spectator, BedWars or test worlds.");
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= QUICKSELL_CANCEL_SLOT && slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateQuickSellControls(player, menu);
            player.updateInventory();
        }, 1L);
    }

    private boolean eco(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.economy.admin")) {
            Text.msg(sender, "&cGeen permissie.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("stats")) {
            economyStats(sender);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("adminbal")) {
            return adminBalanceCommand(sender, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
            return clearBalanceCommand(sender, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("resetall")) {
            return resetAllBalancesCommand(sender, args);
        }
        if (args.length < 3) {
            Text.msg(sender, "&cGebruik: /eco <give|take|set|stats|adminbal|clear|resetall> <player> <amount>");
            return true;
        }
        UUID targetId = findPlayerId(args[1]);
        if (targetId == null) {
            Text.msg(sender, "&cPlayer not found.");
            return true;
        }
        Double amount = parse(args[2]);
        double limit = args[0].equalsIgnoreCase("set") ? MAX_BALANCE : MAX_TRANSACTION;
        if (amount == null || amount < 0.0D || amount > limit) {
            Text.msg(sender, "&cInvalid amount. Use a finite value up to $" + format(limit) + ".");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> setNormalBalance(targetId, normalBalance(targetId) + amount);
            case "take" -> setNormalBalance(targetId, Math.max(0.0D, normalBalance(targetId) - amount));
            case "set" -> setNormalBalance(targetId, amount);
            default -> {
                Text.msg(sender, "&cUnknowne actie.");
                return true;
            }
        }
        Text.msg(sender, "&aEconomy bijgewerkt.");
        return true;
    }

    private boolean adminBalanceCommand(CommandSender sender, String[] args) {
        UUID targetId;
        String targetName;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cGebruik: /eco adminbal <player> [amount]");
                return true;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
        } else {
            targetId = findPlayerId(args[1]);
            if (targetId == null) {
                Text.msg(sender, "&cPlayer not found.");
                return true;
            }
            targetName = playerName(targetId);
        }

        if (args.length >= 3) {
            Double amount = parse(args[2]);
            if (amount == null || amount < 0.0D || amount > MAX_BALANCE) {
                Text.msg(sender, "&cInvalid amount. Use a finite value up to $" + format(MAX_BALANCE) + ".");
                return true;
            }
            setAdminBalance(targetId, amount);
            Text.msg(sender, "&aAdmin balance van &f" + targetName + " &agezet naar &f$" + format(amount) + "&a.");
            return true;
        }

        Text.msg(sender, "&f" + targetName + " &7admin balance: &a$" + format(adminBalance(targetId)) + "&7.");
        return true;
    }

    private boolean clearBalanceCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Text.msg(sender, "&cBevestig met: /eco clear <player> confirm");
            return true;
        }
        UUID targetId = findPlayerId(args[1]);
        if (targetId == null) {
            Text.msg(sender, "&cPlayer not found.");
            return true;
        }
        setBalance(targetId.toString(), 0.0D);
        Text.msg(sender, "&aBalance van &f" + playerName(targetId) + " &ais gewist naar &f$0.00&a.");
        return true;
    }

    private boolean resetAllBalancesCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            Text.msg(sender, "&cDit reset ALLE normale balances naar $" + format(START_BALANCE) + " en admin balances naar $" + format(ADMIN_START_BALANCE) + ".");
            Text.msg(sender, "&cBevestig met: /eco resetall confirm");
            return true;
        }
        int normal = 0;
        int admin = 0;
        for (String key : balances.keys()) {
            if (key.startsWith("admin.")) {
                setBalance(key, ADMIN_START_BALANCE);
                admin++;
                continue;
            }
            try {
                UUID.fromString(key);
                setBalance(key, START_BALANCE);
                normal++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        Text.msg(sender, "&aEconomy reset compleet: &f" + normal + " &anormale balances en &f" + admin + " &aadmin balances.");
        return true;
    }

    private boolean quicksell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cAlleen players.");
            return true;
        }
        if (!canUseQuickSell(player)) {
            Text.msg(player, "&cJe kunt QuickSell niet gebruiken in adminmode, creative/spectator, BedWars of testworld.");
            return true;
        }
        if (args.length == 0) {
            openQuickSell(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("prices")) {
            quickSellPrices(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("all")) {
            openQuickSell(player);
            Text.msg(player, "&7Sleep alles wat je wilt verkopen naar het QuickSell venster en klik bevestigen.");
            return true;
        }
        if (args[0].equalsIgnoreCase("hand")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!isSellable(hand)) {
                Text.msg(player, "&cJe hand is leeg of niet verkoopbaar.");
                return true;
            }
            openQuickSell(player);
            Text.msg(player, "&7Sleep je hand-item naar het QuickSell venster en klik bevestigen.");
            return true;
        }
        openQuickSell(player);
        return true;
    }

    private void openQuickSell(Player player) {
        QuickSellMenu holder = new QuickSellMenu();
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8QuickSell"));
        holder.inventory(inventory);
        annotateInventoryValueLore(player.getInventory());
        updateQuickSellControls(player, holder);
        player.openInventory(inventory);
    }

    private void updateQuickSellControls(Player player, QuickSellMenu menu) {
        Inventory inventory = menu.getInventory();
        if (inventory == null) {
            return;
        }
        annotateQuickSellValueLore(player, inventory);
        SaleQuote quote = quote(player, inventory);
        inventory.setItem(QUICKSELL_CANCEL_SLOT, button(Material.RED_STAINED_GLASS_PANE, "&cCancel", List.of("&7Close QuickSell and return all items.")));
        inventory.setItem(QUICKSELL_PRICES_SLOT, button(Material.PAPER, "&ePriceinfo", List.of("&7Klik for de prijzen van je huidige inventory.", "&7Hover items for waarde per stuk en per stack.")));
        inventory.setItem(QUICKSELL_INFO_SLOT, button(Material.EMERALD, "&aTotaal: $" + format(quote.total()), List.of(
            "&7Items in verkoopvak: &f" + quote.amount(),
            "&7Economy multiplier: &f" + format(quickSellMultiplier()) + "x",
            "&7Sleep items uit je inventory naar boven.",
            "&7Controleer de waarde en bevestig rechts."
        )));
        inventory.setItem(QUICKSELL_CONFIRM_SLOT, button(Material.GREEN_STAINED_GLASS_PANE, "&aBevestig verkoop", List.of(
            "&7Items: &f" + quote.amount(),
            "&7Ontvangst: &a$" + format(quote.total()),
            quote.amount() <= 0 ? "&cSleep eerst items naar het venster." : "&eKlik om deze items te verkopen."
        )));
        annotateInventoryValueLore(player.getInventory());
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

    private SaleQuote quote(Player player, Inventory inventory) {
        double total = 0.0D;
        int amount = 0;
        Material icon = Material.EMERALD;
        for (int slot = 0; slot < QUICKSELL_SELL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isSellable(item)) {
                continue;
            }
            amount += item.getAmount();
            total += quickSellValue(player, item);
            icon = item.getType();
        }
        return new SaleQuote("QuickSell selectie", icon, amount, total);
    }

    private void executeQuickSellBasket(Player player, QuickSellMenu menu) {
        if (!canUseQuickSell(player)) {
            Text.msg(player, "&cJe kunt QuickSell niet gebruiken in adminmode, creative/spectator, BedWars of testworld.");
            returnQuickSellItems(player, menu.getInventory());
            menu.completed(true);
            player.closeInventory();
            return;
        }
        Inventory inventory = menu.getInventory();
        SaleQuote quote = quote(player, inventory);
        if (quote.amount() <= 0 || quote.total() <= 0.0D) {
            Text.msg(player, "&cSleep eerst items naar het QuickSell venster.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 0.65F);
            updateQuickSellControls(player, menu);
            return;
        }

        for (int slot = 0; slot < QUICKSELL_SELL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isSellable(item)) {
                continue;
            }
            if (!isSandboxAdmin(player)) {
                recordMarketSale(item.getType(), item.getAmount(), quickSellValue(player, item), "quicksell basket");
            }
            inventory.setItem(slot, null);
        }
        deposit(player.getUniqueId(), quote.total(), "quicksell basket");
        awardEconomyXp(player, Math.max(8, Math.min(250, quote.amount() / 3 + (int) Math.round(quote.total() / 25.0D))), "quicksell");
        menu.completed(true);
        restoreValueLore(player.getInventory());
        Text.msg(player, "&aQuickSell: &f" + quote.amount() + " items &7-> &a$" + format(quote.total()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.35F);
        player.closeInventory();
    }

    private void cancelQuickSell(Player player, QuickSellMenu menu) {
        returnQuickSellItems(player, menu.getInventory());
        menu.completed(true);
        restoreValueLore(player.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6F, 1.0F);
        player.closeInventory();
    }

    private void returnQuickSellItems(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        for (int slot = 0; slot < QUICKSELL_SELL_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            restoreValueLore(item);
            inventory.setItem(slot, null);
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private double quickSellValue(Player player, ItemStack item) {
        if (!isSellable(item)) {
            return 0.0D;
        }
        return item.getAmount() * quickSellPrice(player, item);
    }

    private List<Material> sellableMaterials(Player player) {
        return java.util.Arrays.stream(player.getInventory().getContents())
            .filter(this::isSellable)
            .map(ItemStack::getType)
            .distinct()
            .sorted(Comparator.comparing((Material material) -> quickSellPrice(material)).reversed().thenComparing(Material::name))
            .toList();
    }

    private boolean isSellable(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0
            && !isMechanicsGuide(item) && !isBossShard(item)
            && (MitchSMP.gameplay() == null || !MitchSMP.gameplay().isTradeRestricted(item));
    }

    private boolean isBossShard(ItemStack item) {
        try {
            return MitchSMP.bossShards() != null && MitchSMP.bossShards().isShard(item);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private void annotateInventoryValueLore(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (ItemStack item : inventory.getContents()) {
            annotateValueLore(item);
        }
    }

    private void annotateQuickSellValueLore(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < QUICKSELL_SELL_SLOTS; slot++) {
            annotateValueLore(player, inventory.getItem(slot));
        }
    }

    private void annotateValueLore(ItemStack item) {
        annotateValueLore(null, item);
    }

    private void annotateValueLore(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = new ArrayList<>();
        if (meta.getLore() != null) {
            lore.addAll(meta.getLore());
        }
        lore.removeIf(this::isQuickSellLore);
        if (isMechanicsGuide(item)) {
            lore.add(Text.color("&8" + QUICKSELL_LORE_MARKER + " &cNiet verkoopbaar."));
            meta.setLore(lore);
            item.setItemMeta(meta);
            return;
        }
        lore.add(Text.color("&8" + QUICKSELL_LORE_MARKER + " &7Waarde/stuk: &a$" + format(quickSellPrice(player, item))));
        lore.add(Text.color("&8" + QUICKSELL_LORE_MARKER + " &7Stackwaarde: &a$" + format(quickSellValue(player, item))));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private boolean isMechanicsGuide(ItemStack item) {
        if (item == null || (item.getType() != Material.WRITTEN_BOOK && item.getType() != Material.BOOK) || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName() && Text.stripColorCodes(meta.getDisplayName()).equalsIgnoreCase("MitchSMP Mechanics Guide")) {
            return true;
        }
        return meta.getLore() != null && meta.getLore().stream()
            .map(Text::stripColorCodes)
            .anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("server guide") || line.toLowerCase(java.util.Locale.ROOT).contains("niet verkoopbaar"));
    }

    private void restoreValueLore(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (ItemStack item : inventory.getContents()) {
            restoreValueLore(item);
        }
    }

    private void restoreValueLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return;
        }
        List<String> lore = new ArrayList<>(meta.getLore());
        if (!lore.removeIf(this::isQuickSellLore)) {
            return;
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    private boolean isQuickSellLore(String line) {
        if (line == null) {
            return false;
        }
        String stripped = ChatColor.stripColor(line);
        return line.contains(QUICKSELL_LORE_MARKER) || (stripped != null && stripped.contains(QUICKSELL_LORE_MARKER));
    }

    private void quickSellPrices(CommandSender sender) {
        Text.msg(sender, "&aQuickSell prijzen &7(market money multiplier " + format(quickSellMultiplier()) + "x)");
        List<Material> materials = sender instanceof Player player ? sellableMaterials(player) : java.util.Arrays.stream(Material.values()).filter(material -> material != Material.AIR).limit(25).toList();
        if (materials.isEmpty()) {
            Text.msg(sender, "&7Je hebt geen verkoopbare items in je inventory.");
            return;
        }
        for (Material material : materials.stream().limit(25).toList()) {
            EconomyWatchService watch = MitchSMP.economyWatch();
            if (watch != null) {
                Text.msg(sender, watch.explain(material));
            } else {
                Text.msg(sender, "&7" + pretty(material) + ": &a$" + format(quickSellPrice(material)));
            }
        }
    }

    private double quickSellPrice(Material material) {
        EconomyWatchService watch = MitchSMP.economyWatch();
        if (watch != null) {
            return watch.quickSellPrice(material);
        }
        return baseQuickSellPrice(material) * quickSellMultiplier();
    }

    private double quickSellPrice(ItemStack item) {
        if (!isSellable(item)) {
            return 0.0D;
        }
        try {
            if (MitchSMP.corruptedHearts() != null && MitchSMP.corruptedHearts().isCorruptedHeart(item)) {
                return 750.0D * quickSellMultiplier();
            }
        } catch (IllegalStateException ignored) {
        }
        EconomyWatchService watch = MitchSMP.economyWatch();
        if (watch != null) {
            return watch.quickSellPrice(item);
        }
        double base = Math.max(quickSellPrice(item.getType()), gearFloor(item.getType()));
        double enchantBonus = fallbackEnchantValue(item, base);
        ItemMeta meta = item.getItemMeta();
        double rarityBonus = isUnbreakable(meta) ? Math.max(200.0D, base * 0.25D) : 0.0D;
        double floor = gearFloor(item.getType());
        if (floor > 0.0D && enchantBonus > 0.0D) {
            floor *= 1.35D;
        }
        return Math.max(floor, base + enchantBonus + rarityBonus);
    }

    private double quickSellPrice(Player player, ItemStack item) {
        double base = quickSellPrice(item);
        int efficiency = economyPerk(player, "economy_quicksell_efficiency");
        return base * (1.0D + Math.min(15, efficiency) * 0.01D);
    }

    private int economyPerk(Player player, String key) {
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

    private void awardEconomyXp(Player player, int amount, String reason) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            SkillService skills = MitchSMP.skills();
            if (skills != null) {
                skills.addXp(player.getUniqueId(), "economy", amount, reason);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private double baseQuickSellPrice(Material material) {
        String name = material.name();
        if (name.contains("NETHERITE")) {
            return name.contains("SWORD") || name.contains("AXE") || name.contains("CHESTPLATE") ? 850.0D : 650.0D;
        }
        if (name.contains("DIAMOND")) {
            if (name.contains("SWORD") || name.contains("AXE") || name.contains("CHESTPLATE")) {
                return 240.0D;
            }
            if (name.contains("HELMET") || name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("PICKAXE") || name.contains("SHOVEL")) {
                return 180.0D;
            }
        }
        if (name.contains("ORE")) {
            return name.contains("DIAMOND") || name.contains("EMERALD") ? 18.0D : 4.0D;
        }
        if (name.contains("SPAWN_EGG")) {
            return 250.0D;
        }
        return switch (material) {
            case DIAMOND -> 22.0D;
            case EMERALD -> 12.0D;
            case IRON_INGOT -> 3.0D;
            case GOLD_INGOT -> 5.0D;
            case REDSTONE -> 1.25D;
            case GUNPOWDER -> 2.0D;
            case EXPERIENCE_BOTTLE -> 0.8D;
            case TOTEM_OF_UNDYING -> 120.0D;
            case ENCHANTED_GOLDEN_APPLE -> 300.0D;
            case GOLDEN_APPLE -> 18.0D;
            case NETHER_STAR -> 500.0D;
            case DRAGON_EGG -> 2_500.0D;
            case ELYTRA -> 1_500.0D;
            default -> 1.0D;
        };
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

    private double fallbackEnchantValue(ItemStack item, double base) {
        double unit = Math.max(10.0D, base * 0.12D);
        double bonus = 0.0D;
        bonus += enchantLevel(item, Enchantment.SHARPNESS) * unit * 1.25D;
        bonus += enchantLevel(item, Enchantment.PROTECTION) * unit * 1.25D;
        bonus += enchantLevel(item, Enchantment.EFFICIENCY) * unit * 0.90D;
        bonus += enchantLevel(item, Enchantment.UNBREAKING) * unit * 0.75D;
        bonus += enchantLevel(item, Enchantment.FORTUNE) * unit * 1.80D;
        bonus += enchantLevel(item, Enchantment.SILK_TOUCH) * unit * 2.00D;
        bonus += enchantLevel(item, Enchantment.MENDING) * unit * 3.00D;
        bonus += enchantLevel(item, Enchantment.LOOTING) * unit * 1.65D;
        bonus += enchantLevel(item, Enchantment.POWER) * unit * 1.10D;
        bonus += enchantLevel(item, Enchantment.INFINITY) * unit * 2.50D;
        bonus += enchantLevel(item, Enchantment.FLAME) * unit * 1.50D;
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

    private double quickSellMultiplier() {
        EconomyWatchService watch = MitchSMP.economyWatch();
        if (watch != null) {
            return watch.moneyMultiplier();
        }
        List<Map.Entry<UUID, Double>> top = topBalances(250);
        if (top.isEmpty()) {
            return 1.0D;
        }
        double total = top.stream().mapToDouble(Map.Entry::getValue).sum();
        double average = total / top.size();
        if (average <= 2_500.0D) {
            return 1.25D;
        }
        double multiplier = Math.sqrt(5_000.0D / Math.max(5_000.0D, average));
        return Math.max(0.35D, Math.min(1.25D, multiplier));
    }

    private void economyStats(CommandSender sender) {
        List<Map.Entry<UUID, Double>> top = topBalances(250);
        double total = top.stream().mapToDouble(Map.Entry::getValue).sum();
        double average = top.isEmpty() ? 0.0D : total / top.size();
        double richest = top.isEmpty() ? 0.0D : top.get(0).getValue();
        Text.msg(sender, "&aEconomy stats:");
        Text.msg(sender, "&7Tracked players: &f" + top.size());
        Text.msg(sender, "&7Total money: &a$" + format(total));
        Text.msg(sender, "&7Average balance: &a$" + format(average));
        Text.msg(sender, "&7Richest balance: &a$" + format(richest));
        Text.msg(sender, "&7QuickSell multiplier: &f" + format(quickSellMultiplier()) + "x");
        EconomyWatchService watch = MitchSMP.economyWatch();
        if (watch != null) {
            Text.msg(sender, "&7EconomyWatch: &aactive &8(/econwatch stats)");
        } else {
            Text.msg(sender, "&7EconomyWatch: &cinactive");
        }
    }

    private void recordMarketSale(Material material, int amount, double total, String reason) {
        if (amount <= 0) {
            return;
        }
        EconomyWatchService watch = MitchSMP.economyWatch();
        if (watch != null) {
            watch.recordSale(material, amount, total, reason);
        }
    }

    private boolean canUseQuickSell(Player player) {
        if (isSandboxAdmin(player)) {
            return true;
        }
        return player != null
            && !MitchSMP.permissions().isAdminMode(player)
            && player.getGameMode() != GameMode.CREATIVE
            && player.getGameMode() != GameMode.SPECTATOR
            && !isBedWarsWorld(player);
    }

    private boolean isBedWarsWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        String world = player.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        return world.startsWith("bedwars_") || world.startsWith("bw_") || world.contains("bedwars");
    }

    private boolean isSandboxAdmin(Player player) {
        return player != null
            && player.getWorld() != null
            && player.getWorld().getName().toLowerCase(java.util.Locale.ROOT).startsWith("mitchtest_")
            && MitchSMP.permissions().has(player, "mitchsmp.essentials.admin");
    }

    private String pretty(Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private void ensure(UUID playerId) {
        ensure(playerId.toString(), START_BALANCE);
    }

    private void ensure(String key, double startBalance) {
        if (!balances.contains(key)) {
            balances.set(key, format(startBalance));
            saveBalancesSoon();
        }
    }

    private boolean isAdminWalletActive(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && MitchSMP.permissions().isAdminRestricted(player);
    }

    private String activeBalanceKey(UUID playerId) {
        if (isSandboxWalletActive(playerId)) {
            return sandboxBalanceKey(playerId);
        }
        return isAdminWalletActive(playerId) ? adminBalanceKey(playerId) : playerId.toString();
    }

    private String depositBalanceKey(UUID playerId, String reason) {
        if (isSandboxWalletActive(playerId)) {
            return sandboxBalanceKey(playerId);
        }
        String lowerReason = reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);
        if (lowerReason.startsWith("auction sale")) {
            return playerId.toString();
        }
        return activeBalanceKey(playerId);
    }

    private double activeStartBalance(UUID playerId) {
        if (isSandboxWalletActive(playerId)) {
            return ADMIN_START_BALANCE;
        }
        return isAdminWalletActive(playerId) ? ADMIN_START_BALANCE : START_BALANCE;
    }

    private String activeBalanceLabel(UUID playerId) {
        if (isSandboxWalletActive(playerId)) {
            return "Sandbox balance";
        }
        return isAdminWalletActive(playerId) ? "Admin balance" : "Balance";
    }

    private String adminBalanceKey(UUID playerId) {
        return "admin.shared";
    }

    private boolean isSandboxWalletActive(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return isSandboxAdmin(player);
    }

    private String sandboxBalanceKey(UUID playerId) {
        return "sandbox." + playerId;
    }

    private UUID findPlayerId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        UUID known = MitchSMP.permissions().findKnownPlayer(name);
        if (known != null) {
            return known;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getUniqueId() != null) {
            return cached.getUniqueId();
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String playerName(UUID id) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? id.toString() : offline.getName();
    }

    private double adminBalance(UUID playerId) {
        String key = adminBalanceKey(playerId);
        ensure(key, ADMIN_START_BALANCE);
        return Math.max(0.0D, balances.getDouble(key, ADMIN_START_BALANCE));
    }

    private void setAdminBalance(UUID playerId, double amount) {
        setBalance(adminBalanceKey(playerId), amount);
    }

    private double normalBalance(UUID playerId) {
        String key = playerId.toString();
        ensure(key, START_BALANCE);
        return Math.max(0.0D, balances.getDouble(key, START_BALANCE));
    }

    private void setNormalBalance(UUID playerId, double amount) {
        setBalance(playerId.toString(), amount);
        log("ADMIN_SET_NORMAL", null, playerId, amount, "admin economy command");
    }

    private void setBalance(String key, double amount) {
        balances.set(key, format(clampBalance(amount)));
        saveBalancesSoon();
    }

    private void saveBalancesSoon() {
        balances.saveSoon(this, 60L);
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private Map.Entry<UUID, Double> entry(String key) {
        if (key.startsWith("admin.")) {
            return null;
        }
        try {
            UUID id = UUID.fromString(key);
            return new AbstractMap.SimpleEntry<>(id, Math.max(0.0D, balances.getDouble(key, START_BALANCE)));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Double parse(String input) {
        try {
            double parsed = Double.parseDouble(input);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean validTransaction(double amount) {
        return Double.isFinite(amount) && amount > 0.0D && amount <= MAX_TRANSACTION;
    }

    private double clampBalance(double amount) {
        if (!Double.isFinite(amount)) {
            return START_BALANCE;
        }
        return Math.max(0.0D, Math.min(MAX_BALANCE, amount));
    }

    private String format(double amount) {
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    private static class QuickSellMenu implements InventoryHolder {
        private Inventory inventory;
        private boolean completed;

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        void completed(boolean completed) {
            this.completed = completed;
        }

        boolean completed() {
            return completed;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record SaleQuote(String label, Material icon, int amount, double total) {
    }

    private void log(String type, UUID from, UUID to, double amount, String reason) {
        String line = Instant.now() + " " + type + " from=" + from + " to=" + to + " amount=" + format(amount) + " reason=" + reason + System.lineSeparator();
        if (transactionWriter != null) {
            transactionWriter.execute(() -> {
                try {
                    Files.createDirectories(transactions.getParent());
                    Files.writeString(transactions, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    getLogger().warning("Could not write transaction log: " + exception.getMessage());
                }
            });
        }
        if (amount >= 25_000.0D && (type.equals("TRANSFER") || type.equals("ADMIN_SET_NORMAL") || type.equals("DEPOSIT") && reason != null && reason.toLowerCase(Locale.ROOT).contains("quicksell"))) {
            String alert = "&c[Economy Alert] &f" + type + " &7$" + format(amount) + " &8from=" + from + " to=" + to + " reason=" + reason;
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (MitchSMP.permissions().has(staff, "mitchsmp.economy.alerts")) {
                    Text.msg(staff, alert);
                }
            }
            getLogger().warning(Text.stripColorCodes(alert));
        }
    }
}



