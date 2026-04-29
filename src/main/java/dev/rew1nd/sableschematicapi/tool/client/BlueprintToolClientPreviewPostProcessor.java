package dev.rew1nd.sableschematicapi.tool.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreviewGenerator;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreviewNbt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.GlStateBackup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BlueprintToolClientPreviewPostProcessor {
    private static final int PADDING = 8;
    private static final int BUFFER_BYTES = 2 * 1024 * 1024;
    private static final double ENTITY_BOUNDS_PADDING = 1.0;
    private static final double ENTITY_BOUNDS_HEIGHT = 2.0;
    private static boolean levelRenderEntityMethodResolved;
    private static @Nullable Method levelRenderEntityMethod;

    private BlueprintToolClientPreviewPostProcessor() {
    }

    public static byte[] attachClientPreview(final byte[] data) throws IOException {
        final CompoundTag tag = SableBlueprintPreviewNbt.read(data);
        SableBlueprintPreviewNbt.putPreview(tag, null);

        final SableBlueprint blueprint = SableBlueprint.load(tag);
        final SableBlueprintPreview preview = generateRenderedPreview(blueprint);
        SableBlueprintPreviewNbt.putPreview(tag, preview);
        return SableBlueprintPreviewNbt.write(tag);
    }

    public static byte[] stripPreview(final byte[] data) throws IOException {
        return SableBlueprintPreviewNbt.stripPreview(data);
    }

    private static @Nullable SableBlueprintPreview generateRenderedPreview(final SableBlueprint blueprint) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Client preview rendering must run on the render thread.");
        }

        try {
            final SableBlueprintPreview livePreview = renderLivePreview(blueprint);
            if (livePreview != null) {
                return livePreview;
            }
        } catch (final RuntimeException ignored) {
        }

        try {
            return renderBlueprintDataPreview(blueprint);
        } catch (final RuntimeException ignored) {
            return SableBlueprintPreviewGenerator.generate(blueprint.subLevels());
        }
    }

    private static @Nullable SableBlueprintPreview renderLivePreview(final SableBlueprint blueprint) {
        final List<LivePreviewEntry> entries = collectLivePreviewEntries(blueprint);
        if (entries.size() != blueprint.subLevels().size() || entries.isEmpty()) {
            return null;
        }

        final LivePreviewGeometry geometry = analyzeLive(entries);
        final EnumMap<SableBlueprintPreview.View, int[]> views = new EnumMap<>(SableBlueprintPreview.View.class);
        for (final SableBlueprintPreview.View view : SableBlueprintPreview.View.values()) {
            views.put(view, renderLiveView(geometry, view, SableBlueprintPreview.DEFAULT_RESOLUTION));
        }

        return new SableBlueprintPreview(
                SableBlueprintPreview.DEFAULT_RESOLUTION,
                geometry.basis().data().id(),
                geometry.bounds(),
                views
        );
    }

    private static List<LivePreviewEntry> collectLivePreviewEntries(final SableBlueprint blueprint) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return List.of();
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.of();
        }

        final List<LivePreviewEntry> entries = new ObjectArrayList<>(blueprint.subLevels().size());
        for (final SableBlueprint.SubLevelData data : blueprint.subLevels()) {
            final SubLevel subLevel = container.getSubLevel(data.sourceUuid());
            if (!(subLevel instanceof final ClientSubLevel clientSubLevel) || clientSubLevel.isRemoved()) {
                continue;
            }

            final BoundingBox3ic bounds = clientSubLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.volume() <= 0) {
                continue;
            }

            entries.add(new LivePreviewEntry(data, clientSubLevel, new Pose3d(clientSubLevel.renderPose())));
        }
        return entries;
    }

    private static LivePreviewGeometry analyzeLive(final List<LivePreviewEntry> entries) {
        final LivePreviewEntry basis = selectLiveBasis(entries);
        final BoundingBox3d bounds = new BoundingBox3d();
        boolean hasBounds = false;

        for (final LivePreviewEntry entry : entries) {
            final BoundingBox3ic localBounds = entry.subLevel().getPlot().getBoundingBox();
            for (final Vector3d corner : liveCornersInBasis(entry, basis, localBounds)) {
                if (hasBounds) {
                    bounds.expandTo(corner.x, corner.y, corner.z);
                } else {
                    bounds.setUnchecked(corner.x, corner.y, corner.z, corner.x, corner.y, corner.z);
                    hasBounds = true;
                }
            }
        }

        if (!hasBounds) {
            bounds.setUnchecked(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        }
        return new LivePreviewGeometry(List.copyOf(entries), basis, bounds);
    }

    private static List<Vector3d> liveCornersInBasis(final LivePreviewEntry entry,
                                                     final LivePreviewEntry basis,
                                                     final BoundingBox3ic localBounds) {
        final List<Vector3d> corners = new ObjectArrayList<>(8);
        for (final int x : new int[]{localBounds.minX(), localBounds.maxX() + 1}) {
            for (final int y : new int[]{localBounds.minY(), localBounds.maxY() + 1}) {
                for (final int z : new int[]{localBounds.minZ(), localBounds.maxZ() + 1}) {
                    final Vector3d point = new Vector3d(x, y, z);
                    entry.pose().transformPosition(point);
                    basis.pose().transformPositionInverse(point);
                    corners.add(point);
                }
            }
        }
        return corners;
    }

    private static LivePreviewEntry selectLiveBasis(final List<LivePreviewEntry> entries) {
        return entries.stream()
                .max(Comparator
                        .comparingLong((LivePreviewEntry entry) -> localVolume(entry.data()))
                        .thenComparingInt(entry -> entry.data().blocks().size()))
                .orElseThrow();
    }

    private static int[] renderLiveView(final LivePreviewGeometry geometry,
                                        final SableBlueprintPreview.View view,
                                        final int resolution) {
        final Minecraft minecraft = Minecraft.getInstance();
        final TextureTarget target = new TextureTarget(resolution, resolution, true, Minecraft.ON_OSX);
        final GlStateBackup glState = new GlStateBackup();
        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        final LiveCamera camera = liveCamera(geometry, view, resolution);
        final MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        boolean pushedModelView = false;

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

            renderLiveBlocks(geometry, camera, bufferSource);
            renderLiveBlockEntities(geometry, camera);
            renderLiveEntities(geometry, camera, bufferSource);
            bufferSource.endBatch();

            return readPixels(target, resolution);
        } finally {
            bufferSource.endBatch();
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

    private static LiveCamera liveCamera(final LivePreviewGeometry geometry,
                                         final SableBlueprintPreview.View view,
                                         final int resolution) {
        final ViewAxes axes = viewAxes(view);
        final Vector3d centerLocal = new Vector3d(
                (geometry.bounds().minX() + geometry.bounds().maxX()) * 0.5,
                (geometry.bounds().minY() + geometry.bounds().maxY()) * 0.5,
                (geometry.bounds().minZ() + geometry.bounds().maxZ()) * 0.5
        );
        final AxisRange rightRange = boundsRange(geometry.bounds(), axes.right());
        final AxisRange upRange = boundsRange(geometry.bounds(), axes.up());
        final AxisRange depthRange = boundsRange(geometry.bounds(), axes.forward());
        final double span = Math.max(rightRange.size(), upRange.size());
        final double paddedSpan = Math.max(1.0, span * resolution / Math.max(1.0, resolution - PADDING * 2.0));
        final double depth = Math.max(1.0, depthRange.size());
        final float halfSpan = (float) (paddedSpan * 0.5);
        final float cameraDistance = (float) (depth * 0.5 + 16.0);
        final float far = (float) (depth + 32.0);

        final Pose3d basisPose = geometry.basis().pose();
        final Vector3d forwardWorld = worldDirection(basisPose, axes.forward());
        final Vector3d upWorld = worldDirection(basisPose, axes.up());
        final Vector3d centerWorld = basisPose.transformPosition(new Vector3d(centerLocal));
        final Vector3d cameraPosition = new Vector3d(centerWorld).sub(new Vector3d(forwardWorld).mul(cameraDistance));
        final Matrix4f projection = new Matrix4f().ortho(-halfSpan, halfSpan, -halfSpan, halfSpan, 0.05F, far);
        final Matrix4f modelView = new Matrix4f().setLookAt(
                0.0F, 0.0F, 0.0F,
                (float) forwardWorld.x(), (float) forwardWorld.y(), (float) forwardWorld.z(),
                (float) upWorld.x(), (float) upWorld.y(), (float) upWorld.z()
        );

        return new LiveCamera(projection, modelView, cameraPosition);
    }

    private static void renderLiveBlocks(final LivePreviewGeometry geometry,
                                         final LiveCamera camera,
                                         final MultiBufferSource.BufferSource bufferSource) {
        final Minecraft minecraft = Minecraft.getInstance();
        final List<ClientSubLevel> subLevels = geometry.entries().stream()
                .map(LivePreviewEntry::subLevel)
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

    private static void renderLiveBlockEntities(final LivePreviewGeometry geometry,
                                                final LiveCamera camera) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final VanillaSubLevelBlockEntityRenderer blockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(
                minecraft.getBlockEntityRenderDispatcher(),
                minecraft.renderBuffers(),
                new Long2ObjectOpenHashMap<>()
        );
        final List<ClientSubLevel> subLevels = geometry.entries().stream()
                .map(LivePreviewEntry::subLevel)
                .toList();

        final Object visualizationManager = level == null ? null : flywheelVisualizationManager(level);
        final boolean drawingDiagram = setFlywheelDrawingDiagram(visualizationManager, true);
        try {
            if (visualizationManager != null) {
                renderLiveFlywheelBlockEntities(visualizationManager, geometry, camera, blockEntityRenderer);
            }

            SubLevelRenderDispatcher.get().renderBlockEntities(
                    subLevels,
                    blockEntityRenderer,
                    camera.position().x(),
                    camera.position().y(),
                    camera.position().z(),
                    0.0F
            );
        } finally {
            if (drawingDiagram) {
                setFlywheelDrawingDiagram(visualizationManager, false);
            }
        }
    }

    private static void renderLiveFlywheelBlockEntities(final Object visualizationManager,
                                                        final LivePreviewGeometry geometry,
                                                        final LiveCamera camera,
                                                        final VanillaSubLevelBlockEntityRenderer blockEntityRenderer) {
        final BlockEntityRenderDispatcherExtension dispatcher =
                (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();
        final Vector3d chunkOffset = new Vector3d();
        final Matrix4f transformation = new Matrix4f();
        final Matrix4f transformationInverse = new Matrix4f();

        try {
            for (final LivePreviewEntry entry : geometry.entries()) {
                final List<BlockEntity> blockEntities = flywheelEmbeddedBlockEntities(visualizationManager, entry.subLevel())
                        .stream()
                        .filter(BlueprintToolClientPreviewPostProcessor::shouldRenderFlywheelFallback)
                        .toList();
                if (blockEntities.isEmpty()) {
                    continue;
                }

                final SubLevelRenderData data = entry.subLevel().getRenderData();
                if (data == null) {
                    continue;
                }

                entry.subLevel().renderPose().rotationPoint().negate(chunkOffset.zero());
                data.getTransformation(camera.position().x(), camera.position().y(), camera.position().z(), transformation);

                final Vector3f c = transformation.invert(transformationInverse).transformPosition(new Vector3f());
                dispatcher.sable$setCameraPosition(new Vec3(
                        c.x - chunkOffset.x(),
                        c.y - chunkOffset.y(),
                        c.z - chunkOffset.z()
                ));

                final PoseStack matrices = new PoseStack();
                matrices.pushPose();
                matrices.mulPose(transformation);
                blockEntityRenderer.renderBlockEntities(blockEntities, matrices, 0.0F, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                matrices.popPose();
            }
        } finally {
            dispatcher.sable$setCameraPosition(null);
        }
    }

    private static @Nullable Object flywheelVisualizationManager(final ClientLevel level) {
        try {
            final Class<?> managerClass = Class.forName("dev.engine_room.flywheel.api.visualization.VisualizationManager");
            final Method get = findMethod(managerClass, "get", 1);
            return get == null ? null : get.invoke(null, level);
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean setFlywheelDrawingDiagram(final @Nullable Object visualizationManager,
                                                     final boolean drawingDiagram) {
        if (visualizationManager == null) {
            return false;
        }

        try {
            final Method setDrawingDiagram = findMethod(visualizationManager.getClass(), "sable$setDrawingDiagram", 1);
            if (setDrawingDiagram == null) {
                return false;
            }

            setDrawingDiagram.invoke(visualizationManager, drawingDiagram);
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static List<BlockEntity> flywheelEmbeddedBlockEntities(final Object visualizationManager,
                                                                   final ClientSubLevel subLevel) {
        try {
            final Method blockEntitiesMethod = findMethod(visualizationManager.getClass(), "blockEntities", 0);
            if (blockEntitiesMethod == null) {
                return List.of();
            }

            final Object visualManager = blockEntitiesMethod.invoke(visualizationManager);
            if (visualManager == null) {
                return List.of();
            }

            final Method getStorage = findMethod(visualManager.getClass(), "getStorage", 0);
            if (getStorage == null) {
                return List.of();
            }

            final Object storage = getStorage.invoke(visualManager);
            if (storage == null) {
                return List.of();
            }

            final Method getEmbedding = findMethod(storage.getClass(), "sable$getEmbeddingInfo", 1);
            if (getEmbedding == null) {
                return List.of();
            }

            final Object embedding = getEmbedding.invoke(storage, subLevel);
            if (embedding == null) {
                return List.of();
            }

            final Method blockEntities = findMethod(embedding.getClass(), "blockEntities", 0);
            if (blockEntities == null) {
                return List.of();
            }

            final Object value = blockEntities.invoke(embedding);
            if (!(value instanceof final Iterable<?> iterable)) {
                return List.of();
            }

            final List<BlockEntity> result = new ArrayList<>();
            for (final Object item : iterable) {
                if (item instanceof final BlockEntity blockEntity) {
                    result.add(blockEntity);
                }
            }
            return result;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return List.of();
        }
    }

    private static boolean shouldRenderFlywheelFallback(final BlockEntity blockEntity) {
        try {
            final Class<?> helperClass = Class.forName("dev.engine_room.flywheel.lib.visualization.VisualizationHelper");
            final Method skipVanillaRender = findMethod(helperClass, "skipVanillaRender", 1);
            if (skipVanillaRender == null) {
                return true;
            }

            return Boolean.TRUE.equals(skipVanillaRender.invoke(null, blockEntity));
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return true;
        }
    }

    private static @Nullable Method findMethod(final Class<?> type, final String name, final int parameterCount) {
        for (final Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static void renderLiveEntities(final LivePreviewGeometry geometry,
                                           final LiveCamera camera,
                                           final MultiBufferSource.BufferSource bufferSource) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        for (final LivePreviewEntry entry : geometry.entries()) {
            final List<Entity> entities = level.getEntitiesOfClass(
                    Entity.class,
                    entry.subLevel().getPlot().getBoundingBox().toAABB().inflate(16.0)
            );
            final PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.mulPose(camera.modelView());

            for (final Entity entity : entities) {
                if (entity instanceof Player || entity.isRemoved()) {
                    continue;
                }
                if (Sable.HELPER.getContaining(entity) != entry.subLevel()
                        && Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != entry.subLevel()) {
                    continue;
                }

                final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
                renderLevelEntity(
                        minecraft,
                        entity,
                        camera.position().x(),
                        camera.position().y(),
                        camera.position().z(),
                        partialTick,
                        poseStack,
                        bufferSource
                );
            }
            poseStack.popPose();
        }
    }

    private static void renderLevelEntity(final Minecraft minecraft,
                                          final Entity entity,
                                          final double cameraX,
                                          final double cameraY,
                                          final double cameraZ,
                                          final float partialTick,
                                          final PoseStack poseStack,
                                          final MultiBufferSource bufferSource) {
        final Method method = levelRenderEntityMethod(minecraft);
        if (method == null) {
            return;
        }

        try {
            method.invoke(minecraft.levelRenderer, entity, cameraX, cameraY, cameraZ, partialTick, poseStack, bufferSource);
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    private static @Nullable Method levelRenderEntityMethod(final Minecraft minecraft) {
        if (levelRenderEntityMethodResolved) {
            return levelRenderEntityMethod;
        }

        levelRenderEntityMethodResolved = true;
        try {
            final Method method = minecraft.levelRenderer.getClass().getDeclaredMethod(
                    "renderEntity",
                    Entity.class,
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    PoseStack.class,
                    MultiBufferSource.class
            );
            method.setAccessible(true);
            levelRenderEntityMethod = method;
            return method;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable SableBlueprintPreview renderBlueprintDataPreview(final SableBlueprint blueprint) {
        final List<SableBlueprint.SubLevelData> subLevels = blueprint.subLevels();
        if (subLevels.isEmpty()) {
            return null;
        }

        final List<PreviewEntry> entries = createPreviewEntries(subLevels);
        final PreviewGeometry geometry = analyze(entries);
        final EnumMap<SableBlueprintPreview.View, int[]> views = new EnumMap<>(SableBlueprintPreview.View.class);
        for (final SableBlueprintPreview.View view : SableBlueprintPreview.View.values()) {
            views.put(view, renderView(entries, geometry, view, SableBlueprintPreview.DEFAULT_RESOLUTION));
        }

        return new SableBlueprintPreview(
                SableBlueprintPreview.DEFAULT_RESOLUTION,
                geometry.basis().data().id(),
                geometry.bounds(),
                views
        );
    }

    private static List<PreviewEntry> createPreviewEntries(final List<SableBlueprint.SubLevelData> subLevels) {
        final Map<UUID, LivePose> livePoses = collectLivePoses(subLevels);
        final List<PreviewEntry> entries = new ObjectArrayList<>(subLevels.size());

        for (final SableBlueprint.SubLevelData subLevel : subLevels) {
            final LivePose livePose = livePoses.get(subLevel.sourceUuid());
            if (livePose != null) {
                entries.add(new PreviewEntry(subLevel, livePose.pose(), livePose.blocksOrigin()));
            } else {
                entries.add(new PreviewEntry(subLevel, new Pose3d(subLevel.relativePose()), BlockPos.ZERO));
            }
        }

        return entries;
    }

    private static Map<UUID, LivePose> collectLivePoses(final List<SableBlueprint.SubLevelData> subLevels) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return Map.of();
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Map.of();
        }

        final Map<UUID, LivePose> livePoses = new HashMap<>();
        for (final SableBlueprint.SubLevelData entry : subLevels) {
            final SubLevel subLevel = container.getSubLevel(entry.sourceUuid());
            if (!(subLevel instanceof final ClientSubLevel clientSubLevel) || clientSubLevel.isRemoved()) {
                continue;
            }

            final BoundingBox3ic bounds = clientSubLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.volume() <= 0) {
                continue;
            }

            livePoses.put(entry.sourceUuid(), new LivePose(
                    new Pose3d(clientSubLevel.renderPose()),
                    new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())
            ));
        }
        return livePoses;
    }

    private static int[] renderView(final List<PreviewEntry> entries,
                                    final PreviewGeometry geometry,
                                    final SableBlueprintPreview.View view,
                                    final int resolution) {
        final Minecraft minecraft = Minecraft.getInstance();
        final BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        final BlockEntityRenderDispatcher blockEntityRenderer = minecraft.getBlockEntityRenderDispatcher();
        final TextureTarget target = new TextureTarget(resolution, resolution, true, Minecraft.ON_OSX);
        final GlStateBackup glState = new GlStateBackup();
        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();

        RenderSystem.backupGlState(glState);
        RenderSystem.backupProjectionMatrix();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);

        try (final ByteBufferBuilder buffer = new ByteBufferBuilder(BUFFER_BYTES)) {
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(projectionMatrix(geometry.bounds(), resolution), VertexSorting.ORTHOGRAPHIC_Z);

            modelViewStack.pushMatrix();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            final MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(buffer);
            final Matrix4f screenFromBasis = screenFromBasis(geometry.bounds(), view, resolution);
            for (final PreviewEntry entry : entries) {
                renderSubLevel(blockRenderer, blockEntityRenderer, bufferSource, screenFromBasis, relativeMatrix(entry, geometry.basis()), entry);
            }
            bufferSource.endBatch();

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();

            return readPixels(target, resolution);
        } finally {
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.restoreGlState(glState);
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void renderSubLevel(final BlockRenderDispatcher blockRenderer,
                                       final BlockEntityRenderDispatcher blockEntityRenderer,
                                       final MultiBufferSource.BufferSource bufferSource,
                                       final Matrix4f screenFromBasis,
                                       final Matrix4f basisFromEntry,
                                       final PreviewEntry entry) {
        final SableBlueprint.SubLevelData data = entry.data();
        for (final SableBlueprint.BlockData block : data.blocks()) {
            if (block.paletteId() < 0 || block.paletteId() >= data.blockPalette().size()) {
                continue;
            }

            final BlockState state = data.blockPalette().get(block.paletteId());
            if (state.isAir()) {
                continue;
            }

            final BlockPos localPos = block.localPos();
            final PoseStack poseStack = new PoseStack();
            poseStack.mulPose(screenFromBasis);
            poseStack.mulPose(basisFromEntry);
            poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
            blockRenderer.renderSingleBlock(state, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        renderBlockEntities(blockEntityRenderer, bufferSource, screenFromBasis, basisFromEntry, entry);
        renderEntities(bufferSource, screenFromBasis, basisFromEntry, entry);
    }

    private static void renderBlockEntities(final BlockEntityRenderDispatcher blockEntityRenderer,
                                            final MultiBufferSource.BufferSource bufferSource,
                                            final Matrix4f screenFromBasis,
                                            final Matrix4f basisFromEntry,
                                            final PreviewEntry entry) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final SableBlueprint.SubLevelData data = entry.data();
        for (final SableBlueprint.BlockData block : data.blocks()) {
            if (!block.hasBlockEntityData()
                    || block.blockEntityDataId() < 0
                    || block.blockEntityDataId() >= data.blockEntities().size()
                    || block.paletteId() < 0
                    || block.paletteId() >= data.blockPalette().size()) {
                continue;
            }

            final BlockState state = data.blockPalette().get(block.paletteId());
            if (!state.hasBlockEntity()) {
                continue;
            }

            final BlockPos localPos = block.localPos();
            final CompoundTag tag = data.blockEntities().get(block.blockEntityDataId()).copy();
            tag.putInt("x", localPos.getX());
            tag.putInt("y", localPos.getY());
            tag.putInt("z", localPos.getZ());

            try {
                final BlockEntity blockEntity = BlockEntity.loadStatic(localPos, state, tag, level.registryAccess());
                if (blockEntity == null || blockEntityRenderer.getRenderer(blockEntity) == null) {
                    continue;
                }

                blockEntity.setLevel(level);
                final PoseStack poseStack = new PoseStack();
                poseStack.mulPose(screenFromBasis);
                poseStack.mulPose(basisFromEntry);
                poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
                blockEntityRenderer.render(blockEntity, 0.0F, poseStack, bufferSource);
            } catch (final RuntimeException ignored) {
                // Preview rendering must not make saving fail because one optional renderer cannot run off-world.
            }
        }
    }

    private static void renderEntities(final MultiBufferSource.BufferSource bufferSource,
                                       final Matrix4f screenFromBasis,
                                       final Matrix4f basisFromEntry,
                                       final PreviewEntry entry) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final PoseStack poseStack = new PoseStack();
        poseStack.mulPose(screenFromBasis);
        poseStack.mulPose(basisFromEntry);

        for (final SableBlueprint.EntityData data : entry.data().entities()) {
            try {
                final CompoundTag tag = data.tag().copy();
                tag.remove("UUID");
                writeEntityPos(tag, data.localPos());
                final Entity entity = EntityType.create(tag, level).orElse(null);
                if (entity == null) {
                    continue;
                }

                entity.setPos(data.localPos().x(), data.localPos().y(), data.localPos().z());
                minecraft.getEntityRenderDispatcher().render(
                        entity,
                        data.localPos().x(),
                        data.localPos().y(),
                        data.localPos().z(),
                        entity.getYRot(),
                        0.0F,
                        poseStack,
                        bufferSource,
                        LightTexture.FULL_BRIGHT
                );
            } catch (final RuntimeException ignored) {
                // See block entity note above: previews should degrade gracefully.
            }
        }
    }

    private static void writeEntityPos(final CompoundTag tag, final Vector3dc pos) {
        final ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(pos.x()));
        posTag.add(DoubleTag.valueOf(pos.y()));
        posTag.add(DoubleTag.valueOf(pos.z()));
        tag.put("Pos", posTag);
    }

    private static int[] readPixels(final TextureTarget target, final int resolution) {
        final int[] pixels = new int[resolution * resolution];
        try (final NativeImage image = new NativeImage(resolution, resolution, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            for (int y = 0; y < resolution; y++) {
                final int row = y * resolution;
                for (int x = 0; x < resolution; x++) {
                    pixels[row + x] = abgrToArgb(image.getPixelRGBA(x, y));
                }
            }
        }
        return pixels;
    }

    private static int abgrToArgb(final int abgr) {
        final int alpha = abgr & 0xFF000000;
        final int blue = (abgr >> 16) & 0xFF;
        final int green = (abgr >> 8) & 0xFF;
        final int red = abgr & 0xFF;
        return alpha | red << 16 | green << 8 | blue;
    }

    private static Matrix4f projectionMatrix(final BoundingBox3d bounds, final int resolution) {
        final float depth = (float) Math.max(16.0, maxAbs(bounds) + 16.0);
        return new Matrix4f().ortho(0.0F, resolution, resolution, 0.0F, -depth, depth);
    }

    private static Matrix4f screenFromBasis(final BoundingBox3d bounds,
                                            final SableBlueprintPreview.View view,
                                            final int resolution) {
        final double minU = boundsMinU(bounds, view);
        final double maxU = boundsMaxU(bounds, view);
        final double minV = boundsMinV(bounds, view);
        final double maxV = boundsMaxV(bounds, view);
        final double rangeU = Math.max(maxU - minU, 1.0);
        final double rangeV = Math.max(maxV - minV, 1.0);
        final double drawable = Math.max(1.0, resolution - PADDING * 2.0);
        final float scale = (float) (drawable / Math.max(rangeU, rangeV));
        final float offsetU = (float) ((resolution - rangeU * scale) * 0.5);
        final float offsetV = (float) ((resolution - rangeV * scale) * 0.5);

        return switch (view) {
            case TOP -> new Matrix4f()
                    .translation(offsetU, offsetV, 0.0F)
                    .scale(scale, scale, 1.0F)
                    .rotateX((float) (Math.PI * 0.5))
                    .translate((float) -bounds.minX(), 0.0F, (float) -bounds.maxZ());
            case BOTTOM -> new Matrix4f()
                    .translation(offsetU, offsetV, 0.0F)
                    .scale(scale, scale, -1.0F)
                    .rotateX((float) (Math.PI * 0.5))
                    .translate((float) -bounds.minX(), 0.0F, (float) -bounds.maxZ());
            case FRONT -> new Matrix4f()
                    .translation(offsetU, offsetV, 0.0F)
                    .scale(scale, -scale, 1.0F)
                    .translate((float) -bounds.minX(), (float) -bounds.maxY(), 0.0F);
            case BACK -> new Matrix4f()
                    .translation(offsetU, offsetV, 0.0F)
                    .scale(scale, -scale, -1.0F)
                    .translate((float) -bounds.minX(), (float) -bounds.maxY(), 0.0F);
            case RIGHT -> new Matrix4f()
                    .translation(offsetU, offsetV, 0.0F)
                    .scale(-scale, -scale, 1.0F)
                    .rotateY((float) (-Math.PI * 0.5))
                    .translate(0.0F, (float) -bounds.maxY(), (float) -bounds.minZ());
            case LEFT -> new Matrix4f()
                    .translation(offsetU, offsetV, 0.0F)
                    .scale(scale, -scale, 1.0F)
                    .rotateY((float) (Math.PI * 0.5))
                    .translate(0.0F, (float) -bounds.maxY(), (float) -bounds.minZ());
        };
    }

    private static Matrix4f relativeMatrix(final PreviewEntry entry,
                                           final PreviewEntry basis) {
        final Matrix4d entryMatrix = worldFromLocalMatrix(entry);
        final Matrix4d basisInverse = worldFromLocalMatrix(basis).invert();
        return new Matrix4f(basisInverse.mul(entryMatrix));
    }

    private static Matrix4d worldFromLocalMatrix(final PreviewEntry entry) {
        return entry.pose()
                .bakeIntoMatrix(new Matrix4d())
                .translate(entry.blocksOrigin().getX(), entry.blocksOrigin().getY(), entry.blocksOrigin().getZ());
    }

    private static PreviewGeometry analyze(final List<PreviewEntry> entries) {
        final PreviewEntry basis = selectBasis(entries);
        final BoundingBox3d bounds = new BoundingBox3d();
        boolean hasBounds = false;

        for (final PreviewEntry entry : entries) {
            final SableBlueprint.SubLevelData data = entry.data();
            for (final SableBlueprint.BlockData block : data.blocks()) {
                if (block.paletteId() < 0 || block.paletteId() >= data.blockPalette().size()) {
                    continue;
                }
                if (data.blockPalette().get(block.paletteId()).isAir()) {
                    continue;
                }

                final List<Vector3d> corners = blockCornersInBasis(entry, basis, block.localPos());
                for (final Vector3d corner : corners) {
                    if (hasBounds) {
                        bounds.expandTo(corner.x, corner.y, corner.z);
                    } else {
                        bounds.setUnchecked(corner.x, corner.y, corner.z, corner.x, corner.y, corner.z);
                        hasBounds = true;
                    }
                }
            }

            for (final SableBlueprint.EntityData entity : data.entities()) {
                final List<Vector3d> corners = entityCornersInBasis(entry, basis, entity.localPos());
                for (final Vector3d corner : corners) {
                    if (hasBounds) {
                        bounds.expandTo(corner.x, corner.y, corner.z);
                    } else {
                        bounds.setUnchecked(corner.x, corner.y, corner.z, corner.x, corner.y, corner.z);
                        hasBounds = true;
                    }
                }
            }
        }

        if (!hasBounds) {
            bounds.setUnchecked(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        }
        return new PreviewGeometry(basis, bounds);
    }

    private static List<Vector3d> blockCornersInBasis(final PreviewEntry entry,
                                                      final PreviewEntry basis,
                                                      final BlockPos localPos) {
        final List<Vector3d> corners = new ObjectArrayList<>(8);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    final Vector3d point = new Vector3d(
                            localPos.getX() + entry.blocksOrigin().getX() + dx,
                            localPos.getY() + entry.blocksOrigin().getY() + dy,
                            localPos.getZ() + entry.blocksOrigin().getZ() + dz
                    );
                    entry.pose().transformPosition(point);
                    basis.pose().transformPositionInverse(point);
                    point.sub(basis.blocksOrigin().getX(), basis.blocksOrigin().getY(), basis.blocksOrigin().getZ());
                    corners.add(point);
                }
            }
        }
        return corners;
    }

    private static List<Vector3d> entityCornersInBasis(final PreviewEntry entry,
                                                       final PreviewEntry basis,
                                                       final Vector3dc localPos) {
        final List<Vector3d> corners = new ObjectArrayList<>(8);
        for (final double dx : new double[]{-ENTITY_BOUNDS_PADDING, ENTITY_BOUNDS_PADDING}) {
            for (final double dy : new double[]{0.0, ENTITY_BOUNDS_HEIGHT}) {
                for (final double dz : new double[]{-ENTITY_BOUNDS_PADDING, ENTITY_BOUNDS_PADDING}) {
                    final Vector3d point = new Vector3d(
                            localPos.x() + entry.blocksOrigin().getX() + dx,
                            localPos.y() + entry.blocksOrigin().getY() + dy,
                            localPos.z() + entry.blocksOrigin().getZ() + dz
                    );
                    entry.pose().transformPosition(point);
                    basis.pose().transformPositionInverse(point);
                    point.sub(basis.blocksOrigin().getX(), basis.blocksOrigin().getY(), basis.blocksOrigin().getZ());
                    corners.add(point);
                }
            }
        }
        return corners;
    }

    private static PreviewEntry selectBasis(final List<PreviewEntry> entries) {
        return entries.stream()
                .max(Comparator
                        .comparingLong((PreviewEntry entry) -> localVolume(entry.data()))
                        .thenComparingInt(entry -> entry.data().blocks().size()))
                .orElseThrow();
    }

    private static long localVolume(final SableBlueprint.SubLevelData entry) {
        final int width = entry.localBounds().maxX() - entry.localBounds().minX() + 1;
        final int height = entry.localBounds().maxY() - entry.localBounds().minY() + 1;
        final int depth = entry.localBounds().maxZ() - entry.localBounds().minZ() + 1;
        return (long) Math.max(width, 0) * Math.max(height, 0) * Math.max(depth, 0);
    }

    private static double maxAbs(final BoundingBox3d bounds) {
        double value = 0.0;
        value = Math.max(value, Math.abs(bounds.minX()));
        value = Math.max(value, Math.abs(bounds.minY()));
        value = Math.max(value, Math.abs(bounds.minZ()));
        value = Math.max(value, Math.abs(bounds.maxX()));
        value = Math.max(value, Math.abs(bounds.maxY()));
        value = Math.max(value, Math.abs(bounds.maxZ()));
        return value;
    }

    private static double boundsMinU(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM, FRONT, BACK -> bounds.minX();
            case RIGHT, LEFT -> bounds.minZ();
        };
    }

    private static double boundsMaxU(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM, FRONT, BACK -> bounds.maxX();
            case RIGHT, LEFT -> bounds.maxZ();
        };
    }

    private static double boundsMinV(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM -> bounds.minZ();
            case FRONT, BACK, RIGHT, LEFT -> bounds.minY();
        };
    }

    private static double boundsMaxV(final BoundingBox3d bounds, final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP, BOTTOM -> bounds.maxZ();
            case FRONT, BACK, RIGHT, LEFT -> bounds.maxY();
        };
    }

    private static ViewAxes viewAxes(final SableBlueprintPreview.View view) {
        return switch (view) {
            case TOP -> new ViewAxes(new Vector3d(1.0, 0.0, 0.0), new Vector3d(0.0, 0.0, -1.0), new Vector3d(0.0, -1.0, 0.0));
            case BOTTOM -> new ViewAxes(new Vector3d(1.0, 0.0, 0.0), new Vector3d(0.0, 0.0, 1.0), new Vector3d(0.0, 1.0, 0.0));
            case FRONT -> new ViewAxes(new Vector3d(1.0, 0.0, 0.0), new Vector3d(0.0, 1.0, 0.0), new Vector3d(0.0, 0.0, 1.0));
            case BACK -> new ViewAxes(new Vector3d(-1.0, 0.0, 0.0), new Vector3d(0.0, 1.0, 0.0), new Vector3d(0.0, 0.0, -1.0));
            case RIGHT -> new ViewAxes(new Vector3d(0.0, 0.0, -1.0), new Vector3d(0.0, 1.0, 0.0), new Vector3d(-1.0, 0.0, 0.0));
            case LEFT -> new ViewAxes(new Vector3d(0.0, 0.0, 1.0), new Vector3d(0.0, 1.0, 0.0), new Vector3d(1.0, 0.0, 0.0));
        };
    }

    private static Vector3d worldDirection(final Pose3d pose, final Vector3d localDirection) {
        final Vector3d direction = new Vector3d(localDirection).normalize();
        pose.orientation().transform(direction);
        return direction.normalize();
    }

    private static AxisRange boundsRange(final BoundingBox3d bounds, final Vector3d axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (final double x : new double[]{bounds.minX(), bounds.maxX()}) {
            for (final double y : new double[]{bounds.minY(), bounds.maxY()}) {
                for (final double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
                    final double value = x * axis.x() + y * axis.y() + z * axis.z();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        return new AxisRange(min, max);
    }

    private record LivePreviewEntry(SableBlueprint.SubLevelData data, ClientSubLevel subLevel, Pose3d pose) {
    }

    private record LivePreviewGeometry(List<LivePreviewEntry> entries, LivePreviewEntry basis, BoundingBox3d bounds) {
    }

    private record LiveCamera(Matrix4f projection, Matrix4f modelView, Vector3d position) {
    }

    private record ViewAxes(Vector3d right, Vector3d up, Vector3d forward) {
    }

    private record AxisRange(double min, double max) {
        private double size() {
            return this.max - this.min;
        }
    }

    private record PreviewEntry(SableBlueprint.SubLevelData data, Pose3d pose, BlockPos blocksOrigin) {
    }

    private record LivePose(Pose3d pose, BlockPos blocksOrigin) {
    }

    private record PreviewGeometry(PreviewEntry basis, BoundingBox3d bounds) {
    }
}
