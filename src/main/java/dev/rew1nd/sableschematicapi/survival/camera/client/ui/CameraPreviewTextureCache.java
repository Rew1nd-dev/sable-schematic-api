package dev.rew1nd.sableschematicapi.survival.camera.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.survival.client.ui.BlueprintPreviewTextureCache;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolLocalPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import org.jetbrains.annotations.Nullable;

/** Per-screen GPU texture cache for camera blueprint thumbnails. */
final class CameraPreviewTextureCache implements AutoCloseable {
    private final BlueprintPreviewTextureCache<BlueprintToolLocalFiles.Entry> delegate =
            new BlueprintPreviewTextureCache<>("sable_camera_preview", BlueprintToolLocalPreviewCache::preview);

    void draw(final GUIContext context,
              final @Nullable BlueprintToolLocalFiles.Entry entry,
              final SableBlueprintPreview.View view,
              final int x,
              final int y,
              final int width,
              final int height) {
        this.delegate.draw(context, entry, view, x, y, width, height);
    }

    @Override
    public void close() {
        this.delegate.close();
    }
}
