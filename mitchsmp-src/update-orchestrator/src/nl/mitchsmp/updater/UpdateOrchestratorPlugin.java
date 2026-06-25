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
import nl.mitchsmp.core.storage.PropertiesFile;
import nl.mitchsmp.core.util.Tab;
import nl.mitchsmp.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class UpdateOrchestratorPlugin extends JavaPlugin implements TabCompleter {
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
        startupDiagnostics();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!can(sender, "mitchsmp.updates.view")) {
            Text.msg(sender, "&cYou do not have permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "check", "force-check" -> {
                if (!can(sender, "mitchsmp.updates.check")) {
                    Text.msg(sender, "&cYou do not have permission.");
                    return true;
                }
                async(sender, "check releases", () -> check(sender));
            }
            case "list" -> async(sender, "list releases", () -> list(sender));
            case "changelog" -> async(sender, "show changelog", () -> changelog(sender, args.length > 1 ? args[1] : "latest"));
            case "stage" -> {
                if (!can(sender, "mitchsmp.updates.stage")) {
                    Text.msg(sender, "&cYou do not have permission.");
                    return true;
                }
                async(sender, "stage release", () -> stage(sender, args.length > 1 ? args[1] : "latest"));
            }
            case "verify" -> {
                if (!can(sender, "mitchsmp.updates.verify")) {
                    Text.msg(sender, "&cYou do not have permission.");
                    return true;
                }
                verify(sender);
            }
            case "approve", "apply" -> {
                if (!can(sender, "mitchsmp.updates.approve")) {
                    Text.msg(sender, "&cYou do not have permission.");
                    return true;
                }
                approve(sender);
            }
            case "cancel" -> {
                if (!can(sender, "mitchsmp.updates.approve")) {
                    Text.msg(sender, "&cYou do not have permission.");
                    return true;
                }
                cancel(sender);
            }
            case "history" -> history(sender);
            case "rollback" -> {
                if (!can(sender, "mitchsmp.updates.rollback")) {
                    Text.msg(sender, "&cYou do not have permission.");
                    return true;
                }
                rollback(sender, args.length > 1 ? args[1] : "");
            }
            case "manifest" -> showManifest(sender);
            case "debug", "sources" -> debug(sender);
            default -> usage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("status", "check", "list", "changelog", "history"));
            if (can(sender, "mitchsmp.updates.stage")) {
                roots.add("stage");
            }
            if (can(sender, "mitchsmp.updates.verify")) {
                roots.add("verify");
            }
            if (can(sender, "mitchsmp.updates.approve")) {
                roots.addAll(List.of("approve", "apply", "cancel"));
            }
            if (can(sender, "mitchsmp.updates.rollback")) {
                roots.add("rollback");
            }
            if (can(sender, "mitchsmp.updates.debug")) {
                roots.addAll(List.of("manifest", "debug", "sources", "force-check"));
            }
            return Tab.complete(args[0], roots.toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stage")) {
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
        historyLine("STARTUP current=" + currentRelease() + " pending=" + Files.exists(pendingFile));
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
            String json = "{\n"
                + "  \"targetVersion\": \"" + escape(target.getFileName().toString()) + "\",\n"
                + "  \"approvedBy\": \"" + escape(sender.getName()) + "\",\n"
                + "  \"approvedAt\": \"" + Instant.now() + "\",\n"
                + "  \"applyOnNextRestart\": true,\n"
                + "  \"requiresBackup\": true,\n"
                + "  \"backupPath\": \"" + escape(backup.toString().replace('\\', '/')) + "\"\n"
                + "}\n";
            Files.createDirectories(updatesRoot);
            Files.writeString(pendingFile, json, StandardCharsets.UTF_8);
            historyLine(sender.getName() + " approved " + target.getFileName() + " backup=" + backup.getFileName());
            Text.msg(sender, "&aUpdate approved for next restart: &f" + target.getFileName());
            Text.msg(sender, "&7Pending marker: &f" + pendingFile);
            Text.msg(sender, "&cNo hot reload was performed. Apply with the startup/update script while the server is stopped.");
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
        Text.msg(sender, "&cUsage: /updates <status|check|list|changelog|stage|verify|approve|cancel|history|rollback>");
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

    private boolean can(CommandSender sender, String permission) {
        return MitchSMP.permissions().has(sender, permission);
    }

    private String currentRelease() {
        return getDescription() == null ? "unknown" : "v" + getDescription().getVersion();
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
