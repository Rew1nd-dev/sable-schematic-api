package dev.rew1nd.sableschematicapi.tool.client.preview.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Field;

public final class BlueprintToolPreviewLighting {
    private static final @Nullable Field LIGHT_PIXELS = findField(LightTexture.class, NativeImage.class);
    private static final @Nullable Field LIGHT_TEXTURE = findField(LightTexture.class, DynamicTexture.class);
    private static final @Nullable Field UPDATE_LIGHT_TEXTURE = findField(LightTexture.class, Boolean.TYPE);

    private BlueprintToolPreviewLighting() {
    }

    public static boolean applyDiagramLightTexture(final LightTexture lightTexture,
                                                   final float brightnessMultiplier) {
        if (LIGHT_PIXELS == null || LIGHT_TEXTURE == null || UPDATE_LIGHT_TEXTURE == null) {
            return false;
        }

        try {
            final NativeImage lightPixels = (NativeImage) LIGHT_PIXELS.get(lightTexture);
            final DynamicTexture texture = (DynamicTexture) LIGHT_TEXTURE.get(lightTexture);
            final Vector3f color = new Vector3f();

            for (int skyLight = 0; skyLight < 16; ++skyLight) {
                for (int blockLight = 0; blockLight < 16; ++blockLight) {
                    final float skyBrightness = getBrightness(skyLight);
                    final float blockBrightness = getBrightness(blockLight);
                    final float brightness = Math.max(0.25F, Math.max(skyBrightness, blockBrightness) * 0.65F + 0.25F);
                    final float brightnessG = brightness * ((brightness * 0.6F + 0.4F) * 0.6F + 0.4F);
                    final float brightnessB = brightness * (brightness * brightness * 0.6F + 0.4F);

                    color.set(brightness, brightnessG, brightnessB);
                    color.lerp(new Vector3f(0.99F, 1.12F, 1.0F), 0.25F);
                    clampColor(color);
                    color.lerp(new Vector3f(notGamma(color.x), notGamma(color.y), notGamma(color.z)), 0.55F);
                    color.lerp(new Vector3f(0.75F, 0.75F, 0.75F), 0.04F);
                    clampColor(color);
                    color.mul(255.0F * brightnessMultiplier);

                    final int r = (int) color.x();
                    final int g = (int) color.y();
                    final int b = (int) color.z();
                    lightPixels.setPixelRGBA(blockLight, skyLight, 0xFF000000 | b << 16 | g << 8 | r);
                }
            }

            UPDATE_LIGHT_TEXTURE.setBoolean(lightTexture, true);
            texture.upload();
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static float getBrightness(final int lightLevel) {
        final float value = (float) lightLevel / 15.0F;
        return value / (4.0F - 3.0F * value);
    }

    private static void clampColor(final Vector3f color) {
        color.set(
                Mth.clamp(color.x, 0.0F, 1.0F),
                Mth.clamp(color.y, 0.0F, 1.0F),
                Mth.clamp(color.z, 0.0F, 1.0F)
        );
    }

    private static float notGamma(final float value) {
        final float inverted = 1.0F - value;
        return 1.0F - inverted * inverted * inverted * inverted;
    }

    private static @Nullable Field findField(final Class<?> owner,
                                             final Class<?> fieldType) {
        for (final Field field : owner.getDeclaredFields()) {
            if (!fieldType.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                return field;
            } catch (final RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
