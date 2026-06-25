package nl.mitchsmp.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;

public final class PropertiesFile {
    private static final boolean SQLITE_AVAILABLE = sqliteAvailable();
    private final Path path;
    private final Path sqlitePath;
    private final Properties properties = new Properties();
    private final boolean sqliteAvailable;

    public PropertiesFile(Path path) {
        this.path = path;
        this.sqlitePath = path.resolveSibling(path.getFileName() + ".sqlite.db");
        this.sqliteAvailable = SQLITE_AVAILABLE;
        load();
    }

    public static String backendName() {
        return SQLITE_AVAILABLE ? "SQLite" : "crash-safe properties fallback";
    }

    public static boolean sqliteEnabled() {
        return SQLITE_AVAILABLE;
    }

    public synchronized void load() {
        properties.clear();
        if (sqliteAvailable) {
            loadSqlite();
            return;
        }
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
        if (sqliteAvailable) {
            saveSqlite();
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Path bak = path.resolveSibling(path.getFileName() + ".bak");
            try (OutputStream output = Files.newOutputStream(tmp)) {
                properties.store(output, "MitchSMP data");
            }
            if (Files.exists(path)) {
                Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
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
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : fallback;
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

    private void loadSqlite() {
        try {
            Path parent = sqlitePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean fresh = !Files.exists(sqlitePath);
            try (Connection connection = openSqlite()) {
                ensureSchema(connection);
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT key, value FROM kv")) {
                    while (result.next()) {
                        properties.setProperty(result.getString(1), result.getString(2));
                    }
                }
                if (fresh && properties.isEmpty() && Files.exists(path)) {
                    importLegacyProperties(connection);
                }
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Could not load SQLite store " + sqlitePath, exception);
        }
    }

    private void saveSqlite() {
        try {
            Path parent = sqlitePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = openSqlite()) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try (Statement clear = connection.createStatement()) {
                    clear.executeUpdate("DELETE FROM kv");
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO kv(key, value) VALUES(?, ?)")) {
                    for (String key : properties.stringPropertyNames()) {
                        insert.setString(1, key);
                        insert.setString(2, properties.getProperty(key, ""));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Could not save SQLite store " + sqlitePath, exception);
        }
    }

    private void importLegacyProperties(Connection connection) throws IOException, SQLException {
        Properties legacy = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            legacy.load(input);
        }
        if (legacy.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO kv(key, value) VALUES(?, ?)")) {
            for (String key : legacy.stringPropertyNames()) {
                String value = legacy.getProperty(key, "");
                properties.setProperty(key, value);
                insert.setString(1, key);
                insert.setString(2, value);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Connection openSqlite() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
        }
    }

    private static boolean sqliteAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
