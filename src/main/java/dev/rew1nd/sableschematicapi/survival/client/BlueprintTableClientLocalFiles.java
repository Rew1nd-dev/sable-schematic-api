package dev.rew1nd.sableschematicapi.survival.client;

import dev.rew1nd.sableschematicapi.network.SableSchematicApiPackets;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreviewNbt;
import dev.rew1nd.sableschematicapi.survival.BlueprintPayloads;
import dev.rew1nd.sableschematicapi.survival.BlueprintTableLocalFiles;
import dev.rew1nd.sableschematicapi.survival.BlueprintTableUploadHandler;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Client-only provider for blueprint table local file access.
 */
public final class BlueprintTableClientLocalFiles implements BlueprintTableLocalFiles.Provider {
    private List<BlueprintToolLocalFiles.Entry> cachedEntries = Collections.emptyList();
    private List<BlueprintTableLocalFiles.LocalFile> cachedFiles = Collections.emptyList();
    private int revision;

    private BlueprintTableClientLocalFiles() {
    }

    public static void register() {
        BlueprintTableLocalFiles.setProvider(new BlueprintTableClientLocalFiles());
    }

    @Override
    public List<BlueprintTableLocalFiles.LocalFile> localFiles() {
        return this.cachedFiles;
    }

    @Override
    public int revision() {
        return this.revision;
    }

    @Override
    public void refresh() {
        try {
            this.cachedEntries = BlueprintToolLocalFiles.list();
            this.cachedFiles = this.cachedEntries.stream()
                    .map(entry -> new BlueprintTableLocalFiles.LocalFile(entry.name()))
                    .toList();
        } catch (final IOException e) {
            this.cachedEntries = Collections.emptyList();
            this.cachedFiles = Collections.emptyList();
        }
        this.revision++;
    }

    @Override
    public void requestUpload(final String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        try {
            this.refresh();
            final byte[] data = this.readLocalFile(name);
            final CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putByteArray("data", data);
            tag.putByteArray("hash", BlueprintPayloads.sha256(data));
            SableSchematicApiPackets.sendAction(BlueprintTableUploadHandler.UPLOAD_ACTION, tag);
        } catch (final IOException ignored) {
            // The table simply keeps the current selection when a local file disappears.
        }
    }

    private byte[] readLocalFile(final String name) throws IOException {
        for (final BlueprintToolLocalFiles.Entry entry : this.cachedEntries) {
            if (entry.name().equals(name)) {
                final byte[] stripped = SableBlueprintPreviewNbt.stripPreview(Files.readAllBytes(entry.path()));
                if (stripped.length > SableSchematicApiPackets.MAX_BLUEPRINT_BYTES) {
                    throw new IOException("Blueprint file is larger than " + SableSchematicApiPackets.MAX_BLUEPRINT_BYTES + " bytes.");
                }
                return stripped;
            }
        }
        throw new IOException("Blueprint file not found: " + name);
    }
}
