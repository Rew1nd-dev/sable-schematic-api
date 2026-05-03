package dev.rew1nd.sableschematicapi.api.blueprint.survival;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;

/**
 * Small blueprint summary suitable for item tooltips, UI, and build admission.
 */
public record BlueprintSummary(int subLevels, int blocks, int blockEntityTags, int entities) {
    public static BlueprintSummary of(final SableBlueprint blueprint) {
        return new BlueprintSummary(
                blueprint.subLevels().size(),
                blueprint.blockCount(),
                blueprint.blockEntityCount(),
                blueprint.entityCount()
        );
    }
}
