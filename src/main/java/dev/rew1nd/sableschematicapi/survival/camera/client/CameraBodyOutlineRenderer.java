package dev.rew1nd.sableschematicapi.survival.camera.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.rew1nd.sableschematicapi.SableSchematicApi;
import dev.rew1nd.sableschematicapi.compat.create.client.CameraCreateContraptionOutlineRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Veil mask/post-process outline for camera candidate and cached Sable bodies. */
public final class CameraBodyOutlineRenderer {
    private static final @Nullable Method LEVEL_RENDER_ENTITY = findLevelRenderEntityMethod();
    private static final @Nullable Field ENTITY_RENDER_SHADOW = findEntityRenderShadowField();
    private static final ResourceLocation OUTLINE_PIPELINE = SableSchematicApi.id("camera_body_outline");
    private static final ResourceLocation OUTLINE_MASK = SableSchematicApi.id("camera_body_outline_mask");
    private static final int CANDIDATE_COLOR = 0xFFE7B64D;
    private static final int CACHED_COLOR = 0xFF4CC9F0;
    private static final boolean CREATE_LOADED = ModList.get().isLoaded("create");
    private static Set<UUID> candidateIds = Set.of();
    private static Set<UUID> cachedIds = Set.of();
    private static boolean registered;

    private CameraBodyOutlineRenderer() {
    }

    public static void registerEvents() {
        if (!registered) {
            registered = true;
            VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(CameraBodyOutlineRenderer::render);
        }
    }

    public static void update(final Set<UUID> candidates, final Set<UUID> cached) {
        candidateIds = candidates == null ? Set.of() : Set.copyOf(candidates);
        cachedIds = cached == null ? Set.of() : Set.copyOf(cached);
        if (candidateIds.isEmpty() && cachedIds.isEmpty()) {
            removeScheduledPipeline();
        }
    }

