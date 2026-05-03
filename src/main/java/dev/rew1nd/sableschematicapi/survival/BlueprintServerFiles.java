package dev.rew1nd.sableschematicapi.survival;

import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Server-side file storage for uploaded survival blueprints.
 *
 * <p>Files are stored under {@code <server_dir>/Sable-Schematics/uploaded/<player>/<name>.nbt}.
 * The actual blueprint data lives on disk; item stacks only carry a reference.</p>
 */
public final class BlueprintServerFiles {
    private static final String DIRECTORY = "Sable-Schematics";
    private static final String UPLOADED = "uploaded";
    private static final String EXTENSION = ".nbt";

    private BlueprintServerFiles() {
    }

    public static Path uploadedDirectory(final ServerLevel level) {
        return level.getServer().getServerDirectory()
                .resolve(DIRECTORY).resolve(UPLOADED);
    }

    public static void save(final ServerLevel level,
                            final String uploader,
                            final String name,
                            final byte[] data) throws IOException {
        final Path path = path(level, uploader, name);
        Files.createDirectories(path.getParent());
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static byte[] read(final ServerLevel level,
                               final String uploader,
                               final String name) throws IOException {
        return Files.readAllBytes(path(level, uploader, name));
    }

    public static boolean exists(final ServerLevel level,
                                  final String uploader,
                                  final String name) {
        try {
            return Files.isRegularFile(path(level, uploader, name));
        } catch (final IOException e) {
            return false;
        }
    }

    private static Path path(final ServerLevel level,
                              final String uploader,
                              final String name) throws IOException {
        final Path directory = uploadedDirectory(level).toAbsolutePath().normalize();
        final String safeUploader = sanitize(uploader);
        final String safeName = fileName(name);
        final Path resolved = directory.resolve(safeUploader).resolve(safeName).normalize();
        if (!resolved.startsWith(directory)) {
            throw new IOException("Invalid blueprint path: " + uploader + "/" + name);
        }
        return resolved;
    }

    private static String fileName(final String raw) throws IOException {
        String name = sanitize(raw);
        if (name.endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length());
        }
        if (name.isEmpty()) {
            throw new IOException("Empty blueprint name");
        }
        return name + EXTENSION;
    }

    private static String sanitize(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
