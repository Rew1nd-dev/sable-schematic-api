package dev.rew1nd.sableschematicapi.blueprint.tool;

import dev.rew1nd.sableschematicapi.blueprint.SableBlueprint;

public record BlueprintToolSummary(int subLevels, int blocks, int blockEntityTags, int entities) {
    public static BlueprintToolSummary of(final SableBlueprint blueprint) {
        return new BlueprintToolSummary(
                blueprint.subLevels().size(),
                blueprint.blockCount(),
                blueprint.blockEntityCount(),
                blueprint.entityCount()
        );
    }

    public String describe() {
        return "%s sub-level(s), %s block(s), %s block entity tag(s), %s entity tag(s)"
                .formatted(this.subLevels, this.blocks, this.blockEntityTags, this.entities);
    }
}
