package dev.rew1nd.sableschematicapi.client.frontier;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

public final class SableFrontierLaserRenderer {
    private static final net.minecraft.resources.ResourceLocation RENDER_TYPE = SableSchematicApi.id("frontier_laser");
    private static final int SLICE_COUNT = 14;
    private static final int RED = 7;
    private static final int GREEN = 54;
    private static final int BLUE = 142;

    private SableFrontierLaserRenderer() {
    }

    public static void render(final VeilRenderLevelStageEvent.Stage stage,
                              final LevelRenderer levelRenderer,
                              final MultiBufferSource.BufferSource bufferSource,
                              final MatrixStack matrixStack,
                              final Matrix4fc frustumMatrix,
                              final Matrix4fc projectionMatrix,
                              final int renderTick,
                              final DeltaTracker deltaTracker,
                              final Camera camera,
                              final Frustum frustum) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        final List<FrontierLaserStore.Entry> entries = FrontierLaserStore.snapshot();
        if (entries.isEmpty()) {
            return;
        }

        final RenderType renderType = VeilRenderType.get(RENDER_TYPE);
        if (renderType == null) {
            return;
        }

        final VertexConsumer consumer = bufferSource.getBuffer(renderType);
        final Vec3 cameraPosition = camera.getPosition();
        boolean rendered = false;

        matrixStack.matrixPush();
        matrixStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        final Matrix4f matrix = matrixStack.position();
        try {
            for (final FrontierLaserStore.Entry entry : entries) {
                if (entry.subLevel().isRemoved()) {
                    FrontierLaserStore.clear(entry.subLevel());
                    continue;
                }

                if (entry.retracting()) {
                    rendered |= renderRetractingCone(consumer, matrix, entry, entry.retractProgress());
                    continue;
                }

                final SableFrontierData data = FrontierDataStore.get(entry.subLevel());
                if (data == null || !data.enabled()) {
                    FrontierLaserStore.startRetract(entry.subLevel());
                    rendered |= renderRetractingCone(consumer, matrix, entry, 0.0f);
                    continue;
                }

                final SableFrontierLaserGeometry.BasePolygon base =
                        SableFrontierLaserGeometry.currentBase(entry.subLevel(), data);
                if (base == null) {
                    if (entry.lastBase() != null) {
                        FrontierLaserStore.startRetract(entry.subLevel());
                        rendered |= renderRetractingCone(consumer, matrix, entry, 0.0f);
                    }
                    continue;
                }

                FrontierLaserStore.rememberBase(entry.subLevel(), base);
                renderCone(consumer, matrix, entry.apexWorld(), base);
                rendered = true;
            }
        } finally {
            matrixStack.matrixPop();
        }

        if (rendered) {
            bufferSource.endBatch(renderType);
        }
    }

    private static boolean renderRetractingCone(final VertexConsumer consumer,
                                                final Matrix4f matrix,
                                                final FrontierLaserStore.Entry entry,
                                                final float progress) {
        if (entry.lastBase() == null) {
            return false;
        }
        renderCone(consumer, matrix, entry.apexWorld(), retract(entry.apexWorld(), entry.lastBase(), progress));
        return true;
    }

    private static SableFrontierLaserGeometry.BasePolygon retract(final Vector3dc apex,
                                                                  final SableFrontierLaserGeometry.BasePolygon base,
                                                                  final float progress) {
        final List<Vector3d> points = base.points().stream()
                .map(point -> new Vector3d(point).lerp(apex, progress))
                .toList();
        final Vector3d center = new Vector3d(base.center()).lerp(apex, progress);
        return new SableFrontierLaserGeometry.BasePolygon(points, center);
    }

    private static void renderCone(final VertexConsumer consumer,
                                   final Matrix4f matrix,
                                   final Vector3dc apex,
                                   final SableFrontierLaserGeometry.BasePolygon base) {
        final List<Vector3d> points = base.points();
        final Vector3dc center = base.center();

        for (int i = 0; i < points.size(); i++) {
            final Vector3dc a = points.get(i);
            final Vector3dc b = points.get((i + 1) % points.size());
            vertex(consumer, matrix, apex, 10);
            vertex(consumer, matrix, a, 82);
            vertex(consumer, matrix, b, 82);
        }

        for (int i = 0; i < points.size(); i++) {
            final Vector3dc a = points.get(i);
            final Vector3dc b = points.get((i + 1) % points.size());
            vertex(consumer, matrix, center, 38);
            vertex(consumer, matrix, b, 38);
            vertex(consumer, matrix, a, 38);
        }

        for (int slice = 1; slice <= SLICE_COUNT; slice++) {
            final double t = slice / (double) (SLICE_COUNT + 1);
            final int alpha = Math.max(6, (int) Math.round(22.0 * t));
            final Vector3d sliceCenter = interpolate(apex, center, t);
            for (int i = 0; i < points.size(); i++) {
                final Vector3d a = interpolate(apex, points.get(i), t);
                final Vector3d b = interpolate(apex, points.get((i + 1) % points.size()), t);
                vertex(consumer, matrix, sliceCenter, alpha);
                vertex(consumer, matrix, a, alpha);
                vertex(consumer, matrix, b, alpha);
            }
        }
    }

    private static Vector3d interpolate(final Vector3dc from, final Vector3dc to, final double t) {
        return new Vector3d(from).lerp(to, t);
    }

    private static void vertex(final VertexConsumer consumer,
                               final Matrix4f matrix,
                               final Vector3dc position,
                               final int alpha) {
        consumer.addVertex(matrix, (float) position.x(), (float) position.y(), (float) position.z())
                .setColor(RED, GREEN, BLUE, alpha);
    }
}
