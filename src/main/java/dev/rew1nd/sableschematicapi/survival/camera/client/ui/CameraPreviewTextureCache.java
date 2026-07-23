package dev.rew1nd.sableschematicapi.survival.camera.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.platform.NativeImage;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import dev.rew1nd.sableschematicapi.tool.client.preview.BlueprintToolLocalPreviewCache;
import dev.rew1nd.sableschematicapi.tool.client.storage.BlueprintToolLocalFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Per-screen GPU texture cache for camera blueprint thumbnails. */
final class CameraPreviewTextureCache implements AutoCloseable {
    private static final int TEXTURE_SIZE = 128;

    private final Map<TextureKey, CachedTexture> textures = new HashMap<>();
    private boolean closed;

    void draw(final GUIContext context,
              final @Nullable BlueprintToolLocalFiles.Entry entry,
              final SableBlueprintPreview.View view,
              final int x,
              final int y,
              final int width,
              final int height) {
        if (closed || entry == null || width <= 0 || height <= 0) {
            return;
        }
        final SableBlueprintPreview preview = BlueprintToolLocalPreviewCache.preview(entry);
        if (preview == null) {
            return;
        }

        final TextureKey key = new TextureKey(entry.path().toAbsolutePath().normalize(), view);
        CachedTexture cached = textures.get(key);
        if (cached == null || cached.preview() != preview) {
            if (cached != null) {
                Minecraft.getInstance().getTextureManager().release(cached.location());
            }
            cached = create(preview, view);
            textures.put(key, cached);
        }
        context.graphics.blit(
                cached.location(),
                x,
                y,
                width,
                height,
                0.0F,
                0.0F,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE
        );
    }

    private static CachedTexture create(final SableBlueprintPreview preview,
                                        final SableBlueprintPreview.View view) {
        final int resolution = preview.resolution();
        final int[] pixels = preview.pixels(view);
        final NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);
        DynamicTexture texture = null;
        try {
            for (int y = 0; y < TEXTURE_SIZE; y++) {
                final int sourceY = y * resolution / TEXTURE_SIZE;
                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    final int sourceX = x * resolution / TEXTURE_SIZE;
                    image.setPixelRGBA(
                            x,
                            y,
                            FastColor.ABGR32.fromArgb32(pixels[sourceY * resolution + sourceX])
                    );
                }
            }
            texture = new DynamicTexture(image);
            final ResourceLocation location = Minecraft.getInstance().getTextureManager()
                    .register("sable_camera_preview", texture);
            return new CachedTexture(preview, location);
        } catch (final RuntimeException exception) {
            if (texture == null) {
                image.close();
            } else {
                texture.close();
            }
            throw exception;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        final var textureManager = Minecraft.getInstance().getTextureManager();
        for (final CachedTexture cached : textures.values()) {
            textureManager.release(cached.location());
        }
        textures.clear();
    }

    private record TextureKey(Path path, SableBlueprintPreview.View view) {
    }

    private record CachedTexture(SableBlueprintPreview preview, ResourceLocation location) {
    }
}
