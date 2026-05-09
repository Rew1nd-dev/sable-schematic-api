package dev.rew1nd.sableschematicapi.tool.ui.blueprint;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolLocalPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;

import static dev.rew1nd.sableschematicapi.tool.ui.BlueprintToolUiKit.drawLargePreviewTile;

final class BlueprintToolPreviewCanvas extends UIElement {
    private final BlueprintToolLocalFiles.Entry entry;
    private final SableBlueprintPreview.View view;

    BlueprintToolPreviewCanvas(final BlueprintToolLocalFiles.Entry entry, final SableBlueprintPreview.View view) {
        this.entry = entry;
        this.view = view;
    }

    @Override
    public void drawBackgroundAdditional(final GUIContext guiContext) {
        final SableBlueprintPreview preview = BlueprintToolLocalPreviewCache.preview(this.entry);
        final int x = Math.round(getContentX());
        final int y = Math.round(getContentY());
        final int width = Math.round(getContentWidth());
        final int height = Math.round(getContentHeight());
        final int size = Math.max(1, Math.min(width, height));
        final int startX = x + Math.max(0, (width - size) / 2);
        final int startY = y + Math.max(0, (height - size) / 2);
        drawLargePreviewTile(guiContext, preview, this.view, startX, startY, size);
    }
}
