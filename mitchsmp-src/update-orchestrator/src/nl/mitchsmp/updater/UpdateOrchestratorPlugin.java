package nl.mitchsmp.updater;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import nl.mitchsmp.core.api.MitchSMP;
import nl.mitchsmp.core.api.MitchRank;
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class UpdateOrchestratorPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final String MANIFEST = "mitchsmp-release-manifest.json";
    private static final Pattern ASSET_OBJECT_PATTERN = Pattern.compile("\\{[^{}]*\"browser_download_url\"\\s*:\\s*\"[^\"]+\"[^{}]*}", Pattern.DOTALL);
    private static final Pattern ASSET_PAIR_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"(?:(?!\"name\"\\s*:).)*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern MANIFEST_PLUGIN_PATTERN = Pattern.compile("\\{[^{}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"file\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"version\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"sha256\"\\s*:\\s*\"([a-fA-F0-9]{64})\"[^{}]*}", Pattern.DOTALL);
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private PropertiesFile config;
    private Path pluginsDir;
    private Path updatesRoot;
    private Path stagedRoot;
    private Path backupRoot;
    private Path historyRoot;
    private Path historyLog;
    private Path pendingFile;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile String lastCheck = "never";
    private volatile String lastAvailable = "unknown";
    private volatile String stagedVersion = "";
    private volatile String stagedStatus = "none";

    @Override
    public void onEnable() {
        config = new PropertiesFile(getDataFolder().toPath().resolve("updater.properties"));
        pluginsDir = getDataFolder().toPath().getParent();
        updatesRoot = pluginsDir.resolve(".updates");
        stagedRoot = updatesRoot.resolve("staged");
        backupRoot = updatesRoot.resolve("backups");
        historyRoot = updatesRoot.resolve("history");
        historyLog = historyRoot.resolve("update-history.log");
        pendingFile = updatesRoot.resolve("pending-update.json");
        defaults();
        command("updates");
        ensureDirs();
        Bukkit.getPluginManager().registerEvents(this, this);
        startupDiagnostics();
        applyPendingAtStartup();
        startReleaseCheck("startup");
    }

    @Override
    public void onDisable() {
        applyPending("shutdown");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUseUpdater(sender)) {
            Text.msg(sender, "&cOnly the Owner rank or console can use the update manager.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "check", "force-check" -> {
                async(sender, "check releases", () -> check(sender));
            }
            case "list" -> async(sender, "list releases", () -> list(sender));
            case "changelog" -> async(sender, "show changelog", () -> changelog(sender, args.length > 1 ? args[1] : "latest"));
            case "stage" -> async(sender, "stage release", () -> stage(sender, args.length > 1 ? args[1] : "latest"));
            case "downgrade" -> async(sender, "stage downgrade", () -> stageDowngrade(sender, args.length > 1 ? args[1] : ""));
            case "verify" -> verify(sender);
            case "approve", "apply" -> approve(sender);
            case "cancel" -> cancel(sender);
            case "history" -> history(sender);
            case "rollback" -> rollback(sender, args.length > 1 ? args[1] : "");
            case "manifest" -> showManifest(sender);
            case "debug", "sources" -> debug(sender);
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canUseUpdater(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of(
                "status", "check", "force-check", "list", "changelog", "stage", "downgrade",
                "verify", "approve", "apply", "cancel", "history", "rollback", "manifest", "debug", "sources"
            ));
            return Tab.complete(args[0], roots.toArray(String[]::new));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stage") || args[0].equalsIgnoreCase("downgrade"))) {
            return Tab.complete(args[1], "latest", currentRelease());
        }
        return List.of();
    }

    private void defaults() {
        defaultValue("github.owner", "mhinnen2310");
        defaultValue("github.repo", "BloodboundSMP");
        defaultValue("github.releaseChannel", "stable");
        defaultValue("github.includePrereleases", "false");
        defaultValue("github.tokenEnv", "MITCHSMP_GITHUB_TOKEN");
        defaultValue("updates.requireSha256", "true");
        defaultValue("updates.requireManifest", "true");
        defaultValue("updates.backupBeforeApply", "true");
        defaultValue("updates.applyOnNextRestartOnly", "true");
        defaultValue("updates.requireManualApproval", "true");
        defaultValue("security.ownerRankOnly", "true");
    }

    private void defaultValue(String key, String value) {
        if (!config.contains(key)) {
            config.set(key, value);
            config.save();
        }
    }

    private void ensureDirs() {
        try {
            Files.createDirectories(stagedRoot);
            Files.createDirectories(backupRoot);
            Files.createDirectories(historyRoot);
        } catch (IOException exception) {
            getLogger().severe("Could not create update directories: " + exception.getMessage());
        }
    }

    private void startupDiagnostics() {
        getLogger().info("MitchSMP UpdateOrchestrator");
        getLogger().info("Current release: " + currentRelease());
        getLogger().info("Channel: " + config.getString("github.releaseChannel", "stable"));
        getLogger().info("Java: " + Runtime.version().feature());
        getLogger().info("Pending update: " + (Files.exists(pendingFile) ? pendingFile.toString() : "none"));
        getLogger().warning("Security: /updates is hard-gated to Owner rank or console. Rank permission grants alone are ignored.");
        historyLine("STARTUP current=" + currentRelease() + " pending=" + Files.exists(pendingFile));
    }

    private void applyPendingAtStartup() {
        if (!Files.exists(pendingFile)) {
            return;
        }
        getLogger().warning("Pending update marker found after startup. This host did not apply updates while stopped.");
        if (applyPending("startup")) {
            getLogger().warning("Pending update files were copied during startup. Restart once more so Paper loads the new jars.");
            historyLine("STARTUP applied pending update; second restart required");
        }
    }

    private void status(CommandSender sender) {
        Text.msg(sender, "&4Bloodbound Updates");
        Text.msg(sender, "&7Current release: &f" + currentRelease());
        Text.msg(sender, "&7GitHub: &f" + owner() + "/" + repo());
        Text.msg(sender, "&7Channel: &f" + config.getString("github.releaseChannel", "stable"));
        Text.msg(sender, "&7Last check: &f" + lastCheck);
        Text.msg(sender, "&7Latest seen: &f" + lastAvailable);
        Text.msg(sender, "&7Staged: &f" + (stagedVersion.isBlank() ? "none" : stagedVersion + " (" + stagedStatus + ")"));
        Text.msg(sender, "&7Pending update: &f" + (Files.exists(pendingFile) ? "yes" : "none"));
    }

    private void check(CommandSender sender) throws Exception {
        ReleaseInfo release = fetchRelease("latest");
        lastCheck = Instant.now().toString();
        lastAvailable = release.tag();
        historyLine(sender.getName() + " checked releases latest=" + release.tag());
        reply(sender, "&6MitchSMP Update Check");
        reply(sender, "&7Current: &f" + currentRelease());
        reply(sender, "&7Latest stable: &f" + release.tag());
        reply(sender, release.tag().equals(currentRelease()) ? "&aServer is up to date." : "&eUpdate available. Use &f/updates stage " + release.tag());
    }

    @EventHandler
    public void onStaffJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isStaffUpdateViewer(player)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> sendStaffUpdateStatus(player), 60L);
    }

    private void startReleaseCheck(String source) {
        Thread thread = new Thread(() -> checkLatestSilently(source), "Bloodbound-UpdateStartupCheck");
        thread.setDaemon(true);
        thread.start();
    }

    private void checkLatestSilently(String source) {
        try {
            ReleaseInfo release = fetchRelease("latest");
            lastCheck = Instant.now().toString();
            lastAvailable = release.tag();
            String current = currentRelease();
            if (release.tag().equals(current)) {
                getLogger().info("Release check (" + source + "): server is up to date on " + current + ".");
            } else {
                getLogger().warning("Release check (" + source + "): update available. Current=" + current + ", latest=" + release.tag() + ".");
            }
            historyLine(source + " release-check current=" + current + " latest=" + release.tag());
        } catch (Exception exception) {
            lastCheck = Instant.now().toString();
            getLogger().warning("Release check (" + source + ") failed: " + exception.getMessage());
            historyLine(source + " release-check failed " + exception.getMessage());
        }
    }

    private void sendStaffUpdateStatus(Player player) {
        if (player == null || !isStaffUpdateViewer(player)) {
            return;
        }
        String latest = lastAvailable == null ? "unknown" : lastAvailable;
        if (latest.equals("unknown")) {
            Text.msg(player, "&7Update status: &echecking GitHub releases...");
            startReleaseCheck("staff-join");
            return;
        }
        String current = currentRelease();
        if (latest.equals(current)) {
            Text.msg(player, "&7Update status: &aBloodbound plugins are up to date &8(" + current + ").");
        } else {
            Text.msg(player, "&7Update status: &eupdate available &f" + latest + " &8(current " + current + ").");
        }
    }

    private boolean isStaffUpdateViewer(Player player) {
        if (player == null) {
            return false;
        }
        MitchRank rank = MitchSMP.ranks().getRank(player.getUniqueId());
        return rank.ordinal() >= MitchRank.HELPER.ordinal()
            || MitchSMP.permissions().has(player, "mitchsmp.updates.admin")
            || MitchSMP.permissions().has(player, "mitchsmp.errors.view");
    }

    private void list(CommandSender sender) throws Exception {
        String body = get(api("/repos/" + owner() + "/" + repo() + "/releases"));
        List<String> tags = matches(body, "\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
        reply(sender, "&6Recent Bloodbound releases:");
        tags.stream().limit(8).forEach(tag -> reply(sender, "&7- &f" + tag));
    }

    private void changelog(CommandSender sender, String requested) throws Exception {
        ReleaseInfo release = fetchRelease(requested);
        reply(sender, "&6" + release.tag() + " changelog:");
        String notes = release.body().isBlank() ? "No release notes found." : release.body();
        for (String line : notes.split("\\R")) {
            if (!line.isBlank()) {
                reply(sender, "&7" + line);
            }
        }
    }

    private void stage(CommandSender sender, String requested) throws Exception {
        ReleaseInfo release = fetchRelease(requested);
        Path target = stagedRoot.resolve(release.tag());
        deleteDirectory(target);
        Files.createDirectories(target);
        Map<String, String> assets = release.assets();
        download(assetUrl(release, MANIFEST), target.resolve(MANIFEST));
        String manifest = Files.readString(target.resolve(MANIFEST), StandardCharsets.UTF_8);
        List<PluginAsset> expected = parseManifest(manifest);
        if (expected.isEmpty()) {
            throw new IllegalStateException("Manifest has no plugin assets.");
        }
        for (PluginAsset asset : expected) {
            download(assetUrl(release, asset.file()), target.resolve(asset.file()));
        }
        stagedVersion = release.tag();
        stagedStatus = "downloaded";
        historyLine(sender.getName() + " staged " + release.tag() + " assets=" + expected.size());
        reply(sender, "&aStaged &f" + release.tag() + "&a with &f" + expected.size() + " &aplugin jars.");
        reply(sender, "&7Next: &f/updates verify");
    }

    private void stageDowngrade(CommandSender sender, String requested) throws Exception {
        if (requested == null || requested.isBlank() || requested.equalsIgnoreCase("latest")) {
            throw new IllegalArgumentException("Usage: /updates downgrade <version-tag>");
        }
        ReleaseInfo release = fetchRelease(requested);
        if (compareRelease(release.tag(), currentRelease()) >= 0) {
            throw new IllegalArgumentException(release.tag() + " is not older than current " + currentRelease() + ". Use /updates stage for normal updates.");
        }
        stage(sender, release.tag());
        historyLine(sender.getName() + " staged downgrade " + release.tag());
        reply(sender, "&eDowngrade staged. Verify and approve only if you intentionally want to move backwards.");
    }

    private void verify(CommandSender sender) {
        try {
            Path target = latestStage();
            String manifest = Files.readString(target.resolve(MANIFEST), StandardCharsets.UTF_8);
            List<PluginAsset> expected = parseManifest(manifest);
            List<String> failures = new ArrayList<>();
            for (PluginAsset asset : expected) {
                Path file = target.resolve(asset.file());
                if (!Files.exists(file)) {
                    failures.add(asset.file() + " missing");
                    continue;
                }
                String actual = sha256(file);
                if (!actual.equalsIgnoreCase(asset.sha256())) {
                    failures.add(asset.file() + " checksum mismatch");
                }
            }
            if (!failures.isEmpty()) {
                stagedStatus = "failed";
                failures.forEach(failure -> Text.msg(sender, "&c" + failure));
                historyLine(sender.getName() + " verification failed " + target.getFileName() + " " + failures);
                Text.msg(sender, "&cUpdate verification failed. Update blocked.");
                return;
            }
            stagedVersion = target.getFileName().toString();
            stagedStatus = "verified";
            historyLine(sender.getName() + " verification passed " + stagedVersion);
            Text.msg(sender, "&aUpdate verification passed for &f" + stagedVersion + "&a.");
            Text.msg(sender, "&7Next: &f/updates approve");
        } catch (Exception exception) {
            Text.msg(sender, "&cVerification failed: " + exception.getMessage());
            historyLine(sender.getName() + " verification error " + exception.getMessage());
        }
    }

    private void approve(CommandSender sender) {
        try {
            Path target = latestStage();
            if (!"verified".equalsIgnoreCase(stagedStatus) && !Files.exists(target.resolve(MANIFEST))) {
                Text.msg(sender, "&cNo verified staged release found. Use /updates stage and /updates verify first.");
                return;
            }
            Path backup = backupCurrentJars(target.getFileName().toString());
            boolean downgrade = compareRelease(target.getFileName().toString(), currentRelease()) < 0;
            String json = "{\n"
                + "  \"targetVersion\": \"" + escape(target.getFileName().toString()) + "\",\n"
                + "  \"approvedBy\": \"" + escape(sender.getName()) + "\",\n"
                + "  \"approvedAt\": \"" + Instant.now() + "\",\n"
                + "  \"applyOnNextRestart\": true,\n"
                + "  \"allowDowngrade\": " + downgrade + ",\n"
                + "  \"requiresBackup\": true,\n"
                + "  \"backupPath\": \"" + escape(backup.toString().replace('\\', '/')) + "\"\n"
                + "}\n";
            Files.createDirectories(updatesRoot);
            Files.writeString(pendingFile, json, StandardCharsets.UTF_8);
            historyLine(sender.getName() + " approved " + target.getFileName() + " backup=" + backup.getFileName());
            Text.msg(sender, "&aUpdate approved for next restart: &f" + target.getFileName());
            if (downgrade) {
                Text.msg(sender, "&eDowngrade flag is set. Restart will intentionally allow this older version.");
            }
            Text.msg(sender, "&7Pending marker: &f" + pendingFile);
            Text.msg(sender, "&7Restart the server. Bloodbound will copy the pending jars during shutdown/startup if your host has no update script.");
        } catch (Exception exception) {
            Text.msg(sender, "&cApprove failed: " + exception.getMessage());
            historyLine(sender.getName() + " approve error " + exception.getMessage());
        }
    }

    private void cancel(CommandSender sender) {
        try {
            Files.deleteIfExists(pendingFile);
            historyLine(sender.getName() + " cancelled pending update");
            Text.msg(sender, "&ePending update cancelled.");
        } catch (IOException exception) {
            Text.msg(sender, "&cCancel failed: " + exception.getMessage());
        }
    }

    private void rollback(CommandSender sender, String requested) {
        try {
            Optional<Path> backup = requested.isBlank() ? latestBackup() : Optional.of(backupRoot.resolve(requested));
            if (backup.isEmpty() || !Files.exists(backup.get())) {
                Text.msg(sender, "&cBackup not found.");
                return;
            }
            String json = "{\n"
                + "  \"rollbackBackup\": \"" + escape(backup.get().toString().replace('\\', '/')) + "\",\n"
                + "  \"approvedBy\": \"" + escape(sender.getName()) + "\",\n"
                + "  \"approvedAt\": \"" + Instant.now() + "\",\n"
                + "  \"applyOnNextRestart\": true\n"
                + "}\n";
            Files.writeString(pendingFile, json, StandardCharsets.UTF_8);
            historyLine(sender.getName() + " scheduled rollback " + backup.get().getFileName());
            Text.msg(sender, "&aRollback scheduled for next restart: &f" + backup.get().getFileName());
        } catch (Exception exception) {
            Text.msg(sender, "&cRollback failed: " + exception.getMessage());
        }
    }

    private void history(CommandSender sender) {
        try {
            if (!Files.exists(historyLog)) {
                Text.msg(sender, "&7No update history yet.");
                return;
            }
            List<String> lines = Files.readAllLines(historyLog, StandardCharsets.UTF_8);
            Text.msg(sender, "&6Recent update history:");
            lines.stream().skip(Math.max(0, lines.size() - 10)).forEach(line -> Text.msg(sender, "&7- &f" + line));
        } catch (IOException exception) {
            Text.msg(sender, "&cCould not read history: " + exception.getMessage());
        }
    }

    private void showManifest(CommandSender sender) {
        try {
            Path target = latestStage();
            Text.msg(sender, "&7Manifest: &f" + target.resolve(MANIFEST));
        } catch (Exception exception) {
            Text.msg(sender, "&cNo staged manifest found.");
        }
    }

    private void debug(CommandSender sender) {
        Text.msg(sender, "&6Update sources:");
        Text.msg(sender, "&7GitHub API: &f" + api("/repos/" + owner() + "/" + repo() + "/releases/latest"));
        Text.msg(sender, "&7Staging: &f" + stagedRoot);
        Text.msg(sender, "&7Backups: &f" + backupRoot);
        Text.msg(sender, "&7History: &f" + historyLog);
        Text.msg(sender, "&7Token env: &f" + config.getString("github.tokenEnv", "MITCHSMP_GITHUB_TOKEN"));
    }

    private void usage(CommandSender sender) {
        Text.msg(sender, "&cUsage: /updates <status|check|list|changelog|stage|downgrade|verify|approve|cancel|history|rollback>");
    }

    private ReleaseInfo fetchRelease(String requested) throws Exception {
        String path = requested == null || requested.equalsIgnoreCase("latest")
            ? "/repos/" + owner() + "/" + repo() + "/releases/latest"
            : "/repos/" + owner() + "/" + repo() + "/releases/tags/" + requested;
        String body = get(api(path));
        String tag = first(body, "\"tag_name\"\\s*:\\s*\"([^\"]+)\"").orElse("unknown");
        boolean prerelease = body.contains("\"prerelease\": true");
        if (prerelease && !bool("github.includePrereleases", false)) {
            throw new IllegalStateException("Latest release is prerelease and includePrereleases=false.");
        }
        String notes = first(body, "\"body\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").map(this::unescapeJson).orElse("");
        Map<String, String> assets = parseAssets(body);
        return new ReleaseInfo(tag, notes, assets);
    }

    private Map<String, String> parseAssets(String body) {
        Map<String, String> assets = new HashMap<>();
        Matcher pairMatcher = ASSET_PAIR_PATTERN.matcher(body);
        while (pairMatcher.find()) {
            assets.put(unescapeJson(pairMatcher.group(1)), unescapeJson(pairMatcher.group(2)));
        }
        Matcher matcher = ASSET_OBJECT_PATTERN.matcher(body);
        while (matcher.find()) {
            String object = matcher.group();
            Optional<String> name = first(object, "\"name\"\\s*:\\s*\"([^\"]+)\"").map(this::unescapeJson);
            Optional<String> url = first(object, "\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").map(this::unescapeJson);
            if (name.isPresent() && url.isPresent()) {
                assets.put(name.get(), url.get());
            }
        }
        return assets;
    }

    private String assetUrl(ReleaseInfo release, String file) {
        String parsed = release.assets().get(file);
        if (parsed != null && !parsed.isBlank()) {
            return parsed;
        }
        return "https://github.com/" + owner() + "/" + repo() + "/releases/download/" + release.tag() + "/" + file;
    }

    private List<PluginAsset> parseManifest(String manifest) {
        List<PluginAsset> result = new ArrayList<>();
        Matcher matcher = MANIFEST_PLUGIN_PATTERN.matcher(manifest);
        while (matcher.find()) {
            result.add(new PluginAsset(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4).toLowerCase(Locale.ROOT)));
        }
        return result;
    }

    private Path latestStage() throws IOException {
        if (!stagedVersion.isBlank()) {
            Path path = stagedRoot.resolve(stagedVersion);
            if (Files.exists(path)) {
                return path;
            }
        }
        try (Stream<Path> stream = Files.list(stagedRoot)) {
            return stream.filter(Files::isDirectory)
                .max(Comparator.comparing(path -> path.toFile().lastModified()))
                .orElseThrow(() -> new IOException("No staged release found."));
        }
    }

    private Optional<Path> latestBackup() throws IOException {
        if (!Files.exists(backupRoot)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(backupRoot)) {
            return stream.filter(Files::isDirectory).max(Comparator.comparing(path -> path.toFile().lastModified()));
        }
    }

    private boolean applyPending(String trigger) {
        if (pendingFile == null || !Files.exists(pendingFile)) {
            return false;
        }
        try {
            String pending = Files.readString(pendingFile, StandardCharsets.UTF_8);
            Optional<String> rollback = first(pending, "\"rollbackBackup\"\\s*:\\s*\"([^\"]+)\"");
            if (rollback.isPresent()) {
                applyRollback(Path.of(rollback.get()), trigger);
                return true;
            }

            String targetVersion = first(pending, "\"targetVersion\"\\s*:\\s*\"([^\"]+)\"")
                .orElseThrow(() -> new IOException("pending update has no targetVersion"));
            boolean allowDowngrade = pending.contains("\"allowDowngrade\": true");
            if (compareRelease(targetVersion, currentRelease()) < 0 && !allowDowngrade) {
                getLogger().warning("Ignoring older pending update " + targetVersion + " because updater is already " + currentRelease() + ".");
                historyLine(trigger + " ignored older pending update " + targetVersion + " current=" + currentRelease());
                Files.deleteIfExists(pendingFile);
                return false;
            }

            Path target = stagedRoot.resolve(targetVersion);
            applyStaged(target, targetVersion, trigger);
            return true;
        } catch (Exception exception) {
            getLogger().severe("Could not apply pending update during " + trigger + ": " + exception.getMessage());
            historyLine(trigger + " apply error " + exception.getMessage());
            return false;
        }
    }

    private void applyStaged(Path target, String targetVersion, String trigger) throws IOException {
        Path manifestPath = target.resolve(MANIFEST);
        if (!Files.exists(manifestPath)) {
            throw new IOException("staged manifest missing for " + targetVersion);
        }
        String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);
        List<PluginAsset> expected = parseManifest(manifest);
        if (expected.isEmpty()) {
            throw new IOException("staged manifest has no plugin assets");
        }

        List<String> failures = new ArrayList<>();
        for (PluginAsset asset : expected) {
            Path stagedJar = target.resolve(asset.file());
            if (!Files.exists(stagedJar)) {
                failures.add(asset.file() + " missing");
            } else if (!sha256(stagedJar).equalsIgnoreCase(asset.sha256())) {
                failures.add(asset.file() + " checksum mismatch");
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("staged verification failed: " + failures);
        }

        Path backup = backupCurrentJars(targetVersion + "_apply_" + trigger);
        for (PluginAsset asset : expected) {
            removeOldPluginJars(asset);
            Files.copy(target.resolve(asset.file()), pluginsDir.resolve(asset.file()), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(pendingFile);
        stagedVersion = targetVersion;
        stagedStatus = "applied";
        getLogger().warning("Applied pending update " + targetVersion + " during " + trigger + ". Backup: " + backup);
        historyLine(trigger + " applied " + targetVersion + " backup=" + backup.getFileName());
    }

    private void removeOldPluginJars(PluginAsset asset) throws IOException {
        String expectedFile = asset.file();
        String prefix = asset.name() + "-";
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            for (Path jar : stream.filter(path -> isOldPluginJar(path, prefix, expectedFile)).toList()) {
                Files.deleteIfExists(jar);
                historyLine("removed old plugin jar " + jar.getFileName());
            }
        }
    }

    private boolean isOldPluginJar(Path path, String prefix, String expectedFile) {
        String file = path.getFileName().toString();
        return file.startsWith(prefix)
            && file.endsWith(".jar")
            && !file.equals(expectedFile);
    }

    private void applyRollback(Path backup, String trigger) throws IOException {
        Path jars = backup.resolve("plugins");
        if (!Files.isDirectory(jars)) {
            throw new IOException("rollback backup has no plugins directory: " + backup);
        }
        try (Stream<Path> stream = Files.list(jars)) {
            for (Path jar : stream.filter(path -> path.getFileName().toString().endsWith(".jar")).toList()) {
                String file = jar.getFileName().toString();
                String prefix = pluginPrefixFromJar(file);
                if (!prefix.isBlank()) {
                    removeOldPluginJars(new PluginAsset(prefix.substring(0, prefix.length() - 1), file, "rollback", ""));
                }
            }
        }
        try (Stream<Path> stream = Files.list(jars)) {
            for (Path jar : stream.filter(path -> path.getFileName().toString().endsWith(".jar")).toList()) {
                Files.copy(jar, pluginsDir.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.deleteIfExists(pendingFile);
        getLogger().warning("Applied rollback during " + trigger + ": " + backup);
        historyLine(trigger + " applied rollback " + backup.getFileName());
    }

    private String pluginPrefixFromJar(String file) {
        Matcher matcher = Pattern.compile("^(MitchSMP-[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)-\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9]+)?\\.jar$").matcher(file);
        return matcher.find() ? matcher.group(1) + "-" : "";
    }

    private Path backupCurrentJars(String targetVersion) throws IOException {
        Path backup = backupRoot.resolve(BACKUP_FORMAT.format(Instant.now()) + "_before_" + targetVersion);
        Path jars = backup.resolve("plugins");
        Files.createDirectories(jars);
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            for (Path jar : stream.filter(path -> path.getFileName().toString().startsWith("MitchSMP-") && path.getFileName().toString().endsWith(".jar")).toList()) {
                Files.copy(jar, jars.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.writeString(backup.resolve("manifest.json"), "{\"createdAt\":\"" + Instant.now() + "\",\"target\":\"" + escape(targetVersion) + "\"}\n", StandardCharsets.UTF_8);
        return backup;
    }

    private void download(String url, Path target) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        token().ifPresent(token -> builder.header("Authorization", "Bearer " + token));
        HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed " + response.statusCode() + " for " + url);
        }
        Files.write(target, response.body());
    }

    private String get(String url) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET().header("Accept", "application/vnd.github+json");
        token().ifPresent(token -> builder.header("Authorization", "Bearer " + token));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private void async(CommandSender sender, String label, CheckedRunnable task) {
        Text.msg(sender, "&7Starting update task: &f" + label + "&7...");
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                historyLine(sender.getName() + " " + label + " error " + exception.getMessage());
                reply(sender, "&cUpdate task failed: " + exception.getMessage());
            }
        }, "Bloodbound-UpdateOrchestrator");
        thread.setDaemon(true);
        thread.start();
    }

    private void reply(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(this, () -> Text.msg(sender, message));
    }

    private void command(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
            getCommand(name).setTabCompleter(this);
        }
    }

    private boolean canUseUpdater(CommandSender sender) {
        if (!bool("security.ownerRankOnly", true)) {
            return MitchSMP.permissions().has(sender, "mitchsmp.updates.admin");
        }
        if (!(sender instanceof Player player)) {
            return true;
        }
        return MitchSMP.ranks().getRank(player.getUniqueId()) == MitchRank.OWNER;
    }

    private String currentRelease() {
        return getDescription() == null ? "unknown" : "v" + getDescription().getVersion();
    }

    private int compareRelease(String left, String right) {
        int[] a = releaseParts(left);
        int[] b = releaseParts(right);
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int av = index < a.length ? a[index] : 0;
            int bv = index < b.length ? b[index] : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    private int[] releaseParts(String release) {
        String normalized = release == null ? "" : release.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        String[] raw = normalized.split("[^0-9]+");
        List<Integer> parts = new ArrayList<>();
        for (String part : raw) {
            if (!part.isBlank()) {
                try {
                    parts.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {
                    parts.add(0);
                }
            }
        }
        if (parts.isEmpty()) {
            return new int[] {0};
        }
        int[] result = new int[parts.size()];
        for (int index = 0; index < parts.size(); index++) {
            result[index] = parts.get(index);
        }
        return result;
    }

    private String owner() {
        return config.getString("github.owner", "mhinnen2310");
    }

    private String repo() {
        return config.getString("github.repo", "BloodboundSMP");
    }

    private boolean bool(String key, boolean fallback) {
        String value = config.getString(key, String.valueOf(fallback));
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("1");
    }

    private String api(String path) {
        return "https://api.github.com" + path;
    }

    private Optional<String> token() {
        String env = config.getString("github.tokenEnv", "MITCHSMP_GITHUB_TOKEN");
        String token = System.getenv(env);
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token.trim());
    }

    private List<String> matches(String input, String regex) {
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile(regex).matcher(input);
        while (matcher.find()) {
            values.add(unescapeJson(matcher.group(1)));
        }
        return values;
    }

    private Optional<String> first(String input, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(input);
        return matcher.find() ? Optional.of(unescapeJson(matcher.group(1))) : Optional.empty();
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IOException("Could not hash " + file.getFileName(), exception);
        }
    }

    private void historyLine(String line) {
        try {
            Files.createDirectories(historyRoot);
            Files.writeString(historyLog, "[" + Instant.now() + "] " + line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            getLogger().warning("Could not write update history: " + exception.getMessage());
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private String escape(String input) {
        return input == null ? "" : input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String input) {
        return input == null ? "" : input.replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }

    private record ReleaseInfo(String tag, String body, Map<String, String> assets) {
    }

    private record PluginAsset(String name, String file, String version, String sha256) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
