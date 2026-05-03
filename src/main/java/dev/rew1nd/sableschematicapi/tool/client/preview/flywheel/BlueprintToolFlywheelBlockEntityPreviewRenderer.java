package dev.rew1nd.sableschematicapi.tool.client.preview.flywheel;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rew1nd.sableschematicapi.tool.client.preview.camera.BlueprintToolPreviewCamera;
import dev.rew1nd.sableschematicapi.tool.client.preview.live.BlueprintToolLivePreviewEntry;
import dev.rew1nd.sableschematicapi.tool.client.preview.live.BlueprintToolLivePreviewGeometry;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BlueprintToolFlywheelBlockEntityPreviewRenderer {
    private BlueprintToolFlywheelBlockEntityPreviewRenderer() {
    }

    public static @Nullable Object visualizationManager(final ClientLevel level) {
        try {
            final Class<?> managerClass = Class.forName("dev.engine_room.flywheel.api.visualization.VisualizationManager");
            final Method get = findMethod(managerClass, "get", 1);
            return get == null ? null : get.invoke(null, level);
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean setDrawingDiagram(final @Nullable Object visualizationManager,
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

    public static void render(final Object visualizationManager,
                              final BlueprintToolLivePreviewGeometry geometry,
                              final BlueprintToolPreviewCamera camera,
                              final VanillaSubLevelBlockEntityRenderer blockEntityRenderer) {
        final BlockEntityRenderDispatcherExtension dispatcher =
                (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();
        final Vector3d chunkOffset = new Vector3d();
        final Matrix4f transformation = new Matrix4f();
        final Matrix4f transformationInverse = new Matrix4f();

        try {
            for (final BlueprintToolLivePreviewEntry entry : geometry.entries()) {
                final List<BlockEntity> blockEntities = embeddedBlockEntities(visualizationManager, entry.subLevel())
                        .stream()
                        .filter(BlueprintToolFlywheelBlockEntityPreviewRenderer::shouldRenderFallback)
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

    private static List<BlockEntity> embeddedBlockEntities(final Object visualizationManager,
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

    private static boolean shouldRenderFallback(final BlockEntity blockEntity) {
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
}
