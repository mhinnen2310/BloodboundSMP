package nl.mitchsmp.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class BoundedRecordStore {
    private final Path path;
    private final int maxRecords;

    public BoundedRecordStore(Path path, int maxRecords) {
        this.path = path;
        this.maxRecords = Math.max(10, maxRecords);
    }

    public synchronized void append(String type, String payload) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String row = Instant.now().toEpochMilli() + "\t" + clean(type) + "\t" + clean(payload) + System.lineSeparator();
            Files.writeString(path, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            trimIfNeeded();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not append to " + path, exception);
        }
    }

    public synchronized List<String> recent(int limit) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - Math.max(1, limit));
            List<String> result = new ArrayList<>(lines.subList(from, lines.size()));
            java.util.Collections.reverse(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clear " + path, exception);
        }
    }

    private void trimIfNeeded() throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() <= maxRecords) {
            return;
        }
        int from = Math.max(0, lines.size() - maxRecords);
        Files.write(path, lines.subList(from, lines.size()), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    private static String clean(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
