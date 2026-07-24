package dev.rew1nd.sableschematicapi.survival.film;

import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintWorldStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Permanent, content-addressed blueprint objects owned by a world save. */
public final class BlueprintObjectStore {
    private static final String OBJECTS = "objects";
    private static final String EXTENSION = ".nbt";

    private BlueprintObjectStore() {
    }

    public static Path directory(final MinecraftServer server) {
        return BlueprintWorldStore.root(server).resolve(OBJECTS);
    }

    public static Path path(final MinecraftServer server, final String sha256) throws IOException {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid blueprint object SHA-256.");
        }
        final Path root = directory(server).toAbsolutePath().normalize();
        final Path resolved = root.resolve(sha256 + EXTENSION).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Blueprint object path escapes its directory.");
        }
        return resolved;
    }

    public static boolean exists(final MinecraftServer server, final String sha256) {
        try {
            return Files.isRegularFile(path(server, sha256));
        } catch (final IOException exception) {
            return false;
        }
    }

    public static byte[] read(final MinecraftServer server, final String sha256) throws IOException {
        final Path path = path(server, sha256);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Recorded blueprint object is missing.");
        }
        verify(path, sha256);
        return Files.readAllBytes(path);
    }

    static void storeIfAbsent(final MinecraftServer server,
                              final String sha256,
                              final byte[] data) throws IOException {
        final Path target = path(server, sha256);
        if (Files.isRegularFile(target)) {
            verify(target, sha256);
            return;
        }
        Files.createDirectories(target.getParent());
        final Path temporary = Files.createTempFile(target.getParent(), ".freeze-", ".part");
        try {
            Files.write(temporary, data, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (final java.nio.file.AtomicMoveNotSupportedException exception) {
                try {
                    Files.move(temporary, target);
                } catch (final java.nio.file.FileAlreadyExistsException ignored) {
                    // Another job stored the same immutable object concurrently.
                }
            } catch (final java.nio.file.FileAlreadyExistsException ignored) {
                // Content address makes an existing file the desired result.
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        verify(target, sha256);
    }

    public static void verify(final MinecraftServer server, final String sha256) throws IOException {
        verify(path(server, sha256), sha256);
    }

    private static void verify(final Path path, final String expectedSha256) throws IOException {
        if (!expectedSha256.equals(BlueprintWorldStore.sha256(path))) {
            throw new IOException("Recorded blueprint object hash does not match its address.");
        }
        final CompoundTag root = BlueprintWorldStore.readRoot(path);
        if (root.contains("preview", Tag.TAG_COMPOUND)) {
            throw new IOException("Recorded blueprint object unexpectedly contains preview data.");
        }
        try {
            dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.loadWithDiagnostics(root);
        } catch (final RuntimeException exception) {
            throw new IOException("Recorded blueprint object is not a valid Sable blueprint.", exception);
        }
    }
}
