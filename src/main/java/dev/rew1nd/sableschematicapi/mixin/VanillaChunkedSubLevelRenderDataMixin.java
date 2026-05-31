package dev.rew1nd.sableschematicapi.mixin;

import dev.rew1nd.sableschematicapi.client.frontier.SableFrontierShaderUniforms;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class VanillaChunkedSubLevelRenderDataMixin {
    @Shadow
    @Final
    private Vector3d origin;

    @Shadow
    @Final
    private ClientSubLevel subLevel;

    @Inject(method = "renderChunkedSubLevel", at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/ShaderInstance;CHUNK_OFFSET:Lcom/mojang/blaze3d/shaders/Uniform;"
    ))
    private void sableSchematicApi$applyFrontierUniforms(final RenderType layer,
                                                         final ShaderInstance shader,
                                                         final Matrix4f modelView,
                                                         final double camX,
                                                         final double camY,
                                                         final double camZ,
                                                         final CallbackInfo ci) {
        SableFrontierShaderUniforms.applyVanillaChunked(shader, this.subLevel, this.origin, camX, camY, camZ);
    }
}
