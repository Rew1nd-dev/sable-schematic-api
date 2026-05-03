package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import dev.rew1nd.sableschematicapi.api.blueprint.survival.CostQuote;
import net.minecraft.resources.ResourceLocation;

/**
 * Computes the material cost for a typed post-process operation.
 *
 * @param <T> the operation type
 */
public interface BlueprintPostProcessCostStrategy<T extends BlueprintPostProcessOperation> {
    /**
     * The operation type this cost strategy handles.
     */
    ResourceLocation type();

    /**
     * Produces the material quote for one operation. Called during pre-commit validation.
     */
    CostQuote quote(T operation, BlueprintPostProcessCostContext context);
}