    private static void render(final VeilRenderLevelStageEvent.Stage stage,
                               final LevelRenderer levelRenderer,
                               final MultiBufferSource.BufferSource bufferSource,
                               final MatrixStack matrixStack,
                               final Matrix4fc frustumMatrix,
                               final Matrix4fc projectionMatrix,
                               final int renderTick,
                               final DeltaTracker deltaTracker,
                               final Camera camera,
                               final Frustum frustum) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || (candidateIds.isEmpty() && cachedIds.isEmpty())
                || camera == null || frustumMatrix == null || projectionMatrix == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            update(Set.of(), Set.of());
            return;
        }
        final AdvancedFbo mask = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(OUTLINE_MASK);
        final PostPipeline pipeline = VeilRenderSystem.renderer().getPostProcessingManager().getPipeline(OUTLINE_PIPELINE);
        if (mask == null || pipeline == null) {
            return;
        }

        final List<ClientSubLevel> candidates = resolve(level, candidateIds);
        final List<ClientSubLevel> cached = resolve(level, cachedIds);
        if (candidates.isEmpty() && cached.isEmpty()) {
            return;
        }

        final GlStateBackup glState = new GlStateBackup();
        RenderSystem.backupGlState(glState);
        try {
            renderBatch(mask, pipeline, CANDIDATE_COLOR, candidates, minecraft, level, levelRenderer,
                    bufferSource, camera, frustumMatrix, projectionMatrix, deltaTracker);
            renderBatch(mask, pipeline, CACHED_COLOR, cached, minecraft, level, levelRenderer,
                    bufferSource, camera, frustumMatrix, projectionMatrix, deltaTracker);
        } finally {
            bufferSource.endBatch();
            RenderSystem.restoreGlState(glState);
            AdvancedFbo.unbind();
        }
    }

    private static void renderBatch(final AdvancedFbo mask,
                                    final PostPipeline pipeline,
                                    final int color,
                                    final List<ClientSubLevel> targets,
                                    final Minecraft minecraft,
                                    final ClientLevel level,
                                    final LevelRenderer levelRenderer,
                                    final MultiBufferSource.BufferSource bufferSource,
                                    final Camera camera,
                                    final Matrix4fc frustumMatrix,
                                    final Matrix4fc projectionMatrix,
                                    final DeltaTracker deltaTracker) {
        if (targets.isEmpty()) {
            return;
        }
        bufferSource.endBatch();
        mask.bindDraw(true);
        mask.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT);
        try {
            renderMaskGeometry(minecraft, level, levelRenderer, bufferSource, targets,
                    camera, frustumMatrix, projectionMatrix, deltaTracker);
        } finally {
            bufferSource.endBatch();
            AdvancedFbo.unbind();
        }
        setPipelineColor(pipeline, color);
        VeilRenderSystem.renderer().getPostProcessingManager().runPipeline(pipeline);
    }

    private static void renderMaskGeometry(final Minecraft minecraft,
                                           final ClientLevel level,
                                           final LevelRenderer levelRenderer,
                                           final MultiBufferSource.BufferSource bufferSource,
                                           final List<ClientSubLevel> targets,
                                           final Camera camera,
                                           final Matrix4fc frustumMatrix,
                                           final Matrix4fc projectionMatrix,
                                           final DeltaTracker deltaTracker) {
        final Matrix4f modelView = new Matrix4f(frustumMatrix);
        final Matrix4f projection = new Matrix4f(projectionMatrix);
        final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        final double cameraX = camera.getPosition().x;
        final double cameraY = camera.getPosition().y;
        final double cameraZ = camera.getPosition().z;
        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(modelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        try {
            for (final RenderType layer : RenderType.chunkBufferLayers()) {
                layer.setupRenderState();
                final ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) {
                    layer.clearRenderState();
                    continue;
                }
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
                shader.apply();
                SubLevelRenderDispatcher.get().renderSectionLayer(
                        targets, layer, shader, cameraX, cameraY, cameraZ, modelView, projection, partialTick
                );
                bufferSource.endBatch(layer);
                shader.clear();
                layer.clearRenderState();
            }
            SubLevelRenderDispatcher.get().renderAfterSections(
                    targets, cameraX, cameraY, cameraZ, modelView, projection, partialTick
            );
            renderBlockEntities(minecraft, targets, cameraX, cameraY, cameraZ, partialTick);
            renderEntities(minecraft, level, levelRenderer, targets, camera, bufferSource);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void renderBlockEntities(final Minecraft minecraft,
                                            final List<ClientSubLevel> targets,
                                            final double cameraX,
                                            final double cameraY,
                                            final double cameraZ,
                                            final float partialTick) {
        final VanillaSubLevelBlockEntityRenderer renderer = new VanillaSubLevelBlockEntityRenderer(
                minecraft.getBlockEntityRenderDispatcher(), minecraft.renderBuffers(), new Long2ObjectOpenHashMap<>()
        );
        SubLevelRenderDispatcher.get().renderBlockEntities(
                targets, renderer, cameraX, cameraY, cameraZ, partialTick
        );
        minecraft.renderBuffers().bufferSource().endBatch();
    }

    private static void renderEntities(final Minecraft minecraft,
                                       final ClientLevel level,
                                       final LevelRenderer levelRenderer,
                                       final List<ClientSubLevel> targets,
                                       final Camera camera,
                                       final MultiBufferSource.BufferSource bufferSource) {
        final Set<UUID> seen = new HashSet<>();
        final PoseStack poseStack = new PoseStack();
        final EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        final @Nullable Boolean previousShadow = suppressEntityShadows(dispatcher);
        poseStack.pushPose();
        try {
            for (final ClientSubLevel target : targets) {
                for (final Entity entity : level.getEntitiesOfClass(
                        Entity.class, target.getPlot().getBoundingBox().toAABB().inflate(16.0D))) {
                    renderEntityIfTarget(minecraft, level, levelRenderer, entity, targets, seen, camera, poseStack, bufferSource);
                }
            }
            for (final Entity entity : level.entitiesForRendering()) {
                renderEntityIfTarget(minecraft, level, levelRenderer, entity, targets, seen, camera, poseStack, bufferSource);
            }
        } finally {
            restoreEntityShadows(dispatcher, previousShadow);
            poseStack.popPose();
            bufferSource.endBatch();
        }
    }

    private static void renderEntityIfTarget(final Minecraft minecraft,
                                             final ClientLevel level,
                                             final LevelRenderer levelRenderer,
                                             final Entity entity,
                                             final List<ClientSubLevel> targets,
                                             final Set<UUID> seen,
                                             final Camera camera,
                                             final PoseStack poseStack,
                                             final MultiBufferSource.BufferSource bufferSource) {
        if (entity instanceof Player || entity.isRemoved() || !seen.add(entity.getUUID())) {
            return;
        }
        final SubLevel containing = Sable.HELPER.getContaining(entity);
        final SubLevel tracking = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        if (!(containing instanceof ClientSubLevel && targets.contains(containing))
                && !(tracking instanceof ClientSubLevel && targets.contains(tracking))) {
            return;
        }
        if (CREATE_LOADED) {
            CameraCreateContraptionOutlineRenderer.render(
                    minecraft, level, entity, camera, poseStack, bufferSource
            );
        }
        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
        if (renderEntityWithLevelRenderer(levelRenderer, entity, camera, partialTick, poseStack, bufferSource)) {
            return;
        }
        final double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camera.getPosition().x;
        final double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - camera.getPosition().y;
        final double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camera.getPosition().z;
        minecraft.getEntityRenderDispatcher().render(
                entity, x, y, z, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()), partialTick,
                poseStack, bufferSource, minecraft.getEntityRenderDispatcher().getPackedLightCoords(entity, partialTick)
        );
    }

    private static List<ClientSubLevel> resolve(final ClientLevel level, final Set<UUID> ids) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || ids.isEmpty()) {
            return List.of();
        }
        final LinkedHashSet<ClientSubLevel> result = new LinkedHashSet<>();
        for (final UUID id : ids) {
            final SubLevel body = container.getSubLevel(id);
            if (body instanceof final ClientSubLevel clientBody && !clientBody.isRemoved()) {
                result.add(clientBody);
            }
        }
        return List.copyOf(result);
    }

    private static void setPipelineColor(final PostPipeline pipeline, final int argb) {
        final float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        final float red = ((argb >>> 16) & 0xFF) / 255.0F;
        final float green = ((argb >>> 8) & 0xFF) / 255.0F;
        final float blue = (argb & 0xFF) / 255.0F;
        pipeline.getUniformSafe("OutlineColor").setVector(red, green, blue, alpha);
    }

    private static void removeScheduledPipeline() {
        if (VeilRenderSystem.renderer() != null) {
            VeilRenderSystem.renderer().getPostProcessingManager().remove(OUTLINE_PIPELINE);
        }
    }

    private static boolean renderEntityWithLevelRenderer(final LevelRenderer renderer,
                                                         final Entity entity,
                                                         final Camera camera,
                                                         final float partialTick,
                                                         final PoseStack poseStack,
                                                         final MultiBufferSource.BufferSource buffers) {
        if (LEVEL_RENDER_ENTITY == null) {
            return false;
        }
        try {
            LEVEL_RENDER_ENTITY.invoke(renderer, entity, camera.getPosition().x, camera.getPosition().y,
                    camera.getPosition().z, partialTick, poseStack, buffers);
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static @Nullable Boolean suppressEntityShadows(final EntityRenderDispatcher dispatcher) {
        if (ENTITY_RENDER_SHADOW == null) {
            return null;
        }
        try {
            final boolean previous = ENTITY_RENDER_SHADOW.getBoolean(dispatcher);
            dispatcher.setRenderShadow(false);
            return previous;
        } catch (final IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static void restoreEntityShadows(final EntityRenderDispatcher dispatcher, final @Nullable Boolean previous) {
        if (previous != null) {
            dispatcher.setRenderShadow(previous);
        }
    }

    private static @Nullable Method findLevelRenderEntityMethod() {
        for (final Method method : LevelRenderer.class.getDeclaredMethods()) {
            final Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 7 && method.getReturnType() == Void.TYPE
                    && parameters[0] == Entity.class
                    && parameters[1] == Double.TYPE && parameters[2] == Double.TYPE && parameters[3] == Double.TYPE
                    && parameters[4] == Float.TYPE && parameters[5] == PoseStack.class
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

    private static @Nullable Field findEntityRenderShadowField() {
        try {
            final Field field = EntityRenderDispatcher.class.getDeclaredField("shouldRenderShadow");
            field.setAccessible(true);
            return field;
        } catch (final NoSuchFieldException | RuntimeException ignored) {
            return null;
        }
    }
}
