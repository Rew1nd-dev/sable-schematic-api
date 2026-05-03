package dev.rew1nd.sableschematicapi.tool.client.preview.live;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolPreviewEntityRenderers;
import dev.rew1nd.sableschematicapi.tool.client.preview.camera.BlueprintToolPreviewCamera;
import dev.rew1nd.sableschematicapi.tool.client.preview.camera.BlueprintToolPreviewCameras;
import dev.rew1nd.sableschematicapi.tool.client.preview.flywheel.BlueprintToolFlywheelBlockEntityPreviewRenderer;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewFramebuffer;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewLighting;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.GlStateBackup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BlueprintToolLivePreviewRenderer {
    private static final @Nullable Method LEVEL_RENDER_ENTITY = findLevelRenderEntityMethod();

    private BlueprintToolLivePreviewRenderer() {
    }

    public static @Nullable SableBlueprintPreview render(final SableBlueprint blueprint) {
        final List<BlueprintToolLivePreviewEntry> entries = BlueprintToolLivePreviewCollector.collect(blueprint);
        if (entries.size() != blueprint.subLevels().size() || entries.isEmpty()) {
            return null;
        }

        final BlueprintToolLivePreviewGeometry geometry = BlueprintToolLivePreviewCollector.analyze(entries);
        final EnumMap<SableBlueprintPreview.View, int[]> views = new EnumMap<>(SableBlueprintPreview.View.class);
        for (final SableBlueprintPreview.View view : SableBlueprintPreview.View.values()) {
            views.put(view, renderView(geometry, view, SableBlueprintPreview.DEFAULT_RESOLUTION));
        }

        return new SableBlueprintPreview(
                SableBlueprintPreview.DEFAULT_RESOLUTION,
                geometry.basis().data().id(),
                geometry.bounds(),
                views
        );
    }

    private static int[] renderView(final BlueprintToolLivePreviewGeometry geometry,
                                    final SableBlueprintPreview.View view,
                                    final int resolution) {
        final Minecraft minecraft = Minecraft.getInstance();
        final TextureTarget target = new TextureTarget(resolution, resolution, true, Minecraft.ON_OSX);
        final GlStateBackup glState = new GlStateBackup();
        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        final BlueprintToolPreviewCamera camera = BlueprintToolPreviewCameras.liveCamera(geometry.bounds(), geometry.basis().pose(), view, resolution);
        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        final ClientLevel level = minecraft.level;
        final Object visualizationManager = level == null ? null : BlueprintToolFlywheelBlockEntityPreviewRenderer.visualizationManager(level);
        final LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
        boolean pushedModelView = false;
        boolean drawingDiagram = false;

        RenderSystem.backupGlState(glState);
        RenderSystem.backupProjectionMatrix();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        bufferSource.endBatch();

        try {
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(camera.projection(), VertexSorting.ORTHOGRAPHIC_Z);

            modelViewStack.pushMatrix();
            pushedModelView = true;
            modelViewStack.identity();
            modelViewStack.mul(camera.modelView());
            RenderSystem.applyModelViewMatrix();

            Lighting.setupNetherLevel();
            lightTexture.turnOnLightLayer();
            BlueprintToolPreviewLighting.applyDiagramLightTexture(lightTexture, 1.0F);
            renderBlocks(geometry, camera, bufferSource);
            BlueprintToolPreviewLighting.applyDiagramLightTexture(lightTexture, 1.0F);
            drawingDiagram = BlueprintToolFlywheelBlockEntityPreviewRenderer.setDrawingDiagram(visualizationManager, true);
            renderBlockEntities(geometry, camera, visualizationManager);
            renderEntities(geometry, camera, bufferSource);
            bufferSource.endBatch();

            return BlueprintToolPreviewFramebuffer.readPixels(target, resolution);
        } finally {
            bufferSource.endBatch();
            if (drawingDiagram) {
                BlueprintToolFlywheelBlockEntityPreviewRenderer.setDrawingDiagram(visualizationManager, false);
            }
            restoreLighting(level, lightTexture);
            if (pushedModelView) {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.restoreGlState(glState);
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void restoreLighting(final @Nullable ClientLevel level,
                                        final LightTexture lightTexture) {
        if (level != null && level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }
        lightTexture.updateLightTexture(0.0F);
    }

    private static void renderBlocks(final BlueprintToolLivePreviewGeometry geometry,
                                     final BlueprintToolPreviewCamera camera,
                                     final MultiBufferSource.BufferSource bufferSource) {
        final Minecraft minecraft = Minecraft.getInstance();
        final List<ClientSubLevel> subLevels = geometry.entries().stream()
                .map(BlueprintToolLivePreviewEntry::subLevel)
                .toList();

        for (final RenderType layer : RenderType.chunkBufferLayers()) {
            layer.setupRenderState();
            final ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                layer.clearRenderState();
                continue;
            }

            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(), camera.projection(), minecraft.getWindow());
            shader.apply();
            SubLevelRenderDispatcher.get().renderSectionLayer(
                    subLevels,
                    layer,
                    shader,
                    camera.position().x(),
                    camera.position().y(),
                    camera.position().z(),
                    RenderSystem.getModelViewMatrix(),
                    camera.projection(),
                    0.0F
            );

            bufferSource.endBatch(layer);
            shader.clear();
            layer.clearRenderState();
        }

        SubLevelRenderDispatcher.get().renderAfterSections(
                subLevels,
                camera.position().x(),
                camera.position().y(),
                camera.position().z(),
                RenderSystem.getModelViewMatrix(),
                camera.projection(),
                0.0F
        );
    }

    private static void renderBlockEntities(final BlueprintToolLivePreviewGeometry geometry,
                                            final BlueprintToolPreviewCamera camera,
                                            final @Nullable Object visualizationManager) {
        final Minecraft minecraft = Minecraft.getInstance();
        final VanillaSubLevelBlockEntityRenderer blockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(
                minecraft.getBlockEntityRenderDispatcher(),
                minecraft.renderBuffers(),
                new Long2ObjectOpenHashMap<>()
        );
        final List<ClientSubLevel> subLevels = geometry.entries().stream()
                .map(BlueprintToolLivePreviewEntry::subLevel)
                .toList();

        if (visualizationManager != null) {
            BlueprintToolFlywheelBlockEntityPreviewRenderer.render(visualizationManager, geometry, camera, blockEntityRenderer);
        }

        SubLevelRenderDispatcher.get().renderBlockEntities(
                subLevels,
                blockEntityRenderer,
                camera.position().x(),
                camera.position().y(),
                camera.position().z(),
                0.0F
        );
    }

    private static void renderEntities(final BlueprintToolLivePreviewGeometry geometry,
                                       final BlueprintToolPreviewCamera camera,
                                       final MultiBufferSource.BufferSource bufferSource) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        for (final BlueprintToolLivePreviewEntry entry : geometry.entries()) {
            final Set<UUID> seenEntities = new HashSet<>();
            final List<Entity> entities = level.getEntitiesOfClass(
                    Entity.class,
                    entry.subLevel().getPlot().getBoundingBox().toAABB().inflate(16.0)
            );
            final PoseStack poseStack = new PoseStack();
            poseStack.pushPose();

            for (final Entity entity : entities) {
                renderEntityIfBelongsToEntry(minecraft, level, entity, entry, seenEntities, camera, poseStack, bufferSource);
            }
            for (final Entity entity : level.entitiesForRendering()) {
                renderEntityIfBelongsToEntry(minecraft, level, entity, entry, seenEntities, camera, poseStack, bufferSource);
            }

            poseStack.popPose();
        }
    }

    private static void renderEntityIfBelongsToEntry(final Minecraft minecraft,
                                                     final ClientLevel level,
                                                     final Entity entity,
                                                     final BlueprintToolLivePreviewEntry entry,
                                                     final Set<UUID> seenEntities,
                                                     final BlueprintToolPreviewCamera camera,
                                                     final PoseStack poseStack,
                                                     final MultiBufferSource.BufferSource bufferSource) {
        if (entity instanceof Player || entity.isRemoved()) {
            return;
        }
        if (Sable.HELPER.getContaining(entity) != entry.subLevel()
                && Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != entry.subLevel()) {
            return;
        }
        if (!seenEntities.add(entity.getUUID())) {
            return;
        }

        renderEntity(minecraft, level, entity, camera, poseStack, bufferSource);
    }

    private static void renderEntity(final Minecraft minecraft,
                                     final ClientLevel level,
                                     final Entity entity,
                                     final BlueprintToolPreviewCamera camera,
                                     final PoseStack poseStack,
                                     final MultiBufferSource.BufferSource bufferSource) {
        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
        final double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camera.position().x();
        final double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - camera.position().y();
        final double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camera.position().z();
        final int packedLight = minecraft.getEntityRenderDispatcher().getPackedLightCoords(entity, partialTick);
        BlueprintToolPreviewEntityRenderers.render(
                entity,
                x,
                y,
                z,
                entity.getYRot(),
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );
        if (renderEntityWithLevelRenderer(minecraft, entity, camera, partialTick, poseStack, bufferSource)) {
            return;
        }

        minecraft.getEntityRenderDispatcher().render(
                entity,
                x,
                y,
                z,
                Mth.lerp(partialTick, entity.yRotO, entity.getYRot()),
                partialTick,
                poseStack,
                bufferSource,
                packedLight
        );
    }

    private static boolean renderEntityWithLevelRenderer(final Minecraft minecraft,
                                                         final Entity entity,
                                                         final BlueprintToolPreviewCamera camera,
                                                         final float partialTick,
                                                         final PoseStack poseStack,
                                                         final MultiBufferSource.BufferSource bufferSource) {
        if (LEVEL_RENDER_ENTITY == null) {
            return false;
        }

        try {
            LEVEL_RENDER_ENTITY.invoke(
                    minecraft.levelRenderer,
                    entity,
                    camera.position().x(),
                    camera.position().y(),
                    camera.position().z(),
                    partialTick,
                    poseStack,
                    bufferSource
            );
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static @Nullable Method findLevelRenderEntityMethod() {
        for (final Method method : LevelRenderer.class.getDeclaredMethods()) {
            final Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 7 || method.getReturnType() != Void.TYPE) {
                continue;
            }
            if (parameters[0] == Entity.class
                    && parameters[1] == Double.TYPE
                    && parameters[2] == Double.TYPE
                    && parameters[3] == Double.TYPE
                    && parameters[4] == Float.TYPE
                    && parameters[5] == PoseStack.class
                    && parameters[6] == MultiBufferSource.class) {
                try {
                    method.setAccessible(true);
                    return method;
                } catch (final RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
