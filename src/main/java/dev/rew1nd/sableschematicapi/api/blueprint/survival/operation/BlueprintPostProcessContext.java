package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import net.minecraft.server.level.ServerLevel;

/**
 * Context provided to {@link BlueprintPostProcessMapper#apply(BlueprintPostProcessOperation, BlueprintPostProcessContext)}.
 */
public record BlueprintPostProcessContext(
        BlueprintPlaceSession session,
        ServerLevel level
) {
}
