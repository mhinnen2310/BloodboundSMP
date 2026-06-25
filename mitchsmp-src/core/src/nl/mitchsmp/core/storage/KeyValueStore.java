package nl.mitchsmp.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeyValueStore {
    private final Path path;
    private final Map<String, String> values = new LinkedHashMap<>();

    public KeyValueStore(Path path) {
        this.path = path;
        load();
    }

    public synchronized void load() {
        values.clear();
        if (!Files.exists(path)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int split = line.indexOf('\t');
                if (split <= 0) {
                    continue;
                }
                values.put(line.substring(0, split), line.substring(split + 1));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + path, exception);
        }
    }

    public synchronized String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    public synchronized boolean getBoolean(String key, boolean fallback) {
        String raw = values.get(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }

    public synchronized void set(String key, Object value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, clean(String.valueOf(value)));
        }
        save();
    }

    private void save() {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = values.entrySet().stream()
                .map(entry -> entry.getKey() + "\t" + entry.getValue())
                .toList();
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + path, exception);
        }
    }

    private static String clean(String input) {
        return input.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
