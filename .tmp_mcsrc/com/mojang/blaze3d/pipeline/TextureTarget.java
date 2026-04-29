package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TextureTarget extends RenderTarget {
    public TextureTarget(int width, int height, boolean useDepth, boolean clearError) {
        super(useDepth);
        RenderSystem.assertOnRenderThreadOrInit();
        this.resize(width, height, clearError);
    }
}
