package dev.rew1nd.sableschematicapi.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Locale;

public final class SableBlueprintFiles {
    private static final String DIRECTORY = "sable_blueprints";
    private static final String EXTENSION = ".nbt";

    private SableBlueprintFiles() {
    }

    public static Path directory(final MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY);
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

    public static void save(final MinecraftServer server, final String name, final SableBlueprint blueprint) throws IOException {
        final Path path = path(server, name);
        Files.createDirectories(path.getParent());

        try (final OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            NbtIo.writeCompressed(blueprint.save(), stream);
        }
    }

    public static SableBlueprint load(final MinecraftServer server, final String name) throws IOException {
        return loadWithDiagnostics(server, name).blueprint();
    }

    public static SableBlueprintDecodeResult loadWithDiagnostics(final MinecraftServer server, final String name) throws IOException {
        final Path path = path(server, name);

        try (final InputStream stream = Files.newInputStream(path, StandardOpenOption.READ)) {
            final CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            if (tag == null) {
                throw new IOException("Blueprint payload is empty.");
            }
            return SableBlueprint.loadWithDiagnostics(tag);
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
        String normalized = name.trim().toLowerCase(Locale.ROOT);

        if (normalized.endsWith(EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - EXTENSION.length());
        }

        if (normalized.isEmpty() || normalized.contains("\\") || normalized.contains("/") || normalized.contains("..")) {
            throw new IOException("Invalid blueprint name: " + name);
        }

        return normalized + EXTENSION;
    }
}
