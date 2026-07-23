package dev.rew1nd.sableschematicapi.compat.create.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Writes Create contraption block geometry into the camera's Veil outline mask. */
public final class CameraCreateContraptionOutlineRenderer {
    private static final RenderType CONTRAPTION_MASK_GEOMETRY = MaskRenderTypes.CONTRAPTION_MASK_GEOMETRY;

    private CameraCreateContraptionOutlineRenderer() {
    }

    public static void render(final Minecraft minecraft,
                              final ClientLevel level,
                              final Entity entity,
                              final Camera camera,
                              final PoseStack poseStack,
                              final MultiBufferSource.BufferSource bufferSource) {
        if (!(entity instanceof AbstractContraptionEntity contraptionEntity)) {
            return;
        }
        final Contraption contraption = contraptionEntity.getContraption();
        if (contraption == null || !contraptionEntity.isAliveOrStale() || !contraptionEntity.isReadyForRender()) {
            return;
        }

        final float partialTick = minecraft.getTimer()
                .getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
        final EntityRenderTransform entityTransform = resolveEntityRenderTransform(entity, partialTick);
        final double x = entityTransform.x() - camera.getPosition().x;
        final double y = entityTransform.y() - camera.getPosition().y;
        final double z = entityTransform.z() - camera.getPosition().z;

        final ClientContraption clientContraption = contraption.getOrCreateClientContraptionLazy();
        final VirtualRenderWorld renderWorld = clientContraption.getRenderLevel();
        final PoseStack localTransforms = new PoseStack();
        localTransforms.pushPose();
        contraptionEntity.applyLocalTransforms(localTransforms, partialTick);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        if (entityTransform.renderPose() != null) {
            poseStack.mulPose(toQuaternionf(entityTransform.renderPose()));
        }
        try {
            for (final RenderType renderType : RenderType.chunkBufferLayers()) {
                final SuperByteBuffer buffer = ContraptionEntityRenderer.getBuffer(contraption, renderWorld, renderType);
                if (buffer.isEmpty()) {
                    continue;
                }
                final VertexConsumer vertexConsumer = new WhiteMaskVertexConsumer(
                        bufferSource.getBuffer(CONTRAPTION_MASK_GEOMETRY)
                );
                buffer.reset()
                        .transform(localTransforms)
                        .renderInto(poseStack, vertexConsumer);
            }
            bufferSource.endBatch(CONTRAPTION_MASK_GEOMETRY);
        } finally {
            poseStack.popPose();
            localTransforms.popPose();
        }
    }

    private static EntityRenderTransform resolveEntityRenderTransform(final Entity entity, final float partialTick) {
        final double entityX = Mth.lerp(partialTick, entity.xOld, entity.getX());
        final double entityY = Mth.lerp(partialTick, entity.yOld, entity.getY());
        final double entityZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        final SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing instanceof ClientSubLevel clientSubLevel) {
            final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
            final Vector3d transformed = renderPose.transformPosition(new Vector3d(entityX, entityY, entityZ));
            return new EntityRenderTransform(transformed.x, transformed.y, transformed.z, renderPose);
        }

        final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);
        if (trackingSubLevel instanceof ClientSubLevel clientSubLevel && !entity.isPassenger()) {
            final Vector3d oldLocal = trackingSubLevel.lastPose()
                    .transformPositionInverse(new Vector3d(entity.xOld, entity.yOld, entity.zOld));
            final Vector3d newLocal = trackingSubLevel.logicalPose()
                    .transformPositionInverse(new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
            final Vector3d interpolatedLocal = new Vector3d(
                    Mth.lerp(partialTick, oldLocal.x, newLocal.x),
                    Mth.lerp(partialTick, oldLocal.y, newLocal.y),
                    Mth.lerp(partialTick, oldLocal.z, newLocal.z)
            );
            clientSubLevel.renderPose(partialTick).transformPosition(interpolatedLocal);
            return new EntityRenderTransform(interpolatedLocal.x, interpolatedLocal.y, interpolatedLocal.z, null);
        }

        return new EntityRenderTransform(entityX, entityY, entityZ, null);
    }

    private static Quaternionf toQuaternionf(final Pose3dc pose) {
        return new Quaternionf(
                (float) pose.orientation().x(),
                (float) pose.orientation().y(),
                (float) pose.orientation().z(),
                (float) pose.orientation().w()
        );
    }

    private static final class WhiteMaskVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;

        private WhiteMaskVertexConsumer(final VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z) {
            delegate.addVertex(x, y, z);
            delegate.setColor(255, 255, 255, 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(final float u, final float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(final int u, final int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(final int u, final int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(final float x, final float y, final float z) {
            return this;
        }
    }

    private static final class MaskRenderTypes extends RenderStateShard {
        private static final RenderType CONTRAPTION_MASK_GEOMETRY = RenderType.create(
                "sable_schematic_api:camera_outline_contraption_mask_geometry",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                true,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTextureState(NO_TEXTURE)
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false)
        );

        private MaskRenderTypes() {
            super(null, null, null);
        }
    }

    private record EntityRenderTransform(double x,
                                         double y,
                                         double z,
                                         @Nullable Pose3dc renderPose) {
    }
}
