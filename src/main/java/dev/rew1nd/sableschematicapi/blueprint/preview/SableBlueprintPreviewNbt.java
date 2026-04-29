package dev.rew1nd.sableschematicapi.blueprint.preview;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SableBlueprintPreviewNbt {
    private static final String PREVIEW_KEY = "preview";

    private SableBlueprintPreviewNbt() {
    }

    public static byte[] withPreview(final byte[] data, final @Nullable SableBlueprintPreview preview) throws IOException {
        final CompoundTag tag = read(data);
        putPreview(tag, preview);
        return write(tag);
    }

    public static void putPreview(final CompoundTag tag, final @Nullable SableBlueprintPreview preview) {
        if (preview == null) {
            tag.remove(PREVIEW_KEY);
        } else {
            tag.put(PREVIEW_KEY, preview.save());
        }
    }

    public static byte[] stripPreview(final byte[] data) throws IOException {
        final CompoundTag tag = read(data);
        if (!tag.contains(PREVIEW_KEY)) {
            return data;
        }

        tag.remove(PREVIEW_KEY);
        return write(tag);
    }

    public static CompoundTag read(final byte[] data) throws IOException {
        try (final ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            final CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            if (tag == null) {
                throw new IOException("Blueprint NBT is empty.");
            }
            return tag;
        }
    }

    public static byte[] write(final CompoundTag tag) throws IOException {
        try (final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, stream);
            return stream.toByteArray();
        }
    }
}
