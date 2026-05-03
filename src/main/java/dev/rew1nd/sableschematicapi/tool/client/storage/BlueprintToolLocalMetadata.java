package dev.rew1nd.sableschematicapi.tool.client.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public final class BlueprintToolLocalMetadata {
    private static final String GLOBAL_EXTRA_DATA = "global_extra_data";
    private static final String MOD_DATA = "sable_schematic_api";
    private static final String METADATA = "metadata";
    private static final String AUTHOR = "author";
    private static final String DESCRIPTION = "description";
    private static final int MAX_AUTHOR_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 4096;

    private BlueprintToolLocalMetadata() {
    }

    public static Metadata read(final BlueprintToolLocalFiles.Entry entry) throws IOException {
        final CompoundTag root = readRoot(entry);
        final CompoundTag metadata = metadata(root, false);
        if (metadata == null) {
            return Metadata.EMPTY;
        }

        return new Metadata(
                metadata.getString(AUTHOR),
                metadata.getString(DESCRIPTION)
        );
    }

    public static Metadata write(final BlueprintToolLocalFiles.Entry entry,
                                 final String author,
                                 final String description) throws IOException {
        final CompoundTag root = readRoot(entry);
        final CompoundTag metadata = metadata(root, true);
        final Metadata value = new Metadata(author, description);

        metadata.putString(AUTHOR, value.author());
        metadata.putString(DESCRIPTION, value.description());

        try (final OutputStream stream = Files.newOutputStream(
                entry.path(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            NbtIo.writeCompressed(root, stream);
        }

        return value;
    }

    private static CompoundTag readRoot(final BlueprintToolLocalFiles.Entry entry) throws IOException {
        try (final InputStream stream = Files.newInputStream(entry.path(), StandardOpenOption.READ)) {
            final CompoundTag root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            if (root == null) {
                throw new IOException("Blueprint file is empty.");
            }
            return root;
        }
    }

    private static CompoundTag metadata(final CompoundTag root,
                                        final boolean create) {
        CompoundTag global = root.contains(GLOBAL_EXTRA_DATA, Tag.TAG_COMPOUND)
                ? root.getCompound(GLOBAL_EXTRA_DATA)
                : null;
        if (global == null) {
            if (!create) {
                return null;
            }
            global = new CompoundTag();
            root.put(GLOBAL_EXTRA_DATA, global);
        }

        CompoundTag modData = global.contains(MOD_DATA, Tag.TAG_COMPOUND)
                ? global.getCompound(MOD_DATA)
                : null;
        if (modData == null) {
            if (!create) {
                return null;
            }
            modData = new CompoundTag();
            global.put(MOD_DATA, modData);
        }

        CompoundTag metadata = modData.contains(METADATA, Tag.TAG_COMPOUND)
                ? modData.getCompound(METADATA)
                : null;
        if (metadata == null) {
            if (!create) {
                return null;
            }
            metadata = new CompoundTag();
            modData.put(METADATA, metadata);
        }
        return metadata;
    }

    private static String clamp(final String value,
                                final int maxLength) {
        final String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record Metadata(String author, String description) {
        public static final Metadata EMPTY = new Metadata("", "");

        public Metadata {
            author = clamp(author, MAX_AUTHOR_LENGTH);
            description = clamp(description, MAX_DESCRIPTION_LENGTH);
        }
    }
}
