package dev.rew1nd.sableschematicapi.tool.client.preview.util;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

public final class BlueprintToolPreviewFramebuffer {
    private BlueprintToolPreviewFramebuffer() {
    }

    public static int[] readPixels(final TextureTarget target, final int resolution) {
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
}
