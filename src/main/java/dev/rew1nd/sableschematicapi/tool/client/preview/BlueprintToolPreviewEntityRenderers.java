package dev.rew1nd.sableschematicapi.tool.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintToolPreviewEntityRenderers {
    private static final List<BlueprintToolPreviewEntityRenderer> RENDERERS = new ArrayList<>();

    private BlueprintToolPreviewEntityRenderers() {
    }

    public static void register(final BlueprintToolPreviewEntityRenderer renderer) {
        if (renderer != null && !RENDERERS.contains(renderer)) {
            RENDERERS.add(renderer);
        }
    }

    public static void render(final Entity entity,
                              final double x,
                              final double y,
                              final double z,
                              final float entityYaw,
                              final float partialTicks,
                              final PoseStack poseStack,
                              final MultiBufferSource bufferSource,
                              final int packedLight) {
        for (final BlueprintToolPreviewEntityRenderer renderer : RENDERERS) {
            try {
                renderer.render(entity, x, y, z, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
            } catch (final RuntimeException ignored) {
                // Preview rendering is best-effort; one compat renderer must not break the saved blueprint.
            }
        }
    }
}
