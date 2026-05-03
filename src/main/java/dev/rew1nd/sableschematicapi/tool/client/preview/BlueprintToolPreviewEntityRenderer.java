package dev.rew1nd.sableschematicapi.tool.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;

public interface BlueprintToolPreviewEntityRenderer {
    void render(Entity entity,
                double x,
                double y,
                double z,
                float entityYaw,
                float partialTicks,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight);
}
