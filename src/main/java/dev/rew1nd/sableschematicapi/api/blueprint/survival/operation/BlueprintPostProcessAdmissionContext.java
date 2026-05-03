package dev.rew1nd.sableschematicapi.api.blueprint.survival.operation;

import dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;
import net.minecraft.server.level.ServerLevel;

/**
 * Context provided to {@link BlueprintPostProcessMapper#admit(BlueprintPostProcessOperation, BlueprintPostProcessAdmissionContext)}.
 */
public record BlueprintPostProcessAdmissionContext(
        BlueprintPlaceSession session,
        SableBlueprint blueprint,
        ServerLevel level
) {
}
