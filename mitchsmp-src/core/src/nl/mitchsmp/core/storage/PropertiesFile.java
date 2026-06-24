package nl.mitchsmp.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

public final class PropertiesFile {
    private final Path path;
    private final Properties properties = new Properties();

    public PropertiesFile(Path path) {
        this.path = path;
        load();
    }

    public synchronized void load() {
        properties.clear();
        if (!Files.exists(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load " + path, exception);
        }
    }

    public synchronized void save() {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "MitchSMP data");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save " + path, exception);
        }
    }

    public synchronized String getString(String key, String fallback) {
        return properties.getProperty(key, fallback);
    }

    public synchronized int getInt(String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public synchronized long getLong(String key, long fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public synchronized double getDouble(String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public synchronized void set(String key, Object value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, String.valueOf(value));
        }
    }

    public synchronized boolean contains(String key) {
        return properties.containsKey(key);
    }

    public synchronized Set<String> keys() {
        return Set.copyOf(properties.stringPropertyNames());
    }
}
