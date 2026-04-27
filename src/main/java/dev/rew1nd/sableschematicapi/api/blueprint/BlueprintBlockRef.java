package dev.rew1nd.sableschematicapi.api.blueprint;

import net.minecraft.core.BlockPos;

/**
 * Stable reference to a block inside a Sable blueprint.
 *
 * <p>This reference is independent of both the original source storage position
 * and the final placed storage position. Save-time code should store this form
 * in sidecar data, then placement code should resolve it through
 * {@link BlueprintPlaceSession#mapBlock(BlueprintBlockRef)}.</p>
 *
 * @param subLevelId blueprint-local sub-level id
 * @param localPos   block position relative to the saved sub-level payload
 */
public record BlueprintBlockRef(int subLevelId, BlockPos localPos) {
}
