package dev.rew1nd.sableschematicapi.api.blueprint.survival;

/**
 * Declares how aggressively a block cost rule match may be cached.
 */
public enum BlueprintBlockCostRuleCacheMode {
    /**
     * The predicate may depend on payload data such as future block entity NBT.
     */
    NONE,

    /**
     * The predicate result depends only on the exact {@link net.minecraft.world.level.block.state.BlockState}.
     */
    STATE,

    /**
     * The predicate result depends only on the {@link net.minecraft.world.level.block.Block}.
     */
    BLOCK
}
