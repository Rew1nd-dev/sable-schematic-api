package dev.rew1nd.sableschematicapi.tool.client;

import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class BlueprintToolLocalPreviewCache {
    private static final Map<Path, CacheEntry> CACHE = new HashMap<>();

    private BlueprintToolLocalPreviewCache() {
    }

    public static @Nullable SableBlueprintPreview preview(final @Nullable BlueprintToolLocalFiles.Entry entry) {
        if (entry == null) {
            return null;
        }

        final Path path = entry.path().toAbsolutePath().normalize();
        try {
            final long size = Files.size(path);
            final long modified = Files.getLastModifiedTime(path).toMillis();
            final CacheEntry cached = CACHE.get(path);
            if (cached != null && cached.size() == size && cached.modified() == modified) {
                return cached.preview();
            }

            final SableBlueprintPreview preview = readPreview(path);
            CACHE.put(path, new CacheEntry(size, modified, preview));
            return preview;
        } catch (final IOException | RuntimeException e) {
            CACHE.remove(path);
            return null;
        }
    }

    public static void invalidate(final @Nullable BlueprintToolLocalFiles.Entry entry) {
        if (entry != null) {
            CACHE.remove(entry.path().toAbsolutePath().normalize());
        }
    }

    private static @Nullable SableBlueprintPreview readPreview(final Path path) throws IOException {
        try (final InputStream stream = Files.newInputStream(path)) {
            final CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            if (tag == null || !tag.contains("preview", Tag.TAG_COMPOUND)) {
                return null;
            }

            return SableBlueprintPreview.load(tag.getCompound("preview"));
        }
    }

    private record CacheEntry(long size, long modified, @Nullable SableBlueprintPreview preview) {
    }
}
