package dev.rew1nd.sableschematicapi.survival.manuscript;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.camera.CameraConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** World-owned mutable blueprint alias storage used by manuscripts. */
public final class BlueprintWorldStore {
    private static final String ROOT = "Sable-Schematics";
    private static final String UPLOAD = "upload";
    private static final String STAGING = ".staging";
    private static final String EXTENSION = ".nbt";
    private static final long MAX_DECODE_BYTES = 256L * 1024L * 1024L;

    private BlueprintWorldStore() {
    }

    public static Path root(final MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(ROOT);
    }

    public static Path uploadPath(final MinecraftServer server,
                                  final UUID ownerId,
                                  final String blueprintName) throws IOException {
        final Path uploadRoot = root(server).resolve(UPLOAD).toAbsolutePath().normalize();
        final String encodedName = encodedFileName(blueprintName);
        final Path resolved = uploadRoot.resolve(ownerId.toString()).resolve(encodedName).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IOException("Blueprint path escapes the upload directory.");
        }
        return resolved;
    }

    public static Path newStagingPath(final MinecraftServer server,
                                      final UUID playerId,
                                      final UUID transferId) throws IOException {
        final Path stagingRoot = root(server).resolve(STAGING).toAbsolutePath().normalize();
        final Path playerRoot = stagingRoot.resolve(playerId.toString()).normalize();
        final Path resolved = playerRoot.resolve(transferId + ".part").normalize();
        if (!resolved.startsWith(stagingRoot)) {
            throw new IOException("Blueprint staging path escapes the staging directory.");
        }
        Files.createDirectories(playerRoot);
        Files.deleteIfExists(resolved);
        return Files.createFile(resolved);
    }

    public static BlueprintDocument inspect(final Path path) throws IOException {
        ensureBoundedFile(path);
        final String sha256 = sha256(path);
        final CompoundTag root = readRoot(path);
        final SableBlueprint blueprint;
        try {
            blueprint = SableBlueprint.loadWithDiagnostics(root).blueprint();
        } catch (final RuntimeException exception) {
            throw new IOException("Invalid Sable blueprint.", exception);
        }
        final Metadata metadata = metadata(root);
        return new BlueprintDocument(
                sha256,
                metadata.author(),
                metadata.description(),
                BlueprintSummary.of(blueprint),
                blueprint.preview()
        );
    }

    public static BlueprintDocument inspect(final MinecraftServer server,
                                            final BlueprintAlias alias) throws IOException {
        return inspect(uploadPath(server, alias.ownerId(), alias.blueprintName()));
    }

    public static CompoundTag readRoot(final Path path) throws IOException {
        ensureBoundedFile(path);
        final long decodeLimit = Math.min(
                MAX_DECODE_BYTES,
                Math.max(16L * 1024L * 1024L, (long) CameraConfig.MAX_MANUSCRIPT_UPLOAD_BYTES.get() * 8L)
        );
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ))) {
            final CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.create(decodeLimit));
            if (root == null) {
                throw new IOException("Blueprint file is empty.");
            }
            return root;
        } catch (final RuntimeException exception) {
            throw new IOException("Blueprint NBT exceeds its decode limit or is malformed.", exception);
        }
    }

    public static byte[] readBytes(final MinecraftServer server,
                                   final BlueprintAlias alias) throws IOException {
        final Path path = uploadPath(server, alias.ownerId(), alias.blueprintName());
        ensureBoundedFile(path);
        return Files.readAllBytes(path);
    }

    public static String sha256(final Path path) throws IOException {
        final MessageDigest digest = digest();
        try (InputStream input = new DigestInputStream(
                new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ)),
                digest
        )) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String currentSha256(final MinecraftServer server,
                                       final BlueprintAlias alias) throws IOException {
        final Path path = uploadPath(server, alias.ownerId(), alias.blueprintName());
        return Files.isRegularFile(path) ? sha256(path) : "";
    }

    public static void commit(final Path staging,
                              final MinecraftServer server,
                              final BlueprintAlias alias) throws IOException {
        inspect(staging);
        final Path target = uploadPath(server, alias.ownerId(), alias.blueprintName());
        Files.createDirectories(target.getParent());
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException exception) {
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteStaging(final Path staging) {
        if (staging == null) {
            return;
        }
        try {
            final Path stagingRoot = staging.getParent() == null ? null : staging.getParent().getParent();
            final Path normalized = staging.toAbsolutePath().normalize();
            if (stagingRoot != null && normalized.startsWith(stagingRoot.toAbsolutePath().normalize())) {
                Files.deleteIfExists(normalized);
            }
        } catch (final IOException ignored) {
        }
    }

    private static void ensureBoundedFile(final Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Blueprint file does not exist.");
        }
        final long size = Files.size(path);
        if (size <= 0L || size > CameraConfig.MAX_MANUSCRIPT_UPLOAD_BYTES.get()) {
            throw new IOException("Blueprint file size is outside the configured manuscript limit.");
        }
    }

    private static String encodedFileName(final String rawName) throws IOException {
        String name = rawName == null ? "" : rawName.trim();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        if (name.isBlank() || name.length() > 128 || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new IOException("Invalid blueprint name.");
        }
        return URLEncoder.encode(name.toLowerCase(java.util.Locale.ROOT), StandardCharsets.UTF_8)
                .replace("+", "%20") + EXTENSION;
    }

    private static Metadata metadata(final CompoundTag root) {
        if (!root.contains("global_extra_data", Tag.TAG_COMPOUND)) {
            return Metadata.EMPTY;
        }
        final CompoundTag global = root.getCompound("global_extra_data");
        if (!global.contains("sable_schematic_api", Tag.TAG_COMPOUND)) {
            return Metadata.EMPTY;
        }
        final CompoundTag modData = global.getCompound("sable_schematic_api");
        if (!modData.contains("metadata", Tag.TAG_COMPOUND)) {
            return Metadata.EMPTY;
        }
        final CompoundTag metadata = modData.getCompound("metadata");
        return new Metadata(metadata.getString("author"), metadata.getString("description"));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record Metadata(String author, String description) {
        private static final Metadata EMPTY = new Metadata("", "");

        private Metadata {
            author = author == null ? "" : author;
            description = description == null ? "" : description;
        }
    }
}
