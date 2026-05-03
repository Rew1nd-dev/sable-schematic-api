package dev.rew1nd.sableschematicapi.tool.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreviewGenerator;
import dev.rew1nd.sableschematicapi.tool.client.preview.live.BlueprintToolLivePreviewRenderer;
import dev.rew1nd.sableschematicapi.tool.client.preview.stored.BlueprintToolStoredPreviewRenderer;
import org.jetbrains.annotations.Nullable;

public final class BlueprintToolPreviewRenderer {
    private BlueprintToolPreviewRenderer() {
    }

    public static @Nullable SableBlueprintPreview generate(final SableBlueprint blueprint) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Client preview rendering must run on the render thread.");
        }

        try {
            final SableBlueprintPreview livePreview = BlueprintToolLivePreviewRenderer.render(blueprint);
            if (livePreview != null) {
                return livePreview;
            }
        } catch (final RuntimeException ignored) {
        }

        try {
            return BlueprintToolStoredPreviewRenderer.render(blueprint);
        } catch (final RuntimeException ignored) {
            return SableBlueprintPreviewGenerator.generate(blueprint.subLevels());
        }
    }
}
