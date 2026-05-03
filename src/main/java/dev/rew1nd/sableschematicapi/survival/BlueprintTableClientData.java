package dev.rew1nd.sableschematicapi.survival;

import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles.Entry;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Client-side data holder for the blueprint table UI.
 * Refreshes the local file list and tracks revision for UI updates.
 */
public final class BlueprintTableClientData {
    private static List<Entry> cachedFiles = Collections.emptyList();
    private static int revision;

    private BlueprintTableClientData() {
    }

    public static List<Entry> localFiles() {
        return cachedFiles;
    }

    public static int fileListRevision() {
        return revision;
    }

    public static void refreshLocalFiles() {
        if (net.minecraft.client.Minecraft.getInstance().gameDirectory == null) {
            return;
        }
        try {
            cachedFiles = BlueprintToolLocalFiles.list();
        } catch (final IOException e) {
            cachedFiles = Collections.emptyList();
        }
        revision++;
    }

    public static byte[] readLocalFile(final String name) throws IOException {
        for (final Entry entry : cachedFiles) {
            if (entry.name().equals(name)) {
                return BlueprintToolLocalFiles.read(entry);
            }
        }
        throw new IOException("Blueprint file not found: " + name);
    }
}
