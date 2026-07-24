package dev.rew1nd.sableschematicapi.survival.manuscript.client;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.BlueprintSummary;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.survival.manuscript.BlueprintAlias;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-only 128 MiB LRU for previews fetched through traded manuscripts. */
public final class RemoteBlueprintPreviewCache {
    private static final long MAX_WEIGHT = 128L * 1024L * 1024L;
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static long weight;

    private RemoteBlueprintPreviewCache() {
    }

    public static synchronized @Nullable Entry latest(final BlueprintAlias alias) {
        Entry latest = null;
        for (final Map.Entry<String, Entry> candidate : CACHE.entrySet()) {
            if (candidate.getValue().alias().equals(alias)) {
                latest = candidate.getValue();
            }
        }
        return latest;
    }

    public static synchronized @Nullable Entry get(final BlueprintAlias alias, final String sha256) {
        return CACHE.get(key(alias, sha256));
    }

    public static synchronized Entry put(final BlueprintAlias alias,
                                         final String sha256,
                                         final CompoundTag details) {
        final CompoundTag summaryTag = details.getCompound("summary");
        final BlueprintSummary summary = new BlueprintSummary(
                summaryTag.getInt("sub_levels"),
                summaryTag.getInt("blocks"),
                summaryTag.getInt("block_entity_tags"),
                summaryTag.getInt("entities")
        );
        SableBlueprintPreview preview = null;
        if (details.contains("preview", Tag.TAG_COMPOUND)) {
            try {
                preview = SableBlueprintPreview.load(details.getCompound("preview"));
            } catch (final RuntimeException ignored) {
                preview = null;
            }
        }
        final Entry entry = new Entry(
                alias,
                sha256,
                details.getString("author"),
                details.getString("description"),
                summary,
                preview
        );
        final String key = key(alias, sha256);
        final Entry previous = CACHE.put(key, entry);
        if (previous != null) {
            weight -= previous.weight();
        }
        weight += entry.weight();
        evict();
        return entry;
    }

    public static synchronized void clear() {
        CACHE.clear();
        weight = 0L;
    }

    private static void evict() {
        final var iterator = CACHE.entrySet().iterator();
        while (weight > MAX_WEIGHT && iterator.hasNext()) {
            final Entry entry = iterator.next().getValue();
            weight -= entry.weight();
            iterator.remove();
        }
    }

    private static String key(final BlueprintAlias alias, final String sha256) {
        return alias.cacheKey() + "@" + sha256;
    }

    public record Entry(BlueprintAlias alias,
                        String sha256,
                        String author,
                        String description,
                        BlueprintSummary summary,
                        @Nullable SableBlueprintPreview preview) {
        private long weight() {
            final long previewBytes = this.preview == null
                    ? 0L
                    : (long) this.preview.resolution() * this.preview.resolution()
                    * SableBlueprintPreview.View.values().length * Integer.BYTES;
            return previewBytes + (long) (this.author.length() + this.description.length() + 256) * Character.BYTES;
        }
    }
}
