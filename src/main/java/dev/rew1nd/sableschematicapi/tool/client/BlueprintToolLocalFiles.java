package dev.rew1nd.sableschematicapi.tool.client;

import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class BlueprintToolLocalFiles {
    private static final String DIRECTORY = "Sable-Schematics";
    private static final String EXTENSION = ".nbt";

    private BlueprintToolLocalFiles() {
    }

    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(DIRECTORY);
    }

    public static List<Entry> list() throws IOException {
        final Path directory = directory();
        Files.createDirectories(directory);
        try (final Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION))
                    .map(Entry::of)
                    .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public static void save(final String name, final byte[] data) throws IOException {
        final Path path = path(name);
        Files.createDirectories(path.getParent());
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static byte[] read(final Entry entry) throws IOException {
        final byte[] data = Files.readAllBytes(entry.path());
        if (data.length > SableSchematicApiPackets.MAX_BLUEPRINT_BYTES) {
            throw new IOException("Blueprint file is larger than " + SableSchematicApiPackets.MAX_BLUEPRINT_BYTES + " bytes.");
        }
        return data;
    }

    private static Path path(final String name) throws IOException {
        final Path directory = directory().toAbsolutePath().normalize();
        final String fileName = fileName(name);
        final Path path = directory.resolve(fileName).normalize();
        if (!path.startsWith(directory)) {
            throw new IOException("Invalid blueprint path: " + name);
        }
        return path;
    }

    private static String fileName(final String name) throws IOException {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - EXTENSION.length());
        }
        if (normalized.isEmpty() || normalized.contains("\\") || normalized.contains("/") || normalized.contains("..")) {
            throw new IOException("Invalid blueprint name: " + name);
        }
        return normalized + EXTENSION;
    }

    public record Entry(String name, Path path) {
        static Entry of(final Path path) {
            final String fileName = path.getFileName().toString();
            final String name = fileName.endsWith(EXTENSION) ? fileName.substring(0, fileName.length() - EXTENSION.length()) : fileName;
            return new Entry(name, path);
        }
    }
}
