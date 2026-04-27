package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.core.BlockPos;

/**
 * Stable reference to a block inside a Sable blueprint.
 *
 * @param subLevelId blueprint-local sub-level id
 * @param localPos   block position relative to the saved sub-level payload
 */
public record BlueprintBlockRef(int subLevelId, BlockPos localPos) {
}
