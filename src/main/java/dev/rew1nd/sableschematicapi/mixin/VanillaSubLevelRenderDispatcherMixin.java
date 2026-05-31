package dev.rew1nd.sableschematicapi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierCoordinates;
import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierShaderUniforms;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class VanillaSubLevelRenderDispatcherMixin {
    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void sableSchematicApi$clearFrontierUniforms(final Iterable<ClientSubLevel> sublevels,
                                                         final RenderType renderType,
                                                         final ShaderInstance shader,
                                                         final double cameraX,
                                                         final double cameraY,
                                                         final double cameraZ,
                                                         final Matrix4f modelView,
                                                         final Matrix4f projection,
                                                         final float partialTicks,
                                                         final CallbackInfo ci) {
        SableFrontierShaderUniforms.disable(shader);
    }

    @Redirect(method = "renderBlockEntities", at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/render/dispatcher/SubLevelRenderDispatcher$BlockEntityRenderer;renderBlockEntities(Ljava/util/Collection;Lcom/mojang/blaze3d/vertex/PoseStack;FDDD)V"
    ))
    private void sableSchematicApi$filterFrontierBlockEntities(final SubLevelRenderDispatcher.BlockEntityRenderer renderer,
                                                               final Collection<BlockEntity> blockEntities,
                                                               final PoseStack poseStack,
                                                               final float partialTick,
                                                               final double cameraX,
                                                               final double cameraY,
                                                               final double cameraZ) {
        final Collection<BlockEntity> visible = visibleBlockEntities(blockEntities);
        if (!visible.isEmpty()) {
            renderer.renderBlockEntities(visible, poseStack, partialTick, cameraX, cameraY, cameraZ);
        }
    }

    @Redirect(method = "renderBlockEntities", at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/render/dispatcher/SubLevelRenderDispatcher$BlockEntityRenderer;renderSingleBE(Lnet/minecraft/world/level/block/entity/BlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FDDD)V"
    ))
    private void sableSchematicApi$filterFrontierSingleBlockEntity(final SubLevelRenderDispatcher.BlockEntityRenderer renderer,
                                                                   final BlockEntity blockEntity,
                                                                   final PoseStack poseStack,
                                                                   final float partialTick,
                                                                   final double cameraX,
                                                                   final double cameraY,
                                                                   final double cameraZ) {
        if (SableFrontierCoordinates.isBlockEntityVisible(blockEntity)) {
            renderer.renderSingleBE(blockEntity, poseStack, partialTick, cameraX, cameraY, cameraZ);
        }
    }

    private static Collection<BlockEntity> visibleBlockEntities(final Collection<BlockEntity> blockEntities) {
        List<BlockEntity> filtered = null;
        int index = 0;
        for (final BlockEntity blockEntity : blockEntities) {
            if (SableFrontierCoordinates.isBlockEntityVisible(blockEntity)) {
                if (filtered != null) {
                    filtered.add(blockEntity);
                }
            } else if (filtered == null) {
                filtered = new ArrayList<>(blockEntities.size());
                int copyIndex = 0;
                for (final BlockEntity previous : blockEntities) {
                    if (copyIndex++ >= index) {
                        break;
                    }
                    filtered.add(previous);
                }
            }
            index++;
        }
        return filtered == null ? blockEntities : filtered;
    }
}
