package dev.rew1nd.sableschematicapi.tool.client.preview;

import dev.rew1nd.sableschematicapi.tool.client.session.BlueprintToolClientSession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintToolSelectionPreviewRenderers {
    private static final List<BlueprintToolSelectionPreviewRenderer> RENDERERS = new ArrayList<>();

    private BlueprintToolSelectionPreviewRenderers() {
    }

    public static void register(final BlueprintToolSelectionPreviewRenderer renderer) {
        if (renderer != null && !RENDERERS.contains(renderer)) {
            RENDERERS.add(renderer);
        }
    }

    public static void render(final Player player) {
        final AABB selectionBox = BlueprintToolClientSession.selectionPreviewBox(player);
        if (player == null || selectionBox == null || RENDERERS.isEmpty()) {
            return;
        }

        final BlueprintToolSelectionPreview preview = new BlueprintToolSelectionPreview(
                player,
                selectionBox,
                BlueprintToolClientSession.hasRange()
        );
        for (final BlueprintToolSelectionPreviewRenderer renderer : RENDERERS) {
            renderer.render(preview);
        }
    }
}
