package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.resources.ResourceLocation;

/**
 * Sanitizes a saved block entity tag before survival commit loads it.
 */
public interface BlueprintNbtSanitizer {
    ResourceLocation id();

    NbtSanitizeResult sanitize(BlueprintNbtSanitizeContext context);
}
