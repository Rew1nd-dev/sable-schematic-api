package dev.rew1nd.sableschematicapi.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SableBlueprintRootFiles {
    private static final String DIRECTORY = "Sable-Schematics";
    private static final String EXTENSION = ".nbt";

    private SableBlueprintRootFiles() {
    }

    public static Path directory(final MinecraftServer server) {
        return server.getServerDirectory().resolve(DIRECTORY);
    }

    public static Path path(final MinecraftServer server, final String name) throws IOException {
        final Path directory = directory(server).toAbsolutePath().normalize();
        final String fileName = fileName(name);
        final Path path = directory.resolve(fileName).normalize();

        if (!path.startsWith(directory)) {
            throw new IOException("Invalid blueprint path: " + name);
        }

        return path;
    }

    public static SableBlueprint load(final MinecraftServer server, final String name) throws IOException {
        final Path path = path(server, name);

        try (final InputStream stream = Files.newInputStream(path, StandardOpenOption.READ)) {
            final CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            return SableBlueprint.load(tag);
        }
    }

    public static Set<String> list(final MinecraftServer server) throws IOException {
        final Path directory = directory(server);

        if (!Files.isDirectory(directory)) {
            return Set.of();
        }

        try (final Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION))
                    .map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .collect(Collectors.toSet());
        }
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
}
