package dev.rew1nd.sableschematicapi.survival;

import org.jetbrains.annotations.Nullable;

/**
 * Creates optional special material sources for a blueprint cannon.
 */
public interface BlueprintCannonMaterialSourceProvider {
    @Nullable BlueprintCannonMaterialSource create(BlueprintCannonMaterialSourceContext context);
}
