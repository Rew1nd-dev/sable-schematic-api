package dev.rew1nd.sableschematicapi.tool.client.preview.stored;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolPreviewEntityRenderers;
import dev.rew1nd.sableschematicapi.tool.client.preview.camera.BlueprintToolPreviewCamera;
import dev.rew1nd.sableschematicapi.tool.client.preview.camera.BlueprintToolPreviewCameras;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewBounds;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewConstants;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewEntityNbt;
import dev.rew1nd.sableschematicapi.tool.client.preview.util.BlueprintToolPreviewFramebuffer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.GlStateBackup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BlueprintToolStoredPreviewRenderer {
    private BlueprintToolStoredPreviewRenderer() {
    }

    public static @Nullable SableBlueprintPreview render(final SableBlueprint blueprint) {
        final List<SableBlueprint.SubLevelData> subLevels = blueprint.subLevels();
        if (subLevels.isEmpty()) {
            return null;
        }

        final List<BlueprintToolStoredPreviewEntry> entries = createEntries(subLevels);
        final BlueprintToolStoredPreviewGeometry geometry = analyze(entries);
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

    private static List<BlueprintToolStoredPreviewEntry> createEntries(final List<SableBlueprint.SubLevelData> subLevels) {
        final Map<UUID, BlueprintToolLivePose> livePoses = collectLivePoses(subLevels);
        final List<BlueprintToolStoredPreviewEntry> entries = new ObjectArrayList<>(subLevels.size());

        for (final SableBlueprint.SubLevelData subLevel : subLevels) {
            final BlueprintToolLivePose livePose = livePoses.get(subLevel.sourceUuid());
            if (livePose != null) {
                entries.add(new BlueprintToolStoredPreviewEntry(subLevel, livePose.pose(), livePose.blocksOrigin()));
            } else {
                entries.add(new BlueprintToolStoredPreviewEntry(subLevel, new Pose3d(subLevel.relativePose()), BlockPos.ZERO));
            }
        }

        return entries;
    }

    private static Map<UUID, BlueprintToolLivePose> collectLivePoses(final List<SableBlueprint.SubLevelData> subLevels) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return Map.of();
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Map.of();
        }

        final Map<UUID, BlueprintToolLivePose> livePoses = new HashMap<>();
        for (final SableBlueprint.SubLevelData entry : subLevels) {
            final SubLevel subLevel = container.getSubLevel(entry.sourceUuid());
            if (!(subLevel instanceof final ClientSubLevel clientSubLevel) || clientSubLevel.isRemoved()) {
                continue;
            }

            final BoundingBox3ic bounds = clientSubLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.volume() <= 0) {
                continue;
            }

            livePoses.put(entry.sourceUuid(), new BlueprintToolLivePose(
                    new Pose3d(clientSubLevel.renderPose()),
                    new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ())
            ));
        }
        return livePoses;
    }

    private static int[] renderView(final List<BlueprintToolStoredPreviewEntry> entries,
                                    final BlueprintToolStoredPreviewGeometry geometry,
                                    final SableBlueprintPreview.View view,
                                    final int resolution) {
        final Minecraft minecraft = Minecraft.getInstance();
        final BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        final BlockEntityRenderDispatcher blockEntityRenderer = minecraft.getBlockEntityRenderDispatcher();
        final TextureTarget target = new TextureTarget(resolution, resolution, true, Minecraft.ON_OSX);
        final GlStateBackup glState = new GlStateBackup();
        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        final BlueprintToolPreviewCamera camera = BlueprintToolPreviewCameras.storedCamera(geometry.bounds(), view, resolution);

        RenderSystem.backupGlState(glState);
        RenderSystem.backupProjectionMatrix();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);

        try (final ByteBufferBuilder buffer = new ByteBufferBuilder(BlueprintToolPreviewConstants.BUFFER_BYTES)) {
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setProjectionMatrix(camera.projection(), VertexSorting.ORTHOGRAPHIC_Z);

            modelViewStack.pushMatrix();
            modelViewStack.identity();
            modelViewStack.mul(camera.modelView());
            RenderSystem.applyModelViewMatrix();

            final MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(buffer);
            final Matrix4f cameraSpaceFromBasis = BlueprintToolPreviewCameras.cameraSpaceFromBasis(camera);
            for (final BlueprintToolStoredPreviewEntry entry : entries) {
                renderSubLevel(blockRenderer, blockEntityRenderer, bufferSource, cameraSpaceFromBasis, relativeMatrix(entry, geometry.basis()), entry);
            }
            bufferSource.endBatch();

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();

            return BlueprintToolPreviewFramebuffer.readPixels(target, resolution);
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
                                       final Matrix4f cameraSpaceFromBasis,
                                       final Matrix4f basisFromEntry,
                                       final BlueprintToolStoredPreviewEntry entry) {
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
            poseStack.mulPose(cameraSpaceFromBasis);
            poseStack.mulPose(basisFromEntry);
            poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
            blockRenderer.renderSingleBlock(state, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        renderBlockEntities(blockEntityRenderer, bufferSource, cameraSpaceFromBasis, basisFromEntry, entry);
        renderEntities(bufferSource, cameraSpaceFromBasis, basisFromEntry, entry);
    }

    private static void renderBlockEntities(final BlockEntityRenderDispatcher blockEntityRenderer,
                                            final MultiBufferSource.BufferSource bufferSource,
                                            final Matrix4f cameraSpaceFromBasis,
                                            final Matrix4f basisFromEntry,
                                            final BlueprintToolStoredPreviewEntry entry) {
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
                poseStack.mulPose(cameraSpaceFromBasis);
                poseStack.mulPose(basisFromEntry);
                poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
                blockEntityRenderer.render(blockEntity, 0.0F, poseStack, bufferSource);
            } catch (final RuntimeException ignored) {
                // Preview rendering must not make saving fail because one optional renderer cannot run off-world.
            }
        }
    }

    private static void renderEntities(final MultiBufferSource.BufferSource bufferSource,
                                       final Matrix4f cameraSpaceFromBasis,
                                       final Matrix4f basisFromEntry,
                                       final BlueprintToolStoredPreviewEntry entry) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final PoseStack poseStack = new PoseStack();
        poseStack.mulPose(cameraSpaceFromBasis);
        poseStack.mulPose(basisFromEntry);

        for (final SableBlueprint.EntityData data : entry.data().entities()) {
            try {
                final CompoundTag tag = data.tag().copy();
                tag.remove("UUID");
                BlueprintToolPreviewEntityNbt.writeEntityPos(tag, data.localPos());
                final Entity entity = EntityType.create(tag, level).orElse(null);
                if (entity == null) {
                    continue;
                }

                entity.setPos(data.localPos().x(), data.localPos().y(), data.localPos().z());
                BlueprintToolPreviewEntityRenderers.render(
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

    private static Matrix4f relativeMatrix(final BlueprintToolStoredPreviewEntry entry,
                                           final BlueprintToolStoredPreviewEntry basis) {
        final Matrix4d entryMatrix = worldFromLocalMatrix(entry);
        final Matrix4d basisInverse = worldFromLocalMatrix(basis).invert();
        return new Matrix4f(basisInverse.mul(entryMatrix));
    }

    private static Matrix4d worldFromLocalMatrix(final BlueprintToolStoredPreviewEntry entry) {
        return entry.pose()
                .bakeIntoMatrix(new Matrix4d())
                .translate(entry.blocksOrigin().getX(), entry.blocksOrigin().getY(), entry.blocksOrigin().getZ());
    }

    private static BlueprintToolStoredPreviewGeometry analyze(final List<BlueprintToolStoredPreviewEntry> entries) {
        final BlueprintToolStoredPreviewEntry basis = selectBasis(entries);
        final BoundingBox3d bounds = new BoundingBox3d();
        boolean hasBounds = false;

        for (final BlueprintToolStoredPreviewEntry entry : entries) {
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
        return new BlueprintToolStoredPreviewGeometry(basis, bounds);
    }

    private static List<Vector3d> blockCornersInBasis(final BlueprintToolStoredPreviewEntry entry,
                                                      final BlueprintToolStoredPreviewEntry basis,
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

    private static List<Vector3d> entityCornersInBasis(final BlueprintToolStoredPreviewEntry entry,
                                                       final BlueprintToolStoredPreviewEntry basis,
                                                       final Vector3dc localPos) {
        final List<Vector3d> corners = new ObjectArrayList<>(8);
        for (final double dx : new double[]{-BlueprintToolPreviewConstants.ENTITY_BOUNDS_PADDING, BlueprintToolPreviewConstants.ENTITY_BOUNDS_PADDING}) {
            for (final double dy : new double[]{0.0, BlueprintToolPreviewConstants.ENTITY_BOUNDS_HEIGHT}) {
                for (final double dz : new double[]{-BlueprintToolPreviewConstants.ENTITY_BOUNDS_PADDING, BlueprintToolPreviewConstants.ENTITY_BOUNDS_PADDING}) {
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

    private static BlueprintToolStoredPreviewEntry selectBasis(final List<BlueprintToolStoredPreviewEntry> entries) {
        return entries.stream()
                .max(Comparator
                        .comparingLong((BlueprintToolStoredPreviewEntry entry) -> BlueprintToolPreviewBounds.localVolume(entry.data()))
                        .thenComparingInt(entry -> entry.data().blocks().size()))
                .orElseThrow();
    }
}
