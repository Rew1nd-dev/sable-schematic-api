package dev.rew1nd.sableschematicapi.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolPreviewEntityRenderer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

public enum CreateContraptionPreviewEntityRenderer implements BlueprintToolPreviewEntityRenderer {
    INSTANCE;

    @Override
    public void render(final Entity entity,
                       final double x,
                       final double y,
                       final double z,
                       final float entityYaw,
                       final float partialTicks,
                       final PoseStack poseStack,
                       final MultiBufferSource bufferSource,
                       final int packedLight) {
        if (!(entity instanceof final AbstractContraptionEntity contraptionEntity)) {
            return;
        }
        if (!contraptionEntity.isReadyForRender() || !contraptionEntity.isAliveOrStale()) {
            return;
        }
        if (!VisualizationManager.supportsVisualization(entity.level())) {
            return;
        }

        final Contraption contraption = contraptionEntity.getContraption();
        if (contraption == null) {
            return;
        }

        final ClientContraption clientContraption = contraption.getOrCreateClientContraptionLazy();
        final VirtualRenderWorld renderWorld = clientContraption.getRenderLevel();
        final PoseStack model = new PoseStack();
        contraptionEntity.applyLocalTransforms(model, partialTicks);
        final Matrix4f world = new Matrix4f();
        ContraptionMatrices.translateToEntity(world, contraptionEntity, partialTicks);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        try {
            for (final RenderType renderType : RenderType.chunkBufferLayers()) {
                final SuperByteBuffer buffer = ContraptionEntityRenderer.getBuffer(contraption, renderWorld, renderType);
                if (!buffer.isEmpty()) {
                    buffer.transform(model)
                            .useLevelLight(entity.level(), world)
                            .renderInto(poseStack, bufferSource.getBuffer(renderType));
                }
            }
        } finally {
            poseStack.popPose();
        }
    }
}
