package dev.rew1nd.sableschematicapi.survival.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.mojang.blaze3d.platform.NativeImage;
import dev.rew1nd.sableschematicapi.blueprint.preview.SableBlueprintPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Per-screen generic GPU preview cache. */
public final class BlueprintPreviewTextureCache<K> implements AutoCloseable {
    private static final int TEXTURE_SIZE = 128;

    private final Function<K, @Nullable SableBlueprintPreview> previewSource;
    private final String textureName;
    private final Map<TextureKey<K>, CachedTexture> textures = new HashMap<>();
    private boolean closed;

    public BlueprintPreviewTextureCache(final String textureName,
                                        final Function<K, @Nullable SableBlueprintPreview> previewSource) {
        this.textureName = Objects.requireNonNull(textureName, "textureName");
        this.previewSource = Objects.requireNonNull(previewSource, "previewSource");
    }

    public void draw(final GUIContext context,
                     final @Nullable K key,
                     final SableBlueprintPreview.View view,
                     final int x,
                     final int y,
                     final int width,
                     final int height) {
        if (this.closed || key == null || width <= 0 || height <= 0) {
            return;
        }
        final SableBlueprintPreview preview = this.previewSource.apply(key);
        if (preview == null) {
            return;
        }
        final TextureKey<K> textureKey = new TextureKey<>(key, view);
        CachedTexture cached = this.textures.get(textureKey);
        if (cached == null || cached.preview() != preview) {
            if (cached != null) {
                Minecraft.getInstance().getTextureManager().release(cached.location());
            }
            cached = create(preview, view);
            this.textures.put(textureKey, cached);
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

    private CachedTexture create(final SableBlueprintPreview preview,
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
                    .register(this.textureName, texture);
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
        if (this.closed) {
            return;
        }
        this.closed = true;
        final var manager = Minecraft.getInstance().getTextureManager();
        for (final CachedTexture cached : this.textures.values()) {
            manager.release(cached.location());
        }
        this.textures.clear();
    }

    private record TextureKey<K>(K key, SableBlueprintPreview.View view) {
    }

    private record CachedTexture(SableBlueprintPreview preview, ResourceLocation location) {
    }
}
