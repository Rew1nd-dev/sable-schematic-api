package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import net.minecraft.resources.ResourceLocation;

/**
 * Computes survival material costs for one effective blueprint block payload.
 */
public interface BlueprintBlockCostRule {
    ResourceLocation id();

    /**
     * Cost consumed when the block state itself is placed during the incremental
     * block phase.
     */
    CostQuote quotePlacement(BlueprintBuildBlockPayload payload, BlueprintBlockCostContext context);

    /**
     * Cost consumed when this block's saved block entity NBT is loaded during the
     * commit phase.
     */
    CostQuote quoteNbtLoad(BlueprintBuildBlockPayload payload, BlueprintBlockCostContext context);
}
