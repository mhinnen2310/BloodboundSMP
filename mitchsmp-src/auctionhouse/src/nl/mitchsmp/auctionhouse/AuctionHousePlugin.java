package nl.mitchsmp.auctionhouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import nl.mitchsmp.core.api.EconomyService;
import nl.mitchsmp.core.api.EconomyWatchService;
import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionHousePlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final int PAGE_SIZE = 45;
    private static final int INVENTORY_SIZE = 54;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 46;
    private static final int SEARCH_SLOT = 47;
    private static final int SORT_SLOT = 49;
    private static final int MY_LISTINGS_SLOT = 50;
    private static final int CLEAR_SLOT = 51;
    private static final int NEXT_SLOT = 53;
    private static final long LISTING_LIFETIME_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final double ABSOLUTE_MAX_PRICE = 1_000_000_000.0D;
    private static final int MAX_SERIALIZED_ITEM_BYTES = 262_144;

    private final Map<UUID, ViewState> states = new HashMap<>();
    private final Map<UUID, Boolean> awaitingSearch = new HashMap<>();
    private final Map<UUID, Boolean> awaitingListingPrice = new HashMap<>();
    private final Map<UUID, ItemStack> pendingListingItems = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> fakeSearchSigns = new HashMap<>();
    private final Map<UUID, Material> fakeSearchMaterials = new HashMap<>();
    private PropertiesFile data;
    private PropertiesFile sandboxData;
    private final ThreadLocal<PropertiesFile> scopedStore = new ThreadLocal<>();
    private final Object listingLock = new Object();
    private long auditSequence;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("listings.properties"));
        sandboxData = new PropertiesFile(getDataFolder().toPath().resolve("sandbox-listings.properties"));
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("auctionhouse") != null) {
            getCommand("auctionhouse").setExecutor(this);
            getCommand("auctionhouse").setTabCompleter(this);
        }
        if (getCommand("ahadmin") != null) {
            getCommand("ahadmin").setExecutor(this);
            getCommand("ahadmin").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::expireListings, 100L, 20L * 60L * 5L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PropertiesFile previousStore = pushStore(sender instanceof Player player ? player : null);
        try {
            if (command.getName().equalsIgnoreCase("ahadmin")) {
                return adminCommand(sender, args);
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                if (sender instanceof Player player) {
                    if (blockAdminMode(player)) {
                        return true;
                    }
                    if (args.length == 0) {
                        state(player).search("");
                        state(player).page(0);
                    }
                    open(player, state(player).page());
                } else {
                    list(sender);
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("help")) {
                help(sender);
                return true;
            }

            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cAlleen players.");
                return true;
            }

            if (blockAdminMode(player)) {
                return true;
            }

            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("sell")) {
                sell(player, args);
                return true;
            }
            if (action.equals("buy")) {
                buy(player, args);
                return true;
            }
            if (action.equals("cancel")) {
                cancel(player, args);
                return true;
            }
            if (action.equals("search")) {
                search(player, args);
                return true;
            }
            if (action.equals("sort")) {
                sort(player, args);
                return true;
            }
            if (action.equals("clear") || action.equals("reset")) {
                state(player).search("");
                state(player).sort(SortMode.RECENT);
                open(player, 0);
                return true;
            }

            help(sender);
            return true;
        } finally {
            popStore(previousStore);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("ahadmin")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.auctionhouse.admin")) {
                return List.of();
            }
            if (args.length == 1) {
                return Tab.complete(args[0], "inspect", "listings", "remove", "logs");
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("inspect") || args[0].equalsIgnoreCase("remove"))) {
                return listingIds(args[1], sender, false);
            }
            return List.of();
        }
        if (args.length == 1) {
            return Tab.complete(args[0], "sell", "list", "search", "sort", "clear", "buy", "cancel", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return Tab.amounts(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sort")) {
            return Tab.complete(args[1], "recent", "price_low", "price_high");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            return Tab.complete(args[1], "clear");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            return listingIds(args[1], sender, false);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            return listingIds(args[1], sender, true);
        }
        return List.of();
    }

    private boolean blockAdminMode(Player player) {
        if (isSandboxAdmin(player)) {
            return false;
        }
        if (!MitchSMP.permissions().isAdminMode(player) && !isTestWorld(player)) {
            return false;
        }
        Text.msg(player, "&cAuctionHouse is geblokkeerd in adminmode/testworld zodat staff- of test-items niet naar de SMP lekken.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
        return true;
    }

    private boolean isTestWorld(Player player) {
        return player != null
            && player.getWorld() != null
            && player.getWorld().getName().toLowerCase(Locale.ROOT).startsWith("mitchtest_");
    }

    private boolean isSandboxAdmin(Player player) {
        return isTestWorld(player) && MitchSMP.permissions().has(player, "mitchsmp.essentials.admin");
    }

    private PropertiesFile store() {
        PropertiesFile scoped = scopedStore.get();
        return scoped == null ? data : scoped;
    }

    private PropertiesFile storeFor(Player player) {
        return isSandboxAdmin(player) ? sandboxData : data;
    }

    private PropertiesFile pushStore(Player player) {
        PropertiesFile previous = scopedStore.get();
        scopedStore.set(storeFor(player));
        return previous;
    }

    private void popStore(PropertiesFile previous) {
        if (previous == null) {
            scopedStore.remove();
        } else {
            scopedStore.set(previous);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverRefunds(event.getPlayer());
    }

    @EventHandler
    public void onSignSearch(SignChangeEvent event) {
        Player player = event.getPlayer();
        PropertiesFile previousStore = pushStore(player);
        try {
            if (Boolean.TRUE.equals(awaitingListingPrice.remove(player.getUniqueId()))) {
                event.setCancelled(true);
                handleListingPriceInput(player, event.getLine(0) == null ? "" : event.getLine(0).trim());
                return;
            }
            if (!Boolean.TRUE.equals(awaitingSearch.remove(player.getUniqueId()))) {
                return;
            }
            event.setCancelled(true);
            handleSearchInput(player, event.getLine(0) == null ? "" : event.getLine(0).trim());
        } finally {
            popStore(previousStore);
        }
    }

    @EventHandler
    public void onVirtualSignSearch(UncheckedSignChangeEvent event) {
        Player player = event.getPlayer();
        PropertiesFile previousStore = pushStore(player);
        try {
            if (Boolean.TRUE.equals(awaitingListingPrice.remove(player.getUniqueId()))) {
                event.setCancelled(true);
                List<?> lines = event.lines();
                String message = lines.isEmpty() ? "" : plainLine(lines.get(0)).trim();
                handleListingPriceInput(player, message);
                return;
            }
            if (!Boolean.TRUE.equals(awaitingSearch.remove(player.getUniqueId()))) {
                return;
            }

            event.setCancelled(true);
            List<?> lines = event.lines();
            String message = lines.isEmpty() ? "" : plainLine(lines.get(0)).trim();
            handleSearchInput(player, message);
        } finally {
            popStore(previousStore);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        PropertiesFile previousStore = pushStore(event.getWhoClicked() instanceof Player player ? player : null);
        try {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MyListingsMenu myMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                if (blockAdminMode(player)) {
                    player.closeInventory();
                    return;
                }
                handleMyListingsClick(player, myMenu, event);
                Bukkit.getScheduler().runTaskLater(this, player::updateInventory, 1L);
            }
            return;
        }
        if (!(top.getHolder() instanceof AuctionMenu menu)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (blockAdminMode(player)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }

        if (slot == PREVIOUS_SLOT) {
            open(player, menu.page() - 1);
            return;
        }
        if (slot == BACK_SLOT) {
            ViewState state = state(player);
            if (!state.search().isBlank() || state.sort() != SortMode.RECENT) {
                state.search("");
                state.sort(SortMode.RECENT);
                open(player, 0);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot == SEARCH_SLOT) {
            requestSearch(player, true);
            return;
        }
        if (slot == SORT_SLOT) {
            ViewState state = state(player);
            state.sort(state.sort().next());
            open(player, 0);
            return;
        }
        if (slot == MY_LISTINGS_SLOT) {
            openMyListings(player, 0);
            return;
        }
        if (slot == CLEAR_SLOT) {
            ViewState state = state(player);
            state.search("");
            state.sort(SortMode.RECENT);
            open(player, 0);
            return;
        }
        if (slot == NEXT_SLOT) {
            open(player, menu.page() + 1);
            return;
        }
        if (slot >= PAGE_SIZE) {
            return;
        }

        Listing listing = listingAt(menu, slot);
        if (listing == null) {
            open(player, menu.page());
            return;
        }

        boolean changed = event.isShiftClick() ? cancelListing(player, listing) : buyListing(player, listing);
        if (changed) {
            open(player, menu.page());
        }
        Bukkit.getScheduler().runTaskLater(this, player::updateInventory, 1L);
        } finally {
            popStore(previousStore);
        }
    }

    private void open(Player player, int page) {
        if (blockAdminMode(player)) {
            return;
        }
        ViewState state = state(player);
        List<Listing> current = listings(state.search(), state.sort());
        int maxPage = maxPage(current);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        state.page(clampedPage);

        AuctionMenu holder = new AuctionMenu(clampedPage, state.search(), state.sort());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Text.color("&8AuctionHouse &7" + (clampedPage + 1) + "/" + (maxPage + 1)));
        holder.inventory(inventory);

        int offset = clampedPage * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && offset + slot < current.size(); slot++) {
            inventory.setItem(slot, listingIcon(current.get(offset + slot), player));
        }

        inventory.setItem(PREVIOUS_SLOT, button(clampedPage > 0 ? Material.ARROW : Material.BARRIER, clampedPage > 0 ? "&aPrevious page" : "&8No previous page", List.of("&7Click to go back.")));
        inventory.setItem(BACK_SLOT, button(Material.OAK_SIGN, "&eTerug", List.of("&7Reset filters of sluit de AH.")));
        inventory.setItem(SEARCH_SLOT, button(Material.COMPASS, "&bSearch", List.of("&7Huidig: &f" + blankSearch(state.search()), "&7Klik om direct een sign te openen.", "&7Typ clear om te resetten.")));
        inventory.setItem(SORT_SLOT, button(Material.EMERALD, "&aSorteren", List.of("&7Huidig: &f" + state.sort().display(), "&7Klik om te wisselen.", "&7Volgende: &f" + state.sort().next().display())));
        inventory.setItem(MY_LISTINGS_SLOT, button(Material.CHEST, "&6Mijn listings", List.of("&7Bekijk, annuleer of maak listings.", "&7Slots: &f" + ownListingCount(player) + "/" + listingLimit(player))));
        inventory.setItem(CLEAR_SLOT, button(Material.PAPER, "&eFilters resetten", List.of("&7Zet zoeken uit en sorteer op recent.")));
        inventory.setItem(NEXT_SLOT, button(clampedPage < maxPage ? Material.ARROW : Material.BARRIER, clampedPage < maxPage ? "&aNext page" : "&8No next page", List.of("&7Click to continue.")));

        if (current.isEmpty()) {
            inventory.setItem(22, button(Material.PAPER, "&7No listings found", List.of("&7Adjust your search filter.", "&7List: &f/ah sell <price>")));
        }

        player.openInventory(inventory);
    }

    private ItemStack listingIcon(Listing listing, Player viewer) {
        ItemStack icon = listing.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
            boolean owner = listing.seller().equals(viewer.getUniqueId());
            meta.setDisplayName(Text.color("&a#" + listing.id() + " &f" + itemName(listing.item())));
            meta.setLore(colorList(
                "&7Amount: &f" + listing.item().getAmount(),
                "&7Price: &a$" + format(listing.price()),
                "&7Verkoper: &f" + safeName(seller),
                "&7Sort-id: &f" + listing.id(),
                owner ? "&eShift-click om te annuleren." : "&eKlik om te kopen."
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void openMyListings(Player player, int page) {
        if (blockAdminMode(player)) {
            return;
        }
        List<Listing> own = ownListings(player);
        int allowedSlots = listingLimit(player);
        int maxPage = maxPage(own, allowedSlots);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        MyListingsMenu holder = new MyListingsMenu(clampedPage);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Text.color("&8Mijn AH Listings"));
        holder.inventory(inventory);

        int offset = clampedPage * allowedSlots;
        for (int slot = 0; slot < allowedSlots && offset + slot < own.size(); slot++) {
            Listing listing = own.get(offset + slot);
            holder.listing(slot, listing.id());
            inventory.setItem(slot, listingIcon(listing, player));
        }
        for (int slot = allowedSlots; slot < PAGE_SIZE; slot++) {
            inventory.setItem(slot, button(Material.RED_STAINED_GLASS_PANE, "&cGesloten listing slot", List.of("&7Upgrade je rank for meer AH slots.", "&7Default 18, VIP 27, MVP 36, Legend 45.")));
        }

        inventory.setItem(PREVIOUS_SLOT, button(clampedPage > 0 ? Material.ARROW : Material.BARRIER, clampedPage > 0 ? "&aPrevious page" : "&8No previous page", List.of("&7Click to go back.")));
        inventory.setItem(BACK_SLOT, button(Material.OAK_SIGN, "&eTerug naar AH", List.of("&7Open het hoofdmenu.")));
        inventory.setItem(SORT_SLOT, button(Material.EMERALD, "&aNew listing", List.of("&7Click an item in your inventory,", "&7or place a cursor item on a free slot.", "&7Then type the price on a sign.", "&7Slots: &f" + own.size() + "/" + listingLimit(player))));
        inventory.setItem(NEXT_SLOT, button(clampedPage < maxPage ? Material.ARROW : Material.BARRIER, clampedPage < maxPage ? "&aNext page" : "&8No next page", List.of("&7Click to continue.")));
        if (own.isEmpty()) {
            inventory.setItem(22, button(Material.PAPER, "&7Nog geen listings", List.of("&7Klik onderin op &fNew listing&7.")));
        }

        player.openInventory(inventory);
    }

    private void handleMyListingsClick(Player player, MyListingsMenu menu, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT) {
            openMyListings(player, menu.page() - 1);
            return;
        }
        if (slot == BACK_SLOT) {
            open(player, state(player).page());
            return;
        }
        if (slot == SORT_SLOT) {
            Text.msg(player, "&eKlik een item uit je inventory of plaats een cursor-item op een vrij AH-slot.");
            return;
        }
        if (slot == NEXT_SLOT) {
            openMyListings(player, menu.page() + 1);
            return;
        }
        if (slot >= event.getView().getTopInventory().getSize()) {
            stageClickedInventoryListing(player, event);
            return;
        }
        if (slot >= listingLimit(player)) {
            return;
        }
        Integer id = menu.listing(slot);
        if (id == null) {
            if (!stageCursorListing(player, event)) {
                Text.msg(player, "&7Plaats een item op dit vrije slot om een listing te maken.");
            }
            return;
        }
        if (id == null) {
            return;
        }
        Listing listing = listing(String.valueOf(id));
        if (listing != null && cancelListing(player, listing)) {
            openMyListings(player, menu.page());
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(colorList(lore.toArray(String[]::new)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> colorList(String... lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(Text.color(line));
        }
        return result;
    }

    private void sell(Player player, String[] args) {
        if (args.length < 2) {
            Text.msg(player, "&cGebruik: /ah sell <price>");
            return;
        }
        Double price = parse(args[1]);
        if (price == null || price <= 0.0D) {
            Text.msg(player, "&cInvalid price.");
            return;
        }
        createListing(player, price);
    }

    private boolean createListing(Player player, double price) {
        if (blockAdminMode(player)) {
            return false;
        }
        ItemStack item = pendingListingItems.remove(player.getUniqueId());
        boolean pending = item != null;
        if (item == null) {
            item = player.getInventory().getItemInMainHand();
        }
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            Text.msg(player, "&cKies een item om te verkopen.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
            return false;
        }
        String validation = validateItem(item);
        if (validation != null) {
            Text.msg(player, "&cThis item cannot be listed: &f" + validation + "&c.");
            audit("REJECT", player.getUniqueId(), -1, price, validation + " item=" + itemName(item));
            if (pending) {
                give(player, item);
            }
            return false;
        }
        if (!Double.isFinite(price) || price > ABSOLUTE_MAX_PRICE) {
            Text.msg(player, "&cPrice is invalid or exceeds the AuctionHouse safety limit.");
            if (pending) {
                give(player, item);
            }
            return false;
        }
        if (isMechanicsGuide(item)) {
            Text.msg(player, "&cDit guide boek heeft geen economische waarde en kan niet op de Auction House.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
            if (pending) {
                give(player, item);
            }
            return false;
        }
        int own = ownListingCount(player);
        int limit = listingLimit(player);
        if (own >= limit) {
            Text.msg(player, "&cJe hebt je AH listing limiet bereikt: &f" + own + "/" + limit + "&c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.6F);
            if (pending) {
                give(player, item);
            }
            return false;
        }
        long now = System.currentTimeMillis();
        Listing listing;
        synchronized (listingLock) {
            int id = nextAvailableId();
            listing = new Listing(id, player.getUniqueId(), price, now, now + LISTING_LIFETIME_MILLIS, item.clone());
            store().set("listing." + id, listing.encode());
            store().set("next-id", id + 1);
            store().save();
        }
        if (!pending) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        audit("CREATE", player.getUniqueId(), listing.id(), price, fingerprint(item));
        evaluateListingRisk(player, listing);
        Text.msg(player, "&aListing #" + listing.id() + " created for &f$" + format(price) + "&a. Expires in 7 days.");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.3F);
        return true;
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
            .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("server guide") || line.toLowerCase(Locale.ROOT).contains("niet verkoopbaar"));
    }

    private void buy(Player player, String[] args) {
        if (args.length < 2) {
            Text.msg(player, "&cGebruik: /ah buy <id>");
            return;
        }
        Listing listing = listing(args[1]);
        if (listing == null) {
            Text.msg(player, "&cListing not found.");
            return;
        }
        buyListing(player, listing);
    }

    private boolean buyListing(Player player, Listing listing) {
        if (blockAdminMode(player)) {
            return false;
        }
        if (listing.seller().equals(player.getUniqueId())) {
            Text.msg(player, "&cYou cannot buy your own listing. Use &f/ah cancel " + listing.id() + " &cof shift-click in de UI.");
            return false;
        }

        EconomyService economy = MitchSMP.economy();
        if (economy == null) {
            Text.msg(player, "&cEconomy is niet geladen.");
            return false;
        }
        Listing claimed;
        synchronized (listingLock) {
            claimed = listing(String.valueOf(listing.id()));
            if (claimed == null) {
                Text.msg(player, "&cThis listing was already bought, cancelled or expired.");
                return false;
            }
            String validation = validateItem(claimed.item());
            if (validation != null) {
                store().set("listing." + claimed.id(), null);
                queueRefund(claimed.seller(), claimed.id(), claimed.item(), "invalid-on-buy:" + validation);
                store().save();
                Text.msg(player, "&cThis listing failed validation and was removed. No money was charged.");
                return false;
            }
            if (!hasInventorySpace(player, claimed.item())) {
                Text.msg(player, "&cYour inventory does not have enough room for this listing.");
                return false;
            }
            store().set("listing." + claimed.id(), null);
            store().save();
            if (!economy.withdraw(player.getUniqueId(), claimed.price(), "auction buy #" + claimed.id())) {
                store().set("listing." + claimed.id(), claimed.encode());
                store().save();
                Text.msg(player, "&cYou do not have enough money.");
                return false;
            }
        }

        economy.deposit(claimed.seller(), claimed.price(), "auction sale #" + claimed.id());
        give(player, claimed.item());
        recordPairTransaction(claimed.seller(), player.getUniqueId(), claimed.price(), claimed.item());
        audit("BUY", player.getUniqueId(), claimed.id(), claimed.price(), "seller=" + claimed.seller() + " item=" + fingerprint(claimed.item()));
        Text.msg(player, "&aListing purchased for &f$" + format(listing.price()) + "&a.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
        return true;
    }

    private void cancel(Player player, String[] args) {
        if (args.length < 2) {
            Text.msg(player, "&cGebruik: /ah cancel <id>");
            return;
        }
        Listing listing = listing(args[1]);
        if (listing == null) {
            Text.msg(player, "&cListing not found.");
            return;
        }
        cancelListing(player, listing);
    }

    private boolean cancelListing(Player player, Listing listing) {
        if (blockAdminMode(player)) {
            return false;
        }
        if (!listing.seller().equals(player.getUniqueId()) && !MitchSMP.permissions().has(player, "mitchsmp.auctionhouse.admin")) {
            Text.msg(player, "&cDit is niet jouw listing.");
            return false;
        }
        synchronized (listingLock) {
            Listing current = listing(String.valueOf(listing.id()));
            if (current == null) {
                Text.msg(player, "&cThis listing is no longer available.");
                return false;
            }
            store().set("listing." + current.id(), null);
            queueRefund(current.seller(), current.id(), current.item(), "cancelled");
            store().save();
            if (current.seller().equals(player.getUniqueId())) {
                deliverRefunds(player);
            }
            audit("CANCEL", player.getUniqueId(), current.id(), current.price(), "seller=" + current.seller());
        }
        Text.msg(player, "&aListing cancelled.");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8F, 1.0F);
        return true;
    }

    private void search(Player player, String[] args) {
        if (args.length < 2) {
            requestSearch(player, false);
            return;
        }
        String query = join(args, 1);
        state(player).search(query.equalsIgnoreCase("clear") || query.equalsIgnoreCase("reset") ? "" : query);
        open(player, 0);
    }

    private void sort(Player player, String[] args) {
        if (args.length < 2) {
            Text.msg(player, "&cGebruik: /ah sort <recent|price_low|price_high>");
            return;
        }
        state(player).sort(SortMode.parse(args[1]));
        open(player, 0);
    }

    private void list(CommandSender sender) {
        List<Listing> current = listings("", SortMode.RECENT);
        if (current.isEmpty()) {
            Text.msg(sender, "&7AuctionHouse is leeg.");
            return;
        }
        Text.msg(sender, "&aAuctionHouse listings:");
        for (Listing listing : current.stream().limit(10).toList()) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
            Text.msg(sender, "&7#" + listing.id() + " &f" + itemName(listing.item()) + " x" + listing.item().getAmount() + " &7- &a$" + format(listing.price()) + " &7- &f" + safeName(seller));
        }
    }

    private void help(CommandSender sender) {
        Text.msg(sender, "&7/ah opent de UI. Filters: &f/ah search <term>&7, &f/ah sort <recent|price_low|price_high>&7.");
        Text.msg(sender, "&7Plaatsen: &f/ah sell <price>&7. Snel: &f/ah buy <id>&7, &f/ah cancel <id>&7.");
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.auctionhouse.admin")) {
            Text.msg(sender, "&cYou do not have permission to inspect the AuctionHouse.");
            return true;
        }
        String action = args.length == 0 ? "listings" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("listings")) {
            List<Listing> current = listings("", SortMode.RECENT);
            Text.msg(sender, "&6AuctionHouse listings &7(" + current.size() + "):");
            for (Listing listing : current.stream().limit(25).toList()) {
                Text.msg(sender, "&7#" + listing.id() + " &f" + itemName(listing.item()) + " x" + listing.item().getAmount()
                    + " &8| &a$" + format(listing.price()) + " &8| &7seller=" + safeName(Bukkit.getOfflinePlayer(listing.seller())));
            }
            return true;
        }
        if (action.equals("logs")) {
            List<String> logs = store().keys().stream().filter(key -> key.startsWith("audit.")).sorted(Comparator.reverseOrder()).limit(25).map(key -> store().getString(key, "")).toList();
            Text.msg(sender, "&6Recent AuctionHouse audit records &7(" + logs.size() + "):");
            logs.forEach(line -> Text.msg(sender, "&7- &f" + line));
            return true;
        }
        if ((action.equals("inspect") || action.equals("remove")) && args.length >= 2) {
            Listing listing = listing(args[1]);
            if (listing == null) {
                Text.msg(sender, "&cListing not found.");
                return true;
            }
            if (action.equals("inspect")) {
                double reference = appraisedValue(listing.item());
                Text.msg(sender, "&6Listing #" + listing.id() + " inspection:");
                Text.msg(sender, "&7Seller: &f" + safeName(Bukkit.getOfflinePlayer(listing.seller())) + " &8(" + listing.seller() + ")");
                Text.msg(sender, "&7Item: &f" + itemName(listing.item()) + " x" + listing.item().getAmount() + " &8| hash=" + fingerprint(listing.item()));
                Text.msg(sender, "&7Price: &a$" + format(listing.price()) + " &8| &7reference: &a$" + format(reference));
                Text.msg(sender, "&7Created: &f" + (listing.createdAt() <= 0L ? "legacy" : Instant.ofEpochMilli(listing.createdAt())) + " &8| &7expires: &f" + (listing.expiresAt() == Long.MAX_VALUE ? "legacy/no-expiry" : Instant.ofEpochMilli(listing.expiresAt())));
                String validation = validateItem(listing.item());
                Text.msg(sender, validation == null ? "&aValidation PASS" : "&cValidation FAILED: &f" + validation);
                return true;
            }
            synchronized (listingLock) {
                Listing current = listing(args[1]);
                if (current == null) {
                    Text.msg(sender, "&cListing was already removed.");
                    return true;
                }
                store().set("listing." + current.id(), null);
                queueRefund(current.seller(), current.id(), current.item(), "admin-remove");
                store().save();
                audit("ADMIN_REMOVE", sender instanceof Player player ? player.getUniqueId() : null, current.id(), current.price(), "seller=" + current.seller());
                Player seller = Bukkit.getPlayer(current.seller());
                if (seller != null) {
                    deliverRefunds(seller);
                }
            }
            Text.msg(sender, "&aListing removed and queued for seller refund.");
            return true;
        }
        Text.msg(sender, "&cUsage: /ahadmin <inspect|listings|remove|logs> [id]");
        return true;
    }

    private String validateItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0 || item.getAmount() > 64) {
            return "invalid item amount";
        }
        if (MitchSMP.gameplay() != null && MitchSMP.gameplay().isTradeRestricted(item)) {
            return "trade-restricted reward item: " + MitchSMP.gameplay().restrictionReason(item);
        }
        String type = item.getType().name();
        if (type.equals("BEDROCK") || type.equals("BARRIER") || type.contains("COMMAND_BLOCK") || type.contains("STRUCTURE_BLOCK")
            || type.equals("JIGSAW") || type.equals("DEBUG_STICK") || type.equals("KNOWLEDGE_BOOK") || type.contains("SHULKER_BOX") || type.equals("BUNDLE")) {
            return "blacklisted material";
        }
        byte[] serialized;
        try {
            serialized = item.serializeAsBytes();
            if (serialized.length == 0 || serialized.length > MAX_SERIALIZED_ITEM_BYTES) {
                return "unsafe metadata size";
            }
            ItemStack decoded = ItemStack.deserializeBytes(serialized);
            if (decoded == null || decoded.getType() != item.getType() || decoded.getAmount() != item.getAmount()) {
                return "corrupt item serialization";
            }
        } catch (RuntimeException exception) {
            return "unreadable item metadata";
        }
        int customModel = customModelData(item);
        if (customModel >= 910000) {
            boolean validShard = customModel == 910001 && MitchSMP.bossShards() != null && MitchSMP.bossShards().isShard(item);
            boolean validHeart = customModel == 910002 && MitchSMP.corruptedHearts() != null && MitchSMP.corruptedHearts().isCorruptedHeart(item);
            if (!validShard && !validHeart) {
                return "invalid Bloodbound custom item identity";
            }
        }
        return null;
    }

    private int customModelData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return -1;
        }
        try {
            ItemMeta meta = item.getItemMeta();
            Object has = meta.getClass().getMethod("hasCustomModelData").invoke(meta);
            if (has instanceof Boolean present && present) {
                Object value = meta.getClass().getMethod("getCustomModelData").invoke(meta);
                return value instanceof Number number ? number.intValue() : -1;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return -1;
    }

    private double appraisedValue(ItemStack item) {
        if (item == null) {
            return 0.0D;
        }
        try {
            if (MitchSMP.bossShards() != null && MitchSMP.bossShards().isShard(item)) {
                return 2_500.0D * item.getAmount();
            }
            if (MitchSMP.corruptedHearts() != null && MitchSMP.corruptedHearts().isCorruptedHeart(item)) {
                return 750.0D * item.getAmount();
            }
            EconomyWatchService watch = MitchSMP.economyWatch();
            return watch == null ? 0.0D : watch.quickSellPrice(item) * item.getAmount();
        } catch (IllegalStateException exception) {
            return 0.0D;
        }
    }

    private void evaluateListingRisk(Player seller, Listing listing) {
        double reference = Math.max(0.01D, appraisedValue(listing.item()));
        double ratio = listing.price() / reference;
        if (listing.price() >= 100_000.0D || ratio >= 50.0D) {
            String detail = "listing #" + listing.id() + " seller=" + seller.getName() + " price=$" + format(listing.price()) + " reference=$" + format(reference) + " ratio=" + format(ratio) + "x";
            audit("OVERPRICE", seller.getUniqueId(), listing.id(), listing.price(), detail);
            alertStaff("&c[AH Alert] &fSuspicious overprice: &7" + detail);
        }
    }

    private void recordPairTransaction(UUID seller, UUID buyer, double amount, ItemStack item) {
        String first = seller.toString().compareTo(buyer.toString()) <= 0 ? seller.toString() : buyer.toString();
        String second = first.equals(seller.toString()) ? buyer.toString() : seller.toString();
        String base = "pair." + first + "." + second + ".";
        long now = System.currentTimeMillis();
        long window = store().getLong(base + "window", 0L);
        if (now - window > 24L * 60L * 60L * 1000L) {
            store().set(base + "window", now);
            store().set(base + "count", 0);
            store().set(base + "total", 0.0D);
        }
        int count = store().getInt(base + "count", 0) + 1;
        double total = store().getDouble(base + "total", 0.0D) + amount;
        store().set(base + "count", count);
        store().set(base + "total", total);
        store().save();
        double reference = Math.max(0.01D, appraisedValue(item));
        if (count >= 3 && (total >= 50_000.0D || amount / reference >= 25.0D)) {
            String detail = "pair=" + first + "/" + second + " count24h=" + count + " total=$" + format(total);
            audit("PAIR_RISK", buyer, -1, amount, detail);
            alertStaff("&c[AH Alert] &fRepeated high-risk pair trading: &7" + detail);
        }
    }

    private void expireListings() {
        expireListings(data);
        expireListings(sandboxData);
    }

    private void expireListings(PropertiesFile targetStore) {
        PropertiesFile previousStore = scopedStore.get();
        scopedStore.set(targetStore);
        try {
        long now = System.currentTimeMillis();
        List<Listing> expired = listings("", SortMode.RECENT).stream().filter(listing -> listing.expiresAt() < now).toList();
        if (expired.isEmpty()) {
            return;
        }
        synchronized (listingLock) {
            for (Listing listing : expired) {
                Listing current = listing(String.valueOf(listing.id()));
                if (current == null || current.expiresAt() >= now) {
                    continue;
                }
                store().set("listing." + current.id(), null);
                queueRefund(current.seller(), current.id(), current.item(), "expired");
                audit("EXPIRE", current.seller(), current.id(), current.price(), fingerprint(current.item()));
            }
            store().save();
        }
        expired.stream().map(Listing::seller).distinct().map(Bukkit::getPlayer).filter(player -> player != null).forEach(this::deliverRefunds);
        } finally {
            popStore(previousStore);
        }
    }

    private void queueRefund(UUID seller, int listingId, ItemStack item, String reason) {
        String base = "refund." + seller + "." + listingId;
        if (!store().contains(base)) {
            store().set(base, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            store().set(base + ".reason", reason);
        }
    }

    private void deliverRefunds(Player player) {
        String prefix = "refund." + player.getUniqueId() + ".";
        List<String> keys = store().keys().stream().filter(key -> key.startsWith(prefix) && !key.endsWith(".reason")).sorted().toList();
        for (String key : keys) {
            try {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(store().getString(key, "")));
                if (!hasInventorySpace(player, item)) {
                    Text.msg(player, "&eAuctionHouse has a returned item waiting. Free inventory space and rejoin or reopen AH.");
                    continue;
                }
                store().set(key, null);
                store().set(key + ".reason", null);
                store().save();
                give(player, item);
                audit("REFUND", player.getUniqueId(), parseRefundId(key), 0.0D, fingerprint(item));
                Text.msg(player, "&eAuctionHouse returned &f" + itemName(item) + " x" + item.getAmount() + "&e.");
            } catch (RuntimeException exception) {
                getLogger().severe("Corrupt AH refund for " + player.getUniqueId() + " at " + key + ": " + exception.getMessage());
            }
        }
    }

    private int parseRefundId(String key) {
        try {
            return Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void audit(String type, UUID actor, int listingId, double amount, String detail) {
        String value = System.currentTimeMillis() + " | " + type + " | actor=" + actor + " | listing=" + listingId + " | amount=" + format(amount) + " | " + detail;
        store().set("audit." + System.currentTimeMillis() + "." + (++auditSequence), value);
        List<String> keys = store().keys().stream().filter(key -> key.startsWith("audit.")).sorted().toList();
        for (int index = 0; index < Math.max(0, keys.size() - 1_000); index++) {
            store().set(keys.get(index), null);
        }
        store().save();
    }

    private void alertStaff(String message) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (MitchSMP.permissions().has(staff, "mitchsmp.auctionhouse.alerts")) {
                Text.msg(staff, message);
            }
        }
        getLogger().warning(Text.stripColorCodes(message));
    }

    private String fingerprint(ItemStack item) {
        if (item == null) {
            return "null";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(item.serializeAsBytes());
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < Math.min(8, digest.length); index++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException | RuntimeException exception) {
            return "unavailable";
        }
    }

    private int nextId() {
        return store().getInt("next-id", 1);
    }

    private int nextAvailableId() {
        int id = Math.max(1, nextId());
        while (store().contains("listing." + id)) {
            id++;
        }
        return id;
    }

    private Listing listingAt(AuctionMenu menu, int slot) {
        int index = menu.page() * PAGE_SIZE + slot;
        List<Listing> current = listings(menu.search(), menu.sort());
        if (index < 0 || index >= current.size()) {
            return null;
        }
        return current.get(index);
    }

    private Listing listing(String rawId) {
        try {
            int id = Integer.parseInt(rawId);
            return Listing.decode(id, store().getString("listing." + id, ""));
        } catch (Exception exception) {
            return null;
        }
    }

    private List<Listing> listings(String search, SortMode sort) {
        String query = normalize(search);
        List<Listing> result = new ArrayList<>();
        for (String key : store().keys()) {
            if (!key.startsWith("listing.")) {
                continue;
            }
            try {
                int id = Integer.parseInt(key.substring("listing.".length()));
                Listing listing = Listing.decode(id, store().getString(key, ""));
                if (listing != null && matches(listing, query)) {
                    result.add(listing);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        result.sort(sort.comparator());
        return result;
    }

    private List<Listing> ownListings(Player player) {
        return listings("", SortMode.RECENT).stream()
            .filter(listing -> listing.seller().equals(player.getUniqueId()))
            .toList();
    }

    private int ownListingCount(Player player) {
        return ownListings(player).size();
    }

    private int listingLimit(Player player) {
        MitchRank rank = MitchSMP.ranks().getRank(player.getUniqueId());
        if (rank.staff() || rank.inherits(MitchRank.LEGEND)) {
            return 45;
        }
        if (rank.inherits(MitchRank.MVP)) {
            return 36;
        }
        if (rank.inherits(MitchRank.VIP)) {
            return 27;
        }
        return 18;
    }

    private boolean matches(Listing listing, String query) {
        if (query.isBlank()) {
            return true;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller());
        String haystack = normalize("#" + listing.id()
            + " " + itemName(listing.item())
            + " " + listing.item().getType().name()
            + " " + safeName(seller));
        return haystack.contains(query);
    }

    private List<String> listingIds(String input, CommandSender sender, boolean ownOnly) {
        boolean canCancelAny = MitchSMP.permissions().has(sender, "mitchsmp.auctionhouse.admin");
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        List<String> ids = new ArrayList<>();
        for (Listing listing : listings("", SortMode.RECENT)) {
            if (ownOnly && !canCancelAny && !listing.seller().equals(senderId)) {
                continue;
            }
            ids.add(String.valueOf(listing.id()));
        }
        return Tab.complete(input, ids);
    }

    private int maxPage(List<Listing> listings) {
        if (listings.isEmpty()) {
            return 0;
        }
        return (listings.size() - 1) / PAGE_SIZE;
    }

    private int maxPage(List<Listing> listings, int pageSize) {
        if (listings.isEmpty()) {
            return 0;
        }
        return (listings.size() - 1) / Math.max(1, pageSize);
    }

    private ViewState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new ViewState("", SortMode.RECENT, 0));
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private boolean hasInventorySpace(Player player, ItemStack item) {
        if (player == null || item == null || item.getType() == Material.AIR) {
            return true;
        }
        int remaining = item.getAmount();
        for (ItemStack content : player.getInventory().getContents()) {
            if (content == null || content.getType() == Material.AIR) {
                remaining -= 64;
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private String itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return Text.stripColorCodes(meta.getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace("_", " ");
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? "Unknown" : player.getName();
    }

    private Double parse(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String blankSearch(String search) {
        return search == null || search.isBlank() ? "geen" : search;
    }

    private String normalize(String input) {
        return input == null ? "" : Text.stripColorCodes(input).toLowerCase(Locale.ROOT).replace("_", " ").trim();
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private void requestSearch(Player player, boolean closeInventory) {
        awaitingSearch.put(player.getUniqueId(), true);
        awaitingListingPrice.remove(player.getUniqueId());
        if (closeInventory) {
            player.closeInventory();
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!Boolean.TRUE.equals(awaitingSearch.get(player.getUniqueId()))) {
                return;
            }
            if (!openSearchSign(player)) {
                awaitingSearch.remove(player.getUniqueId());
                Text.msg(player, "&cCould not open sign input. Temporarily use &f/ah search <term>&c.");
            }
        }, 1L);
    }

    private void requestListingPrice(Player player) {
        awaitingListingPrice.put(player.getUniqueId(), true);
        awaitingSearch.remove(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!Boolean.TRUE.equals(awaitingListingPrice.get(player.getUniqueId()))) {
                return;
            }
            if (!openSearchSign(player)) {
                awaitingListingPrice.remove(player.getUniqueId());
                returnPendingListing(player);
                Text.msg(player, "&cCould not open sign input. Temporarily use &f/ah sell <price>&c.");
            }
        }, 1L);
    }

    private void handleSearchInput(Player player, String message) {
        Bukkit.getScheduler().runTask(this, () -> {
            restoreFakeSign(player);
            if (message.equalsIgnoreCase("cancel")) {
                Text.msg(player, "&7Search cancelled.");
                open(player, state(player).page());
                return;
            }
            if (message.equalsIgnoreCase("clear") || message.equalsIgnoreCase("reset")) {
                state(player).search("");
                Text.msg(player, "&aZoekfilter gewist.");
            } else {
                state(player).search(message);
                Text.msg(player, message.isBlank() ? "&aSearch gewist." : "&aSearch naar &f" + message + "&a.");
            }
            open(player, 0);
        });
    }

    private void handleListingPriceInput(Player player, String message) {
        Bukkit.getScheduler().runTask(this, () -> {
            restoreFakeSign(player);
            if (message.equalsIgnoreCase("cancel")) {
                Text.msg(player, "&7New listing cancelled.");
                returnPendingListing(player);
                openMyListings(player, 0);
                return;
            }
            Double price = parse(message);
            if (price == null || price <= 0.0D) {
                Text.msg(player, "&cInvalid price. Item returned.");
                returnPendingListing(player);
                openMyListings(player, 0);
                return;
            }
            createListing(player, price);
        });
    }

    private String plainLine(Object component) {
        if (component == null) {
            return "";
        }
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object serializer = serializerClass.getMethod("plainText").invoke(null);
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            return String.valueOf(serializer.getClass().getMethod("serialize", componentClass).invoke(serializer, component));
        } catch (ReflectiveOperationException exception) {
            return String.valueOf(component);
        }
    }

    private boolean openSearchSign(Player player) {
        try {
            org.bukkit.Location base = player.getLocation();
            org.bukkit.Location signLocation = new org.bukkit.Location(base.getWorld(), base.getBlockX(), base.getBlockY() + 3.0D, base.getBlockZ());
            fakeSearchSigns.put(player.getUniqueId(), signLocation);
            fakeSearchMaterials.put(player.getUniqueId(), signLocation.getBlock().getType());
            signLocation.getBlock().setType(Material.OAK_SIGN);
            player.sendBlockChange(signLocation, Bukkit.createBlockData(Material.OAK_SIGN));
            boolean priceInput = Boolean.TRUE.equals(awaitingListingPrice.get(player.getUniqueId()));
            player.sendSignChange(signLocation, new String[] {"", "^^^^^^^^^^", priceInput ? "Price" : "Search", "line 1"});
            Class<?> positionClass = Class.forName("io.papermc.paper.math.Position");
            Object position = positionClass.getMethod("block", org.bukkit.Location.class).invoke(null, signLocation);
            Class<?> sideClass = Class.forName("org.bukkit.block.sign.Side");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object front = Enum.valueOf((Class<? extends Enum>) sideClass.asSubclass(Enum.class), "FRONT");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    Player.class.getMethod("openVirtualSign", positionClass, sideClass).invoke(player, position, front);
                } catch (ReflectiveOperationException exception) {
                    getLogger().warning("Could not open fake sign search: " + exception.getMessage());
                    awaitingSearch.remove(player.getUniqueId());
                    awaitingListingPrice.remove(player.getUniqueId());
                    returnPendingListing(player);
                    restoreFakeSign(player);
                    Text.msg(player, "&cCould not open sign input. Temporarily use &f/ah search <term> &cof &f/ah sell <price>&c.");
                }
            }, 2L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (Boolean.TRUE.equals(awaitingSearch.get(player.getUniqueId())) || Boolean.TRUE.equals(awaitingListingPrice.get(player.getUniqueId()))) {
                    awaitingSearch.remove(player.getUniqueId());
                    awaitingListingPrice.remove(player.getUniqueId());
                    returnPendingListing(player);
                    restoreFakeSign(player);
                    Text.msg(player, "&7Sign input verlopen. Klik opnieuw.");
                }
            }, 20L * 30L);
            Text.msg(player, priceInput
                ? "&aType the price on line 1 of the sign and click Done."
                : "&aType your search term on line 1 of the sign and click Done.");
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Could not open virtual sign search: " + exception.getMessage());
            return false;
        }
    }

    private void restoreFakeSign(Player player) {
        org.bukkit.Location location = fakeSearchSigns.remove(player.getUniqueId());
        if (location != null && location.getWorld() != null) {
            Material old = fakeSearchMaterials.remove(player.getUniqueId());
            if (old != null) {
                location.getBlock().setType(old);
            }
            player.sendBlockChange(location, location.getBlock().getType(), (byte) 0);
        }
    }

    private boolean stageCursorListing(Player player, InventoryClickEvent event) {
        ItemStack cursor = cursorItem(event);
        if (!validListingItem(player, cursor)) {
            return false;
        }
        pendingListingItems.put(player.getUniqueId(), cursor.clone());
        clearCursor(event);
        requestListingPrice(player);
        return true;
    }

    private boolean stageClickedInventoryListing(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (!validListingItem(player, clicked)) {
            return false;
        }
        pendingListingItems.put(player.getUniqueId(), clicked.clone());
        clearCurrentItem(event);
        requestListingPrice(player);
        return true;
    }

    private boolean validListingItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return false;
        }
        if (isMechanicsGuide(item)) {
            Text.msg(player, "&cDit guide boek kan niet verkocht worden.");
            return false;
        }
        int own = ownListingCount(player);
        int limit = listingLimit(player);
        if (own >= limit) {
            Text.msg(player, "&cJe AH listing limiet is bereikt: &f" + own + "/" + limit + "&c.");
            return false;
        }
        return true;
    }

    private ItemStack cursorItem(InventoryClickEvent event) {
        try {
            Object value = event.getClass().getMethod("getCursor").invoke(event);
            return value instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private void clearCursor(InventoryClickEvent event) {
        try {
            event.getClass().getMethod("setCursor", ItemStack.class).invoke(event, new ItemStack(Material.AIR));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void clearCurrentItem(InventoryClickEvent event) {
        try {
            event.getClass().getMethod("setCurrentItem", ItemStack.class).invoke(event, new ItemStack(Material.AIR));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void returnPendingListing(Player player) {
        ItemStack pending = pendingListingItems.remove(player.getUniqueId());
        if (pending != null && pending.getType() != Material.AIR) {
            give(player, pending);
        }
    }

    private enum SortMode {
        RECENT("recent geplaatst", Comparator.comparingInt(Listing::id).reversed()),
        PRICE_LOW("price low-high", Comparator.comparingDouble(Listing::price).thenComparing(Comparator.comparingInt(Listing::id).reversed())),
        PRICE_HIGH("price high-low", Comparator.comparingDouble(Listing::price).reversed().thenComparing(Comparator.comparingInt(Listing::id).reversed()));

        private final String display;
        private final Comparator<Listing> comparator;

        SortMode(String display, Comparator<Listing> comparator) {
            this.display = display;
            this.comparator = comparator;
        }

        String display() {
            return display;
        }

        Comparator<Listing> comparator() {
            return comparator;
        }

        SortMode next() {
            return switch (this) {
                case RECENT -> PRICE_LOW;
                case PRICE_LOW -> PRICE_HIGH;
                case PRICE_HIGH -> RECENT;
            };
        }

        static SortMode parse(String input) {
            String normalized = input.toLowerCase(Locale.ROOT).replace("-", "_");
            return switch (normalized) {
                case "low", "lowest", "price_low", "laag", "laag_hoog" -> PRICE_LOW;
                case "high", "highest", "price_high", "hoog", "hoog_laag" -> PRICE_HIGH;
                default -> RECENT;
            };
        }
    }

    private static final class ViewState {
        private String search;
        private SortMode sort;
        private int page;

        ViewState(String search, SortMode sort, int page) {
            this.search = search;
            this.sort = sort;
            this.page = page;
        }

        String search() {
            return search;
        }

        void search(String value) {
            search = value == null ? "" : value.trim();
        }

        SortMode sort() {
            return sort;
        }

        void sort(SortMode value) {
            sort = value == null ? SortMode.RECENT : value;
        }

        int page() {
            return page;
        }

        void page(int value) {
            page = value;
        }
    }

    private static final class AuctionMenu implements InventoryHolder {
        private final int page;
        private final String search;
        private final SortMode sort;
        private Inventory inventory;

        AuctionMenu(int page, String search, SortMode sort) {
            this.page = page;
            this.search = search;
            this.sort = sort;
        }

        int page() {
            return page;
        }

        String search() {
            return search;
        }

        SortMode sort() {
            return sort;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MyListingsMenu implements InventoryHolder {
        private final int page;
        private final Map<Integer, Integer> listings = new HashMap<>();
        private Inventory inventory;

        MyListingsMenu(int page) {
            this.page = page;
        }

        int page() {
            return page;
        }

        void listing(int slot, int listingId) {
            listings.put(slot, listingId);
        }

        Integer listing(int slot) {
            return listings.get(slot);
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Listing(int id, UUID seller, double price, long createdAt, long expiresAt, ItemStack item) {
        String encode() {
            return seller + ";" + price + ";" + createdAt + ";" + expiresAt + ";" + Base64.getEncoder().encodeToString(item.serializeAsBytes());
        }

        static Listing decode(int id, String raw) {
            try {
                String[] parts = raw.split(";", 5);
                if (parts.length == 3) {
                    UUID seller = UUID.fromString(parts[0]);
                    double price = Double.parseDouble(parts[1]);
                    ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[2]));
                    return new Listing(id, seller, price, 0L, Long.MAX_VALUE, item);
                }
                if (parts.length != 5) {
                    return null;
                }
                UUID seller = UUID.fromString(parts[0]);
                double price = Double.parseDouble(parts[1]);
                long createdAt = Long.parseLong(parts[2]);
                long expiresAt = Long.parseLong(parts[3]);
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[4]));
                return new Listing(id, seller, price, createdAt, expiresAt, item);
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }
}
