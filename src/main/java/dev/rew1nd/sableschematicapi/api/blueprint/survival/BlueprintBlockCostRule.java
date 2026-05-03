package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.resources.ResourceLocation;

/**
 * Computes the immediate material cost for one effective survival-build block payload.
 */
public interface BlueprintBlockCostRule {
    ResourceLocation id();

    CostQuote quote(BlueprintBuildBlockPayload payload, BlueprintBlockCostContext context);
}
