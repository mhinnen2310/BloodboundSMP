package nl.mitchsmp.skills;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.SkillService;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class SkillsPlugin extends JavaPlugin implements Listener, TabCompleter, SkillService {
    private static final int MAX_LEVEL = 100;
    private static final String STATE_PREFIX = "[MSA:";
    private static final String LORE_MARKER = "[MSAbility]";
    private static final int CATEGORY_COUNT = 6;
    private static final Set<Perk> ACTIVE_PERKS = EnumSet.of(
        Perk.MINING_SPEED, Perk.MINING_YIELD, Perk.DEEP_MINER, Perk.ORE_SURVEYOR, Perk.VEIN_DISCIPLINE, Perk.MINING_MASTERY,
        Perk.FORESTER, Perk.REPLANTER, Perk.HARVEST_FLOW, Perk.FARMING_MASTERY,
        Perk.DUELIST, Perk.COMBAT_SUSTAIN, Perk.BOUNTY_FOCUS, Perk.ESCAPE_DISCIPLINE, Perk.KINGSLAYER_FOCUS,
        Perk.BREWING_FOCUS, Perk.RELIC_ALCHEMY, Perk.INFERNAL_RESOLVE, Perk.ALCHEMY_GRANDMASTER,
        Perk.RUNE_SENSE, Perk.TABLE_ATTUNEMENT, Perk.BOOKSMITH, Perk.ENCHANTING_GRANDMASTER,
        Perk.ECONOMY_QUICKSELL_EFFICIENCY, Perk.ORDER_RUNNER, Perk.CONTRACT_BROKER, Perk.AUCTION_APPRAISER,
        Perk.WORKERS_INSTINCT,
        Perk.STONE_SENSE, Perk.ORE_INSTINCT, Perk.DEEP_DELVER, Perk.CONTRACT_MINER, Perk.SHARD_PROSPECTOR, Perk.BLOODBOUND_PICK,
        Perk.GREEN_THUMB, Perk.RANCH_HAND, Perk.FIELD_SENSE, Perk.STOCKPILE_DISCIPLINE, Perk.CRIMSON_GROWTH, Perk.BLOODBOUND_HOE,
        Perk.ARCANE_FAMILIARITY, Perk.TEMPERED_TOOLS, Perk.ENCHANTERS_FOCUS, Perk.ARCANE_WORKSTATION, Perk.RUNE_EFFICIENCY, Perk.BLOODBOUND_INFUSION,
        Perk.FRESH_BLOOD, Perk.PURSUIT, Perk.KILL_PRESSURE, Perk.BLOOD_RUSH_NODE, Perk.BOUNTY_HUNGER, Perk.BLOODBOUND_BLADE,
        Perk.IRON_POSTURE, Perk.GUARDED_RECOVERY, Perk.COMBAT_ENDURANCE, Perk.HOLD_THE_LINE, Perk.LAST_STAND, Perk.BLOODBOUND_GUARD,
        Perk.STEADY_AIM, Perk.HUNTERS_MARK, Perk.LONGSHOT_DISCIPLINE, Perk.EVENT_COVER, Perk.PIERCING_FOCUS, Perk.BLOODBOUND_BOW
    );
    private final Set<UUID> abilityBreaking = new HashSet<>();
    private final Map<UUID, Long> abilityToggleCooldowns = new HashMap<>();
    private final Map<String, Long> activeSkillCooldowns = new HashMap<>();
    private final Map<UUID, Long> bountyXpCooldowns = new HashMap<>();
    private final Map<UUID, Long> kingslayerWarnings = new HashMap<>();
    private final Set<UUID> kingslayerGlowing = new HashSet<>();
    private final Map<UUID, Long> brewingBoostCooldowns = new HashMap<>();
    private final Random random = new Random();
    private PropertiesFile data;
    private PropertiesFile skillConfig;
    private NamespacedKey guideKey;
    private NamespacedKey recoveryPreviewExpiryKey;
    private boolean skillDataDirty;

    @Override
    public void onEnable() {
        data = new PropertiesFile(getDataFolder().toPath().resolve("skills.properties"));
        skillConfig = new PropertiesFile(getDataFolder().toPath().resolve("skilltree.properties"));
        writeDefaultSkilltreeConfig();
        guideKey = new NamespacedKey(this, "mechanics_guide");
        recoveryPreviewExpiryKey = new NamespacedKey(this, "recovery_preview_expiry");
        wipeLegacySkillProgressOnce();
        MitchSMP.registerService(SkillService.class, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        command("skills");
        command("skillsadmin");
        command("anvil");
        command("enchant");
        command("abilities");
        command("mechanics");
        command("scout");
        command("markvein");
        command("bloodrush");
        command("brewboost");
        command("contractboost");
        Bukkit.getScheduler().runTaskTimer(this, this::mechanicsTip, 20L * 60L * 15L, 20L * 60L * 15L);
        Bukkit.getScheduler().runTaskTimer(this, this::updateKingslayerTargets, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::flushSkillData, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, this::applyPassiveSkillEffects, 100L, 100L);
    }

    private void wipeLegacySkillProgressOnce() {
        String marker = "migration.v1_0_20_bloodbound_tree_reset_done";
        if (Boolean.parseBoolean(data.getString(marker, "false"))) {
            return;
        }
        int removed = 0;
        for (String key : new ArrayList<>(data.keys())) {
            if (key.startsWith("xp.") || key.startsWith("points.") || key.startsWith("perk.") || key.startsWith("skill.")) {
                data.set(key, null);
                removed++;
            }
        }
        data.set(marker, true);
        data.save();
        if (removed > 0) {
            getLogger().info("Bloodbound skilltree rebuild wiped " + removed + " test skill-progress entries. Item abilities and other player data were not touched.");
        }
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(kingslayerGlowing)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                setGlowing(player, false);
            }
        }
        data.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("skills")) {
            return skills(sender, args);
        }
        if (name.equals("skillsadmin")) {
            String[] forwarded = new String[args.length + 1];
            forwarded[0] = "admin";
            System.arraycopy(args, 0, forwarded, 1, args.length);
            return skillsAdmin(sender, forwarded);
        }
        if (name.equals("anvil") || name.equals("enchant")) {
            if (!(sender instanceof Player player)) {
                Text.msg(sender, "&cPlayers only.");
                return true;
            }
            return openPortableStation(player, name);
        }
        if (name.equals("abilities")) {
            return abilities(sender, args);
        }
        if (name.equals("mechanics")) {
            return mechanics(sender);
        }
        if (name.equals("scout")) {
            return activeScout(sender);
        }
        if (name.equals("markvein")) {
            return activeMarkVein(sender);
        }
        if (name.equals("bloodrush")) {
            return activeBloodrush(sender);
        }
        if (name.equals("brewboost")) {
            return activeBrewBoost(sender);
        }
        if (name.equals("contractboost")) {
            return activeContractBoost(sender);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("skills")) {
            boolean admin = MitchSMP.permissions().has(sender, "mitchsmp.skills.admin");
            if (args.length == 1) {
                List<String> options = new ArrayList<>(List.of("page", "branch", "preview", "enchant", "anvil", "reset"));
                if (admin) {
                    options.addAll(List.of("reload", "admin"));
                }
                return Tab.complete(args[0], options);
            }
            if (args.length == 2 && args[0].matches("(?i)page|branch|preview")) {
                return Tab.complete(args[1], specializationKeys());
            }
            if (!admin) {
                return List.of();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                return Tab.complete(args[1], "addxp", "givepoint", "points", "reset", "unlock", "setbranch", "clearbranch", "debug", "testgui", "reload");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
                return Tab.onlinePlayers(args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("addxp")) {
                return Tab.complete(args[3], Category.keys());
            }
        }
        if (name.equals("skillsadmin")) {
            if (!MitchSMP.permissions().has(sender, "mitchsmp.skills.admin")) {
                return List.of();
            }
            if (args.length == 1) {
                return Tab.complete(args[0], "addxp", "givepoint", "points", "reset", "unlock", "setbranch", "clearbranch", "debug", "testgui", "reload");
            }
            if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
                return Tab.onlinePlayers(args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("addxp")) {
                return Tab.complete(args[2], Category.keys());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("setbranch")) {
                List<String> options = new ArrayList<>(List.of(specializationKeys()));
                options.addAll(java.util.Arrays.stream(MainBranch.values()).map(MainBranch::key).toList());
                return Tab.complete(args[2], options);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("setbranch")) {
                return Tab.complete(args[3], specializationKeys());
            }
            if (args.length == 3 && args[0].matches("(?i)unlock|testgui")) {
                return Tab.complete(args[2], java.util.Arrays.stream(Perk.values()).map(Perk::key).toArray(String[]::new));
            }
        }
        if (name.equals("abilities")) {
            boolean admin = MitchSMP.permissions().has(sender, "mitchsmp.skills.admin");
            if (args.length == 1) {
                List<String> options = new ArrayList<>(List.of("toggle", "activate", "clean"));
                if (admin) {
                    options.addAll(List.of("grant", "complete", "testkit", "config"));
                }
                return Tab.complete(args[0], options);
            }
            if (admin && args.length == 2 && args[0].equalsIgnoreCase("grant")) {
                return Tab.complete(args[1], Ability.keys());
            }
            if (admin && args.length == 2 && args[0].equalsIgnoreCase("config")) {
                List<String> result = new ArrayList<>(Tab.complete(args[1], Ability.keys()));
                result.addAll(Tab.complete(args[1], "settings", "list"));
                return result;
            }
            if (admin && args.length == 3 && args[0].equalsIgnoreCase("config")) {
                if (args[1].equalsIgnoreCase("settings")) {
                    return Tab.complete(args[2], "enchant_min_level", "enchant_chance_percent", "loot_chance_percent");
                }
                Ability ability = Ability.from(args[1]);
                if (ability == Ability.AEGIS_GUARD) {
                    return Tab.complete(args[2], "required", "enabled", "cooldown", "duration");
                }
                if (ability == Ability.BLOOD_FORGED_EDGE) {
                    return Tab.complete(args[2], "required", "enabled", "cooldown", "duration");
                }
                return ability != null && ability.pvpRelated() ? Tab.complete(args[2], "required", "enabled", "cooldown") : Tab.complete(args[2], "required", "enabled");
            }
            if (admin && args.length == 4 && args[0].equalsIgnoreCase("config") && args[1].equalsIgnoreCase("settings")) {
                return Tab.complete(args[3], "20", "25", "30", "1", "2", "5", "10");
            }
            if (admin && args.length == 4 && args[0].equalsIgnoreCase("config") && args[2].equalsIgnoreCase("cooldown")) {
                return Tab.complete(args[3], "0", "2", "5", "10", "15", "30", "60", "1800");
            }
        }
        return List.of();
    }

    private boolean skills(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return skillsAdmin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!MitchSMP.permissions().has(player, "mitchsmp.skills.admin")) {
                Text.msg(player, "&cYou do not have permission.");
                return true;
            }
            skillConfig.load();
            writeDefaultSkilltreeConfig();
            Text.msg(player, "&aBloodbound skilltree config reloaded.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                Text.msg(player, "&cThis resets your Bloodbound skilltree choices and points.");
                Text.msg(player, "&7Use &f/skills reset confirm &7to choose a different build.");
                return true;
            }
            resetPlayer(player);
            Text.msg(player, "&aYour Bloodbound skilltree has been reset.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.8F);
            return true;
        }
        if (args.length > 0 && args[0].matches("(?i)enchant|anvil")) {
            return openPortableStation(player, args[0]);
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.skills.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length >= 2 && args[0].matches("(?i)page|branch|preview")) {
            Specialization specialization = Specialization.from(args[1]);
            if (specialization != null) {
                openSpecialization(player, specialization);
                return true;
            }
        }
        openSkills(player);
        return true;
    }

    private boolean abilities(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("config")) {
            return abilityConfig(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("grant")) {
            return abilityGrant(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("complete")) {
            return abilityComplete(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("testkit")) {
            return abilityTestKit(sender);
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.skills.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("activate")) {
            return activateHeldAbility(player);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("clean")) {
            int cleaned = cleanAbilityLore(player);
            Text.msg(player, "&aCleaned ability lore on &f" + cleaned + " &aitem(s).");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            toggleHeldAbility(player);
            return true;
        }
        openAbilities(player);
        return true;
    }

    private boolean mechanics(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        try {
            ItemStack guide = new ItemStack(Material.WRITTEN_BOOK);
            ItemMeta meta = guide.getItemMeta();
            if (meta instanceof BookMeta book) {
                applyBookText(book, "setTitle", "Bloodbound Guide");
                applyBookText(book, "setAuthor", "BloodboundSMP");
                meta.setDisplayName(Text.color("&4Bloodbound Mechanics Guide"));
                meta.setLore(List.of(
                    Text.color("&7Open this book for the full BloodboundSMP guide."),
                    Text.color("&8Server guide - no sell value.")
                ));
                meta.getPersistentDataContainer().set(guideKey, PersistentDataType.BYTE, (byte) 1);
                applyBookPages(book, mechanicsPages());
                guide.setItemMeta(book);
            }
            player.getInventory().addItem(guide).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            Text.msg(player, "&aWritten mechanics guide received. Open the book or use &f/skills &aand &f/abilities&a.");
        } catch (Throwable exception) {
            getLogger().warning("/mechanics could not create a book for " + player.getName() + ": " + exception.getMessage());
            Text.msg(player, "&cCould not create the mechanics book. Temporarily use &f/commands &cand &f/skills&c.");
        }
        return true;
    }

    private boolean activeScout(CommandSender sender) {
        Player player = activePlayer(sender);
        if (player == null) {
            return true;
        }
        int hunter = Math.max(perk(player, Perk.KINGSLAYER_FOCUS), perk(player, Perk.BOUNTY_FOCUS));
        if (hunter <= 0) {
            Text.msg(player, "&cScout requires the Hunter combat path.");
            return true;
        }
        if (!activeCooldown(player, "scout", Math.max(30, 180 - hunter * 8))) {
            return true;
        }
        double range = Math.min(192.0D, 64.0D + hunter * 12.0D);
        int threats = 0;
        int highValue = 0;
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || restricted(target) || target.getWorld() == null || player.getWorld() == null || !target.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = target.getLocation().distance(player.getLocation());
            if (distance > range) {
                continue;
            }
            threats++;
            boolean valuable = MitchSMP.hearts() != null && MitchSMP.hearts().getHearts(target.getUniqueId()) >= 20
                || MitchSMP.bounties() != null && MitchSMP.bounties().getBounty(target.getUniqueId()) > 0.0D;
            if (valuable) {
                highValue++;
            }
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = target;
            }
        }
        String nearestText = nearest == null ? "none" : nearest.getName() + " ~" + Math.round(nearestDistance) + "m";
        player.sendActionBar(Text.color("&4Scout &8| &f" + threats + " &7nearby, &6" + highValue + " &7high-value &8| &7nearest: &f" + nearestText));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.7F);
        return true;
    }

    private boolean activeMarkVein(CommandSender sender) {
        Player player = activePlayer(sender);
        if (player == null) {
            return true;
        }
        int prospector = Math.max(perk(player, Perk.ORE_SURVEYOR), perk(player, Perk.VEIN_DISCIPLINE));
        if (prospector <= 0) {
            Text.msg(player, "&cMark Vein requires the Prospector mining path.");
            return true;
        }
        if (!activeCooldown(player, "markvein", Math.max(20, 140 - prospector * 7))) {
            return true;
        }
        int radius = Math.min(14, 5 + prospector / 2);
        int ores = 0;
        int valuable = 0;
        Location origin = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -Math.min(5, radius); y <= Math.min(5, radius); y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = origin.getWorld().getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                    String name = block.getType().name();
                    if (!name.contains("ORE") && !name.equals("ANCIENT_DEBRIS")) {
                        continue;
                    }
                    ores++;
                    if (name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("ANCIENT")) {
                        valuable++;
                        origin.getWorld().spawnParticle(org.bukkit.Particle.NOTE, block.getLocation().add(0.5D, 0.5D, 0.5D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    }
                }
            }
        }
        player.sendActionBar(Text.color("&bMark Vein &8| &f" + ores + " &7ores found, &b" + valuable + " &7high-value nearby"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.4F);
        return true;
    }

    private boolean activeBloodrush(CommandSender sender) {
        Player player = activePlayer(sender);
        if (player == null) {
            return true;
        }
        int duelist = Math.max(perk(player, Perk.DUELIST), perk(player, Perk.COMBAT_MASTERY));
        if (duelist <= 0) {
            Text.msg(player, "&cBloodrush requires the Duelist combat path.");
            return true;
        }
        if (!activeCooldown(player, "bloodrush", Math.max(60, 240 - duelist * 10))) {
            return true;
        }
        int duration = 80 + duelist * 6;
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, duelist >= 8 ? 1 : 0, false, false, true));
        player.sendActionBar(Text.color("&4Bloodrush &8| &c" + Math.round(duration / 20.0D) + "s sustain window"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 0.8F);
        return true;
    }

    private boolean activeBrewBoost(CommandSender sender) {
        Player player = activePlayer(sender);
        if (player == null) {
            return true;
        }
        int brewer = perk(player, Perk.BREWING_FOCUS);
        if (brewer <= 0) {
            Text.msg(player, "&cBrewboost requires the Brewer alchemy path.");
            return true;
        }
        if (!activeCooldown(player, "brewboost", Math.max(45, 180 - brewer * 10))) {
            return true;
        }
        data.set("active.brewboost.until." + profileKey(player.getUniqueId()), System.currentTimeMillis() + (60_000L + brewer * 5_000L));
        saveSkillDataSoon();
        player.sendActionBar(Text.color("&5Brewboost &8| &aNext brewing actions are accelerated"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.25F);
        return true;
    }

    private boolean activeContractBoost(CommandSender sender) {
        Player player = activePlayer(sender);
        if (player == null) {
            return true;
        }
        int broker = perk(player, Perk.CONTRACT_BROKER);
        if (broker <= 0) {
            Text.msg(player, "&cContractboost requires the Contractor economy path.");
            return true;
        }
        if (!activeCooldown(player, "contractboost", Math.max(120, 600 - broker * 25))) {
            return true;
        }
        data.set("active.contractboost.until." + profileKey(player.getUniqueId()), System.currentTimeMillis() + (10L * 60L * 1000L));
        saveSkillDataSoon();
        player.sendActionBar(Text.color("&6Contractboost &8| &aYour next contract payout hook is primed"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35F, 1.2F);
        return true;
    }

    private Player activePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return null;
        }
        if (!MitchSMP.permissions().has(player, "mitchsmp.skills.use")) {
            Text.msg(player, "&cYou do not have permission.");
            return null;
        }
        if (restricted(player)) {
            Text.msg(player, "&cSkill actions are disabled in this mode.");
            return null;
        }
        return player;
    }

    private boolean activeCooldown(Player player, String key, int seconds) {
        String mapKey = profileKey(player.getUniqueId()) + "." + key;
        long now = System.currentTimeMillis();
        long until = activeSkillCooldowns.getOrDefault(mapKey, 0L);
        if (until > now) {
            player.sendActionBar(Text.color("&c" + key + " cooldown &8| &f" + Math.max(1L, (until - now + 999L) / 1000L) + "s"));
            return false;
        }
        activeSkillCooldowns.put(mapKey, now + Math.max(1, seconds) * 1000L);
        return true;
    }

    private double bloodboundEnchantBonus(Player player, ItemStack item, Ability ability) {
        if (player == null || item == null || ability == null) {
            return 0.0D;
        }
        double bonus = capstoneBonus(player, Perk.BLOODBOUND_INFUSION);
        if (item.getType() == Material.ENCHANTED_BOOK) {
            return bonus;
        }
        Perk specific = switch (ability) {
            case GODS_DRILL -> Perk.BLOODBOUND_PICK;
            case HARVEST_LORD -> Perk.BLOODBOUND_HOE;
            case BLOOD_FORGED_EDGE, STORM_BIND, ANCIENT_TIMBER, EARTHSHAPER -> Perk.BLOODBOUND_BLADE;
            case AEGIS_GUARD -> Perk.BLOODBOUND_GUARD;
            case ECHO_QUIVER -> Perk.BLOODBOUND_BOW;
        };
        return Math.min(100.0D, bonus + capstoneBonus(player, specific));
    }

    private double capstoneBonus(Player player, Perk perk) {
        int level = perk(player, perk);
        if (level <= 0) {
            return 0.0D;
        }
        double perLevel = configDouble("node." + perk.key() + ".bloodbound_enchant_bonus_percent", isBloodboundEnchantCapstone(perk) ? 2.0D : 0.0D);
        double cap = configDouble("node." + perk.key() + ".bloodbound_enchant_bonus_cap_percent", isBloodboundEnchantCapstone(perk) ? 12.0D : 0.0D);
        return Math.max(0.0D, Math.min(cap, level * perLevel));
    }

    private double configDouble(String key, double fallback) {
        try {
            return Double.parseDouble(skillConfig.getString(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void applyBookText(BookMeta book, String methodName, String value) {
        try {
            Method method = book.getClass().getMethod(methodName, String.class);
            method.invoke(book, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void applyBookPages(BookMeta book, List<String> pages) {
        if (invokeBookList(book, "setPages", pages)) {
            return;
        }
        invokeBookArray(book, "addPage", pages);
    }

    private boolean invokeBookList(BookMeta book, String methodName, List<String> pages) {
        try {
            Method method = book.getClass().getMethod(methodName, List.class);
            method.invoke(book, pages);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean invokeBookArray(BookMeta book, String methodName, List<String> pages) {
        try {
            Method method = book.getClass().getMethod(methodName, String[].class);
            method.invoke(book, (Object) pages.toArray(String[]::new));
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private List<String> mechanicsPages() {
        return List.of(
            page("&4Bloodbound Guide",
                "PvP-first Lifesteal SMP.\n\nEverything is built around risk, progression and player-made stories.\n\nUse /skills for your skill tree and /abilities for item-bound endgame abilities."),
            page("&4Lifesteal",
                "Start: 10 hearts.\nKill: +1 heart.\nDeath: -1 heart.\nMin: 1 heart.\nMax: 20 hearts.\n\nHearts are the main progression. Strong players automatically become more valuable targets."),
            page("&5Corrupted Hearts",
                "Corrupted Hearts restore 1 heart, but only below 10 hearts.\n\nThey are rare dungeon/structure loot and can also appear through dangerous mobs.\n\nDisabled in BedWars."),
            page("&6Bounties",
                "Bounties rise with heart count.\n\n20-heart players build extra bounty while they stay on top.\n\nAnti-farm rules prevent friends from endlessly claiming each other."),
            page("&eEconomy",
                "Money is used for the Auction House, shops, quicksell, contracts, resource orders, cosmetics and events.\n\nNo buying hearts. No pay-to-win combat buffs.\n\nEconomyWatch adjusts prices dynamically."),
            page("&aQuickSell",
                "Use /sell.\n\nDrag items into the QuickSell inventory. Then you get a confirmation screen with the price.\n\nPrices depend on the active economy, rarity and crafting value."),
            page("&bAuction House",
                "Use /ah.\n\nSell with /ah sell <price>.\n\nIn the UI you can search, sort, reset filters, open My Listings and create new listings."),
            page("&3Resource Orders",
                "/orders opens server resource orders.\n\nThe server asks for different resources each day. This keeps mining, farming and trading useful.\n\nPaid ranks get more chances, not combat power."),
            page("&dContracts",
                "/contracts opens high-risk missions.\n\nExamples: kills, bounty targets, travel and looting dangerous structures.\n\nNo mindless grind tasks; rewards should match the risk."),
            page("&9Collection Log",
                "/collection shows relics, seasonal collectibles, event items and world collectibles.\n\n100% completion is meant to be an extremely long endgame grind."),
            page("&6Seasons",
                "/season shows the current season status.\n\nSeasonal and overall stats are tracked separately.\n\nAfter a season ends, winners remain archived in the Hall of Fame for prestige."),
            page("&eHall of Fame",
                "/legacy hof takes you to the protected Hall of Fame world.\n\nEvery season snapshot remains archived separately.\n\nAdmins can delete test seasons with season/legacy delete commands."),
            page("&aHUD",
                "/hud opens your HUD settings.\n\n/hud mode seasonal or overall switches K/D/KDR between season and all-time stats.\n\nYou can add or remove HUD components."),
            page("&2Skills",
                "/skills opens the Bloodbound progression tree.\n\nLanes: Rookie, Bloodbound, Combat, Economy, Relic/Legacy and EndBoss.\n\nXP tracks still come from real gameplay such as mining, combat, contracts and enchanting."),
            page("&2Skill XP",
                "Mining XP: blocks and ores.\nFarming XP: crops and logs.\nCombat XP: kills and damage.\nEnchanting XP: using enchanted tools.\nEconomy XP: selling, buying and trading."),
            page("&bSkill Perks",
                "Nodes are connected progression choices, not free hearts.\n\nThey tie rookie goals, bounties, contracts, relics, events and EndBoss preparation into one long-term build."),
            page("&dTool Abilities",
                "/abilities works on the item in your hand.\n\nAn item only gets an ability when the enchanting table rolls a rare Bloodbound enchant.\n\nThe challenge appears after that enchant is on the item."),
            page("&dExamples",
                "God's Drill: pickaxe 3x3 mining after a massive mining challenge.\n\nAncient Timber: axe breaks connected logs.\n\nHarvest Lord: hoe harvests crops in an area."),
            page("&4EndBoss",
                "/endboss ritual toont requirements.\n\nThe Infernal Sovereign opens through a demanding ritual, requires multiple players and creates a temporary hell world.\n\nAfter entering, you cannot return."),
            page("&4Boss Gear",
                "Endboss loot can grant Infernal Ward gear.\n\nInfernal Ward reduces charged attacks so they cannot one-shot you.\n\nHellforged gear is rare endgame loot."),
            page("&cEvents",
                "/event status shows active events.\n\nSupply drops, meteors, vaults, bandits, merchants, rifts, feasts, ambushes and boss events pull players out of their bases."),
            page("&6Boss Shards",
                "Boss Shards come from powerful bosses and events.\n\nUse /opshop for the shard shop.\n\nShards have custom model data, so resource packs can give them their own model."),
            page("&eBedWars",
                "/bw join for separated BedWars.\n\nSMP mechanics such as lifesteal, homes and TPA are disabled there.\n\nInventories are isolated and restored afterwards."),
            page("&7Staff/Admin",
                "Staff powers work through /adminmode.\n\nBuiten adminmode spelen staff en owners eerlijk mee als normale high-rank players.\n\nStaff tools do not belong in the SMP economy."),
            page("&8Fair Play",
                "Cheating, xray, dupes and suspicious behavior are logged.\n\nFake ores can detect xray.\n\nUse /commands for every command your rank can use."),
            page("&2Start Route",
                "1. Claim starter kit.\n2. Use /rtp.\n3. Gather resources.\n4. Complete orders/contracts.\n5. Build gear.\n6. PvP for hearts.\n7. Grind skills and relics.\n8. Work toward the EndBoss.")
        );
    }

    private String page(String title, String body) {
        return Text.color(title + "\n&0\n&0" + body.replace("\n", "\n&0"));
    }

    private void mechanicsTip() {
        Bukkit.broadcastMessage(Text.PREFIX + Text.color("&aNew here? Use &f/mechanics &afor the guide book, &f/skills &afor progression and &f/abilities &afor tool-bound endgame abilities."));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!data.contains("seen." + id)) {
            data.set("seen." + id, Instant.now().toString());
            saveSkillDataSoon();
            Bukkit.getScheduler().runTaskLater(this, () -> Text.msg(event.getPlayer(), "&aTip: use &f/mechanics &afor an overview of every Bloodbound system."), 80L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> cleanAbilityLore(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (restricted(player)) {
            return;
        }
        if (abilityBreaking.contains(player.getUniqueId())) {
            return;
        }
        Material block = event.getBlock().getType();
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean enchantedUse = hasKnownEnchant(tool);

        if (isMiningBlock(block)) {
            int miningXp = block.name().contains("ORE") ? 8 : 2;
            if (event.getBlock().getLocation().getBlockY() <= 0) {
                miningXp += Math.max(0, perk(player, Perk.DEEP_MINER) / 2);
            }
            addSkillXp(player, Category.MINING, miningXp);
            applyMiningPerks(player, event.getBlock(), tool);
        }
        if (isFarmBlock(block) || isLog(block)) {
            int farmingXp = isLog(block) ? 3 + perk(player, Perk.FORESTER) / 5 : 5 + perk(player, Perk.SUPPLY_GARDENER) / 4;
            addSkillXp(player, Category.FARMING, farmingXp);
            applyFarmingPerks(player, event.getBlock(), tool);
        }
        if (enchantedUse) {
            addSkillXp(player, Category.ENCHANTING, 1);
        }
        Ability ability = abilityFor(tool);
        if (ability == null) {
            return;
        }
        AbilityState state = state(tool, ability);
        int gain = challengeGain(player, ability, block, null);
        if (gain > 0) {
            state = progress(player, tool, ability, state, gain);
        } else {
            writeLore(tool, ability, state);
        }
        if (state.unlocked() && state.enabled() && abilityGloballyEnabled(ability)) {
            Block origin = event.getBlock();
            Material originType = block;
            ItemStack abilityTool = tool == null ? null : tool.clone();
            Bukkit.getScheduler().runTask(this, () -> runBlockAbility(player, origin, originType, abilityTool, ability));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || restricted(attacker)) {
            return;
        }
        if (event.getEntity() instanceof Player target) {
            int duelist = perk(attacker, Perk.DUELIST);
            if (duelist > 0) {
                multiplyDamage(event, 1.0D + Math.min(0.05D, duelist * 0.005D));
            }
            if (MitchSMP.bounties() != null && MitchSMP.bounties().getBounty(target.getUniqueId()) > 0.0D) {
                long now = System.currentTimeMillis();
                if (now - bountyXpCooldowns.getOrDefault(attacker.getUniqueId(), 0L) >= 10_000L) {
                    bountyXpCooldowns.put(attacker.getUniqueId(), now);
                    addSkillXp(attacker, Category.COMBAT, 2 + perk(attacker, Perk.BOUNTY_FOCUS));
                }
            }
            tryEscape(target, event);
        }
        ItemStack tool = combatItem(attacker, event.getDamager());
        Ability ability = abilityFor(tool);
        if (ability == null) {
            return;
        }
        AbilityState state = progress(attacker, tool, ability, state(tool, ability), abilityChallengeGain(attacker, 1));
        if (!state.unlocked() || !state.enabled() || !abilityGloballyEnabled(ability)) {
            return;
        }
        if (ability == Ability.BLOOD_FORGED_EDGE && lowHealth(attacker) && bloodforgedActive(attacker, ability)) {
            multiplyDamage(event, 2.0D);
            attacker.getWorld().spawnParticle(org.bukkit.Particle.CRIT, attacker.getLocation(), 6, 0.25D, 0.35D, 0.25D, 0.02D);
            attacker.sendActionBar(Text.color("&4Blood-Forged Edge &6ACTIVE &8| &cx2 damage"));
        }
    }

    @EventHandler
    public void onAnyDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player defender) || restricted(defender)
            || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        handleAegisBlock(defender, event);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile == null || !(projectile.getShooter() instanceof Player player) || restricted(player)) {
            return;
        }
        ItemStack tool = combatItem(player, projectile);
        Ability ability = abilityFor(tool);
        if (ability == null) {
            return;
        }
        AbilityState state = progress(player, tool, ability, state(tool, ability), abilityChallengeGain(player, 1));
        if (!state.unlocked() || !state.enabled() || !abilityGloballyEnabled(ability)) {
            return;
        }
        Location target = event.getHitEntity() != null ? event.getHitEntity().getLocation()
            : event.getHitBlock() != null ? event.getHitBlock().getLocation()
            : projectile.getLocation();
        if (ability == Ability.ECHO_QUIVER && event.getHitEntity() != null && activateAbility(player, ability)) {
            lightningStrike(target, false);
            target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target, 16, 0.35D, 0.55D, 0.35D, 0.04D);
        } else if (ability == Ability.STORM_BIND && activateAbility(player, ability)) {
            target.getWorld().createExplosion(target, 2.6F, false, false);
            target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, target, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity == null ? null : entity.getKiller();
        if (killer == null || restricted(killer)) {
            return;
        }
        addSkillXp(killer, Category.COMBAT, entity instanceof Player ? 80 : 8);
        if (!(entity instanceof Player) && isFarmAnimal(entity)) {
            addSkillXp(killer, Category.FARMING, 6);
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        Ability ability = abilityFor(weapon);
        if (ability != null) {
            AbilityState state = progress(killer, weapon, ability, state(weapon, ability), abilityChallengeGain(killer, 1));
            if (ability == Ability.BLOOD_FORGED_EDGE && state.unlocked() && state.enabled() && abilityGloballyEnabled(ability)) {
                heal(killer, 1.0D + perk(killer, Perk.COMBAT_SUSTAIN) * 0.10D);
            }
        }
        int sustain = perk(killer, Perk.COMBAT_SUSTAIN);
        if (sustain > 0) {
            heal(killer, sustain * 0.15D);
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player && !restricted(player)) {
            addSkillXp(player, Category.FARMING, 12);
            int mastery = perk(player, Perk.FARMING_MASTERY);
            if (mastery > 0) {
                player.sendActionBar(Text.color("&aFarming XP &8+ &7animal breeding"));
            }
        }
    }

    private boolean isFarmAnimal(Entity entity) {
        if (entity == null || entity.getType() == null) {
            return false;
        }
        String type = entity.getType().name();
        return type.equals("COW") || type.equals("SHEEP") || type.equals("PIG") || type.equals("CHICKEN")
            || type.equals("RABBIT") || type.equals("GOAT") || type.equals("BEE") || type.equals("MOOSHROOM");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (restricted(player)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (event.getClickedBlock() != null && event.getClickedBlock().getType().name().equals("BREWING_STAND")) {
            accelerateBrewing(player, event.getClickedBlock());
        }
        Ability ability = abilityFor(item);
        if (ability == null) {
            return;
        }
        AbilityState state = state(item, ability);
        if (ability == Ability.AEGIS_GUARD && state.unlocked() && state.enabled() && abilityGloballyEnabled(ability)
            && isSneaking(player) && isRightClick(event.getAction())) {
            event.setCancelled(true);
            activateAegis(player, item);
            return;
        }
        if (ability == Ability.HARVEST_LORD && state.unlocked() && state.enabled() && abilityGloballyEnabled(ability) && isRightClick(event.getAction())) {
            plantSeedPatch(player, event.getClickedBlock());
            event.setCancelled(true);
        }
        writeLore(item, ability, state);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (player == null || item == null || restricted(player)) {
            return;
        }
        String type = item.getType().name();
        int base = type.equals("ENCHANTED_GOLDEN_APPLE") ? 30
            : type.equals("GOLDEN_APPLE") ? 10
            : type.contains("POTION") ? 8
            : type.equals("HONEY_BOTTLE") ? 4 : 0;
        if (base <= 0) {
            return;
        }
        int bonus = perk(player, Perk.ALCHEMY_MASTERY) / 4;
        if (type.contains("APPLE")) {
            bonus += perk(player, Perk.APPLE_LORE) / 2;
        }
        if (type.equals("ENCHANTED_GOLDEN_APPLE") || type.contains("POTION")) {
            bonus += perk(player, Perk.RELIC_ALCHEMY) / 2;
        }
        addSkillXp(player, Category.ALCHEMY, base + bonus);
        if (type.contains("POTION") && perk(player, Perk.ALCHEMY_GRANDMASTER) > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> extendActivePotionEffects(player), 2L);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        // Dropping is intentionally never used as an ability shortcut.
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player == null || restricted(player) || !isSneaking(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        Ability ability = abilityFor(item);
        if (ability == null) {
            return;
        }
        event.setCancelled(true);
        toggleAbility(player, item, ability);
        player.sendActionBar(Text.color("&d" + ability.display() + " &8| " + (state(item, ability).enabled() ? "&aENABLED" : "&cDISABLED")));
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        if (player == null || item == null || blockedAbilityWorld(player)) {
            return;
        }
        if (!MitchSMP.permissions().isAdminRestricted(player)) {
            addSkillXp(player, Category.ENCHANTING, Math.max(10, effectiveEnchantLevel(event.getExpLevelCost()) * 2));
        }
        Ability ability = item.getType() == Material.ENCHANTED_BOOK ? randomBookAbility() : baseAbilityFor(item);
        if (ability == null || hasAwakenedAbility(item, ability)) {
            return;
        }
        int minLevel = Math.max(1, data.getInt("ability.settings.enchant_min_level", 25));
        int effectiveLevel = effectiveEnchantLevel(event.getExpLevelCost());
        double chance = Math.max(0.0D, data.getDouble("ability.settings.enchant_chance_percent", 4.0D));
        if (chance < 100.0D && effectiveLevel < minLevel) {
            return;
        }
        chance += Math.min(3.0D, Math.max(0, effectiveLevel - minLevel) * 0.15D);
        chance += Math.min(2.0D, perk(player, Perk.ENCHANTING_MASTERY) * 0.10D);
        chance += Math.min(2.0D, perk(player, Perk.RUNE_SENSE) * 0.20D);
        chance += Math.min(3.5D, perk(player, Perk.TABLE_ATTUNEMENT) * 0.35D);
        chance += bloodboundEnchantBonus(player, item, ability);
        if (random.nextDouble() * 100.0D > chance) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> grantAbilityEnchant(player, item, ability, "&dYour enchantment resonates: &f" + ability.display() + " &dwas added."));
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event == null || event.getInventory() == null) {
            return;
        }
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        applyAbilityBookResult(event, left, right);
        if (event.getResult() == null) {
            applyAbilityBookResult(event, right, left);
        }
    }

    private void applyAbilityBookResult(PrepareAnvilEvent event, ItemStack target, ItemStack book) {
        Ability bookAbility = abilityBookAbility(book);
        Ability targetAbility = baseAbilityFor(target);
        if (bookAbility == null || targetAbility == null || bookAbility != targetAbility || hasAwakenedAbility(target, targetAbility)) {
            return;
        }
        ItemStack result = event.getResult() == null || event.getResult().getType() == Material.AIR ? target.clone() : event.getResult().clone();
        writeLore(result, targetAbility, new AbilityState(0, false, true));
        event.setResult(result);
    }

    private Ability abilityBookAbility(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return null;
        }
        for (Ability ability : Ability.values()) {
            if (hasAwakenedAbility(item, ability)) {
                return ability;
            }
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line == null ? "" : line);
            if (stripped == null) {
                continue;
            }
            for (Ability ability : Ability.values()) {
                if (stripped.equalsIgnoreCase("BloodboundSMP Enchant: " + ability.display())
                    || stripped.equalsIgnoreCase("Bloodbound Enchant: " + ability.display())
                    || stripped.equalsIgnoreCase("Awakened Ability: " + ability.display())) {
                    return ability;
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (event.isPlugin() || event.getLoot() == null) {
            return;
        }
        double chance = Math.max(0.0D, data.getDouble("ability.settings.loot_chance_percent", 0.35D));
        for (ItemStack item : event.getLoot()) {
            Ability ability = baseAbilityFor(item);
            if (ability == null || hasAwakenedAbility(item, ability) || !isRareLootHost(item)) {
                continue;
            }
            if (random.nextDouble() * 100.0D <= chance) {
                grantAbilityEnchant(null, item, ability, null);
                return;
            }
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SkillsMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (menu.type().equals("skills")) {
            if (event.getRawSlot() == 4) {
                buyPerk(player, Perk.WORKERS_INSTINCT);
                openSkills(player);
                return;
            }
            MainBranch mainBranch = mainBranchSlot(event.getRawSlot());
            if (mainBranch != null) {
                chooseMainBranch(player, mainBranch);
                openSkills(player);
                return;
            }
            Specialization specialization = specializationSlot(event.getRawSlot());
            if (specialization != null) {
                openSpecialization(player, specialization);
                return;
            }
            Specialization view = specializationViewSlot(event.getRawSlot());
            if (view != null) {
                openSpecialization(player, view);
                return;
            }
            if (event.getRawSlot() == 45) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.0F);
                mechanics(player);
                return;
            }
            if (event.getRawSlot() == 53) {
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.45F, 1.5F);
                openAbilities(player);
                return;
            }
            return;
        }
        if (menu.type().startsWith("spec:")) {
            Specialization specialization = Specialization.valueOf(menu.type().split(":")[1]);
            if (event.getRawSlot() == 45) {
                openSkills(player);
                return;
            }
            if (event.getRawSlot() == 49) {
                openBranchOverview(player, specialization);
                return;
            }
            if (event.getRawSlot() == 53) {
                openAbilities(player);
                return;
            }
            if (event.getRawSlot() == 4 && canChooseSpecialization(player, specialization)) {
                openSpecializationConfirm(player, specialization);
                return;
            }
            Perk perk = specializationPerkAt(specialization, event.getRawSlot());
            if (perk != null) {
                buyPerk(player, perk);
                openSpecialization(player, specialization);
            }
            return;
        }
        if (menu.type().startsWith("choose:")) {
            Specialization specialization = Specialization.valueOf(menu.type().split(":")[1]);
            if (event.getRawSlot() == 11) {
                confirmSpecialization(player, specialization);
                openSpecialization(player, specialization);
                return;
            }
            if (event.getRawSlot() == 15 || event.getRawSlot() == 22) {
                openSpecialization(player, specialization);
            }
            return;
        }
        if (menu.type().startsWith("lane:")) {
            BloodboundLane lane = BloodboundLane.valueOf(menu.type().split(":")[1]);
            if (event.getRawSlot() == 45) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 0.9F);
                openSkills(player);
                return;
            }
            if (event.getRawSlot() == 49) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.1F);
                openLaneInfo(player, lane);
                return;
            }
            if (event.getRawSlot() == 53) {
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.45F, 1.5F);
                openAbilities(player);
                return;
            }
            Perk perk = perkSlot(lane, event.getRawSlot());
            if (perk != null) {
                buyPerk(player, perk);
                openLane(player, lane);
            }
            return;
        }
        if (menu.type().startsWith("category:")) {
            String[] parts = menu.type().split(":");
            Category category = Category.valueOf(parts[1]);
            int page = parts.length >= 3 ? parseInt(parts[2], 0, 0, 99) : 0;
            if (event.getRawSlot() == 45) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 0.9F);
                if (page > 0) {
                    openCategory(player, category, page - 1);
                } else {
                    openSkills(player);
                }
                return;
            }
            if (event.getRawSlot() == 49) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 0.9F);
                openSkills(player);
                return;
            }
            if (event.getRawSlot() == 53) {
                if (page < maxPerkPage(category)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 1.25F);
                    openCategory(player, category, page + 1);
                }
                return;
            }
            Perk perk = perkSlot(category, page, event.getRawSlot());
            if (perk != null) {
                buyPerk(player, perk);
                openCategory(player, category, page);
            }
            return;
        }
        if (menu.type().equals("abilities")) {
            if (event.getRawSlot() == 13) {
                toggleHeldAbility(player);
                openAbilities(player);
            }
        }
    }

    private void openSkills(Player player) {
        SkillsMenu holder = new SkillsMenu("skills");
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&4Bloodbound Specialization Tree"));
        holder.inventory(inventory);
        fillSpecializationFrame(inventory);
        drawFoundationConnectors(inventory);
        inventory.setItem(4, treeNodeIcon(player, Perk.WORKERS_INSTINCT, "&6Root Skill", List.of(
            "&7Everyone starts here.",
            "&7Unlock this before choosing Frontier",
            "&7or Warpath.",
            "&8No free hearts. No money printer."
        )));
        inventory.setItem(20, mainBranchIcon(player, MainBranch.FRONTIER));
        inventory.setItem(24, mainBranchIcon(player, MainBranch.WARPATH));
        for (Specialization specialization : Specialization.values()) {
            inventory.setItem(specialization.slot(), specializationIcon(player, specialization));
            inventory.setItem(specialization.viewSlot(), icon(Material.COMPASS, specialization.color() + "View " + specialization.display() + " Path", List.of(
                "&7Preview is always allowed.",
                "&7Unlocking is only allowed after",
                "&7choosing this specialization.",
                "&8Status: " + specializationStatusLabel(player, specialization)
            )));
        }
        inventory.setItem(45, icon(Material.WRITTEN_BOOK, "&aMechanics Guide", List.of("&7Click for the written Bloodbound guide.")));
        inventory.setItem(49, icon(Material.EXPERIENCE_BOTTLE, "&bProgression Overview", List.of(
            "&7Skillpoints: &f" + points(player),
            "&7Root: " + (rootUnlocked(player) ? "&aUnlocked" : "&cLocked"),
            "&7Direction: &f" + chosenMainDisplay(player),
            "&7Specialization: &f" + chosenSpecializationDisplay(player)
        )));
        inventory.setItem(53, icon(Material.ANVIL, "&dTool Abilities", List.of("&7Click to inspect your held item ability.", "&7Abilities unlock through rare Bloodbound enchants.")));
        player.openInventory(inventory);
    }

    private void openSpecialization(Player player, Specialization specialization) {
        SkillsMenu holder = new SkillsMenu("spec:" + specialization.name());
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color(specialization.color() + specialization.display() + " Path"));
        holder.inventory(inventory);
        fillSpecializationFrame(inventory);
        drawSpecializationConnectors(inventory);
        boolean active = chosenSpecialization(player) == specialization;
        boolean canChoose = canChooseSpecialization(player, specialization);
        List<String> gatewayLore = new ArrayList<>();
        gatewayLore.add("&7Branch: &f" + specialization.main().display());
        gatewayLore.add("&7Page: &f" + specialization.name().toLowerCase(Locale.ROOT) + "_1");
        gatewayLore.add("&7Systems: &f" + specialization.systems());
        gatewayLore.add("&8");
        gatewayLore.add("&7Identity: " + specialization.identity());
        gatewayLore.add("&8");
        gatewayLore.add("&7Status: " + specializationStatusLabel(player, specialization));
        if (active) {
            gatewayLore.add("&aThis is your active specialization.");
        } else if (canChoose) {
            gatewayLore.add("&eClick to choose this specialization.");
            gatewayLore.add("&cThis locks the other paths for upgrades.");
        } else {
            gatewayLore.add("&7Preview Mode");
            gatewayLore.add("&8You can view this branch, but cannot unlock here.");
        }
        inventory.setItem(4, icon(specialization.icon(), specialization.color() + specialization.display() + " Oath", gatewayLore));
        for (Perk perk : specialization.perks()) {
            inventory.setItem(perkSlotConfig(perk), treeNodeIcon(player, perk, specialization.display(), List.of("&7Specialization: &f" + specialization.display())));
        }
        inventory.setItem(45, icon(Material.ARROW, "&aBack", List.of("&7Return to Page 1.")));
        inventory.setItem(49, icon(Material.PAPER, "&eBranch Overview", List.of(
            "&7Preview all branches from Page 1.",
            "&7Current direction: &f" + chosenMainDisplay(player),
            "&7Current specialization: &f" + chosenSpecializationDisplay(player),
            "&7No cherry-picking endgame skills."
        )));
        inventory.setItem(53, icon(Material.ANVIL, "&dTool Abilities", List.of("&7Item-bound abilities remain separate.", "&7Branch capstones can improve Bloodbound enchant chance.")));
        player.openInventory(inventory);
    }

    private void openSpecializationConfirm(Player player, Specialization specialization) {
        SkillsMenu holder = new SkillsMenu("choose:" + specialization.name());
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&4Choose " + specialization.display() + "?"));
        holder.inventory(inventory);
        inventory.setItem(11, icon(Material.GREEN_STAINED_GLASS_PANE, "&aConfirm " + specialization.display(), List.of(
            "&7This chooses &f" + specialization.main().display() + "&7.",
            "&7This locks all other specializations",
            "&7for upgrades.",
            "&8Preview stays available."
        )));
        inventory.setItem(13, icon(specialization.icon(), specialization.color() + specialization.display(), List.of(
            "&7" + specialization.identity(),
            "&8",
            "&7Locked after choosing:",
            "&8- Prospector, Cultivator, Runesmith",
            "&8- Bloodreaver, Bulwark, Marksman",
            "&8Except this chosen path."
        )));
        inventory.setItem(15, icon(Material.RED_STAINED_GLASS_PANE, "&cCancel", List.of("&7Return to preview.")));
        inventory.setItem(22, icon(Material.ARROW, "&7Back", List.of("&7Do not choose yet.")));
        player.openInventory(inventory);
    }

    private void openLane(Player player, BloodboundLane lane) {
        SkillsMenu holder = new SkillsMenu("lane:" + lane.name());
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&4" + lane.display()));
        holder.inventory(inventory);
        fillTreeFrame(inventory);
        drawLaneConnectors(inventory, lane);
        inventory.setItem(4, bloodboundLaneIcon(player, lane));
        inventory.setItem(13, icon(Material.EXPERIENCE_BOTTLE, "&bXP Sources", laneSources(player, lane)));
        List<Perk> perks = perksFor(lane);
        if (perks.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, "&7No active nodes", List.of("&7This lane is disabled in config.")));
        }
        for (Perk perk : perks) {
            inventory.setItem(perkSlotConfig(perk), perkIcon(player, perk));
        }
        inventory.setItem(45, icon(Material.ARROW, "&aBack", List.of("&7Return to the Bloodbound skilltree.")));
        inventory.setItem(49, icon(Material.PAPER, "&eLane Info", laneInfoLore(lane)));
        inventory.setItem(53, icon(Material.ANVIL, "&dTool Abilities", List.of("&7Item-bound abilities are separate.", "&7Use &f/abilities &7for your held item.")));
        player.openInventory(inventory);
    }

    private void openLaneInfo(Player player, BloodboundLane lane) {
        player.sendActionBar(Text.color("&4" + lane.display() + " &8| &7" + lane.shortGoal()));
    }

    private void openCategory(Player player, Category category, int page) {
        int maxPage = maxPerkPage(category);
        page = Math.max(0, Math.min(maxPage, page));
        SkillsMenu holder = new SkillsMenu("category:" + category.name() + ":" + page);
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.color("&8" + category.display()));
        holder.inventory(inventory);
        inventory.setItem(4, categoryIcon(player, category));
        int level = level(player, category);
        int totalXp = xp(player, category);
        inventory.setItem(13, icon(Material.EXPERIENCE_BOTTLE, "&bLevel &f" + level + "&7/100", List.of(
            level >= MAX_LEVEL ? "&7XP: &aMAX" : "&7XP: &f" + xpIntoLevel(totalXp) + "&7/&f" + nextXp(level),
            "&7Total XP: &f" + totalXp,
            "&7Progress is per player.",
            "&7Skillpoints: &f" + points(player)
        )));
        int[] slots = perkDisplaySlots();
        List<Perk> perks = perksFor(category);
        int start = page * slots.length;
        int end = Math.min(perks.size(), start + slots.length);
        if (perks.isEmpty()) {
            inventory.setItem(22, icon(Material.BARRIER, "&7No active perks", List.of("&7This category has no visible perks yet.")));
        }
        for (int index = start; index < end; index++) {
            inventory.setItem(slots[index - start], perkIcon(player, perks.get(index)));
        }
        inventory.setItem(45, icon(Material.ARROW, page > 0 ? "&aPrevious page" : "&aBack", page > 0 ? List.of("&7Page " + page + " of " + (maxPage + 1) + ".") : List.of("&7Return to the skilltree.")));
        inventory.setItem(49, icon(Material.NETHER_STAR, "&aSkillpoints: &f" + points(player), List.of("&7Page: &f" + (page + 1) + "/" + (maxPage + 1), "&7Locked perks stay visible.", "&7Perks cost 1 skillpoint.")));
        inventory.setItem(53, icon(Material.ARROW, page < maxPage ? "&aNext page" : "&7Last page", page < maxPage ? List.of("&7View more perks.") : categoryGuide(category)));
        player.openInventory(inventory);
    }

    private void openAbilities(Player player) {
        SkillsMenu holder = new SkillsMenu("abilities");
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.color("&8Tool Ability"));
        holder.inventory(inventory);
        ItemStack hand = player.getInventory().getItemInMainHand();
        Ability baseAbility = baseAbilityFor(hand);
        Ability ability = abilityFor(hand);
        if (baseAbility == null) {
            inventory.setItem(13, icon(Material.BARRIER, "&cNo ability item", List.of("&7Hold a weapon, tool, or shield.", "&7Then this menu shows its item-bound challenge.")));
        } else if (ability == null) {
            inventory.setItem(11, hand.clone());
            inventory.setItem(13, icon(Material.ANVIL, "&dNo ability enchant", List.of(
                "&7This item can roll &f" + baseAbility.display() + "&7.",
                "&7Enchant it at an enchanting table",
                "&7for a rare Bloodbound enchant chance.",
                "&7The challenge appears after that."
            )));
            inventory.setItem(15, icon(Material.PAPER, "&e" + baseAbility.display(), baseAbility.description()));
        } else {
            AbilityState state = state(hand, ability);
            writeLore(hand, ability, state);
            inventory.setItem(11, hand.clone());
            inventory.setItem(13, icon(state.enabled() ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, state.enabled() ? "&aAbility ON" : "&cAbility OFF", List.of(
                "&7Click to toggle.",
                "&7Command: &f/abilities toggle",
                "&7Unlocked: &f" + state.unlocked(),
                "&7Progress: &f" + state.progress() + "/" + required(ability)
            )));
            inventory.setItem(15, icon(Material.PAPER, "&e" + ability.display(), ability.description()));
        }
        player.openInventory(inventory);
    }

    private boolean skillsAdmin(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.skills.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length < 2) {
            Text.msg(sender, "&cUsage: /skillsadmin <addxp|givepoint|points|reset|unlock|setbranch|clearbranch|debug|testgui|reload> ...");
            return true;
        }
        if (args[1].equalsIgnoreCase("reload")) {
            skillConfig.load();
            writeDefaultSkilltreeConfig();
            Text.msg(sender, "&aBloodbound skilltree config reloaded.");
            return true;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
        if (target == null) {
            Text.msg(sender, "&cPlayer is not online.");
            return true;
        }
        if (args[1].equalsIgnoreCase("addxp")) {
            if (args.length < 5) {
                Text.msg(sender, "&cUsage: /skills admin addxp <player> <category> <amount>");
                return true;
            }
            Category category = Category.from(args[3]);
            if (category == null) {
                Text.msg(sender, "&cUnknown category.");
                return true;
            }
            addSkillXp(target, category, parseInt(args[4], 0, 0, 10_000_000));
            Text.msg(sender, "&aXP granted.");
            return true;
        }
        if (args[1].equalsIgnoreCase("points")) {
            if (args.length < 4) {
                Text.msg(sender, "&cUsage: /skillsadmin points <player> <amount>");
                return true;
            }
            data.set("points." + profileKey(target.getUniqueId()), Math.max(0, parseInt(args[3], 0, 0, 10_000)));
            data.save();
            Text.msg(sender, "&aSkillpoints updated.");
            return true;
        }
        if (args[1].equalsIgnoreCase("givepoint")) {
            if (args.length < 4) {
                Text.msg(sender, "&cUsage: /skillsadmin givepoint <player> <amount>");
                return true;
            }
            int amount = parseInt(args[3], 0, -10_000, 10_000);
            data.set("points." + profileKey(target.getUniqueId()), Math.max(0, points(target) + amount));
            data.save();
            Text.msg(sender, "&aSkillpoints changed by &f" + amount + "&a for &f" + target.getName() + "&a.");
            return true;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            resetPlayer(target);
            Text.msg(sender, "&aSkills reset for &f" + target.getName() + "&a.");
            return true;
        }
        if (args[1].equalsIgnoreCase("unlock")) {
            if (args.length < 4) {
                Text.msg(sender, "&cUsage: /skillsadmin unlock <player> <node>");
                return true;
            }
            Perk perk = Perk.from(args[3]);
            if (perk == null) {
                Text.msg(sender, "&cUnknown node.");
                return true;
            }
            if (perk == Perk.WORKERS_INSTINCT) {
                data.set("tree." + profileKey(target.getUniqueId()) + ".root", true);
            } else {
                data.set("perk." + profileKey(target.getUniqueId()) + "." + perk.key(), Math.max(1, perk(target, perk)));
            }
            data.save();
            Text.msg(sender, "&aUnlocked &f" + perkDisplay(perk) + " &afor &f" + target.getName() + "&a.");
            return true;
        }
        if (args[1].equalsIgnoreCase("setbranch")) {
            if (args.length < 4) {
                Text.msg(sender, "&cUsage: /skillsadmin setbranch <player> <specialization>");
                Text.msg(sender, "&7Optional: &f/skillsadmin setbranch <player> <frontier|warpath> <specialization>");
                return true;
            }
            Specialization specialization = args.length >= 5 ? Specialization.from(args[4]) : Specialization.from(args[3]);
            MainBranch branch = args.length >= 5 ? MainBranch.from(args[3]) : specialization == null ? null : specialization.main();
            if (specialization == null || branch == null || specialization.main() != branch) {
                Text.msg(sender, "&cUnknown or mismatched branch/specialization.");
                return true;
            }
            data.set("tree." + profileKey(target.getUniqueId()) + ".root", true);
            data.set("tree." + profileKey(target.getUniqueId()) + ".main", branch.name());
            data.set("tree." + profileKey(target.getUniqueId()) + ".specialization", specialization.name());
            data.set("tree." + profileKey(target.getUniqueId()) + ".page." + specialization.name(), true);
            data.save();
            Text.msg(sender, "&aSet &f" + target.getName() + " &ato &f" + specialization.display() + "&a.");
            return true;
        }
        if (args[1].equalsIgnoreCase("clearbranch")) {
            data.set("tree." + profileKey(target.getUniqueId()) + ".main", null);
            data.set("tree." + profileKey(target.getUniqueId()) + ".specialization", null);
            for (Specialization specialization : Specialization.values()) {
                data.set("tree." + profileKey(target.getUniqueId()) + ".page." + specialization.name(), null);
            }
            data.save();
            Text.msg(sender, "&aBranch choice cleared for &f" + target.getName() + "&a.");
            return true;
        }
        if (args[1].equalsIgnoreCase("debug")) {
            Text.msg(sender, "&5Skill debug for &f" + target.getName());
            Text.msg(sender, "&7Root=&f" + rootUnlocked(target) + " &7Main=&f" + chosenMainDisplay(target) + " &7Spec=&f" + chosenSpecializationDisplay(target));
            Text.msg(sender, "&7Points=&f" + points(target) + " &7Mining=&f" + level(target, Category.MINING) + " &7Farming=&f" + level(target, Category.FARMING) + " &7Combat=&f" + level(target, Category.COMBAT));
            Text.msg(sender, "&7Alchemy=&f" + level(target, Category.ALCHEMY) + " &7Enchanting=&f" + level(target, Category.ENCHANTING) + " &7Economy=&f" + level(target, Category.ECONOMY));
            return true;
        }
        if (args[1].equalsIgnoreCase("testgui")) {
            if (args.length >= 4) {
                Specialization specialization = Specialization.from(args[3]);
                if (specialization != null) {
                    openSpecialization(target, specialization);
                    Text.msg(sender, "&aOpened specialization preview for &f" + target.getName() + "&a.");
                    return true;
                }
            }
            openSkills(target);
            Text.msg(sender, "&aOpened skilltree GUI for &f" + target.getName() + "&a.");
            return true;
        }
        return true;
    }

    private boolean abilityGrant(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.skills.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Ability ability = args.length < 2 ? baseAbilityFor(hand) : Ability.from(args[1]);
        if (ability == null) {
            Text.msg(sender, args.length < 2 ? "&cHold a supported tool or weapon, or use &f/abilities grant <ability>&c." : "&cUnknown ability.");
            return true;
        }
        if (baseAbilityFor(hand) != ability) {
            Text.msg(sender, "&cHold a matching item for &f" + ability.display() + "&c.");
            return true;
        }
        grantAbilityEnchant(player, hand, ability, "&dBloodbound enchant applied: &f" + ability.display() + "&d.");
        return true;
    }

    private boolean abilityComplete(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.skills.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Ability ability = abilityFor(hand);
        if (ability == null) {
            Text.msg(sender, "&cHold a Bloodbound ability item.");
            return true;
        }
        AbilityState complete = new AbilityState(required(ability), true, true);
        writeLore(hand, ability, complete);
        Text.msg(sender, "&aChallenge completed for &f" + ability.display() + "&a.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        return true;
    }

    private boolean abilityTestKit(CommandSender sender) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.skills.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.msg(sender, "&cPlayers only.");
            return true;
        }
        List<ItemStack> items = List.of(
            abilityItem(Material.NETHERITE_PICKAXE, Ability.GODS_DRILL, "&dTest Pickaxe: God's Drill", Enchantment.EFFICIENCY, 8),
            abilityItem(Material.NETHERITE_AXE, Ability.ANCIENT_TIMBER, "&dTest Axe: Ancient Timber", Enchantment.EFFICIENCY, 8),
            abilityItem(Material.NETHERITE_SHOVEL, Ability.EARTHSHAPER, "&dTest Shovel: Earthshaper", Enchantment.EFFICIENCY, 8),
            abilityItem(Material.NETHERITE_HOE, Ability.HARVEST_LORD, "&dTest Hoe: Harvest Lord", Enchantment.EFFICIENCY, 8),
            abilityItem(Material.NETHERITE_SWORD, Ability.BLOOD_FORGED_EDGE, "&dTest Sword: Blood-Forged Edge", Enchantment.SHARPNESS, 8),
            abilityItem(Material.BOW, Ability.ECHO_QUIVER, "&dTest Bow: Echo Quiver", Enchantment.POWER, 8),
            abilityItem(Material.TRIDENT, Ability.STORM_BIND, "&dTest Trident: Storm Bind", Enchantment.UNBREAKING, 8),
            abilityItem(Material.SHIELD, Ability.AEGIS_GUARD, "&dTest Shield: Aegis Guard", Enchantment.UNBREAKING, 8)
        );
        player.getInventory().addItem(items.toArray(ItemStack[]::new)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Text.msg(player, "&aAbility testkit granted. Use &f/abilities toggle &aor the &f/abilities &aGUI to toggle the held item.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.2F);
        return true;
    }

    private ItemStack abilityItem(Material material, Ability ability, String name, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setUnbreakable(true);
            meta.addEnchant(enchantment, level, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.setLore(List.of(Text.color("&8Admin test item - not a normal drop.")));
            item.setItemMeta(meta);
        }
        writeLore(item, ability, new AbilityState(required(ability), true, true));
        return item;
    }

    private boolean abilityConfig(CommandSender sender, String[] args) {
        if (!MitchSMP.permissions().has(sender, "mitchsmp.skills.admin")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        if (args.length == 1 || args.length == 2 && args[1].equalsIgnoreCase("list")) {
            Text.msg(sender, "&5Ability configuration:");
            for (Ability ability : Ability.values()) {
                String cooldown = ability.pvpRelated() ? " &7cooldown=&f" + formatSeconds(cooldownSeconds(ability)) + "s" : "";
                String details = ability == Ability.BLOOD_FORGED_EDGE
                    ? " &7duration=&f" + formatSeconds(data.getDouble("ability.blood_forged_edge.active_duration_seconds", 8.0D)) + "s"
                    : ability == Ability.AEGIS_GUARD
                        ? " &7duration=&f" + formatSeconds(data.getDouble("ability.aegis_guard.active_duration_seconds", 12.0D)) + "s &7absorption=&f100%"
                        : "";
                Text.msg(sender, "&d" + ability.id() + " &7required=&f" + required(ability) + " &7enabled=&f" + abilityGloballyEnabled(ability) + cooldown + details);
            }
            Text.msg(sender, "&7Global: enchant_min_level=&f" + data.getInt("ability.settings.enchant_min_level", 25)
                + " &7enchant_chance_percent=&f" + data.getDouble("ability.settings.enchant_chance_percent", 4.0D)
                + " &7loot_chance_percent=&f" + data.getDouble("ability.settings.loot_chance_percent", 0.35D));
            return true;
        }
        if (args.length < 4) {
            Text.msg(sender, "&cUsage: /abilities config <ability> <required|enabled|cooldown|duration|hits|mitigation> <value>");
            return true;
        }
        if (args[1].equalsIgnoreCase("settings")) {
            return abilitySettings(sender, args);
        }
        Ability ability = Ability.from(args[1]);
        if (ability == null) {
            Text.msg(sender, "&cUnknown ability.");
            return true;
        }
        if (args[2].equalsIgnoreCase("required")) {
            data.set("ability." + ability.id() + ".required", Math.max(1, parseInt(args[3], ability.defaultRequired(), 1, 100_000_000)));
        } else if (args[2].equalsIgnoreCase("enabled")) {
            data.set("ability." + ability.id() + ".enabled", Boolean.parseBoolean(args[3]));
        } else if (args[2].equalsIgnoreCase("cooldown") && ability.pvpRelated()) {
            try {
                data.set("ability." + ability.id() + ".cooldown_seconds", Math.max(0.0D, Math.min(86_400.0D, Double.parseDouble(args[3]))));
            } catch (NumberFormatException exception) {
                Text.msg(sender, "&cCooldown must be a number of seconds.");
                return true;
            }
        } else if (args[2].equalsIgnoreCase("duration") && (ability == Ability.BLOOD_FORGED_EDGE || ability == Ability.AEGIS_GUARD)) {
            try {
                data.set("ability." + ability.id() + ".active_duration_seconds", Math.max(0.5D, Math.min(3600.0D, Double.parseDouble(args[3]))));
            } catch (NumberFormatException exception) {
                Text.msg(sender, "&cDuration must be a number of seconds.");
                return true;
            }
        } else if (args[2].equalsIgnoreCase("hits") && ability == Ability.AEGIS_GUARD) {
            data.set("ability.aegis_guard.hits_before_cooldown", parseInt(args[3], 5, 1, 100));
        } else if (args[2].equalsIgnoreCase("mitigation") && ability == Ability.AEGIS_GUARD) {
            try {
                data.set("ability.aegis_guard.mitigation_percent", Math.max(0.0D, Math.min(95.0D, Double.parseDouble(args[3]))));
            } catch (NumberFormatException exception) {
                Text.msg(sender, "&cMitigation must be a percentage.");
                return true;
            }
        } else {
            Text.msg(sender, "&cUnknown setting.");
            return true;
        }
        data.save();
        Text.msg(sender, "&aAbility config updated.");
        return true;
    }

    private boolean abilitySettings(CommandSender sender, String[] args) {
        String key = args[2].toLowerCase(Locale.ROOT);
        if (!List.of("enchant_min_level", "enchant_chance_percent", "loot_chance_percent").contains(key)) {
            Text.msg(sender, "&cUnknown setting. Use enchant_min_level, enchant_chance_percent or loot_chance_percent.");
            return true;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException exception) {
            Text.msg(sender, "&cValue must be a number.");
            return true;
        }
        if (key.equals("enchant_min_level")) {
            data.set("ability.settings." + key, Math.max(1, Math.min(30, (int) value)));
        } else {
            data.set("ability.settings." + key, Math.max(0.0D, Math.min(100.0D, value)));
        }
        data.save();
        Text.msg(sender, "&aAbility setting &f" + key + " &aset to &f" + data.getString("ability.settings." + key, String.valueOf(value)) + "&a.");
        return true;
    }

    private void addSkillXp(Player player, Category category, int amount) {
        addSkillXp(player.getUniqueId(), player, category, amount);
    }

    private void addSkillXp(UUID id, Player player, Category category, int amount) {
        if (amount <= 0 || level(id, category) >= MAX_LEVEL) {
            return;
        }
        int oldLevel = level(id, category);
        int newXp = xp(id, category) + amount;
        int newLevel = levelFromXp(newXp);
        data.set(skillKey(id, category, "xp"), newXp);
        data.set(skillKey(id, category, "level"), newLevel);
        if (newLevel > oldLevel) {
            int gained = newLevel - oldLevel;
            data.set("points." + profileKey(id), points(id) + gained);
            if (player != null) {
                Text.msg(player, "&a" + category.display() + " level up: &f" + oldLevel + " -> " + newLevel + " &7(+" + gained + " skillpoint)");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65F, 1.35F);
                player.sendTitle(Text.color("&6" + category.display() + " Level " + newLevel), Text.color("&f+" + gained + " skillpoint"), 10, 50, 15);
            }
        }
        if (newLevel > oldLevel) {
            saveSkillDataSoon();
            skillDataDirty = false;
        } else {
            skillDataDirty = true;
        }
    }

    private void saveSkillDataSoon() {
        data.saveSoon(this, 80L);
    }

    private void flushSkillData() {
        if (skillDataDirty) {
            data.save();
            skillDataDirty = false;
        }
    }

    private int levelFromXp(int xp) {
        int level = 1;
        int remaining = Math.max(0, xp);
        while (level < MAX_LEVEL && remaining >= nextXp(level)) {
            remaining -= nextXp(level);
            level++;
        }
        return level;
    }

    private int nextXp(int level) {
        return Math.max(250, level * level * 80);
    }

    private int xp(Player player, Category category) {
        return xp(player.getUniqueId(), category);
    }

    private int xp(UUID id, Category category) {
        return data.getInt(skillKey(id, category, "xp"), 0);
    }

    private int level(Player player, Category category) {
        UUID id = player.getUniqueId();
        return level(id, category);
    }

    private int level(UUID id, Category category) {
        int computed = levelFromXp(xp(id, category));
        int stored = data.getInt(skillKey(id, category, "level"), computed);
        int level = Math.max(1, Math.min(MAX_LEVEL, computed));
        if (stored != level) {
            if (level > stored) {
                data.set("points." + profileKey(id), points(id) + (level - stored));
            }
            data.set(skillKey(id, category, "level"), level);
            saveSkillDataSoon();
        }
        return level;
    }

    private int xpIntoLevel(int totalXp) {
        int level = 1;
        int remaining = Math.max(0, totalXp);
        while (level < MAX_LEVEL && remaining >= nextXp(level)) {
            remaining -= nextXp(level);
            level++;
        }
        return level >= MAX_LEVEL ? 0 : remaining;
    }

    private String skillKey(UUID id, Category category, String field) {
        return "skill." + profileKey(id) + "." + category.key() + "." + field;
    }

    private int points(Player player) {
        return points(player.getUniqueId());
    }

    private int points(UUID id) {
        return data.getInt("points." + profileKey(id), 0);
    }

    private int perk(Player player, Perk perk) {
        return Math.min(perkMax(perk), data.getInt("perk." + profileKey(player.getUniqueId()) + "." + perk.key(), 0));
    }

    private void buyPerk(Player player, Perk perk) {
        if (perk == Perk.WORKERS_INSTINCT) {
            buyRootSkill(player);
            return;
        }
        Specialization specialization = specializationOf(perk);
        if (specialization != null && chosenSpecialization(player) != specialization) {
            if (chosenSpecialization(player) == null) {
                Text.msg(player, "&cChoose the &f" + specialization.display() + " &cspecialization before upgrading this path.");
            } else {
                Text.msg(player, "&cPreview only. You chose &f" + chosenSpecialization(player).display() + "&c.");
            }
            return;
        }
        int current = perk(player, perk);
        if (current >= perkMax(perk)) {
            Text.msg(player, "&cThis perk is maxed.");
            return;
        }
        if (points(player) <= 0) {
            Text.msg(player, "&cYou do not have skillpoints.");
            return;
        }
        if (level(player, perk.category()) < perkRequiredLevel(perk)) {
            Text.msg(player, "&cYou need level &f" + perkRequiredLevel(perk) + " &cin " + perk.category().display() + "&c.");
            return;
        }
        if (!requirementsMet(player, perk)) {
            Text.msg(player, "&cThis node needs an earlier skill first.");
            for (String line : requirementLore(player, perk)) {
                Text.msg(player, line);
            }
            return;
        }
        SkillLane lane = lane(perk);
        SkillLane chosen = chosenLane(player, perk.category());
        if (lane.exclusive() && chosen != null && chosen != lane) {
            Text.msg(player, "&cYou already chose the &f" + chosen.display() + " &cpath in " + perk.category().display() + "&c.");
            Text.msg(player, "&7Reset this skilltree if you want to rebuild into &f" + lane.display() + "&7.");
            return;
        }
        data.set("perk." + profileKey(player.getUniqueId()) + "." + perk.key(), current + 1);
        data.set("points." + profileKey(player.getUniqueId()), points(player) - 1);
        saveSkillDataSoon();
        Text.msg(player, "&aPerk purchased: &f" + perkDisplay(perk) + " " + (current + 1) + "/" + perkMax(perk) + " &8[" + lane.display() + "]");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.5F);
    }

    private void buyRootSkill(Player player) {
        if (rootUnlocked(player)) {
            Text.msg(player, "&cWorker's Instinct is already unlocked.");
            return;
        }
        if (points(player) <= 0) {
            Text.msg(player, "&cYou need 1 skillpoint to unlock Worker's Instinct.");
            return;
        }
        data.set("tree." + profileKey(player.getUniqueId()) + ".root", true);
        data.set("points." + profileKey(player.getUniqueId()), points(player) - 1);
        saveSkillDataSoon();
        Text.msg(player, "&aUnlocked: &fWorker's Instinct&a. Choose Frontier or Warpath next.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
    }

    private SkillLane chosenLane(Player player, Category category) {
        for (Perk perk : perksFor(category)) {
            SkillLane lane = lane(perk);
            if (lane.exclusive() && perk(player, perk) > 0) {
                return lane;
            }
        }
        return null;
    }

    private SkillLane lane(Perk perk) {
        String configured = skillConfig.getString("node." + perk.key() + ".lane", "");
        SkillLane override = SkillLane.from(configured);
        if (override != null) {
            return override;
        }
        return switch (perk) {
            case MINING_SPEED, VEIN_DISCIPLINE -> SkillLane.TUNNEL_RAT;
            case MINING_YIELD, ORE_SURVEYOR -> SkillLane.PROSPECTOR;
            case DEEP_MINER -> SkillLane.DEEP_MINER;
            case FARMING_YIELD, REPLANTER, HARVEST_FLOW -> SkillLane.CULTIVATOR;
            case FORESTER -> SkillLane.FORESTER;
            case SUPPLY_GARDENER -> SkillLane.HOMESTEADER;
            case DUELIST -> SkillLane.REAVER;
            case COMBAT_SUSTAIN -> SkillLane.WARDEN;
            case ESCAPE_DISCIPLINE -> SkillLane.PHANTOM;
            case BOUNTY_FOCUS, KINGSLAYER_FOCUS -> SkillLane.HEXBLADE;
            case BREWING_FOCUS, ALCHEMY_MASTERY -> SkillLane.BREWER;
            case APPLE_LORE, RELIC_ALCHEMY -> SkillLane.RELICIST;
            case INFERNAL_RESOLVE -> SkillLane.INFERNALIST;
            case RUNE_SENSE, TABLE_ATTUNEMENT -> SkillLane.RUNEWRIGHT;
            case BOOKSMITH, ANVIL_CARE -> SkillLane.BOOKSMITH;
            case ENCHANTING_MASTERY -> SkillLane.ARCANIST;
            case ECONOMY_QUICKSELL_EFFICIENCY -> SkillLane.MERCHANT;
            case ORDER_RUNNER -> SkillLane.LOGISTICIAN;
            case CONTRACT_BROKER -> SkillLane.CONTRACTOR;
            default -> SkillLane.MASTERY;
        };
    }

    private void resetPlayer(Player player) {
        UUID id = player.getUniqueId();
        for (String key : data.keys()) {
            if (key.contains(id.toString())) {
                data.set(key, null);
            }
        }
        data.save();
    }

    private void applyMiningPerks(Player player, Block block, ItemStack tool) {
        int speed = perk(player, Perk.MINING_SPEED);
        if (speed > 0) {
            int mastery = perk(player, Perk.MINING_MASTERY);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 140, Math.min(3, (speed + mastery) / 6), false, false, true));
        }
        int yield = perk(player, Perk.MINING_YIELD);
        double yieldChance = yield * 2.5D + perk(player, Perk.ORE_SURVEYOR) * 1.5D + perk(player, Perk.VEIN_DISCIPLINE) + perk(player, Perk.MINING_MASTERY) * 3.0D;
        if (yieldChance > 0.0D && block.getType().name().contains("ORE") && Math.random() * 100.0D < Math.min(75.0D, yieldChance)) {
            player.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(block.getType()));
        }
    }

    private void applyFarmingPerks(Player player, Block block, ItemStack tool) {
        int yield = perk(player, Perk.FARMING_YIELD);
        double yieldChance = yield * 3.5D + perk(player, Perk.FARMING_MASTERY) * 2.5D;
        if (block.getType().name().contains("LOG")) {
            yieldChance += perk(player, Perk.FORESTER) * 1.5D;
        }
        if (yieldChance > 0.0D && Math.random() * 100.0D < Math.min(95.0D, yieldChance)) {
            player.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(block.getType()));
        }
        int replanter = perk(player, Perk.REPLANTER);
        Material crop = block.getType();
        if (replanter >= perkMax(Perk.REPLANTER) && isCropLike(crop)) {
            Bukkit.getScheduler().runTask(this, () -> replantArea(player, block, crop, 2, 16));
        } else if (replanter > 0 && isCropLike(crop) && Math.random() * 100.0D < 45.0D + replanter * 5.0D) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (block.getType() == Material.AIR) {
                    block.setType(crop, false);
                }
            });
        }
        int flow = perk(player, Perk.HARVEST_FLOW);
        if (flow > 0 && isCropLike(crop) && tool != null && tool.getType().name().endsWith("_HOE")) {
            int radius = flow >= 8 ? 2 : 1;
            int cap = Math.min(20, 3 + flow * 2);
            breakArea(player, block, tool, radius, this::isCropLike, cap);
        }
    }

    private boolean openPortableStation(Player player, String type) {
        boolean anvil = type.equalsIgnoreCase("anvil");
        if (!MitchSMP.permissions().has(player, anvil ? "mitchsmp.skills.anvil" : "mitchsmp.skills.enchant")) {
            Text.msg(player, "&cYou do not have permission.");
            return true;
        }
        if (chosenSpecialization(player) != Specialization.RUNESMITH || perk(player, Perk.ARCANE_WORKSTATION) <= 0) {
            Text.msg(player, "&cYou need &fArcane Workstation &cin the Runesmith path to use /" + type.toLowerCase(Locale.ROOT) + ".");
            return true;
        }
        if (MitchSMP.combatTags() != null && MitchSMP.combatTags().isTagged(player.getUniqueId())) {
            Text.msg(player, "&cYou cannot use /" + type.toLowerCase(Locale.ROOT) + " while combat tagged.");
            return true;
        }
        String method = anvil ? "openAnvil" : "openEnchanting";
        try {
            player.getClass().getMethod(method, Location.class, boolean.class).invoke(player, player.getLocation(), true);
            player.sendActionBar(Text.color("&5" + perkDisplay(Perk.ARCANE_WORKSTATION) + " &8| &aPortable " + type.toLowerCase(Locale.ROOT) + " opened"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Text.msg(player, "&cPortable " + type.toLowerCase(Locale.ROOT) + " is unavailable on this server build.");
            getLogger().warning("Could not open portable " + type + " for " + player.getName() + ": " + exception.getMessage());
        }
        return true;
    }

    private void accelerateBrewing(Player player, Block block) {
        int level = perk(player, Perk.BREWING_FOCUS);
        long now = System.currentTimeMillis();
        if (level <= 0 || now - brewingBoostCooldowns.getOrDefault(player.getUniqueId(), 0L) < 30_000L) {
            return;
        }
        try {
            Object state = block.getClass().getMethod("getState").invoke(block);
            int current = ((Number) state.getClass().getMethod("getBrewingTime").invoke(state)).intValue();
            if (current <= 0) {
                return;
            }
            int reduced = Math.max(1, (int) Math.round(current * (1.0D - Math.min(0.50D, level * 0.05D))));
            state.getClass().getMethod("setBrewingTime", int.class).invoke(state, reduced);
            state.getClass().getMethod("update").invoke(state);
            brewingBoostCooldowns.put(player.getUniqueId(), now);
            player.sendActionBar(Text.color("&5Brewing Focus &8| &a" + (current - reduced) + " ticks saved"));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void extendActivePotionEffects(Player player) {
        int level = perk(player, Perk.ALCHEMY_GRANDMASTER);
        if (level <= 0) {
            return;
        }
        try {
            Object raw = player.getClass().getMethod("getActivePotionEffects").invoke(player);
            if (!(raw instanceof Iterable<?> effects)) {
                return;
            }
            for (Object effect : effects) {
                PotionEffectType type = (PotionEffectType) effect.getClass().getMethod("getType").invoke(effect);
                int duration = ((Number) effect.getClass().getMethod("getDuration").invoke(effect)).intValue();
                int amplifier = ((Number) effect.getClass().getMethod("getAmplifier").invoke(effect)).intValue();
                boolean ambient = (Boolean) effect.getClass().getMethod("isAmbient").invoke(effect);
                boolean particles = (Boolean) effect.getClass().getMethod("hasParticles").invoke(effect);
                boolean icon = (Boolean) effect.getClass().getMethod("hasIcon").invoke(effect);
                int extended = (int) Math.round(duration * (1.0D + level * 0.05D));
                player.addPotionEffect(new PotionEffect(type, extended, amplifier, ambient, particles, icon));
            }
            player.sendActionBar(Text.color("&5Alchemy Grandmaster &8| &aPotion duration extended"));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void applyPassiveSkillEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (restricted(player) || player.getWorld() == null || player.getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER) {
                continue;
            }
            int resolve = perk(player, Perk.INFERNAL_RESOLVE);
            if (resolve > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 140, 0, false, false, true));
            }
        }
    }

    private void tryEscape(Player defender, EntityDamageByEntityEvent event) {
        int level = perk(defender, Perk.ESCAPE_DISCIPLINE);
        if (level <= 0 || data.getLong(aegisActiveKey(defender.getUniqueId()), 0L) > System.currentTimeMillis()) {
            return;
        }
        AttributeInstance attribute = defender.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attribute == null ? 20.0D : Math.max(1.0D, attribute.getBaseValue());
        if (defender.getHealth() - damageAmount(event) > maxHealth * 0.20D) {
            return;
        }
        String key = "perk.escape_discipline.cooldown." + profileKey(defender.getUniqueId());
        long now = System.currentTimeMillis();
        if (data.getLong(key, 0L) > now) {
            return;
        }
        Location origin = defender.getLocation();
        Location attackerLocation = event.getDamager() == null ? null : event.getDamager().getLocation();
        double dx = attackerLocation == null ? 1.0D : origin.getX() - attackerLocation.getX();
        double dz = attackerLocation == null ? 1.0D : origin.getZ() - attackerLocation.getZ();
        double length = Math.max(0.01D, Math.sqrt(dx * dx + dz * dz));
        double distance = 10.0D + level * 4.0D;
        Location destination = new Location(origin.getWorld(), origin.getX() + dx / length * distance, origin.getY() + 2.0D, origin.getZ() + dz / length * distance, origin.getYaw(), origin.getPitch());
        multiplyDamage(event, 0.0D);
        defender.teleport(destination);
        defender.setFallDistance(0.0F);
        defender.setNoDamageTicks(60);
        data.set(key, now + 60L * 60L * 1000L);
        saveSkillDataSoon();
        if (origin.getWorld() != null) {
            origin.getWorld().createExplosion(origin, 0.0F, false, false);
            origin.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, origin, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        defender.sendActionBar(Text.color("&5Escape Discipline &8| &aTriggered &8| &c60m cooldown"));
    }

    private void updateKingslayerTargets() {
        for (UUID id : new HashSet<>(kingslayerGlowing)) {
            Player target = Bukkit.getPlayer(id);
            if (target != null) {
                setGlowing(target, false);
            }
        }
        kingslayerGlowing.clear();
        long now = System.currentTimeMillis();
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            int level = perk(hunter, Perk.KINGSLAYER_FOCUS);
            if (level <= 0 || restricted(hunter)) {
                continue;
            }
            double range = Math.min(128.0D, 32.0D + level * 9.6D);
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(hunter) || restricted(target) || target.getWorld() == null || !target.getWorld().equals(hunter.getWorld())
                    || target.getLocation().distanceSquared(hunter.getLocation()) > range * range) {
                    continue;
                }
                boolean valuable = MitchSMP.hearts() != null && MitchSMP.hearts().getHearts(target.getUniqueId()) >= 20
                    || MitchSMP.bounties() != null && MitchSMP.bounties().getBounty(target.getUniqueId()) > 0.0D;
                if (!valuable) {
                    continue;
                }
                setGlowing(target, true);
                kingslayerGlowing.add(target.getUniqueId());
                if (now - kingslayerWarnings.getOrDefault(target.getUniqueId(), 0L) >= 5L * 60L * 1000L) {
                    kingslayerWarnings.put(target.getUniqueId(), now);
                    target.sendActionBar(Text.color("&4A Kingslayer hunter has detected you within &f" + Math.round(range) + " blocks&4."));
                }
            }
        }
    }

    private void setGlowing(Player player, boolean glowing) {
        try {
            player.getClass().getMethod("setGlowing", boolean.class).invoke(player, glowing);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private AbilityState progress(Player player, ItemStack item, Ability ability, AbilityState state, int amount) {
        if (item == null || amount <= 0) {
            return state;
        }
        int required = required(ability);
        if (state.unlocked()) {
            writeLore(item, ability, state);
            return state;
        }
        int progress = Math.min(required, state.progress() + amount);
        boolean unlocked = progress >= required;
        AbilityState updated = new AbilityState(progress, unlocked, state.enabled());
        writeLore(item, ability, updated);
        if (unlocked) {
            Text.msg(player, "&6Endgame ability unlocked on your item: &f" + ability.display() + "&6.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
        return updated;
    }

    private void runBlockAbility(Player player, Block origin, Material originType, ItemStack tool, Ability ability) {
        if (ability == Ability.GODS_DRILL) {
            breakOrientedArea(player, origin, tool, 1, this::isDrillBlock, 9);
        } else if (ability == Ability.ANCIENT_TIMBER) {
            breakTimber(player, origin, originType, tool);
        } else if (ability == Ability.EARTHSHAPER) {
            breakOrientedArea(player, origin, tool, 1, this::isShovelBlock, 9);
        } else if (ability == Ability.HARVEST_LORD) {
            replantArea(player, origin, originType, 1, 9);
        }
    }

    private void breakArea(Player player, Block origin, ItemStack tool, int radius, MaterialFilter filter, int cap) {
        if (origin == null || origin.getLocation() == null || origin.getLocation().getWorld() == null) {
            return;
        }
        abilityBreaking.add(player.getUniqueId());
        try {
            int broken = 0;
            Location base = origin.getLocation();
            for (int dx = -radius; dx <= radius && broken < cap; dx++) {
                for (int dz = -radius; dz <= radius && broken < cap; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    Block block = base.getWorld().getBlockAt(base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
                    if (block != null && filter.accept(block.getType())) {
                        block.breakNaturally(tool);
                        broken++;
                    }
                }
            }
        } finally {
            abilityBreaking.remove(player.getUniqueId());
        }
    }

    private void breakOrientedArea(Player player, Block origin, ItemStack tool, int radius, MaterialFilter filter, int cap) {
        if (origin == null || origin.getLocation() == null || origin.getLocation().getWorld() == null) {
            return;
        }
        abilityBreaking.add(player.getUniqueId());
        try {
            int broken = 0;
            Location base = origin.getLocation();
            Plane plane = drillPlane(player);
            for (int a = -radius; a <= radius && broken < cap; a++) {
                for (int b = -radius; b <= radius && broken < cap; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    Block block = switch (plane) {
                        case FLOOR -> base.getWorld().getBlockAt(base.getBlockX() + a, base.getBlockY(), base.getBlockZ() + b);
                        case NORTH_SOUTH -> base.getWorld().getBlockAt(base.getBlockX() + a, base.getBlockY() + b, base.getBlockZ());
                        case EAST_WEST -> base.getWorld().getBlockAt(base.getBlockX(), base.getBlockY() + b, base.getBlockZ() + a);
                    };
                    if (block != null && filter.accept(block.getType())) {
                        block.breakNaturally(tool);
                        broken++;
                    }
                }
            }
        } finally {
            abilityBreaking.remove(player.getUniqueId());
        }
    }

    private Plane drillPlane(Player player) {
        Location location = player.getLocation();
        if (Math.abs(location.getPitch()) > 55.0F) {
            return Plane.FLOOR;
        }
        double yaw = Math.toRadians(location.getYaw());
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return Math.abs(x) > Math.abs(z) ? Plane.EAST_WEST : Plane.NORTH_SOUTH;
    }

    private void breakTimber(Player player, Block origin, Material originType, ItemStack tool) {
        if (origin == null || !isLog(originType)) {
            return;
        }
        abilityBreaking.add(player.getUniqueId());
        try {
            ArrayDeque<Block> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            List<Block> leaves = new ArrayList<>();
            Location treeOrigin = origin.getLocation();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        queue.add(treeOrigin.getWorld().getBlockAt(treeOrigin.getBlockX() + dx, treeOrigin.getBlockY() + dy, treeOrigin.getBlockZ() + dz));
                    }
                }
            }
            int broken = 0;
            while (!queue.isEmpty() && broken < 384) {
                Block block = queue.removeFirst();
                Location location = block.getLocation();
                String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
                if (!seen.add(key)) {
                    continue;
                }
                if (isLog(block.getType())) {
                    if (block != origin) {
                        block.breakNaturally(tool);
                        broken++;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 2; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                queue.add(location.getWorld().getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz));
                            }
                        }
                    }
                    continue;
                }
                if (isLeaves(block.getType())) {
                    int ox = location.getBlockX() - treeOrigin.getBlockX();
                    int oy = location.getBlockY() - treeOrigin.getBlockY();
                    int oz = location.getBlockZ() - treeOrigin.getBlockZ();
                    if (ox * ox + oz * oz > 100 || Math.abs(oy) > 24) {
                        continue;
                    }
                    leaves.add(block);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                Block nearby = location.getWorld().getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz);
                                if (isLeaves(nearby.getType())) {
                                    queue.add(nearby);
                                }
                            }
                        }
                    }
                }
            }
            int leafCap = 512;
            for (Block leaf : leaves) {
                if (leafCap-- <= 0) {
                    break;
                }
                if (leaf != null && isLeaves(leaf.getType())) {
                    leaf.breakNaturally(tool);
                }
            }
        } finally {
            abilityBreaking.remove(player.getUniqueId());
        }
    }

    private void replantArea(Player player, Block center, Material crop, int radius, int cap) {
        if (center == null || crop == null || center.getLocation() == null || center.getLocation().getWorld() == null) {
            return;
        }
        Location base = center.getLocation();
        int planted = 0;
        for (int dx = -radius; dx <= radius && planted < cap; dx++) {
            for (int dz = -radius; dz <= radius && planted < cap; dz++) {
                Block target = base.getWorld().getBlockAt(base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
                if (target.getType() == Material.AIR && consumeSeed(player, crop)) {
                    target.setType(crop, false);
                    planted++;
                }
            }
        }
    }

    private void plantSeedPatch(Player player, Block clicked) {
        if (clicked == null || clicked.getLocation() == null || clicked.getLocation().getWorld() == null) {
            Text.msg(player, "&cRight-click farmland or a crop block with this hoe.");
            return;
        }
        Material crop = firstSeedCrop(player);
        if (crop == null) {
            Text.msg(player, "&cYou need seeds or crops in your inventory.");
            return;
        }
        Location base = clicked.getLocation();
        int planted = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block ground = base.getWorld().getBlockAt(base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
                Block above = base.getWorld().getBlockAt(base.getBlockX() + dx, base.getBlockY() + 1, base.getBlockZ() + dz);
                if (above.getType() == Material.AIR && isPlantableGround(ground.getType()) && consumeSeed(player, crop)) {
                    above.setType(crop, false);
                    planted++;
                }
            }
        }
        if (planted > 0) {
            Text.msg(player, "&aHarvest Lord planted &f" + planted + " &acrop(s).");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.25F, 1.8F);
        } else {
            Text.msg(player, "&cNo valid empty crop spots found.");
        }
    }

    private Material firstSeedCrop(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            Material crop = cropFromSeedItem(item);
            if (crop != null && item.getAmount() > 0) {
                return crop;
            }
        }
        return null;
    }

    private boolean consumeSeed(Player player, Material crop) {
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            Material itemCrop = cropFromSeedItem(item);
            if (itemCrop == crop && item != null && item.getAmount() > 0) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private Material cropFromSeedItem(ItemStack item) {
        if (item == null || item.getType() == null) {
            return null;
        }
        String name = item.getType().name();
        if (name.equals("WHEAT_SEEDS") || name.equals("SEEDS") || name.equals("WHEAT")) {
            return Material.WHEAT;
        }
        if (name.equals("CARROT") || name.equals("CARROTS")) {
            return Material.CARROT;
        }
        if (name.equals("POTATO") || name.equals("POTATOES")) {
            return materialNamed("POTATOES");
        }
        if (name.equals("BEETROOT_SEEDS") || name.equals("BEETROOT") || name.equals("BEETROOTS")) {
            return materialNamed("BEETROOTS");
        }
        return null;
    }

    private boolean isPlantableGround(Material material) {
        if (material == null) {
            return false;
        }
        return material.name().equals("FARMLAND");
    }

    private Material materialNamed(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Ability abilityFor(ItemStack item) {
        if (recoveryPreviewExpired(item)) {
            return null;
        }
        Ability ability = baseAbilityFor(item);
        if (ability == null || !hasAwakenedAbility(item, ability)) {
            return null;
        }
        return ability;
    }

    @Override
    public List<ItemStack> createRecoveryAbilitySamples(UUID playerId) {
        long expiry = System.currentTimeMillis() + 30L * 60L * 1000L;
        return List.of(
            recoveryAbilityItem(Material.STONE_PICKAXE, Ability.GODS_DRILL, "&7Recovery Drill Trial", expiry),
            recoveryAbilityItem(Material.SHIELD, Ability.AEGIS_GUARD, "&7Recovery Aegis Trial", expiry)
        );
    }

    @Override
    public ItemStack applyUnlockedAbility(String abilityKey, ItemStack item, String displayName) {
        Ability ability = Ability.from(abilityKey);
        if (item == null || ability == null || baseAbilityFor(item) != ability) {
            return item;
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null && displayName != null && !displayName.isBlank()) {
            meta.setDisplayName(Text.color(displayName));
            result.setItemMeta(meta);
        }
        writeLore(result, ability, new AbilityState(required(ability), true, true));
        return result;
    }

    private ItemStack recoveryAbilityItem(Material material, Ability ability, String name, long expiry) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.getPersistentDataContainer().set(recoveryPreviewExpiryKey, PersistentDataType.STRING, String.valueOf(expiry));
            item.setItemMeta(meta);
        }
        writeLore(item, ability, new AbilityState(required(ability), true, true));
        meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add(Text.color("&cTrial expires in 30 minutes."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean recoveryPreviewExpired(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return false;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(recoveryPreviewExpiryKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return System.currentTimeMillis() >= Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private Ability baseAbilityFor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        String name = item.getType().name();
        if (name.endsWith("_PICKAXE")) {
            return Ability.GODS_DRILL;
        }
        if (name.endsWith("_AXE")) {
            return Ability.ANCIENT_TIMBER;
        }
        if (name.endsWith("_SHOVEL")) {
            return Ability.EARTHSHAPER;
        }
        if (name.endsWith("_HOE")) {
            return Ability.HARVEST_LORD;
        }
        if (name.endsWith("_SWORD")) {
            return Ability.BLOOD_FORGED_EDGE;
        }
        if (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW) {
            return Ability.ECHO_QUIVER;
        }
        if (item.getType() == Material.TRIDENT) {
            return Ability.STORM_BIND;
        }
        if (item.getType() == Material.SHIELD) {
            return Ability.AEGIS_GUARD;
        }
        return null;
    }

    private Ability randomBookAbility() {
        Ability[] abilities = Ability.values();
        return abilities[random.nextInt(abilities.length)];
    }

    private AbilityState state(ItemStack item, Ability ability) {
        if (item == null || ability == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return new AbilityState(0, false, true);
        }
        ItemMeta meta = item.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(stateKey(ability), PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            String[] parts = stored.split(":");
            if (parts.length >= 3) {
                return new AbilityState(parseInt(parts[0], 0, 0, Integer.MAX_VALUE), Boolean.parseBoolean(parts[1]), Boolean.parseBoolean(parts[2]));
            }
        }
        if (meta.getLore() == null) {
            return new AbilityState(0, false, true);
        }
        AbilityState latest = null;
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line == null ? "" : line);
            if (stripped == null || !stripped.startsWith(STATE_PREFIX + ability.id() + ":")) {
                continue;
            }
            String raw = stripped.substring(1, stripped.length() - 1);
            String[] parts = raw.split(":");
            if (parts.length >= 4) {
                latest = new AbilityState(parseInt(parts[2], 0, 0, Integer.MAX_VALUE), Boolean.parseBoolean(parts[3]), parts.length < 5 || Boolean.parseBoolean(parts[4]));
            }
        }
        return latest == null ? new AbilityState(0, false, true) : latest;
    }

    private void writeLore(ItemStack item, Ability ability, AbilityState state) {
        if (item == null || ability == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(awakenKey(ability), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(stateKey(ability), PersistentDataType.STRING, state.progress() + ":" + state.unlocked() + ":" + state.enabled());
        List<String> lore = new ArrayList<>();
        if (meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (isAbilityLoreLine(line)) {
                    continue;
                }
                lore.add(line);
            }
        }
        int required = required(ability);
        lore.add(Text.rawColor("&dBloodboundSMP Enchant: &f" + ability.display()));
        lore.add(Text.color("&7Challenge: &f" + ability.challenge() + " &e" + Math.min(state.progress(), required) + "/" + required));
        lore.add(Text.color("&7Status: " + (state.unlocked() ? "&aUnlocked" : "&cLocked") + " &8| &7Toggle: " + (state.enabled() ? "&aON" : "&cOFF")));
        if (ability.pvpRelated()) {
            lore.add(Text.color("&7Cooldown: &f" + formatSeconds(cooldownSeconds(ability)) + " seconds"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void grantAbilityEnchant(Player player, ItemStack item, Ability ability, String message) {
        if (item == null || ability == null || hasAwakenedAbility(item, ability)) {
            return;
        }
        writeLore(item, ability, new AbilityState(0, false, true));
        if (player != null) {
            if (message != null && !message.isBlank()) {
                Text.msg(player, message);
            }
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.2F);
        }
    }

    private boolean isRareLootHost(ItemStack item) {
        if (item == null || item.getType() == null) {
            return false;
        }
        String name = item.getType().name();
        return name.startsWith("DIAMOND_")
            || name.startsWith("NETHERITE_")
            || item.getType() == Material.BOW
            || item.getType() == Material.CROSSBOW
            || item.getType() == Material.TRIDENT
            || item.getType() == Material.SHIELD;
    }

    private int cleanAbilityLore(Player player) {
        if (player == null || player.getInventory() == null) {
            return 0;
        }
        int cleaned = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            cleaned += cleanAbilityLore(item);
        }
        player.getInventory().setContents(contents);
        cleaned += cleanAbilityLore(player.getInventory().getItemInMainHand());
        cleaned += cleanAbilityLore(player.getInventory().getItemInOffHand());
        cleaned += cleanAbilityLore(player.getInventory().getHelmet());
        cleaned += cleanAbilityLore(player.getInventory().getChestplate());
        cleaned += cleanAbilityLore(player.getInventory().getLeggings());
        cleaned += cleanAbilityLore(player.getInventory().getBoots());
        return cleaned;
    }

    private int cleanAbilityLore(ItemStack item) {
        Ability ability = baseAbilityFor(item);
        if (ability == null) {
            return 0;
        }
        AbilityState before = state(item, ability);
        if (hasAwakenedAbility(item, ability)) {
            writeLore(item, ability, before);
        } else {
            stripAbilityLore(item);
        }
        return 1;
    }

    private boolean isAbilityLoreLine(String line) {
        String stripped = ChatColor.stripColor(line == null ? "" : line);
        if (stripped == null) {
            return false;
        }
        return stripped.startsWith(STATE_PREFIX)
            || stripped.contains(LORE_MARKER)
            || stripped.startsWith("Awakened Ability:")
            || stripped.startsWith("MitchSMP Enchant:")
            || stripped.startsWith("BloodboundSMP Enchant:")
            || stripped.startsWith("Bloodbound Enchant:")
            || stripped.startsWith("Challenge:")
            || stripped.startsWith("Status:")
            || stripped.startsWith("Cooldown:")
            || stripped.startsWith("Ability blijft op dit item")
            || stripped.startsWith("Tool-bound forever")
            || stripped.startsWith("No cooldown. Tool-bound");
    }

    private void stripAbilityLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return;
        }
        List<String> lore = new ArrayList<>();
        for (String line : meta.getLore()) {
            if (!isAbilityLoreLine(line)) {
                lore.add(line);
            }
        }
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    private boolean hasAwakenedAbility(ItemStack item, Ability ability) {
        if (item == null || ability == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(awakenKey(ability), PersistentDataType.BYTE)) {
            return true;
        }
        return meta.getLore() != null && meta.getLore().stream()
            .map(line -> ChatColor.stripColor(line == null ? "" : line))
            .anyMatch(line -> line != null
                && (line.equalsIgnoreCase("MitchSMP Enchant: " + ability.display())
                || line.equalsIgnoreCase("BloodboundSMP Enchant: " + ability.display())
                || line.equalsIgnoreCase("Bloodbound Enchant: " + ability.display())
                || line.equalsIgnoreCase("Awakened Ability: " + ability.display())));
    }

    private NamespacedKey awakenKey(Ability ability) {
        return new NamespacedKey(this, "awakened_" + ability.id());
    }

    private NamespacedKey stateKey(Ability ability) {
        return new NamespacedKey(this, "ability_state_" + ability.id());
    }

    private void toggleHeldAbility(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Ability ability = abilityFor(hand);
        if (ability == null) {
            Text.msg(player, "&cNo Bloodbound ability item in your hand.");
            return;
        }
        toggleAbility(player, hand, ability);
    }

    private void toggleAbility(Player player, ItemStack item, Ability ability) {
        long now = System.currentTimeMillis();
        long last = abilityToggleCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250L) {
            return;
        }
        abilityToggleCooldowns.put(player.getUniqueId(), now);
        AbilityState state = state(item, ability);
        AbilityState toggled = new AbilityState(state.progress(), state.unlocked(), !state.enabled());
        writeLore(item, ability, toggled);
        Text.msg(player, toggled.enabled() ? "&aAbility enabled." : "&cAbility disabled.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.28F, toggled.enabled() ? 1.45F : 0.85F);
    }

    private boolean activateHeldAbility(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        Ability ability = abilityFor(item);
        if (ability == null) {
            item = player.getInventory().getItemInOffHand();
            ability = abilityFor(item);
        }
        if (ability != Ability.AEGIS_GUARD) {
            Text.msg(player, "&cHold an unlocked Aegis Guard item to activate it.");
            return true;
        }
        activateAegis(player, item);
        return true;
    }

    private void activateAegis(Player player, ItemStack shield) {
        AbilityState state = state(shield, Ability.AEGIS_GUARD);
        if (!state.unlocked() || !state.enabled() || !abilityGloballyEnabled(Ability.AEGIS_GUARD)) {
            player.sendActionBar(Text.color("&5Aegis Guard &cLOCKED OR DISABLED"));
            return;
        }
        long now = System.currentTimeMillis();
        long activeUntil = data.getLong(aegisActiveKey(player.getUniqueId()), 0L);
        if (activeUntil > now) {
            player.sendActionBar(Text.color("&5Aegis Guard &6ACTIVE &7" + Math.max(1L, (activeUntil - now + 999L) / 1000L) + "s"));
            return;
        }
        long remaining = cooldownRemainingMillis(player.getUniqueId(), Ability.AEGIS_GUARD);
        if (remaining > 0L) {
            player.sendActionBar(Text.color("&5Aegis Guard &cCOOLDOWN &7" + Math.max(1L, (remaining + 999L) / 1000L) + "s"));
            return;
        }
        long duration = Math.max(500L, Math.round(data.getDouble("ability.aegis_guard.active_duration_seconds", 12.0D) * 1000.0D));
        long until = now + duration;
        data.set(aegisActiveKey(player.getUniqueId()), until);
        data.set(cooldownKey(player.getUniqueId(), Ability.AEGIS_GUARD), until + Math.round(cooldownSeconds(Ability.AEGIS_GUARD) * 1000.0D));
        saveSkillDataSoon();
        abilityActivationEffects(player, Ability.AEGIS_GUARD);
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isSneaking(Player player) {
        if (player == null) {
            return false;
        }
        for (String methodName : List.of("isSneaking", "isShiftKeyDown")) {
            try {
                Object value = player.getClass().getMethod(methodName).invoke(player);
                if (value instanceof Boolean result) {
                    return result;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
    }

    private int challengeGain(Player player, Ability ability, Material block, Entity entity) {
        return switch (ability) {
            case GODS_DRILL -> isMiningBlock(block) ? abilityChallengeGain(player, 1) : 0;
            case ANCIENT_TIMBER -> isLog(block) ? abilityChallengeGain(player, 1) : 0;
            case EARTHSHAPER -> isShovelBlock(block) ? abilityChallengeGain(player, 1) : 0;
            case HARVEST_LORD -> isFarmBlock(block) ? abilityChallengeGain(player, 1) : 0;
            default -> 0;
        };
    }

    private int abilityChallengeGain(Player player, int base) {
        return Math.max(1, base + perk(player, Perk.ENCHANTING_MASTERY) / 5 + perk(player, Perk.ANVIL_CARE) / 5);
    }

    private int required(Ability ability) {
        return data.getInt("ability." + ability.id() + ".required", ability.defaultRequired());
    }

    private boolean abilityGloballyEnabled(Ability ability) {
        return Boolean.parseBoolean(data.getString("ability." + ability.id() + ".enabled", "true"));
    }

    private double cooldownSeconds(Ability ability) {
        double fallback = switch (ability) {
            case BLOOD_FORGED_EDGE -> 2.0D;
            case ECHO_QUIVER -> 10.0D;
            case STORM_BIND -> 15.0D;
            case AEGIS_GUARD -> Math.max(1.0D, data.getLong("ability.aegis_guard.cooldown_minutes", 30L) * 60.0D);
            default -> 0.0D;
        };
        return Math.max(0.0D, data.getDouble("ability." + ability.id() + ".cooldown_seconds", fallback));
    }

    private long cooldownRemainingMillis(UUID playerId, Ability ability) {
        if (playerId == null || ability == null || !ability.pvpRelated()) {
            return 0L;
        }
        long until = data.getLong(cooldownKey(playerId, ability), 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private boolean activateAbility(Player player, Ability ability) {
        if (player == null || ability == null || cooldownRemainingMillis(player.getUniqueId(), ability) > 0L) {
            return false;
        }
        long duration = Math.round(cooldownSeconds(ability) * 1000.0D);
        if (duration > 0L) {
            data.set(cooldownKey(player.getUniqueId(), ability), System.currentTimeMillis() + duration);
            saveSkillDataSoon();
        }
        abilityActivationEffects(player, ability);
        return true;
    }

    private boolean bloodforgedActive(Player player, Ability ability) {
        long now = System.currentTimeMillis();
        String activeKey = "ability." + ability.id() + ".active_until." + profileKey(player.getUniqueId());
        if (data.getLong(activeKey, 0L) > now) {
            return true;
        }
        if (!activateAbility(player, ability)) {
            return false;
        }
        double seconds = Math.max(0.5D, data.getDouble("ability.blood_forged_edge.active_duration_seconds", 8.0D));
        data.set(activeKey, now + Math.round(seconds * 1000.0D));
        saveSkillDataSoon();
        return true;
    }

    private void abilityActivationEffects(Player player, Ability ability) {
        if (player == null) {
            return;
        }
        Location location = player.getLocation().add(0.0D, 1.0D, 0.0D);
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, location, 18, 0.45D, 0.65D, 0.45D, 0.04D);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, location, 10, 0.35D, 0.55D, 0.35D, 0.02D);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.25F, 1.55F);
        player.sendActionBar(Text.color("&4" + ability.display() + " &6activated"));
    }

    private String cooldownKey(UUID playerId, Ability ability) {
        return "ability." + ability.id() + ".cooldown_until." + profileKey(playerId);
    }

    private String formatSeconds(double seconds) {
        return seconds == Math.rint(seconds) ? String.valueOf((long) seconds) : String.format(Locale.US, "%.1f", seconds);
    }

    private ItemStack combatItem(Player attacker, Entity damager) {
        if (damager instanceof Projectile projectile) {
            ItemStack projectileItem = projectileItem(projectile);
            if (abilityFor(projectileItem) != null) {
                return projectileItem;
            }
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (hand != null && (hand.getType() == Material.BOW || hand.getType() == Material.CROSSBOW || hand.getType() == Material.TRIDENT)) {
                return hand;
            }
        }
        return attacker.getInventory().getItemInMainHand();
    }

    private ItemStack projectileItem(Projectile projectile) {
        if (projectile == null) {
            return null;
        }
        for (String methodName : List.of("getItemStack", "getWeapon")) {
            try {
                Object value = projectile.getClass().getMethod(methodName).invoke(projectile);
                if (value instanceof ItemStack item && item.getType() != Material.AIR) {
                    return item;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean restricted(Player player) {
        if (player == null) {
            return true;
        }
        String world = player.getWorld() == null ? "" : player.getWorld().getName().toLowerCase(Locale.ROOT);
        if (MitchSMP.permissions().isAdminMode(player)) {
            return !MitchSMP.permissions().isAdminModeOverride(player) && !world.startsWith("mitchtest_");
        }
        return world.contains("bedwars") || world.contains("tntrun") || world.contains("spleef") || world.startsWith("mitchtest_");
    }

    private boolean blockedAbilityWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return true;
        }
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return world.contains("bedwars") || world.contains("tntrun") || world.contains("spleef")
            || world.startsWith("mitchtest_") && !MitchSMP.permissions().isAdminMode(player);
    }

    private int effectiveEnchantLevel(int rawCost) {
        int cost = Math.max(1, rawCost);
        return cost <= 3 ? cost * 10 : cost;
    }

    private boolean isMiningBlock(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if (name.contains("CHEST") || name.contains("SHULKER") || name.contains("BARREL")
            || name.contains("SPAWNER") || name.contains("BED") || name.contains("DOOR")
            || name.contains("SIGN") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE")) {
            return false;
        }
        return name.contains("ORE")
            || name.contains("STONE")
            || name.contains("DEEPSLATE")
            || name.contains("BLACKSTONE")
            || name.contains("BASALT")
            || name.contains("TUFF")
            || name.contains("CALCITE")
            || name.contains("DRIPSTONE")
            || name.contains("TERRACOTTA")
            || name.contains("CONCRETE")
            || name.contains("PRISMARINE")
            || name.contains("NETHERRACK")
            || name.contains("END_STONE")
            || name.contains("OBSIDIAN")
            || name.contains("BRICKS")
            || name.contains("COPPER")
            || material == Material.ANCIENT_DEBRIS;
    }

    private boolean isDrillBlock(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return isMiningBlock(material)
            || name.equals("GRANITE")
            || name.equals("DIORITE")
            || name.equals("ANDESITE")
            || name.equals("COBBLESTONE")
            || name.equals("COBBLED_DEEPSLATE")
            || name.equals("POLISHED_DEEPSLATE")
            || name.equals("SMOOTH_BASALT")
            || name.equals("DRIPSTONE_BLOCK")
            || name.equals("MAGMA_BLOCK")
            || name.equals("END_STONE");
    }

    private boolean isShovelBlock(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.contains("DIRT") || name.contains("SAND") || name.contains("GRAVEL");
    }

    private boolean isFarmBlock(Material material) {
        return isCropLike(material);
    }

    private boolean isCropLike(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return material == Material.WHEAT
            || material == Material.CARROT
            || name.contains("CARROT")
            || name.contains("POTATO")
            || name.contains("BEETROOT")
            || name.contains("NETHER_WART")
            || name.contains("COCOA")
            || name.contains("SUGAR_CANE")
            || name.contains("CACTUS")
            || name.contains("BAMBOO")
            || name.contains("MELON")
            || name.contains("PUMPKIN");
    }

    private boolean isLog(Material material) {
        return material != null && (material.name().contains("LOG") || material.name().endsWith("_WOOD"));
    }

    private boolean isLeaves(Material material) {
        return material != null && (material.name().contains("LEAVES") || material.name().contains("WART_BLOCK"));
    }

    private void lightningStrike(Location location, boolean damage) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        try {
            Method method = location.getWorld().getClass().getMethod(damage ? "strikeLightning" : "strikeLightningEffect", Location.class);
            method.invoke(location.getWorld(), location);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            location.getWorld().spawnParticle(org.bukkit.Particle.CRIT, location, 24, 0.35D, 0.85D, 0.35D, 0.04D);
            location.getWorld().createExplosion(location, 0.0F, false, false);
        }
    }

    private boolean hasKnownEnchant(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.containsEnchantment(Enchantment.EFFICIENCY)
            || item.containsEnchantment(Enchantment.FORTUNE)
            || item.containsEnchantment(Enchantment.SILK_TOUCH)
            || item.containsEnchantment(Enchantment.SHARPNESS)
            || item.containsEnchantment(Enchantment.PROTECTION)
            || item.containsEnchantment(Enchantment.UNBREAKING)
            || item.containsEnchantment(Enchantment.MENDING)
            || item.containsEnchantment(Enchantment.LOOTING)
            || item.containsEnchantment(Enchantment.POWER)
            || item.containsEnchantment(Enchantment.INFINITY)
            || item.containsEnchantment(Enchantment.FLAME);
    }

    private void heal(Player player, double amount) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 20.0D : attribute.getBaseValue();
        player.setHealth(Math.min(max, player.getHealth() + amount));
    }

    private boolean lowHealth(Player player) {
        if (player == null) {
            return false;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 20.0D : Math.max(1.0D, attribute.getBaseValue());
        return player.getHealth() <= max * 0.33D;
    }

    private void multiplyDamage(EntityDamageByEntityEvent event, double multiplier) {
        try {
            Method getDamage = event.getClass().getMethod("getDamage");
            Method setDamage = event.getClass().getMethod("setDamage", double.class);
            Object raw = getDamage.invoke(event);
            if (raw instanceof Number number) {
                setDamage.invoke(event, Math.max(0.0D, number.doubleValue() * multiplier));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void handleAegisBlock(Player defender, EntityDamageEvent event) {
        ItemStack shield = aegisShield(defender);
        Ability ability = abilityFor(shield);
        if (ability != Ability.AEGIS_GUARD) {
            return;
        }
        AbilityState state = state(shield, ability);
        if (!state.unlocked() && event instanceof EntityDamageByEntityEvent && isPlayerBlocking(defender)) {
            state = progress(defender, shield, ability, state, abilityChallengeGain(defender, 1));
        }
        if (state.unlocked() && state.enabled() && abilityGloballyEnabled(ability)) {
            long now = System.currentTimeMillis();
            long activeUntil = data.getLong(aegisActiveKey(defender.getUniqueId()), 0L);
            if (activeUntil <= now) {
                return;
            }
            event.setCancelled(true);
            long seconds = Math.max(1L, (activeUntil - now + 999L) / 1000L);
            defender.sendActionBar(Text.color("&5Aegis Guard &bACTIVE &8| &f100% absorbed &8| &7" + seconds + "s"));
            defender.setNoDamageTicks(10);
            defender.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, defender.getLocation(), 8, 0.35D, 0.45D, 0.35D, 0.02D);
        }
    }

    private String aegisActiveKey(UUID playerId) {
        return "ability.aegis_guard.active_until." + profileKey(playerId);
    }

    private boolean isPlayerBlocking(Player player) {
        try {
            Object value = player.getClass().getMethod("isBlocking").invoke(player);
            return value instanceof Boolean blocking && blocking;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private ItemStack aegisShield(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (abilityFor(main) == Ability.AEGIS_GUARD) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (abilityFor(off) == Ability.AEGIS_GUARD) {
            return off;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (abilityFor(item) == Ability.AEGIS_GUARD) {
                return item;
            }
        }
        return null;
    }

    private double damageAmount(EntityDamageByEntityEvent event) {
        try {
            Object raw = event.getClass().getMethod("getDamage").invoke(event);
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return 1.0D;
    }

    private boolean rootUnlocked(Player player) {
        return Boolean.parseBoolean(data.getString("tree." + profileKey(player.getUniqueId()) + ".root", "false"));
    }

    private MainBranch chosenMain(Player player) {
        return MainBranch.from(data.getString("tree." + profileKey(player.getUniqueId()) + ".main", ""));
    }

    private Specialization chosenSpecialization(Player player) {
        return Specialization.from(data.getString("tree." + profileKey(player.getUniqueId()) + ".specialization", ""));
    }

    private String chosenMainDisplay(Player player) {
        MainBranch branch = chosenMain(player);
        return branch == null ? "Not chosen" : branch.display();
    }

    private String chosenSpecializationDisplay(Player player) {
        Specialization specialization = chosenSpecialization(player);
        return specialization == null ? "Not chosen" : specialization.display();
    }

    private void chooseMainBranch(Player player, MainBranch branch) {
        if (!rootUnlocked(player)) {
            Text.msg(player, "&cUnlock Worker's Instinct first.");
            return;
        }
        MainBranch chosen = chosenMain(player);
        if (chosen != null && chosen != branch) {
            Text.msg(player, "&cYou already chose &f" + chosen.display() + "&c.");
            return;
        }
        if (chosen == null) {
            data.set("tree." + profileKey(player.getUniqueId()) + ".main", branch.name());
            saveSkillDataSoon();
            Text.msg(player, "&aDirection chosen: &f" + branch.display() + "&a. Preview and choose a specialization next.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.2F);
        }
    }

    private boolean canChooseSpecialization(Player player, Specialization specialization) {
        if (!rootUnlocked(player)) {
            return false;
        }
        MainBranch chosenMain = chosenMain(player);
        Specialization chosenSpec = chosenSpecialization(player);
        return chosenSpec == null && (chosenMain == null || chosenMain == specialization.main());
    }

    private void confirmSpecialization(Player player, Specialization specialization) {
        if (!canChooseSpecialization(player, specialization)) {
            Text.msg(player, "&cYou cannot choose this specialization right now.");
            return;
        }
        data.set("tree." + profileKey(player.getUniqueId()) + ".main", specialization.main().name());
        data.set("tree." + profileKey(player.getUniqueId()) + ".specialization", specialization.name());
        data.set("tree." + profileKey(player.getUniqueId()) + ".page." + specialization.name(), true);
        saveSkillDataSoon();
        Text.msg(player, "&aSpecialization chosen: &f" + specialization.display() + "&a.");
        Text.msg(player, "&7Other branches remain visible as preview-only.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.35F);
    }

    private String specializationStatusLabel(Player player, Specialization specialization) {
        Specialization chosen = chosenSpecialization(player);
        if (chosen == specialization) {
            return "&aActive";
        }
        if (chosen != null) {
            return "&8Preview-only &7(chosen: &f" + chosen.display() + "&7)";
        }
        if (!rootUnlocked(player)) {
            return "&cLocked &7(root required)";
        }
        MainBranch main = chosenMain(player);
        if (main != null && main != specialization.main()) {
            return "&8Preview-only &7(direction: &f" + main.display() + "&7)";
        }
        return "&eAvailable to choose";
    }

    private MainBranch mainBranchSlot(int slot) {
        return switch (slot) {
            case 20 -> MainBranch.FRONTIER;
            case 24 -> MainBranch.WARPATH;
            default -> null;
        };
    }

    private Specialization specializationSlot(int slot) {
        for (Specialization specialization : Specialization.values()) {
            if (specialization.slot() == slot) {
                return specialization;
            }
        }
        return null;
    }

    private Specialization specializationViewSlot(int slot) {
        for (Specialization specialization : Specialization.values()) {
            if (specialization.viewSlot() == slot) {
                return specialization;
            }
        }
        return null;
    }

    private String[] specializationKeys() {
        return java.util.Arrays.stream(Specialization.values()).map(Specialization::key).toArray(String[]::new);
    }

    private Specialization specializationOf(Perk perk) {
        for (Specialization specialization : Specialization.values()) {
            if (specialization.perks().contains(perk)) {
                return specialization;
            }
        }
        return null;
    }

    private Perk specializationPerkAt(Specialization specialization, int slot) {
        for (Perk perk : specialization.perks()) {
            if (perkSlotConfig(perk) == slot) {
                return perk;
            }
        }
        return null;
    }

    private ItemStack mainBranchIcon(Player player, MainBranch branch) {
        MainBranch chosen = chosenMain(player);
        boolean locked = !rootUnlocked(player);
        boolean other = chosen != null && chosen != branch;
        String color = locked || other ? "&8" : chosen == branch ? "&a" : "&e";
        return icon(branch.icon(), color + branch.display(), List.of(
            "&7Theme: &f" + branch.theme(),
            "&7Systems: &f" + branch.systems(),
            "&7Status: " + (locked ? "&cLocked: unlock Worker's Instinct" : other ? "&8Preview-only" : chosen == branch ? "&aChosen" : "&eAvailable"),
            "&8",
            "&7Choose this before committing",
            "&7to a specialization."
        ));
    }

    private ItemStack specializationIcon(Player player, Specialization specialization) {
        return icon(specialization.icon(), specialization.color() + specialization.display(), List.of(
            "&7Main branch: &f" + specialization.main().display(),
            "&7Identity: " + specialization.identity(),
            "&7Systems: &f" + specialization.systems(),
            "&7Status: " + specializationStatusLabel(player, specialization),
            "&8",
            "&eClick to preview this path."
        ));
    }

    private ItemStack treeNodeIcon(Player player, Perk perk, String branch, List<String> extraLore) {
        int current = perk == Perk.WORKERS_INSTINCT ? (rootUnlocked(player) ? 1 : 0) : perk(player, perk);
        int max = perk == Perk.WORKERS_INSTINCT ? 1 : perkMax(perk);
        NodeState state = treeNodeState(player, perk);
        String color = switch (state) {
            case MAXED -> "&a";
            case UNLOCKED -> "&2";
            case AVAILABLE -> "&e";
            case PREVIEW -> "&8";
            case LOCKED -> "&c";
        };
        Specialization specialization = specializationOf(perk);
        List<String> lore = new ArrayList<>(List.of(
            "&7Branch: &f" + branch,
            "&7Page: &f" + (specialization == null ? "FOUNDATION" : specialization.name() + "_1"),
            "&7Status: " + state.label(),
            "&7Current: &f" + current + "/" + max,
            "&7Cost: &f" + (current >= max ? "0" : "1") + " skillpoint",
            "&7XP track: &f" + perk.category().display(),
            "&7Systems: &f" + linkedSystems(perk)
        ));
        lore.addAll(extraLore);
        lore.addAll(requirementLore(player, perk));
        lore.add("&8");
        lore.addAll(perkDescriptionLines(perk));
        if (state == NodeState.PREVIEW) {
            lore.add("&8Preview Mode: you can view this,");
            lore.add("&8but cannot unlock skills here.");
        } else if (state == NodeState.AVAILABLE) {
            lore.add("&eClick to unlock or upgrade.");
        } else if (state == NodeState.LOCKED) {
            lore.add("&cLocked. Check requirements above.");
        }
        return icon(perkIcon(perk), color + perkDisplay(perk) + " &f" + current + "/" + max, lore);
    }

    private NodeState treeNodeState(Player player, Perk perk) {
        if (perk == Perk.WORKERS_INSTINCT) {
            if (rootUnlocked(player)) {
                return NodeState.MAXED;
            }
            return points(player) > 0 ? NodeState.AVAILABLE : NodeState.LOCKED;
        }
        Specialization specialization = specializationOf(perk);
        if (specialization != null && chosenSpecialization(player) != specialization) {
            return NodeState.PREVIEW;
        }
        return nodeState(player, perk);
    }

    private void fillSpecializationFrame(Inventory inventory) {
        ItemStack filler = icon(Material.GLASS_PANE, "&8", List.of());
        for (int slot : new int[] {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52}) {
            inventory.setItem(slot, filler);
        }
    }

    private void drawFoundationConnectors(Inventory inventory) {
        ItemStack link = icon(Material.GREEN_STAINED_GLASS_PANE, "&bPath", List.of("&7Foundation connection."));
        for (int slot : new int[] {13, 21, 22, 23, 30, 31, 32}) {
            inventory.setItem(slot, link);
        }
    }

    private void drawSpecializationConnectors(Inventory inventory) {
        ItemStack link = icon(Material.GREEN_STAINED_GLASS_PANE, "&bPath", List.of("&7Specialization connection."));
        for (int slot : new int[] {13, 22, 31, 40}) {
            inventory.setItem(slot, link);
        }
    }

    private void openBranchOverview(Player player, Specialization specialization) {
        player.sendActionBar(Text.color(specialization.color() + specialization.display() + " &8| &7" + specialization.identity()));
    }

    private ItemStack categoryIcon(Player player, Category category) {
        int level = level(player, category);
        int totalXp = xp(player, category);
        return icon(category.icon(), "&a" + category.display(), List.of(
            "&7Level: &f" + level + "&7/100",
            level >= MAX_LEVEL ? "&7XP: &aMAX" : "&7XP: &f" + xpIntoLevel(totalXp) + "&7/&f" + nextXp(level),
            "&7Total XP: &f" + totalXp,
            "&7Perks: &f" + perksFor(category).size(),
            "&eClick for category details."
        ));
    }

    private ItemStack bloodboundLaneIcon(Player player, BloodboundLane lane) {
        int unlocked = 0;
        int maxed = 0;
        int available = 0;
        for (Perk perk : perksFor(lane)) {
            NodeState state = nodeState(player, perk);
            if (state == NodeState.UNLOCKED || state == NodeState.MAXED) {
                unlocked++;
            }
            if (state == NodeState.MAXED) {
                maxed++;
            }
            if (state == NodeState.AVAILABLE) {
                available++;
            }
        }
        return icon(lane.icon(), lane.color() + lane.display(), List.of(
            "&7" + lane.shortGoal(),
            "&8",
            "&7Nodes unlocked: &f" + unlocked + "&7/" + perksFor(lane).size(),
            "&7Nodes maxed: &f" + maxed,
            "&7Available upgrades: &f" + available,
            "&7Skillpoints: &f" + points(player),
            "&8",
            "&7Systems: &f" + lane.systems(),
            "&eClick to open this path."
        ));
    }

    private ItemStack perkIcon(Player player, Perk perk) {
        int current = perk(player, perk);
        boolean locked = level(player, perk.category()) < perkRequiredLevel(perk) || !requirementsMet(player, perk);
        SkillLane lane = lane(perk);
        SkillLane chosen = chosenLane(player, perk.category());
        boolean laneLocked = lane.exclusive() && chosen != null && chosen != lane && current <= 0;
        boolean maxed = current >= perkMax(perk);
        NodeState state = nodeState(player, perk);
        String color = switch (state) {
            case MAXED -> "&a";
            case UNLOCKED -> "&2";
            case AVAILABLE -> "&e";
            case PREVIEW -> "&8";
            case LOCKED -> "&c";
        };
        List<String> lore = new ArrayList<>(List.of(
            "&7Lane: &f" + bloodboundLane(perk).display(),
            "&7XP track: &f" + perk.category().display(),
            "&7Choice path: &f" + lane.display() + (lane.exclusive() ? " &8(exclusive)" : ""),
            "&7Status: " + state.label(),
            "&7Current: &f" + current + "/" + perkMax(perk),
            "&7Cost: &f1 skillpoint",
            "&7Required XP level: &f" + perkRequiredLevel(perk),
            "&7Gameplay: &f" + linkedSystems(perk)
        ));
        if (chosen != null && lane.exclusive()) {
            lore.add("&7Chosen path: &f" + chosen.display());
        }
        lore.addAll(requirementLore(player, perk));
        lore.add("&8");
        lore.addAll(perkDescriptionLines(perk));
        if (maxed) {
            lore.add("&aMaxed.");
        } else if (laneLocked) {
            lore.add("&cLocked by your " + chosen.display() + " path.");
        } else if (locked) {
            lore.add("&cLocked. Check requirements above.");
        } else if (points(player) <= 0) {
            lore.add("&cNo skillpoints available.");
        } else {
            lore.add(lane.exclusive() && chosen == null ? "&eClick to choose this path." : "&eClick to buy.");
        }
        return icon(perkIcon(perk), color + perkDisplay(perk) + " &f" + current + "/" + perkMax(perk), lore);
    }

    private NodeState nodeState(Player player, Perk perk) {
        int current = perk(player, perk);
        if (current >= perkMax(perk)) {
            return NodeState.MAXED;
        }
        if (current > 0) {
            return NodeState.UNLOCKED;
        }
        SkillLane lane = lane(perk);
        SkillLane chosen = chosenLane(player, perk.category());
        boolean laneLocked = lane.exclusive() && chosen != null && chosen != lane;
        if (laneLocked || level(player, perk.category()) < perkRequiredLevel(perk) || !requirementsMet(player, perk) || points(player) <= 0) {
            return NodeState.LOCKED;
        }
        return NodeState.AVAILABLE;
    }

    private boolean requirementsMet(Player player, Perk perk) {
        for (Perk required : requiredPerks(perk)) {
            if (perk(player, required) <= 0) {
                return false;
            }
        }
        return true;
    }

    private List<String> requirementLore(Player player, Perk perk) {
        List<String> lore = new ArrayList<>();
        List<Perk> required = requiredPerks(perk);
        if (required.isEmpty()) {
            lore.add("&7Requires: &aNo previous node");
            return lore;
        }
        for (Perk req : required) {
            lore.add((perk(player, req) > 0 ? "&aRequires: " : "&cRequires: ") + perkDisplay(req) + " 1/" + perkMax(req));
        }
        return lore;
    }

    private List<Perk> requiredPerks(Perk perk) {
        String configured = skillConfig.getString("node." + perk.key() + ".requires", "");
        if (!configured.isBlank()) {
            List<Perk> parsed = new ArrayList<>();
            for (String part : configured.split(",")) {
                Perk required = Perk.from(part.trim());
                if (required != null && required != perk) {
                    parsed.add(required);
                }
            }
            return parsed;
        }
        return switch (perk) {
            case ORE_INSTINCT -> List.of(Perk.STONE_SENSE);
            case DEEP_DELVER -> List.of(Perk.ORE_INSTINCT);
            case CONTRACT_MINER -> List.of(Perk.DEEP_DELVER);
            case SHARD_PROSPECTOR -> List.of(Perk.DEEP_DELVER);
            case BLOODBOUND_PICK -> List.of(Perk.CONTRACT_MINER, Perk.SHARD_PROSPECTOR);
            case RANCH_HAND -> List.of(Perk.GREEN_THUMB);
            case FIELD_SENSE -> List.of(Perk.RANCH_HAND);
            case STOCKPILE_DISCIPLINE -> List.of(Perk.FIELD_SENSE);
            case CRIMSON_GROWTH -> List.of(Perk.FIELD_SENSE);
            case BLOODBOUND_HOE -> List.of(Perk.STOCKPILE_DISCIPLINE, Perk.CRIMSON_GROWTH);
            case TEMPERED_TOOLS -> List.of(Perk.ARCANE_FAMILIARITY);
            case ENCHANTERS_FOCUS -> List.of(Perk.TEMPERED_TOOLS);
            case ARCANE_WORKSTATION -> List.of(Perk.ENCHANTERS_FOCUS);
            case RUNE_EFFICIENCY -> List.of(Perk.ENCHANTERS_FOCUS);
            case BLOODBOUND_INFUSION -> List.of(Perk.ARCANE_WORKSTATION, Perk.RUNE_EFFICIENCY);
            case PURSUIT -> List.of(Perk.FRESH_BLOOD);
            case KILL_PRESSURE -> List.of(Perk.PURSUIT);
            case BLOOD_RUSH_NODE -> List.of(Perk.KILL_PRESSURE);
            case BOUNTY_HUNGER -> List.of(Perk.KILL_PRESSURE);
            case BLOODBOUND_BLADE -> List.of(Perk.BLOOD_RUSH_NODE, Perk.BOUNTY_HUNGER);
            case GUARDED_RECOVERY -> List.of(Perk.IRON_POSTURE);
            case COMBAT_ENDURANCE -> List.of(Perk.GUARDED_RECOVERY);
            case HOLD_THE_LINE -> List.of(Perk.COMBAT_ENDURANCE);
            case LAST_STAND -> List.of(Perk.COMBAT_ENDURANCE);
            case BLOODBOUND_GUARD -> List.of(Perk.HOLD_THE_LINE, Perk.LAST_STAND);
            case HUNTERS_MARK -> List.of(Perk.STEADY_AIM);
            case LONGSHOT_DISCIPLINE -> List.of(Perk.HUNTERS_MARK);
            case EVENT_COVER -> List.of(Perk.LONGSHOT_DISCIPLINE);
            case PIERCING_FOCUS -> List.of(Perk.LONGSHOT_DISCIPLINE);
            case BLOODBOUND_BOW -> List.of(Perk.EVENT_COVER, Perk.PIERCING_FOCUS);
            case ORE_SURVEYOR -> List.of(Perk.MINING_YIELD);
            case VEIN_DISCIPLINE -> List.of(Perk.MINING_SPEED);
            case MINING_MASTERY -> List.of(Perk.DEEP_MINER);
            case REPLANTER -> List.of(Perk.FARMING_YIELD);
            case HARVEST_FLOW -> List.of(Perk.REPLANTER);
            case FARMING_MASTERY -> List.of(Perk.FORESTER);
            case BOUNTY_FOCUS -> List.of(Perk.COMBAT_SUSTAIN);
            case DUELIST -> List.of(Perk.COMBAT_SUSTAIN);
            case ESCAPE_DISCIPLINE -> List.of(Perk.DUELIST);
            case KINGSLAYER_FOCUS -> List.of(Perk.BOUNTY_FOCUS);
            case BREWING_FOCUS -> List.of(Perk.ALCHEMY_MASTERY);
            case RELIC_ALCHEMY -> List.of(Perk.BREWING_FOCUS);
            case INFERNAL_RESOLVE -> List.of(Perk.RELIC_ALCHEMY);
            case ALCHEMY_GRANDMASTER -> List.of(Perk.INFERNAL_RESOLVE);
            case TABLE_ATTUNEMENT -> List.of(Perk.RUNE_SENSE);
            case BOOKSMITH -> List.of(Perk.TABLE_ATTUNEMENT);
            case ENCHANTING_GRANDMASTER -> List.of(Perk.BOOKSMITH);
            case ORDER_RUNNER -> List.of(Perk.ECONOMY_QUICKSELL_EFFICIENCY);
            case CONTRACT_BROKER -> List.of(Perk.ORDER_RUNNER);
            case AUCTION_APPRAISER -> List.of(Perk.CONTRACT_BROKER);
            default -> List.of();
        };
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

    private int averageSkillLevel(Player player) {
        int total = 0;
        for (Category category : Category.values()) {
            total += level(player, category);
        }
        return Math.max(1, total / Category.values().length);
    }

    private List<Perk> perksFor(Category category) {
        return java.util.Arrays.stream(Perk.values())
            .filter(perk -> perk.category() == category)
            .filter(this::perkEnabled)
            .sorted(Comparator.comparingInt(this::perkRequiredLevel).thenComparing(this::perkSlotConfig))
            .toList();
    }

    private List<Perk> perksFor(BloodboundLane lane) {
        return java.util.Arrays.stream(Perk.values())
            .filter(perk -> bloodboundLane(perk) == lane)
            .filter(this::perkEnabled)
            .sorted(Comparator.comparingInt(this::perkRequiredLevel).thenComparing(this::perkSlotConfig))
            .toList();
    }

    private Perk perkSlot(BloodboundLane lane, int slot) {
        for (Perk perk : perksFor(lane)) {
            if (perkSlotConfig(perk) == slot) {
                return perk;
            }
        }
        return null;
    }

    private BloodboundLane bloodboundLaneSlot(int slot) {
        for (BloodboundLane lane : BloodboundLane.values()) {
            if (lane.slot() == slot) {
                return lane;
            }
        }
        return null;
    }

    private void fillTreeFrame(Inventory inventory) {
        ItemStack filler = icon(Material.GLASS_PANE, "&8", List.of());
        for (int slot : new int[] {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 48, 50, 51, 52}) {
            inventory.setItem(slot, filler);
        }
    }

    private void drawLaneConnectors(Inventory inventory, BloodboundLane lane) {
        ItemStack unlocked = icon(Material.GREEN_STAINED_GLASS_PANE, "&bPath link", List.of("&7Follow the linked nodes upward."));
        ItemStack locked = icon(Material.RED_STAINED_GLASS_PANE, "&8Locked branch", List.of("&7Unlock earlier nodes first."));
        int[] line = switch (lane) {
            case ROOKIE -> new int[] {30, 31, 32, 22, 13};
            case BLOODBOUND -> new int[] {19, 20, 21, 22, 23, 24, 25};
            case COMBAT -> new int[] {29, 30, 31, 32, 33};
            case ECONOMY -> new int[] {20, 21, 22, 23, 24};
            case RELIC -> new int[] {29, 30, 31, 22, 13};
            case ENDBOSS -> new int[] {28, 29, 30, 31, 32, 33, 34};
        };
        for (int slot : line) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, unlocked);
            }
        }
        for (int slot : new int[] {10, 16, 37, 43}) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, locked);
            }
        }
    }

    private List<String> laneSources(Player player, BloodboundLane lane) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Primary sources:");
        for (Category category : lane.categories()) {
            int level = level(player, category);
            int totalXp = xp(player, category);
            lore.add("&8- &f" + category.display() + " &7level &f" + level + "&7/100 &8(" + totalXp + " XP)");
        }
        lore.add("&8");
        lore.add("&7Earn XP through the connected SMP loop,");
        lore.add("&7then spend points on this lane's nodes.");
        return lore;
    }

    private List<String> laneInfoLore(BloodboundLane lane) {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + lane.shortGoal());
        lore.add("&8");
        lore.add("&7Connected systems:");
        for (String system : lane.systems().split(", ")) {
            lore.add("&8- &f" + system);
        }
        lore.add("&8");
        lore.add("&7Design rule: strong identity,");
        lore.add("&7no free hearts, no money printer.");
        return lore;
    }

    private BloodboundLane bloodboundLane(Perk perk) {
        Specialization specialization = specializationOf(perk);
        if (specialization != null) {
            return specialization.main() == MainBranch.FRONTIER ? BloodboundLane.ROOKIE : BloodboundLane.COMBAT;
        }
        String configured = skillConfig.getString("node." + perk.key() + ".tree_lane", "");
        BloodboundLane override = BloodboundLane.from(configured);
        if (override != null) {
            return override;
        }
        return switch (perk) {
            case MINING_SPEED, MINING_YIELD, FARMING_YIELD, SUPPLY_GARDENER, COMBAT_SUSTAIN -> BloodboundLane.ROOKIE;
            case DEEP_MINER, ORE_SURVEYOR, BOUNTY_FOCUS, KINGSLAYER_FOCUS, ESCAPE_DISCIPLINE -> BloodboundLane.BLOODBOUND;
            case DUELIST, VEIN_DISCIPLINE -> BloodboundLane.COMBAT;
            case ECONOMY_QUICKSELL_EFFICIENCY, ORDER_RUNNER, CONTRACT_BROKER, AUCTION_APPRAISER -> BloodboundLane.ECONOMY;
            case FORESTER, REPLANTER, HARVEST_FLOW, FARMING_MASTERY, APPLE_LORE, RELIC_ALCHEMY -> BloodboundLane.RELIC;
            case BREWING_FOCUS, INFERNAL_RESOLVE, ALCHEMY_GRANDMASTER, RUNE_SENSE, TABLE_ATTUNEMENT, BOOKSMITH, ANVIL_CARE, ENCHANTING_MASTERY, ENCHANTING_GRANDMASTER -> BloodboundLane.ENDBOSS;
            default -> BloodboundLane.ROOKIE;
        };
    }

    private String linkedSystems(Perk perk) {
        Specialization specialization = specializationOf(perk);
        if (specialization != null) {
            return specialization.systems();
        }
        return switch (bloodboundLane(perk)) {
            case ROOKIE -> "rookie, goals, skirmish";
            case BLOODBOUND -> "lifesteal, bounties, recovery";
            case COMBAT -> "CombatTag, assists, PvP";
            case ECONOMY -> "EconomyWatch, orders, contracts, AH";
            case RELIC -> "collections, relics, seasons";
            case ENDBOSS -> "artifacts, ritual, EndBoss";
        };
    }

    private void writeDefaultSkilltreeConfig() {
        boolean changed = false;
        if (!skillConfig.contains("meta.version")) {
            skillConfig.set("meta.version", "bloodbound-tree-v1");
            skillConfig.set("meta.notes", "Edit node.<id> values, then run /skills reload. Effects are implemented by matching node ids in the plugin engine.");
            changed = true;
        }
        changed |= setDefault("tree.respec.enabled", true);
        changed |= setDefault("tree.respec.cost_money", 0.0D);
        changed |= setDefault("tree.respec.cooldown_seconds", 86400);
        changed |= setDefault("tree.root.node", Perk.WORKERS_INSTINCT.key());
        for (MainBranch branch : MainBranch.values()) {
            String prefix = "branch." + branch.key() + ".";
            changed |= setDefault(prefix + "enabled", true);
            changed |= setDefault(prefix + "display", branch.display());
            changed |= setDefault(prefix + "icon", branch.icon().name());
            changed |= setDefault(prefix + "theme", branch.theme());
            changed |= setDefault(prefix + "linked_systems", branch.systems());
        }
        for (Specialization specialization : Specialization.values()) {
            String prefix = "specialization." + specialization.key() + ".";
            changed |= setDefault(prefix + "enabled", true);
            changed |= setDefault(prefix + "display", specialization.display());
            changed |= setDefault(prefix + "main_branch", specialization.main().key());
            changed |= setDefault(prefix + "icon", specialization.icon().name());
            changed |= setDefault(prefix + "identity", specialization.identity());
            changed |= setDefault(prefix + "linked_systems", specialization.systems());
            changed |= setDefault(prefix + "page", specialization.name() + "_1");
            changed |= setDefault(prefix + "choice_locked_after_confirm", true);
        }
        for (Perk perk : Perk.values()) {
            String prefix = "node." + perk.key() + ".";
            Specialization specialization = specializationOf(perk);
            changed |= setDefault(prefix + "enabled", ACTIVE_PERKS.contains(perk));
            changed |= setDefault(prefix + "display", perk.display());
            changed |= setDefault(prefix + "category", perk.category().key());
            changed |= setDefault(prefix + "tree_lane", bloodboundLane(perk).key());
            changed |= setDefault(prefix + "lane", laneDefault(perk).name().toLowerCase(Locale.ROOT));
            changed |= setDefault(prefix + "main_branch", specialization == null ? "foundation" : specialization.main().key());
            changed |= setDefault(prefix + "specialization", specialization == null ? "foundation" : specialization.key());
            changed |= setDefault(prefix + "page", specialization == null ? "FOUNDATION" : specialization.name() + "_1");
            changed |= setDefault(prefix + "skillpoint_cost", 1);
            changed |= setDefault(prefix + "icon", perk.icon().name());
            changed |= setDefault(prefix + "slot", perk.slot());
            changed |= setDefault(prefix + "required_level", perk.requiredLevel());
            changed |= setDefault(prefix + "max_level", perk.max());
            changed |= setDefault(prefix + "requires", defaultRequirementString(perk));
            changed |= setDefault(prefix + "linked_systems", linkedSystems(perk));
            changed |= setDefault(prefix + "bloodbound_enchant_bonus_percent", isBloodboundEnchantCapstone(perk) ? 2.0D : 0.0D);
            changed |= setDefault(prefix + "bloodbound_enchant_bonus_cap_percent", isBloodboundEnchantCapstone(perk) ? 12.0D : 0.0D);
            changed |= setDefault(prefix + "description", perk.description());
        }
        if (changed) {
            skillConfig.save();
        }
    }

    private boolean setDefault(String key, Object value) {
        if (skillConfig.contains(key)) {
            return false;
        }
        skillConfig.set(key, value);
        return true;
    }

    private String defaultRequirementString(Perk perk) {
        return switch (perk) {
            case ORE_INSTINCT -> "stone_sense";
            case DEEP_DELVER -> "ore_instinct";
            case CONTRACT_MINER, SHARD_PROSPECTOR -> "deep_delver";
            case BLOODBOUND_PICK -> "contract_miner,shard_prospector";
            case RANCH_HAND -> "green_thumb";
            case FIELD_SENSE -> "ranch_hand";
            case STOCKPILE_DISCIPLINE, CRIMSON_GROWTH -> "field_sense";
            case BLOODBOUND_HOE -> "stockpile_discipline,crimson_growth";
            case TEMPERED_TOOLS -> "arcane_familiarity";
            case ENCHANTERS_FOCUS -> "tempered_tools";
            case ARCANE_WORKSTATION, RUNE_EFFICIENCY -> "enchanters_focus";
            case BLOODBOUND_INFUSION -> "arcane_workstation,rune_efficiency";
            case PURSUIT -> "fresh_blood";
            case KILL_PRESSURE -> "pursuit";
            case BLOOD_RUSH_NODE, BOUNTY_HUNGER -> "kill_pressure";
            case BLOODBOUND_BLADE -> "blood_rush_node,bounty_hunger";
            case GUARDED_RECOVERY -> "iron_posture";
            case COMBAT_ENDURANCE -> "guarded_recovery";
            case HOLD_THE_LINE, LAST_STAND -> "combat_endurance";
            case BLOODBOUND_GUARD -> "hold_the_line,last_stand";
            case HUNTERS_MARK -> "steady_aim";
            case LONGSHOT_DISCIPLINE -> "hunters_mark";
            case EVENT_COVER, PIERCING_FOCUS -> "longshot_discipline";
            case BLOODBOUND_BOW -> "event_cover,piercing_focus";
            case ORE_SURVEYOR -> "mining_yield";
            case VEIN_DISCIPLINE -> "mining_speed";
            case MINING_MASTERY -> "deep_miner";
            case REPLANTER -> "farming_yield";
            case HARVEST_FLOW -> "replanter";
            case FARMING_MASTERY -> "forester";
            case BOUNTY_FOCUS, DUELIST -> "combat_sustain";
            case ESCAPE_DISCIPLINE -> "duelist";
            case KINGSLAYER_FOCUS -> "bounty_focus";
            case BREWING_FOCUS -> "alchemy_mastery";
            case RELIC_ALCHEMY -> "brewing_focus";
            case INFERNAL_RESOLVE -> "relic_alchemy";
            case ALCHEMY_GRANDMASTER -> "infernal_resolve";
            case TABLE_ATTUNEMENT -> "rune_sense";
            case BOOKSMITH -> "table_attunement";
            case ENCHANTING_GRANDMASTER -> "booksmith";
            case ORDER_RUNNER -> "economy_quicksell_efficiency";
            case CONTRACT_BROKER -> "order_runner";
            case AUCTION_APPRAISER -> "contract_broker";
            default -> "";
        };
    }

    private boolean isBloodboundEnchantCapstone(Perk perk) {
        return perk == Perk.BLOODBOUND_PICK
            || perk == Perk.BLOODBOUND_HOE
            || perk == Perk.BLOODBOUND_INFUSION
            || perk == Perk.BLOODBOUND_BLADE
            || perk == Perk.BLOODBOUND_GUARD
            || perk == Perk.BLOODBOUND_BOW;
    }

    private boolean perkEnabled(Perk perk) {
        return Boolean.parseBoolean(skillConfig.getString("node." + perk.key() + ".enabled", String.valueOf(ACTIVE_PERKS.contains(perk))));
    }

    private String perkDisplay(Perk perk) {
        return skillConfig.getString("node." + perk.key() + ".display", perk.display());
    }

    private Material perkIcon(Perk perk) {
        try {
            return Material.valueOf(skillConfig.getString("node." + perk.key() + ".icon", perk.icon().name()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return perk.icon();
        }
    }

    private int perkSlotConfig(Perk perk) {
        return Math.max(0, Math.min(53, skillConfig.getInt("node." + perk.key() + ".slot", perk.slot())));
    }

    private int perkRequiredLevel(Perk perk) {
        return Math.max(1, Math.min(MAX_LEVEL, skillConfig.getInt("node." + perk.key() + ".required_level", perk.requiredLevel())));
    }

    private int perkMax(Perk perk) {
        return Math.max(1, Math.min(100, skillConfig.getInt("node." + perk.key() + ".max_level", perk.max())));
    }

    private List<String> perkDescriptionLines(Perk perk) {
        return java.util.Arrays.stream(skillConfig.getString("node." + perk.key() + ".description", perk.description()).split("\\|"))
            .map(line -> "&7" + line)
            .toList();
    }

    private SkillLane laneDefault(Perk perk) {
        return switch (perk) {
            case MINING_SPEED, VEIN_DISCIPLINE -> SkillLane.TUNNEL_RAT;
            case MINING_YIELD, ORE_SURVEYOR -> SkillLane.PROSPECTOR;
            case DEEP_MINER -> SkillLane.DEEP_MINER;
            case FARMING_YIELD, REPLANTER, HARVEST_FLOW -> SkillLane.CULTIVATOR;
            case FORESTER -> SkillLane.FORESTER;
            case SUPPLY_GARDENER -> SkillLane.HOMESTEADER;
            case DUELIST -> SkillLane.REAVER;
            case COMBAT_SUSTAIN -> SkillLane.WARDEN;
            case ESCAPE_DISCIPLINE -> SkillLane.PHANTOM;
            case BOUNTY_FOCUS, KINGSLAYER_FOCUS -> SkillLane.HEXBLADE;
            case BREWING_FOCUS, ALCHEMY_MASTERY -> SkillLane.BREWER;
            case APPLE_LORE, RELIC_ALCHEMY -> SkillLane.RELICIST;
            case INFERNAL_RESOLVE -> SkillLane.INFERNALIST;
            case RUNE_SENSE, TABLE_ATTUNEMENT -> SkillLane.RUNEWRIGHT;
            case BOOKSMITH, ANVIL_CARE -> SkillLane.BOOKSMITH;
            case ENCHANTING_MASTERY -> SkillLane.ARCANIST;
            case ECONOMY_QUICKSELL_EFFICIENCY -> SkillLane.MERCHANT;
            case ORDER_RUNNER -> SkillLane.LOGISTICIAN;
            case CONTRACT_BROKER -> SkillLane.CONTRACTOR;
            default -> SkillLane.MASTERY;
        };
    }

    private int[] perkDisplaySlots() {
        return new int[] { 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32 };
    }

    private List<String> categoryGuide(Category category) {
        return switch (category) {
            case MINING -> List.of("&7XP: ores, stone, deepslate, debris.", "&7Choose one identity:", "&9Deep Miner &7dominates below Y0.", "&bProspector &7finds ore value.", "&8Tunnel Rat &7moves and survives underground.", "&7Active: &f/markvein");
            case FARMING -> List.of("&7XP: crops and logs.", "&7Choose one identity:", "&2Forester &7owns tree/log economy.", "&aCultivator &7controls fields and replanting.", "&6Harvester &7keeps rebuild supplies flowing.");
            case COMBAT -> List.of("&7XP: damage, kills and bounty pressure.", "&7Choose one identity:", "&cReaver &7executes low targets.", "&fWarden &7outlasts burst.", "&5Phantom &7escapes and outplays.", "&4Hexblade &7tracks high-value targets.");
            case ALCHEMY -> List.of("&7XP: potions, apples, corrupted materials.", "&7Occultist-style progression:", "&dBrewing tempo", "&5relic/boss material value", "&cnether and ritual readiness.", "&7Active: &f/brewboost");
            case ENCHANTING -> List.of("&7XP: enchanting and ability item use.", "&7Choose one identity:", "&dRunewright &7hunts rare ability rolls.", "&eBooksmith &7moves power through books/anvils.", "&5Arcanist &7masters item-bound challenges.");
            case ECONOMY -> List.of("&7XP: QuickSell, AH, orders, and contracts.", "&7Choose one identity:", "&aForger/Merchant &7profits from selling.", "&6Contractor &7takes high-risk jobs.", "&bLogistician &7keeps server orders moving.", "&7Active: &f/contractboost");
        };
    }

    private Category categorySlot(int slot) {
        for (Category category : Category.values()) {
            if (category.slot() == slot) {
                return category;
            }
        }
        return null;
    }

    private Perk perkSlot(int slot) {
        for (Perk perk : Perk.values()) {
            if (perk.slot() == slot) {
                return perk;
            }
        }
        return null;
    }

    private Perk perkSlot(Category category, int page, int slot) {
        List<Perk> perks = perksFor(category);
        int[] slots = perkDisplaySlots();
        for (int index = 0; index < Math.min(perks.size(), slots.length); index++) {
            if (slots[index] == slot) {
                int actual = page * slots.length + index;
                return actual >= 0 && actual < perks.size() ? perks.get(actual) : null;
            }
        }
        return null;
    }

    private int maxPerkPage(Category category) {
        int count = perksFor(category).size();
        int pageSize = perkDisplaySlots().length;
        return Math.max(0, (count - 1) / pageSize);
    }

    @Override
    public void addXp(UUID playerId, String categoryKey, int amount, String reason) {
        Category category = Category.from(categoryKey);
        if (playerId == null || category == null || amount <= 0) {
            return;
        }
        addSkillXp(playerId, Bukkit.getPlayer(playerId), category, amount);
    }

    @Override
    public int getPerkLevel(UUID playerId, String perkKey) {
        if (playerId == null || perkKey == null) {
            return 0;
        }
        return Math.max(0, data.getInt("perk." + profileKey(playerId) + "." + perkKey.toLowerCase(Locale.ROOT), 0));
    }

    private String profileKey(UUID playerId) {
        Player online = playerId == null ? null : Bukkit.getPlayer(playerId);
        return online != null && MitchSMP.permissions().isAdminMode(online) ? "admin." + playerId : String.valueOf(playerId);
    }

    @Override
    public String getAbilityHud(UUID playerId) {
        List<String> lines = getAbilityHudLines(playerId);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    @Override
    public List<String> getAbilityHudLines(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player == null || restricted(player)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        ItemStack item = player.getInventory().getItemInMainHand();
        Ability ability = abilityFor(item);
        if (ability == null) {
            item = player.getInventory().getItemInOffHand();
            ability = abilityFor(item);
        }
        if (ability != null) {
            lines.add(renderAbilityHud(playerId, item, ability));
        }
        ItemStack aegis = aegisShield(player);
        if (aegis != null && ability != Ability.AEGIS_GUARD) {
            lines.add(renderAbilityHud(playerId, aegis, Ability.AEGIS_GUARD));
        }
        return lines;
    }

    private String renderAbilityHud(UUID playerId, ItemStack item, Ability ability) {
        AbilityState state = state(item, ability);
        String status;
        if (!abilityGloballyEnabled(ability)) {
            status = "&4DISABLED";
        } else if (!state.unlocked()) {
            status = "&cLOCKED";
        } else if (!state.enabled()) {
            status = "&7OFF";
        } else {
            long now = System.currentTimeMillis();
            long activeUntil = data.getLong("ability." + ability.id() + ".active_until." + profileKey(playerId), 0L);
            if (activeUntil > now) {
                status = "&6ACTIVE " + Math.max(1L, (activeUntil - now + 999L) / 1000L) + "s";
            } else {
                long remaining = cooldownRemainingMillis(playerId, ability);
                status = remaining > 0L ? "&cCOOLDOWN " + Math.max(1L, (remaining + 999L) / 1000L) + "s" : "&aREADY";
            }
        }
        return "&5" + ability.display() + ":" + status;
    }

    private int parseInt(String input, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(input)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private interface MaterialFilter {
        boolean accept(Material material);
    }

    private enum Plane {
        FLOOR,
        NORTH_SOUTH,
        EAST_WEST
    }

    private record AbilityState(int progress, boolean unlocked, boolean enabled) {
    }

    private static final class SkillsMenu implements InventoryHolder {
        private final String type;
        private Inventory inventory;

        SkillsMenu(String type) {
            this.type = type;
        }

        String type() {
            return type;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum NodeState {
        LOCKED("&cLocked"),
        AVAILABLE("&eAvailable"),
        PREVIEW("&8Preview-only"),
        UNLOCKED("&2Unlocked"),
        MAXED("&aMaxed");

        private final String label;

        NodeState(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private enum MainBranch {
        FRONTIER("frontier", "Frontier", Material.CHEST, "gathering, economy, crafting, preparation", "contracts, orders, economy, crafting"),
        WARPATH("warpath", "Warpath", Material.DIAMOND_SWORD, "PvP, Lifesteal, bounties, defense", "lifesteal, bounties, skirmish, CombatTag");

        private final String key;
        private final String display;
        private final Material icon;
        private final String theme;
        private final String systems;

        MainBranch(String key, String display, Material icon, String theme, String systems) {
            this.key = key;
            this.display = display;
            this.icon = icon;
            this.theme = theme;
            this.systems = systems;
        }

        String key() { return key; }
        String display() { return display; }
        Material icon() { return icon; }
        String theme() { return theme; }
        String systems() { return systems; }

        static MainBranch from(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            for (MainBranch branch : values()) {
                if (branch.key.equalsIgnoreCase(input) || branch.name().equalsIgnoreCase(input) || branch.display.equalsIgnoreCase(input)) {
                    return branch;
                }
            }
            return null;
        }
    }

    private enum Specialization {
        PROSPECTOR("prospector", "Prospector", MainBranch.FRONTIER, Material.DIAMOND_PICKAXE, "&b", 28, 37, "Resource/mining specialist.", "mining, contracts, EconomyWatch, tool progression",
            Perk.STONE_SENSE, Perk.ORE_INSTINCT, Perk.DEEP_DELVER, Perk.CONTRACT_MINER, Perk.SHARD_PROSPECTOR, Perk.BLOODBOUND_PICK),
        CULTIVATOR("cultivator", "Cultivator", MainBranch.FRONTIER, Material.GOLDEN_HOE, "&a", 30, 38, "Farming, sustain and recovery specialist.", "farming, animals, recovery, food economy",
            Perk.GREEN_THUMB, Perk.RANCH_HAND, Perk.FIELD_SENSE, Perk.STOCKPILE_DISCIPLINE, Perk.CRIMSON_GROWTH, Perk.BLOODBOUND_HOE),
        RUNESMITH("runesmith", "Runesmith", MainBranch.FRONTIER, Material.ANVIL, "&d", 32, 39, "Gear, enchanting and workstation specialist.", "enchanting, anvils, artifacts, gear economy",
            Perk.ARCANE_FAMILIARITY, Perk.TEMPERED_TOOLS, Perk.ENCHANTERS_FOCUS, Perk.ARCANE_WORKSTATION, Perk.RUNE_EFFICIENCY, Perk.BLOODBOUND_INFUSION),
        BLOODREAVER("bloodreaver", "Bloodreaver", MainBranch.WARPATH, Material.NETHERITE_SWORD, "&4", 34, 40, "Aggressive Lifesteal and bounty hunter.", "lifesteal, bounties, kills, seasons, legacy",
            Perk.FRESH_BLOOD, Perk.PURSUIT, Perk.KILL_PRESSURE, Perk.BLOOD_RUSH_NODE, Perk.BOUNTY_HUNGER, Perk.BLOODBOUND_BLADE),
        BULWARK("bulwark", "Bulwark", MainBranch.WARPATH, Material.SHIELD, "&9", 36, 41, "Defensive frontline and teamfight sustain.", "CombatTag, recovery, shield play, events",
            Perk.IRON_POSTURE, Perk.GUARDED_RECOVERY, Perk.COMBAT_ENDURANCE, Perk.HOLD_THE_LINE, Perk.LAST_STAND, Perk.BLOODBOUND_GUARD),
        MARKSMAN("marksman", "Marksman", MainBranch.WARPATH, Material.BOW, "&e", 43, 42, "Ranged bounty pressure and assist support.", "bows, assists, bounties, skirmish, events",
            Perk.STEADY_AIM, Perk.HUNTERS_MARK, Perk.LONGSHOT_DISCIPLINE, Perk.EVENT_COVER, Perk.PIERCING_FOCUS, Perk.BLOODBOUND_BOW);

        private final String key;
        private final String display;
        private final MainBranch main;
        private final Material icon;
        private final String color;
        private final int slot;
        private final int viewSlot;
        private final String identity;
        private final String systems;
        private final List<Perk> perks;

        Specialization(String key, String display, MainBranch main, Material icon, String color, int slot, int viewSlot, String identity, String systems, Perk... perks) {
            this.key = key;
            this.display = display;
            this.main = main;
            this.icon = icon;
            this.color = color;
            this.slot = slot;
            this.viewSlot = viewSlot;
            this.identity = identity;
            this.systems = systems;
            this.perks = List.of(perks);
        }

        String key() { return key; }
        String display() { return display; }
        MainBranch main() { return main; }
        Material icon() { return icon; }
        String color() { return color; }
        int slot() { return slot; }
        int viewSlot() { return viewSlot; }
        String identity() { return identity; }
        String systems() { return systems; }
        List<Perk> perks() { return perks; }

        static Specialization from(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            for (Specialization specialization : values()) {
                if (specialization.key.equalsIgnoreCase(input) || specialization.name().equalsIgnoreCase(input) || specialization.display.equalsIgnoreCase(input)) {
                    return specialization;
                }
            }
            return null;
        }
    }

    private enum BloodboundLane {
        ROOKIE("rookie", "Rookie Path", Material.LEATHER_BOOTS, 10, "&a", "Learn the server loop without free hearts.", "rookie, goals, skirmish", Category.MINING, Category.FARMING, Category.COMBAT),
        BLOODBOUND("bloodbound", "Bloodbound Path", Material.REDSTONE, 12, "&4", "Turn hearts, bounties and recovery into identity.", "lifesteal, bounties, corrupted hearts", Category.COMBAT, Category.ALCHEMY),
        COMBAT("combat", "Combat Path", Material.DIAMOND_SWORD, 14, "&c", "Build a PvP style through awareness, assists and risk.", "CombatTag, skirmish, assists", Category.COMBAT, Category.MINING),
        ECONOMY("economy", "Economy Path", Material.EMERALD, 16, "&6", "Read markets, run orders and broker contracts.", "EconomyWatch, orders, contracts, AH", Category.ECONOMY),
        RELIC("relic", "Relic & Legacy Path", Material.ECHO_SHARD, 30, "&5", "Collect, archive and build season prestige.", "collections, relics, seasons, legacy", Category.FARMING, Category.ALCHEMY),
        ENDBOSS("endboss", "Event & EndBoss Path", Material.NETHER_STAR, 32, "&d", "Prepare for rituals, artifacts and the Archfiend.", "events, artifacts, boss shards, EndBoss", Category.ENCHANTING, Category.ALCHEMY);

        private final String key;
        private final String display;
        private final Material icon;
        private final int slot;
        private final String color;
        private final String shortGoal;
        private final String systems;
        private final List<Category> categories;

        BloodboundLane(String key, String display, Material icon, int slot, String color, String shortGoal, String systems, Category... categories) {
            this.key = key;
            this.display = display;
            this.icon = icon;
            this.slot = slot;
            this.color = color;
            this.shortGoal = shortGoal;
            this.systems = systems;
            this.categories = List.of(categories);
        }

        String key() { return key; }
        String display() { return display; }
        Material icon() { return icon; }
        int slot() { return slot; }
        String color() { return color; }
        String shortGoal() { return shortGoal; }
        String systems() { return systems; }
        List<Category> categories() { return categories; }

        static BloodboundLane from(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            for (BloodboundLane lane : values()) {
                if (lane.key.equalsIgnoreCase(input) || lane.name().equalsIgnoreCase(input) || lane.display.equalsIgnoreCase(input)) {
                    return lane;
                }
            }
            return null;
        }
    }

    private enum SkillLane {
        MASTERY("Mastery", false),
        DEEP_MINER("Deep Miner", true),
        PROSPECTOR("Prospector", true),
        TUNNEL_RAT("Tunnel Rat", true),
        CULTIVATOR("Cultivator", true),
        FORESTER("Forester", true),
        HOMESTEADER("Homesteader", true),
        REAVER("Reaver", true),
        WARDEN("Warden", true),
        PHANTOM("Phantom", true),
        HEXBLADE("Hexblade", true),
        DUELIST("Duelist", true),
        HUNTER("Hunter", true),
        SURVIVOR("Survivor", true),
        BREWER("Brewer", true),
        RELICIST("Relicist", true),
        INFERNALIST("Infernalist", true),
        RUNEWRIGHT("Runewright", true),
        BOOKSMITH("Booksmith", true),
        ARCANIST("Arcanist", true),
        MERCHANT("Merchant", true),
        CONTRACTOR("Contractor", true),
        LOGISTICIAN("Logistician", true);

        private final String display;
        private final boolean exclusive;

        SkillLane(String display, boolean exclusive) {
            this.display = display;
            this.exclusive = exclusive;
        }

        String display() {
            return display;
        }

        boolean exclusive() {
            return exclusive;
        }

        static SkillLane from(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            for (SkillLane lane : values()) {
                if (lane.name().equalsIgnoreCase(input) || lane.display.equalsIgnoreCase(input)) {
                    return lane;
                }
            }
            return null;
        }
    }

    private enum Category {
        MINING("mining", "Mining", Material.DIAMOND_PICKAXE, 10),
        FARMING("farming", "Farming", Material.WHEAT, 11),
        COMBAT("combat", "PvP & Combat", Material.DIAMOND_SWORD, 12),
        ALCHEMY("alchemy", "Alchemy", Material.GOLDEN_APPLE, 14),
        ENCHANTING("enchanting", "Enchanting", Material.EXPERIENCE_BOTTLE, 15),
        ECONOMY("economy", "Economy", Material.EMERALD, 16);

        private final String key;
        private final String display;
        private final Material icon;
        private final int slot;

        Category(String key, String display, Material icon, int slot) {
            this.key = key;
            this.display = display;
            this.icon = icon;
            this.slot = slot;
        }

        String key() {
            return key;
        }

        String display() {
            return display;
        }

        Material icon() {
            return icon;
        }

        int slot() {
            return slot;
        }

        static String[] keys() {
            return java.util.Arrays.stream(values()).map(Category::key).toArray(String[]::new);
        }

        static Category from(String input) {
            for (Category category : values()) {
                if (category.key.equalsIgnoreCase(input) || category.name().equalsIgnoreCase(input)) {
                    return category;
                }
            }
            return null;
        }
    }

    private enum Perk {
        MINING_SPEED("mining_speed", "Mining Speed", Category.MINING, Material.DIAMOND_PICKAXE, 19, 1, 20, "Effect: frequent Haste while mining so long sessions feel faster.|Per tier: stronger and more reliable mining speed boosts."),
        MINING_YIELD("mining_yield", "Mining Yield", Category.MINING, Material.DIAMOND_ORE, 20, 5, 25, "Effect: ore blocks can drop an extra ore block on break.|Per tier: +2.5% bonus-drop chance, max +62.5%."),
        DEEP_MINER("deep_miner", "Deep Miner", Category.MINING, Material.DEEPSLATE_DIAMOND_ORE, 21, 15, 15, "Effect: grants bonus Mining XP at Y 0 and below.|Per tiers: up to +7 XP for every deep-mined block."),
        ORE_SURVEYOR("ore_surveyor", "Ore Surveyor", Category.MINING, Material.COMPASS, 22, 25, 10, "Effect: improves valuable ore payout without changing mining speed.|Per tier: +1.5% bonus ore chance."),
        VEIN_DISCIPLINE("vein_discipline", "Vein Discipline", Category.MINING, Material.IRON_PICKAXE, 23, 40, 10, "Effect: cleaner vein work improves ore yield instead of duplicating Haste.|Per tier: +1% bonus ore chance."),
        MINING_MASTERY("mining_mastery", "Mining Mastery", Category.MINING, Material.NETHERITE_PICKAXE, 24, 75, 5, "Effect: mining capstone for stronger Haste and better ore yield.|Per tier: +3% bonus ore chance and contributes to Haste level."),
        FARMING_YIELD("farming_yield", "Farming Yield", Category.FARMING, Material.WHEAT, 21, 1, 25, "Effect: crops and logs can drop extra block loot.|Per tier: +3.5% bonus-drop chance, max 87.5%."),
        FORESTER("forester", "Forester", Category.FARMING, Material.OAK_LOG, 22, 8, 15, "Effect: logs grant more Farming XP and can duplicate.|Per tier: extra XP plus +1.5% bonus-log chance."),
        REPLANTER("replanter", "Replanter", Category.FARMING, Material.CARROT, 23, 15, 10, "Effect: broken crop blocks can auto-replant.|Per tier: higher replant chance, up to 95%."),
        HARVEST_FLOW("harvest_flow", "Harvest Flow", Category.FARMING, Material.GOLDEN_HOE, 24, 25, 10, "Effect: hoes harvest nearby crops in one action.|Per tier: more crops cleared per click, radius grows near max."),
        SUPPLY_GARDENER("supply_gardener", "Supply Gardener", Category.FARMING, Material.HAY_BLOCK, 25, 40, 10, "Effect: crops grant extra Farming XP.|Per tiers: up to +2 XP per harvested crop."),
        FARMING_MASTERY("farming_mastery", "Farming Mastery", Category.FARMING, Material.NETHERITE_HOE, 26, 75, 5, "Effect: improves crop/log duplication and animal-farming feedback.|Per tier: +2.5% farming drop chance."),
        COMBAT_SUSTAIN("combat_sustain", "Combat Sustain", Category.COMBAT, Material.TOTEM_OF_UNDYING, 23, 10, 20, "Effect: heals after a real kill.|Per tier: restores 0.15 health without adding hearts."),
        BOUNTY_FOCUS("bounty_focus", "Bounty Focus", Category.COMBAT, Material.NETHER_STAR, 24, 15, 10, "Effect: damaging a bounty target grants extra Combat XP.|Per tier: +1 Combat XP, limited to once per 10 seconds."),
        DUELIST("duelist", "Duelist", Category.COMBAT, Material.DIAMOND_SWORD, 25, 25, 10, "Effect: slightly increases direct player-vs-player damage.|Per tier: +0.5% PvP damage, max +5%."),
        ESCAPE_DISCIPLINE("escape_discipline", "Escape Discipline", Category.COMBAT, Material.SHIELD, 26, 35, 10, "Effect: prevents a critical hit and teleports you away below 20% health.|Per tier: escape distance grows from 14 to 50 blocks; 60-minute cooldown."),
        KINGSLAYER_FOCUS("kingslayer_focus", "Kingslayer Focus", Category.COMBAT, Material.GOLD_BLOCK, 27, 50, 10, "Effect: highlights nearby 20-heart or bounty targets.|Per tier: detection range grows from 42 to 128 blocks; targets are warned."),
        COMBAT_MASTERY("combat_mastery", "Combat Mastery", Category.COMBAT, Material.NETHERITE_SWORD, 28, 80, 5, "Effect: combat prestige status.|Per tier: endgame status without selling hearts."),
        ALCHEMY_MASTERY("alchemy_mastery", "Alchemy Mastery", Category.ALCHEMY, Material.GOLDEN_APPLE, 24, 10, 20, "Effect: alchemy progression and consumable identity.|Per tier: stronger utility hooks over time."),
        BREWING_FOCUS("brewing_focus", "Brewing Focus", Category.ALCHEMY, Material.EXPERIENCE_BOTTLE, 25, 15, 10, "Effect: interacting with an active brewing stand shortens its timer.|Per tier: 5% faster, max 50%; once per 30 seconds."),
        APPLE_LORE("apple_lore", "Apple Lore", Category.ALCHEMY, Material.ENCHANTED_GOLDEN_APPLE, 26, 30, 10, "Effect: rare consumables give stronger alchemy progression.|Per tier: apples matter more for the category grind."),
        RELIC_ALCHEMY("relic_alchemy", "Relic Alchemy", Category.ALCHEMY, Material.ECHO_SHARD, 27, 45, 10, "Effect: raises Alchemy progression from rare consumables.|Per tier: contributes to mastery rewards without consuming boss currency."),
        INFERNAL_RESOLVE("infernal_resolve", "Infernal Resolve", Category.ALCHEMY, Material.MAGMA_BLOCK, 28, 60, 10, "Effect: grants maintained Fire Resistance while exploring the Nether.|Any purchased tier unlocks the passive protection."),
        ALCHEMY_GRANDMASTER("alchemy_grandmaster", "Alchemy Grandmaster", Category.ALCHEMY, Material.DRAGON_EGG, 29, 85, 5, "Effect: consumed potion effects last longer.|Per tier: +5% duration, max +25%."),
        ENCHANTING_MASTERY("enchanting_mastery", "Enchanting Mastery", Category.ENCHANTING, Material.EXPERIENCE_BOTTLE, 25, 10, 20, "Effect: improves Bloodbound ability challenge progression.|Per tier: more progress from ability actions."),
        RUNE_SENSE("rune_sense", "Rune Sense", Category.ENCHANTING, Material.BOOK, 26, 15, 10, "Effect: increases the chance to roll a Bloodbound ability enchant.|Per tier: improves rare ability discovery."),
        TABLE_ATTUNEMENT("table_attunement", "Table Attunement", Category.ENCHANTING, Material.ANVIL, 27, 25, 10, "Effect: improves high-level Bloodbound enchant rolls.|Per tier: further raises ability-enchant chance."),
        BOOKSMITH("booksmith", "Booksmith", Category.ENCHANTING, Material.ENCHANTED_BOOK, 28, 35, 10, "Effect: max rank unlocks `/skills anvil` from anywhere.|The portable anvil still follows normal item rules."),
        ANVIL_CARE("anvil_care", "Anvil Care", Category.ENCHANTING, Material.IRON_INGOT, 29, 50, 10, "Effect: represents mastery of repair and combination work.|Per tier: increases Enchanting progression gained from ability challenges."),
        ENCHANTING_GRANDMASTER("enchanting_grandmaster", "Enchanting Grandmaster", Category.ENCHANTING, Material.NETHER_STAR, 30, 80, 5, "Effect: max rank unlocks `/skills enchant` from anywhere.|Portable enchanting still uses normal XP and lapis."),
        ECONOMY_QUICKSELL_EFFICIENCY("economy_quicksell_efficiency", "QuickSell Efficiency", Category.ECONOMY, Material.EMERALD, 31, 1, 15, "Effect: improves the final `/sell` payout.|Per tier: +1% QuickSell value, max +15%."),
        MARKET_ANALYST("market_analyst", "Market Analyst", Category.ECONOMY, Material.PAPER, 32, 5, 10, "Effect: clearer dynamic market guidance in economy UIs.|Per tier: improves market-awareness hooks."),
        BULK_SELLER("bulk_seller", "Bulk Seller", Category.ECONOMY, Material.HOPPER, 33, 10, 10, "Effect: bulk selling becomes more efficient.|Per tier: improves large-sale progression."),
        ORDER_RUNNER("order_runner", "Order Runner", Category.ECONOMY, Material.CHEST, 34, 15, 10, "Effect: resource orders pay more.|Per tier: +1.5% order payout, max +15%."),
        CONTRACT_BROKER("contract_broker", "Contract Broker", Category.ECONOMY, Material.COMPASS, 35, 20, 10, "Effect: high-risk contracts pay more.|Per tier: +2% contract payout, max +20%."),
        AUCTION_APPRAISER("auction_appraiser", "Auction Appraiser", Category.ECONOMY, Material.GOLD_INGOT, 36, 25, 10, "Effect: AH-prijzen beter leren lezen.|Per tier: betere listing/value hooks."),
        TRADE_LEDGER("trade_ledger", "Trade Ledger", Category.ECONOMY, Material.WRITTEN_BOOK, 37, 30, 10, "Effect: pay/trade activiteit telt mee.|Per tier: betere trade reputation progress."),
        PRICE_MEMORY("price_memory", "Price Memory", Category.ECONOMY, Material.REDSTONE, 38, 35, 10, "Effect: market history becomes more useful.|Per tier: better price-trend hooks."),
        LIQUIDITY_SENSE("liquidity_sense", "Liquidity Sense", Category.ECONOMY, Material.WATER_BUCKET, 39, 40, 10, "Effect: actieve economie beter benutten.|Per tier: betere active-market scaling."),
        TAX_EFFICIENCY("tax_efficiency", "Tax Efficiency", Category.ECONOMY, Material.SUNFLOWER, 40, 45, 10, "Effect: toekomstige fees iets slimmer.|Per tier: kleine fee/merchant efficiency."),
        RESOURCE_APPRAISER("resource_appraiser", "Resource Appraiser", Category.ECONOMY, Material.DIAMOND, 41, 55, 10, "Effect: high-value resources beter waarderen.|Per tier: betere rare-resource hooks."),
        MERCHANT_REPUTATION("merchant_reputation", "Merchant Reputation", Category.ECONOMY, Material.EMERALD_BLOCK, 42, 65, 10, "Effect: merchant prestige route.|Per tier: betere shop/order reputation."),
        BLACK_MARKET_SENSE("black_market_sense", "Black Market Sense", Category.ECONOMY, Material.TRIPWIRE_HOOK, 43, 75, 5, "Effect: rare merchant/event economy.|Per tier: betere black-market hooks."),
        ECONOMY_MASTERY("economy_mastery", "Economy Mastery", Category.ECONOMY, Material.NETHER_STAR, 44, 90, 5, "Effect: economy prestige status.|Per tier: kleine globale economy mastery."),
        WORKERS_INSTINCT("workers_instinct", "Worker's Instinct", Category.MINING, Material.NETHER_STAR, 4, 1, 1, "Root skill: starts your Bloodbound progression.|Small early-game progression boost, no free hearts."),
        STONE_SENSE("stone_sense", "Stone Sense", Category.MINING, Material.STONE, 13, 1, 3, "Prospector: improves basic mining progression.|Connects early resources to rookie contracts."),
        ORE_INSTINCT("ore_instinct", "Ore Instinct", Category.MINING, Material.DIAMOND_ORE, 22, 8, 3, "Prospector: improves ore-focused progression.|Helps mining feel like a real identity."),
        DEEP_DELVER("deep_delver", "Deep Delver", Category.MINING, Material.DEEPSLATE_DIAMOND_ORE, 31, 18, 3, "Prospector: rewards dangerous deep mining.|Supports midgame/endgame resource preparation."),
        CONTRACT_MINER("contract_miner", "Contract Miner", Category.MINING, Material.WRITTEN_BOOK, 29, 30, 2, "Prospector: mining contracts matter more.|Rewards high-risk resource work without printing money."),
        SHARD_PROSPECTOR("shard_prospector", "Shard Prospector", Category.MINING, Material.ECHO_SHARD, 33, 45, 2, "Prospector: improves shard/material progression visibility.|Does not grant free shards."),
        BLOODBOUND_PICK("bloodbound_pick", "Bloodbound Pick", Category.MINING, Material.NETHERITE_PICKAXE, 40, 70, 1, "Prospector capstone: improves Bloodbound pickaxe enchant chance.|Chance and cap are config-driven."),
        GREEN_THUMB("green_thumb", "Green Thumb", Category.FARMING, Material.WHEAT, 13, 1, 3, "Cultivator: improves basic farming progression.|Stable sustain without combat power."),
        RANCH_HAND("ranch_hand", "Ranch Hand", Category.FARMING, Material.WHEAT, 22, 8, 3, "Cultivator: animal and breeding progression.|Supports food and recovery loops."),
        FIELD_SENSE("field_sense", "Field Sense", Category.FARMING, Material.COMPASS, 31, 18, 3, "Cultivator: clearer farming goal progression.|Makes long-term sustain easier to plan."),
        STOCKPILE_DISCIPLINE("stockpile_discipline", "Stockpile Discipline", Category.FARMING, Material.CHEST, 29, 30, 2, "Cultivator: improves recovery and food-prep loops.|No hearts, no combat item advantage."),
        CRIMSON_GROWTH("crimson_growth", "Crimson Growth", Category.FARMING, Material.REDSTONE, 33, 45, 2, "Cultivator: high-tier farming and sustain identity.|Useful for rebuilding after losses."),
        BLOODBOUND_HOE("bloodbound_hoe", "Bloodbound Hoe", Category.FARMING, Material.NETHERITE_HOE, 40, 70, 1, "Cultivator capstone: improves Bloodbound hoe enchant chance.|Chance and cap are config-driven."),
        ARCANE_FAMILIARITY("arcane_familiarity", "Arcane Familiarity", Category.ENCHANTING, Material.BOOK, 13, 1, 3, "Runesmith: basic enchanting progression.|Introduces gear improvement identity."),
        TEMPERED_TOOLS("tempered_tools", "Tempered Tools", Category.ENCHANTING, Material.ANVIL, 22, 8, 3, "Runesmith: tool and gear improvement progression.|Supports crafting and market loops."),
        ENCHANTERS_FOCUS("enchanters_focus", "Enchanter's Focus", Category.ENCHANTING, Material.EXPERIENCE_BOTTLE, 31, 18, 3, "Runesmith: improves enchant-related progression.|Prepares for portable workstations."),
        ARCANE_WORKSTATION("arcane_workstation", "Arcane Workstation", Category.ENCHANTING, Material.ANVIL, 29, 30, 1, "Runesmith unlock: enables /anvil and /enchant outside combat.|Disabled while combat tagged or in blocked worlds."),
        RUNE_EFFICIENCY("rune_efficiency", "Rune Efficiency", Category.ENCHANTING, Material.LAPIS_LAZULI, 33, 45, 2, "Runesmith: improves gear-upgrade efficiency progression.|No free OP enchants."),
        BLOODBOUND_INFUSION("bloodbound_infusion", "Bloodbound Infusion", Category.ENCHANTING, Material.ENCHANTED_BOOK, 40, 70, 1, "Runesmith capstone: improves relevant Bloodbound enchant chance.|Chance and cap are config-driven."),
        FRESH_BLOOD("fresh_blood", "Fresh Blood", Category.COMBAT, Material.REDSTONE, 13, 1, 3, "Bloodreaver: valid fights grant better PvP progression.|Anti-farm rules still apply."),
        PURSUIT("pursuit", "Pursuit", Category.COMBAT, Material.COMPASS, 22, 8, 3, "Bloodreaver: bounty target pressure progression.|No unfair wallhack tracking."),
        KILL_PRESSURE("kill_pressure", "Kill Pressure", Category.COMBAT, Material.IRON_SWORD, 31, 18, 3, "Bloodreaver: legitimate kills matter more.|No extra hearts are granted."),
        BLOOD_RUSH_NODE("blood_rush_node", "Blood Rush", Category.COMBAT, Material.REDSTONE, 29, 30, 2, "Bloodreaver: momentum after kills and assists.|Strictly config-driven and anti-farm aware."),
        BOUNTY_HUNGER("bounty_hunger", "Bounty Hunger", Category.COMBAT, Material.GOLD_INGOT, 33, 45, 2, "Bloodreaver: bounty participation progression.|Rewards risk without money-printing."),
        BLOODBOUND_BLADE("bloodbound_blade", "Bloodbound Blade", Category.COMBAT, Material.NETHERITE_SWORD, 40, 70, 1, "Bloodreaver capstone: improves Bloodbound sword enchant chance.|Chance and cap are config-driven."),
        IRON_POSTURE("iron_posture", "Iron Posture", Category.COMBAT, Material.IRON_CHESTPLATE, 13, 1, 3, "Bulwark: defensive combat progression.|A frontline identity, not immortality."),
        GUARDED_RECOVERY("guarded_recovery", "Guarded Recovery", Category.COMBAT, Material.GOLDEN_APPLE, 22, 8, 3, "Bulwark: recovery and survival utility progression.|Does not grant free hearts."),
        COMBAT_ENDURANCE("combat_endurance", "Combat Endurance", Category.COMBAT, Material.SHIELD, 31, 18, 3, "Bulwark: better feedback and progression during CombatTag.|Rewards staying alive under pressure."),
        HOLD_THE_LINE("hold_the_line", "Hold the Line", Category.COMBAT, Material.IRON_BARS, 29, 30, 2, "Bulwark: teamfight and event defense identity.|Supports group PvP without hard damage scaling."),
        LAST_STAND("last_stand", "Last Stand", Category.COMBAT, Material.TOTEM_OF_UNDYING, 33, 45, 2, "Bulwark: emergency defensive utility progression.|Cooldown and power are config-driven."),
        BLOODBOUND_GUARD("bloodbound_guard", "Bloodbound Guard", Category.COMBAT, Material.SHIELD, 40, 70, 1, "Bulwark capstone: improves Bloodbound shield enchant chance.|No permanent OP reduction."),
        STEADY_AIM("steady_aim", "Steady Aim", Category.COMBAT, Material.BOW, 13, 1, 3, "Marksman: basic ranged progression.|Supports bows without forcing melee."),
        HUNTERS_MARK("hunters_mark", "Hunter's Mark", Category.COMBAT, Material.COMPASS, 22, 8, 3, "Marksman: bounty and assist target progression.|No wallhack tracking."),
        LONGSHOT_DISCIPLINE("longshot_discipline", "Longshot Discipline", Category.COMBAT, Material.ARROW, 31, 18, 3, "Marksman: ranged assist progression.|Rewards skillful distance pressure."),
        EVENT_COVER("event_cover", "Event Cover", Category.COMBAT, Material.CROSSBOW, 29, 30, 2, "Marksman: ranged support during events.|Supports team fights and skirmishes."),
        PIERCING_FOCUS("piercing_focus", "Piercing Focus", Category.COMBAT, Material.ARROW, 33, 45, 2, "Marksman: high-tier ranged progression.|Config-driven, not guaranteed burst."),
        BLOODBOUND_BOW("bloodbound_bow", "Bloodbound Bow", Category.COMBAT, Material.BOW, 40, 70, 1, "Marksman capstone: improves Bloodbound bow enchant chance.|Chance and cap are config-driven.");

        private final String key;
        private final String display;
        private final Category category;
        private final Material icon;
        private final int slot;
        private final int requiredLevel;
        private final int max;
        private final String description;

        Perk(String key, String display, Category category, Material icon, int slot, int requiredLevel, int max, String description) {
            this.key = key;
            this.display = display;
            this.category = category;
            this.icon = icon;
            this.slot = slot;
            this.requiredLevel = requiredLevel;
            this.max = max;
            this.description = description;
        }

        String key() { return key; }
        String display() { return display; }
        Category category() { return category; }
        Material icon() { return icon; }
        int slot() { return slot; }
        int requiredLevel() { return requiredLevel; }
        int max() { return max; }
        String description() { return description; }
        List<String> descriptionLines() {
            return java.util.Arrays.stream(description.split("\\|"))
                .map(line -> "&7" + line)
                .toList();
        }

        static Perk from(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            for (Perk perk : values()) {
                if (perk.key.equalsIgnoreCase(input) || perk.name().equalsIgnoreCase(input) || perk.display.equalsIgnoreCase(input)) {
                    return perk;
                }
            }
            return null;
        }
    }

    private enum Ability {
        GODS_DRILL("gods_drill", "God's Drill", "Mine blocks", 500_000, List.of("&73x3 oriented mining for pickaxes.", "&7Toggle from &f/abilities&7, the GUI, or sneak + drop.")),
        ANCIENT_TIMBER("ancient_timber", "Ancient Timber", "Chop logs", 250_000, List.of("&7Fells the whole tree, logs and leaves.", "&7Great for late-game base building.")),
        EARTHSHAPER("earthshaper", "Earthshaper", "Dig shovel blocks", 250_000, List.of("&73x3 digging for shovel blocks.", "&7Useful terraforming, not combat power.")),
        HARVEST_LORD("harvest_lord", "Harvest Lord", "Harvest crops", 200_000, List.of("&7Sneak-free right-click plants a 3x3 crop patch.", "&7Uses seeds or crops from your inventory.")),
        BLOOD_FORGED_EDGE("blood_forged_edge", "Blood-Forged Edge", "Kill mobs/players", 25_000, List.of("&7Small sustain on kills.", "&7Below 33% health, hits deal x2 damage.")),
        ECHO_QUIVER("echo_quiver", "Echo Quiver", "Land projectile hits", 100_000, List.of("&7Arrows call lightning onto the hit target.", "&7Works on bow/crossbow items.")),
        STORM_BIND("storm_bind", "Storm Bind", "Land trident hits", 75_000, List.of("&7Thrown tridents explode on impact.", "&7Explosion does not break blocks.")),
        AEGIS_GUARD("aegis_guard", "Aegis Guard", "Block attacks", 50_000, List.of("&7Manually activate by sneak-right-clicking the Aegis item.", "&7Absorbs 100% damage during its configured active duration."));

        private final String id;
        private final String display;
        private final String challenge;
        private final int defaultRequired;
        private final List<String> description;

        Ability(String id, String display, String challenge, int defaultRequired, List<String> description) {
            this.id = id;
            this.display = display;
            this.challenge = challenge;
            this.defaultRequired = defaultRequired;
            this.description = description;
        }

        String id() { return id; }
        String display() { return display; }
        String challenge() { return challenge; }
        int defaultRequired() { return defaultRequired; }
        List<String> description() { return description; }
        boolean pvpRelated() {
            return this == BLOOD_FORGED_EDGE || this == ECHO_QUIVER || this == STORM_BIND || this == AEGIS_GUARD;
        }

        static Ability from(String input) {
            for (Ability ability : values()) {
                if (ability.id.equalsIgnoreCase(input) || ability.name().equalsIgnoreCase(input)) {
                    return ability;
                }
            }
            return null;
        }

        static String[] keys() {
            return java.util.Arrays.stream(values()).map(Ability::id).toArray(String[]::new);
        }
    }
}



